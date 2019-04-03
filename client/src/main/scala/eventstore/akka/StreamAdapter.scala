package eventstore
package akka

import scala.reflect.ClassTag
import _root_.akka.NotUsed
import _root_.akka.actor._
import _root_.akka.actor.Status.Failure
import _root_.akka.stream._
import _root_.akka.stream.scaladsl._
import core.syntax._
import streams.SourceStageLogic.{LiveCallback, OnLive, StageError}
import streams.{AllStreamsSourceStage, StreamSourceStage}
import StreamAdapter.Fsm

private[eventstore] object StreamAdapter {

  def all(
     connection: ActorRef,
     client: ActorRef,
     fromPositionExclusive: Option[Position],
     credentials: Option[UserCredentials],
     settings: Settings
   ): Props = {
    val src: LiveCallback => Source[IndexedEvent, NotUsed] = cb => Source.fromGraph(
      new AllStreamsSourceStage(connection, fromPositionExclusive, credentials, settings, infinite = true, cb)
    )
    Props(new StreamAdapter[IndexedEvent](src, client, settings.readBatchSize, _.position.commitPosition))
  }

  def stream(
    connection: ActorRef,
    client: ActorRef,
    streamId: EventStream.Id,
    fromNumberExclusive: Option[EventNumber],
    credentials: Option[UserCredentials],
    settings: Settings
  ): Props = {
    val src: LiveCallback => Source[Event, NotUsed] = cb => Source.fromGraph(
      new StreamSourceStage(connection, streamId, fromNumberExclusive, credentials, settings, infinite = true, cb)
    )
    Props(new StreamAdapter[Event](src, client, settings.readBatchSize, _.record.number.value))
  }

  ///

  private[eventstore] object Fsm {

    sealed trait Fsm[T] {
      def onMessage(msg: T):       State => Result
      def onReady:                 State => Result
      def onLive(ol: OnLive):      State => Result
      def onCompleted:             State => Result
      def onFailure(f: Throwable): State => Result
      def onInitialized:           State => Result
    }

    sealed trait Input
    case object OnInit                               extends Input
    case object OnComplete                           extends Input
    case object OnDownstreamReady                    extends Input
    final case class OnError(ex: Throwable)          extends Input
    final case class OnMessage[T](msg: T, pos: Long) extends Input
    final case class OnLiveMessage(onLive: OnLive)   extends Input

    sealed trait Instruction
    case object NotifyLive                      extends Instruction
    case object CheckReady                      extends Instruction
    case object Stop                            extends Instruction
    case object Ack                             extends Instruction
    final case class LogError(e: Throwable)     extends Instruction
    final case class DeliverError(e: Throwable) extends Instruction
    final case class Deliver[T](msg: T)         extends Instruction

    final case class Data private(
      untilThrottle: Int,
      live: Option[Long],
      lastOut: Option[Long]
    )

    object Data {

      def apply(untilThrottle: Int): Data = Data(math.max(untilThrottle, 1), None, None)

      implicit class DataOps(val d: Data) extends AnyVal {
        def withLastOut(p: Long): Data           = d.copy(lastOut = Some(p))
        def withLive(p: Long): Data              = d.copy(live = Some(p))
        def clearLive: Data                      = d.copy(live = None)
        def decrementThrottle: Data              = d.copy(untilThrottle = d.untilThrottle - 1)
        def resetThrottle(value: Int): Data      = d.copy(untilThrottle = math.max(value, 1))
        def shouldThrottle: Boolean              = d.untilThrottle <= 0
        def hasSeenOut(pos: Long): Boolean       = d.lastOut.exists(pos <= _)
        def shouldNotifyLive(pos: Long): Boolean = d.live.exists(_ <= pos)
      }
    }

    sealed trait State
    case object WaitingOnUpstreamInitialized              extends State
    final case class Running(data: Data)                  extends State
    final case class WaitingOnDownstreamReady(data: Data) extends State

    final case class Result(state: State, instructions: List[Instruction])
    object Result {
      def apply(state: State, instructions: Instruction*): Result    = Result(state, instructions.toList)
      def running(da: (Data, List[Instruction])): Result             = Result(Running(da._1), da._2)
      def waitingOnDownstream(da: (Data, List[Instruction])): Result = Result(WaitingOnDownstreamReady(da._1), da._2)
    }

    final case class UnexpectedInput(msg: String) extends RuntimeException(msg)

    ///

    def unexpected(msg: String, st: State): Result = Result(
      st, LogError(UnexpectedInput(s"Did not expect $msg when being in state $st"))
    )

    def apply[T](position: T => Long, batchSize: Int): Fsm[T] = new Fsm[T] {

      val handleLive: OnLive => Data => (Data, List[Instruction]) = o => d => {

        def clearAndNotify       = (d.clearLive, List(NotifyLive))
        def withLive(pos: Long)  = (d.withLive(pos), Nil)

        o.fold(clearAndNotify, p => d.hasSeenOut(p).fold(clearAndNotify, withLive(p)))
      }

      def step(in: Input): State => Result = in match {
        case OnMessage(m, p) => {
          case Running(d) =>

            val (data, mayBeNotify) =
              d.shouldNotifyLive(p).fold((d.withLastOut(p).clearLive, Some(NotifyLive)), (d.withLastOut(p), None))

            data.shouldThrottle.fold(
              Result(WaitingOnDownstreamReady(data),  Deliver(m) :: CheckReady :: mayBeNotify.toList: _*),
              Result(Running(data.decrementThrottle), Deliver(m) :: Ack        :: mayBeNotify.toList: _*)
            )
          case st => unexpected(s"OnMessage($m, $p)", st)
        }
        case OnDownstreamReady => {
          case WaitingOnDownstreamReady(data) => Result(Running(data.resetThrottle(batchSize)), Ack)
          case st                             => unexpected("OnDownstreamReady", st)
        }
        case OnLiveMessage(o) => {
          case WaitingOnDownstreamReady(d) => Result.waitingOnDownstream(handleLive(o)(d))
          case Running(d)                  => Result.running(handleLive(o)(d))
          case st                          => unexpected(s"OnLiveMessage($o)", st)
        }
        case OnError(se: StageError) => Result(_, LogError(se), Stop)
        case OnError(er)             => Result(_, DeliverError(er), Stop)
        case OnInit => {
          case WaitingOnUpstreamInitialized => Result(Running(Data(batchSize)), Ack)
          case st                           => unexpected("OnInit", st)
        }
        case OnComplete              => Result(_, Ack, Stop)
      }

      def onMessage(msg: T):       State => Result = step(OnMessage(msg, position(msg)))
      def onReady:                 State => Result = step(OnDownstreamReady)
      def onLive(ol: OnLive):      State => Result = step(OnLiveMessage(ol))
      def onFailure(f: Throwable): State => Result = step(OnError(f))
      def onInitialized:           State => Result = step(OnInit)
      def onCompleted:             State => Result = step(OnComplete)
    }
  }
}

