package eventstore

import eventstore.core.{settings ⇒ cs}
import eventstore.{core ⇒ c}

trait CoreCompat {

  private lazy val RM: Boolean  = Settings.Default.requireMaster
  private lazy val RLT: Boolean = Settings.Default.resolveLinkTos
  private lazy val RBS: Int     = Settings.Default.readBatchSize

  object WriteEvents {

    def unapply(arg: c.WriteEvents): Option[(c.EventStream.Id, List[c.EventData], c.ExpectedVersion, Boolean)] =
      c.WriteEvents.unapply(arg)

    def apply(
      streamId:        c.EventStream.Id,
      events:          List[c.EventData],
      expectedVersion: c.ExpectedVersion = c.ExpectedVersion.Any,
      requireMaster:   Boolean           = RM
    ): c.WriteEvents = c.WriteEvents(streamId, events, expectedVersion, requireMaster)

    object StreamMetadata {

      def apply(
        streamId:        c.EventStream.Metadata,
        data:            c.Content,
        expectedVersion: c.ExpectedVersion = c.ExpectedVersion.Any,
        requireMaster:   Boolean           = RM
      ): c.WriteEvents = c.WriteEvents.StreamMetadata(streamId, data, randomUuid, expectedVersion, requireMaster)

    }
  }

  object DeleteStream {

    def unapply(arg: c.DeleteStream): Option[(c.EventStream.Id, c.ExpectedVersion.Existing, Boolean, Boolean)] =
      c.DeleteStream.unapply(arg)

    def apply(
      streamId:        c.EventStream.Id,
      expectedVersion: c.ExpectedVersion.Existing = c.ExpectedVersion.Any,
      hard:            Boolean                    = false,
      requireMaster: Boolean                      = RM
    ): c.DeleteStream = c.DeleteStream(streamId, expectedVersion, hard, requireMaster)
  }

  object TransactionStart {

    def unapply(arg: c.TransactionStart): Option[(c.EventStream.Id, c.ExpectedVersion, Boolean)] =
      c.TransactionStart.unapply(arg)

    def apply(
      streamId:        c.EventStream.Id,
      expectedVersion: c.ExpectedVersion = c.ExpectedVersion.Any,
      requireMaster:  Boolean            = RM
    ): c.TransactionStart = c.TransactionStart(streamId, expectedVersion, requireMaster)
  }

  object TransactionWrite {

    def unapply(arg: c.TransactionWrite): Option[(Long, List[c.EventData], Boolean)] =
      c.TransactionWrite.unapply(arg)

    def apply(
      transactionId:  Long,
      events:         List[c.EventData],
      requireMaster:  Boolean           = RM
    ): c.TransactionWrite = c.TransactionWrite(transactionId, events, requireMaster)
  }

  object TransactionCommit {

    def unapply(arg: c.TransactionCommit): Option[(Long, Boolean)] =
      c.TransactionCommit.unapply(arg)

    def apply(
      transactionId: Long,
      requireMaster: Boolean = RM
    ): c.TransactionCommit = c.TransactionCommit(transactionId, requireMaster)
  }


  object ReadEvent {

    def unapply(arg: c.ReadEvent): Option[(c.EventStream.Id, c.EventNumber, Boolean, Boolean)] =
      c.ReadEvent.unapply(arg)

    def apply(
      streamId:       c.EventStream.Id,
      eventNumber:    c.EventNumber    = c.EventNumber.First,
      resolveLinkTos: Boolean          = RLT,
      requireMaster:  Boolean          = RM
    ): c.ReadEvent = c.ReadEvent(streamId, eventNumber, resolveLinkTos, requireMaster)

    object StreamMetadata {
      def apply(
        streamId:       c.EventStream.Metadata,
        eventNumber:    c.EventNumber          = c.EventNumber.Last,
        resolveLinkTos: Boolean                = RLT,
        requireMaster:  Boolean                = RM
      ): c.ReadEvent = c.ReadEvent.StreamMetadata(streamId, eventNumber, resolveLinkTos, requireMaster)
    }
  }

