package eventstore.operations

import eventstore.NotHandled.{ NotReady, TooBusy }
import eventstore.PersistentSubscription.{ Connect, Connected, EventAppeared }
import eventstore.operations.{ PersistentSubscriptionOperation => PsO }
import eventstore._
import eventstore.operations.OnIncoming.{ Continue, Ignore, Retry, Stop }
import eventstore.tcp.PackOut

import scala.util.{ Failure, Success, Try }

class PersistentSubscriptionOperationSpec extends OperationSpec {
  "PersistentSubscriptionOperation when subscribing" should {
    "return id equal to correlationId" in new ConnectingScope {
      operation.id mustEqual pack.correlationId
    }

    "drop OutFunc on disconnected" in new ConnectingScope {
      val expected = operation.copy(ongoing = false)
      operation.disconnected mustEqual OnDisconnected.Continue(expected)
    }

    "retry on connected" in new ConnectingScope {
      operation.copy(ongoing = false).connected mustEqual OnConnected.Retry(operation, pack)
    }

    "unsubscribe on clientTerminated" in new ConnectingScope {
      operation.clientTerminated must beSome(pack.copy(message = Unsubscribe))
    }

    "ignore out messages except Unsubscribe" in new ConnectingScope {
      val outs = Seq(
        connect,
        ReadEvent(streamId),
        ReadStreamEvents(streamId),
        ReadAllEvents(),
        Ping,
        HeartbeatRequest,
        Authenticate,
        ScavengeDatabase
      )
      foreach(outs) { x => operation.inspectOut.isDefinedAt(x) must beFalse }

      operation.inspectOut(Unsubscribe) mustEqual OnOutgoing.Stop(unsubscribe, Try(Unsubscribed))
    }

    "forward new events" in new ConnectingScope {
      val event = eventAppeared(streamId)
      operation.inspectIn(Try(event)) mustEqual Continue(operation, Try(event))

      val resolved = eventAppeared(resolvedEvent(streamId))
      operation.inspectIn(Try(resolved)) mustEqual Continue(operation, Try(resolved))
    }

    "become subscribed on success" in new ConnectingScope {
      operation.inspectIn(Try(connected)) mustEqual Continue(
        PsO.Connected(connect, pack, client, ongoing = true, 1),
        Try(connected)
      )
    }

    "stay on success if disconnected" in new ConnectingScope {
      val disconnected = operation.copy(ongoing = false)
      disconnected.inspectIn(Try(connected)) mustEqual Ignore
    }

    "stop on expected error" in new ConnectingScope {
      operation.inspectIn(Failure(SubscriptionDropped.AccessDenied)) must beLike {
        case Stop(Failure(_: AccessDeniedException)) => ok
      }
    }

    "retry on NotReady" in new ConnectingScope {
      operation.inspectIn(Failure(NotHandled(NotReady))) mustEqual Retry(operation, pack)
    }

    "retry on TooBusy" in new ConnectingScope {
      operation.inspectIn(Failure(NotHandled(TooBusy))) mustEqual Retry(operation, pack)
    }

    "stop on OperationTimedOut" in new ConnectingScope {
      operation.inspectIn(Failure(OperationTimedOut)) mustEqual Stop(OperationTimeoutException(pack))
    }

    "stop on NotAuthenticated" in new ConnectingScope {
      operation.inspectIn(Failure(NotAuthenticated)) must beLike {
        case Stop(Failure(_: NotAuthenticatedException)) => ok
      }
    }

    "stop on BadRequest" in new ConnectingScope {
      operation.inspectIn(Failure(BadRequest)) must beLike {
        case Stop(Failure(_: ServerErrorException)) => ok
      }
    }

    "stop on unexpected" in new ConnectingScope {
      operation.inspectIn(Try(Pong)) must beLike {
        case Stop(Failure(_: CommandNotExpectedException)) => ok
      }
    }

    "stop on unexpected error" in new ConnectingScope {
      operation.inspectIn(Failure(ReadEventError.StreamDeleted)) must beLike {
        case Stop(Failure(_: CommandNotExpectedException)) => ok
      }
    }

    "return 0 for version" in new ConnectingScope {
      operation.version mustEqual 0
      val o1 = operation.disconnected must beLike {
        case OnDisconnected.Continue(x) if x.version == 0 =>
          x.connected must beLike {
            case OnConnected.Retry(x, _) if x.version == 0 => ok
          }
      }
    }
  }

