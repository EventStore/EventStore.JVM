package eventstore

import scala.concurrent.Future
import akka.util.Timeout
import akka.actor.ActorRef
import akka.pattern.ask

/**
 * @author Yaroslav Klymko
 */
class EventStoreConnection(connection: ActorRef, settings: Settings = Settings.Default) {

  def futureIn[OUT <: Out, IN <: In](out: OUT)(
    implicit outIn: OutInTag[OUT, IN],
    credentials: Option[UserCredentials] = settings.defaultCredentials,
    timeout: Timeout = Timeout(settings.responseTimeout)): Future[IN] = {

    val future = connection ? credentials.fold[OutLike](out)(WithCredentials(out, _))
    future.mapTo[IN](outIn.inTag)
  }
}