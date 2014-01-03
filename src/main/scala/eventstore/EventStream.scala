package eventstore

sealed trait EventStream {
  def isSystem: Boolean
  def isMetadata: Boolean
}

object EventStream {

  def apply(id: String): Id = Id(id)

  def apply(x: UserCredentials): Id = System(x)

  case object All extends EventStream {

    def isSystem = true
    def isMetadata = false

    override lazy val toString = "Stream.All"
  }

  sealed trait Id extends EventStream {
    lazy val streamId: String = prefix + value

    def value: String
    def prefix: String
  }

  object Id {
    def apply(streamId: String): Id = {
      require(streamId != null, "streamId must be not null") // TODO use 'must not be'
      require(streamId.nonEmpty, "streamId must be not empty")
      if (streamId startsWith "$$") Metadata(streamId substring 2) else HasMetadata(streamId)
    }
  }

  sealed trait HasMetadata extends Id {
    lazy val metadata: Metadata = Metadata(streamId)
  }

  object HasMetadata {
    def apply(streamId: String): HasMetadata = {
      require(streamId != null, "streamId must be not null")
      require(streamId.nonEmpty, "streamId must be not empty")
      require(!(streamId startsWith "$$"), "streamId must not start with $$")
      if (streamId startsWith "$") System(streamId substring 1) else Plain(streamId)
    }
  }

  case class Plain(value: String) extends HasMetadata {
    require(value != null, "value must be not null")
    require(value.nonEmpty, "value must be not empty")
    require(!(value startsWith "$"), "value must not start with $")

    def prefix = ""
    def isSystem = false
    def isMetadata = false

    override lazy val toString = s"Stream($streamId)"
  }

  case class System(value: String) extends HasMetadata {
    require(value != null, "value must be not null")
    require(value.nonEmpty, "value must be not empty")
    require(!(value startsWith "$"), "value must not start with $")

    def prefix = "$"
    def isSystem = true
    def isMetadata = false

    override lazy val toString = s"SystemStream($streamId)"
  }

  object System {
    def apply(x: UserCredentials): System = System(s"user-${x.login}")
  }

  case class Metadata(value: String) extends Id {
    require(value != null, "value must be not null")
    require(value.nonEmpty, "value must be not empty")
    require(!(value startsWith "$$"), "value must not start with $$")

    def prefix = "$$"
    def isSystem = false
    def isMetadata = true

    lazy val owner: HasMetadata = HasMetadata(value) // TODO rename to original

    override lazy val toString = s"MetadataStream($streamId)"
  }
}