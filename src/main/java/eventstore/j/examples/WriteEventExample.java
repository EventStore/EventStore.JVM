package eventstore.j.examples;

import akka.actor.*;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import eventstore.*;
import eventstore.j.EventDataBuilder;
import eventstore.j.WriteEventsBuilder;
import eventstore.tcp.ConnectionActor;
import scala.Option;

import java.util.UUID;

public class WriteEventExample {
    public static void main(String[] args) {
        final ActorSystem system = ActorSystem.create();
        final ActorRef connection = system.actorOf(ConnectionActor.getProps());
        final ActorRef writeResult = system.actorOf(Props.create(WriteResult.class));

        final EventData event = new EventDataBuilder("my-event")
                .eventId(UUID.randomUUID())
                .data("my event data")
                .metadata("my first event")
                .build();

        final WriteEvents writeEvents = new WriteEventsBuilder("my-stream")
                .addEvent(event)
                .expectAnyVersion()
                .build();

        connection.tell(writeEvents, writeResult);
    }


    public static class WriteResult extends UntypedActor {
        final LoggingAdapter log = Logging.getLogger(getContext().system(), this);

        public void onReceive(Object message) throws Exception {
            if (message instanceof WriteEventsCompleted) {
                final WriteEventsCompleted completed = (WriteEventsCompleted) message;
                final Option<EventNumber.Range> range = completed.numbersRange();
                log.info("range: {}", range);
            } else if (message instanceof Status.Failure) {
                final Status.Failure failure = ((Status.Failure) message);
                final EsException exception = (EsException) failure.cause();
                log.error("reason: {}, message: {}", exception.reason(), exception.message());
            } else
                unhandled(message);

            context().system().shutdown();
        }
    }
}
