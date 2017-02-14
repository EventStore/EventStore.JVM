package eventstore

import akka.actor._
import eventstore.tcp.ConnectionActor

class EventStoreExtension(system: ActorSystem) extends Extension {

  def settings: Settings = Settings(system.settings.config)

  val actor: ActorRef = system.actorOf(ConnectionActor.props(settings), "eventstore-connection")

  val connection: EsConnection = new EsConnection(actor, system, settings)

  lazy val projectionsClient: ProjectionsClient = new ProjectionsClient(settings, system)

  /**
   * Java API
   */
  val connectionJava: j.EsConnection = j.EsConnectionImpl(system, settings)
}

object EventStoreExtension extends ExtensionId[EventStoreExtension] with ExtensionIdProvider {
  def lookup() = EventStoreExtension

  def createExtension(system: ExtendedActorSystem): EventStoreExtension = new EventStoreExtension(system)

  /**
   * Java API
   */
  override def get(system: ActorSystem): EventStoreExtension = super.get(system)
}
