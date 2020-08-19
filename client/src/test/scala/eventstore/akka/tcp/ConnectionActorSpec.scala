package eventstore
package akka
package tcp

import java.net.InetSocketAddress
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}
import _root_.akka.actor.{ActorRef, Status, Terminated}
import _root_.akka.io.{IO, Tcp}
import _root_.akka.testkit.{TestActorRef, TestProbe}
import _root_.akka.stream.StreamTcpException
import NotHandled.{MasterInfo, NotMaster}
import eventstore.core.syntax._
import eventstore.core.{BytesReader, BytesWriter, HeartbeatRequest, HeartbeatResponse}
import eventstore.core.tcp._
import eventstore.core.settings.ClusterSettings
import eventstore.core.cluster.ClusterException
import eventstore.core.cluster.GossipSeedsOrDns.GossipSeeds
import eventstore.akka.cluster.ClusterDiscovererActor.{Address, GetAddress}
import eventstore.akka.tcp.ConnectionActor.{Disconnected, Connected}

class ConnectionActorSpec extends ActorSpec {

  val off = 1.minute

  "Connection Actor" should {

    "receive PackIn while connecting" in new TestScope {
      val id = randomUuid
      client ! PackOut(Authenticate, id)
      client ! PackIn(Success(Authenticated), id)
      expectMsg(Authenticated)
    }

    "receive PackIn while connected" in new TestScope {

      connectedAndIdentified()

      client ! Authenticate
      val correlationId = connection.expectMsgPF() {
        case PackOut(Authenticate, x, `credentials`) => x
      }

      client ! packIn(Success(Authenticated))
      expectNoMessage(duration)

      client ! PackIn(Success(Authenticated), correlationId)
      expectMsg(Authenticated)
    }

    "receive PackIn while reconnecting" in new TestScope {

      connectedAndIdentified()

      client ! Authenticate
      val correlationId = connection.expectMsgPF() {
        case PackOut(Authenticate, x, `credentials`) => x
      }
      client ! tcpException
      expectConnect()

      client ! packIn(Success(Authenticated))
      expectNoMessage(duration)

      client ! PackIn(Success(Authenticated), correlationId)
      expectMsg(Authenticated)
    }

    "identify client version after connected" in new TestScope {

      sendConnected()

      connection.expectMsgPF() {
        case PackOut(IdentifyClient(1, _), _, _) => client ! ClientIdentified
      }
    }

    "not reconnect if never connected before" in new TestScope {
      client ! tcpException
      expectNoMsgs()
      expectTerminated()
    }

    "not reconnect when connection lost if maxReconnections == 0" in new TcpScope {
      val (c, tcpConnection) = connect(settings.copy(maxReconnections = 0))
      tcpConnection ! Tcp.Abort
      expectMsg(Tcp.Aborted)
      expectNoMessage()
    }

    "reconnect when connection lost" in new TcpScope {
      val (_, tcpConnection) = connect(settings.copy(maxReconnections = 1, reconnectionDelayMin = 100.millis))
      //      tcpConnection ! Abort
      //      expectMsg(Aborted)
      //      expectMsgType[Connected]
    }

    "reconnect when connection actor died" in new TestScope {
      connectedAndIdentified()
      system stop connection.ref
      expectTerminated(connection.ref)
      verifyReconnections(settings.maxReconnections)
      expectNoMsgs()

      override def settings = Settings(maxReconnections = 1, reconnectionDelayMin = 100.millis)
    }

    "reconnect when pipeline actor died" in new TestScope {
      connectedAndIdentified()
      system stop connection.ref
      expectTerminated(connection.ref)
      verifyReconnections(settings.maxReconnections)
      expectNoMsgs()

      override def settings = Settings(maxReconnections = 1, reconnectionDelayMin = 100.millis)
    }

    "keep trying to reconnect for maxReconnections times" in new TestScope {
      connectedAndIdentified()
      client ! tcpException
      verifyReconnections(settings.maxReconnections)
      expectNoMsgs()

      override def settings = Settings(maxReconnections = 5, reconnectionDelayMin = 100.millis)
    }

    "use reconnectionDelay from settings" in new TestScope {
      connectedAndIdentified()
      client ! tcpException
      tcp.expectNoMessage(100.millis)
      verifyReconnections(settings.maxReconnections)

      override def settings = Settings(maxReconnections = 3, reconnectionDelayMin = 500.millis)
    }

    "not reconnect if heartbeat response received in time" in new TcpScope {
      val (_, tcpConnection) = connect()

      val req = expectPack
      req.message mustEqual Success(HeartbeatRequest)

      tcpConnection ! write(PackOut(HeartbeatResponse, req.correlationId))
      expectPack.message mustEqual Success(HeartbeatRequest)
    }

    "not close connection if heartbeat response received in time" in new TcpScope {
      val (_, tcpConnection) = connect(settings.copy(maxReconnections = 0))

      val req = expectPack
      req.message mustEqual Success(HeartbeatRequest)

      tcpConnection ! write(PackOut(HeartbeatResponse, req.correlationId))

      expectPack.message mustEqual Success(HeartbeatRequest)
    }

    "respond with HeartbeatResponseCommand on HeartbeatRequestCommand" in new TcpScope {
      val (_, tcpConnection) = connect(settings.copy(maxReconnections = 0))
      val req = packOut(HeartbeatRequest)
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

      tcpConnection ! write(PackOut(Pong, req.correlationId))
    }

    "pong" in new TcpScope {
      val (_, tcpConnection) = connect()

      tcpConnection ! write(Ping)
      expectPack.message mustEqual Success(Pong)
    }

    "stash Out message while connecting for the first time" in new TestScope {
      client ! Ping
      connectedAndIdentified()
      connection.expectMsgPF() { case PackOut(Ping, _, _) => }
    }

    "stash PackOut message while connecting for the first time" in new TestScope {
      val pack = PackOut(Ping, randomUuid)
      client ! pack
      connectedAndIdentified()
      connection.expectMsg(pack)
    }

    "reply with OperationTimedOut if no reply received" in new OperationTimedOutScope {
      connectedAndIdentified()
      val ping = PackOut(Ping, id, credentials)
      client ! ping
      client ! packIn(Pong)

      client ! Authenticate
      client ! packIn(Authenticated)

      expectNoMessage(100.millis)
      expectOperationTimedOut(ping, Authenticate)
      client ! PackIn(Try(Pong), id)
      expectNoMessage(100.millis)
    }

    "reply with OperationTimedOut if not connected within timeout" in new OperationTimedOutScope {
      val ping = PackOut(Ping, id, credentials)
      client ! ping
      client ! packIn(Pong)

      client ! Authenticate
      client ! packIn(Authenticated)

      expectNoMessage(100.millis)
      expectOperationTimedOut(ping, Authenticate)
      client ! PackIn(Try(Pong), id)
      expectNoMessage(100.millis)
    }

    "reply with OperationTimedOut if not reconnected within timeout" in new OperationTimedOutScope {
      connectedAndIdentified()
      client ! tcpException

      val ping = PackOut(Ping, id, credentials)
      client ! ping
      client ! packIn(Pong)

      client ! Authenticate
      client ! packIn(Authenticated)

      client ! tcpException

      expectNoMessage(100.millis)
      expectOperationTimedOut(ping, Authenticate)
      client ! PackIn(Try(Pong), id)
      expectNoMessage(100.millis)
    }

    "reply with OperationTimedOut if no reply received" in new OperationTimedOutScope {
      connectedAndIdentified()
      val ping = PackOut(Ping, id, credentials)
      client ! ping
      client ! packIn(Pong)

      client ! Authenticate
      client ! packIn(Authenticated)

      client ! tcpException

      expectNoMessage(100.millis)
      expectOperationTimedOut(ping, Authenticate)
      client ! PackIn(Try(Pong), id)
      expectNoMessage(100.millis)
    }

    "reply with OperationTimedOut if not subscribed within timeout" in new OperationTimedOutScope {
      val subscribeTo = PackOut(SubscribeTo(EventStream.All), id, credentials)
      client ! subscribeTo
      client ! packIn(SubscribeToAllCompleted(0))

      expectNoMessage(100.millis)
      expectOperationTimedOut(subscribeTo)

      client ! PackIn(Try(SubscribeToAllCompleted(0)), id)

      expectNoMessage(100.millis)
    }

    "reply with OperationTimedOut if not unsubscribed within timeout" in new OperationTimedOutScope {
      connectedAndIdentified()
      val subscribeTo = PackOut(SubscribeTo(EventStream.All), id, credentials)
      client ! subscribeTo
      client ! packIn(SubscribeToAllCompleted(0))
      client ! PackIn(Try(SubscribeToAllCompleted(0)), id)

      expectMsg(SubscribeToAllCompleted(0))
      expectNoMessage(operationTimeout + 100.millis)

      client ! PackOut(Unsubscribe, id, credentials)
      expectNoMessage(100.millis)
      expectOperationTimedOut(Unsubscribe)

      client ! packIn(Unsubscribed)
      client ! PackIn(Try(Unsubscribed), id)
      expectNoMessage(100.millis)
    }

    "bind actor to correlationId temporarily" in new TcpScope {
      val (connection, tcpConnection) = connect()
      val probe = TestProbe()
      val actor = probe.ref
      connection.tell(Ping, actor)
      val pack = expectPack
      pack.message mustEqual Success(Ping)
      val correlationId = pack.correlationId
      tcpConnection ! write(PackOut(Pong, correlationId))
      probe expectMsg Pong
      tcpConnection ! write(PackOut(Pong, correlationId))
      probe.expectNoMessage(1.second)
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

      val res = PackOut(Pong, req.correlationId)
      tcpConnection ! write(res)
      probe expectMsg Pong

      system stop actor
      deathProbe.expectMsgPF() {
        case Terminated(`actor`) =>
      }

      tcpConnection ! write(res)
      probe expectNoMessage 1.second
      deathProbe expectNoMessage 1.second
    }

    "unsubscribe if not yet subscribed and unsubscribe received" in new SubscriptionScope {
      client ! Unsubscribe
      client ! PackIn(Try(subscribeCompleted), id)
      connection.expectMsg(PackOut(Unsubscribe, id, credentials))
      client ! PackIn(Try(Unsubscribed), id)
      expectMsg(Unsubscribed)
      expectNoMessage(duration)
    }

    "not unsubscribe if not yet subscribed and client died" in new TestScope {
      val probe = TestProbe()
      client.tell(SubscribeTo(EventStream.All), probe.ref)
      system stop probe.ref
      connectedAndIdentified()
      expectNoMessage(duration)
    }

    "unsubscribe if client died" in new SubscriptionScope {
      client ! PackIn(Try(subscribeCompleted), id)
      system stop testActor
      connection.expectMsg(PackOut(Unsubscribe, id, credentials))
    }

    "unsubscribe if not subscribed and client died" in new SubscriptionScope {
      system stop testActor
      connection.expectMsg(PackOut(Unsubscribe, id, credentials))
      expectNoMessage()
    }

    "not unsubscribe twice" in new SubscriptionScope {
      def forStream(/*stream: EventStream,*/ id: Uuid, uc: Option[UserCredentials], probe: TestProbe) = {
        client ! PackIn(Try(subscribeCompleted), id)
        client.tell(Unsubscribe, probe.ref)
        system stop probe.ref
        client.tell(Unsubscribe, probe.ref)
        connection.expectMsg(PackOut(Unsubscribe, id, uc))
        expectNoMessage(duration)
      }
    }

    "not unsubscribe twice if client died" in new SubscriptionScope {
      client ! PackIn(Try(subscribeCompleted), id)
      expectMsg(subscribeCompleted)
      system stop testActor
      connection expectMsg PackOut(Unsubscribe, id, credentials)
      connection.expectNoMessage(duration)
    }

    "re-subscribe after reconnected" in new SubscriptionScope {
      client ! PackIn(Try(subscribeCompleted), id)
      client ! tcpException
      expectConnect()
      connectedAndIdentified()
      connection.expectMsg(PackOut(subscribeTo, id, credentials))
    }

    "not unsubscribe after reconnected" in new SubscriptionScope {
      val completed = subscribeCompleted
      client ! PackIn(Try(completed), id)
      expectMsg(completed)
      client ! Status.Failure(new StreamTcpException("test"))
      expectConnect()
      client ! Unsubscribe
      expectMsg(Unsubscribed)
      connectedAndIdentified()
      connection.expectNoMessage(duration)
    }

    "ignore subscribed while reconnecting" in new SubscriptionScope {
      client ! tcpException
      expectConnect()
      val completed = subscribeCompleted
      val completedEvent = PackIn(Try(completed), id)
      client ! completedEvent
      expectNoMessage(duration)
      connectedAndIdentified()
      connection expectMsg PackOut(subscribeTo, id, credentials)
      client ! completedEvent
      expectMsg(completed)
    }

    "reply with unsubscribed if connection lost while unsubscribing" in new SubscriptionScope {
      val completed = subscribeCompleted
      client ! PackIn(Try(completed), id)
      expectMsg(completed)
      client ! Unsubscribe
      connection expectMsg PackOut(Unsubscribe, id, credentials)
      client ! tcpException
      expectConnect()
      expectMsg(Unsubscribed)
      connectedAndIdentified()
      connection.expectNoMessage(duration)
    }

    "unsubscribe if event appeared and no bound operation found" in new TestScope {
      connectedAndIdentified()
      val corrId = randomUuid
      val eventId = randomUuid
      val eventRecord = EventRecord(EventStream.Id("streamId"), EventNumber.First, EventData("test", eventId))
      val indexedEvent = IndexedEvent(eventRecord, Position.First)
      client ! PackIn(Try(StreamEventAppeared(indexedEvent)), corrId)
      connection expectMsg PackOut(Unsubscribe, corrId, credentials)
    }

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

    "unsubscribe when received SubscribeCompleted but client not found" in new TestScope {
      connectedAndIdentified()
      forall(List(SubscribeToAllCompleted(0), SubscribeToStreamCompleted(0))) {
        in =>
          val pack = packIn(in)
          client ! pack
          connection expectMsg PackOut(Unsubscribe, pack.correlationId, `credentials`)
          success
      }
    }

    "retry operation after reconnected" in new TestScope {
      connectedAndIdentified()
      client ! Authenticate
      val cmd = connection.expectMsgPF() {
        case x @ PackOut(Authenticate, _, `credentials`) => x
      }
      client ! tcpException
      expectConnect()
      connectedAndIdentified()

      connection expectMsg cmd

      client ! tcpException
      expectConnect()
      connectedAndIdentified()

      connection expectMsg cmd
    }

    "retry operation if TooBusy" in new TestScope {
      connectedAndIdentified()
      client ! Authenticate
      val (cmd, id) = connection.expectMsgPF() {
        case x @ PackOut(Authenticate, cid, `credentials`) => (x, cid)
      }

      client ! PackIn(Failure(NotHandled(NotHandled.NotReady)), id)
      connection expectMsg cmd
    }

    "retry operation if NotReady" in new TestScope {
      connectedAndIdentified()
      client ! Authenticate
      val (cmd, id) = connection.expectMsgPF() {
        case x @ PackOut(Authenticate, cid, `credentials`) => (x, cid)
      }

      client ! PackIn(Failure(NotHandled(NotHandled.NotReady)), id)
      connection expectMsg cmd
    }

    "retry operation after connected but NotReady" in new TestScope {
      connectedAndIdentified()
      client ! Authenticate

      val (cmd, id) = connection.expectMsgPF() {
        case x @ PackOut(Authenticate, cid, `credentials`) => (x, cid)
      }
      client ! PackIn(Failure(NotHandled(NotHandled.NotReady)), id)
      connection expectMsg cmd
    }

    "keep retrying until max retries reached" in new TestScope {
      connectedAndIdentified()
      client ! Authenticate
      val (cmd, id) = connection.expectMsgPF() {
        case x @ PackOut(Authenticate, cid, `credentials`) => (x, cid)
      }
      val notReady = PackIn(Failure(NotHandled(NotHandled.NotReady)), id)
      client ! notReady
      connection expectMsg cmd
      client ! notReady
      connection expectMsg cmd
      client ! notReady

      expectMsgPF() {
        case Status.Failure(_: RetriesLimitReachedException) =>
      }

      override def settings = super.settings.copy(operationMaxRetries = 2)
    }

    "keep retrying subscription until max retries reached" in new TestScope {
      connectedAndIdentified()
      client ! SubscribeTo(EventStream.Id("stream"))
      val (cmd, id) = connection.expectMsgPF() {
        case x @ PackOut(_, cid, `credentials`) => (x, cid)
      }

      val tooBusy = PackIn(Failure(NotHandled(NotHandled.TooBusy)), id)
      client ! tooBusy
      connection expectMsg cmd
      client ! tooBusy
      connection expectMsg cmd
      client ! tooBusy

      expectMsgPF() {
        case Status.Failure(_: RetriesLimitReachedException) =>
      }

      override def settings = super.settings.copy(operationMaxRetries = 2)
    }

    "should process messages from single client in parallel" in new TestScope {
      connectedAndIdentified()

      def tell(msg: Out) = {
        client ! msg
        connection.expectMsgPF() {
          case PackOut(`msg`, id, `credentials`) => id
        }
      }

      val id1 = tell(Ping)
      val id2 = tell(Authenticate)

      id1 mustNotEqual id2

      client ! PackIn(Try(Pong), id2)
      //      client ! PackIn(Try(Pong), id1)) TODO this should fail, let's test it, ideally this should trigger retry
      client ! PackIn(Try(Authenticated), id1)
    }

    "process messages from different clients in parallel" in new TestScope {
      connectedAndIdentified()

      def tell(msg: Out, probe: TestProbe) = {
        client.tell(msg, probe.ref)
        connection.expectMsgPF() {
          case PackOut(`msg`, id, `credentials`) => id
        }
      }

      val probe1 = TestProbe()
      val probe2 = TestProbe()
      val id1 = tell(Ping, probe1)
      val id2 = tell(Authenticate, probe2)

      client ! PackIn(Try(Pong), id2)
      //      client ! PackIn(Try(Pong), id1)) TODO this should fail, let's test it, ideally this should trigger retry
      client ! PackIn(Try(Authenticated), id1)
    }

    "ask for address on start" in new ClusterScope {
      discoverer expectMsg GetAddress()
      client ! Address(address)
      tcp expectMsg connect(address)
    }

    "re-connect to new address when notified by discoverer" in new ClusterScope {
      discoverer expectMsg GetAddress()
      client ! Address(address)
      tcp expectMsg connect(address)
      sendConnected(address)

      client ! Address(address2)
      expectTerminated(connection.ref)
      tcp expectMsg connect(address2)
      sendConnected(address2)
    }

    "re-connect to new master on NotMaster failure" in new ClusterScope {
      discoverer expectMsg GetAddress()
      client ! Address(address)
      tcp expectMsg connect(address)
      sendConnected(address)

      val notMaster = NotHandled(NotMaster(MasterInfo(address2, new InetSocketAddress(0))))
      client ! packIn(Failure(notMaster))
      expectTerminated(connection.ref)
      tcp expectMsg connect(address2)
      sendConnected(address2)
    }

    "not re-connect to address if it was not changed" in new ClusterScope {
      discoverer expectMsg GetAddress()
      client ! Address(address)
      tcp expectMsg connect(address)
      connectedAndIdentified(address)

      client ! Address(address)
      expectNoMsgs()
    }

    "not re-connect on bad NotMaster failure" in new ClusterScope {
      discoverer expectMsg GetAddress()
      client ! Address(address)
      tcp expectMsg connect(address)
      connectedAndIdentified(address)

      val notMaster = NotHandled(NotMaster(MasterInfo(address, new InetSocketAddress(0))))
      client ! packIn(Failure(notMaster))
      expectNoMsgs()
    }

    "ask for different address if failed to connect" in new ClusterScope {
      discoverer expectMsg GetAddress()
      client ! Address(address)
      tcp expectMsg connect(address)
      client ! tcpException

      discoverer expectMsg GetAddress(Some(address))
    }

    "stop when cluster failed" in new ClusterScope {
      discoverer expectMsg GetAddress()
      client ! Status.Failure(ClusterException("test"))
      expectTerminated()
    }

    "abort wrong connection" in new TestScope {
      val probe = TestProbe()
      watch(probe.ref)
      client.tell(Connected(new InetSocketAddress(0)), probe.ref)
      expectTerminated(probe.ref)
    }

    "ignore Disconnected" in new TestScope {
      connectedAndIdentified()
      client ! Disconnected(new InetSocketAddress(0))
      expectNoMsgs()
    }

    "handle Disconnect" in new TestScope {
      connectedAndIdentified()
      client ! Disconnected(settings.address)
      expectConnect()
    }

    "stop on ClusterFailure" in new ClusterScope {
      discoverer expectMsg GetAddress()
      watch(client)
      client ! Status.Failure(ClusterException("test"))
      expectTerminated(client)
    }

    "automatically reply on Ping" in new TestScope {
      connectedAndIdentified()
      val ping = packIn(Ping)
      client ! ping
      connection.expectMsg(PackOut(Pong, ping.correlationId))
    }

    "automatically reply on HeartbeatRequest" in new TestScope {
      connectedAndIdentified()
      val heartbeatRequest = packIn(HeartbeatRequest)
      client ! heartbeatRequest
      connection.expectMsg(PackOut(HeartbeatResponse, heartbeatRequest.correlationId))
    }

    /*
    "retry if received NotReady" in new TestScope {
      sendConnected()
      val heartbeatRequest = PackOut(HeartbeatRequest)
      client ! heartbeatRequest
      connection.expectMsg(client)
      client ! PackIn(Failure(NotHandled(NotHandled.NotReady)), id)
      client ! PackIn(Failure(NotReady))
    }*/
  }

