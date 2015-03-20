package eventstore
package operations

import eventstore.NotHandled.{ NotReady, TooBusy }
import eventstore.tcp.PackOut
import eventstore.operations.OnIncoming._
import eventstore.operations.{ SubscriptionOperation => SO }
import scala.util.{ Try, Failure }

class SubscriptionOperationSpec extends OperationSpec {
  val streamId = EventStream.Id("streamId")
  val streams = Seq(EventStream.All, streamId)

  "SubscriptionOperation when subscribing" should {
    "return id equal to correlationId" in foreach(streams) { implicit stream =>
      new SubscribingScope {
        operation.id mustEqual pack.correlationId
      }
    }

    "drop OutFunc on disconnected" in foreach(streams) { implicit stream =>
      new SubscribingScope {
        val expected = operation.copy(ongoing = false)
        operation.disconnected mustEqual OnDisconnected.Continue(expected)
      }
    }

    "retry on connected" in foreach(streams) { implicit stream =>
      new SubscribingScope {
        operation.copy(ongoing = false).connected mustEqual OnConnected.Retry(operation, pack)
      }
    }

    "unsubscribe on clientTerminated" in foreach(streams) { implicit stream =>
      new SubscribingScope {
        operation.clientTerminated must beSome(pack.copy(message = Unsubscribe))
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

        operation.inspectOut(Unsubscribe) mustEqual OnOutgoing.Stop(unsubscribe, Try(Unsubscribed))
      }
    }

    "forward new events" in foreach(streams) { implicit stream =>
      new SubscribingScope {
        val event = eventAppeared(streamId)
        operation.inspectIn(Try(event)) mustEqual Continue(operation, Try(event))

        val resolved = eventAppeared(resolvedEvent(streamId))
        operation.inspectIn(Try(resolved)) mustEqual Continue(operation, Try(resolved))
      }
    }

    "become subscribed on success" in foreach(streams) { implicit stream =>
      new SubscribingScope {
        operation.inspectIn(Try(subscribeCompleted)) mustEqual Continue(
          SubscriptionOperation.Subscribed(subscribeTo, pack, client, ongoing = true, 1),
          Try(subscribeCompleted))
      }
    }

    "stay on success if disconnected" in foreach(streams) { implicit stream =>
      new SubscribingScope {
        val disconnected = operation.copy(ongoing = false)
        disconnected.inspectIn(Try(subscribeCompleted)) mustEqual Ignore
      }
    }

    "stop on expected error" in foreach(streams) { implicit stream =>
      new SubscribingScope {
        operation.inspectIn(Failure(SubscriptionDropped.AccessDenied)) must beLike {
          case Stop(Failure(_: AccessDeniedException)) => ok
        }
      }
    }

    "retry on NotReady" in foreach(streams) { implicit stream =>
      new SubscribingScope {
        operation.inspectIn(Failure(NotHandled(NotReady))) mustEqual Retry(operation, pack)
      }
    }

    "retry on TooBusy" in foreach(streams) { implicit stream =>
      new SubscribingScope {
        operation.inspectIn(Failure(NotHandled(TooBusy))) mustEqual Retry(operation, pack)
      }
    }

    "stop on OperationTimedOut" in foreach(streams) { implicit stream =>
      new SubscribingScope {
        operation.inspectIn(Failure(OperationTimedOut)) mustEqual Stop(OperationTimeoutException(pack))
      }
    }

    "stop on NotAuthenticated" in foreach(streams) { implicit stream =>
      new SubscribingScope {
        operation.inspectIn(Failure(NotAuthenticated)) must beLike {
          case Stop(Failure(_: NotAuthenticatedException)) => ok
        }
      }
    }

    "stop on BadRequest" in foreach(streams) { implicit stream =>
      new SubscribingScope {
        operation.inspectIn(Failure(BadRequest)) must beLike {
          case Stop(Failure(_: ServerErrorException)) => ok
        }
      }
    }

    "stop on unexpected" in foreach(streams) { implicit stream =>
      new SubscribingScope {
        val unexpected = stream match {
          case _: EventStream.Id => SubscribeToAllCompleted(0)
          case _                 => SubscribeToStreamCompleted(0)
        }
        operation.inspectIn(Try(unexpected)) must beLike {
          case Stop(Failure(_: CommandNotExpectedException)) => ok
        }
      }
    }

    "stop on unexpected error" in foreach(streams) { implicit stream =>
      new SubscribingScope {
        operation.inspectIn(Failure(ReadEventError.StreamDeleted)) must beLike {
          case Stop(Failure(_: CommandNotExpectedException)) => ok
        }
      }
    }

    "return 0 for version" in foreach(streams) { implicit stream =>
      new SubscribingScope {
        operation.version mustEqual 0
        val o1 = operation.disconnected must beLike {
          case OnDisconnected.Continue(x) if x.version == 0 =>
            x.connected must beLike {
              case OnConnected.Retry(x, _) if x.version == 0 => ok
            }
        }
      }
    }
  }

