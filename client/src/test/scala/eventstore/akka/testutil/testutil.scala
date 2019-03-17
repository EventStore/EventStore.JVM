package eventstore
package akka

package object testutil {

  def byteStringInt8(xs: Int*): ByteString =
    ByteString(xs.map(_.toByte).toArray)

}
