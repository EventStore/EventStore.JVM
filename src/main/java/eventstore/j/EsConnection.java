package eventstore.j;

import eventstore.*;
import org.reactivestreams.Publisher;
import scala.concurrent.Future;

import java.io.Closeable;
import java.util.Collection;

/**
 * Maintains a full duplex connection to the EventStore
 * <p>
 * All operations are handled in a full async manner.
 * Many threads can use an <code>EsConnection</code> at the same time or a single thread can make many asynchronous requests.
 * To get the most performance out of the connection it is generally recommended to use it in this way.
 */
public interface EsConnection {

  /**
   * Write events to a stream
   * <p>
   * When writing events to a stream the {@link eventstore.ExpectedVersion} choice can
   * make a very large difference in the observed behavior. For example, if no stream exists
   * and ExpectedVersion.Any is used, a new stream will be implicitly created when appending.
   * <p>
   * There are also differences in idempotency between different types of calls.
   * If you specify an ExpectedVersion aside from ExpectedVersion.Any the Event Store
   * will give you an idempotency guarantee. If using ExpectedVersion.Any the Event Store
   * will do its best to provide idempotency but does not guarantee idempotency
   *
   * @param stream          name of the stream to write events to
   * @param expectedVersion expected version of the stream to write to, or <code>ExpectedVersion.Any</code> if <code>null</code>
   * @param events          events to append to the stream
   * @param credentials     optional user credentials to perform operation with.
   * @return A {@link scala.concurrent.Future} that the caller can await on
   */
  Future<WriteResult> writeEvents(
      String stream,
      ExpectedVersion expectedVersion,
      Collection<EventData> events,
      UserCredentials credentials);

  /**
   * Write events to a stream
   * <p>
   * When writing events to a stream the {@link eventstore.ExpectedVersion} choice can
   * make a very large difference in the observed behavior. For example, if no stream exists
   * and ExpectedVersion.Any is used, a new stream will be implicitly created when appending.
   * <p>
   * There are also differences in idempotency between different types of calls.
   * If you specify an ExpectedVersion aside from ExpectedVersion.Any the Event Store
   * will give you an idempotency guarantee. If using ExpectedVersion.Any the Event Store
   * will do its best to provide idempotency but does not guarantee idempotency
   *
   * @param stream          name of the stream to write events to
   * @param expectedVersion expected version of the stream to write to, or <code>ExpectedVersion.Any</code> if <code>null</code>
   * @param events          events to append to the stream
   * @param credentials     optional user credentials to perform operation with.
   * @param requireMaster   Require Event Store to refuse operation if it is not master
   * @return A {@link scala.concurrent.Future} that the caller can await on
   */
  Future<WriteResult> writeEvents(
      String stream,
      ExpectedVersion expectedVersion,
      Collection<EventData> events,
      UserCredentials credentials,
      boolean requireMaster);

  /**
   * Deletes a stream from the Event Store
   *
   * @param stream          name of the stream to delete
   * @param expectedVersion optional expected version that the stream should have when being deleted, or <code>ExpectedVersion.Any</code> if <code>null</code>
   * @param credentials     optional user credentials to perform operation with.
   * @return A {@link scala.concurrent.Future} that the caller can await on
   */
  Future<DeleteResult> deleteStream(
      String stream,
      ExpectedVersion.Existing expectedVersion,
      UserCredentials credentials);

  /**
   * Deletes a stream from the Event Store
   *
   * @param stream          name of the stream to delete
   * @param expectedVersion optional expected version that the stream should have when being deleted, or <code>ExpectedVersion.Any</code> if <code>null</code>
   * @param hardDelete      Indicator for tombstoning vs soft-deleting the stream. Tombstoned streams can never be recreated. Soft-deleted streams can be written to again, but the EventNumber sequence will not start from 0.
   * @param credentials     optional user credentials to perform operation with.
   * @return A {@link scala.concurrent.Future} that the caller can await on
   */
  Future<DeleteResult> deleteStream(
      String stream,
      ExpectedVersion.Existing expectedVersion,
      boolean hardDelete,
      UserCredentials credentials);

