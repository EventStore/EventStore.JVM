package eventstore
package akka

import scala.util.Try

package object testutil {

  def byteStringInt8(xs: Int*): ByteString =
    ByteString(xs.map(_.toByte).toArray)

  def isES20Series: Boolean =
    sys.env.get("ES_TEST_IS_20_SERIES").flatMap(v => Try(v.toBoolean).toOption).getOrElse(false)

}
