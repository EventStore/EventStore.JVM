package eventstore

import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.stream.testkit.scaladsl.TestSink

class StreamPublisherITest extends TestConnection {
  implicit val materializer = ActorMaterializer()

  "StreamPublisher" should {
    "subscribe to stream" in new Scope {
      val events = appendMany(3)
      val src = source().map { _.data }
      src.runWith(TestSink.probe[EventData])
        .request(3)
        .expectNextN(events)
    }

    "subscribe to stream from number" in new Scope {
      val events = appendMany(3) drop 1
      val src = source(Some(EventNumber.First)).map { _.data }
      src.runWith(TestSink.probe[EventData])
        .request(2)
        .expectNextN(events)
    }

    "subscribe to non-existing stream" in new Scope {
      val src = source().map { _.data }
      val events = appendMany(3)
      src.runWith(TestSink.probe[EventData])
        .request(3)
        .expectNextN(events)
    }

    "subscribe to non-existing stream from number" in new Scope {
      val src = source(Some(EventNumber.First)).map { _.data }
      val events = appendMany(3).drop(1)
      src.runWith(TestSink.probe[EventData])
        .request(2)
        .expectNextN(events)
    }
  }

  "StreamPublisher finite" should {
    "subscribe to stream" in new Scope {
      val events = appendMany(3)
      val src = source(infinite = false).map { _.data }
      src.runWith(TestSink.probe[EventData])
        .request(3)
        .expectNextN(events)
        .expectComplete()
    }

    "subscribe to stream from number" in new Scope {
      val events = appendMany(3) drop 1
      val src = source(Some(EventNumber.First), infinite = false).map { _.data }
      src.runWith(TestSink.probe[EventData])
        .request(2)
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

  }

  private trait Scope extends TestConnectionScope {
    val connection = new EsConnection(actor, system)

    def source(eventNumber: Option[EventNumber] = None, infinite: Boolean = true) = {
      Source(connection.streamPublisher(streamId, eventNumber, infinite = infinite))
    }
  }
}
