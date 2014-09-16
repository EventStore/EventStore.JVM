package eventstore

import scala.reflect.ClassTag

sealed trait OutInTag[OUT, IN] {
  def outTag: ClassTag[OUT]
  def inTag: ClassTag[IN]
}

object OutInTag {
  abstract class AbstractTag[OUT, IN](
    implicit val outTag: ClassTag[OUT],
    val inTag: ClassTag[IN]) extends OutInTag[OUT, IN]

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