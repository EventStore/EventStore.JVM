package eventstore

import akka.actor._
import eventstore.tcp.ConnectionActor

class EventStoreExtension(system: ActorSystem) extends Extension {

  def settings: Settings = Settings.Default

  val actor: ActorRef = system.actorOf(ConnectionActor.props(settings))

  val connection: EsConnection = new EsConnection(actor, system, settings.operationTimeout)
}

object EventStoreExtension extends ExtensionId[EventStoreExtension] with ExtensionIdProvider {
  def lookup() = EventStoreExtension

  def createExtension(system: ExtendedActorSystem) = new EventStoreExtension(system)
}
