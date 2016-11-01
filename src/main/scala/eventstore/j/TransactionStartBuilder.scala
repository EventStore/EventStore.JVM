package eventstore
package j

import Builder._

class TransactionStartBuilder(streamId: String) extends Builder[TransactionStart]
    with ExpectVersionSnippet[TransactionStartBuilder]
    with RequireMasterSnippet[TransactionStartBuilder] {

  protected val _streamId = EventStream.Id(streamId)

  override def expectNoStream: TransactionStartBuilder = super.expectNoStream
  override def expectAnyVersion: TransactionStartBuilder = super.expectAnyVersion
  override def expectVersion(x: Int): TransactionStartBuilder = super.expectVersion(x)
  override def expectVersion(x: ExpectedVersion): TransactionStartBuilder = super.expectVersion(x)

  override def performOnAnyNode: TransactionStartBuilder = super.performOnAnyNode
  override def performOnMasterOnly: TransactionStartBuilder = super.performOnMasterOnly
  override def requireMaster(x: Boolean): TransactionStartBuilder = super.requireMaster(x)

  def build: TransactionStart = TransactionStart(
    streamId = _streamId,
    expectedVersion = _expectVersion,
    requireMaster = _requireMaster
  )
}
