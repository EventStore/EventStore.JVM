package eventstore
package j

import Builder._

class SubscribeToBuilder extends Builder[SubscribeTo] with ResolveLinkTosSnippet[SubscribeToBuilder] {
  protected var _stream: EventStream = null

  def toAll: SubscribeToBuilder = set {
    _stream = EventStream.All
  }

  def toStream(streamId: String): SubscribeToBuilder = set {
    _stream = EventStream(streamId)
  }

  override def resolveLinkTos(x: Boolean): SubscribeToBuilder = super.resolveLinkTos(x)

  def build: SubscribeTo = SubscribeTo(
    stream = _stream,
    resolveLinkTos = _resolveLinkTos)
}