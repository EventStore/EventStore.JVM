package eventstore

import scala.concurrent.Future

/**
 * @author Yaroslav Klymko
 */
trait EventStoreConnection {

  def connectionName: String

  def connect: Future[Unit]

  def close()

  /**
   * @param streamId The name of the stream to be deleted
   * @param expVer The expected version the stream should have when being deleted.
   * @param credentials The optional user credentials to perform operation with.
   */
  def deleteStream(streamId: String, expVer: ExpectedVersion, credentials: Option[UserCredentials] = None)

  //      /// <summary>
  //      /// Appends Events synchronously to a stream.
  //      /// </summary>
  //      /// <remarks>
  //      /// When appending events to a stream the <see cref="ExpectedVersion"/> choice can
  //      /// make a very large difference in the observed behavior. If no stream exists
  //      /// and ExpectedVersion.Any is used. A new stream will be implicitly created when appending
  //      /// as an example.
  //      ///
  //      /// There are also differences in idempotency between different types of calls.
  //      /// If you specify an ExpectedVersion aside from ExpectedVersion.Any the Event Store
  //      /// will give you an idempotency guarantee. If using ExpectedVersion.Any the Event Store
  //      /// will do its best to provide idempotency but does not guarantee idempotency.
  //      /// </remarks>
  //      /// <param name="stream">The name of the stream to append the events to.</param>
  //      /// <param name="expectedVersion">The expected version of the stream</param>
  //      /// <param name="events">The events to write to the stream</param>
  //      /// <param name="userCredentials">The optional user credentials to perform operation with.</param>
  //      void AppendToStream(streamId:String, int expectedVersion, IEnumerable<EventData> events, credentials: Option[UserCredentials] = None);

  def appendToStream(streamId: String, expVer: ExpectedVersion, events: List[EventRecord], credentials: Option[UserCredentials] = None) // TODO

  //      /// <summary>
  //      /// Starts a transaction in the event store on a given stream asynchronously
  //      /// </summary>
  //      /// <remarks>
  //      /// A <see cref="EventStoreTransaction"/> allows the calling of multiple writes with multiple
  //      /// round trips over long periods of time between the caller and the event store. This method
  //      /// is only available through the TCP interface and no equivalent exists for the RESTful interface.
  //      /// </remarks>
  //      /// <param name="stream">The stream to start a transaction on</param>
  //      /// <param name="expectedVersion">The expected version of the stream at the time of starting the transaction</param>
  //      /// <param name="userCredentials">The optional user credentials to perform operation with.</param>
  //      /// <returns>A task the caller can use to control the operation.</returns>
  //      Task<EventStoreTransaction> StartTransactionAsync(streamId:String, int expectedVersion, credentials: Option[UserCredentials] = None);
  def startTransaction(streamId: String, expVer: ExpectedVersion, credentials: Option[UserCredentials] = None): Future[EventStoreTransaction]

  //      /// <summary>
  //      /// Continues transaction by provided transaction ID.
  //      /// </summary>
  //      /// <remarks>
  //      /// A <see cref="EventStoreTransaction"/> allows the calling of multiple writes with multiple
  //      /// round trips over long periods of time between the caller and the event store. This method
  //      /// is only available through the TCP interface and no equivalent exists for the RESTful interface.
  //      /// </remarks>
  //      /// <param name="transactionId">The transaction ID that needs to be continued.</param>
  //      /// <param name="userCredentials">The optional user credentials to perform operation with.</param>
  //      /// <returns><see cref="EventStoreTransaction"/> object.</returns>
  //      EventStoreTransaction ContinueTransaction(long transactionId, credentials: Option[UserCredentials] = None);
  def continueTransaction(transactionId: Long, credentials: Option[UserCredentials] = None): Future[EventStoreTransaction]

  //
  //      /// <summary>
  //      /// Reads count Events from an Event Stream forwards (e.g. oldest to newest) starting from position start
  //      /// </summary>
  //      /// <param name="stream">The stream to read from</param>
  //      /// <param name="eventNumber">The event number to read, -1 (<see cref="StreamPosition">StreamPosition.End</see>) for reading latest event in the stream</param>
  //      /// <param name="resolveLinkTos">Whether to resolve LinkTo events automatically</param>
  //      /// <param name="userCredentials">The optional user credentials to perform operation with.</param>
  //      /// <returns>A <see cref="EventReadResult"/> containing the results of the read operation</returns>
  //      EventReadResult ReadEvent(streamId:String, int eventNumber, resolveLinkTos:Boolean, credentials: Option[UserCredentials] = None);

