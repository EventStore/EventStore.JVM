package eventstore
package util

object ByteStringToString {
  def apply(x: ByteString): String = {
    val bytes = if (x.size <= 10) x.mkString(",")
    else x.take(9).mkString("", ",", ",..")
    s"ByteString($bytes)"
  }
}