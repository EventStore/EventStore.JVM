package eventstore

import akka.stream.ActorMaterializer
import org.specs2.specification.{ Fragments, Step }

abstract class AbstractStreamsITest extends util.ActorSpec {

  implicit val materializer = ActorMaterializer()

  override def map(fs: => Fragments) = super.map(fs) ^ Step(materializer.shutdown())

  trait TestScope extends ActorScope {
    def settings = Settings.Default
  }
}
