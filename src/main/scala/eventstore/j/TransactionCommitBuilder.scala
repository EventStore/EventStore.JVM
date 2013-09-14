package eventstore
package j

import Builder._

/**
 * @author Yaroslav Klymko
 */
class TransactionCommitBuilder(transactionId: Long) extends Builder[TransactionCommit]
    with RequireMasterSnippet[TransactionCommitBuilder] {

  def requireMaster(x: Boolean) = requireMasterSnippet.requireMaster(x)

  def build = TransactionCommit(
    transactionId = transactionId,
    requireMaster = requireMasterSnippet.value)
}