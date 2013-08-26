package eventstore

/**
 * @author Yaroslav Klymko
 */
class EventStoreException extends Exception {

  /*namespace EventStore.ClientAPI.Exceptions
  {
      public class EventStoreConnectionException : Exception
      {
          public EventStoreConnectionException()
          {
          }

          public EventStoreConnectionException(string message): base(message)
          {
          }

          public EventStoreConnectionException(string message, Exception innerException): base(message, innerException)
          {
          }

          protected EventStoreConnectionException(SerializationInfo info, StreamingContext context): base(info, context)
          {
          }
      }
  }
  */

}

class StreamDeletedException(streamId: String) extends EventStoreException {
  /*using EventStore.ClientAPI.Common.Utils;

namespace EventStore.ClientAPI.Exceptions
{
    public class StreamDeletedException : EventStoreConnectionException
    {
        public readonly string Stream;

        public StreamDeletedException(string stream)
            : base(string.Format("Event stream '{0}' is deleted.", stream))
        {
            Ensure.NotNullOrEmpty(stream, "stream");
            Stream = stream;
        }

        public StreamDeletedException()
            : base("Transaction failed due to underlying stream being deleted.")
        {
            Stream = null;
        }
    }
}
*/
}

class WrongExpectedVersionException extends EventStoreException {
  /*namespace EventStore.ClientAPI.Exceptions
{
    public class WrongExpectedVersionException : EventStoreConnectionException
    {
        public WrongExpectedVersionException(string message) : base(message)
        {
        }

        public WrongExpectedVersionException(string message, Exception innerException) : base(message, innerException)
        {
        }

        protected WrongExpectedVersionException(SerializationInfo info, StreamingContext context) : base(info, context)
        {
        }
    }
}
*/
}

