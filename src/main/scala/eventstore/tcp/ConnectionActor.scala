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
  case object WaitReconnected

  /**
   * Java API
   */
  def getProps(): Props = props()

  /**
   * Java API
   */
  def getProps(settings: Settings): Props = props(settings)

  /**
   * Java API
   */
  def reconnected: Reconnected.type = Reconnected

  /**
   * Java API
   */
  def waitReconnected: WaitReconnected.type = WaitReconnected
}

private[eventstore] class ConnectionActor(settings: Settings) extends Actor with ActorLogging {

  import ConnectionActor.{ Reconnected, WaitReconnected }
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
    def onPipeline(pipeline: ActorRef) = operations.values.foreach(o => sendPack(pipeline, o.pack))

    val receive: Receive = {
      case init.Event(x) =>
        val os = dispatch(operations, x, _ => Unit) // TODO rename result
        context become connecting(os)
    }

    receive orElse rcvOut(operations, connecting, None) orElse rcvConnected(operations, onPipeline) orElse rcvConnectFailed(None)
  }

  def connected(operations: Operations, connection: ActorRef, pipeline: ActorRef, heartbeatId: Long): Receive = {
    val scheduled = CancellableAdapter(
      system.scheduler.scheduleOnce(heartbeatInterval, self, Heartbeat),
      system.scheduler.scheduleOnce(heartbeatInterval + heartbeatTimeout, self, HeartbeatTimeout(heartbeatId)))

    def maybeReconnect(reason: String) = {
      val failure = connectionLostFailure
      operations.manySet.foreach(_ ! failure) // TODO merge
      //val result = operations.flatMap(_.connectionLost) // TODO

      if (!scheduled.isCancelled) scheduled.cancel()
      val template = "connection lost to {}: {}"

      DelayedRetry.opt(maxReconnections, reconnectionDelayMin, reconnectionDelayMax) match {
        case None =>
          log.error(template, address, reason)
          context stop self

        case Some(retry) =>
          log.warning(template, address, reason)
          connect("reconnecting", retry.delay)
          //          context become reconnecting(result, retry, Set.empty)
          context become reconnecting(Operations.Empty, retry, Set.empty)
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

      case WaitReconnected => sender() ! Reconnected

      case init.Event(x) =>
        scheduled.cancel()
        val result = dispatch(operations, x, sendPack(pipeline, _)) // TODO rename result
        context become connected(result, connection, pipeline, heartbeatId + 1)

      case Heartbeat => sendPack(pipeline, TcpPackageOut(HeartbeatRequest))

      case HeartbeatTimeout(id) => if (id == heartbeatId) {
        connection ! Tcp.Close
        maybeReconnect(s"no heartbeat within $heartbeatTimeout")
      }
    }

    receive orElse rcvOut(operations, connected(_, connection, pipeline, heartbeatId), Some(pipeline))
  }

  def reconnecting(operations: Operations, retry: DelayedRetry, clients: Set[ActorRef]): Receive = {
    def reconnect = retry.next.map { retry =>
      connect("reconnecting", retry.delay)
      reconnecting(operations, retry, clients)
    }

    def onPipeline(pipeline: ActorRef) = {
      operations.values.foreach { o => sendPack(pipeline, o.pack) }
      clients.foreach(_ ! Reconnected)
    }

    val receive: Receive = {
      case WaitReconnected => context become reconnecting(operations, retry, clients + sender())
      case init.Event(x) =>
        val result = dispatch(Operations.Empty /*TODO*/ , x, _ => Unit) // TODO rename result
        context become reconnecting(result, retry, clients)
      case _: OutLike | _: TcpPackageOut => sender() ! connectionLostFailure // TODO enqueue
    }

    receive orElse
      rcvConnected(operations, onPipeline) orElse
      rcvConnectFailed(reconnect) orElse
      rcvOut(operations, reconnecting(_, retry, clients), None)
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

  def rcvConnected(operations: Operations, onPipeline: ActorRef => Any): Receive = {
    case Tcp.Connected(`address`, _) =>
      log.info("connected to {}", address)
      val connection = sender()
      val pipeline = newPipeline(connection)
      connection ! Tcp.Register(pipeline)
      onPipeline(pipeline)
      context watch connection
      context watch pipeline
      context become connected(operations, connection, pipeline, 0)
  }

  def sendPack(pipeline: ActorRef, pack: TcpPackageOut): Unit = {
    log.debug(pack.toString)
    pipeline ! init.command(pack)
  }

  def rcvOut(operations: Operations, receive: Operations => Receive, pipeline: Option[ActorRef]): Receive = {
    def send(pack: TcpPackageOut): Unit = {
      pipeline.foreach(sendPack(_, pack))
    }

    def newOperation(pack: TcpPackageOut) = {
      context watch sender() // TODO don't do that every time
      send(pack)
      Operation(pack, sender())
    }

    {
      case pack: TcpPackageOut =>
        log.warning(pack.toString)
        val msg = pack.message
        // TODO create operations.update
        // operations.get(pack.correlationId) is not checked for isDefined ... hmm....
        val operation = operations.single(pack.correlationId) orElse operations.many(sender()).find(_.inspectOut.isDefinedAt(msg)) match {
          case None => newOperation(pack)
          case Some(o) =>
            val (operation, pack) = o.inspectOut(msg)
            pack.foreach(send) // TODO
            operation
        }
        context become receive(operations + operation)

      case message: OutLike =>
        // TODO current requirement is the only one subscription per actor allowed
        val msg = message.out
        val operation = operations.many(sender()).find(_.inspectOut.isDefinedAt(msg)) match {
          case Some(o) =>
            val (operation, pack) = o.inspectOut(msg)
            pack.foreach(send) // TODO
            operation
          case None =>
            val pack = TcpPackageOut(message.out, randomUuid, credentials(message))
            val operation = newOperation(pack)
            operation
        }
        context become receive(operations + operation)

      case Terminated(actor) =>
        val os = operations.many(actor)
        if (os.nonEmpty) {
          for {
            o <- os
            p <- o.clientTerminated
          } send(p)
          context become receive(operations -- os)
        }
    }
  }

  def dispatch(operations: Operations, in: TcpPackageIn, reply: TcpPackageOut => Unit): Operations = {
    log.debug(in.toString)
    val correlationId = in.correlationId

    def forward(msg: Try[In]): Operations = {
      operations.single(correlationId) match {
        case Some(operation) =>
          operation.inspectIn(msg) match {
            case None => operations - operation
            case Some((operation, pack)) =>
              pack.foreach(reply)
              operations + operation
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

      case x =>
        forward(x)
    }
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