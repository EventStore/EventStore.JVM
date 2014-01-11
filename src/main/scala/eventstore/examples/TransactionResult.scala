package eventstore.examples

import akka.actor.Status.Failure
import akka.actor.{ ActorLogging, Actor }
import eventstore.TransactionActor.CommitCompleted

class TransactionResult extends Actor with ActorLogging {
  def receive = {
    case Failure(x) =>
      log.error(x.toString)
      context.system.shutdown()

    case x =>
      log.info(x.toString)
      if (x == CommitCompleted) context.system.shutdown()
  }
}