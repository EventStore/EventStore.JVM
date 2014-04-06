package eventstore.examples

import akka.actor.ActorSystem
import eventstore._
import scala.util.{ Success, Failure }
import scala.concurrent.Future

object EsConnectionExample extends App {
  val system = ActorSystem()

  import system.dispatcher

  val connection = EsConnection(system)
  val log = system.log

  val stream = EventStream("my-stream")

  val readEvent: Future[ReadEventCompleted] = connection.future(ReadEvent(stream))
  readEvent.onComplete {
    case Failure(e)                         => log.error(e.toString)
    case Success(ReadEventCompleted(event)) => log.info(event.toString)
  }

  val readStreamEvents: Future[ReadStreamEventsCompleted] = connection.future(ReadStreamEvents(stream))
  readStreamEvents.onComplete {
    case Failure(e)                            => log.error(e.toString)
    case Success(x: ReadStreamEventsCompleted) => log.info(x.events.toString())
  }

  val readAllEvents: Future[ReadAllEventsCompleted] = connection.future(ReadAllEvents(maxCount = 5))
  readAllEvents.onComplete {
    case Failure(e)                         => log.error(e.toString)
    case Success(x: ReadAllEventsCompleted) => log.info(x.events.toString())
  }

  val writeEvents: Future[WriteEventsCompleted] = connection.future(WriteEvents(stream, List(EventData("my-event"))))
  writeEvents.onComplete {
    case Failure(e)                       => log.error(e.toString)
    case Success(x: WriteEventsCompleted) => log.info(x.numbersRange.toString)
  }
}