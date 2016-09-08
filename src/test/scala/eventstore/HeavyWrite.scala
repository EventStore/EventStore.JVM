package eventstore

import scala.compat.Platform
import scala.concurrent._
import scala.concurrent.duration._

class HeavyWrite extends util.ActorSpec {
  "eventstore-client" should {

    "survive heavy write" in new ActorScope {

      val events = 100
      val writes = 1000
      val times = 20
      val testName = HeavyWrite.this.getClass.getSimpleName
      val connection = EventStoreExtension(system).connection

      val results = for { n <- 0 until times } yield {

        val futures = for { _ <- 0 until writes } yield {
          val writeEvents = WriteEvents(EventStream.Id(randomUuid.toString), List.fill(events)(EventData(testName)))
          connection.future(writeEvents)
        }

        val start = Platform.currentTime
        Await.result(Future.sequence(futures), 10.seconds)
        val duration = Platform.currentTime - start
        duration
      }

      val average = results.sum / times
      println(s"average: $average ms")
      average should beLessThan(1000L)
    }
  }
}
