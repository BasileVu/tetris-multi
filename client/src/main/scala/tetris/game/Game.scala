package tetris.game

import json._
import org.scalajs.dom
import org.scalajs.dom.WebSocket
import org.scalajs.dom.raw.{HTMLButtonElement, MessageEvent, MouseEvent}
import shared.Actions._
import shared.GameAPIKeys
import shared.GameRules._


class Game {
  private val userGB: GameBox = new GameBox("user-game-box", nGameRows, nGameCols, nNextPieceRows, nNextPieceCols)
  private val opponentGB: GameBox = new GameBox("opponent-game-box", nGameRows, nGameCols, nNextPieceRows, nNextPieceCols)

  private val host: String = dom.window.location.host
  private val ws = new WebSocket(s"ws://$host/ws")

  private var id: String = ""

  def sendAction(action: Action): Unit = {
    val json: String = Map(
      GameAPIKeys.id -> id,
      GameAPIKeys.action -> action.name
    ).js.toDenseString

    ws.send(json)
  }

  def drawGridIfExists(data: JValue, key: String, opponent: Boolean): Unit = {
    if (data(key) != JUndefined) {
      // FIXME change to use seqs instead of arrays everywhere
      val grid = data(key).value.asInstanceOf[Seq[Seq[Boolean]]].map(_.toArray).toArray
      val gb = if (opponent) opponentGB else userGB

      key match {
        case GameAPIKeys.gameGrid => gb.drawGame(grid)
        case GameAPIKeys.nextPieceGrid => gb.drawNextPiece(grid)
      }
    }
  }

  def changeIntValueIfExists(data: JValue, key: String, opponent: Boolean): Unit = {
    if (data(key) != JUndefined) {
      val number = data(key).value.asInstanceOf[Int]
      val gb = if (opponent) opponentGB else userGB

      key match {
        case GameAPIKeys.piecesPlaced => gb.setPiecesPlaced(number)
        case GameAPIKeys.points => gb.setPoints(number)
      }
    }
  }

  def handleMessage(data: JValue): Unit = {
    if (data(GameAPIKeys.id) != JUndefined) {
      id = data(GameAPIKeys.id).value.asInstanceOf[String]
    }
    else if (data(GameAPIKeys.won) != JUndefined) {
      val won = data(GameAPIKeys.won).value.asInstanceOf[Boolean]
      if (won) {
        println("You won the game!")
      } else {
        println("You lost the game.")
      }
    }
    else if (data(GameAPIKeys.draw) != JUndefined) {
      println("Draw!")
    }
    else {
      val opponent = data(GameAPIKeys.opponent).value.asInstanceOf[Boolean]
      drawGridIfExists(data, GameAPIKeys.gameGrid, opponent)
      drawGridIfExists(data, GameAPIKeys.nextPieceGrid, opponent)
      changeIntValueIfExists(data, GameAPIKeys.piecesPlaced, opponent)
      changeIntValueIfExists(data, GameAPIKeys.points, opponent)
    }
  }

  def run(): Unit = {
    userGB.drawGame(Array.ofDim[Boolean](nGameRows, nGameCols))
    userGB.drawNextPiece(Array.ofDim[Boolean](nNextPieceRows, nNextPieceCols))
    opponentGB.drawGame(Array.ofDim[Boolean](nGameRows, nGameCols))
    opponentGB.drawNextPiece(Array.ofDim[Boolean](nNextPieceRows, nNextPieceCols))

    val startButton = dom.document.querySelector("#ready-button").asInstanceOf[HTMLButtonElement]
    startButton.onclick = (_: MouseEvent) => sendAction(Start)

    ws.onmessage = (e: MessageEvent) => handleMessage(JValue.fromString(e.data.toString))

    dom.window.onkeydown = (e: dom.KeyboardEvent) => {
      e.keyCode match {
        case 37 | 65 => sendAction(Left)
        case 38 | 87 => sendAction(Rotate)
        case 39 | 68 => sendAction(Right)
        case 40 | 83 => sendAction(Fall)
        case _ =>
      }
    }
  }
}
