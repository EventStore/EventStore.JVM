package eventstore
package akka

import _root_.akka.actor.Status.Failure
import _root_.akka.actor.{ActorRef, FSM, Props, Terminated}
import eventstore.{PersistentSubscription => PS}
import eventstore.PersistentSubscription.Nak.Action.Retry
import eventstore.PersistentSubscription.{Ack, Nak}
import eventstore.akka.{PersistentSubscriptionActor => PSA}

object PersistentSubscriptionActor {

  def props(
    connection:  ActorRef,
    client:      ActorRef,
    streamId:    EventStream.Id,
    groupName:   String,
    credentials: Option[UserCredentials],
    settings:    Settings,
    autoAck:     Boolean                 = true
  ): Props = {
    Props(new PersistentSubscriptionActor(
      connection,
      client,
      streamId,
      groupName,
      credentials,
      settings,
      autoAck
    ))
  }

  sealed trait State

  private case object Unsubscribed extends State

  private case object LiveProcessing extends State

  private case object CatchingUp extends State

  sealed trait Data
  private final case object ConnectionDetails
    extends Data
  private final case class SubscriptionDetails(subscriptionId: String, lastEventNum: Option[EventNumber.Exact])
    extends Data

  final case class ManualAck(eventId: Uuid)
  final case class ManualNak(eventId: Uuid)
}

private[eventstore] class PersistentSubscriptionActor private (
    val connection:  ActorRef,
    val client:      ActorRef,
    val streamId:    EventStream,
    val groupName:   String,
    val credentials: Option[UserCredentials],
    val settings:    Settings,
    val autoAck:     Boolean
) extends AbstractPersistentSubscriptionActor[Event] with FSM[PSA.State, PSA.Data] {

  context watch client
  context watch connection

  type Next = EventNumber.Exact
  type Last = Option[EventNumber.Exact]

  private def connectionDetails = PSA.ConnectionDetails

  private def subscriptionDetails(subId: String, lastEventNum: Last): PSA.SubscriptionDetails =
    PSA.SubscriptionDetails(subId, lastEventNum)

  def getEventId(e: eventstore.Event): Uuid = e match {
    case x: ResolvedEvent => x.linkEvent.data.eventId
    case x                => x.data.eventId
  }

  startWith(PSA.Unsubscribed, connectionDetails)

  onTransition {
    case _ -> PSA.Unsubscribed => subscribeToPersistentStream() // try to (re-)connect.
    case _ -> PSA.LiveProcessing                       => client ! LiveProcessingStarted
  }

  when(PSA.Unsubscribed) {
    case Event(PS.Connected(subId, _, eventNum), _) =>
      val subDetails = subscriptionDetails(subId, eventNum)
      eventNum match {
        case None => goto(PSA.LiveProcessing) using subDetails
        case _    => goto(PSA.CatchingUp) using subDetails
      }
    // Ignore events sent while unsubscribed
    case Event(PS.EventAppeared(_), _) =>
      stay
  }

  when(PSA.LiveProcessing) {
    case Event(PS.EventAppeared(event), details: PSA.SubscriptionDetails) =>
      if (autoAck) toConnection(Ack(details.subscriptionId, getEventId(event) :: Nil))
      client ! event
      stay
    case Event(PSA.ManualAck(eventId), details: PSA.SubscriptionDetails) =>
      toConnection(Ack(details.subscriptionId, eventId :: Nil))
      stay
    case Event(PSA.ManualNak(eventId), details: PSA.SubscriptionDetails) =>
      toConnection(Nak(details.subscriptionId, List(eventId), Retry, None))
      stay
  }

  when(PSA.CatchingUp) {
    case Event(PS.EventAppeared(event), details: PSA.SubscriptionDetails) =>
      if (autoAck) toConnection(Ack(details.subscriptionId, getEventId(event) :: Nil))
      client ! event
      if (details.lastEventNum.exists(_ <= event.number)) goto(PSA.LiveProcessing) using details
      else stay
    case Event(PSA.ManualAck(eventId), details: PSA.SubscriptionDetails) =>
      toConnection(Ack(details.subscriptionId, eventId :: Nil))
      stay
    case Event(PSA.ManualNak(eventId), details: PSA.SubscriptionDetails) =>
      toConnection(Nak(details.subscriptionId, List(eventId), Retry, None))
      stay
  }

  whenUnhandled {
    // If a reconnect is launched in LiveProcessing or CatchingUp, then renew subId
    case Event(PS.Connected(subId, _, eventNum), _) =>
      stay using subscriptionDetails(subId, eventNum)
    // Error conditions
    // This handles when the client or connection is terminated (unrecoverable)
    case Event(Terminated(_), _) =>
      stop()
    // This handles when a generic error has occurred
    case failure @ Event(Failure(e), _) =>
      log.error(e.toString)
      client ! failure
      stop()
    // This is when the subscription is dropped.
    case Event(PSA.Unsubscribed, _) =>
      stop()
    case Event(e, s) =>
      log.warning(s"Received unhandled $e in state $stateName with state $s")
      stay
  }

  initialize()
}
