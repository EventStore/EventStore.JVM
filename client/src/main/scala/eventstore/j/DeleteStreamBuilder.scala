package eventstore
package j

import Builder.RequireMasterSnippet

class DeleteStreamBuilder(streamId: String) extends Builder[DeleteStream]
    with RequireMasterSnippet[DeleteStreamBuilder] {

  protected val _streamId = EventStream.Id(streamId)
  protected var _hard: Boolean = false
  protected var _expectVersion: ExpectedVersion.Existing = ExpectedVersion.Any

  override def performOnAnyNode: DeleteStreamBuilder = super.performOnAnyNode
  override def performOnMasterOnly: DeleteStreamBuilder = super.performOnMasterOnly
  override def requireMaster(x: Boolean): DeleteStreamBuilder = super.requireMaster(x)

  def expectAnyVersion: DeleteStreamBuilder = expectVersion(ExpectedVersion.Any)

  def expectVersion(x: Long): DeleteStreamBuilder = expectVersion(ExpectedVersion.Exact(x))

  def expectVersion(x: ExpectedVersion.Existing): DeleteStreamBuilder = set {
    _expectVersion = x
  }

  def hard(x: Boolean): DeleteStreamBuilder = set {
    _hard = x
  }

  def softDelete: DeleteStreamBuilder = hard(x = false)

  def hardDelete: DeleteStreamBuilder = hard(x = true)

  def build = DeleteStream(
    streamId = _streamId,
    expectedVersion = _expectVersion,
    hard = _hard,
    requireMaster = _requireMaster
  )
}