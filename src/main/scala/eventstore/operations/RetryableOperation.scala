package eventstore
package operations

import eventstore.operations.OnIncoming._
import scala.util.Try

private[eventstore] case class RetryableOperation[C](
    operation:   Operation[C],
    retriesLeft: Int,
    maxRetries:  Int,
    ongoing:     Boolean
) extends Operation[C] {

  def id = operation.id

  def version = operation.version

  def client = operation.client

  def connected = {
    import eventstore.operations.OnConnected._
    operation.connected match {
      case Retry(x, p) => Retry(copy(operation = x, ongoing = true), p)
      case x: Stop     => x
    }
  }

  def clientTerminated = operation.clientTerminated

  def disconnected = {
    import eventstore.operations.OnDisconnected._
    operation.disconnected match {
      case Continue(x) => Continue(copy(operation = x, ongoing = false))
      case x: Stop     => x
    }
  }

  def inspectOut = {
    import eventstore.operations.OnOutgoing._
    operation.inspectOut andThen {
      case Continue(x, p) => Continue(x, p)
      case x: Stop        => x
    }
  }

  def inspectIn(in: Try[In]) = operation.inspectIn(in) match {
    case x: Stop                 => x
    case Ignore                  => Ignore
    case Continue(operation, in) => Continue(reset(operation), in)
    case Retry(operation, pack) =>
      if (!ongoing) Retry(wrap(operation), pack)
      else if (retriesLeft > 0) Retry(decrement(operation), pack)
      else Stop(new RetriesLimitReachedException(s"Operation $pack reached retries limit: $maxRetries"))
  }

  private def decrement(x: Operation[C]) = copy(operation = x, retriesLeft = retriesLeft - 1)

  private def reset(x: Operation[C]) = copy(operation = x, retriesLeft = maxRetries)

  private def wrap(x: Operation[C]) = copy(operation = x)
}

private[eventstore] object RetryableOperation {
  def apply[C](operation: Operation[C], maxRetries: Int, ongoing: Boolean): RetryableOperation[C] = {
    RetryableOperation(operation, maxRetries, maxRetries, ongoing)
  }
}
