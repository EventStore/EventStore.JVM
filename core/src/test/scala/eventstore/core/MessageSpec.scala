package eventstore
package core

import org.specs2.mutable.Specification
import constants.MaxBatchSize
import util.uuid.randomUuid
import PersistentSubscription._
import TestData._

class MessageSpec extends Specification {

  "TransactionStartCompleted" should {

    "throw exception if transactionId < 0" in {
      TransactionStartCompleted(-1) must throwAn[IllegalArgumentException]
    }
  }

  "TransactionWrite" should {

    "throw exception if transactionId < 0" in {
      TransactionWrite(-1, Nil, requireMaster) must throwAn[IllegalArgumentException]
    }
  }

  "TransactionWriteCompleted" should {

    "throw exception if transactionId < 0" in {
      TransactionWriteCompleted(-1) must throwAn[IllegalArgumentException]
    }
  }

  "TransactionCommit" should {

    "throw exception if transactionId < 0" in {
      TransactionCommit(-1, requireMaster) must throwAn[IllegalArgumentException]
    }
  }

  "TransactionCommitCompleted" should {

    "throw exception if transactionId < 0" in {
      TransactionCommitCompleted(-1, None, None) must throwAn[IllegalArgumentException]
    }
  }

  "ReadStreamEvents" should {

    "throw exception if maxCount <= 0" in {
      readStreamEvents(EventStream.Id("test"), maxCount = 0) must throwAn[IllegalArgumentException]
    }

    "throw exception if maxCount > MaxBatchSize" in {
      readStreamEvents(EventStream.Id("test"), maxCount = MaxBatchSize + 1) must throwAn[IllegalArgumentException]
    }

    "throw exception if reading forward from last event" in {
      readStreamEvents(EventStream.Id("test"), EventNumber.Last) must throwAn[IllegalArgumentException]
    }
  }

  "IdentifyClient" should {

    "throw exception if version < 0" in {
      IdentifyClient(-1, None) must throwAn[IllegalArgumentException]
    }
  }

  "ReadStreamEventsCompleted" should {

    "throw exception if reading forward and nextEventNumber is EventNumber.Last" in {
      ReadStreamEventsCompleted(Nil, EventNumber.Last, EventNumber.Exact(0), endOfStream = false, 0,
        ReadDirection.Forward) must throwAn[IllegalArgumentException]
    }

    "throw exception if events.size > MaxBatchSize" in {
      val events = List.fill(MaxBatchSize + 1)(eventRecord)
      ReadStreamEventsCompleted(events, EventNumber.Exact(1), EventNumber.Exact(0), endOfStream = false, 0,
        ReadDirection.Forward) must throwAn[IllegalArgumentException]
    }
  }

  "ReadAllEvents" should {

    "throw exception if maxCount <= 0" in {
      readAllEvents(maxCount = 0) must throwAn[IllegalArgumentException]
    }

    "throw exception if maxCount > MaxBatchSize" in {
      readAllEvents(maxCount = MaxBatchSize + 1) must throwAn[IllegalArgumentException]
    }
  }

  "ReadAllEventsCompleted" should {

    "throw exception if events.size > MaxBatchSize" in {
      val events = List.fill(MaxBatchSize + 1)(indexedEvent)
      val position = Position.Exact(1)
      ReadAllEventsCompleted(events, position, position, ReadDirection.Forward) must throwAn[IllegalArgumentException]
    }
  }

  "SubscribeToAllCompleted" should {

    "throw exception if lastCommit < 0" in {
      SubscribeToAllCompleted(-1) must throwAn[IllegalArgumentException]
    }
  }

  "SubscribeToStreamCompleted" should {

    "throw exception if lastCommit < 0" in {
      SubscribeToStreamCompleted(-1) must throwAn[IllegalArgumentException]
    }
  }

  "PersistentSubscription" should {

    "Ack" should {
      "throw an exception if subscriptionId is null" in {
        Ack(null, randomUuid :: Nil) must throwAn[IllegalArgumentException]
      }

      "throw an exception if subscriptionId is empty" in {
        Ack("", randomUuid :: Nil) must throwAn[IllegalArgumentException]
      }

      "throw an exception if eventIds is empty" in {
        Ack("test", List[Uuid]()) must throwAn[IllegalArgumentException]
      }
    }

    "Nak" should {
      "throw an exception if subscriptionId is null" in {
        Nak(null, randomUuid :: Nil, Nak.Action.Stop) must throwAn[IllegalArgumentException]
      }

      "throw an exception if subscriptionId is empty" in {
        Nak("", randomUuid :: Nil, Nak.Action.Stop) must throwAn[IllegalArgumentException]
      }

      "throw an exception if eventIds is empty" in {
        Nak("test", List[Uuid](), Nak.Action.Stop) must throwAn[IllegalArgumentException]
      }
    }

    "Create" should {
      "throw an exception if groupName is null" in {
        Create(EventStream.Id("test"), null, pss) must throwAn[IllegalArgumentException]
      }

      "throw an exception if groupName is empty" in {
        Create(EventStream.Id("test"), "", pss) must throwAn[IllegalArgumentException]
      }
    }

    "Update" should {
      "throw an exception if groupName is null" in {
        Update(EventStream.Id("test"), null, pss) must throwAn[IllegalArgumentException]
      }

      "throw an exception if groupName is empty" in {
        Update(EventStream.Id("test"), "", pss) must throwAn[IllegalArgumentException]
      }
    }

    "Delete" should {
      "throw an exception if groupName is null" in {
        Delete(EventStream.Id("test"), null) must throwAn[IllegalArgumentException]
      }

      "throw an exception if groupName is empty" in {
        Delete(EventStream.Id("test"), "") must throwAn[IllegalArgumentException]
      }
    }
  }
}