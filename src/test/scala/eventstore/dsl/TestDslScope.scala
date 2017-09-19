package eventstore.dsl

import akka.stream.Materializer
import akka.stream.scaladsl.{ Sink, Source }
import eventstore.{ Content, DeleteStream, DeleteStreamCompleted, EsConnection, EventData, EventStream, WriteEvents, WriteEventsCompleted }
import spray.json.JsObject

import scala.collection.immutable
import scala.concurrent.Future

