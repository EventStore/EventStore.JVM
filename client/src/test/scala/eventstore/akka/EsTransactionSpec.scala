package eventstore
package akka

import java.util.concurrent.TimeoutException
import scala.concurrent.duration._
import scala.concurrent.Future
import _root_.akka.actor.Status.Failure
import _root_.akka.util.Timeout
import TransactionActor._

class EsTransactionSpec extends ActorSpec {

  "EsTransaction.start" should {
    "return timeout exception" in new StartScope {
      start.mustTimeout
    }

    "return error" in new StartScope {
      val future = start
      lastSender ! Failure(exception)
      future mustThrow exception
    }

    "succeed" in new StartScope {
      val future = start
      lastSender ! TransactionId(transactionId)
      future.await_ mustEqual EsTransactionForActor(transactionId, testActor)
    }
  }

  "EsTransaction.continue.write" should {
    "return timeout exception" in new ContinueScope {
      write.mustTimeout
    }

    "return error" in new ContinueScope {
      val future = write
      lastSender ! Failure(exception)
      future mustThrow exception
    }

    "succeed" in new ContinueScope {
      val future = write
      lastSender ! WriteCompleted
      future.await_ must beEqualTo(())
    }
  }

  "EsTransaction.continue.commit" should {
    "return timeout exception" in new ContinueScope {
      commit.mustTimeout
    }

    "return error" in new ContinueScope {
      val future = commit
      lastSender ! Failure(exception)
      future mustThrow exception
    }

    "succeed" in new ContinueScope {
      val future = commit
      lastSender ! Commit
      future.await_ must beEqualTo(())
    }
  }

  trait StartScope extends ActorScope {
    val transactionId = 0L
    implicit val timeout = Timeout(1.second)
    val exception = new AccessDeniedException("test")

    def start = {
      val future = EsTransaction.start(testActor)
      expectMsg(GetTransactionId)
      future
    }
  }

  trait ContinueScope extends StartScope {
    val transaction = EsTransaction.continue(transactionId, testActor)

    def commit = {
      val future = transaction.commit()
      expectMsg(Commit)
      future
    }

    def write = {
      val events = List(EventData("test"))
      val future = transaction.write(events)
      expectMsg(Write(events))
      future
    }
  }

  implicit class RichFuture(self: Future[_]) {
    def mustTimeout = {
      self.await_(1.second) must throwA[TimeoutException]
    }

    def mustThrow(e: Throwable) = {
      self.await_(3.seconds) must throwAn(e)
    }
  }
}