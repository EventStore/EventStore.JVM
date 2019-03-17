package eventstore
package akka
package examples

import java.net.InetSocketAddress
import _root_.akka.actor._
import _root_.akka.actor.Status.Failure
import eventstore.akka.tcp.ConnectionActor

object ReadEventExample extends App {
  val system = ActorSystem()

  val settings = Settings(
    address = new InetSocketAddress("127.0.0.1", 1113),
    defaultCredentials = Some(UserCredentials("admin", "changeit"))
  )

  val connection = system.actorOf(ConnectionActor.props(settings))
  implicit val readResult = system.actorOf(Props[ReadResult])

  connection ! ReadEvent(EventStream.Id("my-stream"), EventNumber.First)

  class ReadResult extends Actor with ActorLogging {
    def receive = {
      case ReadEventCompleted(event) =>
        log.info("event: {}", event)
        context.system.terminate()

      case Failure(e: EsException) =>
        log.error(e.toString)
        context.system.terminate()
    }
  }
}