  /**
   * Deletes a stream from the Event Store
   *
   * @param stream          name of the stream to delete
   * @param expectedVersion optional expected version that the stream should have when being deleted, or <code>ExpectedVersion.Any</code> if <code>null</code>
   * @param hardDelete      Indicator for tombstoning vs soft-deleting the stream. Tombstoned streams can never be recreated. Soft-deleted streams can be written to again, but the EventNumber sequence will not start from 0.
   * @param credentials     optional user credentials to perform operation with.
   * @param requireMaster   Require Event Store to refuse operation if it is not master
   * @return A {@link scala.concurrent.Future} that the caller can await on
   */
  Future<DeleteResult> deleteStream(
      String stream,
      ExpectedVersion.Existing expectedVersion,
      boolean hardDelete,
      UserCredentials credentials,
      boolean requireMaster);

  /**
   * Starts a transaction in the event store on a given stream asynchronously
   * <p>
   * A {@link eventstore.j.EsTransaction} allows the calling of multiple writes with multiple
   * round trips over long periods of time between the caller and the event store. This method
   * is only available through the TCP interface and no equivalent exists for the RESTful interface.
   *
   * @param stream          The stream to start a transaction on
   * @param expectedVersion The expected version of the stream at the time of starting the transaction
   * @param credentials     The optional user credentials to perform operation with
   * @return A {@link scala.concurrent.Future} containing an actual transaction
   */
  Future<EsTransaction> startTransaction(
      String stream,
      ExpectedVersion expectedVersion,
      UserCredentials credentials);

  /**
   * Starts a transaction in the event store on a given stream asynchronously
   * <p>
   * A {@link eventstore.j.EsTransaction} allows the calling of multiple writes with multiple
   * round trips over long periods of time between the caller and the event store. This method
   * is only available through the TCP interface and no equivalent exists for the RESTful interface.
   *
   * @param stream          The stream to start a transaction on
   * @param expectedVersion The expected version of the stream at the time of starting the transaction
   * @param credentials     The optional user credentials to perform operation with
   * @param requireMaster   Require Event Store to refuse operation if it is not master
   * @return A {@link scala.concurrent.Future} containing an actual transaction
   */
  Future<EsTransaction> startTransaction(
      String stream,
      ExpectedVersion expectedVersion,
      UserCredentials credentials,
      boolean requireMaster);


  /**
   * Continues transaction by provided transaction ID.
   * <p>
   * A {@link eventstore.j.EsTransaction} allows the calling of multiple writes with multiple
   * round trips over long periods of time between the caller and the event store. This method
   * is only available through the TCP interface and no equivalent exists for the RESTful interface.
   *
   * @param transactionId The transaction ID that needs to be continued.
   * @param credentials   The optional user credentials to perform operation with
   * @return A transaction for given id
   */
  EsTransaction continueTransaction(long transactionId, UserCredentials credentials);


  /**
   * Reads a single event from a stream at event number
   *
   * @param stream         name of the stream to read from
   * @param eventNumber    optional event number to read, or EventNumber.Last for reading latest event, EventNumber.Last if null
   * @param resolveLinkTos whether to resolve LinkTo events automatically
   * @param credentials    The optional user credentials to perform operation with
   * @return A {@link scala.concurrent.Future} containing an event
   */
  Future<Event> readEvent(
      String stream,
      EventNumber eventNumber,
      boolean resolveLinkTos,
      UserCredentials credentials);

  /**
   * Reads a single event from a stream at event number
   *
   * @param stream         name of the stream to read from
   * @param eventNumber    optional event number to read, or EventNumber.Last for reading latest event, EventNumber.Last if null
   * @param resolveLinkTos whether to resolve LinkTo events automatically
   * @param credentials    The optional user credentials to perform operation with
   * @param requireMaster  Require Event Store to refuse operation if it is not master
   * @return A {@link scala.concurrent.Future} containing an event
   */
  Future<Event> readEvent(
      String stream,
      EventNumber eventNumber,
      boolean resolveLinkTos,
      UserCredentials credentials,
      boolean requireMaster);


  /**
   * Reads count events from a stream forwards (e.g. oldest to newest) starting from event number
   *
   * @param stream         name of stream to read from
   * @param fromNumber     optional event number to read, EventNumber.First if null
   * @param maxCount       maximum count of items to read
   * @param resolveLinkTos whether to resolve LinkTo events automatically
   * @param credentials    The optional user credentials to perform operation with
   * @return A {@link scala.concurrent.Future} containing the results of the read operation
   */
  Future<ReadStreamEventsCompleted> readStreamEventsForward(
      String stream,
      EventNumber.Exact fromNumber,
      int maxCount,
      boolean resolveLinkTos,
      UserCredentials credentials);

