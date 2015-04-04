package eventstore

import eventstore.{ PersistentSubscription => PS }

trait AbstractPersistentSubscriptionActor[T] extends AbstractSubscriptionActor[T] {
  def groupName: String

  def subscribeToPersistentStream(): Unit = toConnection(PS.Connect(EventStream.Id(streamId.streamId), groupName))
}
