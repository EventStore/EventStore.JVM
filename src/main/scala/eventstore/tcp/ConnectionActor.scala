package eventstore
package tcp

import akka.actor._
import pipeline._
import akka.io.{ Tcp, IO }
import scala.concurrent.duration._
import scala.util.{ Try, Failure, Success }
import eventstore.util.{ OneToMany, CancellableAdapter, DelayedRetry }

object ConnectionActor {
  def props(settings: Settings = Settings.Default): Props = Props(classOf[ConnectionActor], settings)

  case object Reconnected

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

  // TODO
  type Operations = OneToMany[Operation, Uuid, ActorRef]
  // TODO
  object Operations {
    val Empty: Operations = OneToMany[Operation, Uuid, ActorRef](_.id, _.client)
  }

  def receive = {
    connect("connecting", Duration.Zero)
    connecting(Operations.Empty)
  }

  def connecting(operations: Operations): Receive = {
    def onPipeline(pipeline: ActorRef) = operations.flatMap(_.connected(sendCommand(pipeline, _)))

    // TODO refactor this
    val receive: Receive = {
      case init.Event(x) =>
        val os = dispatch(operations, x, None) // TODO rename result
        context become connecting(os)
    }

    receive orElse rcvOut(operations, connecting, None) orElse rcvConnected(onPipeline) orElse rcvConnectFailed(None)
  }

  def connected(operations: Operations, connection: ActorRef, pipeline: ActorRef, heartbeatId: Long): Receive = {
    val scheduled = CancellableAdapter(
      system.scheduler.scheduleOnce(heartbeatInterval, self, Heartbeat),
      system.scheduler.scheduleOnce(heartbeatInterval + heartbeatTimeout, self, HeartbeatTimeout(heartbeatId)))

    def outFunc(pack: TcpPackageOut): Unit = sendCommand(pipeline, pack)

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

      case init.Event(x) =>
        scheduled.cancel()
        val result = dispatch(operations, x, Some(pipeline)) // TODO rename result
        context become connected(result, connection, pipeline, heartbeatId + 1)

      case Heartbeat => outFunc(TcpPackageOut(HeartbeatRequest))

      case HeartbeatTimeout(id) => if (id == heartbeatId) {
        connection ! Tcp.Close
        maybeReconnect(s"no heartbeat within $heartbeatTimeout")
      }
    }

    receive orElse rcvOut(operations, connected(_, connection, pipeline, heartbeatId), Some(outFunc))
  }

  def reconnecting(operations: Operations, retry: DelayedRetry): Receive = {
    def reconnect = retry.next.map { retry =>
      connect("reconnecting", retry.delay)
      reconnecting(operations, retry)
    }

    def onPipeline(pipeline: ActorRef) = operations.flatMap(_.connected(sendCommand(pipeline, _)))

    val receive: Receive = {
      case init.Event(x) =>
        val result = dispatch(operations, x, None) // TODO rename result
        context become reconnecting(result, retry)
    }

    receive orElse
      rcvConnected(onPipeline) orElse
      rcvConnectFailed(reconnect) orElse
      rcvOut(operations, reconnecting(_, retry), None)
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

  def rcvOut(operations: Operations, receive: Operations => Receive, outFunc: Option[TcpPackageOut => Unit]): Receive = {
    def inFunc(client: ActorRef)(in: Try[In]): Unit = {
      val msg = in match {
        case Success(x) => x
        case Failure(x) => Status.Failure(x)
      }
      client ! msg
    }

    def rcvPack(pack: TcpPackageOut): Unit = {
      val msg = pack.message

      def forId = operations.single(pack.correlationId)
      def forMsg = operations.many(sender()).find(_.inspectOut.isDefinedAt(msg))

      // TODO current requirement is the only one subscription per actor allowed
      // TODO operations.single(pack.correlationId) is not checked for isDefined ... hmm....
      val result = forId orElse forMsg match {
        case Some(operation) =>
          println(operation.getClass.getSimpleName)
          operation.inspectOut(msg) match {
            case Some(operation) => operations + operation
            case None            => operations - operation
          }

        case None =>
          context watch sender() // TODO don't do that every time
          outFunc.foreach(_.apply(pack)) // TODO here or in the operation
          val operation = Operation(pack, sender(), inFunc(sender()), outFunc)
          operations + operation
      }
      context become receive(result)
    }

    {
      case x: TcpPackageOut => rcvPack(x)
      case x: OutLike       => rcvPack(TcpPackageOut(x.out, randomUuid, credentials(x)))
      case Terminated(actor) =>
        val os = operations.many(actor)
        if (os.nonEmpty) {
          for {
            o <- os
            p <- o.clientTerminated
            f <- outFunc
          } f(p)
          context become receive(operations -- os)
        }
    }
  }

  def dispatch(operations: Operations, in: TcpPackageIn, pipeline: Option[ActorRef]): Operations = {
    def reply(out: TcpPackageOut): Unit = {
      pipeline.foreach(sendCommand(_, out))
    }

    log.debug(in.toString)
    val correlationId = in.correlationId

    def forward(msg: Try[In]): Operations = {
      operations.single(correlationId) match {
        case Some(operation) =>
          println(operation.getClass.getSimpleName)
          operation.inspectIn(msg) match {
            case None            => operations - operation
            case Some(operation) => operations + operation
          }

        case None =>
          msg match {
            case Failure(x) => log.warning("cannot deliver {}, sender not found for correlationId: {}", msg, correlationId)
            case Success(msg) => msg match {
              case Pong | HeartbeatResponse | UnsubscribeCompleted =>
              case _: SubscribeCompleted =>
                log.warning("cannot deliver {}, sender not found for correlationId: {}, unsubscribing", msg, correlationId)
                reply(TcpPackageOut(Unsubscribe, correlationId, defaultCredentials))

              case _ => log.warning("cannot deliver {}, sender not found for correlationId: {}", msg, correlationId)
            }
          }

          operations
      }
    }

    in.message match {
      // TODO reconnect on EsException(NotHandled(NotReady))
      case Success(HeartbeatRequest) =>
        reply(TcpPackageOut(HeartbeatResponse, in.correlationId))
        operations

      case Success(Ping) =>
        reply(TcpPackageOut(Pong, in.correlationId))
        operations

      case x => forward(x)
    }
  }

  def sendCommand(pipeline: ActorRef, pack: TcpPackageOut): Unit = {
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

  def connectionLostFailure = Status.Failure(EsException(EsError.ConnectionLost))

  case class HeartbeatTimeout(id: Long)
  case object Heartbeat
}