package eventstore
package akka
package streams

import scala.concurrent.duration._
import _root_.akka.stream.testkit.scaladsl.TestSink

class AllStreamsSourceITest extends TestConnection {

  "AllStreamsSource" should {

    "subscribe to all" in new Scope {

      val src = sourceFiltered().map(_.event.data)

      val events1 = appendMany(size = 3, sid = streamIdA)
      val events2 = appendMany(size = 3, sid = streamIdB)
      val events3 = appendMany(size = 3, sid = streamIdC)
      val events4 = appendMany(size = 3, sid = streamIdA)
      val events = events1 ++ events2 ++ events3 ++ events4

      src.runWith(TestSink.probe[EventData])
        .request(events.size.toLong)
        .expectNextN(events)
    }

    "subscribe from number" in new Scope {

      appendMany(20)

      val events = source()
        .runWith(TestSink.probe[IndexedEvent])
        .request(10)
        .expectNextN(10)

      source(Some(events.head.position))
        .runWith(TestSink.probe[IndexedEvent])
        .request(9)
        .expectNextN(events drop 1)

    }

    "subscribe from last" in new Scope {
      appendMany(20)

      val probe = sourceFiltered(Some(Position.Last)).map(_.event.data)
        .runWith(TestSink.probe[EventData])

      probe.request(1).expectNoMessage(300.millis)

      val events = appendMany(2)

      probe.request(events.size.toLong).expectNextN(events)

    }

  }

  "AllStreamsSource finite" should {

    "subscribe to all" in new Scope {

      val events = appendMany(3, sid = streamIdB)

      val src = sourceFiltered(infinite = false).map { _.event.data }

      src.runWith(TestSink.probe[EventData])
        .request(events.size.toLong)
        .expectNextN(events)
        .request(1)
        .expectComplete()
    }

    "subscribe from number" in new Scope {

      appendMany()

      val events = sourceFiltered()
        .runWith(TestSink.probe[IndexedEvent])
        .request(10)
        .expectNextN(10)

      sourceFiltered(Some(events.head.position), infinite = false)
        .runWith(TestSink.probe[IndexedEvent])
        .request(9)
        .expectNextN(events drop 1)
        .request(1)
        .expectComplete()

    }

    "subscribe from last" in new Scope {

      appendMany()

      sourceFiltered(Some(Position.Last), infinite = false)
        .map(_.event.data)
        .runWith(TestSink.probe[EventData])
        .request(1)
        .expectComplete()

    }

  }

  private trait Scope extends TestConnectionScope {
    val connection = new EsConnection(actor, system)

    private val streamIdPrefix = s"${streamId.value}"

    val streamIdA = EventStream.Plain(streamIdPrefix + "_a")
    val streamIdB = EventStream.Plain(streamIdPrefix + "_b")
    val streamIdC = EventStream.Plain(streamIdPrefix + "_c")

    def sourceFiltered(position: Option[Position] = None, infinite: Boolean = true, resolveLinkTos: Boolean = false) =
      source(position, infinite, resolveLinkTos).filter(_.event.streamId.value.startsWith(streamIdPrefix))

    def source(position: Option[Position] = None, infinite: Boolean = true, resolveLinkTos: Boolean = false) = {
      connection.allStreamsSource(resolveLinkTos, position, infinite = infinite, readBatchSize = 10)
    }
  }
}