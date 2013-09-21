package eventstore
package j

import Builder._

/**
 * @author Yaroslav Klymko
 */
class SubscribeToBuilder extends Builder[SubscribeTo] with ResolveLinkTosSnippet[SubscribeToBuilder] {
  var _stream: EventStream = null

  def toAll() = set {
    _stream = EventStream.All
  }

  def toStream(streamId: String) = set {
    _stream = EventStream(streamId)
  }

  def resolveLinkTos(x: Boolean) = resolveLinkTosSnippet.resolveLinkTos(x)

  def build = SubscribeTo(
    stream = _stream,
    resolveLinkTos = resolveLinkTosSnippet.value)
}