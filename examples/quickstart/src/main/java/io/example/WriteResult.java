package io.example;

import akka.actor.*;
import akka.event.*;
import eventstore.j.*;
import eventstore.core.*;
import eventstore.akka.tcp.ConnectionActor;

public class WriteResult extends AbstractActor {

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