  abstract class TcpScope extends ActorScope {
    val (address, socket) = bind()
    def settings = Settings(address = address)

    def connect(settings: Settings = settings): (TestActorRef[ConnectionActor], ActorRef) = {
      val connection = newConnection(settings)
      val tcpConnection = newTcpConnection()
      receiveN(1) // IdentifyClient (no reader as it used just used for Out to ES)
      expectPack.message mustEqual Success(HeartbeatRequest)
      connection -> tcpConnection
    }

    def newConnection(settings: Settings = settings) = TestActorRef(new ConnectionActor(settings))

    def newTcpConnection() = {
      expectMsgType[Tcp.Connected]
      val connection = lastSender
      connection ! Tcp.Register(self)
      connection
    }

    def write(x: PackOut): Tcp.Write = Tcp.Write(Frame.toByteString(x))

    def packOut(message: Out): PackOut =
      PackOut(message, randomUuid, None)

    def write(x: Out): Tcp.Write = write(packOut(x))

    def bind(address: InetSocketAddress = new InetSocketAddress("127.0.0.1", 0)): (InetSocketAddress, ActorRef) = {
      IO(Tcp) ! Tcp.Bind(self, address)
      val bound = expectMsgType[Tcp.Bound]
      bound.localAddress -> lastSender
    }

