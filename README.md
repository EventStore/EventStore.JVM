# Event Store JVM Client [![Build Status](https://travis-ci.org/EventStore/EventStore.JVM.png?branch=master)](https://travis-ci.org/EventStore/EventStore.JVM)

**JVM Client works nicely with EventStore 2.0.1**

<table border="0">
  <tr>
    <td><a href="http://www.scala-lang.org">Scala</a> </td>
    <td>2.10.4/2.11.0</td>
  </tr>
  <tr>
    <td><a href="http://akka.io">Akka</a> </td>
    <td>2.3.2</td>
  </tr>
</table>


We have two APIs available:

* Calling methods on `EsConnection`

We are using [`scala.concurrent.Future`](http://docs.scala-lang.org/overviews/core/futures.html) for asynchronous calls, however it is not friendly enough for Java users.
In order to make Java devs happy and not reinvent a wheel, we propose to use tools invented by Akka team.
[Check it out](http://doc.akka.io/docs/akka/2.2.4/java/futures.html)

```java
final EsConnection connection = EsConnectionFactory.create(system);
final Future<Event> future = connection.readEvent("my-stream", new EventNumber.Exact(0), false, null);
```

```scala
val connection = EsConnection(system)
val future = connection future ReadEvent(EventStream("my-stream"), EventNumber.First)
```

* Sending messages to `eventstore.ConnectionActor`

```java
final ActorRef connection = system.actorOf(ConnectionActor.getProps());
final ReadEvent readEvent = new ReadEventBuilder("my-stream")
        .first()
        .build();
connection.tell(readEvent, null);
```

```scala
val connection = system.actorOf(ConnectionActor.props())
connection ! ReadEvent(EventStream("my-stream"), EventNumber.First)
```


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
        final ActorRef connection = system.actorOf(ConnectionActor.getProps(settings));
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

### Subscribe to All

```java
import akka.actor.ActorSystem;
import eventstore.IndexedEvent;
import eventstore.SubscriptionObserver;
import eventstore.j.EsConnection;
import eventstore.j.EsConnectionFactory;

import java.io.Closeable;

public class SubscribeToAllExample {
    public static void main(String[] args) {
        final ActorSystem system = ActorSystem.create();
        final EsConnection connection = EsConnectionFactory.create(system);
        final Closeable closeable = connection.subscribeToAll(new SubscriptionObserver<IndexedEvent>() {
            @Override
            public void onLiveProcessingStart(Closeable subscription) {
                system.log().info("live processing started");
            }

            @Override
            public void onEvent(IndexedEvent event, Closeable subscription) {
                system.log().info(event.toString());
            }

            @Override
            public void onError(Throwable e) {
                system.log().error(e.toString());
            }

            @Override
            public void onClose() {
                system.log().error("subscription closed");
            }
        }, false, null);
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
        log.info("event: {}", event)
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

  val event = EventData("my-event", data = Content("my event data"), metadata = Content("my first event"))

  connection ! WriteEvents(EventStream("my-stream"), List(event))

  class WriteResult extends Actor with ActorLogging {
    def receive = {
      case WriteEventsCompleted(eventNumber) =>
        log.info("eventNumber: {}", eventNumber)
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

### Count all events

```scala
import akka.actor._
import eventstore.LiveProcessingStarted
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
  context.setReceiveTimeout(1.second)

  def receive = count(0)

  def count(n: Long, printed: Boolean = false): Receive = {
    case x: IndexedEvent       => context become count(n + 1)
    case LiveProcessingStarted => log.info("live processing started")
    case ReceiveTimeout if !printed =>
      log.info("count {}", n)
      context become count(n, printed = true)
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

### EventStoreExtension

Most common use case is to have a single Event Store connection per application.
Thus you can use our akka extension, it will make sure you have a single instance of connection actor.
  
```scala  
EventStoreExtension(system).actor ! ReadEvent(EventStream("stream"))
EventStoreExtension(system).connection.future(ReadEvent(EventStream("stream")))
```

## Setup

* Maven:
```xml
    <dependency>
        <groupId>com.geteventstore</groupId>
        <artifactId>eventstore-client_2.11</artifactId>
        <version>0.4.0</version>
    </dependency>
```

* Sbt
```scala
    libraryDependencies += "com.geteventstore" %% "eventstore-client" % "0.4.0"
```
