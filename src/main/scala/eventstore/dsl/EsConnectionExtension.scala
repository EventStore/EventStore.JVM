package eventstore.dsl

import eventstore.{ EsConnection, EventStream }

object EsConnectionExtension {

  implicit class EsConnectionPimper(val connection: EsConnection) extends AnyVal {
    def getStream(name: String): EsStream = {
      require(name != null, "Name cannot be null")
      require(!name.isEmpty, "Name cannot be empty")

      getStream(EventStream.Id(s"$name"))
    }

    def getStream(streamId: EventStream.Id): EsStream = new EsStream(connection, streamId)

    def getStreamAs[T](name: String)(implicit eventFormat: EventFormat[T]): EsStreamAs[T] = {
      require(name != null, "Name cannot be null")
      require(!name.isEmpty, "Name cannot be empty")

      getStreamAs(EventStream.Id(s"$name"))
    }

    def getStreamAs[T](streamId: EventStream.Id)(implicit eventFormat: EventFormat[T]): EsStreamAs[T] = new EsStreamAs(connection, streamId, eventFormat)

    def getStreamCategory(category: String): EsStreamCategory = {
      new EsStreamCategory(connection, category)
    }

    def getStreamCategoryAs[T](category: String)(implicit eventFormat: EventFormat[T]): EsStreamCategoryAs[T] = {
      new EsStreamCategoryAs[T](connection, category)
    }
  }
}