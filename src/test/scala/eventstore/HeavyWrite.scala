package eventstore

import scala.compat.Platform
import scala.concurrent._
import scala.concurrent.duration._
import scala.util.Random

class HeavyWrite extends util.ActorSpec {
  "eventstore-client" should {

    "survive heavy write" in new ActorScope {

      val events = 10
      val writes = 1000
      val times = 100
      val testName = HeavyWrite.this.getClass.getSimpleName
      val connection = EventStoreExtension(system).connection
      val bytes = {
        val bytes = new Array[Byte](5 * 1024)
        Random.nextBytes(bytes)
        bytes
      }
      val data = Content(bytes)

      val results = for { n <- 0 until times } yield {

        val futures = for { _ <- 0 until writes } yield {
          val es = List.fill(events)(EventData(testName, data = data))
          val writeEvents = WriteEvents(EventStream.Id(randomUuid.toString), es)
          connection(writeEvents)
        }

        val start = Platform.currentTime
        Await.result(Future.sequence(futures), 10.seconds)
        val duration = Platform.currentTime - start
        duration
      }

      val average = results.sum / times
      val evtPerSec = (events * writes * 1000) / average
      println(s"average: $average ms, events per sec: $evtPerSec")
      average should beLessThan(5000L)
    }
  }
}
