package eventstore
package akka
package examples

import _root_.akka.actor.ActorSystem
import scala.concurrent.Future
import eventstore.core.util.uuid.randomUuid

object EsConnectionExample extends App {
  val system = ActorSystem()

  import system.dispatcher

  val connection = EsConnection(system)
  val log = system.log

  val stream = EventStream.Id("my-stream")

  val readEvent: Future[ReadEventCompleted] = connection(ReadEvent(stream))
  readEvent foreach { x =>
    log.info(x.event.toString)
  }

  val readStreamEvents: Future[ReadStreamEventsCompleted] = connection(ReadStreamEvents(stream))
  readStreamEvents foreach { x =>
    log.info(x.events.toString())
  }

  val readAllEvents: Future[ReadAllEventsCompleted] = connection(ReadAllEvents(maxCount = 5))
  readAllEvents foreach { x =>
    log.info(x.events.toString())
  }

  val writeEvents: Future[WriteEventsCompleted] = connection(WriteEvents(stream, List(EventData("my-event", eventId = randomUuid))))
  writeEvents foreach { x =>
    log.info(x.numbersRange.toString)
  }
}