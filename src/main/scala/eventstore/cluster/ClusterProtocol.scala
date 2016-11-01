package eventstore
package cluster

import org.joda.time.format.{ DateTimeFormatter, DateTimeFormat => JodaFormat }
import org.joda.time.{ DateTime, DateTimeZone }
import play.api.libs.json._

import scala.util.{ Failure, Success, Try }

object ClusterProtocol {
  implicit object UuidFormat extends Format[Uuid] {

    def writes(x: Uuid) = JsString(x.toString)

    def reads(json: JsValue) = {
      for { x <- json.validate[String] } yield x.uuid
    }
  }

  implicit object DateTimeFormat extends Format[DateTime] {
    val formats = List(
      JodaFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'"),
      JodaFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSS'Z'"),
      JodaFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"),
      JodaFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSS'Z'"),
      JodaFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss.SSSS'Z'"),
      JodaFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"),
      JodaFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss.SS'Z'"),
      JodaFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss.S'Z'")
    )

    def writes(x: DateTime) = JsString(x.toString(formats.head))

    def reads(json: JsValue) = {
      for { x <- json.validate[String] } yield {
        def loop(h: DateTimeFormatter, t: List[DateTimeFormatter]): DateTime = Try(h.parseDateTime(x)) match {
          case Success(x) => x.withZoneRetainFields(DateTimeZone.UTC)
          case Failure(x) => if (t.nonEmpty) loop(t.head, t.tail) else throw x
        }
        loop(formats.head, formats.tail)
      }
    }
  }

  implicit object NodeStateFormat extends Format[NodeState] {

    def writes(obj: NodeState) = JsString(obj.toString)

    def reads(json: JsValue) = {
      for { x <- json.validate[String] } yield NodeState(x)
    }
  }

  implicit object MemberInfoFormat extends Format[MemberInfo] {
    private val MappingFormat = Json.format[Mapping]

    def reads(json: JsValue) = {
      for { m <- MappingFormat.reads(json) } yield MemberInfo(
        instanceId = m.instanceId,
        timestamp = m.timeStamp,
        state = m.state,
        isAlive = m.isAlive,
        internalTcp = m.internalTcpIp :: m.internalTcpPort,
        externalTcp = m.externalTcpIp :: m.externalTcpPort,
        internalSecureTcp = m.internalTcpIp :: m.internalSecureTcpPort,
        externalSecureTcp = m.externalTcpIp :: m.externalSecureTcpPort,
        internalHttp = m.internalHttpIp :: m.internalHttpPort,
        externalHttp = m.externalHttpIp :: m.externalHttpPort,
        lastCommitPosition = m.lastCommitPosition,
        writerCheckpoint = m.writerCheckpoint,
        chaserCheckpoint = m.chaserCheckpoint,
        epochPosition = m.epochPosition,
        epochNumber = m.epochNumber,
        epochId = m.epochId,
        nodePriority = m.nodePriority
      )
    }

    def writes(x: MemberInfo) = {
      val m = Mapping(
        instanceId = x.instanceId,
        timeStamp = x.timestamp,
        state = x.state,
        isAlive = x.isAlive,
        internalTcpIp = x.internalTcp.getHostString,
        internalTcpPort = x.internalTcp.getPort,
        internalSecureTcpPort = x.internalSecureTcp.getPort,
        externalTcpIp = x.externalTcp.getHostString,
        externalTcpPort = x.externalTcp.getPort,
        externalSecureTcpPort = x.externalSecureTcp.getPort,
        internalHttpIp = x.internalHttp.getHostString,
        internalHttpPort = x.internalHttp.getPort,
        externalHttpIp = x.externalHttp.getHostString,
        externalHttpPort = x.externalHttp.getPort,
        lastCommitPosition = x.lastCommitPosition,
        writerCheckpoint = x.writerCheckpoint,
        chaserCheckpoint = x.chaserCheckpoint,
        epochPosition = x.epochPosition,
        epochNumber = x.epochNumber,
        epochId = x.epochId,
        nodePriority = x.nodePriority
      )

      MappingFormat.writes(m)
    }

    case class Mapping(
      instanceId:            Uuid,
      timeStamp:             DateTime,
      state:                 NodeState,
      isAlive:               Boolean,
      internalTcpIp:         String,
      internalTcpPort:       Int,
      internalSecureTcpPort: Int,
      externalTcpIp:         String,
      externalTcpPort:       Int,
      externalSecureTcpPort: Int,
      internalHttpIp:        String,
      internalHttpPort:      Int,
      externalHttpIp:        String,
      externalHttpPort:      Int,
      lastCommitPosition:    Long,
      writerCheckpoint:      Long,
      chaserCheckpoint:      Long,
      epochPosition:         Long,
      epochNumber:           Int,
      epochId:               Uuid,
      nodePriority:          Int
    )
  }

  implicit object ClusterInfoFormat extends Format[ClusterInfo] {
    private val MappingFormat = Json.format[Mapping]

    def reads(json: JsValue) = {
      for { m <- MappingFormat.reads(json) } yield ClusterInfo(
        serverAddress = m.serverIp :: m.serverPort,
        members = m.members
      )
    }

    def writes(x: ClusterInfo) = {
      val m = Mapping(
        members = x.members,
        serverIp = x.serverAddress.getHostString,
        serverPort = x.serverAddress.getPort
      )
      MappingFormat.writes(m)
    }

    private case class Mapping(members: List[MemberInfo], serverIp: String, serverPort: Int)
  }
}