package eventstore
package cluster

import java.net.InetSocketAddress
import java.time.ZonedDateTime

@SerialVersionUID(1L)
final case class MemberInfo(
    instanceId:         Uuid,
    timestamp:          ZonedDateTime,
    state:              NodeState,
    isAlive:            Boolean,
    internalTcp:        InetSocketAddress,
    externalTcp:        InetSocketAddress,
    internalSecureTcp:  InetSocketAddress,
    externalSecureTcp:  InetSocketAddress,
    internalHttp:       InetSocketAddress,
    externalHttp:       InetSocketAddress,
    lastCommitPosition: Long,
    writerCheckpoint:   Long,
    chaserCheckpoint:   Long,
    epochPosition:      Long,
    epochNumber:        Int,
    epochId:            Uuid,
    nodePriority:       Int
) extends Ordered[MemberInfo] {

  def compare(that: MemberInfo) = this.state compare that.state

  def like(other: MemberInfo): Boolean = this.instanceId == other.instanceId
}