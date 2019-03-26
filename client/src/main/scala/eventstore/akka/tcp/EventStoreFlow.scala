package eventstore
package akka
package tcp

import java.nio.ByteOrder
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import _root_.akka.NotUsed
import _root_.akka.util.{ByteString => ABS}
import _root_.akka.event.LoggingAdapter
import _root_.akka.stream.scaladsl._
import scodec.bits.ByteVector
import eventstore.tcp._
import eventstore.tcp.EventStoreFormats._
import eventstore.syntax._

private[eventstore] object EventStoreFlow {

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

  private implicit class FlowOps[I](self: Flow[I, I, NotUsed]) {
    def mapFuture[O](ordered: Boolean, parallelism: Int)(f: I => O)(implicit ec: ExecutionContext): Flow[I, O, NotUsed] = {
      if (ordered) self.mapAsync(parallelism) { x => Future { f(x) } }
      else self.mapAsyncUnordered(parallelism) { x => Future { f(x) } }
    }
  }
}