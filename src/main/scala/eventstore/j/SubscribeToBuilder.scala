package eventstore
package j

import Builder._

class SubscribeToBuilder extends Builder[SubscribeTo] with ResolveLinkTosSnippet[SubscribeToBuilder] {
  var _stream: EventStream = null

  def toAll() = set {
    _stream = EventStream.All
  }

  def toStream(streamId: String) = set {
    _stream = EventStream(streamId)
  }

  def resolveLinkTos(x: Boolean) = ResolveLinkTosSnippet.resolveLinkTos(x)

  def build = SubscribeTo(
    stream = _stream,
    resolveLinkTos = ResolveLinkTosSnippet.value)
}