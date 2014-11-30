package eventstore
package operations

import NotHandled.{ NotReady, TooBusy }
import tcp.PackOut
import scala.util.{ Try, Success, Failure }
import operations.{ SubscriptionOperation => SO }

class SubscriptionOperationSpec extends OperationSpec {
  val streamId = EventStream.Id("streamId")
  val streams = Seq(EventStream.All, streamId)

  "SubscriptionOperation when subscribing" should {
    "return id equal to correlationId" in foreach(streams) { implicit stream =>
      new SubscribingScope {
        operation.id mustEqual pack.correlationId
      }
    }

    "drop OutFunc on connectionLost" in foreach(streams) { implicit stream =>
      new SubscribingScope {
        val actual = operation.connectionLost()
        actual must beSome
        actual.get.outFunc must beNone
        there were noCallsTo(outFunc, inFunc, client)
      }
    }

    "save new OutFunc on connected and retry" in foreach(streams) { implicit stream =>
      new SubscribingScope {
        val newOutFunc = mock[OutFunc]
        val actual = operation.copy(outFunc = None).connected(newOutFunc)
        actual must beSome
        actual.get.outFunc mustEqual Some(newOutFunc)
        there were noCallsTo(outFunc, inFunc, client)
        there was one(newOutFunc).apply(pack)
      }
    }

    "replace OutFunc on connected and retry" in foreach(streams) { implicit stream =>
      new SubscribingScope {
        val newOutFunc = mock[OutFunc]
        val actual = operation.copy(outFunc = None).connected(newOutFunc)
        actual must beSome
        actual.get.outFunc mustEqual Some(newOutFunc)
        there were noCallsTo(outFunc, inFunc, client)
        there was one(newOutFunc).apply(pack)
      }
    }

    "unsubscribe on clientTerminated" in foreach(streams) { implicit stream =>
      new SubscribingScope {
        operation.clientTerminated()
        there was one(outFunc).apply(PackOut(Unsubscribe, pack.correlationId, pack.credentials))
        there were noCallsTo(inFunc, client)
      }
    }

    "ignore out messages except Unsubscribe" in foreach(streams) { implicit stream =>
      new SubscribingScope {
        val outs = Seq(
          subscribeTo,
          ReadEvent(streamId),
          ReadStreamEvents(streamId),
          ReadAllEvents(),
          Ping,
          HeartbeatRequest,
          Authenticate,
          ScavengeDatabase)
        foreach(outs) { x => operation.inspectOut.isDefinedAt(x) must beFalse }

        operation.inspectOut(Unsubscribe) must beNone

        there was one(outFunc).apply(unsubscribe)
        there was one(inFunc).apply(Try(Unsubscribed))
        there were noCallsTo(client)
      }
    }

    "forward new events" in foreach(streams) { implicit stream =>
      new SubscribingScope {
        val event = eventAppeared(streamId)
        val result = operation.inspectIn(Success(event))
        there was one(inFunc).apply(Success(event))
        there were noCallsTo(outFunc, client)
        result must beSome(operation)
      }
    }

    "stop on unexpected events" in new SubscribingScope()(streamId) {
      val event = eventAppeared(EventStream.Id("unexpected"))
      operation.inspectIn(Success(event)) must beNone
      thereWasStop[CommandNotExpectedException]
    }

    "become subscribed on success" in foreach(streams) { implicit stream =>
      new SubscribingScope {
        val result = operation.inspectIn(Success(subscribeCompleted))
        result must beSome
        result.get must beAnInstanceOf[SubscriptionOperation.Subscribed]
        result.get.version mustEqual 1
        there were noCallsTo(client)
      }
    }

    "stay on success if disconnected" in foreach(streams) { implicit stream =>
      new SubscribingScope {
        val disconnected = operation.copy(outFunc = None)
        val result = disconnected.inspectIn(Success(subscribeCompleted))
        there were noCallsTo(outFunc, inFunc, client)
        result must beSome(disconnected)
      }
    }

    "stop on expected error" in foreach(streams) { implicit stream =>
      new SubscribingScope {
        operation.inspectIn(Failure(SubscriptionDropped.AccessDenied)) must beNone
        thereWasStop[AccessDeniedException]
      }
    }

    "retry on NotReady" in foreach(streams) { implicit stream =>
      new SubscribingScope {
        operation.inspectIn(Failure(NotHandled(NotReady))) must beSome
        thereWasRetry()
      }
    }

    "retry on TooBusy" in foreach(streams) { implicit stream =>
      new SubscribingScope {
        operation.inspectIn(Failure(NotHandled(TooBusy))) must beSome
        thereWasRetry()
      }
    }

    "stop on OperationTimedOut" in foreach(streams) { implicit stream =>
      new SubscribingScope {
        operation.inspectIn(Failure(OperationTimedOut)) must beNone
        thereWasStop(OperationTimeoutException(pack))
      }
    }

    "stop on NotAuthenticated" in foreach(streams) { implicit stream =>
      new SubscribingScope {
        operation.inspectIn(Failure(NotAuthenticated)) must beNone
        thereWasStop[NotAuthenticatedException]
      }
    }

    "stop on BadRequest" in foreach(streams) { implicit stream =>
      new SubscribingScope {
        operation.inspectIn(Failure(BadRequest)) must beNone
        thereWasStop[ServerErrorException]
      }
    }

    "stop on unexpected" in foreach(streams) { implicit stream =>
      new SubscribingScope {
        val unexpected = stream match {
          case _: EventStream.Id => SubscribeToAllCompleted(0)
          case _                 => SubscribeToStreamCompleted(0)
        }
        operation.inspectIn(Success(unexpected))
        thereWasStop[CommandNotExpectedException]
      }
    }

    "stop on unexpected error" in foreach(streams) { implicit stream =>
      new SubscribingScope {
        operation.inspectIn(Failure(ReadEventError.StreamDeleted))
        thereWasStop[CommandNotExpectedException]
      }
    }

    "return 0 for version" in foreach(streams) { implicit stream =>
      new SubscribingScope {
        operation.version mustEqual 0
        val o1 = operation.connectionLost().get
        o1.version mustEqual 0
        val o2 = operation.connected(outFunc).get
        o2.version mustEqual 0
      }
    }
  }

