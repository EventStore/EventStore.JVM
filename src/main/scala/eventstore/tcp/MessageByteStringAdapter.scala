package eventstore
package tcp

import pipeline._
import EventStoreFormats._
import util.{ BytesWriter, BytesReader }

class MessageByteStringAdapter
    extends PipelineStage[PipelineContext, PackOut, ByteString, PackIn, ByteString] {

  def apply(ctx: PipelineContext) = new PipePair[PackOut, ByteString, PackIn, ByteString] {
    val commandPipeline = (x: PackOut) ⇒ ctx.singleCommand(BytesWriter[PackOut].toByteString(x))
    val eventPipeline = (bs: ByteString) ⇒ ctx.singleEvent(BytesReader[PackIn].read(bs))
  }
}