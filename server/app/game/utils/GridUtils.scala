package game.utils

import game.GameState
import shared.GameRules
import shared.GameRules.nGameCols
import shared.Types.Grid

/**
  * utility functions related to grid manipulation.
  */
object GridUtils {
  /**
    * Removes the completed lines of the grid in the specified game state.
    *
    * The lines deleted are returned with their row index in the grid.
    *
    * @param state The game state that contains the grid.
    * @return The lines with their row index in the grid.
    */
  def removeCompletedLines(state: GameState): Array[(Array[Boolean], Int)] = {
    val (removed, kept) = state.gameGrid
      .zipWithIndex
      .partition(p => p._1.count(x => x) == nGameCols)

    val newValues = Array.ofDim[Boolean](removed.length, nGameCols) ++ kept.map(_._1.clone)
    state.updateGameGrid(newValues)

    removed
  }

  /**
    * Pushes the specified lines at the bottom of the grid contained in the specified state.
    *
    * If the lines would make the current piece in the state overlap with the other blocs in the grid, the piece
    * is moved up and if it can't be moved upward anymore, the player owning the state should lose.
    *
    * @param toPush The lines to push to the grid in the specified state.
    * @param state The state containing the grid to update.
    * @return Whether the player owning the state should lose.
    */
  def pushLinesToGrid(toPush: Grid, state: GameState): Boolean = {
    if (toPush.nonEmpty) {
      val piece = state.curPiece

      piece.removeFromGrid()

      state.updateGameGrid(state.gameGrid.drop(toPush.length).map(_.clone) ++ toPush)

      while (piece.wouldCollideIfAddedToGrid()) {
        if (!piece.moveUpWithOnlyGridBoundsCheck(updateGridOnMove = false)) {
          return true
        }
      }

      piece.addToGrid()
    }
    false
  }
}
