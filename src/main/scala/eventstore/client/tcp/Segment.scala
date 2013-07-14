package eventstore.client.tcp


import java.nio.ByteOrder.LITTLE_ENDIAN
import eventstore.client._


/**
 * @author Yaroslav Klymko
 */
case class Segment(correlationId: Uuid, data: ByteString)

object Segment {
  val lengthHeaderLength = 4
  val lengthLimit = 64 * 1024 * 1024

  def sizeToBytes(size: Int): Bytes = {
    val buffer = java.nio.ByteBuffer.allocate(4)
    buffer.putInt(Integer.reverseBytes(size))
    buffer.array()

    //    Array(size, size >> 8, size >> 16, size >> 24).map(_.toByte)
  }


  def serialize(packages: Vector[TcpPackage[Out]]) {

  }

  def deserialize(data: ByteString, onPackage: TcpPackage[In] => Any): ByteString = {
    def deserializePackage(data: ByteString) {
      val tcpPackage = TcpPackage.deserialize(data)
      onPackage(tcpPackage)
    }

    def apply(data: ByteString): ByteString = {

      if (data.length <= lengthHeaderLength) data
      else {
        val (lengthData, tail) = data.splitAt(lengthHeaderLength)
        val segmentLength = lengthData.iterator.getInt(LITTLE_ENDIAN)

        require(
          segmentLength <= lengthLimit,
          s"Reported segment length is out of limit, length: $segmentLength, limit: $lengthLimit")

        tail.length compare segmentLength match {
          case 0 =>
            deserializePackage(tail)
            ByteString.empty

          case -1 => data

          case 1 =>
            val (segment, rest) = tail.splitAt(segmentLength)
            deserializePackage(segment)
            apply(rest)
        }
      }
    }
    apply(data)
  }
}
