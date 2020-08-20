package eventstore
package akka
package tcp

import java.net.InetSocketAddress
import javax.net.ssl._
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}
import _root_.akka.actor._
import _root_.akka.stream.scaladsl._
import _root_.akka.stream.{BufferOverflowException, CompletionStrategy, IgnoreComplete, StreamTcpException}
import eventstore.core.{HeartbeatRequest, HeartbeatResponse}
import eventstore.core.NotHandled.NotMaster
import eventstore.core.settings.ClusterSettings
import eventstore.core.cluster.ClusterException
import eventstore.core.util.{DelayedRetry, OneToMany}
import eventstore.core.tcp.{PackIn, PackOut}
import eventstore.core.operations._
import eventstore.akka.cluster.ClusterDiscovererActor.GetAddress
import eventstore.akka.cluster.ClusterDiscovererActor
import eventstore.akka.cluster.ClusterInfoOf

object ConnectionActor {

  def props(settings: Settings = Settings.Default): Props = Props(new ConnectionActor(settings))

  /**
   * Java API
   */
  def getProps(): Props = props()

  /**
   * Java API
   */
  def getProps(settings: Settings): Props = props(settings)

  private[eventstore]type Operations = OneToMany[Operation[Client], Uuid, Client]

  private[eventstore] object Operations {
    val Empty: Operations = OneToMany[Operation[Client], Uuid, Client](_.id, _.client)
  }

  private[eventstore] final case class Connect(address: InetSocketAddress)
  private[eventstore] final case class Connected(address: InetSocketAddress)
  private[eventstore] final case class TimedOut(id: Uuid, version: Int)
  private[eventstore] final case class Disconnected(address: InetSocketAddress)

  private[eventstore] object TcpFailure {
    def unapply(failure: Status.Failure): Option[RuntimeException] = PartialFunction.condOpt(failure.cause) {
      case x: StreamTcpException      => x
      case x: BufferOverflowException => x
    }
  }

  private[eventstore] object BufferFailure {
    def unapply(failure: Status.Failure): Option[BufferOverflowException] = {
      PartialFunction.condOpt(failure.cause) { case x: BufferOverflowException => x }
    }
  }

  private[eventstore] object ClusterFailure {
    def unapply(failure: Status.Failure): Option[ClusterException] = {
      PartialFunction.condOpt(failure.cause) { case x: ClusterException => x }
    }
  }
}

