package eventstore
package core
package util

object uuid {

  val Zero: Uuid       = java.util.UUID.fromString("00000000-0000-0000-0000-000000000000")
  def randomUuid: Uuid = java.util.UUID.randomUUID()

}
