package eventstore
package tcp

import java.net.InetSocketAddress
import akka.actor._
import akka.io.{ Tcp, IO }
import eventstore.NotHandled.NotMaster
import eventstore.cluster.ClusterDiscovererActor.GetAddress
import eventstore.cluster.{ ClusterException, ClusterSettings, ClusterInfo, ClusterDiscovererActor }
import eventstore.pipeline._
import eventstore.operations._
import eventstore.util.{ CancellableAdapter, DelayedRetry }
import scala.concurrent.duration._
import scala.util.{ Try, Failure, Success }

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

  import context.dispatcher
  import context.system
  import settings._

  type Reconnect = (InetSocketAddress, Operations) => Option[Receive]

  val init = EsPipelineInit(log, backpressure)

  lazy val clusterDiscoverer: Option[ActorRef] = cluster.map(newClusterDiscoverer)
  lazy val delayedRetry = DelayedRetry.opt(maxReconnections, reconnectionDelayMin, reconnectionDelayMax)

  def receive = {
    clusterDiscoverer match {
      case Some(clusterDiscoverer) => clusterDiscoverer ! ClusterDiscovererActor.GetAddress()
      case None                    => connect("Connecting", Duration.Zero)
    }
    connecting(Operations.Empty, reconnect(None, _, _))
  }

  def connecting(operations: Operations, recover: Reconnect): Receive = {
    def connecting(operations: Operations) = this.connecting(operations, recover)
    rcvIncoming(operations, connecting, None) or
      rcvOutgoing(operations, connecting, None) or
      rcvConnected(operations, recover) or
      rcvTimedOut(operations, connecting, None) or
      rcvTerminated(operations, connecting, None)
  }

  def connected(
    address: InetSocketAddress,
    operations: Operations,
    connection: ActorRef,
    pipeline: ActorRef,
    heartbeatId: Long): Receive = {
    val scheduled = CancellableAdapter(
      system.scheduler.scheduleOnce(heartbeatInterval, self, Heartbeat),
      system.scheduler.scheduleOnce(heartbeatInterval + heartbeatTimeout, self, HeartbeatTimeout(heartbeatId)))

    def connected(operations: Operations): Receive = {

      def outFunc(pack: PackOut): Unit = toPipeline(pipeline, pack)

      def maybeReconnect(reason: String, newAddress: InetSocketAddress = address) = {
        if (!scheduled.isCancelled) scheduled.cancel()
        val template = "Connection lost to {}: {}"
        reconnect(delayedRetry, newAddress, operations) match {
          case Some(rcv) =>
            log.warning(template, address, reason)
            context become rcv
          case None =>
            log.error(template, address, reason)
            context stop self
        }
      }

      def onIn(operations: Operations) = {
        scheduled.cancel()
        this.connected(address, operations, connection, pipeline, heartbeatId + 1)
      }

      val receive: Receive = {
        case x: Tcp.ConnectionClosed => x match {
          case Tcp.PeerClosed         => maybeReconnect("peer closed")
          case Tcp.ErrorClosed(error) => maybeReconnect(error.toString)
          case _                      => log.info("closing connection to {}", address)
        }

        case Terminated(`connection`) => maybeReconnect("connection actor died")

        case Terminated(`pipeline`) =>
          connection ! Tcp.Abort
          maybeReconnect("pipeline actor died")

        case Heartbeat => heartbeat(pipeline)

        case HeartbeatTimeout(id) => if (id == heartbeatId) {
          connection ! Tcp.Close
          maybeReconnect(s"no heartbeat within $heartbeatTimeout")
        }

        case ClusterDiscovererActor.Address(newAddress) =>
          if (newAddress != address) {
            log.info("Address changed from {} to {}")
            connection ! Tcp.Close
            reconnect(None, newAddress, operations).foreach { rcv =>
              connect("Connecting", Duration.Zero, newAddress)
              context become rcv
            }
          }

        case Status.Failure(e: ClusterException) =>
          log.error("Cluster failed with error: {}", e)
          context stop self
      }

      receive or
        rcvIncoming(operations, onIn, Some(outFunc)) or
        rcvOutgoing(operations, connected, Some(outFunc)) or
        rcvTimedOut(operations, connected, Some(outFunc)) or
        rcvTerminated(operations, connected, Some(outFunc))
    }

    connected(operations)
  }

  def rcvIncoming(operations: Operations, receive: Operations => Receive, outFunc: Option[PackOut => Unit]): Receive = {
    case init.Event(in) =>
      val correlationId = in.correlationId
      val msg = in.message

      def reply(out: PackOut) = outFunc.foreach(_.apply(out))

      def forward: Operations = {
        operations.single(correlationId) match {
          case Some(operation) =>
            operation.inspectIn(msg) match {
              case OnIncoming.Ignore =>
                operations

              case OnIncoming.Stop(in) =>
                toClient(operation.client, in)
                operations - operation

              case OnIncoming.Retry(operation, pack) =>
                outFunc.foreach { outFunc => outFunc(pack) }
                operations + operation

              case OnIncoming.Continue(operation, in) =>
                toClient(operation.client, in)
                operations + operation
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
            operations
        }
      }

      log.debug(in.toString)
      msg match {
        case Success(HeartbeatRequest)         => reply(PackOut(HeartbeatResponse, correlationId))
        case Success(Ping)                     => reply(PackOut(Pong, correlationId))
        case Failure(NotHandled(_: NotMaster)) => // TODO implement special case of reconnection
        case _                                 => context become receive(forward)
      }
  }

  def rcvTimedOut(operations: Operations, receive: Operations => Receive, outFunc: Option[PackOut => Unit]): Receive = {
    case TimedOut(id, version) =>
      val operation = operations.single(id)
      operation.foreach { operation =>
        if (operation.version == version) {
          val result = operation.inspectIn(Failure(OperationTimedOut)) match {
            case OnIncoming.Ignore =>
              operations

            case OnIncoming.Stop(in) =>
              toClient(operation.client, in)
              operations - operation

            case OnIncoming.Retry(operation, pack) =>
              outFunc.foreach { outFunc => outFunc(pack) }
              operations + operation

            case OnIncoming.Continue(operation, in) =>
              toClient(operation.client, in)
              operations + operation
          }
          context become receive(result)
        }
      }
  }

  def rcvOutgoing(operations: Operations, receive: Operations => Receive, outFunc: Option[PackOut => Unit]): Receive = {

    def rcvPack(pack: PackOut): Unit = {
      val msg = pack.message
      val id = pack.correlationId

      def isDefined(x: Iterable[Operation]) = x.find(_.inspectOut.isDefinedAt(msg))
      def forId = isDefined(operations.single(id))
      def forMsg = isDefined(operations.many(sender()))

      // TODO current requirement is the only one subscription per actor allowed
      val result = forId orElse forMsg match {
        case Some(operation) =>
          operation.inspectOut(msg) match {
            case OnOutgoing.Stop(out, in) =>
              outFunc.foreach { outFunc => outFunc(out) }
              toClient(operation.client, in)
              operations - operation
            case OnOutgoing.Continue(operation, out) =>
              outFunc.foreach { outFunc => outFunc(out) }
              system.scheduler.scheduleOnce(operationTimeout, self, TimedOut(id, operation.version))
              operations + operation
          }

        case None =>
          Operation.opt(pack, sender(), outFunc.isDefined, operationMaxRetries) match {
            case None => operations
            case Some(operation) =>
              context watch sender()
              outFunc.foreach(_.apply(pack))
              system.scheduler.scheduleOnce(operationTimeout, self, TimedOut(id, operation.version))
              operations + operation
          }
      }
      context become receive(result)
    }

    {
      case x: PackOut => rcvPack(x)
      case x: OutLike => rcvPack(PackOut(x.out, randomUuid, credentials(x)))
    }
  }

  def rcvTerminated(operations: Operations, receive: Operations => Receive, outFunc: Option[PackOut => Unit]): Receive = {
    case Terminated(client) =>
      val os = operations.many(client)
      if (os.nonEmpty) {
        for {
          f <- outFunc.toList
          o <- os
          p <- o.clientTerminated
        } f(p)
        context become receive(operations -- os)
      }
  }

  def rcvConnected(operations: Operations, reconnect: Reconnect): Receive = {
    case ClusterDiscovererActor.Address(address) =>
      connect("Connecting", Duration.Zero, address) // TODO TEST

    case Status.Failure(e: ClusterException) =>
      log.error("Cluster failed with error: {}", e)
      context stop self

    case Tcp.Connected(address, _) =>
      log.info("Connected to {}", address)
      val connection = sender()
      val pipeline = newPipeline(connection)
      connection ! Tcp.Register(pipeline)

      val result = operations.flatMap { operation =>
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

    case Tcp.CommandFailed(connect: Tcp.Connect) =>
      val address = connect.remoteAddress
      val template = "Connection failed to {}"
      reconnect(address, operations) match {
        case Some(receive) =>
          log.warning(template, address)
          context become receive
        case None =>
          log.error(template, address)
          context stop self
      }
  }

  def reconnect(
    retry: Option[DelayedRetry],
    address: InetSocketAddress,
    operations: Operations): Option[Receive] = {

    val result = operations.flatMap { operation =>
      operation.disconnected match {
        case OnDisconnected.Continue(operation) => Iterable(operation)
        case OnDisconnected.Stop(in) =>
          toClient(operation.client, in)
          Iterable()
      }
    }

    clusterDiscoverer match {
      case Some(clusterDiscoverer) =>
        def reconnect(address: InetSocketAddress, operations: Operations): Option[Receive] = {
          clusterDiscoverer ! GetAddress(Some(address))
          Some(connecting(operations, reconnect))
        }
        reconnect(address, result)
      case None =>
        def reconnect(retry: Option[DelayedRetry], address: InetSocketAddress, operations: Operations): Option[Receive] = {
          retry.map { retry =>
            connect("Reconnecting", retry.delay)
            connecting(operations, reconnect(retry.next, _, _))
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
    log.debug(pack.toString)
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

  case class HeartbeatTimeout(id: Long)
  case object Heartbeat
  case class TimedOut(id: Uuid, version: Int)
}