package eventstore
package akka

import _root_.akka.stream.ActorMaterializer

abstract class AbstractStreamsITest extends ActorSpec {

  implicit val materializer = ActorMaterializer()

  override def afterAll() = {
    materializer.shutdown()
    super.afterAll()
  }

  trait TestScope extends ActorScope {
    def settings = Settings.Default
  }
}