  object ReadStreamEvents {

    def unapply(arg: c.ReadStreamEvents): Option[(c.EventStream.Id, c.EventNumber, Int, c.ReadDirection, Boolean, Boolean)] =
      c.ReadStreamEvents.unapply(arg)

    def apply(
      streamId:       c.EventStream.Id,
      fromNumber:     c.EventNumber    = c.EventNumber.First,
      maxCount:       Int              = RBS,
      direction:      c.ReadDirection  = c.ReadDirection.Forward,
      resolveLinkTos: Boolean          = RLT,
      requireMaster:  Boolean          = RM
    ): c.ReadStreamEvents = c.ReadStreamEvents(streamId, fromNumber, maxCount, direction, resolveLinkTos, requireMaster)
  }

  object ReadAllEvents {

    def unapply(arg: c.ReadAllEvents): Option[(c.Position, Int, c.ReadDirection, Boolean, Boolean)] =
      c.ReadAllEvents.unapply(arg)

    def apply(
      fromPosition: c.Position   = c.Position.First,
      maxCount: Int              = RBS,
      direction: c.ReadDirection = c.ReadDirection.Forward,
      resolveLinkTos: Boolean    = RLT,
      requireMaster:  Boolean    = RM
    ): c.ReadAllEvents = c.ReadAllEvents(fromPosition, maxCount, direction, resolveLinkTos, requireMaster)
  }

  object PersistentSubscriptionSettings {
    import com.typesafe.config.{Config, ConfigFactory}

    lazy val Default: cs.PersistentSubscriptionSettings        = apply(ConfigFactory.load())
    def apply(conf: Config): cs.PersistentSubscriptionSettings = cs.PersistentSubscriptionSettings(conf)
  }

  object PersistentSubscription {

    import PersistentSubscriptionSettings.{Default ⇒ D}
    import c.EventStream.Id
    import c.{PersistentSubscription ⇒ PS}
    import cs.{PersistentSubscriptionSettings ⇒ PSS}

    def create(streamId: Id, groupName: String, settings: PSS): Create = Create(streamId, groupName, settings)
    def update(streamId: Id, groupName: String, settings: PSS): Update = Update(streamId, groupName, settings)
    def delete(streamId: Id, groupName: String): Delete                = Delete(streamId, groupName)

    object Create {
      def unapply(arg: Create): Option[(Id, String, PSS)]                   = PS.Create.unapply(arg)
      def apply(streamId: Id, groupName: String, settings: PSS = D): Create = PS.Create(streamId, groupName, settings)
    }

    object Update {
      def unapply(arg: Update): Option[(Id, String, PSS)]                   = PS.Update.unapply(arg)
      def apply(streamId: Id, groupName: String, settings: PSS = D): Update = PS.Update(streamId, groupName, settings)
    }

    type Create          = PS.Create
    val  CreateCompleted = PS.CreateCompleted
    type Update          = PS.Update
    val  UpdateCompleted = PS.UpdateCompleted
    type Delete          = PS.Delete
    val  Delete          = PS.Delete
    val  DeleteCompleted = PS.DeleteCompleted
    type Ack             = PS.Ack
    val  Ack             = PS.Ack
    type Nak             = PS.Nak
    val  Nak             = PS.Nak
    type Connect         = PS.Connect
    val  Connect         = PS.Connect
    type Connected       = PS.Connected
    val  Connected       = PS.Connected
    type EventAppeared   = PS.EventAppeared
    val  EventAppeared   = PS.EventAppeared

  }

  object SubscribeTo {
    def unapply(arg: c.SubscribeTo): Option[(c.EventStream, Boolean)]              = SubscribeTo.unapply(arg)
    def apply(stream: c.EventStream, resolveLinkTos: Boolean = RLT): c.SubscribeTo = SubscribeTo(stream, resolveLinkTos)
  }
}

object compat extends CoreCompat