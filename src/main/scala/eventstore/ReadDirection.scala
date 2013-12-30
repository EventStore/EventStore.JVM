package eventstore

sealed trait ReadDirection

object ReadDirection {
  case object Forward extends ReadDirection
  case object Backward extends ReadDirection
}