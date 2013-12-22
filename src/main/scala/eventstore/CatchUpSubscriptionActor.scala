package eventstore

import ReadDirection.Forward
import akka.actor.{ Props, ActorRef }
import akka.actor.Status.Failure
import scala.collection.immutable.Queue
import scala.annotation.tailrec

object CatchUpSubscriptionActor {
  def props(
    connection: ActorRef,
    client: ActorRef,
    fromPositionExclusive: Option[Position.Exact] = None,
    resolveLinkTos: Boolean = false,
    readBatchSize: Int = 500): Props =
    Props(classOf[CatchUpSubscriptionActor], connection, client /*TODO client or parent*/ , fromPositionExclusive, resolveLinkTos, readBatchSize)
}
class CatchUpSubscriptionActor(
    val connection: ActorRef,
    val client: ActorRef,
    fromPositionExclusive: Option[Position.Exact],
    val resolveLinkTos: Boolean,
    readBatchSize: Int) extends AbstractSubscriptionActor {

  val streamId = EventStream.All

  def receive = read(
    lastPosition = fromPositionExclusive,
    nextPosition = fromPositionExclusive getOrElse Position.First)

  def read(lastPosition: Option[Position.Exact], nextPosition: Position): Receive = {
    readEventsFrom(nextPosition)
    read(lastPosition)
  }

  def read(lastPosition: Option[Position.Exact]): Receive = readAllEventsCompleted {
    (events, nextPosition) =>
      if (events.nonEmpty) read(lastPosition = process(lastPosition, events), nextPosition = nextPosition)
      else subscribe(lastPosition = lastPosition, nextPosition = nextPosition)
  }

  def subscribe(lastPosition: Option[Position.Exact], nextPosition: Position): Receive = {
    subscribeToStream(s"lastPosition: $lastPosition")
    subscriptionFailed orElse {
      case SubscribeToAllCompleted(lastCommit) =>
        subscribed = true
        debug(s"subscribed at lastCommit: $lastCommit")
        context become (
          if (lastPosition.exists(_.commitPosition >= lastCommit)) liveProcessing(lastPosition, Queue())
          else {
            debug(s"catch up events from lastPosition: $lastPosition to subscription lastCommit: $lastCommit")
            catchUp(lastPosition, nextPosition, lastCommit)
          })
    }
  }

  def catchUp(
    lastPosition: Option[Position.Exact],
    nextPosition: Position,
    subscriptionLastCommit: Long,
    stash: Queue[IndexedEvent] = Queue()): Receive = {

    readEventsFrom(nextPosition)

    def catchingUp(stash: Queue[IndexedEvent]): Receive = readAllEventsCompleted {
      (events, next) =>
        if (events.isEmpty) liveProcessing(lastPosition, stash)
        else {
          @tailrec def loop(events: List[IndexedEvent], lastPosition: Option[Position.Exact]): Receive = events match {
            case Nil => catchUp(lastPosition, next, subscriptionLastCommit, stash)
            case event :: tail =>
              val position = event.position
              if (lastPosition.exists(_ >= position)) loop(tail, lastPosition)
              else if (position.commitPosition > subscriptionLastCommit) liveProcessing(lastPosition, stash)
              else {
                forward(event)
                loop(tail, Some(position))
              }
          }
          loop(events, lastPosition)
        }
    } orElse {
      case StreamEventAppeared(x) if x.position.commitPosition > subscriptionLastCommit =>
        debug(s"catching up: adding appeared event to stash(${stash.size}): $x")
        context become catchingUp(stash enqueue x)
    }
    catchingUp(stash)
  }

  def liveProcessing(lastPosition: Option[Position.Exact], stash: Queue[IndexedEvent]): Receive = {
    debug(s"live processing started, lastPosition: $lastPosition")
    client ! Cs.LiveProcessingStarted

    def liveProcessing(lastPosition: Option[Position.Exact]): Receive = {
      case StreamEventAppeared(event) => context become liveProcessing(process(lastPosition, event))
    }
    liveProcessing(process(lastPosition, stash))
  }

  def process(lastPosition: Option[Position.Exact], events: Seq[IndexedEvent]): Option[Position.Exact] =
    events.foldLeft(lastPosition)((lastPosition, event) => process(lastPosition, event))

  def process(lastPosition: Option[Position.Exact], event: IndexedEvent): Option[Position.Exact] = {
    val position = event.position
    lastPosition match {
      case Some(last) if last >= position =>
        log.warning(s"$streamId: event.position <= lastPosition: $position <= $last, dropping $event")
        lastPosition
      case _ =>
        forward(event)
        Some(position)
    }
  }

  def readEventsFrom(position: Position) {
    debug(s"reading events from $position")
    connection ! ReadAllEvents(position, readBatchSize, Forward, resolveLinkTos = resolveLinkTos)
  }

  def readAllEventsCompleted(f: (List[IndexedEvent], Position.Exact) => Receive): Receive = {
    case ReadAllEventsCompleted(events, _, nextPosition, Forward) => context become f(events, nextPosition)
    case Failure(e) => throw e
  }

  def forward(event: IndexedEvent) {
    debug(s"forwarding $event")
    client ! Cs.AllStreamsEvent(event)
  }
}