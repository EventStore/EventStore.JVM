package eventstore.util

import akka.NotUsed
import akka.event.LoggingAdapter
import akka.stream.scaladsl.BidiFlow
import eventstore.tcp.{ PackIn, PackOut }
import eventstore.{ HeartbeatRequest, HeartbeatResponse, Ping, Pong }

import scala.util.Success

object BidiLogging {
  def apply(log: LoggingAdapter): BidiFlow[PackIn, PackIn, PackOut, PackOut, NotUsed] = {
    def logPackIn(packIn: PackIn): PackIn = {
      if (log.isDebugEnabled) packIn.message match {
        case Success(HeartbeatRequest) =>
        case Success(Ping)             =>
        case _                         => log.debug(packIn.toString)
      }
      packIn
    }

    def logPackOut(packOut: PackOut): PackOut = {
      if (log.isDebugEnabled) packOut.message match {
        case HeartbeatResponse =>
        case Pong              =>
        case _                 => log.debug(packOut.toString)
      }
      packOut
    }

    BidiFlow.fromFunctions(logPackIn, logPackOut)
  }
}
