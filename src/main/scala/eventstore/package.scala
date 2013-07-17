/**
 * @author Yaroslav Klymko
 */

package object eventstore {
  type Bytes = Array[Byte]
  type ByteBuffer = java.nio.ByteBuffer
  type Uuid = java.util.UUID
  val ByteString = akka.util.ByteString
  type ByteString = akka.util.ByteString
//  val Uuid = java.util.UUID

  def newUuid: Uuid = java.util.UUID.randomUUID()
}
