package eventstore
package operations

import Inspection.Decision.Stop
import scala.util.Success

private[eventstore] class SimpleInspection(in: In) extends Inspection {
  def expected = in.getClass
  def pf = { case Success(`in`) => Stop }
}

private[eventstore] case object AuthenticateInspection extends SimpleInspection(Authenticated)

private[eventstore] case object PingInspection extends SimpleInspection(Pong)

private[eventstore] case object UnsubscribeInspection extends SimpleInspection(Unsubscribed)

private[eventstore] case object HeartbeatRequestInspection extends SimpleInspection(HeartbeatResponse)