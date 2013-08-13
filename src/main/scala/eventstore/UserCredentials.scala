package eventstore

/**
 * @author Yaroslav Klymko
 */
case class UserCredentials(login: String, password: String) {
  require(login != null && login.nonEmpty, s"login must be not null and not empty, but is '$login'")
  require(password != null && password.nonEmpty, s"password must be not null and not empty, but is '$password'")

  override def toString = s"UserCredentials($login,***)"
}

object UserCredentials {
  val defaultAdmin = UserCredentials("admin", "changeit")
}