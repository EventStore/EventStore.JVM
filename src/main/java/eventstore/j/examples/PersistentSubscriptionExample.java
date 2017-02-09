package eventstore.j.examples;

import akka.actor.ActorSystem;
import eventstore.EventStoreExtension;
import eventstore.PersistentSubscriptionSettings;
import eventstore.j.EsConnection;
import eventstore.j.PersistentSubscriptionSettingsBuilder;

public class PersistentSubscriptionExample {
  public static void main(String[] args) {
    final ActorSystem system = ActorSystem.create();
    final EventStoreExtension extension = EventStoreExtension.get(system);
    final EsConnection connection = extension.connectionJava();

    connection.createPersistentSubscription("my-stream", "example", null, null);

    final PersistentSubscriptionSettings settings = new PersistentSubscriptionSettingsBuilder()
        .startFromCurrent()
        .roundRobin()
        .doNotResolveLinkTos()
        .withExtraStatistic()
        .build();

    connection.updatePersistentSubscription("my-stream", "example", settings, null);

    connection.deletePersistentSubscription("my-stream", "example", null);
  }
}
