package eventstore
package cluster

class ClusterException(message: String, val cause: Option[Throwable]) extends EsException(message, cause.orNull) {
  def this(message: String) = this(message, None)

  override def toString = cause match {
    case Some(th) => s"ClusterException($message, $th)"
    case None     => s"ClusterException($message)"
  }
}