    def expectPack = Frame.readIn(expectMsgType[Tcp.Received].data)
    def expectPackOut = Frame.readOut(expectMsgType[Tcp.Received].data)

    def unbind(socket: ActorRef): Unit = {
      socket ! Tcp.Unbind
      expectMsg(Tcp.Unbound)
    }
  }

  object Frame {

    import _root_.akka.util.{ByteString => ABS}
    import scodec.bits.{ByteOrdering, ByteVector}
    import EventStoreFormats._

    val byteOrder = ByteOrdering.LittleEndian

    def readIn(bs: ABS): PackIn = {
      PackInReader.read(ByteVector(bs.toArray).drop(4)).unsafe.value
    }

    def readOut(bs: ABS): PackOut = {

      val readPack = for {
            mb <- BytesReader[MarkerByte]
        reader <- BytesReader.lift(MarkerBytes.readerBy(mb))
        flags  <- BytesReader[Flags]
        corrId <- BytesReader[Uuid]
        creds  <- if ((flags & Flag.Auth) == 0) BytesReader.pure[Option[UserCredentials]](None)
                  else BytesReader[UserCredentials].map(Option(_))
           msg <- reader
      } yield PackOut(msg.get.asInstanceOf[Out], corrId, creds)

      readPack.read(ByteVector(bs.toArray).drop(4)).unsafe.value
    }