private[eventstore] class StreamAdapter[T](
  src:          LiveCallback => Source[T, NotUsed],
  client:       ActorRef,
  batchSize:    Int,
  positionFrom: T ⇒ Long
)(implicit tag: ClassTag[T]) extends Actor with ActorLogging {
  import Fsm._

  private val sink    = Sink.actorRefWithAck[T](self, OnInit, Ack, OnComplete, OnError)
  private val IsReady = Identify("throttle")
  private val Ready   = ActorIdentity("throttle", Some(client))
  private val sm      = Fsm[T](positionFrom, batchSize)

  override def preStart(): Unit = {
    context watch client; src(self ! _).to(sink).run()(ActorMaterializer()); ()
  }

  def receive: Receive = {
    case OnInit => running(sender(), WaitingOnUpstreamInitialized)(OnInit)
  }

  final def running(upstream: ActorRef, st: State): Receive = {

    def run(fn: State => Result): Unit = {

      def handle(a: Instruction): Unit = a match {
        case Deliver(msg)    => client ! msg
        case Ack             => upstream ! Ack
        case CheckReady      => client ! IsReady
        case NotifyLive      => client ! LiveProcessingStarted
        case DeliverError(e) => client ! Failure(e)
        case LogError(e)     => log.error(e.getMessage)
        case Stop            => context stop self
      }

      val result: Result = fn(st)
      result.instructions.foreach(handle)
      context.become(running(upstream, result.state))
    }

    val handler: Receive = {
      case tag(m)     => run(sm.onMessage(m))
      case Ready      => run(sm.onReady)
      case ol: OnLive => run(sm.onLive(ol))
      case OnError(t) => run(sm.onFailure(t))
      case OnInit     => run(sm.onInitialized)
      case OnComplete => run(sm.onCompleted)
    }
    handler
  }
}