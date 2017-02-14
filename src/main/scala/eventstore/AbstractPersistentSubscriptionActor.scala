package eventstore

import akka.actor.{ Actor, ActorLogging, ActorRef }
import eventstore.{ PersistentSubscription => PS }

trait AbstractPersistentSubscriptionActor[T] extends Actor with ActorLogging {
  def groupName: String
  def client: ActorRef
  def connection: ActorRef
  def streamId: EventStream
  def credentials: Option[UserCredentials]
  def settings: Settings

  type Next
  type Last

  def toConnection(x: Out) = connection ! credentials.fold[OutLike](x)(x.withCredentials)
  def toClient(event: T) = client ! event
  def subscribeToPersistentStream(): Unit = toConnection(PS.Connect(EventStream.Id(streamId.streamId), groupName))
}
