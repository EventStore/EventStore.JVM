# Event Store JVM Client [![Build Status](https://travis-ci.org/EventStore/eventstorejvmclient.png?branch=master)](https://travis-ci.org/EventStore/eventstorejvmclient)

**JVM Client works nicely with EventStore 2.0.1**

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

## Java examples

### Read event

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
                .build();
        final ActorRef connection = system.actorOf(ConnectionActor.props(settings));
        final ActorRef readResult = system.actorOf(Props.create(ReadResult.class));

        final ReadEvent readEvent = new ReadEventBuilder("my-stream")
                .first()
                .resolveLinkTos(false)
                .requireMaster(true)
                .build();

        connection.tell(readEvent, readResult);
    }


    public static class ReadResult extends UntypedActor {
        final LoggingAdapter log = Logging.getLogger(getContext().system(), this);

        public void onReceive(Object message) throws Exception {
            if (message instanceof ReadEventCompleted) {
                final ReadEventCompleted completed = (ReadEventCompleted) message;
                final Event event = completed.event();
                log.info("event: {}", event);
            } else if (message instanceof Failure) {
                final Failure failure = ((Failure) message);
                final EsException exception = (EsException) failure.cause();
                log.error("reason: {}, message: {}", exception.reason(), exception.message());
            } else
                unhandled(message);

            context().system().shutdown();
        }
    }
}
```

### Write event

```java
import akka.actor.*;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import eventstore.*;
import eventstore.j.EventDataBuilder;
import eventstore.j.WriteEventsBuilder;
import eventstore.tcp.ConnectionActor;

import java.util.UUID;

