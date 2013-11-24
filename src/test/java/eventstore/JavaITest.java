package eventstore;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.Status.Failure;
import akka.testkit.JavaTestKit;
import eventstore.j.*;
import eventstore.tcp.ConnectionActor;
import org.junit.*;

import java.util.UUID;

import static org.junit.Assert.*;

/**
 * @author Yaroslav Klymko
 */
public class JavaITest {

    static ActorSystem system;
    static ActorRef connection;


    @BeforeClass
    public static void setup() {
        system = ActorSystem.create();
        connection = system.actorOf(Props.create(ConnectionActor.class));
    }

    @AfterClass
    public static void shutdown() {
        JavaTestKit.shutdownActorSystem(system);
        system = null;
    }


    static UUID newUuid() {
        return UUID.randomUUID();
    }


    // TODO what if 2 events with same ID ?
    final String eventType = "java-test";

    @Test
    public void testWriteEvents() {

        new JavaTestKit(system) {{

            final String streamId = "java-write-events" + newUuid();

            final EventData eventData = new EventDataBuilder(eventType)
                    .eventId(newUuid())
                    .data("{\"data\":\"data\"}")
                    .metadata("{\"metadata\":\"metadata\"}")
                    .build();

            final WriteEvents writeEvents = new WriteEventsBuilder(streamId)
                    .expectNoStream()
                    .event(eventData)
                    .requireMaster(true)
                    .build();

            connection.tell(writeEvents, getRef());
            final WriteEventsCompleted completed = expectMsgClass(WriteEventsCompleted.class);

            assertEquals(completed.firstEventNumber(), EventNumber$.MODULE$.First() /*TODO*/);
        }};
    }

    // TODO what's about using enums from java

    @Test
    public void testTransactionWrite() {
        new JavaTestKit(system) {{

            final String streamId = "java-transaction-write" + newUuid();

            final TransactionStart transactionStart = new TransactionStartBuilder(streamId)
                    .expectNoStream()
                    .requireMaster(true)
                    .build();

            connection.tell(transactionStart, getRef());
            final long transactionId = expectMsgClass(TransactionStartCompleted.class).transactionId();

            final EventData eventData = new EventDataBuilder(eventType)
                    .eventId(newUuid())
                    .data("{\"data\":\"data\"}")
                    .metadata("{\"metadata\":\"metadata\"}")
                    .build();

            final TransactionWrite transactionWrite = new TransactionWriteBuilder(transactionId)
                    .addEvent(eventData)
                    .requireMaster(true)
                    .build();

            connection.tell(transactionWrite, getRef());
            final TransactionWriteCompleted transactionWriteCompleted = expectMsgClass(TransactionWriteCompleted.class);

            assertEquals(transactionWriteCompleted.transactionId(), transactionId);

            final TransactionCommit transactionCommit = new TransactionCommitBuilder(transactionId).build();

            connection.tell(transactionCommit, getRef());
            final TransactionCommitCompleted transactionCommitCompleted = expectMsgClass(TransactionCommitCompleted.class);

            assertEquals(transactionCommitCompleted.transactionId(), transactionId);
        }};
    }

    @Test
    public void testReadEvent() {
        new JavaTestKit(system) {{

            final String streamId = "java-read-event" + newUuid();

            final ReadEvent readEvent = new ReadEventBuilder(streamId)
                    .eventNumber(0)
                    .resolveLinkTos(false)
                    .requireMaster(true)
                    .build();

            connection.tell(readEvent, getRef());
            final Failure failed = expectMsgClass(Failure.class);

            assertEquals(((EventStoreException) failed.cause()).reason(), EventStoreError.StreamNotFound());

            final EventData eventData = new EventDataBuilder(eventType)
                    .eventId(newUuid())
                    .data("{\"data\":\"data\"}")
                    .metadata("{\"metadata\":\"metadata\"}")
                    .build();

            final WriteEvents writeEvents = new WriteEventsBuilder(streamId)
                    .event(eventData)
                    .build();

            connection.tell(writeEvents, getRef());
            expectMsgClass(WriteEventsCompleted.class);


            connection.tell(readEvent, getRef());
            final ReadEventCompleted completed = expectMsgClass(ReadEventCompleted.class);

            final Event event = completed.event();
            assertEquals(event.number(), EventNumber$.MODULE$.apply(0));
            assertEquals(event.streamId(), EventStream$.MODULE$.apply(streamId));
            assertEquals(event.data(), eventData);
        }};
    }