  def readEvent(streamId: String, eventNumber: Int, resolveLinkTos: Boolean, credentials: Option[UserCredentials] = None): Future[EventReadResult]

  //      /// <summary>
  //      /// Reads count Events from an Event Stream forwards (e.g. oldest to newest) starting from position start
  //      /// </summary>
  //      /// <param name="stream">The stream to read from</param>
  //      /// <param name="start">The starting point to read from</param>
  //      /// <param name="count">The count of items to read</param>
  //      /// <param name="resolveLinkTos">Whether to resolve LinkTo events automatically</param>
  //      /// <param name="userCredentials">The optional user credentials to perform operation with.</param>
  //      /// <returns>A <see cref="StreamEventsSlice"/> containing the results of the read operation</returns>
  //      StreamEventsSlice ReadStreamEventsForward(streamId:String, int start, int count, resolveLinkTos:Boolean, credentials: Option[UserCredentials] = None);
  def readStreamEventsForward(streamId: String, start: Int, count: Int, resolveLinkTos: Boolean, credentials: Option[UserCredentials] = None): Future[StreamEventsSlice]

  //      /// <summary>
  //      /// Reads count events from an Event Stream backwards (e.g. newest to oldest) from position
  //      /// </summary>
  //      /// <param name="stream">The Event Stream to read from</param>
  //      /// <param name="start">The position to start reading from</param>
  //      /// <param name="count">The count to read from the position</param>
  //      /// <param name="resolveLinkTos">Whether to resolve LinkTo events automatically</param>
  //      /// <param name="userCredentials">The optional user credentials to perform operation with.</param>
  //      /// <returns>An <see cref="StreamEventsSlice"/> containing the results of the read operation</returns>
  //      StreamEventsSlice ReadStreamEventsBackward(streamId:String, int start, int count, resolveLinkTos:Boolean, credentials: Option[UserCredentials] = None);
  def readStreamEventsBackward(streamId: String, start: Int, count: Int, resolveLinkTos: Boolean, credentials: Option[UserCredentials] = None): Future[StreamEventsSlice]

  //
  //      /// <summary>
  //      /// Reads All Events in the node forward. (e.g. beginning to end)
  //      /// </summary>
  //      /// <param name="position">The position to start reading from</param>
  //      /// <param name="maxCount">The maximum count to read</param>
  //      /// <param name="resolveLinkTos">Whether to resolve LinkTo events automatically</param>
  //      /// <param name="userCredentials">The optional user credentials to perform operation with.</param>
  //      /// <returns>A <see cref="AllEventsSlice"/> containing the records read</returns>
  //      AllEventsSlice ReadAllEventsForward(position:Position , maxCount:Int, resolveLinkTos:Boolean, credentials: Option[UserCredentials] = None);
  def readAllEventsForward(position: Position, maxCount: Int, resolveLinkTos: Boolean, credentials: Option[UserCredentials] = None): Future[AllEventsSlice]

  //      /// <summary>
  //      /// Reads All Events in the node backwards (e.g. end to beginning)
  //      /// </summary>
  //      /// <param name="position">The position to start reading from</param>
  //      /// <param name="maxCount">The maximum count to read</param>
  //      /// <param name="resolveLinkTos">Whether to resolve LinkTo events automatically</param>
  //      /// <param name="userCredentials">The optional user credentials to perform operation with.</param>
  //      /// <returns>A <see cref="AllEventsSlice"/> containing the records read</returns>
  //      AllEventsSlice ReadAllEventsBackward(position:Position , maxCount:Int, resolveLinkTos:Boolean, credentials: Option[UserCredentials] = None);
  def readAllEventsBackward(position: Position, maxCount: Int, resolveLinkTos: Boolean, credentials: Option[UserCredentials] = None): Future[AllEventsSlice]

  type SubscriptionDroppedFunc = (EventStoreSubscription, SubscriptionDropReason, Exception) => Any
  case class SubscriptionDropReason()

  def subscribeToStream(streamId: String,
    resolveLinkTos: Boolean,
    eventAppeared: (EventStoreSubscription, ResolvedEvent) => Any,
    subscriptionDropped: Option[SubscriptionDroppedFunc] = None,
    credentials: Option[UserCredentials] = None): Future[EventStoreSubscription]

  class EventStoreStreamCatchUpSubscription
  class EventStoreCatchUpSubscription

  type EventAppeared = (EventStoreCatchUpSubscription, ResolvedEvent) => Any
  type SubscriptionDroppedFFFFF = (EventStoreCatchUpSubscription, SubscriptionDropReason, Exception) => Any
  type LiveProcessingStarted = EventStoreCatchUpSubscription => Any