public class WriteEventExample {
    public static void main(String[] args) {
        final ActorSystem system = ActorSystem.create();
        final ActorRef connection = system.actorOf(ConnectionActor.props(Settings.Default()));
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
                final EventNumber.Exact eventNumber = completed.firstEventNumber();
                log.info("eventNumber: {}", eventNumber);
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
```

### Build event

```java
    final EventData empty = new EventDataBuilder("empty").build();

    final EventData binary = new EventDataBuilder("binary")
            .eventId(UUID.randomUUID())
            .data(new byte[]{1, 2, 3, 4})
            .metadata(new byte[]{5, 6, 7, 8})
            .build();

    final EventData string = new EventDataBuilder("string")
            .eventId(UUID.randomUUID())
            .data("data")
            .metadata("metadata")
            .build();

    final EventData json = new EventDataBuilder("json")
            .eventId(UUID.randomUUID())
            .jsonData("{\"data\":\"data\"}")
            .jsonMetadata("{\"metadata\":\"metadata\"}")
            .build();
```

## Scala examples

### Read event

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
  implicit val readResult = system.actorOf(Props[ReadResult])

  connection ! ReadEvent(EventStream("my-stream"), EventNumber.First)

  class ReadResult extends Actor with ActorLogging {
    def receive = {
      case ReadEventCompleted(event) =>
        log.info(s"event: $event")
        context.system.shutdown()

      case Failure(e: EsException) =>
        log.error(e.toString)
        context.system.shutdown()
    }
  }
}
```

### Write event

```scala
import akka.actor.Status.Failure
import akka.actor.{ ActorLogging, Actor, Props, ActorSystem }
import eventstore._
import eventstore.tcp.ConnectionActor

object WriteEventExample extends App {
  val system = ActorSystem()
  val connection = system.actorOf(ConnectionActor.props())
  implicit val writeResult = system.actorOf(Props[WriteResult])

  val event = EventData("my-event", newUuid, data = Content("my event data"), metadata = Content("my first event"))

  connection ! WriteEvents(EventStream("my-stream"), List(event))

  class WriteResult extends Actor with ActorLogging {
    def receive = {
      case WriteEventsCompleted(eventNumber) =>
        log.info(s"eventNumber: $eventNumber")
        context.system.shutdown()

      case Failure(e: EsException) =>
        log.error(e.toString)
        context.system.shutdown()
    }
  }
}
```

### Start transaction

```scala
import akka.actor.ActorSystem
import eventstore.TransactionActor._
import eventstore.tcp.ConnectionActor
import eventstore.{ EventData, TransactionActor, EventStream, TransactionStart }

object StartTransactionExample extends App {
  val system = ActorSystem()
  val connection = system.actorOf(ConnectionActor.props())

  val kickoff = Start(TransactionStart(EventStream("my-stream")))
  val transaction = system.actorOf(TransactionActor.props(connection, kickoff))

  transaction ! GetTransactionId // replies with `TransactionId(transactionId)`
  transaction ! Write(EventData("transaction-event")) // replies with `WriteCompleted`
  transaction ! Write(EventData("transaction-event")) // replies with `WriteCompleted`
  transaction ! Write(EventData("transaction-event")) // replies with `WriteCompleted`
  transaction ! Commit // replies with `CommitCompleted`
}
```

### Continue transaction

```scala
import akka.actor.ActorSystem
import eventstore.TransactionActor._
import eventstore.tcp.ConnectionActor
import eventstore.{ EventData, TransactionActor }

object ContinueTransactionExample extends App {
  val system = ActorSystem()
  val connection = system.actorOf(ConnectionActor.props())

  val transactionId = 0L
  val kickoff = Continue(transactionId)
  val transaction = system.actorOf(TransactionActor.props(connection, kickoff))

  transaction ! GetTransactionId // replies with `TransactionId(transactionId)`
  transaction ! Write(EventData("transaction-event")) // replies with `WriteCompleted`
  transaction ! Write(EventData("transaction-event")) // replies with `WriteCompleted`
  transaction ! Write(EventData("transaction-event")) // replies with `WriteCompleted`
  transaction ! Commit // replies with `CommitCompleted`
}
```

### Count all events

```scala
import akka.actor._
import eventstore.Subscription.LiveProcessingStarted
import eventstore.tcp.ConnectionActor
import eventstore.{ IndexedEvent, SubscriptionActor }
import scala.concurrent.duration._

object CountAll extends App {
  val system = ActorSystem()
  val connection = system.actorOf(ConnectionActor.props(), "connection")
  val countAll = system.actorOf(Props[CountAll], "count-all")
  system.actorOf(SubscriptionActor.props(connection, countAll), "subscription")
}

class CountAll extends Actor with ActorLogging {
  context.setReceiveTimeout(5.seconds)

  def receive = count(0)

  def count(n: Long): Receive = {
    case x: IndexedEvent       => context become count(n + 1)
    case LiveProcessingStarted => log.info("live processing started")
    case ReceiveTimeout        => log.info("count {}", n)
  }
}

```

### Future-like api

```scala
import akka.actor.ActorSystem
import eventstore._
import scala.util.{ Success, Failure }
import scala.concurrent.Future

object EsConnectionExample extends App {
  val system = ActorSystem()

  import system.dispatcher

  val connection = EsConnection(system)
  val log = system.log

  val stream = EventStream("my-stream")

  val readEvent: Future[ReadEventCompleted] = connection.future(ReadEvent(stream))
  readEvent.onComplete {
    case Failure(e)                         => log.error(e.toString)
    case Success(ReadEventCompleted(event)) => log.info(event.toString)
  }

  val readStreamEvents: Future[ReadStreamEventsCompleted] = connection.future(ReadStreamEvents(stream))
  readStreamEvents.onComplete {
    case Failure(e)                            => log.error(e.toString)
    case Success(x: ReadStreamEventsCompleted) => log.info(x.events.toString())
  }

  val readAllEvents: Future[ReadAllEventsCompleted] = connection.future(ReadAllEvents(maxCount = 5))
  readAllEvents.onComplete {
    case Failure(e)                         => log.error(e.toString)
    case Success(x: ReadAllEventsCompleted) => log.info(x.events.toString())
  }

  val writeEvents: Future[WriteEventsCompleted] = connection.future(WriteEvents(stream, List(EventData("my-event"))))
  writeEvents.onComplete {
    case Failure(e)                       => log.error(e.toString)
    case Success(x: WriteEventsCompleted) => log.info(x.firstEventNumber.toString())
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