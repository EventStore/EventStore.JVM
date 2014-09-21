package eventstore.j;

import eventstore.*;
import scala.Unit;
import scala.concurrent.Future;

import java.io.Closeable;
import java.util.Collection;


public interface EsConnection {

  /**
   * Write events asynchronously to a stream
   * <p/>
   * When writing events to a stream the {@link eventstore.ExpectedVersion} choice can
   * make a very large difference in the observed behavior. For example, if no stream exists
   * and ExpectedVersion.Any is used, a new stream will be implicitly created when appending.
   * <p/>
   * There are also differences in idempotency between different types of calls.
   * If you specify an ExpectedVersion aside from ExpectedVersion.Any the Event Store
   * will give you an idempotency guarantee. If using ExpectedVersion.Any the Event Store
   * will do its best to provide idempotency but does not guarantee idempotency
   *
   * @param stream          name of the stream to write events to
   * @param expectedVersion expected version of the stream to write to, or <code>ExpectedVersion.Any</code> if <code>null</code>
   * @param events          events to append to the stream
   * @param credentials     optional user credentials to perform operation with.
   * @return {@link scala.concurrent.Future} that the caller can await on
   */
  Future<Unit> writeEvents(
      String stream,
      ExpectedVersion expectedVersion,
      Collection<EventData> events,
      UserCredentials credentials);


  /**
   * Deletes a stream from the Event Store asynchronously
   *
   * @param stream          name of the stream to delete
   * @param expectedVersion optional expected version that the stream should have when being deleted, or <code>ExpectedVersion.Any</code> if <code>null</code>
   * @param credentials     optional user credentials to perform operation with.
   * @return {@link scala.concurrent.Future} that the caller can await on
   */
  Future<Unit> deleteStream(
      String stream,
      ExpectedVersion.Existing expectedVersion,
      UserCredentials credentials);

  /**
   * Deletes a stream from the Event Store asynchronously
   *
   * @param stream          name of the stream to delete
   * @param expectedVersion optional expected version that the stream should have when being deleted, or <code>ExpectedVersion.Any</code> if <code>null</code>
   * @param hardDelete      Indicator for tombstoning vs soft-deleting the stream. Tombstoned streams can never be recreated. Soft-deleted streams can be written to again, but the EventNumber sequence will not start from 0.
   * @param credentials     optional user credentials to perform operation with.
   * @return {@link scala.concurrent.Future} that the caller can await on
   */
  Future<Unit> deleteStream(
      String stream,
      ExpectedVersion.Existing expectedVersion,
      boolean hardDelete,
      UserCredentials credentials);

//    Future<EventStoreTransaction> startTransaction(
//            String stream,
//            ExpectedVersion expectedVersion,
//            UserCredentials credentials);
//
//    EventStoreTransaction continueTransaction(long transactionId, UserCredentials credentials);


  /**
   * Asynchronously reads a single event from a stream at event number asynchronously
   *
   * @param stream         name of the stream to read from
   * @param eventNumber    optional event number to read, or EventNumber.Last for reading latest event, EventNumber.Last if null
   * @param resolveLinkTos whether to resolve LinkTo events automatically
   * @param credentials    The optional user credentials to perform operation with
   * @return {@link scala.concurrent.Future} containing an event
   */
  Future<Event> readEvent(
      String stream,
      EventNumber eventNumber,
      boolean resolveLinkTos,
      UserCredentials credentials);


  /**
   * Reads count events from a stream forwards asynchronously (e.g. oldest to newest) starting from event number
   *
   * @param stream         name of stream to read from
   * @param fromNumber     optional event number to read, EventNumber.First if null
   * @param maxCount       maximum count of items to read
   * @param resolveLinkTos whether to resolve LinkTo events automatically
   * @param credentials    The optional user credentials to perform operation with
   * @return {@link scala.concurrent.Future} containing the results of the read operation
   */
  Future<ReadStreamEventsCompleted> readStreamEventsForward(
      String stream,
      EventNumber.Exact fromNumber,
      int maxCount,
      boolean resolveLinkTos,
      UserCredentials credentials);


