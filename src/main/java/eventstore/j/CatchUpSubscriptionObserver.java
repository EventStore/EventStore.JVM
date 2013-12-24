package eventstore.j;

import java.io.Closeable;


public interface CatchUpSubscriptionObserver<T> extends SubscriptionObserver<T> {
    void onLiveProcessingStart(Closeable subscription);
}