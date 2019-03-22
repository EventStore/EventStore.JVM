
import eventstore.{akka => a}
import java.{util => ju}

// TODO(AHJ): Remove this package object after 7.1.0

package object eventstore {

  private[eventstore] val sinceVersion = "7.0.0"

  type Uuid                = ju.UUID

  @deprecated(a.deprecationMsg("EsConnection"), since = sinceVersion)
  type EsConnection        = a.EsConnection
  val EsConnection         = a.EsConnection

  @deprecated(a.deprecationMsg("EventStoreExtension"), since = sinceVersion)
  type EventStoreExtension = a.EventStoreExtension
  val EventStoreExtension  = a.EventStoreExtension

  @deprecated(a.deprecationMsg("OverflowStrategy"), since = sinceVersion)
  type OverflowStrategy    = a.OverflowStrategy
  val OverflowStrategy     = a.OverflowStrategy

  @deprecated(a.deprecationMsg("EsTransaction"), since = sinceVersion)
  type EsTransaction       = a.EsTransaction
  val EsTransaction        = a.EsTransaction

  @deprecated(a.deprecationMsg("ProjectionsClient"), since = sinceVersion)
  type ProjectionsClient   = a.ProjectionsClient
  val ProjectionsClient    = a.ProjectionsClient

  @deprecated(a.deprecationMsg("PersistentSubscriptionActor"), since = sinceVersion)
  val PersistentSubscriptionActor  = a.PersistentSubscriptionActor

  @deprecated(a.deprecationMsg("LiveProcessingStarted"), since = sinceVersion)
  type LiveProcessingStarted = a.LiveProcessingStarted.type
  val LiveProcessingStarted  = a.LiveProcessingStarted

}