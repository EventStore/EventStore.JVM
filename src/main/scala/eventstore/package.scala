import java.net.{ InetAddress, InetSocketAddress }
import java.util.UUID

package object eventstore {
  type Uuid = java.util.UUID
  val ByteString = akka.util.ByteString
  type ByteString = akka.util.ByteString

  def randomUuid: Uuid = java.util.UUID.randomUUID()

  val MaxBatchSize: Int = 10000

  implicit class EsString(val self: String) extends AnyVal {
    def uuid: Uuid = UUID.fromString(self)
  }

  implicit class EsInt(val self: Int) extends AnyVal {
    def ::(host: String): InetSocketAddress = new InetSocketAddress(host, self)
    def ::(host: InetAddress): InetSocketAddress = new InetSocketAddress(host, self)
  }
}