package eventstore
package akka
package cluster

import scala.util.Try
import java.time.{ZoneOffset, ZonedDateTime}
import cats.syntax.all._
import eventstore.core.cluster._
import eventstore.core.syntax._
import io.circe._

private[eventstore] object CirceDecoders {

  implicit val codecForZDT: Decoder[ZonedDateTime] =
    Decoder.decodeZonedDateTime.map(_.withZoneSameInstant(ZoneOffset.UTC))

  implicit val codecForNodeState: Decoder[NodeState] =
    Decoder[String].emapTry(s => Try(NodeState(s)))

  implicit val decoderForMemberInfo: Decoder[MemberInfo] = Decoder.instance {
    c =>
      c.downField("httpEndPointIp").key
       .fold(c.as[MappingPreSeries20].map(MappingPreSeries20.toMemberInfo))(_ =>
         c.as[MappingPostSeries20].map(MappingPostSeries20.toMemberInfo)
       )
  }

  final case class MappingPreSeries20(
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

  object MappingPreSeries20 {

    def toMemberInfo(m: MappingPreSeries20): MemberInfo = MemberInfo(
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

    implicit val decoderForMappingPreSeries20: Decoder[MappingPreSeries20] =
      Decoder.forProduct21(
        "instanceId",
        "timeStamp",
        "state",
        "isAlive",
        "internalTcpIp",
        "internalTcpPort",
        "internalSecureTcpPort",
        "externalTcpIp",
        "externalTcpPort",
        "externalSecureTcpPort",
        "internalHttpIp",
        "internalHttpPort",
        "externalHttpIp",
        "externalHttpPort",
        "lastCommitPosition",
        "writerCheckpoint",
        "chaserCheckpoint",
        "epochPosition",
        "epochNumber",
        "epochId",
        "nodePriority"
      )(MappingPreSeries20.apply)

  }

    ///

    final case class MappingPostSeries20(
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
      httpEndPointIp:        String,
      httpEndPointPort:      Int,
      lastCommitPosition:    Long,
      writerCheckpoint:      Long,
      chaserCheckpoint:      Long,
      epochPosition:         Long,
      epochNumber:           Int,
      epochId:               Uuid,
      nodePriority:          Int
    )

  object MappingPostSeries20 {

    def toMemberInfo(m: MappingPostSeries20): MemberInfo = MemberInfo(
      instanceId = m.instanceId,
      timestamp = m.timeStamp,
      state = m.state,
      isAlive = m.isAlive,
      internalTcp = m.internalTcpIp :: m.internalTcpPort,
      externalTcp = m.externalTcpIp :: m.externalTcpPort,
      internalSecureTcp = m.internalTcpIp :: m.internalSecureTcpPort,
      externalSecureTcp = m.externalTcpIp :: m.externalSecureTcpPort,
      internalHttp = m.httpEndPointIp :: m.httpEndPointPort,
      externalHttp = m.httpEndPointIp :: m.httpEndPointPort,
      lastCommitPosition = m.lastCommitPosition,
      writerCheckpoint = m.writerCheckpoint,
      chaserCheckpoint = m.chaserCheckpoint,
      epochPosition = m.epochPosition,
      epochNumber = m.epochNumber,
      epochId = m.epochId,
      nodePriority = m.nodePriority
    )

    implicit val decoderForMappingPostSeries20: Decoder[MappingPostSeries20] =
      Decoder.forProduct19(
        "instanceId",
        "timeStamp",
        "state",
        "isAlive",
        "internalTcpIp",
        "internalTcpPort",
        "internalSecureTcpPort",
        "externalTcpIp",
        "externalTcpPort",
        "externalSecureTcpPort",
        "httpEndPointIp",
        "httpEndPointPort",
        "lastCommitPosition",
        "writerCheckpoint",
        "chaserCheckpoint",
        "epochPosition",
        "epochNumber",
        "epochId",
        "nodePriority"
      )(MappingPostSeries20.apply)

  }

  implicit val decoderForClusterInfo: Decoder[ClusterInfo] = Decoder.instance { c =>
    (c.get[List[MemberInfo]]("members"), c.get[String]("serverIp"), c.get[Int]("serverPort"))
      .mapN((members, ip, port) => ClusterInfo(ip :: port, members)
    )
  }

}
