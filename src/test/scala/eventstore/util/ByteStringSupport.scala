package eventstore
package util

import scodec.bits.ByteVector

object ByteStringSupport {

  def byteString(xs: Int*): ByteString =
    ByteString(xs.foldLeft(ByteVector.empty)((acc, x) => acc ++ ByteVector.fromInt(x)).toArray)

  def byteStringInt8(xs: Int*): ByteString =
    ByteString(xs.map(_.toByte).toArray)
}
