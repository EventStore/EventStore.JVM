package object eventstore {
  type Uuid = java.util.UUID
  val ByteString = akka.util.ByteString
  type ByteString = akka.util.ByteString

  def randomUuid: Uuid = java.util.UUID.randomUUID()

  val MaxBatchSize = 10000
}
