package eventstore.util

import com.typesafe.config.{ Config, ConfigFactory }
import org.specs2.mutable.Specification
import org.specs2.specification.{ Scope, Step, Fragments }
import akka.actor.ActorSystem
import akka.testkit.{ ImplicitSender, TestKit }
import scala.concurrent.{ Awaitable, Await }
import scala.concurrent.duration._

abstract class ActorSpec extends Specification with NoConversions {
  implicit lazy val system = ActorSystem("test", config)

  def config: Config = ConfigFactory.load

  override def map(fs: => Fragments) = super.map(fs) ^ Step(TestKit.shutdownActorSystem(system))

  protected abstract class ActorScope extends TestKit(system) with ImplicitSender with Scope

  def await_[T](awaitable: Awaitable[T], atMost: Duration = 3.seconds): T = awaitable.await_(atMost)

  implicit class RichAwaitable[T](val awaitable: Awaitable[T]) {
    def await_(implicit atMost: Duration = 3.seconds) = Await.result(awaitable, atMost)
  }
}
