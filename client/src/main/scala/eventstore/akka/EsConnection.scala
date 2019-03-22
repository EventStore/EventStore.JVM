package eventstore
package akka

import java.io.Closeable
import scala.concurrent.{ExecutionContext, Future}
import _root_.akka.NotUsed
import _root_.akka.actor._
import _root_.akka.pattern.ask
import _root_.akka.stream.ActorMaterializer
import _root_.akka.stream.scaladsl._
import _root_.akka.util.Timeout
import org.reactivestreams.Publisher
import eventstore.akka.tcp.ConnectionActor
import eventstore.akka.streams.{AllStreamsSourceStage, StreamSourceStage}

/**
 * Maintains a full duplex connection to the EventStore
 * <p>
 * All operations are handled in a full async manner.
 * Many threads can use an [[EsConnection]] at the same time or a single thread can make many asynchronous requests.
 * To get the most performance out of the connection it is generally recommended to use it in this way.
 */
class EsConnection(
    connection: ActorRef,
    factory:    ActorRefFactory,
    settings:   Settings        = Settings.Default
) {
  private val materializer = ActorMaterializer()(factory)

  implicit val timeout = Timeout(settings.operationTimeout)

  def apply[OUT <: Out, IN <: In](out: OUT, credentials: Option[UserCredentials] = None)(
    implicit outIn: ClassTags[OUT, IN]
  ): Future[IN] = {

    val future = connection ? credentials.fold[OutLike](out)(WithCredentials(out, _))
    future.mapTo[IN](outIn.in)
  }

  /**
   * Starts a transaction in the event store on a given stream asynchronously
   * <p>
   * [[EsTransaction]] allows the calling of multiple writes with multiple
   * round trips over long periods of time between the caller and the event store. This method
   * is only available through the TCP interface and no equivalent exists for the RESTful interface.
   *
   * @param data        Stream id and other params to star transaction
   * @param credentials The optional user credentials to perform operation with
   * @return A [[Future]] containing an actual transaction
   */
  def startTransaction(data: TransactionStart, credentials: Option[UserCredentials] = None): Future[EsTransaction] = {
    val props = TransactionActor.props(connection, TransactionActor.Start(data), credentials = credentials)
    val actor = factory.actorOf(props)
    EsTransaction.start(actor)
  }

  /**
   * Continues transaction by provided transaction ID.
   * <p>
   * [[EsTransaction]] allows the calling of multiple writes with multiple
   * round trips over long periods of time between the caller and the event store. This method
   * is only available through the TCP interface and no equivalent exists for the RESTful interface.
   *
   * @param transactionId The transaction ID that needs to be continued.
   * @param credentials   The optional user credentials to perform operation with
   * @return [[EsTransaction]] for given transaction id
   */
  def continueTransaction(transactionId: Long, credentials: Option[UserCredentials] = None): EsTransaction = {
    val props = TransactionActor.props(connection, TransactionActor.Continue(transactionId), credentials = credentials)
    val actor = factory.actorOf(props)
    EsTransaction.continue(transactionId, actor)
  }

  /**
   * Subscribes to a single event stream. New events
   * written to the stream while the subscription is active will be
   * pushed to the client.
   *
   * @param streamId       The stream to subscribe to
   * @param observer       A [[SubscriptionObserver]] to handle a new event received over the subscription
   * @param resolveLinkTos Whether to resolve LinkTo events automatically
   * @param credentials    The optional user credentials to perform operation with
   * @return A [[Closeable]] representing the subscription which can be closed.
   */
  def subscribeToStream(
    streamId:       EventStream.Id,
    observer:       SubscriptionObserver[Event],
    resolveLinkTos: Boolean                     = settings.resolveLinkTos,
    credentials:    Option[UserCredentials]     = None
  ): Closeable = {

    subscribeToStream(streamId, observer, Some(EventNumber.Last), resolveLinkTos, credentials)
  }

  /**
   * Subscribes to a single event stream. Existing events from
   * lastCheckpoint onwards are read from the stream
   * and presented to the user of [[SubscriptionObserver]]
   * as if they had been pushed.
   * <p>
   * Once the end of the stream is read the subscription is
   * transparently (to the user) switched to push new events as
   * they are written.
   * <p>
   * If events have already been received and resubscription from the same point
   * is desired, use the event number of the last event processed which
   * appeared on the subscription.
   *
   * @param streamId            The stream to subscribe to
   * @param observer            A [[eventstore.SubscriptionObserver]] to handle a new event received over the subscription
   * @param fromNumberExclusive The event number from which to start, or `None` to read all events.
   * @param resolveLinkTos      Whether to resolve LinkTo events automatically
   * @param credentials         The optional user credentials to perform operation with
   * @return A [[Closeable]] representing the subscription which can be closed.
   */
  def subscribeToStreamFrom(
    streamId:            EventStream.Id,
    observer:            SubscriptionObserver[Event],
    fromNumberExclusive: Option[EventNumber.Exact]   = None,
    resolveLinkTos:      Boolean                     = settings.resolveLinkTos,
    credentials:         Option[UserCredentials]     = None
  ): Closeable = {

    subscribeToStream(streamId, observer, fromNumberExclusive, resolveLinkTos, credentials)
  }

  private def subscribeToStream(
    streamId:            EventStream.Id,
    observer:            SubscriptionObserver[Event],
    fromNumberExclusive: Option[EventNumber],
    resolveLinkTos:      Boolean,
    credentials:         Option[UserCredentials]
  ): Closeable = {

    val client = factory.actorOf(SubscriptionObserverActor.props(observer))
    val props = StreamSubscriptionActor.props(
      connection = connection,
      client = client,
      streamId = streamId,
      fromNumberExclusive = fromNumberExclusive,
      credentials = credentials,
      settings = settings.copy(resolveLinkTos = resolveLinkTos)
    )
    factory.actorOf(props)
    ActorCloseable(client)
  }

  /**
   * Subscribes to all events in the Event Store. New events written to the stream
   * while the subscription is active will be pushed to the client.
   *
   * @param observer       A [[SubscriptionObserver]] to handle a new event received over the subscription
   * @param resolveLinkTos Whether to resolve LinkTo events automatically
   * @param credentials    The optional user credentials to perform operation with
   * @return A [[Closeable]] representing the subscription which can be closed.
   */
  def subscribeToAll(
    observer:       SubscriptionObserver[IndexedEvent],
    resolveLinkTos: Boolean                            = settings.resolveLinkTos,
    credentials:    Option[UserCredentials]            = None
  ): Closeable = {

    subscribeToAll(observer, Some(Position.Last), resolveLinkTos, credentials)
  }

  /**
   * Subscribes to a all events. Existing events from position
   * onwards are read from the Event Store and presented to the user of
   * [[SubscriptionObserver]] as if they had been pushed.
   * <p>
   * Once the end of the stream is read the subscription is
   * transparently (to the user) switched to push new events as
   * they are written.
   * <p>
   * If events have already been received and resubscription from the same point
   * is desired, use the position representing the last event processed which
   * appeared on the subscription.
   *
   * @param observer              A [[SubscriptionObserver]] to handle a new event received over the subscription
   * @param fromPositionExclusive The position from which to start, or `None` to read all events
   * @param resolveLinkTos        Whether to resolve LinkTo events automatically
   * @param credentials           The optional user credentials to perform operation with
   * @return A [[Closeable]] representing the subscription which can be closed.
   */
  def subscribeToAllFrom(
    observer:              SubscriptionObserver[IndexedEvent],
    fromPositionExclusive: Option[Position.Exact]             = None,
    resolveLinkTos:        Boolean                            = settings.resolveLinkTos,
    credentials:           Option[UserCredentials]            = None
  ): Closeable = {

    subscribeToAll(observer, fromPositionExclusive, resolveLinkTos, credentials)
  }

  private def subscribeToAll(
    observer:              SubscriptionObserver[IndexedEvent],
    fromPositionExclusive: Option[Position],
    resolveLinkTos:        Boolean,
    credentials:           Option[UserCredentials]
  ) = {

    val client = factory actorOf SubscriptionObserverActor.props(observer)
    val props = SubscriptionActor.props(
      connection = connection,
      client = client,
      fromPositionExclusive = fromPositionExclusive,
      credentials,
      settings = settings.copy(resolveLinkTos = resolveLinkTos)
    )
    factory.actorOf(props)
    ActorCloseable(client)
  }

  def setStreamMetadata(
    streamId:                  EventStream.Id,
    metadata:                  Content,
    expectedMetastreamVersion: ExpectedVersion         = ExpectedVersion.Any,
    credentials:               Option[UserCredentials] = None
  )(implicit ec: ExecutionContext): Future[Option[WriteResult]] = {

    val writeEvents = WriteEvents.StreamMetadata(streamId.metadata, metadata, expectedMetastreamVersion)
    apply(writeEvents, credentials).map(WriteResult.opt)
  }

  // TODO think about replacing content with something similar to what is in the .Net client
  def getStreamMetadata(streamId: EventStream.Id, credentials: Option[UserCredentials] = None)(implicit ec: ExecutionContext): Future[Content] = {

    apply(ReadEvent.StreamMetadata(streamId.metadata), credentials).map {
      case ReadEventCompleted(Event.StreamMetadata(data)) => data
      case ReadEventCompleted(EventRecord.Deleted)        => Content.Empty
      case ReadEventCompleted(event)                      => throw NonMetadataEventException(event)
    }.recover {
      case _: StreamNotFoundException => Content.Empty
      case _: StreamDeletedException  => Content.Empty
    }
  }

  /**
   * Creates Publisher you can use to subscribe to a single event stream. Existing events from
   * lastCheckpoint onwards are read from the stream
   * and presented to the user of `Publisher`
   * as if they had been pushed.
   * <p>
   * Once the end of the stream is read the subscription is
   * transparently (to the user) switched to push new events as
   * they are written.
   * <p>
   * If events have already been received and resubscription from the same point
   * is desired, use the event number of the last event processed which
   * appeared on the subscription.
   *
   * @param streamId            The stream to publish
   * @param fromNumberExclusive The event number from which to start, or `None` to read all events.
   * @param resolveLinkTos      Whether to resolve LinkTo events automatically
   * @param credentials         The optional user credentials to perform operation with
   * @param infinite            Whether to subscribe to the future events upon reading all current
   * @param readBatchSize       Number of events to be retrieved by client as single message
   * @return A [[org.reactivestreams.Publisher]] representing stream
   */
  @deprecated("Use `streamSource(..).runWith(Sink.asPublisher(..))` instead.", since = "5.0.8")
  def streamPublisher(
    streamId:            EventStream.Id,
    fromNumberExclusive: Option[EventNumber]     = None,
    resolveLinkTos:      Boolean                 = settings.resolveLinkTos,
    credentials:         Option[UserCredentials] = None,
    infinite:            Boolean                 = true,
    readBatchSize:       Int                     = settings.readBatchSize
  ): Publisher[Event] = {

    streamSource(
      streamId,
      fromNumberExclusive,
      resolveLinkTos,
      credentials,
      infinite,
      readBatchSize
    ).runWith(Sink.asPublisher(fanout = true))(materializer)
  }

  /**
   * Creates a [[Source]] you can use to subscribe to a single event stream. Existing events from
   * event number onwards are read from the stream and presented to the user of
   * [[Source]] as if they had been pushed.
   * <p>
   * Once the end of the stream is read the [[Source]] transparently (to the user)
   * switches to push new events as they are written.
   * <p>
   * If events have already been received and resubscription from the same point
   * is desired, use the event number of the last event processed.
   *
   * @param streamId                 The stream to subscribe to
   * @param fromEventNumberExclusive The event number from which to start, or [[None]] to read all events.
   * @param resolveLinkTos           Whether to resolve LinkTo events automatically
   * @param credentials              The optional user credentials to perform operation with
   * @param infinite                 Whether to subscribe to the future events upon reading all current
   * @param readBatchSize            Number of events to be retrieved by client as single message
   * @return A [[_root_.akka.stream.scaladsl.Source]] representing the stream
   */
  def streamSource(
    streamId:                 EventStream.Id,
    fromEventNumberExclusive: Option[EventNumber]     = None,
    resolveLinkTos:           Boolean                 = settings.resolveLinkTos,
    credentials:              Option[UserCredentials] = None,
    infinite:                 Boolean                 = true,
    readBatchSize:            Int                     = settings.readBatchSize
  ): Source[Event, NotUsed] = Source.fromGraph(
    new StreamSourceStage(
      connection = connection,
      streamId = streamId,
      fromNumberExclusive = fromEventNumberExclusive,
      credentials = credentials,
      settings = settings.copy(resolveLinkTos = resolveLinkTos, readBatchSize = readBatchSize),
      infinite = infinite
    )
  )

  /**
   * Creates Publisher you can use to subscribes to a all events. Existing events from position
   * onwards are read from the Event Store and presented to the user of
   * `Publisher` as if they had been pushed.
   * <p>
   * Once the end of the stream is read the subscription is
   * transparently (to the user) switched to push new events as
   * they are written.
   * <p>
   * If events have already been received and resubscription from the same point
   * is desired, use the position representing the last event processed which
   * appeared on the subscription.
   *
   * @param resolveLinkTos        Whether to resolve LinkTo events automatically
   * @param fromPositionExclusive The position from which to start, or `None` to read all events
   * @param credentials           The optional user credentials to perform operation with
   * @param infinite              Whether to subscribe to the future events upon reading all current
   * @param readBatchSize         Number of events to be retrieved by client as single message
   * @return A [[org.reactivestreams.Publisher]] representing all streams
   */
  @deprecated("Use `allStreamsSource(..).runWith(Sink.asPublisher(..))` instead.", since = "5.0.8")
  def allStreamsPublisher(
    resolveLinkTos:        Boolean                 = settings.resolveLinkTos,
    fromPositionExclusive: Option[Position]        = None,
    credentials:           Option[UserCredentials] = None,
    infinite:              Boolean                 = true,
    readBatchSize:         Int                     = settings.readBatchSize
  ): Publisher[IndexedEvent] = {

    allStreamsSource(
      resolveLinkTos,
      fromPositionExclusive,
      credentials,
      infinite,
      readBatchSize
    ).runWith(Sink.asPublisher(fanout = true))(materializer)
  }

  /**
   * Creates a [[Source]] you can use to subscribes to all events. Existing events from position
   * onwards are read from the Event Store and presented to the user of
   * [[Source]] as if they had been pushed.
   * <p>
   * Once the end of the stream is read the [[Source]] transparently (to the user)
   * switches to push new events as they are written.
   * <p>
   * If events have already been received and resubscription from the same point
   * is desired, use the position representing the last event processed.
   *
   * @param resolveLinkTos        Whether to resolve LinkTo events automatically
   * @param fromPositionExclusive The position from which to start, or [[None]] to read all events
   * @param credentials           The optional user credentials to perform operation with
   * @param infinite              Whether to subscribe to the future events upon reading all current
   * @param readBatchSize         Number of events to be retrieved by client as single message
   * @return A [[_root_.akka.stream.scaladsl.Source]] representing all streams
   */
  def allStreamsSource(
    resolveLinkTos:        Boolean                 = settings.resolveLinkTos,
    fromPositionExclusive: Option[Position]        = None,
    credentials:           Option[UserCredentials] = None,
    infinite:              Boolean                 = true,
    readBatchSize:         Int                     = settings.readBatchSize
  ): Source[IndexedEvent, NotUsed] = Source.fromGraph(
    new AllStreamsSourceStage(
      connection = connection,
      fromPositionExclusive = fromPositionExclusive,
      credentials = credentials,
      settings = settings.copy(resolveLinkTos = resolveLinkTos, readBatchSize = readBatchSize),
      infinite = infinite
    )
  )

}

object EsConnection {

  def apply(system: ActorSystem, settings: Settings = Settings.Default): EsConnection = {
    val props = ConnectionActor props settings
    new EsConnection(
      connection = system actorOf props,
      factory = system,
      settings = settings
    )
  }
}