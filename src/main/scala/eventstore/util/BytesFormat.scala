package eventstore.util

import akka.util.{ ByteString, ByteIterator, ByteStringBuilder }
import annotation.implicitNotFound

@implicitNotFound(msg = "Cannot find BytesReader or BytesFormat type class for ${T}")
trait BytesReader[T] {
  def read(bi: ByteIterator): T
  def read(bs: ByteString): T = read(bs.iterator)
}

@implicitNotFound(msg = "Cannot find BytesWriter or BytesFormat type class for ${T}")
trait BytesWriter[T] {
  def write(x: T, builder: ByteStringBuilder): Unit

  def toByteString(x: T): ByteString = {
    val builder = ByteString.newBuilder
    write(x, builder)
    builder.result()
  }
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
}