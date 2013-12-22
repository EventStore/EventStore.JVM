package eventstore
package tcp

import org.specs2.mock.Mockito
import akka.io.{ Tcp, IO }
import akka.io.Tcp._
import akka.testkit.{ TestProbe, TestActorRef }
import akka.actor.{ Terminated, ActorRef }
import akka.util.ByteIterator
import java.net.InetSocketAddress
import java.nio.ByteOrder
import scala.concurrent.duration._
import scala.util.{ Try, Success }

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
      val (_, tcpConnection) = connect(settings.copy(maxReconnections = 1, reconnectionDelay = Duration.Zero))
      tcpConnection ! Abort
      expectMsg(Aborted)
      expectMsgType[Connected]
    }

    "reconnect when connection actor died" in new TcpMockScope {
      val settings = Settings(maxReconnections = 1, reconnectionDelay = Duration.Zero)
      val client = newClient(settings)
      val connect = expectMsgType[Connect]
      val connection = TestProbe()
      client.tell(Connected(connect.remoteAddress, new InetSocketAddress(0)), connection.ref)
      connection.expectMsgType[Register]
      system stop connection.ref
      verifyReconnections(settings.maxReconnections)
      expectNoMsg()
    }

    "reconnect when pipeline actor died" in new TcpMockScope {
      val settings = Settings(maxReconnections = 1, reconnectionDelay = Duration.Zero)
      val client = newClient(settings)
      val connect = expectMsgType[Connect]
      val connection = TestProbe()
      client.tell(Connected(connect.remoteAddress, new InetSocketAddress(0)), connection.ref)
      val pipeline = connection.expectMsgType[Register].handler
      system stop pipeline
      verifyReconnections(settings.maxReconnections)
      expectNoMsg()
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
      val req = expectPack
      req.message mustEqual Success(HeartbeatRequest)
      expectMsg(PeerClosed)
      expectMsgType[Connected]
    }

    "not reconnect if heartbeat response received in time" in new TcpScope {
      val (_, tcpConnection) = connect()

      val req = expectPack
      req.message mustEqual Success(HeartbeatRequest)

      tcpConnection ! write(TcpPackageOut(req.correlationId, HeartbeatResponse))
      expectPack.message mustEqual Success(HeartbeatRequest)
    }

    "close connection if heartbeat timed out and maxReconnections == 0" in new TcpScope {
      val (_, tcpConnection) = connect(settings.copy(maxReconnections = 0))
      expectPack.message mustEqual Success(HeartbeatRequest)
      expectMsg(PeerClosed)
      expectNoMsg()
    }

    "not close connection if heartbeat response received in time" in new TcpScope {
      val (_, tcpConnection) = connect(settings.copy(maxReconnections = 0))

      val req = expectPack
      req.message mustEqual Success(HeartbeatRequest)

      tcpConnection ! write(TcpPackageOut(req.correlationId, HeartbeatResponse))

      expectPack.message mustEqual Success(HeartbeatRequest)
    }

    "respond with HeartbeatResponseCommand on HeartbeatRequestCommand" in new TcpScope {
      val (_, tcpConnection) = connect(settings.copy(maxReconnections = 0))
      val req = TcpPackageOut(HeartbeatRequest)
      tcpConnection ! write(req)

      val res = expectPack
      res.correlationId mustEqual req.correlationId
      res.message mustEqual Success(HeartbeatResponse)
    }

    "ping" in new TcpScope {
      val (connection, tcpConnection) = connect()
      connection ! Ping

      val req = expectPack
      req.message mustEqual Success(Ping)

      tcpConnection ! write(TcpPackageOut(req.correlationId, Pong))
    }

    "pong" in new TcpScope {
      val (_, tcpConnection) = connect()

      tcpConnection ! write(Ping)
      expectPack.message mustEqual Success(Pong)
    }

    "stash messages while connecting" in new TcpScope {
      val connection = newConnection()

      connection ! Ping
      connection ! HeartbeatRequest

      val tcpConnection = newTcpConnection()

      expectPack.message mustEqual Success(Ping)
      expectPack.message mustEqual Success(HeartbeatRequest)
    }

    "stash messages while connection lost" in new TcpScope {
      val (connection, tcpConnection) = connect()
      tcpConnection ! Close
      expectMsg(Closed)

      connection ! Ping
      connection ! HeartbeatRequest

      newTcpConnection()

      expectPack.message mustEqual Ping
      expectPack.message mustEqual HeartbeatRequest
    }.pendingUntilFixed

    "bind actor to correlationId" in new TcpScope {
      val (connection, tcpConnection) = connect()

      Seq.fill(5)(TestProbe.apply).foreach {
        probe =>
          val actor = probe.ref
          connection.tell(Ping, actor)

          val pack = expectPack
          pack.message mustEqual Success(Ping)

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

      val req = expectPack
      req.message mustEqual Success(Ping)

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

    "stop subscription if initiator actor died" in new TcpMockScope {
      val settings = Settings(maxReconnections = 1)
      val client = TestActorRef(new ConnectionActor(settings) {
        override def tcp = testActor
        override def newPipeline(connection: ActorRef) = testActor
      })

      val connect = expectMsgType[Connect]
      client ! Connected(connect.remoteAddress, new InetSocketAddress(0))
      expectMsgType[Register]

      foreach(List(EventStream.All, EventStream.Id("stream"))) {
        stream =>

          val probe = TestProbe()

          val subscribeTo = SubscribeTo(EventStream.All)
          client.tell(subscribeTo, probe.ref)
          val init = client.underlyingActor.init
          val correlationId = expectMsgPF() {
            case init.Command(TcpPackageOut(x, `subscribeTo`, _)) => x
          }
          client ! TcpPackageIn(correlationId, Try(SubscribeToStreamCompleted(0)))
          system stop probe.ref

          expectMsgPF() {
            case init.Command(TcpPackageOut(x, UnsubscribeFromStream, _)) => x
          } mustEqual correlationId
      }
    }.pendingUntilFixed // TODO

    "use default credentials if not provided with message" in new SecurityScope {
      val x = UserCredentials("login", "password")
      ?(default = Some(x)) must beSome(x)
    }

    "use credentials that is provided with message" in new SecurityScope {
      val c = UserCredentials("login", "password")
      ?(withMessage = Some(c)) must beSome(c)
    }

    "use credentials provided with message rather then default" in new SecurityScope {
      val x1 = UserCredentials("l1", "p2")
      val x2 = UserCredentials("l2", "p2")
      ?(withMessage = Some(x1), default = Some(x2)) must beSome(x1)
    }

    "use no credentials if either not provided with message and default" in new SecurityScope {
      ?(withMessage = None, default = None) must beNone
    }
  }

  abstract class TcpScope extends ActorScope {
    val (address, socket) = bind()
    val settings = Settings(address = address)

    def connect(settings: Settings = settings): (TestActorRef[ConnectionActor], ActorRef) = {
      val connection = newConnection(settings)
      val tcpConnection = newTcpConnection()
      connection -> tcpConnection
    }

    def newConnection(settings: Settings = settings) = TestActorRef(new ConnectionActor(settings))

    def newTcpConnection() = {
      expectMsgType[Connected]
      val connection = lastSender
      connection ! Register(self)
      connection
    }

    def write(x: TcpPackageOut): Write = Write(Frame.toByteString(x))
    def write(x: Out): Write = write(TcpPackageOut(x))

    def bind(address: InetSocketAddress = new InetSocketAddress(0)): (InetSocketAddress, ActorRef) = {
      IO(Tcp) ! Bind(self, address)
      expectMsgType[Bound].localAddress -> lastSender
    }

    def expectPack = Frame.readIn(expectMsgType[Received].data)
    def expectPackOut = Frame.readOut(expectMsgType[Received].data)

    def unbind(socket: ActorRef) {
      socket ! Unbind
      expectMsg(Unbound)
    }
  }

  object Frame {
    import EventStoreFormats._

    implicit val byteOrder = ByteOrder.LITTLE_ENDIAN

    def readIn(bs: ByteString): TcpPackageIn = {
      val iterator = bs.iterator
      val length = iterator.getInt
      TcpPackageInReader.read(iterator)
    }

    def readOut(bs: ByteString): TcpPackageOut = {
      def readPack(bi: ByteIterator) = {
        import util.BytesReader
        val readMessage = MarkerByte.readMessage(bi)

        val flags = BytesReader[Flags].read(bi)
        val correlationId = BytesReader[Uuid].read(bi)
        val credentials =
          if ((flags & Flag.Auth) == 0) None
          else Some(BytesReader[UserCredentials].read(bi))

        val message = readMessage(bi)
        TcpPackageOut(correlationId, message.get.asInstanceOf[Out], credentials)
      }

      val iterator = bs.iterator
      val length = iterator.getInt
      readPack(iterator)
    }

    def toByteString(pack: TcpPackageOut): ByteString = {
      val bb = ByteString.newBuilder
      val data = util.BytesWriter[TcpPackageOut].toByteString(pack)
      bb.putInt(data.length)
      bb.append(data)
      bb.result()
    }
  }

  abstract class TcpMockScope extends ActorScope {
    //    val pipeline = TestProbe()
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

  trait SecurityScope extends TcpScope {
    def ?(default: Option[UserCredentials] = None, withMessage: Option[UserCredentials] = None) = {
      val (connection, _) = connect(settings.copy(defaultCredentials = default))
      val message = Ping
      val envelope = withMessage match {
        case Some(x) => WithCredentials(message, x)
        case None    => message
      }
      connection ! envelope
      val pack = expectPackOut
      pack.message mustEqual message
      pack.credentials
    }
  }
}
