package eventstore

sealed trait EventStream

object EventStream {

  def apply(id: String): Id = Id(id)

  def apply(x: UserCredentials): Id = Id("$user-" + x.login)

  case object All extends EventStream {
    override def toString = "Stream.All"
  }

  case class Id(value: String) extends EventStream {
    require(value != null, "stream id must be not null")
    require(value.nonEmpty, "stream id must be not empty")

    def isSystem = value startsWith "$"
    def isMeta = value startsWith "$$"

    def meta: EventStream = EventStream("$$" + value)

    override def toString = s"Stream($value)"
  }

  //  case class Meta(value: String) // TODO

  //  case class System(value: String) // TODO
}