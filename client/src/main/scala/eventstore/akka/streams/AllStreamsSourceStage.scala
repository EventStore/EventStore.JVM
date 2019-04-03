package eventstore
package akka
package streams

import _root_.akka.actor.Actor.Receive
import _root_.akka.actor.ActorRef
import _root_.akka.stream.stage._
import _root_.akka.stream.{Attributes, SourceShape, _}
import EventStream.All
import Position.{Exact, First, Last}
import ReadDirection.Forward
import SourceStageLogic.LiveCallback

private[eventstore] class AllStreamsSourceStage(
    connection:            ActorRef,
    fromPositionExclusive: Option[Position],
    credentials:           Option[UserCredentials],
    settings:              Settings,
    infinite:              Boolean                 = true,
    onLiveProcessing:      LiveCallback            = _ => ()
) extends GraphStage[SourceShape[IndexedEvent]] {

  val out: Outlet[IndexedEvent] = Outlet("AllStreamsSource")
  val shape: SourceShape[IndexedEvent] = SourceShape(out)

  def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new SourceStageLogic[IndexedEvent, Position, Exact](
      shape, out, All, connection, credentials, settings, infinite, onLiveProcessing
    ) {

      import settings._

      final val first: Exact = First
      final val eventFrom: IndexedEvent ⇒ IndexedEvent = identity
      final val positionFrom: IndexedEvent ⇒ Exact = _.position
      final val pointerFrom: Exact ⇒ Long = _.commitPosition

      final def operation: ReadFrom = fromPositionExclusive match {
        case Some(Last)     ⇒ ReadFrom.End
        case Some(e: Exact) ⇒ ReadFrom.Exact(e)
        case None           ⇒ ReadFrom.Beginning
      }

      final def buildReadEventsFrom(next: Exact): Out = ReadAllEvents(
        next, readBatchSize, Forward, resolveLinkTos, requireMaster
      )

      final def rcvRead(onRead: (List[IndexedEvent], Exact, Boolean) => Unit, onNotExists: => Unit): Receive = {
        case ReadAllEventsCompleted(events, _, n, Forward) ⇒ onRead(events, n, events.isEmpty)
      }

      final def rcvSubscribed(onSubscribed: Option[Exact] ⇒ Unit): Receive = {
        case SubscribeToAllCompleted(x) ⇒ onSubscribed(Some(Position.Exact(x)))
      }

    }
}