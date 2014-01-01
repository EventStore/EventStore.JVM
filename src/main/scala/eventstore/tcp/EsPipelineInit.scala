package eventstore
package tcp

import akka.event.LoggingAdapter
import akka.io.TcpPipelineHandler.{ Init, WithinActorContext }
import akka.io.{ TcpPipelineHandler, LengthFieldFrame, TcpReadWriteAdapter, BackpressureBuffer }
import java.nio.ByteOrder

object EsPipelineInit {
  def apply(
    log: LoggingAdapter,
    backpressureSettings: BackpressureSettings): Init[WithinActorContext, TcpPackageOut, TcpPackageIn] =
    TcpPipelineHandler.withLogger(log,
      new MessageByteStringAdapter >>
        new LengthFieldFrame(
          maxSize = 64 * 1024 * 1024,
          byteOrder = ByteOrder.LITTLE_ENDIAN,
          lengthIncludesHeader = false) >>
        new TcpReadWriteAdapter >>
        new BackpressureBuffer(
          lowBytes = backpressureSettings.lowWatermark,
          highBytes = backpressureSettings.highWatermark,
          maxBytes = backpressureSettings.maxCapacity))
}
