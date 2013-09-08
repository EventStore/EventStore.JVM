/**
 * @author Yaroslav Klymko
 */
package object eventstore {
  val Seq = scala.collection.immutable.Seq
  type Seq[T] = scala.collection.immutable.Seq[T]

  type Uuid = java.util.UUID
  val ByteString = akka.util.ByteString
  type ByteString = akka.util.ByteString
  //  val Uuid = java.util.UUID

  def newUuid: Uuid = java.util.UUID.randomUUID()

  val MaxBatchSize = 10000
}
