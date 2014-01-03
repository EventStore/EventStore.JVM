package eventstore

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import tcp.ConnectionActor

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