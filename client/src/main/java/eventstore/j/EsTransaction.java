package eventstore.j;

import scala.Unit;
import scala.concurrent.Future;
import java.util.Collection;
import eventstore.core.EventData;

/**
 * Represents a multi-request transaction with the Event Store
 */
public interface EsTransaction {

  /**
   * @return The ID of the transaction. This can be used to recover a transaction later.
   */
  long getId();


  /**
   * Writes to a transaction in the event store asynchronously
   *
   * @param events The events to write
   * @return A {@link scala.concurrent.Future} that the caller can await on
   */
  Future<Unit> write(Collection<EventData> events);

  /**
   * Commits this transaction
   *
   * @return A {@link scala.concurrent.Future} containing expected version for following write requests
   */
  Future<Unit> commit();
}