package eventstore;

import eventstore.j.*;
import eventstore.tcp.ConnectionActor;
import org.junit.*;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.JavaTestKit;

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
            final WriteEventsSucceed succeed = expectMsgClass(WriteEventsSucceed.class);

            assertEquals(succeed.firstEventNumber(), EventNumber$.MODULE$.First() /*TODO*/);
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
            final long transactionId = expectMsgClass(TransactionStartSucceed.class).transactionId();

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
            final TransactionWriteSucceed transactionWriteSucceed = expectMsgClass(TransactionWriteSucceed.class);

            assertEquals(transactionWriteSucceed.transactionId(), transactionId);

            final TransactionCommit transactionCommit = new TransactionCommitBuilder(transactionId).build();

            connection.tell(transactionCommit, getRef());
            final TransactionCommitSucceed transactionCommitSucceed = expectMsgClass(TransactionCommitSucceed.class);

            assertEquals(transactionCommitSucceed.transactionId(), transactionId);
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
            final ReadEventFailed failed = expectMsgClass(ReadEventFailed.class);

            assertEquals(failed.reason(), ReadEventFailed.Reason$.MODULE$.NoStream());

            final EventData eventData = new EventDataBuilder(eventType)
                    .eventId(newUuid())
                    .data("{\"data\":\"data\"}")
                    .metadata("{\"metadata\":\"metadata\"}")
                    .build();

            final WriteEvents writeEvents = new WriteEventsBuilder(streamId)
                    .event(eventData)
                    .build();

            connection.tell(writeEvents, getRef());
            expectMsgClass(WriteEventsSucceed.class);


            connection.tell(readEvent, getRef());
            final ReadEventSucceed succeed = expectMsgClass(ReadEventSucceed.class);

            final Event event = succeed.event();
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
            final ReadStreamEventsFailed failed = expectMsgClass(ReadStreamEventsFailed.class);

            assertEquals(failed.reason(), ReadStreamEventsFailed.Reason$.MODULE$.NoStream());
            assertEquals(failed.direction(), ReadDirection$.MODULE$.Forward());


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
            expectMsgClass(WriteEventsSucceed.class);

            connection.tell(readStreamEvents, getRef());
            final ReadStreamEventsSucceed succeed = expectMsgClass(ReadStreamEventsSucceed.class);

            assertEquals(succeed.direction(), ReadDirection$.MODULE$.Forward());
            assertTrue(succeed.endOfStream());
            assertEquals(succeed.lastEventNumber(), EventNumber$.MODULE$.apply(1));
            assertEquals(succeed.events().length(), 2);
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
            final ReadAllEventsSucceed succeed = expectMsgClass(ReadAllEventsSucceed.class);

            // TODO work with Position
            assertTrue(succeed.position().$greater(Position$.MODULE$.First()));
            assertEquals(succeed.direction(), ReadDirection$.MODULE$.Forward());
        }};
    }
}