  "SubscriptionOperation when subscribed" should {
    "return id equal to correlationId" in foreach(streams) { implicit stream =>
      new SubscribedScope {
        operation.id mustEqual pack.correlationId
      }
    }

    "become subscribing on connectionLost" in foreach(streams) { implicit stream =>
      new SubscribedScope {
        val actual = operation.connectionLost()
        actual must beSome
        val subscribing = actual.get
        subscribing must beAnInstanceOf[SubscriptionOperation.Subscribing]
        subscribing.outFunc must beNone
        actual.get.version mustEqual 1
        there were noCallsTo(outFunc, inFunc, client)
      }
    }

    "become subscribing on connected and retry" in foreach(streams) { implicit stream =>
      new SubscribedScope {
        val newOutFunc = mock[OutFunc]
        val actual = operation.connected(newOutFunc)
        actual must beSome
        actual.get.outFunc mustEqual Some(newOutFunc)
        actual.get.version mustEqual 1
        there were noCallsTo(outFunc, inFunc, client)
        there was one(newOutFunc).apply(pack)
      }
    }

    "unsubscribe on clientTerminated" in foreach(streams) { implicit stream =>
      new SubscribedScope {
        operation.clientTerminated()
        there was one(outFunc).apply(PackOut(Unsubscribe, pack.correlationId, pack.credentials))
        there were noCallsTo(inFunc, client)
      }
    }

    "ignore out messages except Unsubscribe" in foreach(streams) { implicit stream =>
      new SubscribedScope {
        val outs = Seq(
          subscribeTo,
          ReadEvent(streamId),
          ReadStreamEvents(streamId),
          ReadAllEvents(),
          Ping,
          HeartbeatRequest,
          Authenticate,
          ScavengeDatabase)
        foreach(outs) { x => operation.inspectOut.isDefinedAt(x) must beFalse }

        val unsubscribing = operation.inspectOut(Unsubscribe)
        unsubscribing must beSome
        unsubscribing.get.version mustEqual 1
        there was one(outFunc).apply(unsubscribe)
        there were noCallsTo(inFunc, client)
      }
    }

    "forward new events" in foreach(streams) { implicit stream =>
      new SubscribedScope {
        val event = eventAppeared(streamId)
        val result = operation.inspectIn(Success(event))
        there was one(inFunc).apply(Success(event))
        there were noCallsTo(outFunc, client)
        result must beSome(operation)
      }
    }

    "stop on unexpected events" in new SubscribedScope()(streamId) {
      val event = eventAppeared(EventStream.Id("unexpected"))
      operation.inspectIn(Success(event)) must beNone
      thereWasStop[CommandNotExpectedException]
    }

    "stop on AccessDenied" in foreach(streams) { implicit stream =>
      new SubscribedScope {
        operation.inspectIn(Failure(SubscriptionDropped.AccessDenied)) must beNone
        thereWasStop[AccessDeniedException]
      }
    }

    "stop on Unsubscribed" in foreach(streams) { implicit stream =>
      new SubscribedScope {
        operation.inspectIn(Success(Unsubscribed)) must beNone
        thereWasStop(Success(Unsubscribed))
      }
    }

    "stop on NotReady" in foreach(streams) { implicit stream =>
      new SubscribedScope {
        operation.inspectIn(Failure(NotHandled(NotReady))) must beNone
        thereWasStop[CommandNotExpectedException]
      }
    }

    "stop on TooBusy" in foreach(streams) { implicit stream =>
      new SubscribedScope {
        operation.inspectIn(Failure(NotHandled(TooBusy))) must beNone
        thereWasStop[CommandNotExpectedException]
      }
    }

    "stop on OperationTimedOut" in foreach(streams) { implicit stream =>
      new SubscribedScope {
        operation.inspectIn(Failure(OperationTimedOut)) must beNone
        thereWasStop[CommandNotExpectedException]
      }
    }

    "stop on NotAuthenticated" in foreach(streams) { implicit stream =>
      new SubscribedScope {
        operation.inspectIn(Failure(NotAuthenticated)) must beNone
        thereWasStop[CommandNotExpectedException]
      }
    }

    "stop on BadRequest" in foreach(streams) { implicit stream =>
      new SubscribedScope {
        operation.inspectIn(Failure(BadRequest)) must beNone
        thereWasStop[CommandNotExpectedException]
      }
    }

    "stop on unexpected" in foreach(streams) { implicit stream =>
      new SubscribedScope {
        operation.inspectIn(Success(Pong))
        thereWasStop[CommandNotExpectedException]
      }
    }

    "stop on unexpected error" in foreach(streams) { implicit stream =>
      new SubscribedScope {
        operation.inspectIn(Failure(ReadEventError.StreamDeleted))
        thereWasStop[CommandNotExpectedException]
      }
    }

    "return 0 for version" in foreach(streams) { implicit stream =>
      new SubscribedScope {
        operation.version mustEqual 0
        val o1 = operation.connectionLost().get
        o1.version mustEqual 1
      }
    }
  }