    @Test
    public void testReadStreamEvents() {
        new JavaTestKit(system) {{

            final String streamId = "java-read-stream-events" + newUuid();

            final ReadStreamEvents readStreamEvents = new ReadStreamEventsBuilder(streamId)
                    .fromNumber(0)
                    .maxCount(2)
                    .forward()
                    .requireMaster(true)
                    .resolveLinkTos(false)
                    .build();

            connection.tell(readStreamEvents, getRef());
            final Failure failed = expectMsgClass(Failure.class);

            assertEquals(((EventStoreException) failed.cause()).reason(), EventStoreError.StreamNotFound());

            final WriteEventsBuilder builder = new WriteEventsBuilder(streamId);
            for (int x = 0; x < 2; x++) {

                final EventData eventData = new EventDataBuilder(eventType)
                        .eventId(newUuid())
                        .data("{\"data\":\"data\"}")
                        .metadata("{\"metadata\":\"metadata\"}")
                        .build();

                builder.addEvent(eventData);

            }
            connection.tell(builder.build(), getRef());
            expectMsgClass(WriteEventsCompleted.class);

            connection.tell(readStreamEvents, getRef());
            final ReadStreamEventsCompleted completed = expectMsgClass(ReadStreamEventsCompleted.class);

            assertEquals(completed.direction(), ReadDirection$.MODULE$.Forward());
            assertTrue(completed.endOfStream());
            assertEquals(completed.lastEventNumber(), EventNumber$.MODULE$.apply(1));
            assertEquals(completed.events().length(), 2);
        }};

    }


    @Test
    public void testReadAllEvents() {
        new JavaTestKit(system) {{
            final ReadAllEvents readAllEvents = new ReadAllEventsBuilder()
                    .fromFirstPosition()
                    .maxCount(2)
                    .forward()
                    .resolveLinkTos(false)
                    .requireMaster(true)
                    .build();

            connection.tell(readAllEvents, getRef());
            final ReadAllEventsCompleted completed = expectMsgClass(ReadAllEventsCompleted.class);

            // TODO work with Position
            assertTrue(completed.position().$greater$eq(Position$.MODULE$.First()));
            assertEquals(completed.direction(), ReadDirection$.MODULE$.Forward());
        }};
    }

    @Test
    public void testSubscribeTo() {
        new JavaTestKit(system) {{

            final SubscribeTo subscribeToAll = new SubscribeToBuilder()
                    .toAll()
                    .resolveLinkTos(false)
                    .build();

            connection.tell(subscribeToAll, getRef());
            final SubscribeToAllCompleted subscribeToAllCompleted = expectMsgClass(SubscribeToAllCompleted.class);

            assertTrue(subscribeToAllCompleted.lastCommit() > 0);


            final SubscribeTo subscribeToStream = new SubscribeToBuilder()
                    .toStream("java-subscribe-to-stream")
                    .resolveLinkTos(false)
                    .build();

            connection.tell(subscribeToStream, getRef());
            final SubscribeToStreamCompleted subscribeToStreamCompleted = expectMsgClass(SubscribeToStreamCompleted.class);

            assertTrue(subscribeToStreamCompleted.lastCommit() > 0);
            assertTrue(subscribeToStreamCompleted.lastEventNumber().isEmpty());
        }};
    }
}