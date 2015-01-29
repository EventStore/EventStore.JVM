package eventstore

import org.specs2.mock.Mockito
import org.specs2.mutable.Specification

class MessageSpec extends Specification with Mockito {

  "TransactionStartCompleted" should {
    "throw exception if transactionId < 0" in {
      TransactionStartCompleted(-1) must throwAn[IllegalArgumentException]
    }
  }

  "TransactionWrite" should {
    "throw exception if transactionId < 0" in {
      TransactionWrite(-1, Nil) must throwAn[IllegalArgumentException]
    }
  }

  "TransactionWriteCompleted" should {
    "throw exception if transactionId < 0" in {
      TransactionWriteCompleted(-1) must throwAn[IllegalArgumentException]
    }
  }

  "TransactionCommit" should {
    "throw exception if transactionId < 0" in {
      TransactionCommit(-1) must throwAn[IllegalArgumentException]
    }
  }

  "TransactionCommitCompleted" should {
    "throw exception if transactionId < 0" in {
      TransactionCommitCompleted(-1) must throwAn[IllegalArgumentException]
    }
  }

  "ReadStreamEvents" should {
    "throw exception if maxCount <= 0" in {
      ReadStreamEvents(EventStream.Id("test"), maxCount = 0) must throwAn[IllegalArgumentException]
    }

    "throw exception if maxCount > MaxBatchSize" in {
      ReadStreamEvents(EventStream.Id("test"), maxCount = MaxBatchSize + 1) must throwAn[IllegalArgumentException]
    }

    "throw exception if reading forward from last event" in {
      ReadStreamEvents(EventStream.Id("test"), EventNumber.Last) must throwAn[IllegalArgumentException]
    }
  }

  "ReadStreamEventsCompleted" should {
    "throw exception if reading forward and nextEventNumber is EventNumber.Last" in {
      ReadStreamEventsCompleted(Nil, EventNumber.Last, EventNumber.Exact(0), endOfStream = false, 0,
        ReadDirection.Forward) must throwAn[IllegalArgumentException]
    }

    "throw exception if events.size > MaxBatchSize" in {
      val event = mock[Event]
      val events = List.fill(MaxBatchSize + 1)(event)
      ReadStreamEventsCompleted(events, EventNumber.Exact(1), EventNumber.Exact(0), endOfStream = false, 0,
        ReadDirection.Forward) must throwAn[IllegalArgumentException]
    }
  }

  "ReadAllEvents" should {
    "throw exception if maxCount <= 0" in {
      ReadAllEvents(maxCount = 0) must throwAn[IllegalArgumentException]
    }

    "throw exception if maxCount > MaxBatchSize" in {
      ReadAllEvents(maxCount = MaxBatchSize + 1) must throwAn[IllegalArgumentException]
    }
  }

  "ReadAllEventsCompleted" should {
    "throw exception if events.size > MaxBatchSize" in {
      val event = mock[IndexedEvent]
      val events = List.fill(MaxBatchSize + 1)(event)
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
}
