package eventstore

import scala.reflect.ClassTag

sealed trait ClassTags[O, I] {
  def out: ClassTag[O]
  def in: ClassTag[I]
}

object ClassTags {
  abstract class AbstractTag[O, I](implicit val out: ClassTag[O], val in: ClassTag[I]) extends ClassTags[O, I]

  implicit object PingPongTag extends AbstractTag[Ping.type, Pong.type]
  implicit object HeartbeatTag extends AbstractTag[HeartbeatRequest.type, HeartbeatResponse.type]
  implicit object WriteEventsTag extends AbstractTag[WriteEvents, WriteEventsCompleted]
  implicit object DeleteStreamTag extends AbstractTag[DeleteStream, DeleteStreamCompleted]
  implicit object TransactionStartTag extends AbstractTag[TransactionStart, TransactionStartCompleted]
  implicit object TransactionWriteTag extends AbstractTag[TransactionWrite, TransactionWriteCompleted]
  implicit object TransactionCommitTag extends AbstractTag[TransactionCommit, TransactionCommitCompleted]
  implicit object ReadEventTag extends AbstractTag[ReadEvent, ReadEventCompleted]
  implicit object ReadStreamEventsTag extends AbstractTag[ReadStreamEvents, ReadStreamEventsCompleted]
  implicit object ReadAllEventsTag extends AbstractTag[ReadAllEvents, ReadAllEventsCompleted]
  implicit object SubscribeToTag extends AbstractTag[SubscribeTo, SubscribeCompleted]
  implicit object InOutTag extends AbstractTag[In, Out]
}