  "SubscriptionOperation when subscribed" should {
    "return id equal to correlationId" in foreach(streams) { implicit stream =>
      new SubscribedScope {
        operation.id mustEqual pack.correlationId
      }
    }

    "become subscribing on disconnected" in foreach(streams) { implicit stream =>
      new SubscribedScope {
        operation.disconnected must beLike {
          case OnDisconnected.Continue(x: SubscriptionOperation.Subscribing) if x.version == 1 => ok
        }
      }
    }

    "become subscribing on connected and retry" in foreach(streams) { implicit stream =>
      new SubscribedScope {
        operation.connected must beLike {
          case OnConnected.Retry(x: SubscriptionOperation.Subscribing, `pack`) if x.version == 1 => ok
        }
      }
    }

    "unsubscribe on clientTerminated" in foreach(streams) { implicit stream =>
      new SubscribedScope {
        operation.clientTerminated must beSome(pack.copy(message = Unsubscribe))
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
        unsubscribing must beLike {
          case OnOutgoing.Continue(x, `unsubscribe`) if x.version == 1 => ok
        }
      }
    }

    "forward new events" in foreach(streams) { implicit stream =>
      new SubscribedScope {
        val event = eventAppeared(streamId)
        operation.inspectIn(Try(event)) mustEqual Continue(operation, Try(event))

        val resolved = eventAppeared(resolvedEvent(streamId))
        operation.inspectIn(Try(resolved)) mustEqual Continue(operation, Try(resolved))
      }
    }

    "stop on AccessDenied" in foreach(streams) { implicit stream =>
      new SubscribedScope {
        operation.inspectIn(Failure(SubscriptionDropped.AccessDenied)) must beLike {
          case Stop(Failure(_: AccessDeniedException)) => ok
        }
      }
    }

    "stop on Unsubscribed" in foreach(streams) { implicit stream =>
      new SubscribedScope {
        operation.inspectIn(Try(Unsubscribed)) mustEqual Stop(Unsubscribed)
      }
    }

    "stop on NotReady" in foreach(streams) { implicit stream =>
      new SubscribedScope {
        operation.inspectIn(Failure(NotHandled(NotReady))) must beLike {
          case Stop(Failure(_: CommandNotExpectedException)) => ok
        }
      }
    }

    "stop on TooBusy" in foreach(streams) { implicit stream =>
      new SubscribedScope {
        operation.inspectIn(Failure(NotHandled(TooBusy))) must beLike {
          case Stop(Failure(_: CommandNotExpectedException)) => ok
        }
      }
    }

    "stop on OperationTimedOut" in foreach(streams) { implicit stream =>
      new SubscribedScope {
        operation.inspectIn(Failure(OperationTimedOut)) must beLike {
          case Stop(Failure(_: CommandNotExpectedException)) => ok
        }
      }
    }

    "stop on NotAuthenticated" in foreach(streams) { implicit stream =>
      new SubscribedScope {
        operation.inspectIn(Failure(NotAuthenticated)) must beLike {
          case Stop(Failure(_: CommandNotExpectedException)) => ok
        }
      }
    }

    "stop on BadRequest" in foreach(streams) { implicit stream =>
      new SubscribedScope {
        operation.inspectIn(Failure(BadRequest)) must beLike {
          case Stop(Failure(_: CommandNotExpectedException)) => ok
        }
      }
    }

    "stop on unexpected" in foreach(streams) { implicit stream =>
      new SubscribedScope {
        operation.inspectIn(Try(Pong)) must beLike {
          case Stop(Failure(_: CommandNotExpectedException)) => ok
        }
      }
    }

    "stop on unexpected error" in foreach(streams) { implicit stream =>
      new SubscribedScope {
        operation.inspectIn(Failure(ReadEventError.StreamDeleted)) must beLike {
          case Stop(Failure(_: CommandNotExpectedException)) => ok
        }
      }
    }

    "return 0 for version" in foreach(streams) { implicit stream =>
      new SubscribedScope {
        operation.version mustEqual 0
        val o1 = operation.disconnected must beLike {
          case OnDisconnected.Continue(x) if x.version == 1 => ok
        }
      }
    }
  }

