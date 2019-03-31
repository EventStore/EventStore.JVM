package eventstore
package akka
package tcp

import scala.util.Success
import _root_.akka.NotUsed
import _root_.akka.event.LoggingAdapter
import _root_.akka.stream.scaladsl.BidiFlow
import eventstore.core.tcp.{PackIn, PackOut}
import eventstore.core.{HeartbeatRequest, HeartbeatResponse}

private[eventstore] object BidiLogging {

  def apply(log: LoggingAdapter): BidiFlow[PackIn, PackIn, PackOut, PackOut, NotUsed] = {

    def logPackIn(packIn: PackIn): PackIn = {
      if (log.isDebugEnabled) packIn.message match {
        case Success(HeartbeatRequest | Ping) =>
        case _                                => log.debug(packIn.toString)
      }
      packIn
    }

    def logPackOut(packOut: PackOut): PackOut = {
      if (log.isDebugEnabled) packOut.message match {
        case HeartbeatResponse | Pong =>
        case _                        => log.debug(packOut.toString)
      }
      packOut
    }

    BidiFlow.fromFunctions(logPackIn, logPackOut) named "logging"
  }
}
