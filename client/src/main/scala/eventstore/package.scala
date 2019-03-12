
import eventstore.{akka => a}
import java.{util => ju}

// TODO(AHJ): Remove this package object after 7.1.0

package object eventstore {

  private val sinceVersion = "7.0.0"
  private def updateMsg(name: String) =
    s"$name has been moved from eventstore.$name to eventstore.akka.$name. " +
    s"Please update your imports, as this deprecated type alias will be " +
    s"removed in a future version of EventStore.JVM."

  type Uuid                = ju.UUID

  @deprecated(updateMsg("EsConnection"), since = sinceVersion)
  type EsConnection        = a.EsConnection
  val EsConnection         = a.EsConnection

  @deprecated(updateMsg("EventStoreExtension"), since = sinceVersion)
  type EventStoreExtension = a.EventStoreExtension
  val EventStoreExtension  = a.EventStoreExtension

  @deprecated(updateMsg("OverflowStrategy"), since = sinceVersion)
  type OverflowStrategy    = a.OverflowStrategy
  val OverflowStrategy     = a.OverflowStrategy

  @deprecated(updateMsg("EsTransaction"), since = sinceVersion)
  type EsTransaction       = a.EsTransaction
  val EsTransaction        = a.EsTransaction

  @deprecated(updateMsg("ProjectionsClient"), since = sinceVersion)
  type ProjectionsClient   = a.ProjectionsClient
  val ProjectionsClient    = a.ProjectionsClient

}