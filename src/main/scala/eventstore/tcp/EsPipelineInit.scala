package eventstore
package tcp

import akka.event.LoggingAdapter
import pipeline.TcpPipelineHandler.{ Init, WithinActorContext }
import pipeline.{ TcpPipelineHandler, LengthFieldFrame, TcpReadWriteAdapter, BackpressureBuffer }
import java.nio.ByteOrder

object EsPipelineInit {
  def apply(
    log: LoggingAdapter,
    settings: BackpressureSettings): Init[WithinActorContext, PackOut, PackIn] =
    TcpPipelineHandler.withLogger(log,
      new MessageByteStringAdapter >>
        new LengthFieldFrame(
          maxSize = 64 * 1024 * 1024,
          byteOrder = ByteOrder.LITTLE_ENDIAN,
          lengthIncludesHeader = false) >>
        new TcpReadWriteAdapter >>
        new BackpressureBuffer(
          lowBytes = settings.lowWatermark,
          highBytes = settings.highWatermark,
          maxBytes = settings.maxCapacity))
}
