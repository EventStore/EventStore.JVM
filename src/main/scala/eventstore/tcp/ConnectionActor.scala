package eventstore
package tcp

import java.net.InetSocketAddress
import akka.actor._
import akka.io.{ IO, Tcp }
import eventstore.NotHandled.NotMaster
import eventstore.cluster.ClusterDiscovererActor.GetAddress
import eventstore.cluster.{ ClusterDiscovererActor, ClusterException, ClusterInfo, ClusterSettings }
import eventstore.operations._
import eventstore.pipeline._
import eventstore.util.{ CancellableAdapter, DelayedRetry }
import scala.concurrent.duration._
import scala.util.{ Failure, Success, Try }

object ConnectionActor {
  def props(settings: Settings = Settings.Default): Props = Props(classOf[ConnectionActor], settings)

  /**
   * Java API
   */
  def getProps(): Props = props()

  /**
   * Java API
   */
  def getProps(settings: Settings): Props = props(settings)
}

private[eventstore] class ConnectionActor(settings: Settings) extends Actor with ActorLogging {

  import context.{ dispatcher, system }
  import settings._

  type Reconnect = (InetSocketAddress, Operations) => Option[Receive]

  val init = EsPipelineInit(log, backpressure)

  lazy val clusterDiscoverer: Option[ActorRef] = cluster.map(newClusterDiscoverer)
  lazy val delayedRetry = DelayedRetry.opt(maxReconnections, reconnectionDelayMin, reconnectionDelayMax)

  def receive = {
    val address = clusterDiscoverer match {
      case Some(clusterDiscoverer) =>
        clusterDiscoverer ! ClusterDiscovererActor.GetAddress()
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
      case Some(address) => rcvConnected(address, os, recover)
      case None          => rcvAddress(os, address => this.connecting(Some(address), os, recover))
    }

    rcvIncoming(os, connecting, None) or
      rcvOutgoing(os, connecting, None) or
      rcvAddressOrConnected or
      rcvTimedOut(os, connecting, None) or
      rcvTerminated(os, connecting, None)
  }

  def connected(
    address: InetSocketAddress,
    os: Operations,
    connection: ActorRef,
    pipeline: ActorRef,
    heartbeatId: Long): Receive = {
    val scheduled = CancellableAdapter(
      system.scheduler.scheduleOnce(heartbeatInterval, self, Heartbeat),
      system.scheduler.scheduleOnce(heartbeatInterval + heartbeatTimeout, self, HeartbeatTimeout(heartbeatId)))

    def connected(os: Operations): Receive = {

      def outFunc(pack: PackOut): Unit = toPipeline(pipeline, pack)

      def reconnect(reason: String, newAddress: InetSocketAddress = address) = {
        if (!scheduled.isCancelled) scheduled.cancel()
        val msg = s"Connection lost to $address: $reason"
        this.reconnect(newAddress, os) match {
          case None => connectionFailed(msg, os)
          case Some(rcv) =>
            log.warning(msg)
            context become rcv
        }
      }

      def onIn(os: Operations) = {
        scheduled.cancel()
        this.connected(address, os, connection, pipeline, heartbeatId + 1)
      }

      val rcvAddress: Receive = clusterDiscoverer match {
        case None => PartialFunction.empty
        case Some(clusterDiscoverer) =>
          def reconnect(newAddress: InetSocketAddress, reason: String): Unit = if (newAddress != address) {
            log.info("Address changed from {} to {}: {}", address, newAddress, reason)
            connection ! Tcp.Abort

            val result = os.flatMap { operation =>
              operation.disconnected match {
                case OnDisconnected.Continue(operation) => Iterable(operation)
                case OnDisconnected.Stop(in) =>
                  toClient(operation.client, in)
                  Iterable()
              }
            }

            def renewAddress(address: InetSocketAddress, os: Operations): Option[Receive] = {
              clusterDiscoverer ! GetAddress()
              Some(connecting(None, os, renewAddress))
            }

            if (!scheduled.isCancelled) scheduled.cancel()
            connect("Connecting", Duration.Zero, newAddress)
            context become connecting(Some(newAddress), result, renewAddress)
          }

          {
            case ClusterDiscovererActor.Address(x) =>
              reconnect(x, "discovered better node")

            case init.Event(PackIn(Failure(NotHandled(NotMaster(x))), _)) =>
              log.info(x.toString)
              reconnect(x.tcpAddress, "NotMaster failure received")

            case Status.Failure(e: ClusterException) =>
              log.error("Cluster failed with error: {}", e)
              context stop self
          }
      }

      val receive: Receive = {
        case x: Tcp.ConnectionClosed => x match {
          case Tcp.PeerClosed         => reconnect("peer closed")
          case Tcp.ErrorClosed(error) => reconnect(error.toString)
          case _                      => log.info("closing connection to {}", address)
        }

        case Terminated(`connection`) =>
          context unwatch connection
          reconnect("connection actor died")

        case Terminated(`pipeline`) =>
          context unwatch pipeline
          connection ! Tcp.Abort
          reconnect("pipeline actor died")

        case Heartbeat => heartbeat(pipeline)

        case HeartbeatTimeout(id) => if (id == heartbeatId) {
          connection ! Tcp.Close
          reconnect(s"no heartbeat within $heartbeatTimeout")
        }
      }

      receive or
        rcvAddress or
        rcvIncoming(os, onIn, Some(outFunc)) or
        rcvOutgoing(os, connected, Some(outFunc)) or
        rcvTimedOut(os, connected, Some(outFunc)) or
        rcvTerminated(os, connected, Some(outFunc))
    }

    connected(os)
  }

  def rcvIncoming(os: Operations, rcv: Operations => Receive, outFunc: Option[PackOut => Unit]): Receive = {
    case init.Event(in) =>
      def reply(out: PackOut) = outFunc.foreach(_.apply(out))

      val correlationId = in.correlationId
      val msg = in.message

      def forward: Operations = {
        os.single(correlationId) match {
          case Some(operation) =>
            operation.inspectIn(msg) match {
              case OnIncoming.Ignore   => os

              case OnIncoming.Stop(in) => stopOperation(operation, os, in)

              case OnIncoming.Retry(operation, pack) =>
                outFunc.foreach { outFunc => outFunc(pack) }
                os + operation

              case OnIncoming.Continue(operation, in) =>
                toClient(operation.client, in)
                os + operation
            }

          case None =>
            msg match {
              case Failure(x) => log.warning("Cannot deliver {}, client not found for correlationId: {}", msg, correlationId)
              case Success(msg) => msg match {
                case Pong | HeartbeatResponse | Unsubscribed =>
                case _: SubscribeCompleted | _: StreamEventAppeared =>
                  log.warning("Cannot deliver {}, client not found for correlationId: {}, unsubscribing", msg, correlationId)
                  reply(PackOut(Unsubscribe, correlationId, defaultCredentials))

                case _ => log.warning("Cannot deliver {}, client not found for correlationId: {}", msg, correlationId)
              }
            }
            os
        }
      }

      logDebug(in)

      msg match {
        case Success(HeartbeatRequest) => reply(PackOut(HeartbeatResponse, correlationId))
        case Success(Ping)             => reply(PackOut(Pong, correlationId))
        case _                         => context become rcv(forward)
      }
  }

  def rcvTimedOut(os: Operations, rcv: Operations => Receive, outFunc: Option[PackOut => Unit]): Receive = {
    case TimedOut(id, version) =>
      val operation = os.single(id)
      operation.foreach { operation =>
        if (operation.version == version) {
          val result = operation.inspectIn(Failure(OperationTimedOut)) match {
            case OnIncoming.Ignore   => os

            case OnIncoming.Stop(in) => stopOperation(operation, os, in)

            case OnIncoming.Retry(operation, pack) =>
              outFunc.foreach { outFunc => outFunc(pack) }
              os + operation

            case OnIncoming.Continue(operation, in) =>
              toClient(operation.client, in)
              os + operation
          }
          context become rcv(result)
        }
      }
  }

  def rcvOutgoing(os: Operations, rcv: Operations => Receive, outFunc: Option[PackOut => Unit]): Receive = {

    def rcvPack(pack: PackOut): Unit = {
      val msg = pack.message
      val id = pack.correlationId
      val client = sender()

      def isDefined(x: Iterable[Operation]) = x.find(_.inspectOut.isDefinedAt(msg))
      def forId = isDefined(os.single(id))
      def forMsg = isDefined(os.many(client))

      // TODO current requirement is the only one subscription per actor allowed
      val result = forId orElse forMsg match {
        case Some(operation) =>
          operation.inspectOut(msg) match {
            case OnOutgoing.Stop(out, in) =>
              outFunc.foreach { outFunc => outFunc(out) }
              stopOperation(operation, os, in)

            case OnOutgoing.Continue(operation, out) =>
              outFunc.foreach { outFunc => outFunc(out) }
              system.scheduler.scheduleOnce(operationTimeout, self, TimedOut(id, operation.version))
              os + operation
          }

        case None =>
          Operation.opt(pack, client, outFunc.isDefined, operationMaxRetries).fold(os) {
            operation =>
              context watch client
              outFunc.foreach(_.apply(pack))
              system.scheduler.scheduleOnce(operationTimeout, self, TimedOut(id, operation.version))
              os + operation
          }
      }
      context become rcv(result)
    }

    {
      case x: PackOut => rcvPack(x)
      case x: OutLike => rcvPack(PackOut(x.out, randomUuid, credentials(x)))
    }
  }

  def rcvTerminated(os: Operations, rcv: Operations => Receive, outFunc: Option[PackOut => Unit]): Receive = {
    case Terminated(client) =>
      context unwatch client
      val terminated = os.many(client)
      if (terminated.nonEmpty) {
        for {
          f <- outFunc.toList
          o <- terminated
          p <- o.clientTerminated
        } f(p)
        context become rcv(os -- terminated)
      }
  }

  def rcvAddress(os: Operations, rcv: InetSocketAddress => Receive): Receive = {
    case ClusterDiscovererActor.Address(address) =>
      connect("Connecting", Duration.Zero, address) // TODO TEST
      context become rcv(address)

    case Status.Failure(e: ClusterException) => connectionFailed(s"Cluster failed with error: $e", e, os)
  }

  def rcvConnected(address: InetSocketAddress, os: Operations, reconnect: Reconnect): Receive = {
    case Tcp.Connected(`address`, _) =>
      log.info("Connected to {}", address)
      val connection = sender()
      val pipeline = newPipeline(connection)
      connection ! Tcp.Register(pipeline)

      val result = os.flatMap { operation =>
        operation.connected match {
          case OnConnected.Retry(o, p) =>
            toPipeline(pipeline, p)
            Iterable(o)
          case OnConnected.Stop(in) =>
            toClient(operation.client, in)
            Iterable()
        }
      }
      context watch connection
      context watch pipeline
      heartbeat(pipeline)
      context become connected(address, result, connection, pipeline, 0)

    case x: Tcp.Connected =>
      log.debug("Received unexpected {}", x)
      sender() ! Tcp.Abort

    case Tcp.CommandFailed(connect: Tcp.Connect) if connect.remoteAddress == address =>
      val address = connect.remoteAddress
      val msg = s"Connection failed to $address"
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
        case OnDisconnected.Continue(operation) => Iterable(operation)
        case OnDisconnected.Stop(in) =>
          toClient(operation.client, in)
          Iterable()
      }
    }

    clusterDiscoverer match {
      case Some(clusterDiscoverer) =>
        def reconnect(address: InetSocketAddress, os: Operations): Option[Receive] = {
          clusterDiscoverer ! GetAddress(Some(address))
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

  def heartbeat(pipeline: ActorRef): Unit = {
    toPipeline(pipeline, PackOut(HeartbeatRequest))
  }

  def toClient(client: ActorRef, in: Try[In]): Unit = {
    val msg = in match {
      case Success(x) => x
      case Failure(x) => Status.Failure(x)
    }
    client ! msg
  }

  def toPipeline(pipeline: ActorRef, pack: PackOut): Unit = {
    logDebug(pack)
    pipeline ! init.Command(pack)
  }

  def newPipeline(connection: ActorRef): ActorRef = {
    context actorOf TcpPipelineHandler.props(init, connection, self)
  }

  def newClusterDiscoverer(settings: ClusterSettings): ActorRef = {
    context.actorOf(ClusterDiscovererActor.props(settings, ClusterInfo.futureFunc), "cluster")
  }

  def credentials(x: OutLike): Option[UserCredentials] = x match {
    case WithCredentials(_, c) => Some(c)
    case _: Out                => defaultCredentials
  }

  def connect(label: String, in: FiniteDuration, address: InetSocketAddress = address): Unit = {
    val connect = Tcp.Connect(address, timeout = Some(connectionTimeout))
    if (in == Duration.Zero) {
      log.debug("{} to {}", label, address)
      tcp ! connect
    } else {
      log.debug("{} to {} in {}", label, address, in)
      system.scheduler.scheduleOnce(in, tcp, connect)
    }
  }

  def tcp = IO(Tcp)

  def connectionFailed(msg: String, e: EsException, os: Operations): Unit = {
    log.error(msg)
    val failure = Status.Failure(e)
    os.manySet.foreach { client => client ! failure }
    context stop self
  }

  def connectionFailed(msg: String, os: Operations): Unit = {
    connectionFailed(msg, new CannotEstablishConnectionException(msg), os)
  }

  def stopOperation(operation: Operation, os: Operations, in: Try[In]): Operations = {
    val client = operation.client
    toClient(client, in)
    val result = os - operation
    if (!(result contains client)) context unwatch client
    result
  }

  def logDebug(x: PackIn) = if (log.isDebugEnabled) x.message match {
    case Success(HeartbeatRequest) =>
    case _                         => log.debug(x.toString)
  }

  def logDebug(x: PackOut) = if (log.isDebugEnabled) x.message match {
    case HeartbeatResponse =>
    case _                 => log.debug(x.toString)
  }

  case class HeartbeatTimeout(id: Long)
  case object Heartbeat
  case class TimedOut(id: Uuid, version: Int)
}