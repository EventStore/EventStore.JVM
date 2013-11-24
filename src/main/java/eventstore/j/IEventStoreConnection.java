package eventstore.j;

import eventstore.*;
import scala.Unit;
import scala.concurrent.Future;

import java.util.Collection;

/**
 * @author Yaroslav Klymko
 */
interface IEventStoreConnection {
//    Future<Unit> DeleteStreamAsync(String stream, int expectedVersion, UserCredentials userCredentials);
//
//    Future<Unit> AppendToStreamAsync(String stream, int expectedVersion, Collection<EventData> events, UserCredentials userCredentials);
//
//    Future<IEventStoreTransaction> startTransaction(String stream, int expectedVersion, UserCredentials userCredentials);
//
//    IEventStoreTransaction continueTransaction(long transactionId, UserCredentials userCredentials);
//
//    Future<ReadEventSucceed> readEvent(String stream, int eventNumber, boolean resolveLinkTos, UserCredentials userCredentials);
//
//    Future<ReadStreamEventsSucceed> readStreamEventsForward(String stream, int start, int count, boolean resolveLinkTos, UserCredentials userCredentials);
//
//    Future<ReadStreamEventsSucceed> readStreamEventsBackward(String stream, int start, int count, boolean resolveLinkTos, UserCredentials userCredentials);
//
//    Future<ReadAllEventsSucceed> readAllEventsForward(Position position, int maxCount, boolean resolveLinkTos, UserCredentials userCredentials);
//
//    Future<ReadAllEventsSucceed> readAllEventsBackward(Position position, int maxCount, boolean resolveLinkTos, UserCredentials userCredentials);
//
//    Future<EventStoreSubscription> subscribeToStream(
//            String stream,
//            boolean resolveLinkTos,
//            Action<EventStoreSubscription, ResolvedEvent> eventAppeared,
//            Action<EventStoreSubscription, SubscriptionDropReason, Exception> subscriptionDropped,
//            UserCredentials userCredentials);
//
//    EventStoreStreamCatchUpSubscription subscribeToStreamFrom(
//            String stream,
//            int?fromEventNumberExclusive,
//            boolean resolveLinkTos,
//            Action<EventStoreCatchUpSubscription, ResolvedEvent> eventAppeared,
//            Action<EventStoreCatchUpSubscription> liveProcessingStarted,
//            Action<EventStoreCatchUpSubscription, SubscriptionDropReason, Exception> subscriptionDropped,
//            UserCredentials userCredentials);
//
//    EventStoreSubscription subscribeToAll(
//            boolean resolveLinkTos,
//            Action<EventStoreSubscription, ResolvedEvent> eventAppeared,
//            Action<EventStoreSubscription, SubscriptionDropReason, Exception> subscriptionDropped,
//            UserCredentials userCredentials);
//
//    Future<EventStoreSubscription> subscribeToAllAsync(
//            boolean resolveLinkTos,
//            Action<EventStoreSubscription, ResolvedEvent> eventAppeared,
//            Action<EventStoreSubscription, SubscriptionDropReason, Exception> subscriptionDropped,
//            UserCredentials userCredentials);
//
//    EventStoreAllCatchUpSubscription subscribeToAllFrom(
//            Position?fromPositionExclusive,
//            boolean resolveLinkTos,
//            Action<EventStoreCatchUpSubscription, ResolvedEvent> eventAppeared,
//            Action<EventStoreCatchUpSubscription> liveProcessingStarted,
//            Action<EventStoreCatchUpSubscription, SubscriptionDropReason, Exception> subscriptionDropped,
//            UserCredentials userCredentials);
//
//    Future<Unit> setStreamMetadataAsync(String stream, int expectedMetastreamVersion, StreamMetadata metadata, UserCredentials userCredentials);
//
//    Future<Unit> setStreamMetadataAsync(String stream, int expectedMetastreamVersion, byte[] metadata, UserCredentials userCredentials);
//
//    Future<StreamMetadataResult> getStreamMetadataAsync(String stream, UserCredentials userCredentials);
//
//    Future<RawStreamMetadataResult> getStreamMetadataAsRawBytesAsync(String stream, UserCredentials userCredentials);
}
