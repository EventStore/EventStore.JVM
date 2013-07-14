package eventstore.client

import OperationResult._

/**
 * @author Yaroslav Klymko
 */
class DeleteStreamSpec extends TestConnectionSpec {
  "delete stream" should {
    "succeed if doesn't exist when passed ANY for expected version" in new DeleteStreamScope {
      actor ! deleteStream(AnyVersion)
      expectMsg(deleteStreamCompleted)
    }

    "fail if doesn't exist and invalid expect version" in new DeleteStreamScope {
      actor ! deleteStream(EmptyStream)
      expectMsgPF() {
        case DeleteStreamCompleted(WrongExpectedVersion, Some(_)) => true
      }

      actor ! deleteStream(Version(1))
      expectMsgPF() {
        case DeleteStreamCompleted(WrongExpectedVersion, Some(_)) => true
      }
    }

    "succeed if correct expected version" in new DeleteStreamScope {
      actor ! createStream
      expectMsg(createStreamCompleted)

      actor ! deleteStream(EmptyStream)
      expectMsg(deleteStreamCompleted)
    }

    "succeed if any expected version" in new DeleteStreamScope {
      actor ! createStream
      expectMsg(createStreamCompleted)

      actor ! deleteStream(AnyVersion)
      expectMsg(deleteStreamCompleted)
    }

    "fail if invalid expected version" in new DeleteStreamScope {
      actor ! createStream
      expectMsg(createStreamCompleted)

      actor ! deleteStream(NoStream)
      expectMsgPF() {
        case DeleteStreamCompleted(WrongExpectedVersion, Some(_)) => true
      }

      actor ! deleteStream(Version(1))
      expectMsgPF() {
        case DeleteStreamCompleted(WrongExpectedVersion, Some(_)) => true
      }
    }

    "fail if already deleted" in new DeleteStreamScope {
      actor ! createStream
      expectMsg(createStreamCompleted)

      actor ! deleteStream(EmptyStream)
      expectMsg(deleteStreamCompleted)

      actor ! deleteStream(EmptyStream)
      expectMsgPF() {
        case DeleteStreamCompleted(StreamDeleted, Some(_)) => true
      }

      actor ! deleteStream(NoStream)
      expectMsgPF() {
        case DeleteStreamCompleted(StreamDeleted, Some(_)) => true
      }

      actor ! deleteStream(AnyVersion)
      expectMsgPF() {
        case DeleteStreamCompleted(StreamDeleted, Some(_)) => true
      }

      actor ! deleteStream(Version(1))
      expectMsgPF() {
        case DeleteStreamCompleted(StreamDeleted, Some(_)) => true
      }
    }
  }

  abstract class DeleteStreamScope extends TestConnectionScope
}
