package eventstore

/**
 * @author Yaroslav Klymko
 */
case class MarkerByte[T <: Message](byte: Byte)

object MarkerByte {

  def marker[T <: Message](byte: Byte): MarkerByte[T] = MarkerByte[T](byte)

  def marker[T <: Message](byte: Int): MarkerByte[T] = marker[T](byte.toByte)

  implicit val heartbeatRequestCommandMarker = marker[HeartbeatRequestCommand.type](0x01)
  implicit val heartbeatResponseCommandMarker = marker[HeartbeatResponseCommand.type](0x02)

  implicit val pingMarker = marker[Ping.type](0x03)
  implicit val pongMarker = marker[Pong.type](0x04)

  implicit val prepareAckMarker = marker[PrepareAck.type](0x05)
  implicit val commitAckMarker = marker[CommitAck.type](0x06)

  implicit val slaveAssignmentMarker = marker[SlaveAssignment.type](0x07)
  implicit val cloneAssignmentMarker = marker[CloneAssignment.type](0x08)



//  SubscribeReplica = 0x10,
//  CreateChunk = 0x11,
//  PhysicalChunkBulk = 0x12,
//  LogicalChunkBulk = 0x13,
//
//  // CLIENT COMMANDS
//  CreateStream = 0x80,
//  CreateStreamCompleted = 0x81,
//
//  WriteEvents = 0x82,
//  WriteEventsCompleted = 0x83,
//
//  TransactionStart = 0x84,
//  TransactionStartCompleted = 0x85,
//  TransactionWrite = 0x86,
//  TransactionWriteCompleted = 0x87,
//  TransactionCommit = 0x88,
//  TransactionCommitCompleted = 0x89,
//
//  DeleteStream = 0x8A,
//  DeleteStreamCompleted = 0x8B,
//
//  ReadEvent = 0xB0,
//  ReadEventCompleted = 0xB1,
//  ReadStreamEventsForward = 0xB2,
//  ReadStreamEventsForwardCompleted = 0xB3,
//  ReadStreamEventsBackward = 0xB4,
//  ReadStreamEventsBackwardCompleted = 0xB5,
//  ReadAllEventsForward = 0xB6,
//  ReadAllEventsForwardCompleted = 0xB7,
//  ReadAllEventsBackward = 0xB8,
//  ReadAllEventsBackwardCompleted = 0xB9,
//
//  SubscribeToStream = 0xC0,
//  SubscriptionConfirmation = 0xC1,
//  StreamEventAppeared = 0xC2,
//  UnsubscribeFromStream = 0xC3,
//  SubscriptionDropped = 0xC4,
//
//  ScavengeDatabase = 0xD0,
//
implicit val badRequestMarker = marker[BadRequest.type](0xF0)
//  DeniedToRoute = 0xF1
}





