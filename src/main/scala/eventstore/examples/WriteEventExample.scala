package eventstore.examples

import akka.actor.Status.Failure
import akka.actor.{ ActorLogging, Actor, Props, ActorSystem }
import eventstore._
import eventstore.tcp.ConnectionActor

object WriteEventExample extends App {
  val system = ActorSystem()
  val connection = system.actorOf(ConnectionActor.props())
  implicit val writeResult = system.actorOf(Props[WriteResult])

  val event = EventData("my-event", data = Content("my event data"), metadata = Content("my first event"))

  connection ! WriteEvents(EventStream.Id("my-stream"), List(event))

  class WriteResult extends Actor with ActorLogging {
    def receive = {
      case WriteEventsCompleted(range, position) =>
        log.info("range: {}, position: {}", range, position)
        context.system.terminate()

      case Failure(e: EsException) =>
        log.error(e.toString)
        context.system.terminate()
    }
  }
}