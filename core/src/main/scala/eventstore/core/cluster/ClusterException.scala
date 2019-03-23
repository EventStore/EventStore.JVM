package eventstore
package core
package cluster

@SerialVersionUID(1L)
final case class ClusterException(message: String, cause: Option[Throwable]) extends EsException(message, cause)
object ClusterException {
  def apply(message: String): ClusterException                = ClusterException(message, None)
  def apply(message: String, th: Throwable): ClusterException = ClusterException(message, Option(th))
}