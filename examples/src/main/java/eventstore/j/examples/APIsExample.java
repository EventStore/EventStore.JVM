package eventstore.j.examples;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import eventstore.Event;
import eventstore.EventNumber;
import eventstore.ReadEvent;
import eventstore.j.EsConnection;
import eventstore.j.EsConnectionFactory;
import eventstore.j.ReadEventBuilder;
import eventstore.akka.tcp.ConnectionActor;
import scala.concurrent.Future;

public class APIsExample {
    final ActorSystem system = ActorSystem.create();

    public void methodCall() {
        final EsConnection connection = EsConnectionFactory.create(system);
        final Future<Event> future = connection.readEvent("my-stream", new EventNumber.Exact(0), false, null);
    }

    public void messageSending() {
        final ActorRef connection = system.actorOf(ConnectionActor.getProps());
        final ReadEvent readEvent = new ReadEventBuilder("my-stream")
                .first()
                .build();
        connection.tell(readEvent, null);
    }
}
