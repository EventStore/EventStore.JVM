package eventstore

import akka.actor.{Actor, Props, ActorSystem}
import java.net.InetSocketAddress
import scala.reflect.ClassTag
import tcp.ConnectionActor


/**
 * @author Yaroslav Klymko
 */
object Main extends App {
  implicit val system = ActorSystem()

  val address = new InetSocketAddress("127.0.0.1", 1113)
//  val address = new InetSocketAddress("192.168.1.3", 1113)


  def clientActor[T <: Actor](implicit tag: ClassTag[T]) {
    system.actorOf(Props[T])
    system.actorOf(Props(classOf[ConnectionActor], address))
  }


  clientActor[SubscribeActor]
//  clientActor[ReadAllEventsActor]
//  clientActor[ReadStreamEventsActor]
//  clientActor[WriteEventsActor]
//  clientActor[TransactionCommitActor]
}
