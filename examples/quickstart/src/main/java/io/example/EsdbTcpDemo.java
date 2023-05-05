package io.example;

import java.net.InetSocketAddress;
import java.util.UUID;
import akka.actor.*;
import akka.actor.Status.Failure;
import akka.event.*;
import eventstore.j.*;
import eventstore.core.*;
import eventstore.akka.Settings;
import eventstore.akka.tcp.ConnectionActor;

public class EsdbTcpDemo {
    public static void main(String args[]) {

        final ActorSystem system   = ActorSystem.create();
        //final ActorRef connection  = system.actorOf(ConnectionActor.getProps());
        final Settings settings = new SettingsBuilder()
                .address(new InetSocketAddress("127.0.0.1", 1113))
                //.defaultCredentials("admin", "changeit")
                .build();
        final ActorRef connection = system.actorOf(ConnectionActor.getProps(settings));
        final ActorRef writeResult = system.actorOf(Props.create(WriteResult.class));
        final ActorRef readResult = system.actorOf(Props.create(ReadResult.class));        

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


        final ReadEvent readEvent = new ReadEventBuilder("my-stream")
                .first()
                .resolveLinkTos(false)
                .requireMaster(true)
                .build();

        connection.tell(readEvent, readResult);        
    }

}