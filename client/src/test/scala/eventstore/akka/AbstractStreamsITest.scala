package eventstore
package akka

abstract class AbstractStreamsITest extends ActorSpec {

  trait TestScope extends ActorScope {
    def settings = Settings.Default
  }
}
