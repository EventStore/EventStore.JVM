package eventstore
package core

import scodec.bits.{ByteVector => BV}

package object testutil {

  def byteString(xs: Int*): ByteString     = ByteString(xs.foldLeft(BV.empty)((acc, x) => acc ++ BV.fromInt(x)).toArray)
  def byteStringInt8(xs: Int*): ByteString = ByteString(xs.map(_.toByte).toArray)

}