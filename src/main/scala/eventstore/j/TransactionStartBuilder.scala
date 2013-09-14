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

  def expectNoStream = expectedVersionSnippet.expectNoStream
  def expectAnyVersion = expectedVersionSnippet.expectAnyVersion
  def expectVersion(x: Int) = expectedVersionSnippet.expectVersion(x)

  def requireMaster(x: Boolean) = requireMasterSnippet.requireMaster(x)

  def build = TransactionStart(
    streamId = _streamId,
    expectedVersion = expectedVersionSnippet.value,
    requireMaster = requireMasterSnippet.value)
}
