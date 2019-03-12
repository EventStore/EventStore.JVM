package eventstore

import java.util.UUID
import java.net.{InetAddress, InetSocketAddress}

package object syntax {

  type Error       = String
  type Attempt[+A] = Either[Error, A]

  implicit class EsInt(val self: Int) extends AnyVal {
    def ::(host: String): InetSocketAddress = new InetSocketAddress(host, self)
    def ::(host: InetAddress): InetSocketAddress = new InetSocketAddress(host, self)
  }

  implicit class EsString(val self: String) extends AnyVal {
    def uuid: Uuid = UUID.fromString(self)
  }

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
