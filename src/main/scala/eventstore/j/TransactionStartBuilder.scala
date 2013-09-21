package eventstore
package j

import Builder._

/**
 * @author Yaroslav Klymko
 */
class TransactionStartBuilder(streamId: String) extends Builder[TransactionStart]
    with ExpectVersionSnippet[TransactionStartBuilder]
    with RequireMasterSnippet[TransactionStartBuilder] {

  private val _streamId = EventStream(streamId)

  def expectNoStream = ExpectedVersionSnippet.expectNoStream
  def expectAnyVersion = ExpectedVersionSnippet.expectAnyVersion
  def expectVersion(x: Int) = ExpectedVersionSnippet.expectVersion(x)

  def requireMaster(x: Boolean) = RequireMasterSnippet.requireMaster(x)

  def build = TransactionStart(
    streamId = _streamId,
    expectedVersion = ExpectedVersionSnippet.value,
    requireMaster = RequireMasterSnippet.value)
}
