package eventstore
package akka
package examples

import _root_.akka.actor.ActorSystem
import scala.concurrent.Future


object EsConnectionExample extends App {
  val system = ActorSystem()

  import system.dispatcher

  val connection = EsConnection(system)
  val log = system.log

  val stream = EventStream.Id("my-stream")

  val readEvent: Future[ReadEventCompleted] = connection.apply(ReadEvent(stream))
  readEvent foreach { x =>
    log.info(x.event.toString)
  }

  val readStreamEvents: Future[ReadStreamEventsCompleted] = connection.apply(ReadStreamEvents(stream))
  readStreamEvents foreach { x =>
    log.info(x.events.toString())
  }

  val readAllEvents: Future[ReadAllEventsCompleted] = connection.apply(ReadAllEvents(maxCount = 5))
  readAllEvents foreach { x =>
    log.info(x.events.toString())
  }

  val writeEvents: Future[WriteEventsCompleted] = connection.apply(WriteEvents(stream, List(EventData("my-event"))))
  writeEvents foreach { x =>
    log.info(x.numbersRange.toString)
  }
}