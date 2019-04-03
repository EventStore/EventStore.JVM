package eventstore

import eventstore.{akka => a}
import eventstore.core.tcp.TypeAliases

// TODO(AHJ): Remove this package object after 7.1.0

package object tcp extends TypeAliases {

  private final val tcpMsg =
    "This type has been moved from eventstore.tcp to eventstore.akka.tcp. " +
    "Please update your imports, as this deprecated type alias will " +
    "be removed in a future version of EventStore.JVM."

  @deprecated(tcpMsg, sinceV7)
  val ConnectionActor = a.tcp.ConnectionActor

}
