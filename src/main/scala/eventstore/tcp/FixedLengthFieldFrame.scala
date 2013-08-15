package eventstore
package tcp

import java.nio.ByteOrder
import akka.io.{SymmetricPipePair, PipelineContext, LengthFieldFrame}

/**
 * @author Yaroslav Klymko
 */
class FixedLengthFieldFrame(maxSize: Int,
                            byteOrder: ByteOrder = ByteOrder.BIG_ENDIAN,
                            lengthIncludesHeader: Boolean = true)
  extends LengthFieldFrame(maxSize, byteOrder, 4, lengthIncludesHeader) {

  override def apply(ctx: PipelineContext) = {
    val superPipePair = super.apply(ctx)

    new SymmetricPipePair[ByteString, ByteString] {
      implicit val byteOrder = FixedLengthFieldFrame.this.byteOrder

      def eventPipeline = superPipePair.eventPipeline

      override def commandPipeline = {
        bs: ByteString =>
          val length = if (lengthIncludesHeader) bs.length + 4 else bs.length
          if (length > maxSize) Seq()
          else {
            val bb = ByteString.newBuilder
            bb.putInt(length)
            bb ++= bs
            ctx.singleCommand(bb.result())
          }
      }
    }
  }
}