package eventstore
package cluster

import java.net.InetSocketAddress
import java.util.{ Date, UUID }
import spray.json._

object ClusterProtocol extends DefaultJsonProtocol {
  implicit object UuidFormat extends JsonFormat[Uuid] {
    def write(x: Uuid) = JsString(x.toString)

    def read(json: JsValue) = json match {
      case JsString(x) => UUID.fromString(x)
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
      val x = MappingFormat.read(json)

      MemberInfo(
        instanceId = x.instanceId,
        timeStamp = x.timeStamp,
        state = x.state,
        isAlive = x.isAlive,
        internalTcp = new InetSocketAddress(x.internalTcpIp, x.internalTcpPort),
        internalSecureTcp = new InetSocketAddress(x.internalTcpIp, x.internalSecureTcpPort),
        externalTcp = new InetSocketAddress(x.externalTcpIp, x.externalTcpPort),
        externalSecureTcp = new InetSocketAddress(x.externalTcpIp, x.externalSecureTcpPort),
        internalHttp = new InetSocketAddress(x.internalHttpIp, x.internalHttpPort),
        externalHttp = new InetSocketAddress(x.externalHttpIp, x.externalHttpPort),
        lastCommitPosition = x.lastCommitPosition,
        writerCheckpoint = x.writerCheckpoint,
        chaserCheckpoint = x.chaserCheckpoint,
        epochPosition = x.epochPosition,
        epochNumber = x.epochNumber,
        epochId = x.epochId,
        nodePriority = x.nodePriority)
    }

    def write(x: MemberInfo) = {
      val mapping = Mapping(
        instanceId = x.instanceId,
        timeStamp = x.timeStamp,
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

      MappingFormat.write(mapping)
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
      val x = MappingFormat.read(json)
      ClusterInfo(
        members = x.members,
        serverAddress = new InetSocketAddress(x.serverIp, x.serverPort))
    }

    def write(x: ClusterInfo) = {
      val mapping = Mapping(
        members = x.members,
        serverIp = x.serverAddress.getHostString,
        serverPort = x.serverAddress.getPort)
      MappingFormat.write(mapping)
    }

    private case class Mapping(members: List[MemberInfo], serverIp: String, serverPort: Int)
  }
}