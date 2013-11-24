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

  val connection = system.actorOf(Props(classOf[ConnectionActor], settings))
  system.actorOf(Props(classOf[ReadEventActor], connection))
}

class ReadEventActor(connection: ActorRef) extends Actor with ActorLogging {

  connection ! ReadEvent(
    streamId = EventStream.Id("my-stream"),
    eventNumber = EventNumber.First)

  def receive = {
    case ReadEventCompleted(event)                        => log.info(s"SUCCEED: $event")
    case Failure(EventStoreException(reason, message, _)) => log.error(s"FAILED: reason $reason, message: $message")
  }
}