package eventstore.j;

import akka.actor.ActorSystem;
import eventstore.Settings;

public class EsConnectionFactory {
    public static EsConnection create(ActorSystem system, Settings settings) {
        return EsConnectionImpl.apply(system, settings);
    }

    public static EsConnection create(ActorSystem system) {
        return create(system, Settings.getInstance());
    }
}