package eventstore
package operations

import Inspection.Decision.Stop
import scala.util.Success

private[eventstore] class SimpleInspection(in: In) extends Inspection {
  def expected = in.getClass
  def pf = { case Success(`in`) => Stop }
}

private[eventstore] object AuthenticateInspection extends SimpleInspection(Authenticated)

private[eventstore] object PingInspection extends SimpleInspection(Pong)

private[eventstore] object UnsubscribeInspection extends SimpleInspection(Unsubscribed)