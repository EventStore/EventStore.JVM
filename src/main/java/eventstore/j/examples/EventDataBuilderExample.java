package eventstore.j.examples;

import eventstore.EventData;
import eventstore.j.EventDataBuilder;

import java.util.UUID;

public class EventDataBuilderExample {

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
}