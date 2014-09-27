package eventstore
package cluster

import java.util.Date
import org.specs2.mutable.Specification
import spray.json._
import scala.io.Source
import NodeState.{ Slave, Master }

class ClusterProtocolSpec extends Specification {
  "ClusterProtocol" should {
    "parse gossip.json" in {
      val is = getClass.getResourceAsStream("gossip.json")
      val json = Source.fromInputStream(is, "UTF-8").getLines().mkString("\n").parseJson
      val clusterInfo = ClusterProtocol.ClusterInfoFormat.read(json)

      clusterInfo mustEqual ClusterInfo("127.0.0.1" :: 2113, List(
        MemberInfo(
          instanceId = "4534f211-10af-45f1-87c0-8398215328be".uuid,
          timestamp = "2014-09-24T19:53:18.59055Z".date,
          state = Slave,
          isAlive = false,
          internalTcp = "127.0.0.1" :: 3111,
          externalTcp = "127.0.0.1" :: 3112,
          internalSecureTcp = "127.0.0.1" :: 0,
          externalSecureTcp = "127.0.0.1" :: 0,
          internalHttp = "127.0.0.1" :: 3113,
          externalHttp = "127.0.0.1" :: 3114,
          lastCommitPosition = 115826,
          writerCheckpoint = 131337,
          chaserCheckpoint = 131337,
          epochPosition = 131149,
          epochNumber = 1,
          epochId = "b5c64b95-9c8f-4e1c-82f4-f619118edb73".uuid,
          nodePriority = 0),
        MemberInfo(
          instanceId = "8f680215-3abe-4aed-9d06-c5725776303d".uuid,
          timestamp = "2014-09-24T19:53:20.035753Z".date,
          state = Master,
          isAlive = true,
          internalTcp = "127.0.0.1" :: 2111,
          externalTcp = "127.0.0.1" :: 2112,
          internalSecureTcp = "127.0.0.1" :: 0,
          externalSecureTcp = "127.0.0.1" :: 0,
          internalHttp = "127.0.0.1" :: 2113,
          externalHttp = "127.0.0.1" :: 2114,
          lastCommitPosition = 115826,
          writerCheckpoint = 131337,
          chaserCheckpoint = 131337,
          epochPosition = 131149,
          epochNumber = 1,
          epochId = "b5c64b95-9c8f-4e1c-82f4-f619118edb73".uuid,
          nodePriority = 0),
        MemberInfo(
          instanceId = "44baf256-55a4-4ccc-b6ef-7bd383c88991".uuid,
          timestamp = "2014-09-24T19:53:19.086667Z".date,
          state = Slave,
          isAlive = true,
          internalTcp = "127.0.0.1" :: 1111,
          externalTcp = "127.0.0.1" :: 1112,
          internalSecureTcp = "127.0.0.1" :: 0,
          externalSecureTcp = "127.0.0.1" :: 0,
          internalHttp = "127.0.0.1" :: 1113,
          externalHttp = "127.0.0.1" :: 1114,
          lastCommitPosition = 115826,
          writerCheckpoint = 131337,
          chaserCheckpoint = 131337,
          epochPosition = 131149,
          epochNumber = 1,
          epochId = "b5c64b95-9c8f-4e1c-82f4-f619118edb73".uuid,
          nodePriority = 0)))
    }
  }

  implicit class RichString(self: String) {
    def date: Date = ClusterProtocol.DateFormat.format.parse(self)
  }
}
