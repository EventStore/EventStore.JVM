# Event Store JVM Client [![Build Status](https://travis-ci.org/EventStore/eventstorejvmclient.png?branch=master)](https://travis-ci.org/EventStore/eventstorejvmclient)

<table border="0">
  <tr>
    <td>Scala </td>
    <td>2.10.3</td>
  </tr>
  <tr>
    <td>Akka </td>
    <td>2.2.3</td>
  </tr>
</table>

## Status

Currently the only API available is Actor-like via sending/receiving messages to actors.
This is not a user friendliest one, but if you need as much performance as possible, then use Actor-like api.

However we will provide more convenient API based on `scala.concurrent.Future`.

## Examples

### ReadEvent (Java)

```java
import akka.actor.*;
import akka.actor.Status.Failure;
import akka.event.*;
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
        final ActorRef connection = system.actorOf(ConnectionActor.props(settings));
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
            if (message instanceof ReadEventCompleted) {
                final ReadEventCompleted completed = (ReadEventCompleted) message;
                final Event event = completed.event();
                log.info("EVENT: " + event.toString());
            } else if (message instanceof Failure) {
                final Failure failure = ((Failure) message);
                final EventStoreException exception = (EventStoreException) failure.cause();
                log.error("FAILED: reason: {}, message: {}", exception.reason(), exception.message());
            } else
                unhandled(message);
        }
    }
}
```

### ReadEvent (Scala)

```scala
import akka.actor.Status.Failure
import akka.actor._
import eventstore._
import eventstore.tcp.ConnectionActor
import java.net.InetSocketAddress

object ReadEventExample extends App {
  val system = ActorSystem()

  val settings = Settings(
    address = new InetSocketAddress("127.0.0.1", 1113),
    defaultCredentials = Some(UserCredentials("admin", "changeit")))

  val connection = system.actorOf(ConnectionActor.props(settings))
  system.actorOf(Props(classOf[ReadEventActor], connection))
}

class ReadEventActor(connection: ActorRef) extends Actor with ActorLogging {

  connection ! ReadEvent(
    streamId = EventStream.Id("my-stream"),
    eventNumber = EventNumber.First)

  def receive = {
    case ReadEventCompleted(event)                        => log.info(s"SUCCEED: $event")
    case Failure(EventStoreException(reason, message, _)) => log.error(s"FAILED: reason $reason, message: $message")
  }
}
```

## Setup

* Maven:
```xml
    <dependency>
        <groupId>com.geteventstore</groupId>
        <artifactId>eventstore-client_2.10</artifactId>
        <version>0.1</version>
    </dependency>
```

* Sbt
```scala
    libraryDependencies += "com.geteventstore" % "eventstore-client_2.10" % "0.1"
```