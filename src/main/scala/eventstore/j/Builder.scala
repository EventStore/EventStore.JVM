package eventstore
package j

trait Builder[T] {
  def build: T
}

trait ChainSet[T] {
  self: T =>

  protected def set(f: => Any): T = {
    f
    this
  }
}

object Builder {
  trait ExpectVersionSnippetI[T] {
    def expectNoStream: T
    def expectAnyVersion: T
    def expectVersion(x: Int): T
  }

  trait ExpectVersionSnippet[T] extends ExpectVersionSnippetI[T] with ChainSet[T] {
    self: T =>

    object ExpectedVersionSnippet extends ExpectVersionSnippetI[T] {
      var value: ExpectedVersion = ExpectedVersion.Any

      def expectNoStream = set {
        value = ExpectedVersion.NoStream
      }

      def expectAnyVersion = set {
        value = ExpectedVersion.Any
      }

      def expectVersion(x: Int) = set {
        value = ExpectedVersion.Exact(x)
      }
    }
  }

  trait ResolveLinkTosSnippetI[T] {
    def resolveLinkTos(x: Boolean): T
  }

  trait ResolveLinkTosSnippet[T] extends ResolveLinkTosSnippetI[T] with ChainSet[T] {
    self: T =>

    object ResolveLinkTosSnippet extends ResolveLinkTosSnippetI[T] {
      var value = false

      def resolveLinkTos(x: Boolean) = set {
        value = x
      }
    }
  }

  trait RequireMasterSnippetI[T] {
    def requireMaster(x: Boolean): T
  }

  trait RequireMasterSnippet[T] extends RequireMasterSnippetI[T] with ChainSet[T] {
    self: T =>

    object RequireMasterSnippet extends RequireMasterSnippetI[T] {
      var value = true

      def requireMaster(x: Boolean) = set {
        value = x
      }
    }
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

  trait MaxCountSnippetI[T] {
    def maxCount(x: Int): T
  }

  trait MaxCountSnippet[T] extends MaxCountSnippetI[T] with ChainSet[T] {
    self: T =>

    object MaxCountSnippet extends MaxCountSnippetI[T] {
      var value = 500

      def maxCount(x: Int) = set {
        value = x
      }
    }
  }

  trait DirectionSnippetI[T] {
    def forward: T
    def backward: T
  }

  trait DirectionSnippet[T] extends DirectionSnippetI[T] with ChainSet[T] {
    self: T =>

    object DirectionSnippet extends DirectionSnippetI[T] {
      var value: ReadDirection.Value = ReadDirection.Forward

      def forward = set {
        value = ReadDirection.Forward
      }

      def backward = set {
        value = ReadDirection.Backward
      }
    }
  }
}