  "SubscriptionOperation when unsubscribing" should {
    "return id equal to correlationId" in foreach(streams) { implicit stream =>
      new UnsubscribingScope {
        operation.id mustEqual pack.correlationId
      }
    }

    "stop on connectionLost" in foreach(streams) { implicit stream =>
      new UnsubscribingScope {
        operation.connectionLost() must beNone
        thereWasStop(Success(Unsubscribed))
      }
    }

    "stop on connected" in foreach(streams) { implicit stream =>
      new UnsubscribingScope {
        val newOutFunc = mock[OutFunc]
        operation.connected(newOutFunc) must beNone
        thereWasStop(Success(Unsubscribed))
      }
    }

    "stop on clientTerminated" in foreach(streams) { implicit stream =>
      new UnsubscribingScope {
        operation.clientTerminated()
        thereWasStop(Success(Unsubscribed))
      }
    }

    "ignore out messages" in foreach(streams) { implicit stream =>
      new UnsubscribingScope {
        operation.inspectOut mustEqual PartialFunction.empty
        operation.inspectOut.isDefinedAt(Unsubscribe) must beFalse
        there were noCallsTo(outFunc, inFunc, client)
      }
    }

    "stop on success" in foreach(streams) { implicit stream =>
      new UnsubscribingScope {
        operation.inspectIn(Success(Unsubscribed)) must beNone
        thereWasStop(Success(Unsubscribed))
      }
    }

    "stop on expected error" in foreach(streams) { implicit stream =>
      new UnsubscribingScope {
        operation.inspectIn(Failure(SubscriptionDropped.AccessDenied)) must beNone
        thereWasStop[AccessDeniedException]
      }
    }

    "retry on NotReady" in foreach(streams) { implicit stream =>
      new UnsubscribingScope {
        operation.inspectIn(Failure(NotHandled(NotReady))) must beSome
        thereWasRetry()
      }
    }

    "retry on TooBusy" in foreach(streams) { implicit stream =>
      new UnsubscribingScope {
        operation.inspectIn(Failure(NotHandled(TooBusy))) must beSome
        thereWasRetry()
      }
    }

    "stop on OperationTimedOut" in foreach(streams) { implicit stream =>
      new UnsubscribingScope {
        operation.inspectIn(Failure(OperationTimedOut)) must beNone
        thereWasStop(OperationTimeoutException(pack))
      }
    }

    "stop on NotAuthenticated" in foreach(streams) { implicit stream =>
      new UnsubscribingScope {
        operation.inspectIn(Failure(NotAuthenticated)) must beNone
        thereWasStop[CommandNotExpectedException]
      }
    }

    "stop on BadRequest" in foreach(streams) { implicit stream =>
      new UnsubscribingScope {
        operation.inspectIn(Failure(BadRequest)) must beNone
        thereWasStop[ServerErrorException]
      }
    }

    "stop on unexpected" in foreach(streams) { implicit stream =>
      new UnsubscribingScope {
        val unexpected = stream match {
          case x: EventStream.Id => SubscribeToStreamCompleted(0)
          case _                 => SubscribeToAllCompleted(0)
        }
        operation.inspectIn(Success(unexpected))
        thereWasStop[CommandNotExpectedException]
      }
    }

    "stop on unexpected error" in foreach(streams) { implicit stream =>
      new UnsubscribingScope {
        operation.inspectIn(Failure(ReadEventError.StreamDeleted))
        thereWasStop[CommandNotExpectedException]
      }
    }

    "ignore new events" in foreach(streams) { implicit stream =>
      new UnsubscribingScope {
        val result = operation.inspectIn(Success(eventAppeared(streamId)))
        there were noCallsTo(outFunc, inFunc, client)
        result must beSome(operation)
      }
    }

    "stop on unexpected events" in new SubscribedScope()(streamId) {
      val event = eventAppeared(EventStream.Id("unexpected"))
      operation.inspectIn(Success(event)) must beNone
      thereWasStop[CommandNotExpectedException]
    }

    "return 0 for version" in foreach(streams) { implicit stream =>
      new UnsubscribingScope {
        operation.version mustEqual 0
      }
    }
  }

