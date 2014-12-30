package eventstore
package operations

import Decision._
import scala.util.Try

private[eventstore] case class RetryableOperation(
    operation: Operation,
    retriesLeft: Int,
    maxRetries: Int,
    disconnected: Boolean) extends Operation {

  def id = operation.id

  def version = operation.version

  def client = operation.client

  def connected(outFunc: OutFunc) = {
    operation.connected(outFunc).map { operation =>
      copy(operation = operation, disconnected = false)
    }
  }

  def clientTerminated() = operation.clientTerminated()

  def connectionLost() = {
    operation.connectionLost().map { operation =>
      copy(operation = operation, disconnected = true)
    }
  }

  def inspectOut = operation.inspectOut andThen { x =>
    x.map { operation => wrap(operation) }
  }

  def inspectIn(in: Try[In]) = operation.inspectIn(in) match {
    case x: Stop                 => x
    case Ignore                  => Ignore
    case Continue(operation, in) => Continue(reset(operation), in)
    case Retry(operation, pack) =>
      if (disconnected) Retry(wrap(operation), pack)
      else if (retriesLeft > 0) Retry(decrement(operation), pack)
      else Stop(new RetriesLimitReachedException(s"Operation $pack reached retries limit: $maxRetries"))
  }

  private def decrement(x: Operation) = copy(operation = x, retriesLeft = retriesLeft - 1)

  private def reset(x: Operation) = copy(operation = x, retriesLeft = maxRetries)

  private def wrap(x: Operation) = copy(operation = x)
}

private[eventstore] object RetryableOperation {
  def apply(operation: Operation, maxRetries: Int, disconnected: Boolean): RetryableOperation = {
    RetryableOperation(operation, maxRetries, maxRetries, disconnected)
  }
}
