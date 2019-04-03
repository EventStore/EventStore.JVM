package eventstore
package akka

import org.specs2.mutable.Specification
import streams.SourceStageLogic.{ConnectionTerminated, OnLive}
import StreamAdapter._
import StreamAdapter.Fsm._
import StreamAdapterFsmSpec._

class StreamAdapterFsmSpec extends Specification {

  "StreamAdapter.Fsm" should {

    "onInitialized transitions to Running from WaitingOnUpstreamInitialized" in {
      fsm.onInitialized(waitingInitial) mustEqual Result(running, Ack)
    }

    "onInitialized fails to transition when not in WaitingOnUpstreamInitialized" in {
      initialized.map(st => fsm.onInitialized(st) mustEqual unexpected("OnInit", st))
    }

    "onCompleted yields Ack and Stop in any state" in {
      allStates.map(st => fsm.onCompleted(st) mustEqual Result(st, Ack, Stop))
    }

    "onFailure(StageError) yields LogError and Stop" in {
      allStates.map(st => fsm.onFailure(CT)(st) mustEqual Result(st, LogError(CT), Stop))
    }

    "onFailure(Throwable) yields DeliverError and Stop" in {
      allStates.map(st => fsm.onFailure(RE)(st) mustEqual Result(st, DeliverError(RE), Stop))
    }

    "onReady transitions to Running with throttle being reset and Ack" in {
      fsm.onReady(waitingReady) mustEqual Result(Running(Data(batch)), Ack)
    }

    "onReady fails to transition when not in WaitingOnDownstreamReady" in {
      allStates.filterNot(_ == waitingReady).map(st => fsm.onReady(st) mustEqual unexpected("OnDownstreamReady", st))
    }

    "onLive(Now) stays and clears live position with NotifyLive" in {
      fsm.onLive(liveNow)(Running(dataWithLive)) mustEqual Result(Running(dataInit), NotifyLive)
      fsm.onLive(liveNow)(WOTR(dataWithLive))    mustEqual Result(WOTR(dataInit), NotifyLive)
    }

    "onLive(LiveAfter) stays with cleared live position and NotifyLive when event position has been seen" in {

      List(livePos, livePos + 1).map { p =>

        val data   = dataWithLive.withLastOut(p)
        val noLive = data.clearLive
        val run    = fsm.onLive(liveAfter)

        run(Running(data)) mustEqual Result(Running(noLive), NotifyLive)
        run(WOTR(data))    mustEqual Result(WOTR(noLive), NotifyLive)
      }
    }

    "onLive(LiveAfter) stays with updated live position when event position has not been seen" in {

      val lastOut     = livePos - 1
      val dataFstLive = dataInit.withLastOut(lastOut)
      val dataSndLive = dataInit.withLive(lastOut).withLastOut(lastOut)
      val run         = fsm.onLive(liveAfter)

      List(dataFstLive, dataSndLive).map { data =>
        run(Running(data)) mustEqual Result(Running(data.withLive(livePos)))
        run(WOTR(data))    mustEqual Result(WOTR(data.withLive(livePos)))
      }
    }

    "onLive(Now | LiveAfter) fails when in WaitingOnUpstreamInitialized" in {
      List(liveNow, liveAfter).map(l =>
        fsm.onLive(l)(waitingInitial) mustEqual unexpected(s"OnLiveMessage($l)", waitingInitial)
      )
    }

    "onMessage | No Throttle" should {

      "result in Deliver, Ack and NotifyLive when live exists and pos <= event pos" in {
        val expectedData = dataInit.decrementThrottle.withLastOut(eventPos).clearLive
        val expectedInst = List(Deliver(event), Ack, NotifyLive)

        fsm.onMessage(event)(Running(dataWithLive)) mustEqual Result(Running(expectedData), expectedInst)
      }

      "result in Deliver and Ack when no live or live pos > event pos" in {

        val dataLiveGT         = dataInit.withLive(eventPos + 1)
        val expectedDataLiveGT = dataLiveGT.decrementThrottle.withLastOut(eventPos)
        val expectedDataNoLive = dataInit.decrementThrottle.withLastOut(eventPos)
        val expectedInst       = List(Deliver(event), Ack)

        fsm.onMessage(event)(Running(dataLiveGT)) mustEqual Result(Running(expectedDataLiveGT), expectedInst)
        fsm.onMessage(event)(Running(dataInit))   mustEqual Result(Running(expectedDataNoLive), expectedInst)
      }
    }

    "onMessage | Throttle" should {

      "result in Deliver, CheckReady and NotifyLive when live pos <= event pos" in {

        val dataReadyToThrottle = dataWithLive.resetThrottle(1).decrementThrottle
        val expectedData        = dataReadyToThrottle.withLastOut(eventPos).clearLive
        val expectedInst        = List(Deliver(event), CheckReady, NotifyLive)

        fsm.onMessage(event)(Running(dataReadyToThrottle)) mustEqual Result(WOTR(expectedData), expectedInst)
      }

      "result in Deliver and CheckReday when no live or live pos > event pos" in {

        val dataReadyToThrottleNoLive = dataInit.resetThrottle(1).decrementThrottle
        val dataReadyToThrottleLiveGT = dataReadyToThrottleNoLive.withLive(eventPos + 1)
        val expectedDataLiveGT        = dataReadyToThrottleLiveGT.withLastOut(eventPos)
        val expectedDataNoLive        = dataReadyToThrottleNoLive.withLastOut(eventPos)
        val expectedInst              = List(Deliver(event), CheckReady)

        fsm.onMessage(event)(Running(dataReadyToThrottleLiveGT)) mustEqual Result(WOTR(expectedDataLiveGT), expectedInst)
        fsm.onMessage(event)(Running(dataReadyToThrottleNoLive)) mustEqual Result(WOTR(expectedDataNoLive), expectedInst)
      }
    }

    "onMessage" should {

      "fail when not in Running" in {
        allStates.filterNot(_ == running).map(st =>
          fsm.onMessage(event)(st) mustEqual unexpected(s"OnMessage($event, $eventPos)", st))
      }
    }

  }
}

object StreamAdapterFsmSpec {

  val RE   = new RuntimeException("Err")
  val CT   = ConnectionTerminated
  val WOTR = WaitingOnDownstreamReady

  ///

  val batch          = 2
  val eventPos       = 10L
  val livePos        = 2L
  val event          = IndexedEvent(TestData.eventRecord, Position.Exact(eventPos))
  val liveNow        = OnLive.Now
  val liveAfter      = OnLive.LiveAfter(livePos)
  val dataInit       = Data(batch)
  val dataWithLive   = dataInit.withLive(livePos)
  val waitingInitial = WaitingOnUpstreamInitialized
  val running        = Running(dataInit)
  val waitingReady   = WaitingOnDownstreamReady(Data(1).decrementThrottle)
  val allStates      = List(waitingInitial, running, waitingReady)
  val initialized    = List(running, waitingReady)
  val fsm            = Fsm[IndexedEvent](_.position.commitPosition, batch)
}