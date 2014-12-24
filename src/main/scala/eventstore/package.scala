import java.net.{ InetAddress, InetSocketAddress }
import java.util.UUID
import akka.actor.Actor

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

  implicit class RichPartialFunction(val self: Actor.Receive) extends AnyVal {
    // workaround for https://issues.scala-lang.org/browse/SI-8861
    def or(pf: Actor.Receive): Actor.Receive = self.orElse[Any, Unit](pf)
  }
}