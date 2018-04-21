package eventstore

import scala.collection._
import scala.collection.immutable.Queue
import akka.actor.ActorRef
import akka.actor.Actor.Receive
import akka.actor.Terminated
import akka.actor.Status.Failure
import akka.stream._
import akka.stream.stage._
import akka.stream.stage.GraphStageLogic.StageActor
import akka.stream.SourceShape

private[eventstore] abstract class SourceStageLogic[T, O <: Ordered[O], P <: O](
    shape:       SourceShape[T],
    out:         Outlet[T],
    streamId:    EventStream,
    connection:  ActorRef,
    credentials: Option[UserCredentials],
    settings:    Settings,
    infinite:    Boolean
) extends GraphStageLogic(shape) with StageLogging {

  def first: P
  def eventFrom: IndexedEvent ⇒ T
  def positionFrom: T ⇒ P
  def pointerFrom: P ⇒ Long
  def positionExclusive: Option[StreamPointer]

  ///

  private var last: Option[P] = positionExclusive collect { case StreamPointer.Exact(p) ⇒ p }
  private val buffer = mutable.Queue.empty[T]
  private val emptyBehavior = PartialFunction.empty
  private def ready = buffer.size <= settings.readBatchSize

  ///

  override def preStart(): Unit = {

    super.preStart()

    become(emptyBehavior).watch(connection)

    positionExclusive match {
      case Some(StreamPointer.Last)     ⇒ become(subscribingFromLast)
      case Some(StreamPointer.Exact(p)) ⇒ become(reading(p))
      case None                         ⇒ become(reading(first))
    }

  }

  def reading(next: P): Receive = {

    def read(events: List[T], next: P, endOfStream: Boolean): Unit = become {
      enqueueAndPush(events: _*)
      if (endOfStream) {
        if (infinite) subscribing(next)
        else drainAndComplete()
      } else if (ready) reading(next)
      else rcvRequest(reading(next))
    }

    readEventsFrom(next)
    rcvRead(next, read) or rcvRequest()
  }

  def subscribingFromLast: Receive = {
    if (infinite) {
      subscribeToStream()
      rcvSubscribed(_ ⇒ become(subscribed)) or rcvRequest() or rcvUnexpectedUnsubscribed
    } else {
      completeStage()
      emptyBehavior
    }
  }

  def subscribing(next: P): Receive = {

    def subscribed(subscriptionNumber: Option[P]): Unit = become {
      subscriptionNumber
        .filter(sn ⇒ last.forall(l ⇒ pointerFrom(sn) > pointerFrom(l)))
        .map(sn ⇒ catchingUp(next, sn, Queue()))
        .getOrElse(this.subscribed)
    }

    subscribeToStream()
    rcvSubscribed(subscribed) or rcvRequest() or rcvUnexpectedUnsubscribed
  }

  def unsubscribing: Receive = {

    def unsubscribed = {
      def reading = this.reading(last getOrElse first)
      if (ready) reading
      else rcvRequest(reading)
    }

    unsubscribe()
    rcvUnsubscribed(unsubscribed) or rcvRequest()
  }

  def subscribed: Receive = {

    def eventAppeared(event: IndexedEvent) = {
      enqueueAndPush(eventFrom(event))
      if (ready) subscribed else unsubscribing
    }

    rcvEventAppeared(eventAppeared) or
      rcvRequest() or
      rcvUnexpectedUnsubscribed
  }

  def catchingUp(next: P, subscriptionNumber: P, stash: Queue[T]): Receive = {

    def catchUp(subscriptionNumber: P, stash: Queue[T]): Receive = {

      val subPos = pointerFrom(subscriptionNumber)
      val evtPos = positionFrom andThen pointerFrom

      def read(es: List[T], next: P, eos: Boolean): Unit = become {
        enqueueAndPush(es: _*)
        if (es.isEmpty || es.exists(evtPos(_) > subPos)) {
          enqueueAndPush(stash: _*)
          subscribed
        } else {
          if (ready)
            catchingUp(next, subscriptionNumber, stash)
          else unsubscribing
        }
      }

      def eventAppeared(event: IndexedEvent) =
        catchUp(subscriptionNumber, stash enqueue eventFrom(event))

      rcvRead(next, read) or
        rcvEventAppeared(eventAppeared) or
        rcvRequest() or
        rcvUnexpectedUnsubscribed
    }

    readEventsFrom(next)
    catchUp(subscriptionNumber, stash)
  }

  def drainAndComplete(): Receive = {
    emitMultiple(out, buffer.iterator, () ⇒ completeStage())
    emptyBehavior
  }

  /// State related

  def enqueueAndPush(events: T*): Unit = {

    def enqueue(event: T): Unit = {
      val lastIn = buffer.lastOption.map(positionFrom).orElse(last)
      if (lastIn.forall(_ < positionFrom(event))) buffer.enqueue(event)
    }

    events.foreach(enqueue)
    push()
  }

  def push(): Unit = if (isAvailable(out) && buffer.nonEmpty) {
    val event = buffer.dequeue()
    push(out, event)
    last = Some(positionFrom(event))
  }

  /// StageActor Related

  private def become(receive: Receive): StageActor = getStageActor {
    case (_, m) if receive.isDefinedAt(m) ⇒ receive(m)
    case (_, Terminated(`connection`))    ⇒ completeStage()
    case (_, Failure(failure))            ⇒ failStage(failure)
    case (r, m)                           ⇒ log.warning(s"unhandled from message $r: $m")
  }

  /// Messages from OutHandler

  def rcvRequest(): Receive = {
    case Pump ⇒ push()
  }

  def rcvRequest(receive: ⇒ Receive): Receive = {
    case Pump ⇒ push(); if (ready) become(receive)
  }

  /// Messages from Connection

  def rcvRead(next: P, onRead: (List[T], P, Boolean) ⇒ Unit): Receive
  def rcvSubscribed(receive: Option[P] ⇒ Unit): Receive

  def rcvUnsubscribed(receive: ⇒ Receive): Receive = {
    case Unsubscribed ⇒ become(receive)
  }

  def rcvUnexpectedUnsubscribed(): Receive = {
    case Unsubscribed ⇒
      def esLast = positionExclusive collect { case StreamPointer.Last ⇒ subscribingFromLast }
      val action = last.map(subscribing).getOrElse(esLast.getOrElse(subscribing(first)))
      become(action)
  }

  def rcvEventAppeared(receive: IndexedEvent ⇒ Receive): Receive = {
    case StreamEventAppeared(x) ⇒ become(receive(x))
  }

  /// Messages to Connection

  def buildReadEventsFrom(p: P): Out

  def readEventsFrom(next: P): Unit = {
    toConnection(buildReadEventsFrom(next))
  }

  def toConnection(x: Out) =
    connection.tell(credentials.fold[OutLike](x)(x.withCredentials), stageActor.ref)

  def subscribeToStream() =
    toConnection(SubscribeTo(streamId, resolveLinkTos = settings.resolveLinkTos))

  def unsubscribe() = toConnection(Unsubscribe)

  ///

  setHandler(out, new OutHandler {
    def onPull(): Unit = stageActor.ref ! Pump
  })

  case object Pump

  sealed trait StreamPointer extends Product with Serializable
  object StreamPointer {
    case object Last extends StreamPointer
    case class Exact(p: P) extends StreamPointer
  }

}