  "PersistentSubscriptionOperation when subscribed" should {
    "return id equal to correlationId" in new ConnectedScope {
      operation.id mustEqual pack.correlationId
    }

    "become connecting on disconnected" in new ConnectedScope {
      operation.disconnected must beLike {
        case OnDisconnected.Continue(x: PersistentSubscriptionOperation.Connecting[Client]) if x.version == 1 => ok // Should be Connecting <= Persistent's version of subscribing.
      }
    }

    "become connected on connected and retry" in new ConnectedScope {
      operation.connected must beLike {
        case OnConnected.Retry(x: PersistentSubscriptionOperation.Connecting[Client], `pack`) if x.version == 1 => ok
      }
    }

    "unsubscribe on clientTerminated" in new ConnectedScope {
      operation.clientTerminated must beSome(pack.copy(message = Unsubscribe))
    }

    "ignore out messages except Unsubscribe" in new ConnectedScope {
      val outs = Seq(
        subscribeTo,
        ReadEvent(streamId),
        ReadStreamEvents(streamId),
        ReadAllEvents(),
        Ping,
        HeartbeatRequest,
        Authenticate,
        ScavengeDatabase
      )
      foreach(outs) { x => operation.inspectOut.isDefinedAt(x) must beFalse }

      val unsubscribing = operation.inspectOut(Unsubscribe)
      unsubscribing must beLike {
        case OnOutgoing.Continue(x, `unsubscribe`) if x.version == 1 => ok
      }
    }

    "forward new events" in new ConnectedScope {
      val event = eventAppeared(streamId)
      operation.inspectIn(Try(event)) mustEqual Continue(operation, Try(event))

      val resolved = eventAppeared(resolvedEvent(streamId))
      operation.inspectIn(Try(resolved)) mustEqual Continue(operation, Try(resolved))
    }

    "stop on AccessDenied" in new ConnectedScope {
      operation.inspectIn(Failure(SubscriptionDropped.AccessDenied)) must beLike {
        case Stop(Failure(_: AccessDeniedException)) => ok
      }
    }

    "stop on Unsubscribed" in new ConnectedScope {
      operation.inspectIn(Try(Unsubscribed)) mustEqual Stop(Unsubscribed)
    }

    "stop on NotReady" in new ConnectedScope {
      operation.inspectIn(Failure(NotHandled(NotReady))) must beLike {
        case Stop(Failure(_: CommandNotExpectedException)) => ok
      }
    }

    "stop on TooBusy" in new ConnectedScope {
      operation.inspectIn(Failure(NotHandled(TooBusy))) must beLike {
        case Stop(Failure(_: CommandNotExpectedException)) => ok
      }
    }

    "stop on OperationTimedOut" in new ConnectedScope {
      operation.inspectIn(Failure(OperationTimedOut)) must beLike {
        case Stop(Failure(_: CommandNotExpectedException)) => ok
      }
    }

    "stop on NotAuthenticated" in new ConnectedScope {
      operation.inspectIn(Failure(NotAuthenticated)) must beLike {
        case Stop(Failure(_: CommandNotExpectedException)) => ok
      }
    }

    "stop on BadRequest" in new ConnectedScope {
      operation.inspectIn(Failure(BadRequest)) must beLike {
        case Stop(Failure(_: CommandNotExpectedException)) => ok
      }
    }

    "stop on unexpected" in new ConnectedScope {
      operation.inspectIn(Try(Pong)) must beLike {
        case Stop(Failure(_: CommandNotExpectedException)) => ok
      }
    }

    "stop on unexpected error" in new ConnectedScope {
      operation.inspectIn(Failure(ReadEventError.StreamDeleted)) must beLike {
        case Stop(Failure(_: CommandNotExpectedException)) => ok
      }
    }

    "return 0 for version" in new ConnectedScope {
      operation.version mustEqual 0
      val o1 = operation.disconnected must beLike {
        case OnDisconnected.Continue(x) if x.version == 1 => ok
      }
    }
  }

