package eventstore
package cluster

import java.util.Date
import spray.json._

object ClusterProtocol extends DefaultJsonProtocol {
  implicit object UuidFormat extends JsonFormat[Uuid] {
    def write(x: Uuid) = JsString(x.toString)

    def read(json: JsValue) = json match {
      case JsString(x) => x.uuid
      case _           => deserializationError("Uuid expected")
    }
  }

  implicit object DateFormat extends JsonFormat[Date] {
    val format = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'")

    def write(obj: Date) = JsString(format.format(obj))

    def read(json: JsValue) = json match {
      case JsString(x) => DateFormat.format.parse(x)
      case _           => deserializationError("Date expected")
    }
  }

  implicit object NodeStateFormat extends JsonFormat[NodeState] {
    def write(obj: NodeState) = JsString(obj.toString)
    def read(json: JsValue) = json match {
      case JsString(x) => NodeState(x)
      case _           => deserializationError("NodeState expected")
    }
  }

  implicit object MemberInfoFormat extends JsonFormat[MemberInfo] {
    private val MappingFormat = jsonFormat21(Mapping)

    def read(json: JsValue) = {
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
        nodePriority = m.nodePriority)
    }

    def write(x: MemberInfo) = {
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
        nodePriority = x.nodePriority)

      MappingFormat.write(m)
    }

    case class Mapping(
      instanceId: Uuid,
      timeStamp: Date,
      state: NodeState,
      isAlive: Boolean,
      internalTcpIp: String,
      internalTcpPort: Int,
      internalSecureTcpPort: Int,
      externalTcpIp: String,
      externalTcpPort: Int,
      externalSecureTcpPort: Int,
      internalHttpIp: String,
      internalHttpPort: Int,
      externalHttpIp: String,
      externalHttpPort: Int,
      lastCommitPosition: Long,
      writerCheckpoint: Long,
      chaserCheckpoint: Long,
      epochPosition: Long,
      epochNumber: Int,
      epochId: Uuid,
      nodePriority: Int)
  }

  implicit object ClusterInfoFormat extends RootJsonFormat[ClusterInfo] {
    private val MappingFormat = jsonFormat3(Mapping.apply)

    def read(json: JsValue) = {
      val m = MappingFormat.read(json)
      ClusterInfo(
        serverAddress = m.serverIp :: m.serverPort,
        members = m.members)
    }

    def write(x: ClusterInfo) = {
      val m = Mapping(
        members = x.members,
        serverIp = x.serverAddress.getHostString,
        serverPort = x.serverAddress.getPort)
      MappingFormat.write(m)
    }

    private case class Mapping(members: List[MemberInfo], serverIp: String, serverPort: Int)
  }
}