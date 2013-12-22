package eventstore
package tcp

import akka.actor.{ IO => _, _ }
import akka.io._
import java.nio.ByteOrder
import util.{ CancellableAdapter, BidirectionalMap }
import scala.collection.immutable.Queue
import scala.concurrent.duration._
import scala.util.{ Failure, Success }

object ConnectionActor {
  def props(settings: Settings = Settings.Default): Props = Props(classOf[ConnectionActor], settings)
}

class ConnectionActor(settings: Settings) extends Actor with ActorLogging {

  import context.system
  import context.dispatcher
  import settings._

  val init = TcpPipelineHandler.withLogger(log,
    new MessageByteStringAdapter >>
      new LengthFieldFrame(
        maxSize = 64 * 1024 * 1024,
        byteOrder = ByteOrder.LITTLE_ENDIAN,
        lengthIncludesHeader = false) >>
      new TcpReadWriteAdapter >>
      new BackpressureBuffer(
        lowBytes = backpressureSettings.lowWatermark,
        highBytes = backpressureSettings.highWatermark,
        maxBytes = backpressureSettings.maxCapacity))

  override def preStart() {
    log.debug(s"connecting to $address")
    tcp ! connect
  }

  var binding = new BidirectionalMap[Uuid, ActorRef]()

  def receiveTerminated: Receive = {
    case Terminated(actor) => binding = binding - actor // TODO unsubscribe if subscription sender is dead
  }

  def receive = connecting()

  def newPipeline(connection: ActorRef): ActorRef =
    context.actorOf(TcpPipelineHandler.props(init, connection, self))

  def connecting(
    stash: Queue[TcpPackageOut] = Queue(),
    reconnectionsLeft: Int = maxReconnections): Receive = {
    val receive: Receive = {
      case Tcp.Connected(remote, _) =>
        log.info(s"connected to $remote")

        val connection = sender
        val pipeline = newPipeline(connection)
        connection ! Tcp.Register(pipeline)
        context watch connection
        context watch pipeline

        def send(pack: TcpPackageOut) {
          log.debug(pack.toString)
          pipeline ! init.command(pack)
        }

        stash.foreach(send)

        context become connected(connection, pipeline, send)

      case Tcp.CommandFailed(_: Tcp.Connect) =>
        log.error(s"connection failed to $address")
        if (reconnectionsLeft == 0) context stop self
        else {
          reconnect()
          context become connecting(stash, reconnectionsLeft - 1)
        }

      case x: OutLike =>
        log.debug(s"received $x while not connected, adding to stash")
        val pack = tcpPack(x)
        context become connecting(stash enqueue pack)
    }

    receive orElse receiveTerminated
  }

  def connected(connection: ActorRef, pipeline: ActorRef, send: TcpPackageOut => Unit, packNumber: Int = -1): Receive = {

    val scheduled = CancellableAdapter(
      system.scheduler.scheduleOnce(heartbeatTimeout, self, HeartbeatTimeout(packNumber)),
      system.scheduler.scheduleOnce(heartbeatInterval, self, HeartbeatInterval))

    def maybeReconnect(reason: String) {
      if (!scheduled.isCancelled) scheduled.cancel()
      val msg = s"connection lost to $address: $reason"
      if (settings.maxReconnections == 0) {
        log.error(msg)
        context stop self
      } else {
        log.warning(msg)
        reconnect()
        context become connecting()
      }
    }

    val receive: Receive = {
      case init.Event(pack @ TcpPackageIn(correlationId, msg)) =>
        log.debug(pack.toString)
        scheduled.cancel()
        msg match {
          case Success(HeartbeatRequest) => send(TcpPackageOut(correlationId, HeartbeatResponse))
          case Success(Ping)             => send(TcpPackageOut(correlationId, Pong))
          case _                         => dispatch(pack)
        }
        context become connected(connection, pipeline, send, packNumber + 1)

      case x: OutLike               => send(tcpPack(x))

      case HeartbeatInterval        => send(TcpPackageOut(HeartbeatRequest))

      case Terminated(`connection`) => maybeReconnect("connection actor died")

      case Terminated(`pipeline`) =>
        connection ! Tcp.Abort
        maybeReconnect("pipeline actor died")

      case HeartbeatTimeout(`packNumber`) =>
        connection ! Tcp.Close
        maybeReconnect(s"no heartbeat within $heartbeatTimeout")

      case closed: Tcp.ConnectionClosed => closed match {
        case Tcp.PeerClosed         => maybeReconnect("peer closed")
        case Tcp.ErrorClosed(error) => maybeReconnect(error.toString)
        case _                      => log.info(s"closing connection to $address")
      }
    }

    receive orElse receiveTerminated
  }

  def reconnect() {
    if (reconnectionDelay == Duration.Zero) {
      log.info(s"reconnecting to $address")
      tcp ! connect
    } else {
      log.info(s"reconnecting to $address in $reconnectionDelay")
      system.scheduler.scheduleOnce(reconnectionDelay, tcp, connect)
    }
  }

  def tcpPack(message: OutLike): TcpPackageOut = {
    val correlationId = binding.y(sender).getOrElse {
      val x = newUuid
      log.debug(s"add sender $sender for $x")
      context watch sender
      binding = binding.+(x, sender)
      x
    }

    val (out, credentials) = message match {
      case WithCredentials(x, c) => x -> Some(c)
      case x: Out                => x -> settings.defaultCredentials
    }

    TcpPackageOut(correlationId, out, credentials)
  }

  def dispatch(pack: TcpPackageIn) {
    val msg = pack.message match {
      case Success(x) => x
      case Failure(x) => Status.Failure(x)
    }
    val correlationId = pack.correlationId
    binding.x(correlationId) match {
      case Some(channel) => channel ! msg
      case None => msg match {
        case Pong | HeartbeatResponse =>
        case _ =>
          log.warning(s"sender not found by correlationId: $correlationId for $msg")
          system.deadLetters ! msg
      }
    }
  }

  def connect = Tcp.Connect(address, timeout = Some(connectionTimeout))

  def tcp = IO(Tcp)

  case class HeartbeatTimeout(packNumber: Int)
  case object HeartbeatInterval
}