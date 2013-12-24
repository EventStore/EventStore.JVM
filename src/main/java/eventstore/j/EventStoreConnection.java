package eventstore.j;

import eventstore.*;
import scala.Unit;
import scala.concurrent.Future;

import java.io.Closeable;
import java.util.Collection;


public interface EventStoreConnection {
    Future<Unit> writeEvents(
            String stream,
            ExpectedVersion expectedVersion, // TODO should support Any or null
            Collection<EventData> events,
            UserCredentials userCredentials);

    Future<Unit> deleteStream(
            String stream,
            ExpectedVersion expectedVersion,
            UserCredentials userCredentials);

    Future<EventStoreTransaction> startTransaction(
            String stream,
            ExpectedVersion expectedVersion,
            UserCredentials userCredentials);

    EventStoreTransaction continueTransaction(long transactionId, UserCredentials userCredentials);

    Future<Event> readEvent(
            String stream,
            EventNumber eventNumber,
            boolean resolveLinkTos,
            UserCredentials userCredentials);

    Future<ReadStreamEventsCompleted> readStreamEventsForward(
            String stream,
            EventNumber fromNumber,
            int count,
            boolean resolveLinkTos,
            UserCredentials userCredentials);

    Future<ReadStreamEventsCompleted> readStreamEventsBackward(
            String stream,
            EventNumber fromNumber,
            int count,
            boolean resolveLinkTos,
            UserCredentials userCredentials);

    Future<ReadAllEventsCompleted> readAllEventsForward(
            Position position,
            int maxCount,
            boolean resolveLinkTos,
            UserCredentials userCredentials);

    Future<ReadAllEventsCompleted> readAllEventsBackward(
            Position position,
            int maxCount,
            boolean resolveLinkTos,
            UserCredentials userCredentials);

    Closeable subscribeToStream(
            String stream,
            boolean resolveLinkTos,
            SubscriptionObserver<Event> observer,
            UserCredentials userCredentials);

    Closeable subscribeToStreamFrom(
            String stream,
            int fromEventNumberExclusive,
            boolean resolveLinkTos,
            CatchUpSubscriptionObserver<Event> observer,
            UserCredentials userCredentials);

    Closeable subscribeToAll(
            boolean resolveLinkTos,
            SubscriptionObserver<IndexedEvent> observer,
            UserCredentials userCredentials);

    Closeable subscribeToAllFrom(
            boolean resolveLinkTos,
            Position fromPositionExclusive,
            CatchUpSubscriptionObserver<IndexedEvent> observer,
            UserCredentials userCredentials);

//    Future<Unit> setStreamMetadataAsync(String stream, int expectedMetastreamVersion, StreamMetadata metadata, UserCredentials userCredentials);
//
//    Future<Unit> setStreamMetadataAsync(String stream, int expectedMetastreamVersion, byte[] metadata, UserCredentials userCredentials);
//
//    Future<StreamMetadataResult> getStreamMetadataAsync(String stream, UserCredentials userCredentials);
//
//    Future<RawStreamMetadataResult> getStreamMetadataAsRawBytesAsync(String stream, UserCredentials userCredentials);
}
