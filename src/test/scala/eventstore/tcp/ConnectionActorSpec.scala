package eventstore
package tcp

import org.specs2.mutable.{After, SpecificationWithJUnit}
import akka.io.{Tcp, IO}
import akka.io.Tcp._
import java.net.InetSocketAddress
import akka.testkit.{TestActorRef, ImplicitSender, TestKit}
import akka.actor.{ActorRef, ActorSystem}
import org.specs2.specification.Scope
import scala.concurrent.duration._

/**
 * @author Yaroslav Klymko
 */
class ConnectionActorSpec extends SpecificationWithJUnit {

  "Connection Actor" should {

    "not reconnect when connection lost if maxReconnections == 0" in new TcpScope {
      val (address, socket) = bind()

      TestActorRef(new ConnectionActor(Settings(address = address, maxReconnections = 0)))
      expectMsgType[Connected]

      unbind(socket)
      bind(address)

      expectNoMsg(duration)
    }

    "reconnect when connection lost" in new TcpScope {
      val (address, socket) = bind()

      TestActorRef(new ConnectionActor(Settings(address = address, maxReconnections = 1)))
      expectMsgType[Connected]

      unbind(socket)
      bind(address)

      expectMsgType[Connected](duration)
    }

    "keep trying to reconnect for maxReconnections times" in new TcpMockScope {
      verifyReconnections(settings.maxReconnections)
      expectNoMsg()
    }

    "keep trying to reconnect for maxReconnections times when connection lost" in new TcpMockScope {
      val connect = expectMsgType[Connect]
      lastSender ! Connected(connect.remoteAddress, new InetSocketAddress(0))
      expectMsgType[Register]
      lastSender ! PeerClosed // TODO Seq(Closed, Aborted, ConfirmedClosed, PeerClosed, ErrorClosed("error"))

      verifyReconnections(settings.maxReconnections)
      expectNoMsg()
    }

    "stash messages while connecting" in todo
    "stash messages while connection lost" in todo
    "send stashed messages when connection restored" in todo
  }

  abstract class TcpScope extends TestKit(ActorSystem()) with ImplicitSender with Scope {

    val duration = FiniteDuration(10, SECONDS)

    def bind(address: InetSocketAddress = new InetSocketAddress(0)): (InetSocketAddress, ActorRef) = {
      IO(Tcp) ! Bind(self, address)
      expectMsgType[Bound].localAddress -> lastSender
    }

    def unbind(socket: ActorRef) {
      socket ! Unbind
      expectMsg(Unbound)
    }

    //    def newConnection(){
    //      TestActorRef(new ConnectionActor(address))
    //      expectMsgType[Connected]
    //    }
  }


  abstract class TcpMockScope extends TestKit(ActorSystem()) with ImplicitSender with Scope {

    val duration = FiniteDuration(10, SECONDS)
    val settings = Settings(maxReconnections = 3)

    TestActorRef(new ConnectionActor(settings) {
      override val tcp = testActor
    })


    def verifyReconnections(n: Int) {
      if (n >= 0) {
        val connect = expectMsgType[Connect](duration)
        lastSender ! CommandFailed(connect)
        verifyReconnections(n - 1)
      }
    }
  }
}
