package eventstore
import akka.actor.Status.Failure
import akka.actor.{ ActorRef, FSM, Props, Terminated }
import eventstore.PersistentSubscription.Ack
import eventstore.{ PersistentSubscription => PS }

object PersistentSubscriptionState {

  sealed trait State

  case object Unsubscribed extends State

  case object LiveProcessing extends State

  case object CatchingUp extends State

}

sealed trait Data
final case class ConnectionDetails(connection: ActorRef, client: ActorRef, stream: EventStream, groupName: String,
                                   credentials: Option[UserCredentials], settings: Settings, autoAck: Boolean)
    extends Data
final case class SubscriptionDetails(connection: ActorRef, client: ActorRef, stream: EventStream, groupName: String,
                                     credentials: Option[UserCredentials], settings: Settings, autoAck: Boolean,
                                     subscriptionId: String, lastEventNum: Option[EventNumber.Exact])
    extends Data

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
}

class PersistentSubscriptionActor private (
    val connection:  ActorRef,
    val client:      ActorRef,
    val streamId:    EventStream,
    val groupName:   String,
    val credentials: Option[UserCredentials],
    val settings:    Settings,
    val autoAck:     Boolean
) extends AbstractPersistentSubscriptionActor[Event] with FSM[PersistentSubscriptionState.State, Data] {
  import eventstore.{ PersistentSubscriptionState => PersistentState }

  context watch client
  context watch connection

  type Next = EventNumber.Exact
  type Last = Option[EventNumber.Exact]

  def connectionDetails: ConnectionDetails = ConnectionDetails(connection, client, streamId, groupName, credentials, settings,
    autoAck)

  def subscriptionDetails(subId: String, lastEventNum: Last): SubscriptionDetails = SubscriptionDetails(
    connection,
    client, streamId, groupName, credentials, settings, autoAck, subId, lastEventNum
  )

  startWith(PersistentState.Unsubscribed, connectionDetails)

  onTransition {
    case _ -> PersistentState.Unsubscribed =>
      subscribeToPersistentStream() // try to (re-)connect.
    case _ -> PersistentState.LiveProcessing =>
      client ! LiveProcessingStarted
  }

  when(PersistentState.Unsubscribed) {
    case Event(PS.Connected(subId, _, eventNum), _) =>
      val subDetails = subscriptionDetails(subId, eventNum)
      eventNum match {
        case None => goto(PersistentState.LiveProcessing) using subDetails
        case _    => goto(PersistentState.CatchingUp) using subDetails
      }
    // Ignore events sent while unsubscribed
    case Event(PS.EventAppeared(_), _) =>
      stay
  }

  when(PersistentState.LiveProcessing) {
    case Event(PS.EventAppeared(event), details: SubscriptionDetails) =>
      if (autoAck) toConnection(Ack(details.subscriptionId, event.data.eventId :: Nil)) // TODO possibly batching acks?
      client ! event
      stay
  }

  when(PersistentState.CatchingUp) {
    case Event(PS.EventAppeared(event), details: SubscriptionDetails) =>
      if (autoAck) toConnection(Ack(details.subscriptionId, event.data.eventId :: Nil))
      client ! event
      if (details.lastEventNum.exists(_ <= event.number)) goto(PersistentState.LiveProcessing) using details
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
      log.warning(s"Received unhandled $e in state $stateName")
      stay
  }

  initialize()
}
