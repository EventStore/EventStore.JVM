package eventstore
package akka
package cluster

import java.time.{ZoneOffset, ZonedDateTime}
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import spray.json._
import eventstore.core.cluster.NodeState.{Leader, Follower}
import eventstore.core.cluster.{ClusterInfo, MemberInfo}
import eventstore.core.syntax._
import ClusterJsonProtocol._

class ClusterJsonProtocolSpec extends Specification {

  def readResource(p: String) = {
    val is = getClass.getResourceAsStream(p)
    try {
      scala.io.Source.fromInputStream(is).mkString
    } finally {
      is.close()
    }
  }

  "ClusterProtocol" should {

    "parse gossip.json" in new TestScope {
      val text = readResource("gossip.json")
      val read = text.parseJson.convertTo[ClusterInfo]
      read mustEqual clusterInfo
    }

    "parse gossip-es-series20.json" in new TestScope {
      val text = readResource("gossip-es-series20.json")
      val read = text.parseJson.convertTo[ClusterInfo]
      read mustEqual clusterInfo
    }

    "read & write cluster info" in new TestScope {
      val json = clusterInfo.toJson
      json.convertTo[ClusterInfo] mustEqual clusterInfo
    }
  }

  trait TestScope extends Scope {
    val clusterInfo = ClusterInfo("127.0.0.1" :: 2113, List(
      MemberInfo(
        instanceId = "4534f211-10af-45f1-87c0-8398215328be".uuid,
        timestamp = ZonedDateTime.of(2014, 9, 24, 19, 53, 18, 590550000, ZoneOffset.UTC),
        state = Follower,
        isAlive = false,
        internalTcp = "127.0.0.1" :: 3111,
        externalTcp = "127.0.0.1" :: 3112,
        internalSecureTcp = "127.0.0.1" :: 0,
        externalSecureTcp = "127.0.0.1" :: 0,
        internalHttp = "127.0.0.1" :: 3113,
        externalHttp = "127.0.0.1" :: 3113,
        lastCommitPosition = 115826,
        writerCheckpoint = 131337,
        chaserCheckpoint = 131337,
        epochPosition = 131149,
        epochNumber = 1,
        epochId = "b5c64b95-9c8f-4e1c-82f4-f619118edb73".uuid,
        nodePriority = 0
      ),
      MemberInfo(
        instanceId = "8f680215-3abe-4aed-9d06-c5725776303d".uuid,
        timestamp = ZonedDateTime.of(2015, 1, 29, 10, 23, 9, 41562100, ZoneOffset.UTC),
        state = Leader,
        isAlive = true,
        internalTcp = "127.0.0.1" :: 2111,
        externalTcp = "127.0.0.1" :: 2112,
        internalSecureTcp = "127.0.0.1" :: 0,
        externalSecureTcp = "127.0.0.1" :: 0,
        internalHttp = "127.0.0.1" :: 2113,
        externalHttp = "127.0.0.1" :: 2113,
        lastCommitPosition = 115826,
        writerCheckpoint = 131337,
        chaserCheckpoint = 131337,
        epochPosition = 131149,
        epochNumber = 1,
        epochId = "b5c64b95-9c8f-4e1c-82f4-f619118edb73".uuid,
        nodePriority = 0
      ),
      MemberInfo(
        instanceId = "44baf256-55a4-4ccc-b6ef-7bd383c88991".uuid,
        timestamp = ZonedDateTime.of(2015, 1, 26, 19, 52, 40, 0, ZoneOffset.UTC),
        state = Follower,
        isAlive = true,
        internalTcp = "127.0.0.1" :: 1111,
        externalTcp = "127.0.0.1" :: 1112,
        internalSecureTcp = "127.0.0.1" :: 0,
        externalSecureTcp = "127.0.0.1" :: 0,
        internalHttp = "127.0.0.1" :: 1113,
        externalHttp = "127.0.0.1" :: 1113,
        lastCommitPosition = 115826,
        writerCheckpoint = 131337,
        chaserCheckpoint = 131337,
        epochPosition = 131149,
        epochNumber = 1,
        epochId = "b5c64b95-9c8f-4e1c-82f4-f619118edb73".uuid,
        nodePriority = 0
      )
    ))
  }
}
