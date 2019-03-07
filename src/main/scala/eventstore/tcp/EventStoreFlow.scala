package eventstore
package tcp

import java.nio.ByteOrder
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import akka.NotUsed
import akka.util.{ByteString => ABS}
import akka.event.LoggingAdapter
import akka.stream.scaladsl._
import eventstore.tcp.EventStoreFormats._
import eventstore.util.{BidiFraming, BidiLogging, BytesReader, BytesWriter}
import scodec.bits.ByteVector

object EventStoreFlow {

  def apply(
    heartbeatInterval: FiniteDuration,
    parallelism:       Int,
    ordered:           Boolean,
    log:               LoggingAdapter
  )(implicit ec: ExecutionContext): BidiFlow[ABS, PackIn, PackOut, ABS, NotUsed] = {

    val incoming = Flow[ByteVector]
      .mapFuture(ordered, parallelism)(BytesReader[PackIn].read(_).unsafe.value)

    val outgoing = Flow[PackOut]
      .mapFuture(ordered, parallelism)(BytesWriter[PackOut].write)
      .keepAlive(heartbeatInterval, () => BytesWriter[PackOut].write(PackOut(HeartbeatRequest)))

    val autoReply = {

      def reply(message: Out, bv: ByteVector): ByteVector = {

        val packIn  = BytesReader[PackIn].read(bv).unsafe.value
        val packOut = PackOut(message, packIn.correlationId)

        BytesWriter[PackOut].write(packOut)
      }

      BidiReply[ByteVector, ByteVector] {
        case x if x.head == 0x01 => reply(HeartbeatResponse, x)
        case x if x.head == 0x03 => reply(Pong, x)
      }
    }

    val framing       = BidiFraming(fieldLength = 4, maxFrameLength = 64 * 1024 * 1024)(ByteOrder.LITTLE_ENDIAN)
    val convert       = BidiFlow.fromFunctions((bs: ABS) => ByteVector.view(bs.toArray), (bv: ByteVector) => ABS(bv.toArray))
    val serialization = BidiFlow.fromFlows(incoming, outgoing)
    val logging       = BidiLogging(log)

    framing atop convert atop autoReply atop serialization atop logging
  }

  private implicit class FlowOps[In](self: Flow[In, In, NotUsed]) {
    def mapFuture[Out](ordered: Boolean, parallelism: Int)(f: In => Out)(implicit ec: ExecutionContext): Flow[In, Out, NotUsed] = {
      if (ordered) self.mapAsync(parallelism) { x => Future { f(x) } }
      else self.mapAsyncUnordered(parallelism) { x => Future { f(x) } }
    }
  }
}