  /**
   * Reads count events from a stream forwards (e.g. oldest to newest) starting from event number
   *
   * @param stream         name of stream to read from
   * @param fromNumber     optional event number to read, EventNumber.First if null
   * @param maxCount       maximum count of items to read
   * @param resolveLinkTos whether to resolve LinkTo events automatically
   * @param credentials    The optional user credentials to perform operation with
   * @param requireMaster  Require Event Store to refuse operation if it is not master
   * @return A {@link scala.concurrent.Future} containing the results of the read operation
   */
  Future<ReadStreamEventsCompleted> readStreamEventsForward(
      String stream,
      EventNumber.Exact fromNumber,
      int maxCount,
      boolean resolveLinkTos,
      UserCredentials credentials,
      boolean requireMaster);


  /**
   * Reads count events from from a stream backwards (e.g. newest to oldest) starting from event number
   *
   * @param stream         name of stream to read from
   * @param fromNumber     optional event number to read, EventNumber.Last if null
   * @param maxCount       maximum count of items to read
   * @param resolveLinkTos whether to resolve LinkTo events automatically
   * @param credentials    The optional user credentials to perform operation with
   * @return A {@link scala.concurrent.Future} containing the results of the read operation
   */
  Future<ReadStreamEventsCompleted> readStreamEventsBackward(
      String stream,
      EventNumber fromNumber,
      int maxCount,
      boolean resolveLinkTos,
      UserCredentials credentials);

  /**
   * Reads count events from from a stream backwards (e.g. newest to oldest) starting from event number
   *
   * @param stream         name of stream to read from
   * @param fromNumber     optional event number to read, EventNumber.Last if null
   * @param maxCount       maximum count of items to read
   * @param resolveLinkTos whether to resolve LinkTo events automatically
   * @param credentials    The optional user credentials to perform operation with
   * @param requireMaster  Require Event Store to refuse operation if it is not master
   * @return A {@link scala.concurrent.Future} containing the results of the read operation
   */
  Future<ReadStreamEventsCompleted> readStreamEventsBackward(
      String stream,
      EventNumber fromNumber,
      int maxCount,
      boolean resolveLinkTos,
      UserCredentials credentials,
      boolean requireMaster);


  /**
   * Reads all events in the node forward (e.g. beginning to end) starting from position
   *
   * @param fromPosition   optional position to start reading from, Position.First of null
   * @param maxCount       maximum count of items to read
   * @param resolveLinkTos whether to resolve LinkTo events automatically
   * @param credentials    The optional user credentials to perform operation with
   * @return A {@link scala.concurrent.Future} containing the results of the read operation
   */
  Future<ReadAllEventsCompleted> readAllEventsForward(
      Position fromPosition,
      int maxCount,
      boolean resolveLinkTos,
      UserCredentials credentials);

  /**
   * Reads all events in the node forward (e.g. beginning to end) starting from position
   *
   * @param fromPosition   optional position to start reading from, Position.First of null
   * @param maxCount       maximum count of items to read
   * @param resolveLinkTos whether to resolve LinkTo events automatically
   * @param credentials    The optional user credentials to perform operation with
   * @param requireMaster  Require Event Store to refuse operation if it is not master
   * @return A {@link scala.concurrent.Future} containing the results of the read operation
   */
  Future<ReadAllEventsCompleted> readAllEventsForward(
      Position fromPosition,
      int maxCount,
      boolean resolveLinkTos,
      UserCredentials credentials,
      boolean requireMaster);


  /**
   * Reads all events in the node backwards (e.g. end to beginning) starting from position
   *
   * @param fromPosition   optional position to start reading from, Position.Last of null
   * @param maxCount       maximum count of items to read
   * @param resolveLinkTos whether to resolve LinkTo events automatically
   * @param credentials    The optional user credentials to perform operation with
   * @return A {@link scala.concurrent.Future} containing the results of the read operation
   */
  Future<ReadAllEventsCompleted> readAllEventsBackward(
      Position fromPosition,
      int maxCount,
      boolean resolveLinkTos,
      UserCredentials credentials);

