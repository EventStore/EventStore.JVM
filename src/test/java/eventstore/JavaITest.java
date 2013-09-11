package eventstore;

import eventstore.j.*;
import eventstore.tcp.ConnectionActor;
import org.junit.*;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.JavaTestKit;

import java.util.UUID;

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

            final WriteEventsSucceed writeEventsSucceed = expectMsgClass(duration("3 seconds"), WriteEventsSucceed.class);


            Assert.assertEquals(writeEventsSucceed.firstEventNumber(), EventNumber$.MODULE$.First() /*TODO*/);
        }};
    }

    // TODO what's about using enums from java

    @Test
    public void testReadEvent() {
        new JavaTestKit(system) {{

            final String streamId = "java-read-event" + newUuid();

            final ReadEvent readEvent = new ReadEventBuilder(streamId, 0)
                    .requireMaster(true)
                    .resolveLinkTos(false)
                    .build();

            connection.tell(readEvent, getRef());

            final ReadEventFailed readEventfailed = expectMsgClass(duration("3 seconds"), ReadEventFailed.class);

            Assert.assertEquals(readEventfailed.reason(), ReadEventFailed.Reason$.MODULE$.NoStream());


            final EventData eventData = new EventDataBuilder(eventType)
                    .eventId(newUuid())
                    .data("{\"data\":\"data\"}")
                    .metadata("{\"metadata\":\"metadata\"}")
                    .build();

            final WriteEvents writeEvents = new WriteEventsBuilder(streamId)
                    .event(eventData)
                    .build();


            connection.tell(writeEvents, getRef());

            expectMsgClass(duration("3 seconds"), WriteEventsSucceed.class);


            connection.tell(readEvent, getRef());

            final ReadEventSucceed readEventSucceed = expectMsgClass(duration("3 seconds"), ReadEventSucceed.class);

            final Event event = readEventSucceed.event();
            Assert.assertEquals(event.number(), EventNumber$.MODULE$.apply(0));
            Assert.assertEquals(event.streamId(), EventStream$.MODULE$.apply(streamId));
            Assert.assertEquals(event.data(), eventData);
        }};


    }
}