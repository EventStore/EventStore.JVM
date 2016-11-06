package eventstore
package tcp

import java.nio.ByteOrder

import akka.NotUsed
import akka.event.LoggingAdapter
import akka.stream.scaladsl._
import eventstore.tcp.EventStoreFormats._
import eventstore.util.{ BidiFraming, BidiLogging, BytesReader, BytesWriter }

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }

object EventStoreFlow {

  def apply(
    heartbeatInterval: FiniteDuration,
    parallelism:       Int,
    log:               LoggingAdapter
  )(implicit ec: ExecutionContext): BidiFlow[ByteString, PackIn, PackOut, ByteString, NotUsed] = {

    val incoming = Flow[ByteString]
      .mapAsync(parallelism) { x => Future { BytesReader[PackIn].read(x) } }

    val outgoing = Flow[PackOut]
      .keepAlive(heartbeatInterval, () => PackOut(HeartbeatRequest))
      .mapAsync(parallelism) { x => Future { BytesWriter[PackOut].toByteString(x) } }

    val autoReply = {
      def reply(message: Out, byteString: ByteString): ByteString = {
        val packIn = BytesReader[PackIn].read(byteString)
        val packOut = PackOut(message, packIn.correlationId)
        BytesWriter[PackOut].toByteString(packOut)
      }

      BidiReply[ByteString, ByteString] {
        case x if x.head == 0x01 => reply(HeartbeatResponse, x)
        case x if x.head == 0x03 => reply(Pong, x)
      }
    }

    val framing = BidiFraming(fieldLength = 4, maxFrameLength = 64 * 1024 * 1024)(ByteOrder.LITTLE_ENDIAN)

    val serialization = BidiFlow.fromFlows(incoming, outgoing)

    val logging = BidiLogging(log)

    framing atop autoReply atop serialization atop logging
  }
}