    def toByteString(pack: PackOut): ABS = {

      val data   = BytesWriter[PackOut].write(pack)
      val length = ByteVector.fromInt(data.size.toInt, ordering = byteOrder)

      ABS((length ++ data).toArray)

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
    val stream: EventStream = EventStream.Id("stream")
    val subscribeTo = SubscribeTo(stream)
    val subscribeCompleted = SubscribeToStreamCompleted(0)

    connectedAndIdentified()
    client ! subscribeTo
    val id = connection.expectMsgPF() { case PackOut(`subscribeTo`, cid, `credentials`) => cid }

    override def settings = Settings(maxReconnections = 1, heartbeatInterval = 10.seconds, heartbeatTimeout = 20.seconds)
  }

  trait TestScope extends CommonScope {

    private def connectionActor = new ConnectionActor(settings) {
      override def connect(address: InetSocketAddress): Unit = TestScope.this.tcp.ref ! Connect(address)
    }

    val client = TestActorRef(connectionActor)

    expectConnect()
  }

  trait CommonScope extends ActorScope {
    def settings: Settings = Settings()

    val duration = 1.second
    lazy val credentials = settings.defaultCredentials

    val tcp = TestProbe()
    val connection = {
      val probe = TestProbe()
      watch(probe.ref)
      probe
    }

