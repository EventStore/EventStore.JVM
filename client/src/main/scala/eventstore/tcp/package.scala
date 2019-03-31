package eventstore

import eventstore.{akka => a}
import eventstore.core.tcp.TypeAliases

// TODO(AHJ): Remove this package object after 7.1.0

package object tcp extends TypeAliases {

  @deprecated(deprecationMsg("ConnectionActor", "eventstore.tcp", "eventstore.akka.tcp"), since = sinceVersion)
  val ConnectionActor = a.tcp.ConnectionActor

}
