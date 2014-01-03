package eventstore

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.Future

class EsConnection(connection: ActorRef, settings: Settings = Settings.Default, factory: ActorRefFactory) {
  implicit val timeout = Timeout(settings.responseTimeout)

  def future[OUT <: Out, IN <: In](out: OUT, credentials: Option[UserCredentials] = settings.defaultCredentials)(
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