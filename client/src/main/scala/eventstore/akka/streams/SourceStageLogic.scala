package eventstore
package akka
package streams

import scala.collection._
import scala.collection.immutable.Queue
import _root_.akka.actor.Actor.Receive
import _root_.akka.actor.Status.Failure
import _root_.akka.actor.{ActorRef, Terminated}
import _root_.akka.stream.{SourceShape, _}
import _root_.akka.stream.stage.GraphStageLogic.StageActor
import _root_.akka.stream.stage._
import SourceStageLogic.UnhandledMessage

private[eventstore] abstract class SourceStageLogic[T, O <: Ordered[O], P <: O](
  shape:       SourceShape[T],
  out:         Outlet[T],
  streamId:    EventStream,
  connection:  ActorRef,
  credentials: Option[UserCredentials],
  settings:    Settings,
  infinite:    Boolean
) extends GraphStageLogic(shape)
    with StageLogging {

  def first: P
  def eventFrom: IndexedEvent ⇒ T
  def positionFrom: T ⇒ P
  def pointerFrom: P ⇒ Long
  def operation: ReadFrom

  private var state: State = State.from(operation)

  setHandler(out, new OutHandler {
    def onPull(): Unit = push()
  })

  override def preStart(): Unit = {
    super.preStart()

    stageActorBecome(ignoring).watch(connection)

    operation match {
      case ReadFrom.Beginning       ⇒ readFrom(first)
      case ReadFrom.Exact(position) ⇒ readFrom(position)
      case ReadFrom.End             ⇒ if (infinite) subscribe() else completeStage()
    }
  }

  def readFrom(position: P): Unit = {

    def onReadCompleted(events: List[T], next: P, endOfStream: Boolean): Unit = {

      enqueueAndPush(events: _*)

      if (endOfStream) {
        if (infinite) subscribe()
        else drainAndComplete()
      } else {
        if (state.bufferAvailable) readFrom(next)
        else waitUntilBufferAvailable(() ⇒ readFrom(next))
      }
    }

    def onStreamNotExists(): Unit =
      if (infinite) subscribe() else completeStage()

    stageActorBecome {
      rcvRead(onReadCompleted, onStreamNotExists())
    }

    readEventsFrom(position)
  }

  def subscribe(): Unit = {
    stageActorBecome {
      rcvSubscribed(onSubscriptionCompleted)
    }
    subscribeToStream()
  }

  def onSubscriptionCompleted(subscribedFromExcl: Option[P]): Unit = (subscribedFromExcl, state.lastIn) match {
    case (Some(sn), Some(l)) if pointerFrom(sn) > pointerFrom(l) ⇒ catchup(l, sn)
    case (Some(sn), None) if operation.isFirst ⇒ catchup(first, sn)
    case _ ⇒ subscribed()
  }

  def subscribed(): Unit = {

    def onEventAppeared(idxEvent: IndexedEvent): Unit = {
      enqueueAndPush(eventFrom(idxEvent))
      if (state.bufferFull) unsubscribe()
    }

    stageActorBecome {
      rcvEventAppeared(onEventAppeared) or
        rcvUnsubscribed(onUnsubscribeCompleted()) or
        rcvSubscribed(onSubscriptionCompleted)
    }
    ()
  }

  def catchup(from: P, to: P, stash: Queue[T] = Queue.empty[T]): Unit = {

    def doCatchUp(to: P, stash: Queue[T]): Unit = {

      def onReadCompleted(events: List[T], next: P): Unit = {

        enqueueAndPush(events: _*)

        val evtPos = positionFrom andThen pointerFrom

        if (events.isEmpty || events.exists(evtPos(_) >= pointerFrom(to))) {
          enqueueAndPush(stash: _*)
          subscribed()
        } else {
          if (state.bufferFull) unsubscribe()
          else catchup(next, to, stash)
        }
      }

      def onEventAppeared(idxEvent: IndexedEvent): Unit = {
        doCatchUp(to, stash enqueue eventFrom(idxEvent))
      }

      def onResubscribed(subscribedFromExcl: Option[P]): Unit = {
        subscribedFromExcl.filter(_ > to).foreach(p ⇒ doCatchUp(p, Queue.empty[T]))
      }

      stageActorBecome {
        rcvRead((es, n, _) ⇒ onReadCompleted(es, n), subscribed()) or
          rcvEventAppeared(onEventAppeared) or
          rcvSubscribed(onResubscribed) or
          rcvUnsubscribed(onUnsubscribeCompleted())
      }
      ()
    }

    readEventsFrom(from)
    doCatchUp(to, stash)
  }

  def unsubscribe(): Unit = {
    stageActorBecome(
      rcvUnsubscribed(onUnsubscribeCompleted()) or
      rcvEventAppeared(_ => ())
    )
    unsubscribeFromStream()
  }

  def onUnsubscribeCompleted(): Unit = {
    if (state.bufferAvailable) subscribe()
    else waitUntilBufferAvailable(() ⇒ subscribe())
  }

  ///

  def drainAndComplete(): Unit = {
    val (events, newState) = state.dequeueAll
    state = newState
    emitMultiple(out, events.iterator, () ⇒ completeStage())
    stageActorBecome(ignoring)
    ()
  }

  def waitUntilBufferAvailable(action: () ⇒ Unit): Unit = {
    state = state.copy(onBufferAvailable = Some(action))
    stageActorBecome(ignoring)
    ()
  }

  def enqueueAndPush(events: T*): Unit = {
    state = events.foldLeft(state)((s, e) ⇒ s.enqueue(e))
    push()
  }

  def push(): Unit = if (isAvailable(out)) {
    state.dequeue.foreach {
      case (event, newState) ⇒
        state = newState
        push(out, event)
    }

    if (state.bufferAvailable) state.onBufferAvailable.foreach { action ⇒
      state = state.copy(onBufferAvailable = None)
      action()
    }
  }

  /// StageActor Related

  private def stageActorBecome(receive: Receive): StageActor = getStageActor {
    case (_, m) if receive.isDefinedAt(m) ⇒ receive(m)
    case (_, Terminated(`connection`))    ⇒ completeStage()
    case (_, Failure(failure))            ⇒ failStage(failure)
    case (r, m)                           ⇒ failStage(UnhandledMessage(r, m))
  }

  private val ignoring = PartialFunction.empty

  /// Messages from Connection

  def rcvRead(onRead: (List[T], P, Boolean) ⇒ Unit, onNotExists: ⇒ Unit): Receive

  def rcvSubscribed(onSubscribed: Option[P] ⇒ Unit): Receive

  def rcvUnsubscribed(onUnsubscribed: ⇒ Unit): Receive = {
    case Unsubscribed ⇒ onUnsubscribed
  }

  def rcvEventAppeared(onEventAppeared: IndexedEvent ⇒ Unit): Receive = {
    case StreamEventAppeared(event) ⇒ onEventAppeared(event)
  }

  /// Messages to Connection

  def buildReadEventsFrom(p: P): Out

  def readEventsFrom(next: P): Unit =
    toConnection(buildReadEventsFrom(next))

  def subscribeToStream(): Unit =
    toConnection(SubscribeTo(streamId, resolveLinkTos = settings.resolveLinkTos))

  def unsubscribeFromStream(): Unit =
    toConnection(Unsubscribe)

  def toConnection(x: Out) =
    connection.tell(credentials.fold[OutLike](x)(x.withCredentials), stageActor.ref)

  ///

  sealed trait ReadFrom extends Product with Serializable {
    def isFirst: Boolean = false
  }

  object ReadFrom {
    case object End extends ReadFrom
    case class Exact(positionExclusive: P) extends ReadFrom
    case object Beginning extends ReadFrom {
      override def isFirst: Boolean = true
    }
  }

  case class State(
      lastIn:            Option[P],
      buffer:            immutable.Queue[T] = Queue.empty[T],
      onBufferAvailable: Option[() ⇒ Unit]  = None
  ) {

    def bufferAvailable: Boolean = buffer.size <= settings.readBatchSize
    def bufferFull: Boolean = !bufferAvailable

    def enqueue(event: T): State = {
      def alreadyEnqueued(e: T) = lastIn.exists(_ >= positionFrom(e))

      if (alreadyEnqueued(event)) this
      else copy(buffer = buffer.enqueue(event), lastIn = Some(positionFrom(event)))
    }

    def dequeue: Option[(T, State)] =
      buffer.dequeueOption.map { case (e, b) ⇒ (e, copy(buffer = b)) }

    def dequeueAll: (List[T], State) =
      (buffer.toList, copy(buffer = Queue.empty[T]))
  }

  object State {
    def from(operation: ReadFrom): State = {
      val fromPositionExclusive = operation match {
        case ReadFrom.Exact(p) ⇒ Some(p)
        case _                 ⇒ None
      }
      State(fromPositionExclusive)
    }
  }
}

object SourceStageLogic {
  final case class UnhandledMessage(ref: ActorRef, msg: Any)
    extends RuntimeException(s"Unhandled from message: $msg from $ref")
}