    val tcpException = Status.Failure(new StreamTcpException("test"))

    def packIn(message: In): PackIn       = packIn(Try(message))
    def packIn(message: Try[In]): PackIn  = PackIn(message, randomUuid)

    def connect(address: InetSocketAddress = settings.address) = {
      Connect(address)
    }

    def client: TestActorRef[ConnectionActor]

    def expectConnect(): Unit = {
      tcp expectMsg connect()
    }

    def sendConnected(address: InetSocketAddress = settings.address): Unit = {
      client.tell(Connected(address), connection.ref)
    }

    def connectedAndIdentified(address: InetSocketAddress = settings.address): Unit = {
      sendConnected(address)
      connection.expectMsgPF() {
        case PackOut(IdentifyClient(1, _), _, _) => client ! ClientIdentified
      }
    }

    def expectNoMsgs(): Unit = {
      tcp.expectNoMessage(duration)
      connection.expectNoMessage(duration)
      expectNoMessage(duration)
    }

    def expectTerminated(): Unit = {
      val deathWatch = TestProbe()
      deathWatch watch client
      deathWatch.expectMsgPF() {
        case Terminated(x) if x == client => true
      }
    }

    def verifyReconnections(n: Int): Unit = if (n > 0) {
      expectConnect()
      client ! tcpException
      verifyReconnections(n - 1)
    }

