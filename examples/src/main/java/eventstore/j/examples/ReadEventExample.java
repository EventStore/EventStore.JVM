package eventstore.j.examples;

import java.net.InetSocketAddress;
import akka.actor.*;
import akka.actor.Status.Failure;
import akka.event.*;
import eventstore.j.*;
import eventstore.core.*;
import eventstore.akka.Settings;
import eventstore.akka.tcp.ConnectionActor;

public class ReadEventExample {

    public static void main(String[] args) {
        final ActorSystem system = ActorSystem.create();
        final Settings settings = new SettingsBuilder()
                .address(new InetSocketAddress("127.0.0.1", 1113))
                .defaultCredentials("admin", "changeit")
                .build();
        final ActorRef connection = system.actorOf(ConnectionActor.getProps(settings));
        final ActorRef readResult = system.actorOf(Props.create(ReadResult.class));

        final ReadEvent readEvent = new ReadEventBuilder("my-stream")
                .first()
                .resolveLinkTos(false)
                .requireMaster(true)
                .build();

        connection.tell(readEvent, readResult);
    }


    public static class ReadResult extends AbstractActor {
        final LoggingAdapter log = Logging.getLogger(getContext().system(), this);

        @Override
        public Receive createReceive() {
            return receiveBuilder()
                    .match(ReadEventCompleted.class, m -> {
                        final Event event = m.event();
                        log.info("event: {}", event);
                        context().system().terminate();
                    })
                    .match(Failure.class, f -> {
                        final EsException exception = (EsException) f.cause();
                        log.error(exception, exception.toString());
                        context().system().terminate();
                    })
                    .build();
        }
    }
}