package eventstore
package akka
package cluster

import java.time.{ZoneOffset, ZonedDateTime}
import scala.util.Try
import eventstore.core.syntax._
import eventstore.core.cluster.{ClusterInfo, MemberInfo, NodeState}
import spray.json._

private[eventstore] object ClusterJsonProtocol extends DefaultJsonProtocol {

  implicit object UuidFormat extends JsonFormat[Uuid] {
    def write(x: Uuid): JsValue = JsString(x.toString)
    def read(json: JsValue): Uuid = json match {
      case JsString(x) => x.uuid
      case _           => deserializationError(s"expected UUID as string, got $json")
    }
  }

  implicit object DateTimeFormat extends JsonFormat[ZonedDateTime] {
    def write(x: ZonedDateTime): JsValue = JsString(x.toString)
    def read(json: JsValue): ZonedDateTime = json match {
      case JsString(x) => Try(ZonedDateTime.parse(x).withZoneSameInstant(ZoneOffset.UTC)).
                          getOrElse(deserializationError(s"could not find a date/time format for $x"))
      case _           => deserializationError(s"expected date/time as string, got $json")
    }
  }

  implicit object NodeStateFormat extends JsonFormat[NodeState] {
    def write(x: NodeState): JsValue = JsString(x.toString)

    def read(json: JsValue): NodeState = json match {
      case JsString(value) => NodeState(value)
      case _               => deserializationError(s"expected node state as string, got $json")
    }
  }

  implicit object MemberInfoFormat extends RootJsonFormat[MemberInfo] {
    private val MappingFormat = jsonFormat21(Mapping)

    def read(json: JsValue): MemberInfo = {
      val m = MappingFormat.read(json)

      MemberInfo(
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

    def write(x: MemberInfo): JsValue = {
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

      MappingFormat.write(m)
    }

    final case class Mapping(
      instanceId:            Uuid,
      timeStamp:             ZonedDateTime,
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

  implicit object ClusterInfoFormat extends RootJsonFormat[ClusterInfo] {
    private val MappingFormat = jsonFormat3(Mapping)

    def read(json: JsValue): ClusterInfo = {
      val m = MappingFormat.read(json)
      ClusterInfo(
        serverAddress = m.serverIp :: m.serverPort,
        members = m.members
      )
    }

    def write(x: ClusterInfo): JsValue = {
      val m = Mapping(
        members = x.members,
        serverIp = x.serverAddress.getHostString,
        serverPort = x.serverAddress.getPort
      )
      MappingFormat.write(m)
    }

    private final case class Mapping(members: List[MemberInfo], serverIp: String, serverPort: Int)
  }
}
