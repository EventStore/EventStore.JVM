package eventstore

import eventstore.{akka => a}

// TODO(AHJ): Remove this package object after 7.1.0

package object tcp extends TypeAliases {

  @deprecated(a.deprecationMsg("ConnectionActor"), since = "7.0.0")
  val ConnectionActor = a.tcp.ConnectionActor

}
