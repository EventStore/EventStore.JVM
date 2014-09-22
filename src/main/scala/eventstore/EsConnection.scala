package eventstore

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import tcp.ConnectionActor
import java.io.Closeable
import util.ActorCloseable

class EsConnection(
    connection: ActorRef,
    factory: ActorRefFactory,
    operationTimeout: FiniteDuration = Settings.Default.operationTimeout) {
  import scala.concurrent.ExecutionContext.Implicits.global
  implicit val timeout = Timeout(operationTimeout)

  def future[OUT <: Out, IN <: In](out: OUT, credentials: Option[UserCredentials] = None)(
    implicit outIn: OutInTag[OUT, IN]): Future[IN] = {
    val future = connection ? credentials.fold[OutLike](out)(WithCredentials(out, _))
    future.mapTo[IN](outIn.inTag)
  }

  def startTransaction(data: TransactionStart, credentials: Option[UserCredentials] = None): Future[EsTransaction] = {
    val props = TransactionActor.props(connection, TransactionActor.Start(data), credentials = credentials)
    val actor = factory.actorOf(props)
    EsTransaction.start(actor)
  }

  def continueTransaction(transactionId: Long, credentials: Option[UserCredentials] = None): EsTransaction = {
    val props = TransactionActor.props(connection, TransactionActor.Continue(transactionId), credentials = credentials)
    val actor = factory.actorOf(props)
    EsTransaction.continue(transactionId, actor)
  }

  def subscribeToStream(
    streamId: EventStream.Id,
    observer: SubscriptionObserver[Event],
    resolveLinkTos: Boolean = false,
    credentials: Option[UserCredentials] = None): Closeable =
    subscribeToStream(streamId, observer, Some(EventNumber.Last), resolveLinkTos, credentials)

  def subscribeToStreamFrom(
    streamId: EventStream.Id,
    observer: SubscriptionObserver[Event],
    fromNumberExclusive: Option[EventNumber.Exact] = None,
    resolveLinkTos: Boolean = false,
    credentials: Option[UserCredentials] = None): Closeable =
    subscribeToStream(streamId, observer, fromNumberExclusive, resolveLinkTos, credentials)

  private def subscribeToStream(
    streamId: EventStream.Id,
    observer: SubscriptionObserver[Event],
    fromNumberExclusive: Option[EventNumber],
    resolveLinkTos: Boolean,
    credentials: Option[UserCredentials]): Closeable = {
    val client = factory.actorOf(SubscriptionObserverActor.props(observer))
    val props = StreamSubscriptionActor.props(connection, client, streamId, fromNumberExclusive, resolveLinkTos, credentials)
    factory.actorOf(props)
    ActorCloseable(client)
  }

  def subscribeToAll(
    observer: SubscriptionObserver[IndexedEvent],
    resolveLinkTos: Boolean = false,
    credentials: Option[UserCredentials] = None): Closeable =
    subscribeToAll(observer, Some(Position.Last), resolveLinkTos, credentials)

  def subscribeToAllFrom(
    observer: SubscriptionObserver[IndexedEvent],
    fromPositionExclusive: Option[Position.Exact] = None,
    resolveLinkTos: Boolean = false,
    credentials: Option[UserCredentials] = None): Closeable =
    subscribeToAll(observer, fromPositionExclusive, resolveLinkTos, credentials)

  private def subscribeToAll(
    observer: SubscriptionObserver[IndexedEvent],
    fromPositionExclusive: Option[Position],
    resolveLinkTos: Boolean,
    credentials: Option[UserCredentials]) = {
    val client = factory.actorOf(SubscriptionObserverActor.props(observer))
    val props = SubscriptionActor.props(connection, client, fromPositionExclusive, resolveLinkTos)
    factory.actorOf(props)
    ActorCloseable(client)
  }

  def setStreamMetadata(
    streamId: EventStream.Id,
    metadata: Content,
    expectedMetastreamVersion: ExpectedVersion = ExpectedVersion.Any,
    credentials: Option[UserCredentials] = None): Future[Unit] = {
    val writeEvents = WriteEvents.StreamMetadata(streamId.metadata, metadata, expectedMetastreamVersion)
    future(writeEvents, credentials).map(_ => ())
  }

  // TODO think about replacing content with something similar to what is in the .Net client
  def getStreamMetadata(streamId: EventStream.Id, credentials: Option[UserCredentials] = None): Future[Content] = {
    future(ReadEvent.StreamMetadata(streamId.metadata), credentials).map {
      case ReadEventCompleted(Event.StreamMetadata(data)) => data
      case ReadEventCompleted(event) =>
        throw EsException(EsError.NonMetadataEvent(event), Some(s"Non metadata event received $event"))
    }.recover {
      case EsException(EsError.StreamNotFound | EsError.StreamDeleted, _) => Content.Empty
    }
  }
}

object EsConnection {
  def apply(system: ActorSystem, settings: Settings = Settings.Default): EsConnection = new EsConnection(
    connection = system.actorOf(ConnectionActor.props(settings)),
    factory = system,
    operationTimeout = settings.operationTimeout)
}