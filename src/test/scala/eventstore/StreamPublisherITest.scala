package eventstore

import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.stream.testkit.scaladsl.TestSink

import scala.concurrent.duration._

class StreamPublisherITest extends TestConnection {
  implicit val materializer = ActorMaterializer()

  "StreamPublisher" should {
    "subscribe to stream" in new Scope {
      val events = appendMany(3)
      val src = source().map { _.data }
      src.runWith(TestSink.probe[EventData])
        .request(events.size.toLong)
        .expectNextN(events)
    }

    "subscribe to stream from number" in new Scope {
      val events = appendMany(3) drop 1
      val src = source(Some(EventNumber.First)).map { _.data }
      src.runWith(TestSink.probe[EventData])
        .request(events.size.toLong)
        .expectNextN(events)
    }

    "subscribe to non-existing stream" in new Scope {
      val src = source().map { _.data }
      val events = appendMany(3)
      src.runWith(TestSink.probe[EventData])
        .request(events.size.toLong)
        .expectNextN(events)
    }

    "subscribe to non-existing stream from number" in new Scope {
      val src = source(Some(EventNumber.First)).map { _.data }
      val events = appendMany(3).drop(1)
      src.runWith(TestSink.probe[EventData])
        .request(events.size.toLong)
        .expectNextN(events)
    }

    "subscribe to soft deleted stream" in new Scope {
      appendEventToCreateStream()
      deleteStream(hard = false)
      val events = appendMany(size = 2)
      val src = source(Some(EventNumber.First)) map { _.data }
      src.runWith(TestSink.probe[EventData])
        .request(events.size.toLong)
        .expectNextN(events)
    }

    "subscribe to soft deleted long stream" in new Scope {
      appendEventToCreateStream()
      appendMany(size = 20)
      deleteStream(hard = false)
      val events = appendMany(size = 2)
      val src = source(Some(EventNumber.First)) map { _.data }

      src.runWith(TestSink.probe[EventData])
        .request(events.size.toLong)
        .expectNextN(events)
    }

    "subscribe to soft deleted empty stream" in new Scope {
      appendEventToCreateStream()
      deleteStream(hard = false)
      val src = source(Some(EventNumber.First)) map { _.data }
      src.runWith(TestSink.probe[EventData])
        .request(1)
        .expectNoMsg(300.millis)
    }

    "subscribe to truncated stream" in new Scope {
      appendEventToCreateStream()
      truncateStream(EventNumber.Exact(1))
      val events = appendMany(size = 2)
      val src = source(Some(EventNumber.First)) map { _.data }
      src.runWith(TestSink.probe[EventData])
        .request(events.size.toLong)
        .expectNextN(events)
    }

    "subscribe to truncated long stream" in new Scope {
      appendEventToCreateStream()
      appendMany(size = 20)
      truncateStream(EventNumber.Exact(21))
      val events = appendMany(size = 2)
      val src = source(Some(EventNumber.First)) map { _.data }
      src.runWith(TestSink.probe[EventData])
        .request(events.size.toLong)
        .expectNextN(events)
    }
  }

  "StreamPublisher finite" should {
    "subscribe to stream" in new Scope {
      val events = appendMany(3)
      val src = source(infinite = false).map { _.data }
      src.runWith(TestSink.probe[EventData])
        .request(events.size.toLong)
        .expectNextN(events)
        .expectComplete()
    }

    "subscribe to stream from number" in new Scope {
      val events = appendMany(3) drop 1
      val src = source(Some(EventNumber.First), infinite = false).map { _.data }
      src.runWith(TestSink.probe[EventData])
        .request(events.size.toLong)
        .expectNextN(events)
        .expectComplete()
    }

    "subscribe to non-existing stream" in new Scope {
      val src = source(infinite = false).map { _.data }
      src.runWith(TestSink.probe[EventData])
        .request(1)
        .expectComplete()
    }

    "subscribe to non-existing stream from number" in new Scope {
      val src = source(Some(EventNumber.First), infinite = false).map { _.data }
      src.runWith(TestSink.probe[EventData])
        .request(1)
        .expectComplete()
    }

    "subscribe to soft deleted stream" in new Scope {
      appendEventToCreateStream()
      deleteStream(hard = false)
      val events = appendMany(size = 2)
      val src = source(Some(EventNumber.First), infinite = false) map { _.data }
      src.runWith(TestSink.probe[EventData])
        .request(events.size.toLong)
        .expectNextN(events)
        .request(1)
        .expectComplete()
    }

    "subscribe to soft deleted long stream" in new Scope {
      appendEventToCreateStream()
      appendMany(size = 20)
      deleteStream(hard = false)
      val events = appendMany(size = 2)
      val src = source(Some(EventNumber.First), infinite = false) map { _.data }

      src.runWith(TestSink.probe[EventData])
        .request(events.size.toLong)
        .expectNextN(events)
        .request(1)
        .expectComplete()
    }

    "subscribe to soft deleted empty stream" in new Scope {
      appendEventToCreateStream()
      deleteStream(hard = false)
      val src = source(Some(EventNumber.First), infinite = false) map { _.data }
      src.runWith(TestSink.probe[EventData])
        .request(1)
        .expectComplete()
    }

    "subscribe to truncated stream" in new Scope {
      appendEventToCreateStream()
      truncateStream(EventNumber.Exact(1))
      val events = appendMany(size = 2)
      val src = source(Some(EventNumber.First), infinite = false) map { _.data }
      src.runWith(TestSink.probe[EventData])
        .request(events.size.toLong)
        .expectNextN(events)
        .request(1)
        .expectComplete()
    }

    "subscribe to truncated long stream" in new Scope {
      appendEventToCreateStream()
      appendMany(size = 20)
      truncateStream(EventNumber.Exact(21))
      val events = appendMany(size = 2)
      val src = source(Some(EventNumber.First), infinite = false) map { _.data }
      src.runWith(TestSink.probe[EventData])
        .request(events.size.toLong)
        .expectNextN(events)
        .request(1)
        .expectComplete()
    }
  }

  private trait Scope extends TestConnectionScope {
    val connection = new EsConnection(actor, system)

    def source(eventNumber: Option[EventNumber] = None, infinite: Boolean = true) = {
      Source.fromPublisher(connection.streamPublisher(streamId, eventNumber, infinite = infinite, readBatchSize = 10))
    }
  }
}
