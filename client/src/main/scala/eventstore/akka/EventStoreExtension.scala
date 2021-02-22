package eventstore
package akka

import _root_.akka.actor._
import eventstore.akka.tcp.ConnectionActor

class EventStoreExtension(system: ActorSystem) extends Extension {

  def settings: Settings = Settings(system.settings.config)

  lazy val actor: ActorRef = system.actorOf(ConnectionActor.props(settings), "eventstore-connection")
  lazy val connection: EsConnection = new EsConnection(actor, system, settings)(system)
  lazy val projectionsClient: ProjectionsClient = new ProjectionsClient(settings, system)

  /**
   * Java API
   */
  lazy val connectionJava: j.EsConnection = eventstore.j.EsConnectionImpl(system, settings)
}

object EventStoreExtension extends ExtensionId[EventStoreExtension] with ExtensionIdProvider {
  def lookup: ExtensionId[EventStoreExtension] = EventStoreExtension

  def createExtension(system: ExtendedActorSystem): EventStoreExtension = new EventStoreExtension(system)

  /**
   * Java API
   */
  override def get(system: ActorSystem): EventStoreExtension = super.get(system)
}