    case class Connect(address: InetSocketAddress)

  }
  
  trait OperationTimedOutScope extends TestScope {
    def operationTimeout = settings.operationTimeout
    val id = randomUuid

    def expectOperationTimedOut(x: AnyRef, xs: AnyRef*): Unit = {

      def removeOne[T](xs: Seq[T], x: T): Seq[T] = {
        val (h, t) = xs.span(_ != x)
        h ++ t.drop(1)
      }

      def expect(xs: Seq[AnyRef]): Unit = {
        if (xs.nonEmpty) expectMsgPF() {
          case Status.Failure(OperationTimeoutException(PackOut(o, _, `credentials`))) if xs contains o =>
            expect(removeOne(xs, o))
          case Status.Failure(OperationTimeoutException(o)) if xs contains o =>
            expect(removeOne(xs, o))
        }
      }

      expect(x +: xs)
    }

    override def settings = super.settings.copy(operationTimeout = 500.millis)
  }

  trait ClusterScope extends CommonScope {
    val address = "127.0.0.1" :: 1
    val address2 = "127.0.0.1" :: 2
    val discoverer = TestProbe()

    override def settings = super.settings.copy(cluster = Some(ClusterSettings(GossipSeeds(address))))

    private def connectionActor = new ConnectionActor(settings) {
      override def newClusterDiscoverer(settings: ClusterSettings, useTls: Boolean) = discoverer.ref
      override def connect(address: InetSocketAddress): Unit = ClusterScope.this.tcp.ref ! Connect(address)
    }

    val client = TestActorRef(connectionActor)

  }
}
