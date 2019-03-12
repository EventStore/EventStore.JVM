package eventstore
package util

object uuid {

  def randomUuid: Uuid = java.util.UUID.randomUUID()

}
