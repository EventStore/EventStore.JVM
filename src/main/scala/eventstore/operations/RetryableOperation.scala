package eventstore
package operations

import eventstore.operations.OnIncoming._
import scala.util.Try

private[eventstore] case class RetryableOperation(
    operation: Operation,
    retriesLeft: Int,
    maxRetries: Int,
    ongoing: Boolean) extends Operation {

  def id = operation.id

  def version = operation.version

  def client = operation.client

  def connected(outFunc: OutFunc) = {
    operation.connected(outFunc).map { operation =>
      copy(operation = operation, ongoing = true)
    }
  }

  def clientTerminated = operation.clientTerminated

  def disconnected = {
    operation.disconnected match {
      case x: OnDisconnected.Stop     => x
      case OnDisconnected.Continue(x) => OnDisconnected.Continue(copy(operation = x, ongoing = false))
    }
  }

  def inspectOut = operation.inspectOut andThen {
    case OnOutgoing.Continue(x, p) => OnOutgoing.Continue(x, p)
    case x: OnOutgoing.Stop        => x
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

  private def decrement(x: Operation) = copy(operation = x, retriesLeft = retriesLeft - 1)

  private def reset(x: Operation) = copy(operation = x, retriesLeft = maxRetries)

  private def wrap(x: Operation) = copy(operation = x)
}

private[eventstore] object RetryableOperation {
  def apply(operation: Operation, maxRetries: Int, ongoing: Boolean): RetryableOperation = {
    RetryableOperation(operation, maxRetries, maxRetries, ongoing)
  }
}
