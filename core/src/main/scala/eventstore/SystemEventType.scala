package eventstore

object SystemEventType {
  val streamDeleted: String = "$streamDeleted"
  val statsCollection: String = "$statsCollected"
  val linkTo: String = "$>"
  val metadata: String = "$metadata"
  val userCreated: String = "$UserCreated"
  val userUpdated: String = "$UserUpdated"
  val passwordChanged: String = "$PasswordChanged"
}