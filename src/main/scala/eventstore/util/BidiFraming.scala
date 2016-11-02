package eventstore.util

import java.nio.ByteOrder

import akka.NotUsed
import akka.stream.scaladsl.Framing.FramingException
import akka.stream.scaladsl.{ BidiFlow, Flow, Framing, Keep }
import akka.util.ByteString

object BidiFraming {
  def apply(fieldLength: Int, maxFrameLength: Int)(implicit byteOrder: ByteOrder): BidiFlow[ByteString, ByteString, ByteString, ByteString, NotUsed] = {

    val decode = Flow[ByteString].map { byteString =>
      val length = byteString.length
      if (length >= maxFrameLength) {
        throw new FramingException(s"Maximum allowed message size is $maxFrameLength but tried to send $length bytes")
      } else {
        val header = ByteString.newBuilder.putLongPart(length.toLong, fieldLength).result()
        header ++ byteString
      }
    }

    val encode = Framing.lengthField(fieldLength, 0, maxFrameLength, byteOrder) map { _ drop fieldLength }

    BidiFlow.fromFlowsMat(encode, decode)(Keep.left)
  }
}
