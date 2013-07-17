package eventstore

import OperationResult._

/**
 * @author Yaroslav Klymko
 */
class CreateStreamSpec extends TestConnectionSpec {
  "create stream" should {
    "succeed if doesn't exist" in new CreateStreamScope {
      actor ! createStream
      expectMsg(createStreamCompleted)
    }

    "succeed many times for same id" in new CreateStreamScope {
      actor ! createStream
      expectMsg(createStreamCompleted)

      actor ! createStream
      expectMsg(createStreamCompleted)
    }

    "succeed when supposed to by system, but on your own risk" in new CreateStreamScope {
      override val streamId = "$" + newUuid

      actor ! createStream
      expectMsg(createStreamCompleted)
    }

    "fail if already exists" in new CreateStreamScope {
      actor ! createStream
      expectMsg(createStreamCompleted)

      actor ! createStream
      expectMsgPF() {
        case CreateStreamCompleted(WrongExpectedVersion, Some(_)) => true
      }
    }

    "fail if was deleted" in new CreateStreamScope {
      actor ! createStream
      expectMsg(createStreamCompleted)

      actor ! deleteStream()
      expectMsg(deleteStreamCompleted)

      actor ! createStream
      expectMsgPF() {
        case CreateStreamCompleted(StreamDeleted, Some(_)) => true
      }
    }

    "succeed if metadata is empty" in new CreateStreamScope {
      actor ! createStream.copy(metadata = ByteString.empty)
      expectMsg(createStreamCompleted)
    }
  }


  abstract class CreateStreamScope extends TestConnectionScope
}
