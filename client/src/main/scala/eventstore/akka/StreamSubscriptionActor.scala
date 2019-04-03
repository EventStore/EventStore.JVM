package eventstore
package akka

import _root_.akka.actor.{ActorRef, Props}
import eventstore.akka.Settings.{Default => DS}

object StreamSubscriptionActor {

  def props(
    connection:          ActorRef,
    client:              ActorRef,
    streamId:            EventStream.Id,
    fromNumberExclusive: Option[EventNumber],
    credentials:         Option[UserCredentials],
    settings:            Settings
  ): Props = StreamAdapter.stream(
    connection, client, streamId, fromNumberExclusive, credentials, settings
  )

  // Java API
  def getProps(
    connection:            ActorRef,
    client:                ActorRef,
    streamId:              String,
    fromPositionExclusive: java.lang.Long,
    credentials:           UserCredentials,
    settings:              Settings
  ): Props = props(
    connection, client, EventStream.Id(streamId), Option(fromPositionExclusive).map(EventNumber.Exact(_)),
    Option(credentials), Option(settings).getOrElse(DS)
  )

  @deprecated("Use `getProps` with Settings as argument", "3.0.0")
  def getProps(
    connection:          ActorRef,
    client:              ActorRef,
    streamId:            EventStream.Id,
    fromNumberExclusive: Option[EventNumber],
    resolveLinkTos:      Boolean,
    credentials:         Option[UserCredentials],
    readBatchSize:       Int
  ): Props = props(
    connection, client, streamId, fromNumberExclusive, credentials,
    DS.copy(readBatchSize = readBatchSize, resolveLinkTos = resolveLinkTos)
  )

  @deprecated("Use `getProps` with Settings as argument", "3.0.0")
  def getProps(
    connection:          ActorRef,
    client:              ActorRef,
    streamId:            EventStream.Id,
    fromNumberExclusive: Option[EventNumber]
  ): Props = props(
    connection, client, streamId, fromNumberExclusive, None, DS
  )
}