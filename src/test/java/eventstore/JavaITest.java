package eventstore;

import eventstore.j.*;
import eventstore.tcp.ConnectionActor;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

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


    UUID newUuid() {
        return UUID.randomUUID();
    }


    // TODO what if 2 events with same ID ?

    @Test
    public void testWrite() {

        new JavaTestKit(system) {{

            final String streamId = "java-write-" + newUuid();
            final String eventType = "java-test";

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

//            ByteString$.MODULE$.apply() TODO

            Assert.assertEquals(writeEventsSucceed.firstEventNumber(), EventNumber$.MODULE$.First() /*TODO*/);
        }};
    }

    @Test
    public void testReadEvent() { }
}