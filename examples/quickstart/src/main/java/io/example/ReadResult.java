package io.example;

import akka.actor.*;
import akka.actor.Status.Failure;
import akka.event.*;
import eventstore.j.*;
import eventstore.core.*;
import eventstore.akka.tcp.ConnectionActor;

public class ReadResult extends AbstractActor {
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