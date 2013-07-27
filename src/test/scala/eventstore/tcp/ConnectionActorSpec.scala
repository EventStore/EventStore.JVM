package eventstore
package tcp

import org.specs2.mutable.SpecificationWithJUnit
import org.specs2.specification.Scope
import org.specs2.time.NoDurationConversions
import akka.io.{Tcp, IO}
import akka.io.Tcp._
import java.net.InetSocketAddress
import akka.testkit.{TestActorRef, ImplicitSender, TestKit}
import akka.actor.{ActorRef, ActorSystem}
import scala.concurrent.duration._
import java.nio.ByteOrder
import eventstore.Out

/**
 * @author Yaroslav Klymko
 */
class ConnectionActorSpec extends SpecificationWithJUnit with NoDurationConversions {

  val off = FiniteDuration(1, MINUTES)

  "Connection Actor" should {

    "not reconnect when connection lost if maxReconnections == 0" in new TcpScope {
      val (client, connection) = connect(Settings(address = address, maxReconnections = 0))
      connection ! Abort
      expectMsg(Aborted)
      expectNoMsg()
    }

    "reconnect when connection lost" in new TcpScope {
      val (client, connection) = connect(Settings(address = address, maxReconnections = 1))
      connection ! Abort
      expectMsg(Aborted)
      expectMsgType[Connected]
    }

    "keep trying to reconnect for maxReconnections times" in new TcpMockScope {
      val settings = Settings(maxReconnections = 3)
      newClient(settings)
      verifyReconnections(settings.maxReconnections)
      expectNoMsg()
    }

    "keep trying to reconnect for maxReconnections times when connection lost" in new TcpMockScope {
      val settings = Settings(maxReconnections = 3)
      val client = newClient(settings)
      val connect = expectMsgType[Connect]
      client ! Connected(connect.remoteAddress, new InetSocketAddress(0))
      expectMsgType[Register]
      client ! PeerClosed
      verifyReconnections(settings.maxReconnections)
      expectNoMsg()
    }

    "use reconnectionDelay from settings" in new TcpMockScope {
      val settings = Settings(maxReconnections = 3, reconnectionDelay = FiniteDuration(2, SECONDS))
      val client = newClient(settings)

      val connect = expectMsgType[Connect]
      client ! CommandFailed(connect)

      expectNoMsg(FiniteDuration(1, SECONDS))

      expectMsgType[Connect]
      client ! CommandFailed(connect)
    }

    "reconnect if heartbeat timed out" in new TcpScope {
      val (client, connection) = connect(Settings(address = address))
      val req = expectTcpPack
      req.message mustEqual HeartbeatRequestCommand
      expectMsg(PeerClosed)
      expectMsgType[Connected]
      unbind(socket)
    }

    "not reconnect if heartbeat response received in time" in new TcpScope {
      val (client, connection) = connect(Settings(address = address))

      val req = expectTcpPack
      req.message mustEqual HeartbeatRequestCommand

      connection ! Write(Frame(TcpPackage(req.correlationId, HeartbeatResponseCommand)))
      expectTcpPack.message mustEqual HeartbeatRequestCommand

      unbind(socket)
    }

    "close connection if heartbeat timed out and maxReconnections == 0" in new TcpScope {
      val (client, connection) = connect(Settings(address = address, maxReconnections = 0))
      expectTcpPack.message mustEqual HeartbeatRequestCommand
      expectMsg(PeerClosed)
      expectNoMsg()
      unbind(socket)
    }

    "not close connection if heartbeat response received in time" in new TcpScope {
      val (client, connection) = connect(Settings(address = address, maxReconnections = 0))

      val req = expectTcpPack
      req.message mustEqual HeartbeatRequestCommand

      connection ! Write(Frame(TcpPackage(req.correlationId, HeartbeatResponseCommand)))

      expectTcpPack.message mustEqual HeartbeatRequestCommand

      unbind(socket)
    }

    "respond with HeartbeatResponseCommand on HeartbeatRequestCommand" in new TcpScope {
      val (client, connection) = connect(Settings(address = address, maxReconnections = 0))
      val req = TcpPackage[Out](HeartbeatRequestCommand)
      connection ! Write(Frame(req))

      val res = expectTcpPack
      res.correlationId mustEqual req.correlationId
      res.message mustEqual HeartbeatResponseCommand

      unbind(socket)
    }

    "stash messages while connecting" in todo
    "stash messages while connection lost" in todo
    "send stashed messages when connection restored" in todo
  }

  abstract class TcpScope extends TestKit(ActorSystem()) with ImplicitSender with Scope {
    val (address, socket) = bind()


    def connect(settings: Settings): (ActorRef, ActorRef) = {
      val client = TestActorRef(new ConnectionActor(settings))
      val connection = {
        expectMsgType[Connected]
        val connection = lastSender
        connection ! Register(self)
        connection
      }
      client -> connection
    }

    def bind(address: InetSocketAddress = new InetSocketAddress(0)): (InetSocketAddress, ActorRef) = {
      IO(Tcp) ! Bind(self, address)
      expectMsgType[Bound].localAddress -> lastSender
    }

    def expectTcpPack = Frame.unapply(expectMsgType[Received].data)

    def unbind(socket: ActorRef) {
      socket ! Unbind
      expectMsg(Unbound)
    }
  }


  object Frame {
    implicit val byteOrder = ByteOrder.LITTLE_ENDIAN

    def unapply(bs: ByteString): TcpPackage[In] = {
      val iterator = bs.iterator
      val length = iterator.getInt
      TcpPackage.deserialize(iterator.toByteString)
    }

    def apply(pack: TcpPackage[Out]): ByteString = {
      val bb = ByteString.newBuilder
      val data = pack.serialize
      bb.putInt(data.length)
      bb.append(data)
      bb.result()
    }
  }


  abstract class TcpMockScope extends TestKit(ActorSystem()) with ImplicitSender with Scope {

    def newClient(settings: Settings = Settings()) = TestActorRef(new ConnectionActor(settings) {
      override val tcp = testActor
    })

    def verifyReconnections(n: Int) {
      if (n >= 0) {
        val connect = expectMsgType[Connect]
        lastSender ! CommandFailed(connect)
        verifyReconnections(n - 1)
      }
    }
  }
}
