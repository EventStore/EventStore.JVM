package eventstore
package j

import Builder._

class TransactionCommitBuilder(transactionId: Long) extends Builder[TransactionCommit]
    with RequireMasterSnippet[TransactionCommitBuilder] {

  override def performOnAnyNode: TransactionCommitBuilder = super.performOnAnyNode
  override def performOnMasterOnly: TransactionCommitBuilder = super.performOnMasterOnly
  override def requireMaster(x: Boolean): TransactionCommitBuilder = super.requireMaster(x)

  def build: TransactionCommit = TransactionCommit(
    transactionId = transactionId,
    requireMaster = _requireMaster
  )
}