package eventstore.client.tcp



import eventstore.client._
import java.nio.ByteOrder

/**
 * @author Yaroslav Klymko
 */
case class TcpPackage[T <: Message](correlationId: Uuid, message: T) {

  def serialize(implicit ev: T <:< Out): Bytes = {
    val (markerByte, bytes) = Serializers.serialize(message)
    //      Message.serialize(message)(x => Deserializers.serialize(x))
    Array[Byte](markerByte) ++ UuidSerializer.serialize(correlationId) ++ bytes // TODO improve perf
    //    ???
  }
}


object TcpPackage {
  def apply[T <: Message](message: T): TcpPackage[T] = TcpPackage(newUuid, message)

  def deserialize(bs: ByteString): TcpPackage[In] = {
    val length = bs.length
    require(length > 16, s"Segment is too short, length: $length")

    implicit val byteOrder = ByteOrder.BIG_ENDIAN

    val markerByte = bs.head

    val deserializers = Deserializers.deserialize(markerByte)
    val uuid = UuidSerializer.deserialize(bs.drop(1).take(16))
    val command = Message.deserialize(bs.drop(17))(x => deserializers(x.asByteBuffer))
    //      val command = toCommand(buffer)
    TcpPackage(uuid, command)
    //      ???
  }
}
