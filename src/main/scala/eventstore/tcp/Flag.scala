package eventstore.tcp

import eventstore.UserCredentials

/**
 * @author Yaroslav Klymko
 */
object Flags {
  def apply(credentials: Option[UserCredentials]): Flags =
    credentials.fold(Flag.None)(_ => Flag.Auth)
}

object Flag extends Enumeration {
  val None: Flag = 0x00
  val Auth: Flag = 0x01
}