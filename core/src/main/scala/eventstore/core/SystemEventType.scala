package eventstore
package core

object SystemEventType {
  val streamDeleted: String   = s"$$streamDeleted"
  val statsCollection: String = "$statsCollected"
  val linkTo: String          = "$>"
  val metadata: String        = s"$$metadata"
  val userCreated: String     = "$UserCreated"
  val userUpdated: String     = "$UserUpdated"
  val passwordChanged: String = "$PasswordChanged"
}