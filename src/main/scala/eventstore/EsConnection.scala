package eventstore

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.Future

class EsConnection(connection: ActorRef, settings: Settings = Settings.Default, factory: ActorRefFactory) {
  implicit val timeout = Timeout(settings.responseTimeout)

  def future[OUT <: Out, IN <: In](out: OUT)(
    implicit outIn: OutInTag[OUT, IN],
    credentials: Option[UserCredentials] = settings.defaultCredentials): Future[IN] = {

    val future = connection ? credentials.fold[OutLike](out)(WithCredentials(out, _))
    future.mapTo[IN](outIn.inTag)
  }

  def startTransaction(data: TransactionStart)(implicit credentials: Option[UserCredentials] = settings.defaultCredentials): Future[EsTransaction] = {
    val props = TransactionActor.props(connection, TransactionActor.Start(data))
    val actor = factory.actorOf(props)
    EsTransaction.start(actor)
  }

  def continueTransaction(transactionId: Long)(implicit credentials: Option[UserCredentials] = settings.defaultCredentials): EsTransaction = {
    val props = TransactionActor.props(connection, TransactionActor.Continue(transactionId))
    val actor = factory.actorOf(props)
    EsTransaction.continue(transactionId, actor)
  }
}