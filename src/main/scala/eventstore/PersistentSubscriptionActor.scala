package eventstore
import akka.actor.Status.Failure
import akka.actor.{ ActorRef, FSM, Props, Terminated }
import eventstore.PersistentSubscription.Ack
import eventstore.PersistentSubscriptionActor._
import eventstore.{ PersistentSubscription => PS }

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
}

class PersistentSubscriptionActor private (
    val connection:  ActorRef,
    val client:      ActorRef,
    val streamId:    EventStream,
    val groupName:   String,
    val credentials: Option[UserCredentials],
    val settings:    Settings,
    val autoAck:     Boolean
) extends AbstractPersistentSubscriptionActor[Event] with FSM[State, Data] {

  context watch client
  context watch connection

  type Next = EventNumber.Exact
  type Last = Option[EventNumber.Exact]

  private def connectionDetails = ConnectionDetails

  private def subscriptionDetails(subId: String, lastEventNum: Last): SubscriptionDetails = SubscriptionDetails(
    subId, lastEventNum
  )

  startWith(PersistentSubscriptionActor.Unsubscribed, connectionDetails)

  onTransition {
    case _ -> PersistentSubscriptionActor.Unsubscribed =>
      subscribeToPersistentStream() // try to (re-)connect.
    case _ -> LiveProcessing =>
      client ! LiveProcessingStarted
  }

  when(PersistentSubscriptionActor.Unsubscribed) {
    case Event(PS.Connected(subId, _, eventNum), _) =>
      val subDetails = subscriptionDetails(subId, eventNum)
      eventNum match {
        case None => goto(LiveProcessing) using subDetails
        case _    => goto(CatchingUp) using subDetails
      }
    // Ignore events sent while unsubscribed
    case Event(PS.EventAppeared(_), _) =>
      stay
  }

  when(LiveProcessing) {
    case Event(PS.EventAppeared(event), details: SubscriptionDetails) =>
      if (autoAck) toConnection(Ack(details.subscriptionId, event.data.eventId :: Nil))
      client ! event
      stay
  }

  when(CatchingUp) {
    case Event(PS.EventAppeared(event), details: SubscriptionDetails) =>
      if (autoAck) toConnection(Ack(details.subscriptionId, event.data.eventId :: Nil))
      client ! event
      if (details.lastEventNum.exists(_ <= event.number)) goto(LiveProcessing) using details
      else stay
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
    case Event(Unsubscribed, _) =>
      stop()
    case Event(e, s) =>
      log.warning(s"Received unhandled $e in state $stateName with state $s")
      stay
  }

  initialize()
}
