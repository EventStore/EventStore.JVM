package eventstore

import TransactionActor._
import akka.actor.Status.Failure
import akka.util.Timeout
import java.util.concurrent.TimeoutException
import scala.concurrent.duration._
import scala.concurrent.{ Future, Await }
import util.ActorSpec

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
      future must beEqualTo(EsTransactionForActor(transactionId, testActor)).await
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
      future must beEqualTo(()).await
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
      future must beEqualTo(()).await
    }
  }

  trait StartScope extends ActorScope {
    val transactionId = 0L
    implicit val timeout = Timeout(1.second)
    val exception = EsException(EsError.AccessDenied)

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
      Await.result(self, 1.second) must throwA[TimeoutException]
    }

    def mustThrow(e: Throwable) = {
      Await.result(self, 3.seconds) must throwAn(e)
    }
  }
}