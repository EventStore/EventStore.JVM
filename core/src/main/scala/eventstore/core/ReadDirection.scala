package eventstore
package core

sealed trait ReadDirection

object ReadDirection {
  @SerialVersionUID(1L) case object Forward extends ReadDirection
  @SerialVersionUID(1L) case object Backward extends ReadDirection
}