package eventstore

@SerialVersionUID(1L) case class UserCredentials(login: String, password: String) {
  require(login != null, "login is null")
  require(login.nonEmpty, "login is empty")
  require(password != null, "password is null")
  require(password.nonEmpty, "password is empty")

  override def toString = s"UserCredentials($login,***)"
}

object UserCredentials {
  val DefaultAdmin: UserCredentials = UserCredentials("admin", "changeit")
}