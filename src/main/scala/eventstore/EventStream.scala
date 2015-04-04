package eventstore

sealed trait EventStream {
  def isSystem: Boolean
  def isMetadata: Boolean
}

object EventStream {

  def apply(id: String): EventStream = id match {
    case null | "" => All
    case _         => Id(id)
  }

  def apply(x: UserCredentials): EventStream = Id(x)

  case object All extends EventStream {

    def isSystem = true
    def isMetadata = false

    override def toString = "Stream.All"
  }

  sealed trait Id extends EventStream {
    lazy val streamId: String = prefix + value

    def value: String
    def prefix: String
    def metadata: Metadata
  }

  object Id {
    def apply(x: UserCredentials): Id = System(x)

    def apply(streamId: String): Id = {
      require(streamId != null, "streamId must not be null")
      require(streamId.nonEmpty, "streamId must not be empty")
      if (streamId startsWith "$$") Metadata(streamId substring 2) else HasMetadata(streamId)
    }
  }

  sealed trait HasMetadata extends Id {
    lazy val metadata: Metadata = Metadata(streamId)
  }

  object HasMetadata {
    def apply(streamId: String): HasMetadata = {
      require(streamId != null, "streamId must not be null")
      require(streamId.nonEmpty, "streamId must not be empty")
      require(!(streamId startsWith "$$"), s"streamId must not start with $$$$, but is $streamId")
      if (streamId startsWith "$") System(streamId substring 1) else Plain(streamId)
    }
  }

  case class Plain(value: String) extends HasMetadata {
    require(value != null, "value must not be null")
    require(value.nonEmpty, "value must not be empty")
    require(!(value startsWith "$"), s"value must not start with $$, but is $value")

    def prefix = ""
    def isSystem = false
    def isMetadata = false

    override lazy val toString = s"Stream($streamId)"
  }

  case class System(value: String) extends HasMetadata {
    require(value != null, "value must not be null")
    require(value.nonEmpty, "value must not be empty")
    require(!(value startsWith "$"), s"value must not start with $$, but is $value")

    def prefix = "$"
    def isSystem = true
    def isMetadata = false

    override lazy val toString = s"SystemStream($streamId)"
  }

  object System {
    def apply(x: UserCredentials): System = System(s"user-${x.login}")
  }

  case class Metadata(value: String) extends Id {
    require(value != null, "value must not be null")
    require(value.nonEmpty, "value must not be empty")
    require(!(value startsWith "$$"), s"value must not start with $$$$, but is $value")

    def prefix = "$$"
    def isSystem = false
    def isMetadata = true
    def metadata = this

    lazy val original: HasMetadata = HasMetadata(value)

    override lazy val toString = s"MetadataStream($streamId)"
  }
}