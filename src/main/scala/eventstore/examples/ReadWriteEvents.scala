package eventstore
package examples

import akka.actor.Status.Failure
import akka.actor.{ ActorLogging, Props, Actor, ActorSystem }
import scala.util.Random
import scala.concurrent.duration._

object ReadWriteEvents extends App {
  val system = ActorSystem()
  system.actorOf(Props[ReadWriteEventsActor], "read-write")
}

class ReadWriteEventsActor extends Actor with ActorLogging {
  import context.dispatcher

  val maxCount = 10
  val maxAttempts = 3

  val connection = EventStoreExtension(context.system).actor
  val streamId = EventStream.Id(randomUuid.toString)

  val rcvStreamNotFound: Receive = {
    case Failure(_: StreamNotFoundException) =>
      val events = write(ExpectedVersion.NoStream)
      context become rcvWriteCompleted(events)
    case x =>
      log.error(s"Received unexpected $x")
      shutdown()
  }

  override def preStart() = read(EventNumber.First)

  def receive = rcvStreamNotFound

  def rcvWriteCompleted(events: List[EventData]): Receive = {
    case WriteEventsCompleted(Some(range), _) =>
      val number = range.start
      read(number)
      context become rcvReadCompleted(events, number, 1)
    case x =>
      log.error(s"Received unexpected $x")
      shutdown()
  }

  def rcvReadCompleted(events: List[EventData], number: EventNumber, attempt: Int): Receive = {
    case x: ReadStreamEventsCompleted =>
      if (x.events.map(_.data) != events) {
        if (attempt < maxAttempts) {
          log.warning("write.events is not equal to read.events, attempt {}/{} {}", attempt, maxAttempts, x)
          read(number)
          context become rcvReadCompleted(events, number, attempt + 1)
        } else {
          log.error("write.events must equal read.events {}", x)
          shutdown()
        }
      } else if (!x.endOfStream) {
        log.error("endOfStream must be true {}", x)
        shutdown()
      } else {
        val events = write(ExpectedVersion.Exact(x.lastEventNumber))
        context become rcvWriteCompleted(events)
      }
    case x =>
      log.error(s"Received unexpected {}", x)
      shutdown()
  }

  def write(expected: ExpectedVersion) = {
    val bytes = new Array[Byte](100)
    Random.nextBytes(bytes)
    val eventType = randomUuid.toString
    val events = List.fill(maxCount)(EventData(eventType, data = Content(bytes)))
    connection ! WriteEvents(streamId, events, expected)
    events
  }

  def read(number: EventNumber) = {
    val msg = ReadStreamEvents(streamId, number, maxCount)
    context.system.scheduler.scheduleOnce(100.millis, connection, msg)
  }

  def shutdown() = context.system.terminate()
}