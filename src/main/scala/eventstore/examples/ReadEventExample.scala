package eventstore
package examples

import akka.actor.Status.Failure
import akka.actor._
import eventstore._
import eventstore.tcp.ConnectionActor
import java.net.InetSocketAddress

object ReadEventExample extends App {
  val system = ActorSystem()

  val settings = Settings(
    address = new InetSocketAddress("127.0.0.1", 1113),
    defaultCredentials = Some(UserCredentials("admin", "changeit")))

  val connection = system.actorOf(ConnectionActor.props(settings))
  implicit val readResult = system.actorOf(Props(classOf[ReadResult]))

  connection ! ReadEvent(EventStream("my-stream"), EventNumber.First)

  class ReadResult extends Actor with ActorLogging {
    def receive = {
      case ReadEventCompleted(event) =>
        log.info(s"event: $event")
        context.system.shutdown()

      case Failure(EventStoreException(reason, message, _)) =>
        log.error(s"reason: $reason, message: $message")
        context.system.shutdown()
    }
  }
}