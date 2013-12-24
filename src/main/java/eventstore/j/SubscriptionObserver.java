package eventstore.j;

import java.io.Closeable;


public interface SubscriptionObserver<T> {
    void onEvent(T event, Closeable subscription);

    void onError(Exception e);

    void onClose();
}