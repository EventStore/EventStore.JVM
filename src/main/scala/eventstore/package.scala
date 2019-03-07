import java.net.{InetAddress, InetSocketAddress}
import java.nio.ByteBuffer
import java.util.UUID
import akka.actor.Actor
import scodec.bits.ByteVector

package object eventstore {

  type Error       = String
  type Attempt[+A] = Either[Error, A]
  type Uuid        = java.util.UUID

  def randomUuid: Uuid = java.util.UUID.randomUUID()

  val MaxBatchSize: Int = 10000

  implicit class EsString(val self: String) extends AnyVal {
    def uuid: Uuid = UUID.fromString(self)
  }

  implicit class EsInt(val self: Int) extends AnyVal {
    def ::(host: String): InetSocketAddress = new InetSocketAddress(host, self)
    def ::(host: InetAddress): InetSocketAddress = new InetSocketAddress(host, self)
  }

  implicit class RichPartialFunction(val self: Actor.Receive) extends AnyVal {
    // workaround for https://issues.scala-lang.org/browse/SI-8861
    def or(pf: Actor.Receive): Actor.Receive = self.orElse[Any, Unit](pf)
  }


  sealed trait ByteString extends Any {
    def nonEmpty: Boolean
    def toArray: Array[Byte]
    def toByteBuffer: ByteBuffer
    def utf8String: String
    def show: String
  }

  object ByteString {

    private case class DefaultBS(bv: ByteVector) extends AnyVal with ByteString {
      def nonEmpty: Boolean        = bv.nonEmpty
      def toArray: Array[Byte]     = bv.toArray
      def toByteBuffer: ByteBuffer = bv.toByteBuffer
      def utf8String: String       = bv.decodeUtf8.orError

      def show: String = {
        if(bv.isEmpty) "ByteString()"
        else if (bv.length <= 10) s"ByteString(${bv.toSeq.mkString(",")})"
        else s"ByteString(${bv.take(9).toSeq.mkString("", ",", ",..")})"
      }
    }

    val empty: ByteString                       = DefaultBS(ByteVector.empty)
    def apply(content: String): ByteString      = DefaultBS(ByteVector.encodeUtf8(content).orError)
    def apply(content: Array[Byte]): ByteString = DefaultBS(ByteVector(content))
    def apply(content: ByteBuffer): ByteString  = DefaultBS(ByteVector(content))

  }

  ///

  private[eventstore] implicit class AttemptOps[T](val attempt: Attempt[T]) extends AnyVal {
    def unsafe: T = attempt.leftMap(new IllegalArgumentException(_)).orError
  }

  private[eventstore] implicit class EitherOps[A, B](val either: Either[A, B]) extends AnyVal {

    def orError(implicit ev: A <:< Throwable): B = either.fold(throw _, identity)

    def leftMap[C](f: A => C): Either[C, B] = either match {
      case Left(a)      => Left(f(a))
      case r @ Right(_) => r.asInstanceOf[Either[C, B]]
    }
  }

}