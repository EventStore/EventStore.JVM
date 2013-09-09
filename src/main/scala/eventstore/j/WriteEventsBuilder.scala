package eventstore
package j

import scala.collection.mutable.ListBuffer
import scala.collection.JavaConverters._

/**
 * @author Yaroslav Klymko
 */
class WriteEventsBuilder(val streamId: String) extends Builder[AppendToStream] {
  private val _streamId: EventStream.Id = EventStream(streamId)
  private var _expectedVersion: ExpectedVersion = ExpectedVersion.Any
  private var _events: ListBuffer[EventData] = new ListBuffer()
  private var _requireMaster: Boolean = true


//  def streamId(streamId: String) = set {
//    _streamId = EventStream.Id(streamId)
//  }


  def addEvent(x: EventData) = set {
    _events += x
  }

  def addEvents(xs: java.lang.Iterable[EventData]) = set {
    _events ++= xs.asScala
  }

  def event(x: EventData) = set {
    _events = new ListBuffer()
    addEvent(x)
  }

  def events(xs: java.lang.Iterable[EventData]) = set {
    _events = new ListBuffer()
    addEvents(xs)
  }


  def expectNoStream = set {
    _expectedVersion = ExpectedVersion.NoStream
  }

  def expectAnyVersion = set {
    _expectedVersion = ExpectedVersion.Any
  }

  def expectVersion(x: Int) = set {
    _expectedVersion = ExpectedVersion.Exact(x)
  }


  def requireMaster(x: Boolean) = set {
    _requireMaster = x
  }

  def build(): AppendToStream = AppendToStream(
    streamId = _streamId,
    expectedVersion = _expectedVersion,
    events = Seq(_events: _*),
    requireMaster = _requireMaster)
}