  /**
   * Reads all events in the node backwards (e.g. end to beginning) starting from position
   *
   * @param fromPosition   optional position to start reading from, Position.Last of null
   * @param maxCount       maximum count of items to read
   * @param resolveLinkTos whether to resolve LinkTo events automatically
   * @param credentials    The optional user credentials to perform operation with
   * @param requireMaster  Require Event Store to refuse operation if it is not master
   * @return A {@link scala.concurrent.Future} containing the results of the read operation
   */
  Future<ReadAllEventsCompleted> readAllEventsBackward(
      Position fromPosition,
      int maxCount,
      boolean resolveLinkTos,
      UserCredentials credentials,
      boolean requireMaster);


  /**
   * Subscribes to a single event stream. New events
   * written to the stream while the subscription is active will be
   * pushed to the client.
   *
   * @param stream         The stream to subscribe to
   * @param observer       A {@link eventstore.SubscriptionObserver} to handle a new event received over the subscription
   * @param resolveLinkTos Whether to resolve LinkTo events automatically
   * @param credentials    The optional user credentials to perform operation with
   * @return A {@link java.io.Closeable} representing the subscription which can be closed.
   */
  Closeable subscribeToStream(
      String stream,
      SubscriptionObserver<Event> observer,
      boolean resolveLinkTos,
      UserCredentials credentials);


  /**
   * Subscribes to a single event stream. Existing events from
   * lastCheckpoint onwards are read from the stream
   * and presented to the user of <code>SubscriptionObserver</code>
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
   * @param stream                   The stream to subscribe to
   * @param observer                 A {@link eventstore.SubscriptionObserver} to handle a new event received over the subscription
   * @param fromEventNumberExclusive The event number from which to start, or <code>null</code> to read all events.
   * @param resolveLinkTos           Whether to resolve LinkTo events automatically
   * @param credentials              The optional user credentials to perform operation with
   * @return A {@link java.io.Closeable} representing the subscription which can be closed.
   */
  Closeable subscribeToStreamFrom(
      String stream,
      SubscriptionObserver<Event> observer,
      Integer fromEventNumberExclusive,
      boolean resolveLinkTos,
      UserCredentials credentials);

  /**
   * Subscribes to all events in the Event Store. New events written to the stream
   * while the subscription is active will be pushed to the client.
   *
   * @param observer       A {@link eventstore.SubscriptionObserver} to handle a new event received over the subscription
   * @param resolveLinkTos Whether to resolve LinkTo events automatically
   * @param credentials    The optional user credentials to perform operation with
   * @return A {@link java.io.Closeable} representing the subscription which can be closed.
   */
  Closeable subscribeToAll(
      SubscriptionObserver<IndexedEvent> observer,
      boolean resolveLinkTos,
      UserCredentials credentials);


  /**
   * Subscribes to a all events. Existing events from position
   * onwards are read from the Event Store and presented to the user of
   * <code>SubscriptionObserver</code> as if they had been pushed.
   * <p>
   * Once the end of the stream is read the subscription is
   * transparently (to the user) switched to push new events as
   * they are written.
   * <p>
   * If events have already been received and resubscription from the same point
   * is desired, use the position representing the last event processed which
   * appeared on the subscription.
   *
   * @param observer              A {@link eventstore.SubscriptionObserver} to handle a new event received over the subscription
   * @param fromPositionExclusive The position from which to start, or <code>null</code> to read all events
   * @param resolveLinkTos        Whether to resolve LinkTo events automatically
   * @param credentials           The optional user credentials to perform operation with
   * @return A {@link java.io.Closeable} representing the subscription which can be closed.
   */
  Closeable subscribeToAllFrom(
      SubscriptionObserver<IndexedEvent> observer,
      Position.Exact fromPositionExclusive,
      boolean resolveLinkTos,
      UserCredentials credentials);


// TODO support stream not found
//    Future<Unit> setStreamMetadata(String stream, int expectedMetastreamVersion, StreamMetadata metadata, UserCredentials credentials);
//    Future<StreamMetadataResult> getStreamMetadataAsync(String stream, UserCredentials credentials);
//    Future<RawStreamMetadataResult> getStreamMetadataAsRawBytesAsync(String stream, UserCredentials credentials); TODO

