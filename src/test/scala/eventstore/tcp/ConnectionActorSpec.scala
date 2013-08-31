package eventstore
package tcp

import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import org.specs2.time.NoDurationConversions
import org.specs2.mock.Mockito
import akka.io.{ Tcp, IO }
import akka.io.Tcp._
import akka.testkit.{ TestProbe, TestActorRef, ImplicitSender, TestKit }
import akka.actor.{ Terminated, ActorRef, ActorSystem }
import java.net.InetSocketAddress
import java.nio.ByteOrder
import scala.concurrent.duration._
import EventStoreFormats.{ TcpPackageInReader, TcpPackageOutWriter }
import eventstore.util.BytesWriter

/**
 * @author Yaroslav Klymko
 */
class ConnectionActorSpec extends Specification with NoDurationConversions with Mockito {

  val off = FiniteDuration(1, MINUTES)

  "Connection Actor" should {

    "not reconnect when connection lost if maxReconnections == 0" in new TcpScope {
      val (_, tcpConnection) = connect(settings.copy(maxReconnections = 0))
      tcpConnection ! Abort
      expectMsg(Aborted)
      expectNoMsg()
      system.shutdown()
    }

    "reconnect when connection lost" in new TcpScope {
      val (_, tcpConnection) = connect(settings.copy(maxReconnections = 1))
      tcpConnection ! Abort
      expectMsg(Aborted)
      expectMsgType[Connected]
      system.shutdown()
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
      val (_, tcpConnection) = connect()
      val req = expectTcpPack
      req.message mustEqual HeartbeatRequest
      expectMsg(PeerClosed)
      expectMsgType[Connected]
      system.shutdown()
    }

    "not reconnect if heartbeat response received in time" in new TcpScope {
      val (_, tcpConnection) = connect()

      val req = expectTcpPack
      req.message mustEqual HeartbeatRequest

      tcpConnection ! write(TcpPackageOut(req.correlationId, HeartbeatResponse))
      expectTcpPack.message mustEqual HeartbeatRequest

      system.shutdown()
    }

    "close connection if heartbeat timed out and maxReconnections == 0" in new TcpScope {
      val (_, tcpConnection) = connect(settings.copy(maxReconnections = 0))
      expectTcpPack.message mustEqual HeartbeatRequest
      expectMsg(PeerClosed)
      expectNoMsg()
      system.shutdown()
    }

    "not close connection if heartbeat response received in time" in new TcpScope {
      val (_, tcpConnection) = connect(settings.copy(maxReconnections = 0))

      val req = expectTcpPack
      req.message mustEqual HeartbeatRequest

      tcpConnection ! write(TcpPackageOut(req.correlationId, HeartbeatResponse))

      expectTcpPack.message mustEqual HeartbeatRequest

      system.shutdown()
    }

    "respond with HeartbeatResponseCommand on HeartbeatRequestCommand" in new TcpScope {
      val (_, tcpConnection) = connect(settings.copy(maxReconnections = 0))
      val req = TcpPackageOut(HeartbeatRequest)
      tcpConnection ! write(req)

      val res = expectTcpPack
      res.correlationId mustEqual req.correlationId
      res.message mustEqual HeartbeatResponse

      system.shutdown()
    }

    "ping" in new TcpScope {
      val (connection, tcpConnection) = connect()
      connection ! Ping

      val req = expectTcpPack
      req.message mustEqual Ping

      tcpConnection ! write(TcpPackageOut(req.correlationId, Pong))

      system.shutdown()
    }

    "pong" in new TcpScope {
      val (client, connection) = connect()

      connection ! write(TcpPackageOut(Ping))
      expectTcpPack.message mustEqual Pong

      system.shutdown()
    }

    "stash messages while connecting" in todo
    "stash messages while connection lost" in todo
    "send stashed messages when connection restored" in todo

    "bind actor to correlationId" in new TcpScope {
      val (connection, tcpConnection) = connect()

      Seq.fill(5)(TestProbe.apply).foreach {
        probe =>
          val actor = probe.ref
          connection.tell(Ping, actor)

          val pack = expectTcpPack
          pack.message mustEqual Ping

          val correlationId = pack.correlationId
          connection.underlyingActor.binding.x(correlationId) must beSome(actor)

          Seq(HeartbeatResponse, Pong).foreach {
            msg =>
              tcpConnection ! write(TcpPackageOut(newUuid, msg))
              tcpConnection ! write(TcpPackageOut(correlationId, msg))
              probe expectMsg msg
          }
      }
      system.shutdown()
    }

    "unbind actor when stopped" in new TcpScope {
      val (connection, tcpConnection) = connect()

      val probe = TestProbe()
      val actor = probe.ref

      val deathProbe = TestProbe()
      deathProbe watch tcpConnection
      deathProbe watch actor
      deathProbe watch connection

      connection.tell(Ping, probe.ref)

      val req = expectTcpPack
      req.message mustEqual Ping

      val res = TcpPackageOut(req.correlationId, Pong)
      tcpConnection ! write(res)
      probe expectMsg Pong

      system stop actor
      deathProbe.expectMsgPF() {
        case Terminated(`actor`) =>
      }

      tcpConnection ! write(res)
      probe expectNoMsg FiniteDuration(1, SECONDS)
      deathProbe expectNoMsg FiniteDuration(1, SECONDS)

      connection.underlyingActor.binding must beEmpty

      system.shutdown()
    }
  }

  abstract class TcpScope extends TestKit(ActorSystem()) with ImplicitSender with Scope {
    val (address, socket) = bind()
    val settings = Settings(address = address)

    def connect(settings: Settings = settings): (TestActorRef[ConnectionActor], ActorRef) = {
      val client = TestActorRef(new ConnectionActor(settings))
      val connection = {
        expectMsgType[Connected]
        val connection = lastSender
        connection ! Register(self)
        connection
      }
      client -> connection
    }

    def write(x: TcpPackageOut) = Write(Frame(x))

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

    def unapply(bs: ByteString): TcpPackageIn = {
      val iterator = bs.iterator
      val length = iterator.getInt
      TcpPackageInReader.read(iterator)
    }

    def apply(pack: TcpPackageOut): ByteString = {
      val bb = ByteString.newBuilder
      val data = BytesWriter[TcpPackageOut].toByteString(pack)
      bb.putInt(data.length)
      bb.append(data)
      bb.result()
    }
  }

  abstract class TcpMockScope extends TestKit(ActorSystem()) with ImplicitSender with Scope {

    def newClient(settings: Settings = Settings()) = TestActorRef(new ConnectionActor(settings) {
      override def tcp = testActor
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