  private trait SubscriptionScope extends OperationScope {
    def eventAppeared(streamId: EventStream.Id) = {
      val event = EventRecord(streamId, EventNumber.First, EventData("test"))
      val indexedEvent = IndexedEvent(event, Position.First)
      StreamEventAppeared(indexedEvent)
    }

    def stream: EventStream

    lazy val streamId = stream match {
      case x: EventStream.Id => x
      case _                 => SubscriptionOperationSpec.this.streamId
    }
  }

  private abstract class SubscribingScope(implicit val stream: EventStream) extends SubscriptionScope {
    val subscribeTo = SubscribeTo(stream)
    val pack = PackOut(subscribeTo)
    val unsubscribe = PackOut(Unsubscribe, pack.correlationId, pack.credentials)
    val operation = SO.Subscribing(subscribeTo, pack, client, inFunc, Some(outFunc), 0)

    lazy val subscribeCompleted = stream match {
      case x: EventStream.Id => SubscribeToStreamCompleted(0)
      case _                 => SubscribeToAllCompleted(0)
    }
  }

  private abstract class SubscribedScope(implicit val stream: EventStream) extends SubscriptionScope {
    val subscribeTo = SubscribeTo(stream)
    val pack = PackOut(subscribeTo)
    val unsubscribe = PackOut(Unsubscribe, pack.correlationId, pack.credentials)
    val operation = SO.Subscribed(subscribeTo, pack, client, inFunc, outFunc, 0)
  }

  private abstract class UnsubscribingScope(implicit val stream: EventStream) extends SubscriptionScope {
    val subscribeTo = SubscribeTo(stream)
    val pack = PackOut(Unsubscribe)
    val operation = SO.Unsubscribing(stream, pack, client, inFunc, outFunc, 0)
  }
}