private[eventstore] class ConnectionActor(settings: Settings) extends Actor with ActorLogging {
  import ConnectionActor._
  import context.{ dispatcher, system }

  type Reconnect = (InetSocketAddress, Operations) => Option[Receive]

  val flow = EventStoreFlow(settings.heartbeatInterval, settings.serializationParallelism, settings.serializationOrdered, log)
  val tcp = Tcp(system)
  val sslContext = if(settings.enableTcpTls) Some(Tls.createSSLContext(system)) else None

  val identifyClient = IdentifyClient(version = 1, connectionName = settings.connectionName)

  lazy val clusterDiscoverer: Option[ActorRef] = settings.cluster.map(newClusterDiscoverer(_, settings.enableTcpTls))
  lazy val delayedRetry = DelayedRetry.opt(
    left = settings.maxReconnections,
    delay = settings.reconnectionDelayMin,
    maxDelay = settings.reconnectionDelayMax
  )

  def receive = {
    val address = clusterDiscoverer match {
      case Some(cd) =>
        cd ! ClusterDiscovererActor.GetAddress()
        None
      case None =>
        connect("Connecting", Duration.Zero)
        Some(settings.address)
    }
    connecting(address, Operations.Empty, reconnect(_, _, None))
  }

  def connecting(address: Option[InetSocketAddress], os: Operations, recover: Reconnect): Receive = {
    def connecting(os: Operations) = this.connecting(address, os, recover)

    val rcvAddressOrConnected: Receive = address match {
      case Some(addr) => rcvConnected(addr, os, recover)
      case None       => rcvAddress(os, address => this.connecting(Some(address), os, recover))
    }

    rcvAddressOrConnected or rcvPack(os, connecting, None)
  }

  def connected(os: Operations, connection: Connection): Receive = {
    val address = connection.address

    def connected(os: Operations): Receive = {

      def reconnect(reason: String, newAddress: InetSocketAddress = address) = {
        connection.unwatch()
        val msg = s"Connection lost to $address: $reason"
        this.reconnect(newAddress, os) match {
          case None => connectionFailed(msg, os)
          case Some(rcv) =>
            log.warning(msg)
            context become rcv
        }
      }

      val rcvAddress: Receive = clusterDiscoverer match {
        case None => PartialFunction.empty
        case Some(cd) =>
          def reconnect(newAddress: InetSocketAddress, reason: String): Unit = if (newAddress != address) {
            log.info("Address changed from {} to {}: {}", address, newAddress, reason)
            connection.stop()

            val result = os.flatMap { operation =>
              operation.disconnected match {
                case OnDisconnected.Continue(op) => List(op)
                case OnDisconnected.Stop(in)     => operation.client(in); Nil
              }
            }

            def renewAddress: Reconnect = (_, os) => {
              cd ! GetAddress()
              Some(connecting(None, os, renewAddress))
            }

            connect("Connecting", Duration.Zero, newAddress)
            context become connecting(Some(newAddress), result, renewAddress)
          }

          {
            case ClusterDiscovererActor.Address(x)            => reconnect(x, "discovered better node")
            case PackIn(Failure(NotHandled(NotMaster(x))), _) => reconnect(x.tcpAddress, "NotMaster failure received")
            case ClusterFailure(x) =>
              log.error("Cluster failed with error: {}", x)
              context stop self
          }
      }

      val rcvDisconnected: Receive = {
        case Terminated(connection()) => reconnect("source terminated")
        case Disconnected(`address`)  => reconnect("sink disconnected")
        case Disconnected(_)          =>
        case TcpFailure(x)            => reconnect(x.toString)
      }

      rcvDisconnected or rcvAddress or rcvPack(os, connected, Some(connection))
    }

    connected(os)
  }

  def rcvPack(os: Operations, rcv: Operations => Receive, connection: Option[Connection]): Receive = {
    def send(packOut: PackOut) = for { connection <- connection } connection(packOut)

    def inspectIn(msg: Try[In], operation: Operation[Client]) = operation.inspectIn(msg) match {
      case OnIncoming.Ignore   => os
      case OnIncoming.Stop(in) => stopOperation(operation, os, in)
      case OnIncoming.Retry(op, packOut) =>
        send(packOut)
        os + op
      case OnIncoming.Continue(op, in) =>
        op.client(in)
        os + op
    }

    def onPackIn(packIn: PackIn) = {
      val correlationId = packIn.correlationId
      val msg = packIn.message

      def forward: Operations = os.single(correlationId) map { operation =>
        inspectIn(msg, operation)
      } getOrElse {
        msg match {
          case Failure(m) => log.warning("Cannot deliver {}, client not found for correlationId: {}", m, correlationId)
          case Success(m) => m match {
            case Pong | HeartbeatResponse | Unsubscribed | ClientIdentified =>
            case _: SubscribeCompleted | _: StreamEventAppeared =>
              log.warning("Cannot deliver {}, client not found for correlationId: {}, unsubscribing", m, correlationId)
              send(PackOut(Unsubscribe, correlationId, settings.defaultCredentials))

            case _ => log.warning("Cannot deliver {}, client not found for correlationId: {}", m.getClass, correlationId)
          }
        }
        os
      }

      msg match {
        case Success(HeartbeatRequest) => send(PackOut(HeartbeatResponse, correlationId))
        case Success(Ping)             => send(PackOut(Pong, correlationId))
        case _                         => context become rcv(forward)
      }
    }

    def onPackOut(packOut: PackOut): Unit = {
      val msg = packOut.message
      val id = packOut.correlationId
      val client = Client(sender())

      def isDefined(x: Iterable[Operation[Client]]) = x.find(_.inspectOut.isDefinedAt(msg))
      def forId = isDefined(os.single(id))
      def forMsg = isDefined(os.many(client))

      // TODO current requirement is the only one subscription per actor allowed
      val result = forId orElse forMsg match {
        case Some(operation) =>
          operation.inspectOut(msg) match {
            case OnOutgoing.Stop(po, in) =>
              send(po)
              stopOperation(operation, os, in)

            case OnOutgoing.Continue(op, po) =>
              send(po)
              system.scheduler.scheduleOnce(settings.operationTimeout, self, TimedOut(id, op.version))
              os + op
          }

        case None =>
          Operation.opt(packOut, client, connection.isDefined, settings.operationMaxRetries).fold(os) { operation =>
            client.watch()
            send(packOut)
            system.scheduler.scheduleOnce(settings.operationTimeout, self, TimedOut(id, operation.version))
            os + operation
          }
      }
      context become rcv(result)
    }

    def onTimedOut(timedOut: TimedOut) = {
      val operation = os.single(timedOut.id)
      for { operation <- operation if operation.version == timedOut.version } {
        val result = inspectIn(Failure(OperationTimedOut), operation)
        context become rcv(result)
      }
    }

    def onTerminated(client: Client) = {
      client.unwatch()
      val operations = os.many(client)
      if (operations.nonEmpty) {
        for {
          connection <- connection.toList
          operation <- operations
          packOut <- operation.clientTerminated
        } connection(packOut)
        context become rcv(os -- operations)
      }
    }

    {
      case x: PackOut    => onPackOut(x)
      case x: OutLike    => onPackOut(PackOut(x.out, randomUuid, credentials(x)))
      case x: PackIn     => onPackIn(x)
      case x: TimedOut   => onTimedOut(x)
      case Terminated(x) => onTerminated(Client(x))
    }
  }

  def rcvAddress(os: Operations, rcv: InetSocketAddress => Receive): Receive = {
    case ClusterDiscovererActor.Address(address) =>
      connect("Connecting", Duration.Zero, address)
      context become rcv(address)

    case ClusterFailure(x) => connectionFailed(s"Cluster failed with error: $x", x, os)
  }

  def rcvConnected(address: InetSocketAddress, os: Operations, reconnect: Reconnect): Receive = {
    case Connected(`address`) =>
      log.info("Connected to {}", address)
      val connection = Connection(address, sender(), context)
      connection(PackOut(identifyClient, randomUuid))
      val result = os.flatMap { operation =>
        operation.connected match {
          case OnConnected.Retry(op, packOut) =>
            system.scheduler.scheduleOnce(settings.operationTimeout, self, TimedOut(packOut.correlationId, op.version))
            connection(packOut)
            List(op)
          case OnConnected.Stop(in) =>
            operation.client(in)
            Nil
        }
      }
      context become connected(result, connection)

    case Connect(`address`) => connect(address)

    case x: Connected =>
      log.debug("Received unexpected {}", x)
      system stop sender()

    case TcpFailure(x) =>
      val msg = s"Connection failed to $address: $x"
      reconnect(address, os) match {
        case None => connectionFailed(msg, os)
        case Some(rcv) =>
          log.warning(msg)
          context become rcv
      }
  }

  def reconnect(address: InetSocketAddress, os: Operations, retry: Option[DelayedRetry] = delayedRetry): Option[Receive] = {
    val result = os.flatMap { operation =>
      operation.disconnected match {
        case OnDisconnected.Continue(op) => List(op)
        case OnDisconnected.Stop(in)     => operation.client(in); Nil
      }
    }

    clusterDiscoverer match {
      case Some(cd) =>
        def reconnect(address: InetSocketAddress, os: Operations): Option[Receive] = {
          cd ! GetAddress(Some(address))
          Some(connecting(None, os, reconnect))
        }
        reconnect(address, result)

      case None =>
        def reconnect(retry: Option[DelayedRetry], address: InetSocketAddress, os: Operations): Option[Receive] = {
          retry.map { retry =>
            connect("Reconnecting", retry.delay)
            connecting(Some(address), os, reconnect(retry.next, _, _))
          }
        }
        reconnect(retry, address, result)
    }
  }

  def newClusterDiscoverer(settings: ClusterSettings, useTls: Boolean): ActorRef = {
    context.actorOf(ClusterDiscovererActor.props(settings, ClusterInfoOf(useTls).apply), "cluster")
  }

  def credentials(x: OutLike): Option[UserCredentials] = x match {
    case WithCredentials(_, c) => Some(c)
    case _: Out                => settings.defaultCredentials
  }

  def connect(label: String, in: FiniteDuration, address: InetSocketAddress = settings.address): Unit = {
    if (in == Duration.Zero) {
      log.debug("{} to {}", label, address)
      connect(address)
    } else {
      log.debug("{} to {} in {}", label, address, in)
      system.scheduler.scheduleOnce(in, self, Connect(address))
      ()
    }
  }

  def connect(address: InetSocketAddress): Unit = {

    import settings.{connectionTimeout, heartbeatTimeout, bufferSize, bufferOverflowStrategy}

    def secureConnection(ctx: SSLContext) = {
      def createEngine() = Tls.createSSLEngine(address.getHostString, address.getPort, ctx)
      tcp.outgoingConnectionWithTls(
        address, createEngine, None, Nil, connectionTimeout, heartbeatTimeout,_ => Success(()), IgnoreComplete
      )
    }

    def insecureConnection = tcp.outgoingConnection(
      remoteAddress = address, connectTimeout = connectionTimeout, idleTimeout = heartbeatTimeout
    )

    val connection = sslContext match {
      case Some(v) => secureConnection(v)
      case None    => insecureConnection
    }

    val completionMatcher: PartialFunction[Any, CompletionStrategy] = {
      case Status.Success(s: CompletionStrategy) => s
      case Status.Success(_)                     => CompletionStrategy.draining
      case Status.Success                        => CompletionStrategy.draining
    }

    val failureMatcher: PartialFunction[Any, Throwable] = {
      case Status.Failure(cause) => cause
    }

    // completionMatcher & failureMatcher are the same as the deprecated `Source.actorRef` Akka 2.6.0 uses.
    val source = Source.actorRef(completionMatcher, failureMatcher, bufferSize, bufferOverflowStrategy.toAkka)

    // onFailureMessage is the same as the deprecated `Sink.actorRef` in Akka 2.6.0 uses.
    val sink = Sink.actorRef(self, Disconnected(address), th => Status.Failure(th))

    val (ref, connected) = source.viaMat(connection.join(flow))(Keep.both).toMat(sink)(Keep.left).run()
    for { _ <- connected } self.tell(Connected(address), ref)
  }

  def connectionFailed(msg: String, e: EsException, os: Operations): Unit = {
    log.error(msg)
    val failure = Status.Failure(e)
    for { client <- os.manySet } client(failure)
    context stop self
  }

  def connectionFailed(msg: String, os: Operations): Unit = {
    connectionFailed(msg, CannotEstablishConnectionException(msg), os)
  }

  def stopOperation(operation: Operation[Client], os: Operations, in: Try[In]): Operations = {
    val client = operation.client
    client(in)
    val result = os - operation
    if (!(result contains client)) client.unwatch()
    result
  }
}