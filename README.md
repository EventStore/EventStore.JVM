# Event Store JVM Client [![Build Status](https://api.travis-ci.org/EventStore/EventStore.JVM.svg)](https://travis-ci.org/EventStore/EventStore.JVM) [![Coverage Status](https://coveralls.io/repos/EventStore/EventStore.JVM/badge.svg)](https://coveralls.io/r/EventStore/EventStore.JVM) [![Version](https://img.shields.io/maven-central/v/com.geteventstore/eventstore-client_2.13.svg)](http://search.maven.org/#search%7Cga%7C1%7Cg%3Acom.geteventstore%20AND%20eventstore-client)

<table border="0">
  <tr>
    <td><a href="http://www.scala-lang.org">Scala</a> </td>
    <td>2.13.4 / 2.12.13</td>
  </tr>
  <tr>
    <td><a href="http://akka.io">Akka</a> </td>
    <td>2.6.12</td>
  </tr>
  <tr>
    <td><a href="https://eventstore.org">Event Store</a></td>
    <td>v5.x and v20.x are supported</td>    
  </tr>
</table>


We have two APIs available:

* Calling methods on `EsConnection`

We are using [`scala.concurrent.Future`](http://docs.scala-lang.org/overviews/core/futures.html) for asynchronous calls, however it is not friendly enough for Java users.
In order to make Java devs happy and not reinvent a wheel, we propose to use tools invented by Akka team.
[Check it out](https://doc.akka.io/docs/akka/current/futures.html)

```java
final EsConnection connection = EsConnectionFactory.create(system);
final Future<Event> future    = connection.readEvent("my-stream", new EventNumber.Exact(0), false, null);
```

```scala
val connection = EsConnection(system)
val future     = connection(ReadEvent(EventStream.Id("my-stream"), EventNumber.First))
```

* Sending messages to `eventstore.ConnectionActor`

```java
final ActorRef connection = system.actorOf(ConnectionActor.getProps());
final ReadEvent readEvent = new ReadEventBuilder("my-stream").first().build();
connection.tell(readEvent, null);
```

```scala
val connection = system.actorOf(ConnectionActor.props())
connection ! ReadEvent(EventStream.Id("my-stream"), EventNumber.First)
```

## Setup

#### Sbt
```scala
libraryDependencies += "com.geteventstore" %% "eventstore-client" % "7.3.1"
```

#### Maven
```xml
<dependency>
    <groupId>com.geteventstore</groupId>
    <artifactId>eventstore-client_${scala.version}</artifactId>
    <version>7.3.1</version>
</dependency>
```

## Java examples

### Read event

```java
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
```

### Write event

```java
import java.util.UUID;
import akka.actor.*;
import akka.event.*;
import eventstore.j.*;
import eventstore.core.*;
import eventstore.akka.tcp.ConnectionActor;

public class WriteEventExample {

    public static void main(String[] args) {

        final ActorSystem system   = ActorSystem.create();
        final ActorRef connection  = system.actorOf(ConnectionActor.getProps());
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

    public static class WriteResult extends AbstractActor {

        final LoggingAdapter log = Logging.getLogger(getContext().system(), this);

        @Override
        public Receive createReceive() {
            return receiveBuilder()
                    .match(WriteEventsCompleted.class, m -> {
                        log.info("range: {}, position: {}", m.numbersRange(), m.position());
                        context().system().terminate();
                    })
                    .match(Status.Failure.class, f -> {
                        final EsException exception = (EsException) f.cause();
                        log.error(exception, exception.toString());
                    })
                    .build();
        }

    }
}
```

### Subscribe to All

```java
import java.io.Closeable;
import akka.actor.ActorSystem;
import eventstore.j.*;
import eventstore.core.IndexedEvent;
import eventstore.akka.SubscriptionObserver;

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
import java.util.UUID;
import eventstore.core.EventData;
import eventstore.j.EventDataBuilder;

public class EventDataBuilderExample {

    final EventData empty = new EventDataBuilder("eventType").build();

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
}
```

## Scala examples

### Read event

```scala
import java.net.InetSocketAddress
import _root_.akka.actor._
import _root_.akka.actor.Status.Failure
import eventstore.akka.tcp.ConnectionActor

object ReadEventExample extends App {
  val system = ActorSystem()

  val settings = Settings(
    address = new InetSocketAddress("127.0.0.1", 1113),
    defaultCredentials = Some(UserCredentials("admin", "changeit"))
  )

  val connection = system.actorOf(ConnectionActor.props(settings))
  implicit val readResult = system.actorOf(Props[ReadResult]())

  connection ! ReadEvent(EventStream.Id("my-stream"), EventNumber.First)

  class ReadResult extends Actor with ActorLogging {
    def receive = {
      case ReadEventCompleted(event) =>
        log.info("event: {}", event)
        shutdown()

      case Failure(e: EsException) =>
        log.error(e.toString)
        shutdown()
    }

    def shutdown(): Unit = { context.system.terminate(); () }
  }
}
```

### Write event

```scala
import _root_.akka.actor.Status.Failure
import _root_.akka.actor.{ ActorLogging, Actor, Props, ActorSystem }
import eventstore.core.util.uuid.randomUuid
import eventstore.akka.tcp.ConnectionActor

object WriteEventExample extends App {

  val system      = ActorSystem()
  val connection  = system.actorOf(ConnectionActor.props())
  val event       = EventData("my-event", eventId = randomUuid, data = Content("my event data"), metadata = Content("my first event"))

  implicit val writeResult = system.actorOf(Props(WriteResult))

  connection ! WriteEvents(EventStream.Id("my-stream"), List(event))

  case object WriteResult extends Actor with ActorLogging {

    def receive = {
      case WriteEventsCompleted(range, position) =>
        log.info("range: {}, position: {}", range, position)
        shutdown()

      case Failure(e: EsException) =>
        log.error(e.toString)
        shutdown()
    }

    def shutdown(): Unit = { context.system.terminate();  () }
  }
}
```

### Start transaction

```scala
import _root_.akka.actor.{ActorSystem, Props}
import eventstore.core.util.uuid.randomUuid
import eventstore.akka.tcp.ConnectionActor
import eventstore.akka.TransactionActor._

object StartTransactionExample extends App {
  val system = ActorSystem()
  val connection = system.actorOf(ConnectionActor.props(), "connection")

  val kickoff = Start(TransactionStart(EventStream.Id("my-stream")))
  val transaction = system.actorOf(TransactionActor.props(connection, kickoff), "transaction")
  implicit val transactionResult = system.actorOf(Props[TransactionResult], "result")

  val data = EventData("transaction-event", eventId = randomUuid)

  transaction ! GetTransactionId // replies with `TransactionId(transactionId)`
  transaction ! Write(data) // replies with `WriteCompleted`
  transaction ! Write(data) // replies with `WriteCompleted`
  transaction ! Write(data) // replies with `WriteCompleted`
  transaction ! Commit // replies with `CommitCompleted`
}
```

### Count all events

```scala
import _root_.akka.actor._
import scala.concurrent.duration._
import eventstore.akka.tcp.ConnectionActor

object CountAll extends App {
  val system = ActorSystem()
  val connection = system.actorOf(ConnectionActor.props(), "connection")
  val countAll = system.actorOf(Props[CountAll](), "count-all")
  system.actorOf(SubscriptionActor.props(connection, countAll, None, None, Settings.Default), "subscription")
}

class CountAll extends Actor with ActorLogging {
  context.setReceiveTimeout(1.second)

  def receive = count(0)

  def count(n: Long, printed: Boolean = false): Receive = {
    case _: IndexedEvent       => context become count(n + 1)
    case LiveProcessingStarted => log.info("live processing started")
    case ReceiveTimeout if !printed =>
      log.info("count {}", n)
      context become count(n, printed = true)
  }
}
```

### Future-like api

```scala
import _root_.akka.actor.ActorSystem
import scala.concurrent.Future
import eventstore.core.util.uuid.randomUuid

object EsConnectionExample extends App {
  val system = ActorSystem()

  import system.dispatcher

  val connection = EsConnection(system)
  val log = system.log

  val stream = EventStream.Id("my-stream")

  val readEvent: Future[ReadEventCompleted] = connection(ReadEvent(stream))
  readEvent foreach { x =>
    log.info(x.event.toString)
  }

  val readStreamEvents: Future[ReadStreamEventsCompleted] = connection(ReadStreamEvents(stream))
  readStreamEvents foreach { x =>
    log.info(x.events.toString())
  }

  val readAllEvents: Future[ReadAllEventsCompleted] = connection(ReadAllEvents(maxCount = 5))
  readAllEvents foreach { x =>
    log.info(x.events.toString())
  }

  val writeEvents: Future[WriteEventsCompleted] = connection(WriteEvents(stream, List(EventData("my-event", eventId = randomUuid))))
  writeEvents foreach { x =>
    log.info(x.numbersRange.toString)
  }
}
```

### EventStoreExtension

Most common use case is to have a single Event Store connection per application.
Thus you can use our akka extension, it will make sure you have a single instance of connection actor.
  
```scala  
EventStoreExtension(system).actor ! ReadEvent(EventStream.Id("stream"))
EventStoreExtension(system).connection(ReadEvent(EventStream.Id("stream")))
```

### Streams

The client provides Akka Streams interface for EventStore subscriptions.
You can find two methods `allStreamsSource` and `streamSource` available in Java and Scala APIs.

Here is a short example on how to use it:

### List all streams

```scala
import _root_.akka.actor.ActorSystem

object ListAllStreamsExample extends App {
  implicit val system = ActorSystem()
  import system.dispatcher

  val connection = EventStoreExtension(system).connection
  val source = connection.streamSource(EventStream.System.`$streams`, infinite = false, resolveLinkTos = true)

  source
    .runForeach { x => println(x.streamId.streamId) }
    .onComplete { _ => system.terminate() }
}
```

### Reactive Streams

You can use generic [Reactive Streams](http://www.reactive-streams.org) `Publisher` interface for EventStore subscriptions, 
by converting an Akka Stream to Publisher. See: [Integrating Akka Streams with Reactive Streams](https://doc.akka.io/docs/akka/2.6/stream/reactive-streams-interop.html)

Here is a short example on how to accomplish that:

```scala
import _root_.akka.actor.ActorSystem
import _root_.akka.stream.scaladsl._
import org.reactivestreams.{Publisher, Subscriber}
import scala.concurrent.duration._

object MessagesPerSecondReactiveStreams extends App {
  implicit val system = ActorSystem()

  val connection = EventStoreExtension(system).connection

  val publisher: Publisher[String] = connection.allStreamsSource()
    .groupedWithin(Int.MaxValue, 1.second)
    .map { xs => f"${xs.size.toDouble / 1000}%2.1fk m/s" }
    .runWith(Sink.asPublisher(fanout = false))

  val subscriber: Subscriber[String] = Source.asSubscriber[String]
    .to(Sink.foreach(println))
    .run()

  publisher.subscribe(subscriber)
}
```

### Configuration

Default client settings defined in [`core reference.conf`](core/src/main/resources/reference.conf) and [`client reference.conf`](client/src/main/resources/reference.conf).
You can override them via own `application.conf` put in the `src/main/resources`, the same way you might already do for akka.
We are using the same approach using the same configuration [library](https://github.com/lightbend/config).

### Cluster

It is possible to use client against cluster of Event Store.
For this you need to configure client via `eventstore.cluster` section in [`core reference.conf`](core/src/main/resources/reference.conf) or [`ClusterSettings`](core/src/main/scala/eventstore/core/settings/ClusterSettings.scala).
Using `application.conf` for configuration is more preferable option.
