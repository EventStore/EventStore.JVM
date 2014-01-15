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
    defaultCredentials: Option[UserCredentials] = Settings.Default.defaultCredentials,
    responseTimeout: FiniteDuration = Settings.Default.responseTimeout) {
  implicit val timeout = Timeout(responseTimeout)

  def future[OUT <: Out, IN <: In](out: OUT, credentials: Option[UserCredentials] = defaultCredentials)(
    implicit outIn: OutInTag[OUT, IN]): Future[IN] = {

    val future = connection ? credentials.fold[OutLike](out)(WithCredentials(out, _))
    future.mapTo[IN](outIn.inTag)
  }

  def startTransaction(data: TransactionStart): Future[EsTransaction] = {
    val props = TransactionActor.props(connection, TransactionActor.Start(data))
    val actor = factory.actorOf(props)
    EsTransaction.start(actor)
  }

  def continueTransaction(transactionId: Long): EsTransaction = {
    val props = TransactionActor.props(connection, TransactionActor.Continue(transactionId))
    val actor = factory.actorOf(props)
    EsTransaction.continue(transactionId, actor)
  }

  def subscribeToStream(
    streamId: EventStream.Id,
    observer: SubscriptionObserver[Event],
    resolveLinkTos: Boolean = false): Closeable = {
    val client = factory.actorOf(SubscriptionObserverActor.props(observer))
    val props = StreamSubscriptionActor.props(connection, client, streamId, Some(EventNumber.Last), resolveLinkTos)
    factory.actorOf(props)
    ActorCloseable(client)
  }

  def subscribeToStreamFrom(
    streamId: EventStream.Id,
    observer: SubscriptionObserver[Event],
    fromNumberExclusive: Option[EventNumber.Exact] = None,
    resolveLinkTos: Boolean = false): Closeable = {
    val client = factory.actorOf(SubscriptionObserverActor.props(observer))
    val props = StreamSubscriptionActor.props(connection, client, streamId, fromNumberExclusive, resolveLinkTos)
    factory.actorOf(props)
    ActorCloseable(client)
  }
}

object EsConnection {
  def apply(system: ActorSystem, settings: Settings = Settings.Default): EsConnection = {
    val connection = system.actorOf(ConnectionActor.props(settings))
    new EsConnection(
      connection = connection,
      factory = system,
      defaultCredentials = settings.defaultCredentials,
      responseTimeout = settings.responseTimeout)
  }
}

trait SubscriptionObserver[T] {
  def onLiveProcessingStart(subscription: Closeable)
  def onEvent(event: T, subscription: Closeable)
  def onError(e: Throwable)
  def onClose()
}