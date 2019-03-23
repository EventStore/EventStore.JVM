package eventstore
package core

import java.nio.ByteBuffer
import scodec.bits.ByteVector
import syntax._

sealed abstract class ByteString {
  def nonEmpty: Boolean
  def toArray: Array[Byte]
  def toByteBuffer: ByteBuffer
  def utf8String: String
  def show: String
}

object ByteString {

  private final case class DefaultBS(bv: ByteVector) extends ByteString {
    def nonEmpty: Boolean        = bv.nonEmpty
    def toArray: Array[Byte]     = bv.toArray
    def toByteBuffer: ByteBuffer = bv.toByteBuffer
    def utf8String: String       = bv.decodeUtf8.orError

    def show: String = {
      if(bv.isEmpty) "ByteString()"
      else if (bv.length <= 100) s"ByteString(${bv.toSeq.mkString(",")})"
      else s"ByteString(${bv.take(100).toSeq.mkString("", ",", ",..")})"
    }
  }

  val empty: ByteString                       = DefaultBS(ByteVector.empty)
  def apply(content: String): ByteString      = DefaultBS(ByteVector.encodeUtf8(content).orError)
  def apply(content: Array[Byte]): ByteString = DefaultBS(ByteVector(content))
  def apply(content: ByteBuffer): ByteString  = DefaultBS(ByteVector(content))

}