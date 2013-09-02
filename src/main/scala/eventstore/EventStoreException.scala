package eventstore

/**
 * @author Yaroslav Klymko
 */
class EventStoreException(message: String, cause: Option[Throwable] = None)
  extends Exception(message, cause getOrElse null)

object EventStoreException {
  def apply(reason: ReadAllEventsFailed.Reason.Value, msg: Option[String] = None): EventStoreException = {
    import ReadAllEventsFailed.Reason._
    val cause = reason match {
      case Error        => new ServerErrorException(msg)
      case AccessDenied => new AccessDeniedException()
    }
    new ReadException(EventStream.All, Some(cause))
  }

  def apply(
    streamId: EventStream.Id,
    reason: ReadStreamEventsFailed.Reason.Value,
    msg: Option[String]): EventStoreException = {
    import ReadStreamEventsFailed.Reason._

    val cause = reason match {
      case NoStream      => new StreamNotFoundException(streamId)
      case StreamDeleted => new StreamDeletedException(streamId)
      case Error         => new ServerErrorException(msg)
      case AccessDenied  => new AccessDeniedException()
    }
    new ReadException(streamId, Some(cause))
  }

  def apply(streamId: EventStream, reason: SubscriptionDropped.Reason.Value) = {
    import SubscriptionDropped.Reason._
    val cause = reason match {
      case Unsubscribed => None
      case AccessDenied => Some(new AccessDeniedException())
    }
    throw new SubscriptionException(streamId, cause)
  }
}

class ReadException(val streamId: EventStream, cause: Option[Throwable] = None)
  extends EventStoreException(s"failed to read from $streamId", cause)

class SubscriptionException(val streamId: EventStream, cause: Option[Throwable] = None)
  extends EventStoreException(s"subscription failed to $streamId", cause)

class AccessDeniedException(credentials: UserCredentials = UserCredentials.defaultAdmin)
  extends EventStoreException(s"access denied for $credentials")

class StreamDeletedException(streamId: EventStream.Id)
  extends EventStoreException(s"$streamId has been deleted")

class StreamNotFoundException(streamId: EventStream.Id)
  extends EventStoreException(s"$streamId not found")

class ServerErrorException(message: Option[String]) extends EventStoreException(message getOrElse null)