  /**
   * Reads count events from from a stream backwards asynchronously (e.g. newest to oldest) starting from event number
   *
   * @param stream         name of stream to read from
   * @param fromNumber     optional event number to read, EventNumber.Last if null
   * @param maxCount       maximum count of items to read
   * @param resolveLinkTos whether to resolve LinkTo events automatically
   * @param credentials    The optional user credentials to perform operation with
   * @return {@link scala.concurrent.Future} containing the results of the read operation
   */
  Future<ReadStreamEventsCompleted> readStreamEventsBackward(
      String stream,
      EventNumber fromNumber,
      int maxCount,
      boolean resolveLinkTos,
      UserCredentials credentials);


  /**
   * Reads all events in the node forward asynchronously (e.g. beginning to end) starting from position
   *
   * @param fromPosition   optional position to start reading from, Position.First of null
   * @param maxCount       maximum count of items to read
   * @param resolveLinkTos whether to resolve LinkTo events automatically
   * @param credentials    The optional user credentials to perform operation with
   * @return {@link scala.concurrent.Future} containing the results of the read operation
   */
  Future<ReadAllEventsCompleted> readAllEventsForward(
      Position fromPosition,
      int maxCount,
      boolean resolveLinkTos,
      UserCredentials credentials);


  /**
   * Reads all events in the node backwards asynchronously (e.g. end to beginning) starting from position
   *
   * @param fromPosition   optional position to start reading from, Position.Last of null
   * @param maxCount       maximum count of items to read
   * @param resolveLinkTos whether to resolve LinkTo events automatically
   * @param credentials    The optional user credentials to perform operation with
   * @return {@link scala.concurrent.Future} containing the results of the read operation
   */
  Future<ReadAllEventsCompleted> readAllEventsBackward(
      Position fromPosition,
      int maxCount,
      boolean resolveLinkTos,
      UserCredentials credentials);


  Closeable subscribeToStream(
      String stream,
      SubscriptionObserver<Event> observer,
      boolean resolveLinkTos,
      UserCredentials credentials);


  Closeable subscribeToStreamFrom(
      String stream,
      SubscriptionObserver<Event> observer,
      int fromEventNumberExclusive,
      boolean resolveLinkTos,
      UserCredentials credentials);


  Closeable subscribeToAll(
      SubscriptionObserver<IndexedEvent> observer,
      boolean resolveLinkTos,
      UserCredentials credentials);


  Closeable subscribeToAllFrom(
      SubscriptionObserver<IndexedEvent> observer,
      Position.Exact fromPositionExclusive,
      boolean resolveLinkTos,
      UserCredentials credentials);

  // TODO support stream not found
//    Future<Unit> setStreamMetadata(String stream, int expectedMetastreamVersion, StreamMetadata metadata, UserCredentials credentials);


//    Future<StreamMetadataResult> getStreamMetadataAsync(String stream, UserCredentials credentials);
//

  //    Future<RawStreamMetadataResult> getStreamMetadataAsRawBytesAsync(String stream, UserCredentials credentials); TODO

  /**
   * Sets the metadata for a stream.
   *
   * @param stream                    The name of the stream for which to set metadata.
   * @param expectedMetastreamVersion The expected version for the write to the metadata stream.
   * @param metadata                  A byte array representing the new metadata.
   * @param credentials               The optional user credentials to perform operation with
   * @return {@link scala.concurrent.Future} representing the operation
   */
  Future<Unit> setStreamMetadata(
      String stream,
      ExpectedVersion expectedMetastreamVersion,
      byte[] metadata,
      UserCredentials credentials);

  /**
   * Reads the metadata for a stream as a byte array.
   *
   * @param stream      The name of the stream for which to read metadata.
   * @param credentials The optional user credentials to perform operation with
   * @return {@link scala.concurrent.Future} containing the metadata as byte array.
   */
  Future<byte[]> getStreamMetadataBytes(String stream, UserCredentials credentials);
}
