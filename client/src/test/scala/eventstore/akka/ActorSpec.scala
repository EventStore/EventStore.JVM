package eventstore
package akka

import scala.concurrent.duration._
import scala.concurrent.{Await, Awaitable, ExecutionContext}
import com.typesafe.config.{Config, ConfigFactory}
import _root_.akka.actor.ActorSystem
import _root_.akka.testkit.{ImplicitSender, TestKit}
import org.specs2.mutable.Specification
import org.specs2.specification.{AfterAll, Scope}

abstract class ActorSpec extends Specification with AfterAll {
  implicit lazy val system = ActorSystem("test", config)
  implicit def ex: ExecutionContext = system.dispatcher

  def config: Config = ConfigFactory.load

  def afterAll() = {
    TestKit.shutdownActorSystem(system)
  }

  protected abstract class ActorScope extends TestKit(system) with ImplicitSender with Scope

  def await_[T](awaitable: Awaitable[T], atMost: Duration = 6.seconds): T = awaitable.await_(atMost)

  implicit class RichAwaitable[T](val awaitable: Awaitable[T]) {
    def await_(implicit atMost: Duration = 6.seconds) = Await.result(awaitable, atMost)
  }
}
