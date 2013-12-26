package eventstore
package j

import Builder._

class TransactionCommitBuilder(transactionId: Long) extends Builder[TransactionCommit]
    with RequireMasterSnippet[TransactionCommitBuilder] {

  def requireMaster(x: Boolean): TransactionCommitBuilder = RequireMasterSnippet.requireMaster(x)

  def build: TransactionCommit = TransactionCommit(
    transactionId = transactionId,
    requireMaster = RequireMasterSnippet.value)
}