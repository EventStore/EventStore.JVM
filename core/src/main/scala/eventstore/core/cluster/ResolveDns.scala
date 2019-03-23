package eventstore
package core
package cluster

import java.net.InetAddress
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.control.NonFatal

object ResolveDns {

  def apply(dns: String, atMost: FiniteDuration)(implicit ec: ExecutionContext): List[InetAddress] = {
    def resolve = InetAddress.getAllByName(dns).toList
    val result = try Await.result(Future(resolve), atMost) catch {
      case NonFatal(e) => throw ClusterException(s"Error while resolving DNS entry $dns", e)
    }

    if (result.isEmpty) throw ClusterException(s"DNS entry '$dns' resolved into empty list")
    else result
  }
}