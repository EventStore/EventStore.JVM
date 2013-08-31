package eventstore
package tcp

import org.specs2.mock.Mockito
import akka.io.{ Tcp, IO }
import akka.io.Tcp._
import akka.testkit.{ TestProbe, TestActorRef }
import akka.actor.{ Terminated, ActorRef }
import java.net.InetSocketAddress
import java.nio.ByteOrder
import scala.concurrent.duration._
import EventStoreFormats.{ TcpPackageInReader, TcpPackageOutWriter }
import eventstore.util.BytesWriter

/**
 * @author Yaroslav Klymko
 */
class ConnectionActorSpec extends util.ActorSpec with Mockito {

  val off = 1.minute

  "Connection Actor" should {

    "not reconnect when connection lost if maxReconnections == 0" in new TcpScope {
      val (_, tcpConnection) = connect(settings.copy(maxReconnections = 0))
      tcpConnection ! Abort
      expectMsg(Aborted)
      expectNoMsg()
    }

    "reconnect when connection lost" in new TcpScope {
      val (_, tcpConnection) = connect(settings.copy(maxReconnections = 1))
      tcpConnection ! Abort
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
      val settings = Settings(maxReconnections = 3, reconnectionDelay = 2.seconds)
      val client = newClient(settings)

      val connect = expectMsgType[Connect]
      client ! CommandFailed(connect)

      expectNoMsg(1.second)

      expectMsgType[Connect]
      client ! CommandFailed(connect)
    }

    "reconnect if heartbeat timed out" in new TcpScope {
      val (_, tcpConnection) = connect()
      val req = expectTcpPack
      req.message mustEqual HeartbeatRequest
      expectMsg(PeerClosed)
      expectMsgType[Connected]
    }

    "not reconnect if heartbeat response received in time" in new TcpScope {
      val (_, tcpConnection) = connect()

      val req = expectTcpPack
      req.message mustEqual HeartbeatRequest

      tcpConnection ! write(TcpPackageOut(req.correlationId, HeartbeatResponse))
      expectTcpPack.message mustEqual HeartbeatRequest
    }

    "close connection if heartbeat timed out and maxReconnections == 0" in new TcpScope {
      val (_, tcpConnection) = connect(settings.copy(maxReconnections = 0))
      expectTcpPack.message mustEqual HeartbeatRequest
      expectMsg(PeerClosed)
      expectNoMsg()
    }

    "not close connection if heartbeat response received in time" in new TcpScope {
      val (_, tcpConnection) = connect(settings.copy(maxReconnections = 0))

      val req = expectTcpPack
      req.message mustEqual HeartbeatRequest

      tcpConnection ! write(TcpPackageOut(req.correlationId, HeartbeatResponse))

      expectTcpPack.message mustEqual HeartbeatRequest
    }

    "respond with HeartbeatResponseCommand on HeartbeatRequestCommand" in new TcpScope {
      val (_, tcpConnection) = connect(settings.copy(maxReconnections = 0))
      val req = TcpPackageOut(HeartbeatRequest)
      tcpConnection ! write(req)

      val res = expectTcpPack
      res.correlationId mustEqual req.correlationId
      res.message mustEqual HeartbeatResponse
    }

    "ping" in new TcpScope {
      val (connection, tcpConnection) = connect()
      connection ! Ping

      val req = expectTcpPack
      req.message mustEqual Ping

      tcpConnection ! write(TcpPackageOut(req.correlationId, Pong))
    }

    "pong" in new TcpScope {
      val (client, connection) = connect()

      connection ! write(TcpPackageOut(Ping))
      expectTcpPack.message mustEqual Pong
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
      probe expectNoMsg 1.second
      deathProbe expectNoMsg 1.second

      connection.underlyingActor.binding must beEmpty
    }
  }

  abstract class TcpScope extends ActorScope {
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

  abstract class TcpMockScope extends ActorScope {

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