  "SubscriptionOperation when unsubscribing" should {
    "return id equal to correlationId" in foreach(streams) { implicit stream =>
      new UnsubscribingScope {
        operation.id mustEqual pack.correlationId
      }
    }

    "stop on disconnected" in foreach(streams) { implicit stream =>
      new UnsubscribingScope {
        operation.disconnected mustEqual OnDisconnected.Stop(Try(Unsubscribed))
      }
    }

    "stop on connected" in foreach(streams) { implicit stream =>
      new UnsubscribingScope {
        operation.connected mustEqual OnConnected.Stop(Try(Unsubscribed))
      }
    }

    "stop on clientTerminated" in foreach(streams) { implicit stream =>
      new UnsubscribingScope {
        operation.clientTerminated must beNone
      }
    }

    "ignore out messages" in foreach(streams) { implicit stream =>
      new UnsubscribingScope {
        operation.inspectOut mustEqual PartialFunction.empty
        operation.inspectOut.isDefinedAt(Unsubscribe) must beFalse
      }
    }

    "stop on success" in foreach(streams) { implicit stream =>
      new UnsubscribingScope {
        operation.inspectIn(Try(Unsubscribed)) mustEqual Stop(Unsubscribed)
      }
    }

    "stop on expected error" in foreach(streams) { implicit stream =>
      new UnsubscribingScope {
        operation.inspectIn(Failure(SubscriptionDropped.AccessDenied)) must beLike {
          case Stop(Failure(_: AccessDeniedException)) => ok
        }
      }
    }

    "retry on NotReady" in foreach(streams) { implicit stream =>
      new UnsubscribingScope {
        operation.inspectIn(Failure(NotHandled(NotReady))) mustEqual Retry(operation, pack)
      }
    }

    "retry on TooBusy" in foreach(streams) { implicit stream =>
      new UnsubscribingScope {
        operation.inspectIn(Failure(NotHandled(TooBusy))) mustEqual Retry(operation, pack)
      }
    }

    "stop on OperationTimedOut" in foreach(streams) { implicit stream =>
      new UnsubscribingScope {
        operation.inspectIn(Failure(OperationTimedOut)) mustEqual Stop(OperationTimeoutException(pack))
      }
    }

    "stop on NotAuthenticated" in foreach(streams) { implicit stream =>
      new UnsubscribingScope {
        operation.inspectIn(Failure(NotAuthenticated)) must beLike {
          case Stop(Failure(_: CommandNotExpectedException)) => ok
        }
      }
    }

    "stop on BadRequest" in foreach(streams) { implicit stream =>
      new UnsubscribingScope {
        operation.inspectIn(Failure(BadRequest)) must beLike {
          case Stop(Failure(_: ServerErrorException)) => ok
        }
      }
    }

    "stop on unexpected" in foreach(streams) { implicit stream =>
      new UnsubscribingScope {
        val unexpected = stream match {
          case x: EventStream.Id => SubscribeToStreamCompleted(0)
          case _                 => SubscribeToAllCompleted(0)
        }
        operation.inspectIn(Try(unexpected)) must beLike {
          case Stop(Failure(_: CommandNotExpectedException)) => ok
        }
      }
    }

    "stop on unexpected error" in foreach(streams) { implicit stream =>
      new UnsubscribingScope {
        operation.inspectIn(Failure(ReadEventError.StreamDeleted)) must beLike {
          case Stop(Failure(_: CommandNotExpectedException)) => ok
        }
      }
    }

    "ignore new events" in foreach(streams) { implicit stream =>
      new UnsubscribingScope {
        operation.inspectIn(Try(eventAppeared(streamId))) mustEqual Ignore
      }
    }

    "return 0 for version" in foreach(streams) { implicit stream =>
      new UnsubscribingScope {
        operation.version mustEqual 0
      }
    }
  }

  private trait SubscriptionScope extends OperationScope {

    def resolvedEvent(streamId: EventStream.Id) = {
      val event = EventRecord(EventStream.Id(randomUuid.toString), EventNumber.Exact(10), EventData("test"))
      ResolvedEvent(event, EventRecord(streamId, EventNumber.First, event.link()))
    }

    def eventAppeared(streamId: EventStream.Id): StreamEventAppeared = {
      val event = EventRecord(streamId, EventNumber.First, EventData("test"))
      eventAppeared(event)
    }

    def eventAppeared(event: Event): StreamEventAppeared = {
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
    val operation = SO.Subscribing(subscribeTo, pack, client, ongoing = true, 0)

    lazy val subscribeCompleted = stream match {
      case x: EventStream.Id => SubscribeToStreamCompleted(0)
      case _                 => SubscribeToAllCompleted(0)
    }
  }

  private abstract class SubscribedScope(implicit val stream: EventStream) extends SubscriptionScope {
    val subscribeTo = SubscribeTo(stream)
    val pack = PackOut(subscribeTo)
    val unsubscribe = PackOut(Unsubscribe, pack.correlationId, pack.credentials)
    val operation = SO.Subscribed(subscribeTo, pack, client, ongoing = true, 0)
  }

  private abstract class UnsubscribingScope(implicit val stream: EventStream) extends SubscriptionScope {
    val subscribeTo = SubscribeTo(stream)
    val pack = PackOut(Unsubscribe)
    val operation = SO.Unsubscribing(stream, pack, client, ongoing = true, 0)
  }
}