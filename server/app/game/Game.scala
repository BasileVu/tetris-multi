package game

import akka.actor.PoisonPill
import shared.Pieces._
import game.PiecesWithPosition.{GamePiece, NextPiece}
import play.api.libs.json.Json.JsValueWrapper
import shared.GameRules.nGameCols
import play.api.libs.json.{JsBoolean, JsObject, Json}
import shared.Actions._
import shared.{GameAPIKeys, GameRules}

import scala.concurrent.duration._


class Game(p1: Player, p2: Player, gameManager: GameManager) {
  private val player1: PlayerWithState = PlayerWithState(p1)
  private val player2: PlayerWithState = PlayerWithState(p2)

  private var gameFinished: Boolean = false
  private var gameBeganAt: Long = _

  private val players: Map[String, PlayerWithState] = Map(
    player1.id -> player1,
    player2.id -> player2
  )

  //Use the system's dispatcher as ExecutionContext
  private val system = akka.actor.ActorSystem("system")
  import system.dispatcher

  private def opponent(player: PlayerWithState): PlayerWithState = {
    if (player  == player1) player2
    else player1
  }

  def movePiece(id: String, action: Action): Unit = this.synchronized {
    val player = players(id)
    val gs = player.state

    if (!gs.ready) {
      return
    }

    val moved: Boolean = action match {
      case Left => gs.curPiece.moveLeft()
      case Right => gs.curPiece.moveRight()
      case Fall => gs.curPiece.fall()
      case Rotate => gs.curPiece.rotate()
    }

    if (moved) {
      if (action == Fall) handlePieceBottom(player)
      else broadcastPiecePositions(player)
    }
  }

  def setReady(id: String): Unit = {
    val player = players(id)
    player.state.ready = true
    println(id + " is ready")
    broadcast(player, Json.obj(GameAPIKeys.ready -> true))

    if (opponent(player).state.ready) {
      initGame()
    }
  }

  def initGame(): Unit = {
    broadcast(player1, Json.obj(
      GameAPIKeys.gameGrid -> player1.state.gameGrid,
      GameAPIKeys.nextPieceGrid -> player1.state.nextPieceGrid,
      piecePositionsToKeyVal(player1)
    ))
    broadcast(player2, Json.obj(
      GameAPIKeys.gameGrid -> player2.state.gameGrid,
      GameAPIKeys.nextPieceGrid -> player2.state.nextPieceGrid,
      piecePositionsToKeyVal(player2)
    ))

    gameBeganAt = System.currentTimeMillis()

    gameTick(player1)
    gameTick(player2)
  }

  private def handlePieceBottom(player: PlayerWithState): Unit = this.synchronized {
    val opp = opponent(player)
    val state = player.state
    val nBlocksAbove = state.curPiece.getPositions.minBy(_._1)._1

    if (nBlocksAbove <= 1) {
      lose(player)
    }

    val removed = removeCompletedLines(player.state)

    state.piecesPlaced += 1
    state.points += GameRules.pointsForPieceDown(nBlocksAbove, removed.length, state.gameSpeed)

    val oppLost = sendLinesToOpponent(removed, state, opp)
    if (oppLost) {
      lose(opp)
    }

    generateNewPiece(player)
  }

  private def generateNewPiece(player: PlayerWithState) = {
    val state = player.state
    state.curPiece = new GamePiece(state.nextPiece.piece, state.gameGrid)
    state.curPiece.addToGrid()

    state.nextPiece.removeFromGrid()
    state.nextPiece = new NextPiece(randomPiece(), state.nextPieceGrid)
    state.nextPiece.addToGrid()

    state.gameSpeed = GameRules.nextSpeed(state.gameSpeed)

    broadcastPiecePositions(player)

    broadcast(player, Json.obj(
      GameAPIKeys.gameGrid -> player.state.gameGrid,
      GameAPIKeys.nextPieceGrid -> player.state.nextPieceGrid,
      GameAPIKeys.piecesPlaced -> state.piecesPlaced,
      GameAPIKeys.points -> state.points
    ))
  }

  private def gameTick(player: PlayerWithState): Unit = {
    if (gameFinished) return

    system.scheduler.scheduleOnce(player.state.gameSpeed.milliseconds) {
      if (!player.state.curPiece.moveDown()) handlePieceBottom(player)
      else broadcastPiecePositions(player)

      gameTick(player)
    }
  }

  def broadcast(player: PlayerWithState, jsonObj: JsObject): Unit = {
    player.out ! Json.toJson(jsonObj + (GameAPIKeys.opponent -> JsBoolean(false))).toString()
    opponent(player).out ! Json.toJson(jsonObj + (GameAPIKeys.opponent -> JsBoolean(true))).toString()
  }

  def broadcastPiecePositions(player: PlayerWithState): Unit = {
    broadcast(player, Json.obj(piecePositionsToKeyVal(player)))
  }

  def piecePositionsToKeyVal(player: PlayerWithState): (String, JsValueWrapper) = {
    GameAPIKeys.piecePositions -> player.state.curPiece.getPositions.map(p => Array(p._1, p._2))
  }

  private def lose(player: PlayerWithState): Unit = {
    gameFinished = true

    player.out ! Json.obj(GameAPIKeys.won -> JsBoolean(false)).toString()
    opponent(player).out ! Json.obj(GameAPIKeys.won -> JsBoolean(true)).toString()

    player1.out ! PoisonPill
    player2.out ! PoisonPill

    gameManager.deleteGame(player1.id)
    gameManager.deleteGame(player2.id)

    println(s"Game took ${(System.currentTimeMillis() - gameBeganAt) / 1000} seconds")
  }

  def lose(id: String): Unit = {
    lose(players(id))
  }

  def removeCompletedLines(state: GameState): Array[(Array[Boolean], Int)] = {
    val (removed, kept) = state.gameGrid
      .zipWithIndex
      .partition(p => p._1.count(x => x) == nGameCols)

    val newValues = Array.ofDim[Boolean](removed.length, nGameCols) ++ kept.map(_._1.map(identity))
    state.updateGameGrid(newValues)

    removed
  }

  def sendLinesToOpponent(removed: Array[(Array[Boolean], Int)], state: GameState, opp: PlayerWithState): Boolean = {
    val linesBeforeCompleted: Array[Array[Boolean]] = removed.map(p => {
      val row: Array[Boolean] = p._1
      val i = p._2
      row.indices.map(col => !state.curPiece.getPositions.contains((i, col))).toArray
    })

    val toSend = linesBeforeCompleted.length match {
      case 1 | 2 | 3 => linesBeforeCompleted.take(linesBeforeCompleted.length - 1)
      case _ => linesBeforeCompleted
    }

    if (toSend.nonEmpty) {
      val oppPiece = opp.state.curPiece

      oppPiece.removeFromGrid()
      opp.state.updateGameGrid(opp.state.gameGrid.drop(toSend.length) ++ toSend)

      while (oppPiece.wouldCollideIfAddedToGrid()) {
        if (!oppPiece.moveUp(updateGridOnMove = false)) {
          return true
        }
      }

      oppPiece.addToGrid()
    }

    broadcastPiecePositions(opp)
    broadcast(opp, Json.obj(GameAPIKeys.gameGrid -> opp.state.gameGrid))
    false
  }
}