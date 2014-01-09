package eventstore
package tcp

import akka.actor.Status.Failure
import akka.actor.{ Terminated, ActorRef }
import akka.io.Tcp._
import akka.io.{ Tcp, IO }
import akka.testkit.{ TestProbe, TestActorRef }
import akka.util.ByteIterator
import java.net.InetSocketAddress
import java.nio.ByteOrder
import org.specs2.mock.Mockito
import scala.concurrent.duration._
import scala.util.{ Try, Success }

class ConnectionActorSpec extends util.ActorSpec with Mockito {

  val off = 1.minute

  "Connection Actor" should {

    "receive init.Event while connecting" in new TestScope {
      sendConnected()
      client ! PeerClosed
      expectConnect()
      val correlationId = newUuid
      client.underlyingActor.binding = client.underlyingActor.binding.+(correlationId, testActor)
      client ! init.Event(TcpPackageIn(correlationId, Success(Authenticated)))
      expectMsg(Authenticated)
    }

    "receive init.Event while connected" in new TestScope {
      sendConnected()

      client ! Authenticate
      val correlationId = pipeline.expectMsgPF() {
        case init.Command(TcpPackageOut(x, Authenticate, `credentials`)) => x
      }

      client ! init.Event(TcpPackageIn(newUuid, Success(Authenticated)))
      expectNoMsg(duration)

      client ! init.Event(TcpPackageIn(correlationId, Success(Authenticated)))
      expectMsg(Authenticated)
    }

    "not reconnect if never connected before" in new TestScope {
      client ! CommandFailed(connect)
      expectNoMsgs()
      expectTerminated()
    }

    "not reconnect when connection lost if maxReconnections == 0" in new TcpScope {
      val (_, tcpConnection) = connect(settings.copy(maxReconnections = 0))
      tcpConnection ! Abort
      expectMsg(Aborted)
      expectNoMsg()
    }

    "reconnect when connection lost" in new TcpScope {
      val (_, tcpConnection) = connect(settings.copy(maxReconnections = 1, reconnectionDelayMin = Duration.Zero))
      tcpConnection ! Abort
      expectMsg(Aborted)
      expectMsgType[Connected]
    }

    "reconnect when connection actor died" in new TestScope {
      sendConnected()
      system stop connection.ref
      verifyReconnections(settings.maxReconnections)
      expectNoMsgs()

      override def settings = Settings(maxReconnections = 1, reconnectionDelayMin = Duration.Zero)
    }

    "reconnect when pipeline actor died" in new TestScope {
      sendConnected()
      system stop pipeline.ref
      verifyReconnections(settings.maxReconnections)
      expectNoMsgs()

      override def settings = Settings(maxReconnections = 1, reconnectionDelayMin = Duration.Zero)
    }

    "keep trying to reconnect for maxReconnections times" in new TestScope {
      sendConnected()
      client ! PeerClosed
      verifyReconnections(settings.maxReconnections)
      expectNoMsgs()

      override def settings = Settings(maxReconnections = 5, reconnectionDelayMin = Duration.Zero)
    }

    "use reconnectionDelay from settings" in new TestScope {
      sendConnected()
      client ! PeerClosed
      tcp.expectNoMsg(300.millis)
      verifyReconnections(settings.maxReconnections)

      override def settings = Settings(maxReconnections = 3, reconnectionDelayMin = 500.millis)
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

    "stash Out message while connecting for the first time" in new TestScope {
      client ! Ping
      sendConnected()
      pipeline.expectMsgPF() {
        case init.Command(TcpPackageOut(_, Ping, _)) =>
      }
    }

    "stash TcpPackageOut message while connecting for the first time" in new TestScope {
      val pack = TcpPackageOut(Ping)
      client ! pack
      sendConnected()
      pipeline.expectMsg(init.Command(pack))
    }

    "reply with NoConnection error to stashed messages" in new TestScope {
      client ! Ping
      client ! CommandFailed(connect)
      expectNoConnectionFailure()
    }

    "reply with NoConnection error on Out message while reconnecting" in new TestScope {
      sendConnected()
      client ! PeerClosed
      expectConnect()
      client ! Ping
      expectNoConnectionFailure()
    }

    "reply with NoConnection error on TcpPackageOut message while reconnecting" in new TestScope {
      sendConnected()
      client ! PeerClosed
      expectConnect()
      client ! TcpPackageOut(Ping)
      expectNoConnectionFailure()
    }

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

    "stop subscription if initiator actor died" in new SubscriptionScope {
      def forStream(stream: EventStream, correlationId: Uuid, credentials: Option[UserCredentials], probe: TestProbe) {
        client ! init.Event(TcpPackageIn(correlationId, Try(SubscribeToStreamCompleted(0))))
        system stop probe.ref
        pipeline.expectMsg(init.Command(TcpPackageOut(correlationId, UnsubscribeFromStream, credentials)))
      }
    }

    "not stop subscription if not completed and initiator actor died" in new SubscriptionScope {
      def forStream(stream: EventStream, correlationId: Uuid, credentials: Option[UserCredentials], probe: TestProbe) {
        system stop probe.ref
        expectNoMsg()
      }
    }

    // TODO restart subscription on reconnects

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

  trait SubscriptionScope extends TestScope {
    sendConnected()

    foreach(List(EventStream.All, EventStream("stream"))) {
      stream =>
        val probe = TestProbe()
        val subscribeTo = SubscribeTo(stream)
        client.tell(subscribeTo, probe.ref)
        val (correlationId, credentials) = pipeline.expectMsgPF() {
          case init.Command(TcpPackageOut(id, `subscribeTo`, c)) => id -> c
        }
        forStream(stream, correlationId, credentials, probe)
        success
    }

    def forStream(stream: EventStream, correlationId: Uuid, credentials: Option[UserCredentials], probe: TestProbe)

    override def settings = Settings(maxReconnections = 1, heartbeatInterval = 10.seconds, heartbeatTimeout = 20.seconds)
  }

  trait TestScope extends ActorScope {
    val duration = 1.second
    val credentials = settings.defaultCredentials
    val connect = Connect(settings.address, timeout = Some(settings.connectionTimeout))

    val tcp = TestProbe()
    val pipeline = TestProbe()
    val connection = TestProbe()

    val client = TestActorRef(new ConnectionActor(settings) {
      override def tcp = TestScope.this.tcp.ref
      override def newPipeline(connection: ActorRef) = TestScope.this.pipeline.ref
    })

    val init = client.underlyingActor.init

    expectConnect()

    def settings = Settings()

    def expectConnect() {
      tcp expectMsg connect
    }

    def sendConnected() {
      client.tell(Connected(settings.address, new InetSocketAddress(0)), connection.ref)
      connection expectMsg Register(pipeline.ref)
    }

    def expectNoMsgs() {
      tcp.expectNoMsg(duration)
      pipeline.expectNoMsg(duration)
      expectNoMsg(duration)
    }

    def expectTerminated() {
      val deathWatch = TestProbe()
      deathWatch watch client
      deathWatch.expectMsgPF() {
        case Terminated(`client`) => true
      }
    }

    def verifyReconnections(n: Int): Unit = if (n > 0) {
      expectConnect()
      client ! CommandFailed(connect)
      verifyReconnections(n - 1)
    }

    def expectNoConnectionFailure() {
      expectMsg(Failure(EsException(EsError.ConnectionLost)))
    }
  }
}
