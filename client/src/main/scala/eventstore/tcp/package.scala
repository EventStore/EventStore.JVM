package eventstore

import eventstore.{akka => a}

// TODO(AHJ): Remove this package object after 7.1.0

package object tcp extends TypeAliases {

  private val sinceVersion = "7.0.0"

  @deprecated(a.deprecationMsg("ConnectionActor"), since = sinceVersion)
  val ConnectionActor = a.tcp.ConnectionActor

}
