package eventstore
package tcp

import org.specs2.mutable.SpecificationWithJUnit
import org.specs2.specification.Scope
import akka.io.{Tcp, IO}
import akka.io.Tcp._
import java.net.InetSocketAddress
import akka.testkit.{TestActorRef, ImplicitSender, TestKit}
import akka.actor.{ActorRef, ActorSystem}
import scala.concurrent.duration._

/**
 * @author Yaroslav Klymko
 */
class ConnectionActorSpec extends SpecificationWithJUnit {

  "Connection Actor" should {

    "not reconnect when connection lost if maxReconnections == 0" in new TcpScope {
      val (address, socket) = bind()
      connect(address, maxReconnections = 0)
      unbind(socket)
      bind(address)
      expectNoMsg(duration)
    }

    "reconnect when connection lost" in new TcpScope {
      val (address, socket) = bind()
      connect(address, maxReconnections = 1)
      unbind(socket)
      bind(address)
      expectMsgType[Connected](duration)
    }

    "keep trying to reconnect for maxReconnections times" in new TcpMockScope {
      val settings = Settings(maxReconnections = 3)
      newConnection(settings)
      verifyReconnections(settings.maxReconnections)
      expectNoMsg()
    }

    "keep trying to reconnect for maxReconnections times when connection lost" in new TcpMockScope {
      val settings = Settings(maxReconnections = 3)
      newConnection(settings)
      val connect = expectMsgType[Connect]
      lastSender ! Connected(connect.remoteAddress, new InetSocketAddress(0))
      expectMsgType[Register]
      lastSender ! PeerClosed
      verifyReconnections(settings.maxReconnections)
      expectNoMsg()
    }

    "use reconnectionDelay from settings" in new TcpMockScope {
      val settings = Settings(maxReconnections = 3, reconnectionDelay = FiniteDuration(2, SECONDS))
      newConnection(settings)

      val connect = expectMsgType[Connect]
      lastSender ! CommandFailed(connect)

      expectNoMsg(FiniteDuration(1, SECONDS))

      expectMsgType[Connect]
      lastSender ! CommandFailed(connect)
    }

    "stash messages while connecting" in todo
    "stash messages while connection lost" in todo
    "send stashed messages when connection restored" in todo
  }

  abstract class TcpScope extends TestKit(ActorSystem()) with ImplicitSender with Scope {

    val duration = FiniteDuration(10, SECONDS)

    def connect(address: InetSocketAddress, maxReconnections: Int = 0) {
      TestActorRef(new ConnectionActor(Settings(address = address, maxReconnections = maxReconnections)))
      expectMsgType[Connected]
    }

    def bind(address: InetSocketAddress = new InetSocketAddress(0)): (InetSocketAddress, ActorRef) = {
      IO(Tcp) ! Bind(self, address)
      expectMsgType[Bound].localAddress -> lastSender
    }

    def unbind(socket: ActorRef) {
      socket ! Unbind
      expectMsg(Unbound)
    }
  }


  abstract class TcpMockScope extends TestKit(ActorSystem()) with ImplicitSender with Scope {

    def newConnection(settings: Settings = Settings(maxReconnections = 3)) {
      TestActorRef(new ConnectionActor(settings) {
        override val tcp = testActor
      })
    }

    def verifyReconnections(n: Int) {
      if (n >= 0) {
        val connect = expectMsgType[Connect]
        lastSender ! CommandFailed(connect)
        verifyReconnections(n - 1)
      }
    }
  }
}
