package eventstore
package core
package operations

import scala.util.Try

private[eventstore] final case class RetryableOperation[C](
  operation:   Operation[C],
  retriesLeft: Int,
  maxRetries:  Int,
  ongoing:     Boolean
) extends Operation[C] {

  def id               = operation.id
  def version          = operation.version
  def client           = operation.client
  def clientTerminated = operation.clientTerminated

  def connected = {
    import OnConnected._
    operation.connected match {
      case Retry(x, p) => Retry(copy(operation = x, ongoing = true), p)
      case x: Stop     => x
    }
  }

  def disconnected = {
    import OnDisconnected._
    operation.disconnected match {
      case Continue(x) => Continue(copy(operation = x, ongoing = false))
      case x: Stop     => x
    }
  }

  def inspectOut = {
    import OnOutgoing._
    operation.inspectOut andThen {
      case Continue(x, p) => Continue(x, p)
      case x: Stop        => x
    }
  }

  def inspectIn(in: Try[In]) = {
    import OnIncoming._
    operation.inspectIn(in) match {
      case x: Stop        => x
      case Ignore         => Ignore
      case Continue(o, i) => Continue(reset(o), i)
      case Retry(o, pack) =>
        if (!ongoing) Retry(wrap(o), pack)
        else if (retriesLeft > 0) Retry(decrement(o), pack)
        else Stop(new RetriesLimitReachedException(s"Operation $pack reached retries limit: $maxRetries"))
    }
  }

  private def decrement(x: Operation[C]) = copy(operation = x, retriesLeft = retriesLeft - 1)
  private def reset(x: Operation[C])     = copy(operation = x, retriesLeft = maxRetries)
  private def wrap(x: Operation[C])      = copy(operation = x)
}

private[eventstore] object RetryableOperation {

  def apply[C](operation: Operation[C], maxRetries: Int, ongoing: Boolean): RetryableOperation[C] = {
    RetryableOperation(operation, maxRetries, maxRetries, ongoing)
  }

}
