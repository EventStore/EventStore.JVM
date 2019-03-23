package eventstore
package akka
package tcp

import java.nio.ByteOrder
import _root_.akka.NotUsed
import _root_.akka.stream.scaladsl.Framing.FramingException
import _root_.akka.stream.scaladsl.{BidiFlow, Flow, Framing, Keep}
import _root_.akka.util.{ByteString => AKS}

private[eventstore] object BidiFraming {

  def apply(fieldLength: Int, maxFrameLength: Int)(implicit byteOrder: ByteOrder): BidiFlow[AKS, AKS, AKS, AKS, NotUsed] = {

    val decode = Flow[AKS].map { byteString =>
      val length = byteString.length
      if (length >= maxFrameLength) {
        throw new FramingException(s"Maximum allowed message size is $maxFrameLength but tried to send $length bytes")
      } else {
        val header = AKS.newBuilder.putLongPart(length.toLong, fieldLength).result()
        header ++ byteString
      }
    }

    val encode = Framing.lengthField(fieldLength, 0, maxFrameLength, byteOrder) map { _ drop fieldLength }

    BidiFlow.fromFlowsMat(encode, decode)(Keep.left) named "framing"
  }
}
