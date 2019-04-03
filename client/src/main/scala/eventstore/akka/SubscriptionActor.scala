package eventstore
package akka

import _root_.akka.actor.{ActorRef, Props}
import eventstore.akka.Settings.{Default => DS}

object SubscriptionActor {

  def props(
    connection:            ActorRef,
    client:                ActorRef,
    fromPositionExclusive: Option[Position],
    credentials:           Option[UserCredentials],
    settings:              Settings
  ): Props = StreamAdapter.all(
    connection, client, fromPositionExclusive, credentials, settings
  )

  // Java API
  def getProps(
    connection:            ActorRef,
    client:                ActorRef,
    fromPositionExclusive: Position.Exact,
    credentials:           UserCredentials,
    settings:              Settings
  ): Props = props(
    connection, client, Option(fromPositionExclusive), Option(credentials), Option(settings).getOrElse(DS)
  )

  @deprecated("Use `getProps` with Settings as argument", "3.0.0")
  def getProps(
    connection:            ActorRef,
    client:                ActorRef,
    fromPositionExclusive: Option[Position],
    resolveLinkTos:        Boolean,
    credentials:           Option[UserCredentials],
    readBatchSize:         Int
  ): Props = props(
    connection, client, fromPositionExclusive, credentials,
    DS.copy(resolveLinkTos = resolveLinkTos, readBatchSize = readBatchSize)
  )

  @deprecated("Use `getProps` with Settings as argument", "3.0.0")
  def getProps(connection: ActorRef, client: ActorRef, fromPositionExclusive: Option[Position]): Props = props(
    connection, client, fromPositionExclusive, None, DS
  )

}