  def subscribeToStreamFrom(streamId: String,
    fromEventNumberExclusive: Int,
    resolveLinkTos: Boolean,
    eventAppeared: EventAppeared,
    liveProcessingStarted: Option[LiveProcessingStarted],
    subscriptionDropped: Option[SubscriptionDroppedFFFFF],
    credentials: Option[UserCredentials] = None): Future[EventStoreStreamCatchUpSubscription]

  def subscribeToAll(resolveLinkTos: Boolean,
    eventAppeared: (EventStoreSubscription, ResolvedEvent) => Any,
    subscriptionDropped: Option[SubscriptionDroppedFunc] = None,
    credentials: Option[UserCredentials] = None): Future[EventStoreSubscription]

  class EventStoreAllCatchUpSubscription
  class StreamMetadata

  def subscribeToAllFrom(fromPositionExclusive: Position,
    resolveLinkTos: Boolean,
    eventAppeared: EventAppeared,
    liveProcessingStarted: Option[LiveProcessingStarted] = None,
    subscriptionDropped: Option[SubscriptionDroppedFFFFF] = None,
    credentials: Option[UserCredentials] = None): EventStoreAllCatchUpSubscription

  // TODO
  //  def setStreamMetadata(streamId: String, expectedMetastreamVersion: Int, metadata: StreamMetadata, credentials: Option[UserCredentials] = None)

  def setStreamMetadata(streamId: String, expectedMetastreamVersion: Int, metadata: ByteString, credentials: Option[UserCredentials] = None): Future[Unit]

  class StreamMetadataResult

  def getStreamMetadata(streamId: String, credentials: Option[UserCredentials] = None): Future[StreamMetadataResult]

  class RawStreamMetadataResult

  def getStreamMetadataAsRawBytes(streamId: String, credentials: Option[UserCredentials] = None): Future[RawStreamMetadataResult]
}

case class EventStoreTransaction()

case class EventReadResult() {
  /*namespace EventStore.ClientAPI
{
    public enum EventReadStatus
    {
        Success = 0,
        NotFound = 1,
        NoStream = 2,
        StreamDeleted = 3,
    }

    /// <summary>
    /// A Event Read Result is the result of a single event read operation to the event store.
    /// </summary>
    public class EventReadResult
    {
        /// <summary>
        /// The <see cref="EventReadStatus"/> representing the status of this read attempt
        /// </summary>
        public readonly EventReadStatus Status;

        /// <summary>
        /// The name of the stream read
        /// </summary>
        public readonly streamId:String;

        /// <summary>
        /// The event number of the requested event.
        /// </summary>
        public readonly int EventNumber;

        /// <summary>
        /// The event read represented as <see cref="ResolvedEvent"/>
        /// </summary>
        public readonly ResolvedEvent? Event;

        internal EventReadResult(EventReadStatus status,
                                 streamId:String,
                                 int eventNumber,
                                 ClientMessage.ResolvedIndexedEvent @event)
        {
            Ensure.NotNullOrEmpty(stream, "stream");

            Status = status;
            Stream = stream;
            EventNumber = eventNumber;
            Event = status == EventReadStatus.Success ? new ResolvedEvent(@event) : (ResolvedEvent?)null;
        }
    }
}*/
}

case class StreamEventsSlice() {
  /*namespace EventStore.ClientAPI
{
    /// <summary>
    /// An Stream Events Slice represents the result of a single read operation to the event store.
    /// </summary>
    public class StreamEventsSlice
    {
        /// <summary>
        /// The <see cref="SliceReadStatus"/> representing the status of this read attempt
        /// </summary>
        public readonly SliceReadStatus Status;

        /// <summary>
        /// The name of the stream read
        /// </summary>
        public readonly streamId:String;

        /// <summary>
        /// The starting point (represented as a sequence number) of the read operation.
        /// </summary>
        public readonly int FromEventNumber;

        /// <summary>
        /// The direction of read request.
        /// </summary>
        public readonly ReadDirection ReadDirection;

        /// <summary>
        /// The events read represented as <see cref="RecordedEvent"/>
        /// </summary>
        public readonly ResolvedEvent[] Events;

        /// <summary>
        /// The next event number that can be read.
        /// </summary>
        public readonly int NextEventNumber;

        /// <summary>
        /// The last event number in the stream.
        /// </summary>
        public readonly int LastEventNumber;

        /// <summary>
        /// A boolean representing whether or not this is the end of the stream.
        /// </summary>
        public readonly bool IsEndOfStream;

        internal StreamEventsSlice(SliceReadStatus status,
                                   streamId:String,
                                   int fromEventNumber,
                                   ReadDirection readDirection,
                                   ClientMessage.ResolvedIndexedEvent[] events,
                                   int nextEventNumber,
                                   int lastEventNumber,
                                   bool isEndOfStream)
        {
            Ensure.NotNullOrEmpty(stream, "stream");

            Status = status;
            Stream = stream;
            FromEventNumber = fromEventNumber;
            ReadDirection = readDirection;
            if (events == null || events.Length == 0)
                Events = Empty.ResolvedEvents;
            else
            {
                Events = new ResolvedEvent[events.Length];
                for (int i = 0; i < Events.Length; ++i)
                {
                    Events[i] = new ResolvedEvent(events[i]);
                }
            }
            NextEventNumber = nextEventNumber;
            LastEventNumber = lastEventNumber;
            IsEndOfStream = isEndOfStream;
        }
    }
}*/
}

