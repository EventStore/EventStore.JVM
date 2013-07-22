package eventstore
package tcp

import akka.io._

/**
 * @author Yaroslav Klymko
 */
class MessageByteStringAdapter extends PipelineStage[PipelineContext, TcpPackage[Out], ByteString, TcpPackage[In], ByteString] {
  def apply(ctx: PipelineContext) = new PipePair[TcpPackage[Out], ByteString, TcpPackage[In], ByteString] {
    val commandPipeline = (out: TcpPackage[Out]) ⇒ ctx.singleCommand(out.serialize)
    val eventPipeline = (bs: ByteString) ⇒ ctx.singleEvent(TcpPackage.deserialize(bs))
  }
}