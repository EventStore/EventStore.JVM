package eventstore
package j

import Builder._

class TransactionStartBuilder(streamId: String) extends Builder[TransactionStart]
    with ExpectVersionSnippet[TransactionStartBuilder]
    with RequireMasterSnippet[TransactionStartBuilder] {

  private val _streamId = EventStream.Id(streamId)

  def expectNoStream: TransactionStartBuilder = ExpectedVersionSnippet.expectNoStream

  def expectAnyVersion: TransactionStartBuilder = ExpectedVersionSnippet.expectAnyVersion

  def expectVersion(x: Int): TransactionStartBuilder = ExpectedVersionSnippet.expectVersion(x)

  def requireMaster(x: Boolean): TransactionStartBuilder = RequireMasterSnippet.requireMaster(x)

  def build: TransactionStart = TransactionStart(
    streamId = _streamId,
    expectedVersion = ExpectedVersionSnippet.value,
    requireMaster = RequireMasterSnippet.value)
}