  /**
   * Sets the metadata for a stream.
   *
   * @param stream                    The name of the stream for which to set metadata.
   * @param expectedMetastreamVersion The expected version for the write to the metadata stream.
   * @param metadata                  A byte array representing the new metadata.
   * @param credentials               The optional user credentials to perform operation with
   * @return A {@link scala.concurrent.Future} representing the operation
   */
  Future<WriteResult> setStreamMetadata(
      String stream,
      ExpectedVersion expectedMetastreamVersion,
      byte[] metadata,
      UserCredentials credentials);

  /**
   * Reads the metadata for a stream as a byte array.
   *
   * @param stream      The name of the stream for which to read metadata.
   * @param credentials The optional user credentials to perform operation with
   * @return A {@link scala.concurrent.Future} containing the metadata as byte array.
   */
  Future<byte[]> getStreamMetadataBytes(String stream, UserCredentials credentials);

  /**
   * Creates Publisher you can use to subscribe to a single event stream. Existing events from
   * lastCheckpoint onwards are read from the stream
   * and presented to the user of <code>Publisher</code>
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
   * @param stream                   The stream to publish
   * @param fromEventNumberExclusive The event number from which to start, or <code>null</code> to read all events.
   * @param resolveLinkTos           Whether to resolve LinkTo events automatically
   * @param credentials              The optional user credentials to perform operation with
   * @param infinite                 Whether to subscribe to the future events upon reading all current
   * @return A {@link org.reactivestreams.Publisher} representing the stream
   */
  Publisher<Event> streamPublisher(
      String stream,
      EventNumber fromEventNumberExclusive,
      boolean resolveLinkTos,
      UserCredentials credentials,
      boolean infinite);


  /**
   * Creates Publisher you can use to subscribes to a all events. Existing events from position
   * onwards are read from the Event Store and presented to the user of
   * <code>Publisher</code> as if they had been pushed.
   * <p>
   * Once the end of the stream is read the subscription is
   * transparently (to the user) switched to push new events as
   * they are written.
   * <p>
   * If events have already been received and resubscription from the same point
   * is desired, use the position representing the last event processed which
   * appeared on the subscription.
   *
   * @param fromPositionExclusive The position from which to start, or <code>null</code> to read all events
   * @param resolveLinkTos        Whether to resolve LinkTo events automatically
   * @param credentials           The optional user credentials to perform operation with
   * @param infinite              Whether to subscribe to the future events upon reading all current
   * @return A {@link org.reactivestreams.Publisher} representing all streams
   */
  Publisher<IndexedEvent> allStreamsPublisher(
      Position fromPositionExclusive,
      boolean resolveLinkTos,
      UserCredentials credentials,
      boolean infinite);

  /**
   * Asynchronously create a persistent subscription group on a stream
   *
   * @param stream      The name of the stream to create the persistent subscription on
   * @param groupName   The name of the group to create
   * @param settings    The {@link PersistentSubscriptionSettings} for the subscription, or <code>null</code> for defaults
   * @param credentials The credentials to be used for this operation
   * @return A {@link scala.concurrent.Future} representing the operation
   */
  Future<scala.Unit> createPersistentSubscription(
      String stream,
      String groupName,
      PersistentSubscriptionSettings settings,
      UserCredentials credentials);

  /**
   * Asynchronously update a persistent subscription group on a stream
   *
   * @param stream      The name of the stream to create the persistent subscription on
   * @param groupName   The name of the group to create
   * @param settings    The {@link PersistentSubscriptionSettings} for the subscription, or <code>null</code> for defaults
   * @param credentials The credentials to be used for this operation, or <code>null</code> for default
   * @return A {@link scala.concurrent.Future} representing the operation
   */
  Future<scala.Unit> updatePersistentSubscription(
      String stream,
      String groupName,
      PersistentSubscriptionSettings settings,
      UserCredentials credentials);

  /**
   * Asynchronously delete a persistent subscription group on a stream
   *
   * @param stream      The name of the stream to create the persistent subscription on
   * @param groupName   The name of the group to create
   * @param credentials The credentials to be used for this operation, or <code>null</code> for default
   * @return A {@link scala.concurrent.Future} representing the operation
   */
  Future<scala.Unit> deletePersistentSubscription(
      String stream,
      String groupName,
      UserCredentials credentials);
}
