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

  def connected(outFunc: OutFunc) = {
    operation.connected(outFunc).map { operation =>
      copy(operation = operation, disconnected = false)
    }
  }

  def client = operation.client

  def clientTerminated() = operation.clientTerminated()

  def connectionLost() = {
    operation.connectionLost().map { operation =>
      copy(operation = operation, disconnected = true)
    }
  }

  def inspectOut = operation.inspectOut andThen { x =>
    x.map { operation => copy(operation = operation) }
  }

  def inspectIn(in: Try[In]) = operation.inspectIn(in) match {
    case x: Stop                 => x
    case Ignore                  => Ignore
    case Continue(operation, in) => Continue(copy(operation = operation, retriesLeft = retriesLeft - 1), in)
    case Retry(operation, pack) =>
      if (disconnected) Retry(copy(operation = operation), pack)
      else {
        if (retriesLeft > 0) Retry(copy(operation = operation, retriesLeft = retriesLeft - 1), pack)
        else Stop(new RetriesLimitReachedException(s"Operation $pack reached retries limit: $maxRetries"))
      }
  }

  def version = operation.version
}

private[eventstore] object RetryableOperation {
  def apply(operation: Operation, maxRetries: Int, disconnected: Boolean): RetryableOperation = {
    RetryableOperation(operation, maxRetries, maxRetries, disconnected)
  }
}
