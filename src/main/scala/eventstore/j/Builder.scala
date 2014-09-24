package eventstore
package j

trait Builder[T] {
  def build: T
}

trait ChainSet[T] { self: T =>
  protected def set(f: => Any): T = { f; this }
}

object Builder {
  trait ExpectVersionSnippet[T] extends ChainSet[T] { self: T =>
    protected var _expectVersion: ExpectedVersion = ExpectedVersion.Any

    def expectNoStream: T = expectVersion(ExpectedVersion.NoStream)
    def expectAnyVersion: T = expectVersion(ExpectedVersion.Any)
    def expectVersion(x: Int): T = expectVersion(ExpectedVersion.Exact(x))
    def expectVersion(x: ExpectedVersion): T = set {
      _expectVersion = x
    }
  }

  trait ResolveLinkTosSnippet[T] extends ChainSet[T] { self: T =>
    var _resolveLinkTos = Settings.Default.resolveLinkTos

    def resolveLinkTos(x: Boolean): T = set {
      _resolveLinkTos = x
    }
  }

  trait RequireMasterSnippet[T] extends ChainSet[T] { self: T =>
    protected var _requireMaster: Boolean = Settings.Default.requireMaster

    def requireMaster(x: Boolean): T = set {
      _requireMaster = x
    }

    def performOnAnyNode: T = requireMaster(x = false)

    def performOnMasterOnly: T = requireMaster(x = true)
  }

  trait EventDataSnippetI[T] {
    def addEvent(x: EventData): T
    def addEvents(xs: java.lang.Iterable[EventData]): T
    def event(x: EventData): T
    def events(xs: java.lang.Iterable[EventData]): T
  }

  trait EventDataSnippet[T] extends EventDataSnippetI[T] with ChainSet[T] {
    self: T =>

    import scala.collection.mutable.ListBuffer
    import scala.collection.JavaConverters._

    object EventDataSnippet extends EventDataSnippetI[T] {
      var value: ListBuffer[EventData] = new ListBuffer()

      def addEvent(x: EventData) = set {
        value += x
      }

      def addEvents(xs: java.lang.Iterable[EventData]) = set {
        value ++= xs.asScala
      }

      def event(x: EventData) = set {
        value = new ListBuffer()
        addEvent(x)
      }

      def events(xs: java.lang.Iterable[EventData]) = set {
        value = new ListBuffer()
        addEvents(xs)
      }
    }
  }

  trait MaxCountSnippet[T] extends ChainSet[T] { self: T =>
    protected var _maxCount = Settings.Default.readBatchSize

    def maxCount(x: Int): T = set {
      _maxCount = x
    }
  }

  trait DirectionSnippet[T] extends ChainSet[T] { self: T =>
    protected var _direction: ReadDirection = eventstore.ReadDirection.Forward

    def forward: T = set {
      _direction = eventstore.ReadDirection.Forward
    }

    def backward: T = set {
      _direction = eventstore.ReadDirection.Backward
    }
  }
}