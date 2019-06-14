package eventstore
package akka
package examples

import _root_.akka.actor.Status.Failure
import _root_.akka.actor.{ ActorLogging, Actor, Props, ActorSystem }
import eventstore.core.util.uuid.randomUuid
import eventstore.akka.tcp.ConnectionActor

object WriteEventExample extends App {

  val system      = ActorSystem()
  val connection  = system.actorOf(ConnectionActor.props())
  val event       = EventData("my-event", eventId = randomUuid, data = Content("my event data"), metadata = Content("my first event"))

  implicit val writeResult = system.actorOf(Props(WriteResult))

  connection ! WriteEvents(EventStream.Id("my-stream"), List(event))

  case object WriteResult extends Actor with ActorLogging {

    def receive = {
      case WriteEventsCompleted(range, position) =>
        log.info("range: {}, position: {}", range, position)
        shutdown()

      case Failure(e: EsException) =>
        log.error(e.toString)
        shutdown()
    }

    def shutdown(): Unit = { context.system.terminate();  () }
  }
}