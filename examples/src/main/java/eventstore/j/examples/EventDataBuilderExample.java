package eventstore.j.examples;

import eventstore.EventData;
import eventstore.j.EventDataBuilder;

import java.util.UUID;

public class EventDataBuilderExample {

    final EventData empty = new EventDataBuilder("eventType").build();

    final EventData binary = new EventDataBuilder("eventType")
            .eventId(UUID.randomUUID())
            .data(new byte[]{1, 2, 3, 4})
            .metadata(new byte[]{5, 6, 7, 8})
            .build();

    final EventData string = new EventDataBuilder("eventType")
            .eventId(UUID.randomUUID())
            .data("data")
            .metadata("metadata")
            .build();

    final EventData json = new EventDataBuilder("eventType")
            .eventId(UUID.randomUUID())
            .jsonData("{\"data\":\"data\"}")
            .jsonMetadata("{\"metadata\":\"metadata\"}")
            .build();
}
