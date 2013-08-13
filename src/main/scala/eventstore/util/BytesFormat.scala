package eventstore.util

import akka.util.{ByteString, ByteIterator, ByteStringBuilder}
import annotation.implicitNotFound

/**
 * @author Yaroslav Klymko
 */
@implicitNotFound(msg = "Cannot find BytesReader or BytesFormat type class for ${T}")
trait BytesReader[T] {
  def read(bi: ByteIterator): T
  def read(bs: ByteString): T = read(bs.iterator)
}

@implicitNotFound(msg = "Cannot find BytesWriter or BytesFormat type class for ${T}")
trait BytesWriter[T] {
  def write(x: T, builder: ByteStringBuilder)

  def toByteString(x: T): ByteString = {
    val builder = ByteString.newBuilder
    write(x, builder)
    builder.result()
  }
}

@implicitNotFound(msg = "Cannot find BytesFormat type class for ${T}")
trait BytesFormat[T] extends BytesReader[T] with BytesWriter[T]

object BytesWriter {
  def write[T](x: T, builder: ByteStringBuilder)(implicit writer: BytesWriter[T]): Unit = writer.write(x, builder)
  def toByteString[T](x: T)(implicit writer: BytesWriter[T]): ByteString = writer.toByteString(x)
}

object BytesReader {
  def read[T](bi: ByteIterator)(implicit reader: BytesReader[T]): T = reader.read(bi) // TODO
  def read[T](bs: ByteString)(implicit reader: BytesReader[T]): T = reader.read(bs) // TODO
}