  "PersistentSubscriptionOperation when unsubscribing" should {
    "return id equal to correlationId" in new DisconnectingScope {
      operation.id mustEqual pack.correlationId
    }

    "stop on disconnected" in new DisconnectingScope {
      operation.disconnected mustEqual OnDisconnected.Stop(Try(Unsubscribed))
    }

    "stop on connected" in new DisconnectingScope {
      operation.connected mustEqual OnConnected.Stop(Try(Unsubscribed))
    }

    "stop on clientTerminated" in new DisconnectingScope {
      operation.clientTerminated must beNone
    }

    "ignore out messages" in new DisconnectingScope {
      operation.inspectOut mustEqual PartialFunction.empty
      operation.inspectOut.isDefinedAt(Unsubscribe) must beFalse
    }

    "stop on success" in new DisconnectingScope {
      operation.inspectIn(Try(Unsubscribed)) mustEqual Stop(Unsubscribed)
    }

    "stop on expected error" in new DisconnectingScope {
      operation.inspectIn(Failure(SubscriptionDropped.AccessDenied)) must beLike {
        case Stop(Failure(_: AccessDeniedException)) => ok
      }
    }

    "retry on NotReady" in new DisconnectingScope {
      operation.inspectIn(Failure(NotHandled(NotReady))) mustEqual Retry(operation, pack)
    }

    "retry on TooBusy" in new DisconnectingScope {
      operation.inspectIn(Failure(NotHandled(TooBusy))) mustEqual Retry(operation, pack)
    }

    "stop on OperationTimedOut" in new DisconnectingScope {
      operation.inspectIn(Failure(OperationTimedOut)) mustEqual Stop(OperationTimeoutException(pack))
    }

    "stop on NotAuthenticated" in new DisconnectingScope {
      operation.inspectIn(Failure(NotAuthenticated)) must beLike {
        case Stop(Failure(_: CommandNotExpectedException)) => ok
      }
    }

    "stop on BadRequest" in new DisconnectingScope {
      operation.inspectIn(Failure(BadRequest)) must beLike {
        case Stop(Failure(_: ServerErrorException)) => ok
      }
    }

    "stop on unexpected" in new DisconnectingScope {
      val unexpected = Pong
      operation.inspectIn(Try(unexpected)) must beLike {
        case Stop(Failure(_: CommandNotExpectedException)) => ok
      }
    }

    "stop on unexpected error" in new DisconnectingScope {
      operation.inspectIn(Failure(ReadEventError.StreamDeleted)) must beLike {
        case Stop(Failure(_: CommandNotExpectedException)) => ok
      }
    }

    "ignore new events" in new DisconnectingScope {
      operation.inspectIn(Try(eventAppeared(streamId))) mustEqual Ignore
    }

    "return 0 for version" in new DisconnectingScope {
      operation.version mustEqual 0
    }
  }

  private trait PsScope extends OperationScope {
    val streamId = EventStream.Id("streamId")
    val groupName = "groupName"

    def resolvedEvent(streamId: EventStream.Id) = {
      val event = EventRecord(EventStream.Id(randomUuid.toString), EventNumber.Exact(10), EventData("test"))
      ResolvedEvent(event, EventRecord(streamId, EventNumber.First, event.link()))
    }

    def eventAppeared(streamId: EventStream.Id): EventAppeared = {
      val event = EventRecord(streamId, EventNumber.First, EventData("test"))
      eventAppeared(event)
    }

    def eventAppeared(event: Event): EventAppeared = {
      EventAppeared(event)
    }
  }

  private trait ConnectingScope extends PsScope {
    val connect = Connect(streamId, groupName)
    val pack = PackOut(connect)
    val unsubscribe = PackOut(Unsubscribe, pack.correlationId, pack.credentials)
    val operation = PsO.Connecting(connect, pack, client, ongoing = true, 0)

    lazy val connected = Connected("1", 1, Option(EventNumber.First))
  }

  private trait ConnectedScope extends PsScope {
    val connect = Connect(streamId, groupName)
    val subscribeTo = SubscribeTo(streamId)
    val pack = PackOut(subscribeTo)
    val unsubscribe = PackOut(Unsubscribe, pack.correlationId, pack.credentials)
    val operation = PsO.Connected(connect, pack, client, ongoing = true, 0)
  }

  private trait DisconnectingScope extends PsScope {
    val subscribeTo = SubscribeTo(streamId)
    val pack = PackOut(Unsubscribe)
    val operation = PsO.Unsubscribing(streamId, pack, client, ongoing = true, 0)
  }
}
