package eventstore

import akka.actor.ActorRef
import scala.collection.immutable.Queue
import ReadDirection.Forward

/**
 * @author Yaroslav Klymko
 */
class CatchUpSubscriptionActor(
    val connection: ActorRef,
    val client: ActorRef,
    fromPositionExclusive: Option[Position.Exact],
    val resolveLinkTos: Boolean,
    readBatchSize: Int) extends AbstractSubscriptionActor {

  def this(connection: ActorRef, client: ActorRef) = this(connection, client, None, false, 500)

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
    subscriptionFailed(s"lastPosition: $lastPosition") orElse {
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
          def loop(events: List[IndexedEvent], lastPosition: Option[Position.Exact]): Receive = events match {
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
          loop(events.toList, lastPosition)
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

  def readAllEventsCompleted(f: (Seq[IndexedEvent], Position.Exact) => Receive): Receive = {
    case ReadAllEventsSucceed(events, _, nextPosition, Forward) => context become f(events, nextPosition)
    case ReadAllEventsFailed(reason, message, _, Forward)       => throw EventStoreException(reason, message)
  }

  def forward(event: IndexedEvent) {
    debug(s"forwarding $event")
    client ! Cs.AllStreamsEvent(event)
  }
}