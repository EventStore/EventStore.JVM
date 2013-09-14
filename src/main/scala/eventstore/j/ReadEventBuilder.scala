package eventstore
package j

import Builder._

/**
 * @author Yaroslav Klymko
 */
class ReadEventBuilder(streamId: String) extends Builder[ReadEvent]
    with ResolveLinkTosSnippet[ReadEventBuilder]
    with RequireMasterSnippet[ReadEventBuilder] {

  protected val _streamId = EventStream(streamId)
  protected var _eventNumber: EventNumber = EventNumber.First

  def eventNumber(x: Int) = set {
    _eventNumber = if (x < 0) EventNumber.Last else EventNumber(x) // TODO duplicate
  }

  def eventNumberFirst = set {
    _eventNumber = EventNumber.First
  }

  def eventNumberLast = set {
    _eventNumber = EventNumber.Last
  }

  def resolveLinkTos(x: Boolean) = resolveLinkTosSnippet.resolveLinkTos(x)

  def requireMaster(x: Boolean) = requireMasterSnippet.requireMaster(x)

  def build = eventstore.ReadEvent(
    streamId = _streamId,
    eventNumber = _eventNumber,
    resolveLinkTos = resolveLinkTosSnippet.value,
    requireMaster = requireMasterSnippet.value)
}
