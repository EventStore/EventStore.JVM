package eventstore

import akka.stream.ActorMaterializer

abstract class AbstractStreamsITest extends util.ActorSpec {

  implicit val materializer = ActorMaterializer()

  override def afterAll() = {
    materializer.shutdown()
    super.afterAll()
  }

  trait TestScope extends ActorScope {
    def settings = Settings.Default
  }
}