case class AllEventsSlice()
case class EventStoreSubscription()

/*namespace EventStore.ClientAPI
{
    /// <summary>
    /// Maintains a full duplex connection to the EventStore
    /// </summary>
    /// <remarks>
    /// An <see cref="IEventStoreConnection"/> operates quite differently than say a <see cref="SqlConnection"/>. Normally
    /// when using an <see cref="IEventStoreConnection"/> you want to keep the connection open for a much longer of time than
    /// when you use a SqlConnection. If you prefer the usage pattern of using(new Connection()) .. then you would likely
    /// want to create a FlyWeight on top of the <see cref="EventStoreConnection"/>.
    ///
    /// Another difference is that with the <see cref="IEventStoreConnection"/> all operations are handled in a full async manner
    /// (even if you call the synchronous behaviors). Many threads can use an <see cref="IEventStoreConnection"/> at the same
    /// time or a single thread can make many asynchronous requests. To get the most performance out of the connection
    /// it is generally recommended to use it in this way.
    /// </remarks>
    public interface IEventStoreConnection : IDisposable
    {
        string ConnectionName { get; }

        /// <summary>
        /// Connects the <see cref="IEventStoreConnection"/> synchronously to a destination
        /// </summary>
        void Connect();

        /// <summary>
        /// Connects the <see cref="IEventStoreConnection"/> asynchronously to a destination
        /// </summary>
        /// <returns>A <see cref="Task"/> that can be waited upon.</returns>
        Task ConnectAsync();

        /// <summary>
        /// Closes this <see cref="IEventStoreConnection"/>
        /// </summary>
        void Close();

        /// <summary>
        /// Deletes a stream from the Event Store synchronously
        /// </summary>
        /// <param name="stream">The name of the stream to be deleted</param>
        /// <param name="expectedVersion">The expected version the stream should have when being deleted. <see cref="ExpectedVersion"/></param>
        /// <param name="userCredentials">The optional user credentials to perform operation with.</param>
        void DeleteStream(streamId:String, int expectedVersion, credentials: Option[UserCredentials] = None);

        /// <summary>
        /// Deletes a stream from the Event Store asynchronously
        /// </summary>
        /// <param name="stream">The name of the stream to delete.</param>
        /// <param name="expectedVersion">The expected version that the streams should have when being deleted. <see cref="ExpectedVersion"/></param>
        /// <param name="userCredentials">The optional user credentials to perform operation with.</param>
        /// <returns>A <see cref="Task"/> that can be awaited upon by the caller.</returns>
        Task DeleteStreamAsync(streamId:String, int expectedVersion, credentials: Option[UserCredentials] = None);

        /// <summary>
        /// Appends Events synchronously to a stream.
        /// </summary>
        /// <remarks>
        /// When appending events to a stream the <see cref="ExpectedVersion"/> choice can
        /// make a very large difference in the observed behavior. If no stream exists
        /// and ExpectedVersion.Any is used. A new stream will be implicitly created when appending
        /// as an example.
        ///
        /// There are also differences in idempotency between different types of calls.
        /// If you specify an ExpectedVersion aside from ExpectedVersion.Any the Event Store
        /// will give you an idempotency guarantee. If using ExpectedVersion.Any the Event Store
        /// will do its best to provide idempotency but does not guarantee idempotency.
        /// </remarks>
        /// <param name="stream">The name of the stream to append the events to.</param>
        /// <param name="expectedVersion">The expected version of the stream</param>
        /// <param name="events">The events to write to the stream</param>
        void AppendToStream(streamId:String, int expectedVersion, params EventData[] events);

        /// <summary>
        /// Appends Events synchronously to a stream.
        /// </summary>
        /// <remarks>
        /// When appending events to a stream the <see cref="ExpectedVersion"/> choice can
        /// make a very large difference in the observed behavior. If no stream exists
        /// and ExpectedVersion.Any is used. A new stream will be implicitly created when appending
        /// as an example.
        ///
        /// There are also differences in idempotency between different types of calls.
        /// If you specify an ExpectedVersion aside from ExpectedVersion.Any the Event Store
        /// will give you an idempotency guarantee. If using ExpectedVersion.Any the Event Store
        /// will do its best to provide idempotency but does not guarantee idempotency.
        /// </remarks>
        /// <param name="stream">The name of the stream to append the events to.</param>
        /// <param name="expectedVersion">The expected version of the stream</param>
        /// <param name="userCredentials">The optional user credentials to perform operation with.</param>
        /// <param name="events">The events to write to the stream</param>
        void AppendToStream(streamId:String, int expectedVersion, UserCredentials userCredentials, params EventData[] events);

        /// <summary>
        /// Appends Events synchronously to a stream.
        /// </summary>
        /// <remarks>
        /// When appending events to a stream the <see cref="ExpectedVersion"/> choice can
        /// make a very large difference in the observed behavior. If no stream exists
        /// and ExpectedVersion.Any is used. A new stream will be implicitly created when appending
        /// as an example.
        ///
        /// There are also differences in idempotency between different types of calls.
        /// If you specify an ExpectedVersion aside from ExpectedVersion.Any the Event Store
        /// will give you an idempotency guarantee. If using ExpectedVersion.Any the Event Store
        /// will do its best to provide idempotency but does not guarantee idempotency.
        /// </remarks>
        /// <param name="stream">The name of the stream to append the events to.</param>
        /// <param name="expectedVersion">The expected version of the stream</param>
        /// <param name="events">The events to write to the stream</param>
        /// <param name="userCredentials">The optional user credentials to perform operation with.</param>
        void AppendToStream(streamId:String, int expectedVersion, IEnumerable<EventData> events, credentials: Option[UserCredentials] = None);

        /// <summary>
        /// Appends Events asynchronously to a stream.
        /// </summary>
        /// <remarks>
        /// When appending events to a stream the <see cref="ExpectedVersion"/> choice can
        /// make a very large difference in the observed behavior. If no stream exists
        /// and ExpectedVersion.Any is used. A new stream will be implicitly created when appending
        /// as an example.
        ///
        /// There are also differences in idempotency between different types of calls.
        /// If you specify an ExpectedVersion aside from ExpectedVersion.Any the Event Store
        /// will give you an idempotency guarantee. If using ExpectedVersion.Any the Event Store
        /// will do its best to provide idempotency but does not guarantee idempotency
        /// </remarks>
        /// <param name="stream">The name of the stream to append events to</param>
        /// <param name="expectedVersion">The <see cref="ExpectedVersion"/> of the stream to append to</param>
        /// <param name="events">The events to append to the stream</param>
        /// <returns>a <see cref="Task"/> that the caller can await on.</returns>
        Task AppendToStreamAsync(streamId:String, int expectedVersion, params EventData[] events);

        /// <summary>
        /// Appends Events asynchronously to a stream.
        /// </summary>
        /// <remarks>
        /// When appending events to a stream the <see cref="ExpectedVersion"/> choice can
        /// make a very large difference in the observed behavior. If no stream exists
        /// and ExpectedVersion.Any is used. A new stream will be implicitly created when appending
        /// as an example.
        ///
        /// There are also differences in idempotency between different types of calls.
        /// If you specify an ExpectedVersion aside from ExpectedVersion.Any the Event Store
        /// will give you an idempotency guarantee. If using ExpectedVersion.Any the Event Store
        /// will do its best to provide idempotency but does not guarantee idempotency
        /// </remarks>
        /// <param name="stream">The name of the stream to append events to</param>
        /// <param name="expectedVersion">The <see cref="ExpectedVersion"/> of the stream to append to</param>
        /// <param name="userCredentials">The optional user credentials to perform operation with.</param>
        /// <param name="events">The events to append to the stream</param>
        /// <returns>a <see cref="Task"/> that the caller can await on.</returns>
        Task AppendToStreamAsync(streamId:String, int expectedVersion, UserCredentials userCredentials, params EventData[] events);

        /// <summary>
        /// Appends Events asynchronously to a stream.
        /// </summary>
        /// <remarks>
        /// When appending events to a stream the <see cref="ExpectedVersion"/> choice can
        /// make a very large difference in the observed behavior. If no stream exists
        /// and ExpectedVersion.Any is used. A new stream will be implicitly created when appending
        /// as an example.
        ///
        /// There are also differences in idempotency between different types of calls.
        /// If you specify an ExpectedVersion aside from ExpectedVersion.Any the Event Store
        /// will give you an idempotency guarantee. If using ExpectedVersion.Any the Event Store
        /// will do its best to provide idempotency but does not guarantee idempotency
        /// </remarks>
        /// <param name="stream">The name of the stream to append events to</param>
        /// <param name="expectedVersion">The <see cref="ExpectedVersion"/> of the stream to append to</param>
        /// <param name="events">The events to append to the stream</param>
        /// <param name="userCredentials">The optional user credentials to perform operation with.</param>
        /// <returns>a <see cref="Task"/> that the caller can await on.</returns>
        Task AppendToStreamAsync(streamId:String, int expectedVersion, IEnumerable<EventData> events, credentials: Option[UserCredentials] = None);

        /// <summary>
        /// Starts a transaction in the event store on a given stream
        /// </summary>
        /// <remarks>
        /// A <see cref="EventStoreTransaction"/> allows the calling of multiple writes with multiple
        /// round trips over long periods of time between the caller and the event store. This method
        /// is only available through the TCP interface and no equivalent exists for the RESTful interface.
        /// </remarks>
        /// <param name="stream">The stream to start a transaction on</param>
        /// <param name="expectedVersion">The expected version when starting a transaction</param>
        /// <param name="userCredentials">The optional user credentials to perform operation with.</param>
        /// <returns>An <see cref="EventStoreTransaction"/> that can be used to control a series of operations.</returns>
        EventStoreTransaction StartTransaction(streamId:String, int expectedVersion, credentials: Option[UserCredentials] = None);

        /// <summary>
        /// Starts a transaction in the event store on a given stream asynchronously
        /// </summary>
        /// <remarks>
        /// A <see cref="EventStoreTransaction"/> allows the calling of multiple writes with multiple
        /// round trips over long periods of time between the caller and the event store. This method
        /// is only available through the TCP interface and no equivalent exists for the RESTful interface.
        /// </remarks>
        /// <param name="stream">The stream to start a transaction on</param>
        /// <param name="expectedVersion">The expected version of the stream at the time of starting the transaction</param>
        /// <param name="userCredentials">The optional user credentials to perform operation with.</param>
        /// <returns>A task the caller can use to control the operation.</returns>
        Task<EventStoreTransaction> StartTransactionAsync(streamId:String, int expectedVersion, credentials: Option[UserCredentials] = None);

        /// <summary>
        /// Continues transaction by provided transaction ID.
        /// </summary>
        /// <remarks>
        /// A <see cref="EventStoreTransaction"/> allows the calling of multiple writes with multiple
        /// round trips over long periods of time between the caller and the event store. This method
        /// is only available through the TCP interface and no equivalent exists for the RESTful interface.
        /// </remarks>
        /// <param name="transactionId">The transaction ID that needs to be continued.</param>
        /// <param name="userCredentials">The optional user credentials to perform operation with.</param>
        /// <returns><see cref="EventStoreTransaction"/> object.</returns>
        EventStoreTransaction ContinueTransaction(long transactionId, credentials: Option[UserCredentials] = None);

        /// <summary>
        /// Reads count Events from an Event Stream forwards (e.g. oldest to newest) starting from position start
        /// </summary>
        /// <param name="stream">The stream to read from</param>
        /// <param name="eventNumber">The event number to read, -1 (<see cref="StreamPosition">StreamPosition.End</see>) for reading latest event in the stream</param>
        /// <param name="resolveLinkTos">Whether to resolve LinkTo events automatically</param>
        /// <param name="userCredentials">The optional user credentials to perform operation with.</param>
        /// <returns>A <see cref="EventReadResult"/> containing the results of the read operation</returns>
        EventReadResult ReadEvent(streamId:String, int eventNumber, resolveLinkTos:Boolean, credentials: Option[UserCredentials] = None);

        /// <summary>
        /// Reads count Events from an Event Stream forwards (e.g. oldest to newest) starting from position start
        /// </summary>
        /// <param name="stream">The stream to read from</param>
        /// <param name="eventNumber">The event number to read, -1 (<see cref="StreamPosition">StreamPosition.End</see>) for reading latest event in the stream</param>
        /// <param name="resolveLinkTos">Whether to resolve LinkTo events automatically</param>
        /// <param name="userCredentials">The optional user credentials to perform operation with.</param>
        /// <returns>A <see cref="Task&lt;EventReadResult&gt;"/> containing the results of the read operation</returns>
        Task<EventReadResult> ReadEventAsync(streamId:String, int eventNumber, resolveLinkTos:Boolean, credentials: Option[UserCredentials] = None);

        /// <summary>
        /// Reads count Events from an Event Stream forwards (e.g. oldest to newest) starting from position start
        /// </summary>
        /// <param name="stream">The stream to read from</param>
        /// <param name="start">The starting point to read from</param>
        /// <param name="count">The count of items to read</param>
        /// <param name="resolveLinkTos">Whether to resolve LinkTo events automatically</param>
        /// <param name="userCredentials">The optional user credentials to perform operation with.</param>
        /// <returns>A <see cref="StreamEventsSlice"/> containing the results of the read operation</returns>
        StreamEventsSlice ReadStreamEventsForward(streamId:String, int start, int count, resolveLinkTos:Boolean, credentials: Option[UserCredentials] = None);

        /// <summary>
        /// Reads count Events from an Event Stream forwards (e.g. oldest to newest) starting from position start
        /// </summary>
        /// <param name="stream">The stream to read from</param>
        /// <param name="start">The starting point to read from</param>
        /// <param name="count">The count of items to read</param>
        /// <param name="resolveLinkTos">Whether to resolve LinkTo events automatically</param>
        /// <param name="userCredentials">The optional user credentials to perform operation with.</param>
        /// <returns>A <see cref="Task&lt;StreamEventsSlice&gt;"/> containing the results of the read operation</returns>
        Task<StreamEventsSlice> ReadStreamEventsForwardAsync(streamId:String, int start, int count, resolveLinkTos:Boolean, credentials: Option[UserCredentials] = None);

        /// <summary>
        /// Reads count events from an Event Stream backwards (e.g. newest to oldest) from position
        /// </summary>
        /// <param name="stream">The Event Stream to read from</param>
        /// <param name="start">The position to start reading from</param>
        /// <param name="count">The count to read from the position</param>
        /// <param name="resolveLinkTos">Whether to resolve LinkTo events automatically</param>
        /// <param name="userCredentials">The optional user credentials to perform operation with.</param>
        /// <returns>An <see cref="StreamEventsSlice"/> containing the results of the read operation</returns>
        StreamEventsSlice ReadStreamEventsBackward(streamId:String, int start, int count, resolveLinkTos:Boolean, credentials: Option[UserCredentials] = None);

        /// <summary>
        /// Reads count events from an Event Stream backwards (e.g. newest to oldest) from position asynchronously
        /// </summary>
        /// <param name="stream">The Event Stream to read from</param>
        /// <param name="start">The position to start reading from</param>
        /// <param name="count">The count to read from the position</param>
        /// <param name="resolveLinkTos">Whether to resolve LinkTo events automatically</param>
        /// <param name="userCredentials">The optional user credentials to perform operation with.</param>
        /// <returns>An <see cref="Task&lt;StreamEventsSlice&gt;"/> containing the results of the read operation</returns>
        Task<StreamEventsSlice> ReadStreamEventsBackwardAsync(streamId:String, int start, int count, resolveLinkTos:Boolean, credentials: Option[UserCredentials] = None);

        /// <summary>
        /// Reads All Events in the node forward. (e.g. beginning to end)
        /// </summary>
        /// <param name="position">The position to start reading from</param>
        /// <param name="maxCount">The maximum count to read</param>
        /// <param name="resolveLinkTos">Whether to resolve LinkTo events automatically</param>
        /// <param name="userCredentials">The optional user credentials to perform operation with.</param>
        /// <returns>A <see cref="AllEventsSlice"/> containing the records read</returns>
        AllEventsSlice ReadAllEventsForward(position:Position , maxCount:Int, resolveLinkTos:Boolean, credentials: Option[UserCredentials] = None);

        /// <summary>
        /// Reads All Events in the node forward asynchronously (e.g. beginning to end)
        /// </summary>
        /// <param name="position">The position to start reading from</param>
        /// <param name="maxCount">The maximum count to read</param>
        /// <param name="resolveLinkTos">Whether to resolve LinkTo events automatically</param>
        /// <param name="userCredentials">The optional user credentials to perform operation with.</param>
        /// <returns>A <see cref="AllEventsSlice"/> containing the records read</returns>
        Task<AllEventsSlice> ReadAllEventsForwardAsync(position:Position , maxCount:Int, resolveLinkTos:Boolean, credentials: Option[UserCredentials] = None);

        /// <summary>
        /// Reads All Events in the node backwards (e.g. end to beginning)
        /// </summary>
        /// <param name="position">The position to start reading from</param>
        /// <param name="maxCount">The maximum count to read</param>
        /// <param name="resolveLinkTos">Whether to resolve LinkTo events automatically</param>
        /// <param name="userCredentials">The optional user credentials to perform operation with.</param>
        /// <returns>A <see cref="AllEventsSlice"/> containing the records read</returns>
        AllEventsSlice ReadAllEventsBackward(position:Position , maxCount:Int, resolveLinkTos:Boolean, credentials: Option[UserCredentials] = None);

        /// <summary>
        /// Reads All Events in the node backwards (e.g. end to beginning)
        /// </summary>
        /// <param name="position">The position to start reading from</param>
        /// <param name="maxCount">The maximum count to read</param>
        /// <param name="resolveLinkTos">Whether to resolve LinkTo events automatically</param>
        /// <param name="userCredentials">The optional user credentials to perform operation with.</param>
        /// <returns>A <see cref="AllEventsSlice"/> containing the records read</returns>
        Task<AllEventsSlice> ReadAllEventsBackwardAsync(position:Position , maxCount:Int, resolveLinkTos:Boolean, credentials: Option[UserCredentials] = None);

        EventStoreSubscription SubscribeToStream(
                streamId:String,
                resolveLinkTos:Boolean,
                eventAppeared:(EventStoreSubscription, ResolvedEvent) => Any ,
                subscriptionDropped:Option[SubscriptionDroppedFunc],
                credentials: Option[UserCredentials] = None);

        Task<EventStoreSubscription> SubscribeToStreamAsync(
                streamId:String,
                resolveLinkTos:Boolean,
                eventAppeared:(EventStoreSubscription, ResolvedEvent) => Any ,
                subscriptionDropped:Option[SubscriptionDroppedFunc],
                credentials: Option[UserCredentials] = None);

        EventStoreStreamCatchUpSubscription SubscribeToStreamFrom(
                streamId:String,
                fromEventNumberExclusive:Int,
                resolveLinkTos:Boolean,
                eventAppeared:Option[EventAppeared],
                liveProcessingStarted:Option[LiveProcessingStarted],
                subscriptionDropped:Option[SubscriptionDroppedFFFFF],
                credentials: Option[UserCredentials] = None);

        EventStoreSubscription SubscribeToAll(
                resolveLinkTos:Boolean,
                eventAppeared:(EventStoreSubscription, ResolvedEvent) => Any ,
                subscriptionDropped:Option[SubscriptionDroppedFunc],
                credentials: Option[UserCredentials] = None);

        Task<EventStoreSubscription> SubscribeToAllAsync(
                resolveLinkTos:Boolean,
                eventAppeared:(EventStoreSubscription, ResolvedEvent) => Any ,
                subscriptionDropped:Option[SubscriptionDroppedFunc],
                credentials: Option[UserCredentials] = None);

        EventStoreAllCatchUpSubscription SubscribeToAllFrom(
                Position? fromPositionExclusive,
                resolveLinkTos:Boolean,
                eventAppeared:Option[EventAppeared],
                liveProcessingStarted:Option[LiveProcessingStarted],
                subscriptionDropped:Option[SubscriptionDroppedFFFFF],
                credentials: Option[UserCredentials] = None);

        void SetStreamMetadata(streamId:String, expectedMetastreamVersion:Int, metadata:StreamMetadata , credentials: Option[UserCredentials] = None);

        Task SetStreamMetadataAsync(streamId:String, expectedMetastreamVersion:Int, metadata:StreamMetadata , credentials: Option[UserCredentials] = None);

        void SetStreamMetadata(streamId:String, expectedMetastreamVersion:Int, byte[] metadata, credentials: Option[UserCredentials] = None);

        Task SetStreamMetadataAsync(streamId:String, expectedMetastreamVersion:Int, byte[] metadata, credentials: Option[UserCredentials] = None);

        StreamMetadataResult GetStreamMetadata(streamId:String, credentials: Option[UserCredentials] = None);

        Task<StreamMetadataResult> GetStreamMetadataAsync(streamId:String, credentials: Option[UserCredentials] = None);

        RawStreamMetadataResult GetStreamMetadataAsRawBytes(streamId:String, credentials: Option[UserCredentials] = None);

        Task<RawStreamMetadataResult> GetStreamMetadataAsRawBytesAsync(streamId:String, credentials: Option[UserCredentials] = None);
    }
}*/
