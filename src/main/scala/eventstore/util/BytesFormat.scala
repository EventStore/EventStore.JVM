package eventstore
package util

import scodec.bits.ByteVector
import annotation.implicitNotFound

@implicitNotFound(msg = "Cannot find BytesReader or BytesFormat type class for ${T}")
trait BytesReader[+T] { self =>

  def read(bytes: ByteVector): Attempt[ReadResult[T]]

  final def map[B](f: T => B): BytesReader[B]                  = self.read(_).map(_ map f)
  final def flatMap[B](f: T => BytesReader[B]): BytesReader[B] = self.read(_).flatMap(r => f(r.value).read(r.remainder))
}

final case class ReadResult[+A](value: A, remainder: ByteVector) {
  def map[B](f: A => B): ReadResult[B] = ReadResult(f(value), remainder)
}

object ReadResult {
  def apply[A](value: A): ReadResult[A] = ReadResult(value, ByteVector.empty)
}

@implicitNotFound(msg = "Cannot find BytesWriter or BytesFormat type class for ${T}")
trait BytesWriter[T] {
  def write(x: T): ByteVector
}

@implicitNotFound(msg = "Cannot find BytesFormat type class for ${T}")
trait BytesFormat[T] extends BytesReader[T] with BytesWriter[T]

object BytesFormat {
  def apply[T](implicit x: BytesFormat[T]): BytesFormat[T] = x
}

object BytesWriter {
  def apply[T](implicit x: BytesWriter[T]): BytesWriter[T] = x
}

object BytesReader {

  def apply[T](implicit x: BytesReader[T]): BytesReader[T] = x

  def pure[A](a: => A): BytesReader[A] = new BytesReader[A] {
    private lazy val value = a
    def read(bytes: ByteVector) = Right(ReadResult(value, bytes))
  }


  def lift[A](attempt: Attempt[A]): BytesReader[A] = (bytes: ByteVector) => attempt.map(a => ReadResult(a, bytes))

}