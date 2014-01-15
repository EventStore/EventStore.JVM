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
     * @param userCredentials optional user credentials to perform operation with.
     * @return {@link scala.concurrent.Future} that the caller can await on
     */
    Future<Unit> writeEvents(
            String stream,
            ExpectedVersion expectedVersion,
            Collection<EventData> events,
            UserCredentials userCredentials);


    /**
     * Deletes a stream from the Event Store asynchronously
     *
     * @param stream          name of the stream to delete
     * @param expectedVersion optional expected version that the stream should have when being deleted, or <code>ExpectedVersion.Any</code> if <code>null</code>
     * @param userCredentials optional user credentials to perform operation with.
     * @return {@link scala.concurrent.Future} that the caller can await on
     */
    Future<Unit> deleteStream(
            String stream,
            ExpectedVersion.Existing expectedVersion,
            UserCredentials userCredentials);

//    Future<EventStoreTransaction> startTransaction(
//            String stream,
//            ExpectedVersion expectedVersion,
//            UserCredentials userCredentials);
//
//    EventStoreTransaction continueTransaction(long transactionId, UserCredentials userCredentials);


    /**
     * Asynchronously reads a single event from a stream at event number asynchronously
     *
     * @param stream          name of the stream to read from
     * @param eventNumber     optional event number to read, or EventNumber.Last for reading latest event, EventNumber.Last if null
     * @param resolveLinkTos  whether to resolve LinkTo events automatically
     * @param userCredentials The optional user credentials to perform operation with
     * @return {@link scala.concurrent.Future} containing an event
     */
    Future<Event> readEvent(
            String stream,
            EventNumber eventNumber,
            boolean resolveLinkTos,
            UserCredentials userCredentials);


    /**
     * Reads count events from a stream forwards asynchronously (e.g. oldest to newest) starting from event number
     *
     * @param stream          name of stream to read from
     * @param fromNumber      optional event number to read, EventNumber.First if null
     * @param maxCount        maximum count of items to read
     * @param resolveLinkTos  whether to resolve LinkTo events automatically
     * @param userCredentials The optional user credentials to perform operation with
     * @return {@link scala.concurrent.Future} containing the results of the read operation
     */
    Future<ReadStreamEventsCompleted> readStreamEventsForward(
            String stream,
            EventNumber.Exact fromNumber,
            int maxCount,
            boolean resolveLinkTos,
            UserCredentials userCredentials);


    /**
     * Reads count events from from a stream backwards asynchronously (e.g. newest to oldest) starting from event number
     *
     * @param stream          name of stream to read from
     * @param fromNumber      optional event number to read, EventNumber.Last if null
     * @param maxCount        maximum count of items to read
     * @param resolveLinkTos  whether to resolve LinkTo events automatically
     * @param userCredentials The optional user credentials to perform operation with
     * @return {@link scala.concurrent.Future} containing the results of the read operation
     */
    Future<ReadStreamEventsCompleted> readStreamEventsBackward(
            String stream,
            EventNumber fromNumber,
            int maxCount,
            boolean resolveLinkTos,
            UserCredentials userCredentials);


    /**
     * Reads all events in the node forward asynchronously (e.g. beginning to end) starting from position
     *
     * @param fromPosition    optional position to start reading from, Position.First of null
     * @param maxCount        maximum count of items to read
     * @param resolveLinkTos  whether to resolve LinkTo events automatically
     * @param userCredentials The optional user credentials to perform operation with
     * @return {@link scala.concurrent.Future} containing the results of the read operation
     */
    Future<ReadAllEventsCompleted> readAllEventsForward(
            Position fromPosition,
            int maxCount,
            boolean resolveLinkTos,
            UserCredentials userCredentials);


    /**
     * Reads all events in the node backwards asynchronously (e.g. end to beginning) starting from position
     *
     * @param fromPosition    optional position to start reading from, Position.Last of null
     * @param maxCount        maximum count of items to read
     * @param resolveLinkTos  whether to resolve LinkTo events automatically
     * @param userCredentials The optional user credentials to perform operation with
     * @return {@link scala.concurrent.Future} containing the results of the read operation
     */
    Future<ReadAllEventsCompleted> readAllEventsBackward(
            Position fromPosition,
            int maxCount,
            boolean resolveLinkTos,
            UserCredentials userCredentials);


    Closeable subscribeToStream(
            String stream,
            SubscriptionObserver<Event> observer,
            boolean resolveLinkTos/*,
            UserCredentials userCredentials TODO*/);
//

    Closeable subscribeToStreamFrom(
            String stream,
            int fromEventNumberExclusive,
            SubscriptionObserver<Event> observer,
            boolean resolveLinkTos/*,
            UserCredentials userCredentials TODO*/);
//
//    Closeable subscribeToAll(
//            boolean resolveLinkTos,
//            SubscriptionObserver<IndexedEvent> observer,
//            UserCredentials userCredentials);
//
//    Closeable subscribeToAllFrom(
//            boolean resolveLinkTos,
//            Position fromPositionExclusive,
//            SubscriptionObserver<IndexedEvent> observer,
//            UserCredentials userCredentials);

//    Future<Unit> setStreamMetadataAsync(String stream, int expectedMetastreamVersion, StreamMetadata metadata, UserCredentials userCredentials);
//
//    Future<Unit> setStreamMetadataAsync(String stream, int expectedMetastreamVersion, byte[] metadata, UserCredentials userCredentials);
//
//    Future<StreamMetadataResult> getStreamMetadataAsync(String stream, UserCredentials userCredentials);
//
//    Future<RawStreamMetadataResult> getStreamMetadataAsRawBytesAsync(String stream, UserCredentials userCredentials);
}
