package eventstore.examples

import akka.actor.Status.Failure
import akka.actor.{ ActorLogging, Actor, Props, ActorSystem }
import eventstore._
import eventstore.tcp.ConnectionActor

object WriteEventExample extends App {
  val system = ActorSystem()
  val connection = system.actorOf(ConnectionActor.props())
  implicit val writeResult = system.actorOf(Props[WriteResult])

  val event = EventData("my-event", newUuid, data = Content("my event data"), metadata = Content("my first event"))

  connection ! WriteEvents(EventStream("my-stream"), List(event))

  class WriteResult extends Actor with ActorLogging {
    def receive = {
      case WriteEventsCompleted(eventNumber) =>
        log.info(s"eventNumber: $eventNumber")
        context.system.shutdown()

      case Failure(EsException(reason, message, _)) =>
        log.error(s"reason $reason, message: $message")
        context.system.shutdown()
    }
  }
}