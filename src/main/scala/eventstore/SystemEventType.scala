package eventstore

object SystemEventType {
  val streamDeleted = "$streamDeleted"
  val statsCollection = "$statsCollected"
  val linkTo = "$>"
  val metadata = "$metadata"
  val userCreated = "$UserCreated"
  val userUpdated = "$UserUpdated"
  val passwordChanged = "$PasswordChanged"
}