package eventstore.util;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.testkit.JavaTestKit;
import eventstore.Settings;
import eventstore.tcp.ConnectionActor;
import org.junit.AfterClass;
import org.junit.BeforeClass;

/**
 * @author Yaroslav Klymko
 */
abstract public class ActorTest {

    protected static ActorSystem system;
    protected static ActorRef connection;


    @BeforeClass
    public static void setup() {
        system = ActorSystem.create();
        connection = system.actorOf(ConnectionActor.props(Settings.Default()));
    }

    @AfterClass
    public static void shutdown() {
        JavaTestKit.shutdownActorSystem(system);
        system = null;
    }

    protected class TestKit extends JavaTestKit {
        public TestKit() {
            super(system);
        }
    }
}
