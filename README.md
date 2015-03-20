# Event Store JVM Client [![Build Status](https://api.travis-ci.org/EventStore/EventStore.JVM.svg)](https://travis-ci.org/EventStore/EventStore.JVM) [![Coverage Status](https://coveralls.io/repos/EventStore/EventStore.JVM/badge.svg)](https://coveralls.io/r/EventStore/EventStore.JVM)

**JVM Client works nicely with EventStore 3.0.1**

<table border="0">
  <tr>
    <td><a href="http://www.scala-lang.org">Scala</a> </td>
    <td>2.10.4/2.11.5</td>
  </tr>
  <tr>
    <td><a href="http://akka.io">Akka</a> </td>
    <td>2.3.9</td>
  </tr>
</table>


We have two APIs available:

* Calling methods on `EsConnection`

We are using [`scala.concurrent.Future`](http://docs.scala-lang.org/overviews/core/futures.html) for asynchronous calls, however it is not friendly enough for Java users.
In order to make Java devs happy and not reinvent a wheel, we propose to use tools invented by Akka team.
[Check it out](http://doc.akka.io/docs/akka/2.3.9/java/futures.html)

```java
final EsConnection connection = EsConnectionFactory.create(system);
final Future<Event> future = connection.readEvent("my-stream", new EventNumber.Exact(0), false, null);
```

```scala
val connection = EsConnection(system)
val future = connection future ReadEvent(EventStream.Id("my-stream"), EventNumber.First)
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
connection ! ReadEvent(EventStream.Id("my-stream"), EventNumber.First)
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
                log.error(exception, exception.toString());
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
                log.info("range: {}, position: {}", completed.numbersRange(), completed.position());
            } else if (message instanceof Status.Failure) {
                final Status.Failure failure = ((Status.Failure) message);
                final EsException exception = (EsException) failure.cause();
                log.error(exception, exception.toString());
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

  connection ! ReadEvent(EventStream.Id("my-stream"), EventNumber.First)

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

  connection ! WriteEvents(EventStream.Id("my-stream"), List(event))

  class WriteResult extends Actor with ActorLogging {
    def receive = {
      case WriteEventsCompleted(range, position) =>
        log.info("range: {}, position: {}", range, position)
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

  val kickoff = Start(TransactionStart(EventStream.Id("my-stream")))
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
import scala.concurrent.Future
import eventstore._

object EsConnectionExample extends App {
  val system = ActorSystem()

  import system.dispatcher

  val connection = EsConnection(system)
  val log = system.log

  val stream = EventStream.Id("my-stream")

  val readEvent: Future[ReadEventCompleted] = connection.future(ReadEvent(stream))
  readEvent.onSuccess {
    case ReadEventCompleted(event) => log.info(event.toString)
  }

  val readStreamEvents: Future[ReadStreamEventsCompleted] = connection.future(ReadStreamEvents(stream))
  readStreamEvents.onSuccess {
    case x: ReadStreamEventsCompleted => log.info(x.events.toString())
  }

  val readAllEvents: Future[ReadAllEventsCompleted] = connection.future(ReadAllEvents(maxCount = 5))
  readAllEvents.onSuccess {
    case x: ReadAllEventsCompleted => log.info(x.events.toString())
  }

  val writeEvents: Future[WriteEventsCompleted] = connection.future(WriteEvents(stream, List(EventData("my-event"))))
  writeEvents.onSuccess {
    case x: WriteEventsCompleted => log.info(x.numbersRange.toString)
  }
}
```

### EventStoreExtension

Most common use case is to have a single Event Store connection per application.
Thus you can use our akka extension, it will make sure you have a single instance of connection actor.
  
```scala  
EventStoreExtension(system).actor ! ReadEvent(EventStream.Id("stream"))
EventStoreExtension(system).connection.future(ReadEvent(EventStream.Id("stream")))
```

### Configuration

Default client settings defined in [`reference.conf`](src/main/resources/reference.conf).
You can override them via own `application.conf` put in the `src/main/resources`, the same way you might already do for akka.
We are using the same approach - [config](https://github.com/typesafehub/config).

### Cluster

It is possible to use client against cluster of Event Store.
For this you need to configure client via `eventstore.cluster` section in [`reference.conf`](src/main/resources/reference.conf) or [`ClusterSettings`](src/main/scala/eventstore/cluster/ClusterSettings.scala).
Using `application.conf` for configuration is more preferable option.

## Setup

* Sbt
```scala
    libraryDependencies += "com.geteventstore" %% "eventstore-client" % "2.0.1"
```

* Maven:
```xml
    <dependency>
        <groupId>com.geteventstore</groupId>
        <artifactId>eventstore-client_2.11</artifactId>
        <version>2.0.1</version>
    </dependency>
```

or

```xml
    <dependency>
        <groupId>com.geteventstore</groupId>
        <artifactId>eventstore-client_2.10</artifactId>
        <version>2.0.1</version>
    </dependency>
```