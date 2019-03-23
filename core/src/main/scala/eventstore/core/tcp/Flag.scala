package eventstore
package core
package tcp

object Flags {
  def apply(credentials: Option[UserCredentials]): Flags =
    credentials.fold(Flag.None)(_ => Flag.Auth)
}

object Flag {
  val None: Flag = 0x00
  val Auth: Flag = 0x01
}