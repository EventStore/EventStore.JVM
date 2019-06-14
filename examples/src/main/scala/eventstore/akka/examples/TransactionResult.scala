package eventstore
package akka
package examples

import _root_.akka.actor.Status.Failure
import _root_.akka.actor.{ ActorLogging, Actor }
import eventstore.akka.TransactionActor.CommitCompleted

class TransactionResult extends Actor with ActorLogging {
  def receive = {
    case Failure(x) =>
      log.error(x.toString)
      shutdown()

    case x =>
      log.info(x.toString)
      if (x == CommitCompleted) shutdown()
  }

  def shutdown(): Unit = { context.system.terminate(); () }
}