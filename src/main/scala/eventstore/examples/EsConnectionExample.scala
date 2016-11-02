package eventstore.examples

import akka.actor.ActorSystem
import scala.concurrent.Future
import eventstore._

object EsConnectionExample extends App {
  val system = ActorSystem()

  import system.dispatcher

  val connection = EsConnection(system)
  val log = system.log

  val stream = EventStream.Id("my-stream")

  val readEvent: Future[ReadEventCompleted] = connection.apply(ReadEvent(stream))
  readEvent.onSuccess {
    case ReadEventCompleted(event) => log.info(event.toString)
  }

  val readStreamEvents: Future[ReadStreamEventsCompleted] = connection.apply(ReadStreamEvents(stream))
  readStreamEvents.onSuccess {
    case x: ReadStreamEventsCompleted => log.info(x.events.toString())
  }

  val readAllEvents: Future[ReadAllEventsCompleted] = connection.apply(ReadAllEvents(maxCount = 5))
  readAllEvents.onSuccess {
    case x: ReadAllEventsCompleted => log.info(x.events.toString())
  }

  val writeEvents: Future[WriteEventsCompleted] = connection.apply(WriteEvents(stream, List(EventData("my-event"))))
  writeEvents.onSuccess {
    case x: WriteEventsCompleted => log.info(x.numbersRange.toString)
  }
}