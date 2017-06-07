package game

import akka.actor.{ActorRef}
import models.User

case class PlayerWithState(player: Player) {
  val user: User = player.user
  var out: ActorRef = player.out
  val state: GameState = new GameState()

  def changeActorRef(newRef: ActorRef): Unit = {
    player.changeActorRef(newRef)
    out = newRef
  }
}
