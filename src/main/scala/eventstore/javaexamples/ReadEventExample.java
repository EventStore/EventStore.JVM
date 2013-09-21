package eventstore.javaexamples;

import akka.actor.*;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import eventstore.*;
import eventstore.j.*;
import eventstore.tcp.ConnectionActor;

import java.net.InetSocketAddress;


public class ReadEventExample {

    public static void main(String[] args) {
        final ActorSystem system = ActorSystem.create();
        final Settings settings = new SettingsBuilder()
                .address(new InetSocketAddress("127.0.0.1", 1113))
                .defaultCredentials("admin", "changeit")
                .requireMaster(true)
                .build();
        final ActorRef connection = system.actorOf(Props.create(ConnectionActor.class, settings));
        final ActorRef readEventActor = system.actorOf(Props.create(ReadEventActor.class, connection));
    }


    public static class ReadEventActor extends UntypedActor {
        final LoggingAdapter log = Logging.getLogger(getContext().system(), this);

        ActorRef connection;

        public ReadEventActor(ActorRef connection) {
            this.connection = connection;
        }

        @Override
        public void preStart() throws Exception {
            final ReadEvent readEvent = new ReadEventBuilder("my-stream")
                    .eventNumberFirst()
                    .resolveLinkTos(false)
                    .requireMaster(true)
                    .build();

            connection.tell(readEvent, getSelf());
        }

        public void onReceive(Object message) throws Exception {
            if (message instanceof ReadEventSucceed) {
                final ReadEventSucceed succeed = (ReadEventSucceed) message;
                final Event event = succeed.event();
                log.info("SUCCEED: " + event.toString());

            } else if (message instanceof ReadEventFailed) {
                final ReadEventFailed failed = (ReadEventFailed) message;
                log.error("FAILED: reason: {}, message: {}", failed.reason(), failed.message());

            } else
                unhandled(message);
        }
    }
}