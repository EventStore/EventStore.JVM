package eventstore
package tcp

import akka.actor._
import akka.io.{ Tcp, IO }
import scala.concurrent.duration._
import scala.util.{ Try, Failure, Success }
import pipeline._
import operations.{ Operation, Operations }
import util.{ CancellableAdapter, DelayedRetry }

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

  val init = EsPipelineInit(log, backpressure)

  def receive = {
    connect("connecting", Duration.Zero)
    connecting(Operations.Empty)
  }

  def connecting(operations: Operations): Receive = {
    def onPipeline(pipeline: ActorRef) = {
      operations.flatMap(_.connected(sendCommand(pipeline, _)))
    }

    rcvIncoming(operations, connecting, None) or
      rcvOutgoing(operations, connecting, None) or
      rcvConnected(onPipeline) or
      rcvConnectFailed(None) or
      rcvTimedOut(operations, connecting) or
      rcvTerminated(operations, connecting)
  }

  def connected(operations: Operations, connection: ActorRef, pipeline: ActorRef, heartbeatId: Long): Receive = {
    val scheduled = CancellableAdapter(
      system.scheduler.scheduleOnce(heartbeatInterval, self, Heartbeat),
      system.scheduler.scheduleOnce(heartbeatInterval + heartbeatTimeout, self, HeartbeatTimeout(heartbeatId)))

    def connected(operations: Operations): Receive = {

      def outFunc(pack: PackOut): Unit = sendCommand(pipeline, pack)

      def maybeReconnect(reason: String) = {
        val result = operations.flatMap(_.connectionLost())

        if (!scheduled.isCancelled) scheduled.cancel()
        val template = "connection lost to {}: {}"

        DelayedRetry.opt(maxReconnections, reconnectionDelayMin, reconnectionDelayMax) match {
          case None =>
            log.error(template, address, reason)
            context stop self

          case Some(retry) =>
            log.warning(template, address, reason)
            connect("reconnecting", retry.delay)
            context become reconnecting(result, retry)
        }
      }

      def onIn(operations: Operations) = {
        scheduled.cancel()
        this.connected(operations, connection, pipeline, heartbeatId + 1)
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

        case Heartbeat => outFunc(PackOut(HeartbeatRequest))

        case HeartbeatTimeout(id) => if (id == heartbeatId) {
          connection ! Tcp.Close
          maybeReconnect(s"no heartbeat within $heartbeatTimeout")
        }
      }

      receive or
        rcvIncoming(operations, onIn, Some(outFunc)) or
        rcvOutgoing(operations, connected, Some(outFunc)) or
        rcvTimedOut(operations, connected) or
        rcvTerminated(operations, connected)
    }

    connected(operations)
  }

  def reconnecting(operations: Operations, retry: DelayedRetry): Receive = {
    def reconnecting(operations: Operations): Receive = {
      def reconnect = retry.next.map { retry =>
        connect("reconnecting", retry.delay)
        this.reconnecting(operations, retry)
      }

      def onPipeline(pipeline: ActorRef) = {
        operations.flatMap(_.connected(sendCommand(pipeline, _)))
      }

      rcvIncoming(operations, reconnecting, None) or
        rcvOutgoing(operations, reconnecting, None) or
        rcvConnected(onPipeline) or
        rcvConnectFailed(reconnect) or
        rcvTimedOut(operations, reconnecting) or
        rcvTerminated(operations, reconnecting)
    }

    reconnecting(operations)
  }

  def rcvIncoming(operations: Operations, receive: Operations => Receive, outFunc: Option[PackOut => Unit]): Receive = {
    case init.Event(in) =>
      val correlationId = in.correlationId
      val msg = in.message

      def reply(out: PackOut) = {
        outFunc.foreach(_.apply(out))
      }

      def forward: Operations = {
        operations.single(correlationId) match {
          case Some(operation) =>
            operation.inspectIn(msg) match {
              case Some(operation) => operations + operation
              case None            => operations - operation
            }

          case None =>
            msg match {
              case Failure(x) => log.warning("cannot deliver {}, client not found for correlationId: {}", msg, correlationId)
              case Success(msg) => msg match {
                case Pong | HeartbeatResponse | Unsubscribed =>
                case _: SubscribeCompleted | _: StreamEventAppeared =>
                  log.warning("cannot deliver {}, client not found for correlationId: {}, unsubscribing", msg, correlationId)
                  reply(PackOut(Unsubscribe, correlationId, defaultCredentials))

                case _ => log.warning("cannot deliver {}, client not found for correlationId: {}", msg, correlationId)
              }
            }
            operations
        }
      }

      log.debug(in.toString)
      msg match {
        case Success(HeartbeatRequest) => reply(PackOut(HeartbeatResponse, correlationId))
        case Success(Ping)             => reply(PackOut(Pong, correlationId))
        case _                         => context become receive(forward)
      }
  }

  def rcvTimedOut(operations: Operations, receive: Operations => Receive): Receive = {
    case TimedOut(id, version) =>
      val operation = operations.single(id)
      operation.foreach { operation =>
        if (operation.version == version) {
          val result = operation.inspectIn(Failure(OperationTimedOut)) match {
            case Some(operation) => operations + operation
            case None            => operations - operation
          }
          context become receive(result)
        }
      }
  }

  def rcvOutgoing(operations: Operations, receive: Operations => Receive, outFunc: Option[PackOut => Unit]): Receive = {
    def inFunc(client: ActorRef)(in: Try[In]): Unit = {
      val msg = in match {
        case Success(x) => x
        case Failure(x) => Status.Failure(x)
      }
      client ! msg
    }

    def rcvPack(pack: PackOut): Unit = {
      val msg = pack.message
      val id = pack.correlationId

      def forId = operations.single(id).find(_.inspectOut.isDefinedAt(msg))
      def forMsg = operations.many(sender()).find(_.inspectOut.isDefinedAt(msg))

      // TODO current requirement is the only one subscription per actor allowed
      val result = forId orElse forMsg match {
        case Some(operation) =>
          operation.inspectOut(msg) match {
            case None => operations - operation
            case Some(operation) =>
              system.scheduler.scheduleOnce(operationTimeout, self, TimedOut(id, operation.version))
              operations + operation
          }

        case None =>
          Operation.opt(pack, sender(), inFunc(sender()), outFunc) match {
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

  def rcvTerminated(operations: Operations, receive: Operations => Receive): Receive = {
    case Terminated(client) =>
      val os = operations.many(client)
      if (os.nonEmpty) {
        os.foreach(_.clientTerminated())
        context become receive(operations -- os)
      }
  }

  def rcvConnectFailed(recover: => Option[Receive]): Receive = {
    case Tcp.CommandFailed(_: Tcp.Connect) =>
      val template = "connection failed to {}"
      recover match {
        case Some(x) =>
          log.warning(template, address)
          context become x
        case None =>
          log.error(template, address)
          context stop self
      }
  }

  def rcvConnected(onPipeline: ActorRef => Operations): Receive = {
    case Tcp.Connected(`address`, _) =>
      log.info("connected to {}", address)
      val connection = sender()
      val pipeline = newPipeline(connection)
      val operations = onPipeline(pipeline)
      connection ! Tcp.Register(pipeline)
      context watch connection
      context watch pipeline
      context become connected(operations, connection, pipeline, 0)
  }

  def sendCommand(pipeline: ActorRef, pack: PackOut): Unit = {
    log.debug(pack.toString)
    pipeline ! init.Command(pack)
  }

  def newPipeline(connection: ActorRef): ActorRef = {
    context actorOf TcpPipelineHandler.props(init, connection, self)
  }

  def credentials(x: OutLike): Option[UserCredentials] = x match {
    case WithCredentials(_, c) => Some(c)
    case _: Out                => defaultCredentials
  }

  def connect(label: String, in: FiniteDuration) {
    val connect = Tcp.Connect(address, timeout = Some(connectionTimeout))
    if (in == Duration.Zero) {
      log.info("{} to {}", label, address)
      tcp ! connect
    } else {
      log.info("{} to {} in {}", label, address, in)
      system.scheduler.scheduleOnce(in, tcp, connect)
    }
  }

  def tcp = IO(Tcp)

  case class HeartbeatTimeout(id: Long)
  case object Heartbeat
  case class TimedOut(id: Uuid, version: Int)
}