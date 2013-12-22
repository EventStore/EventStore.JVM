package eventstore
package util

trait BetterToString {
  self: Product =>
  override def toString = ImproveByteString(self)
}

object ImproveByteString {
  def apply(product: Product): String = {
    val name = product.productPrefix
    val fields = product.productIterator.map {
      case x: ByteString if x.nonEmpty => "ByteString(..)"
      case x                           => x
    }
    s"$name(${fields.mkString(",")}})"
  }
}