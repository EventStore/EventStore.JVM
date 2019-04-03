package eventstore
package core

import java.util.UUID
import java.net.{InetAddress, InetSocketAddress}

private[eventstore] object syntax {

  type Error       = String
  type Attempt[+A] = Either[Error, A]

  implicit class EsInt(val self: Int) extends AnyVal {
    def ::(host: String): InetSocketAddress = new InetSocketAddress(host, self)
    def ::(host: InetAddress): InetSocketAddress = new InetSocketAddress(host, self)
  }

  implicit class StringOps(val self: String) extends AnyVal {
    def uuid: Uuid = UUID.fromString(self)
  }

  implicit class AttemptOps[T](val attempt: Attempt[T]) extends AnyVal {
    def unsafe: T = attempt.leftMap(new IllegalArgumentException(_)).orError
  }

  implicit class EitherOps[A, B](val either: Either[A, B]) extends AnyVal {

    def orError(implicit ev: A <:< Throwable): B = either.fold(throw _, identity)
    def attempt(implicit ev: A <:< Throwable): Attempt[B] = either.leftMap(_.getMessage)

    def leftMap[C](f: A => C): Either[C, B] = either match {
      case Left(a)      => Left(f(a))
      case r @ Right(_) => r.asInstanceOf[Either[C, B]]
    }
  }

  implicit class BooleanOps(val b: Boolean) extends AnyVal {
    @inline def fold[A](t: => A, f: => A): A = if (b) t else f
  }

}
