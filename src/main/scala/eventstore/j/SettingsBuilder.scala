package eventstore
package j

import Settings.Default
import Builder.{ ResolveLinkTosSnippet, RequireMasterSnippet }
import java.net.InetSocketAddress
import eventstore.cluster.ClusterSettings
import scala.concurrent.duration._

class SettingsBuilder extends Builder[Settings]
    with RequireMasterSnippet[SettingsBuilder]
    with ResolveLinkTosSnippet[SettingsBuilder] {

  protected var _address = Default.address
  protected var _maxReconnections = Default.maxReconnections
  protected var _reconnectionDelayMin = Default.reconnectionDelayMin
  protected var _reconnectionDelayMax = Default.reconnectionDelayMax
  protected var _defaultCredentials = Default.defaultCredentials
  protected var _heartbeatInterval = Default.heartbeatInterval
  protected var _heartbeatTimeout = Default.heartbeatTimeout
  protected var _operationMaxRetries = Default.operationMaxRetries
  protected var _connectionTimeout = Default.connectionTimeout
  protected var _operationTimeout = Default.operationTimeout
  protected var _readBatchSize = Default.readBatchSize
  protected var _bufferSize = Default.bufferSize
  protected var _cluster = Default.cluster
  protected var _http = Default.http

  def address(x: InetSocketAddress): SettingsBuilder = set { _address = x }

  def address(host: String): SettingsBuilder = address(host :: Default.address.getPort)

  def connectionTimeout(x: FiniteDuration): SettingsBuilder = set { _connectionTimeout = x }

  def connectionTimeout(length: Long, unit: TimeUnit): SettingsBuilder = connectionTimeout(FiniteDuration(length, unit))

  def connectionTimeout(seconds: Long): SettingsBuilder = connectionTimeout(seconds, SECONDS)

  def maxReconnections(x: Int): SettingsBuilder = set { _maxReconnections = x }

  def reconnectionDelayMin(x: FiniteDuration): SettingsBuilder = set { _reconnectionDelayMin = x }

  def reconnectionDelayMin(length: Long, unit: TimeUnit): SettingsBuilder = reconnectionDelayMin(FiniteDuration(length, unit))

  def reconnectionDelayMin(seconds: Long): SettingsBuilder = reconnectionDelayMin(seconds, SECONDS)

  def reconnectionDelayMax(x: FiniteDuration): SettingsBuilder = set { _reconnectionDelayMax = x }

  def reconnectionDelayMax(length: Long, unit: TimeUnit): SettingsBuilder = reconnectionDelayMax(FiniteDuration(length, unit))

  def reconnectionDelayMax(seconds: Long): SettingsBuilder = reconnectionDelayMax(seconds, SECONDS)

  def defaultCredentials(x: Option[UserCredentials]): SettingsBuilder = set { _defaultCredentials = x }

  def defaultCredentials(x: UserCredentials): SettingsBuilder = defaultCredentials(Option(x))

  def defaultCredentials(login: String, password: String): SettingsBuilder =
    defaultCredentials(UserCredentials(login = login, password = password))

  def noDefaultCredentials: SettingsBuilder = defaultCredentials(None)

  def heartbeatInterval(x: FiniteDuration): SettingsBuilder = set { _heartbeatInterval = x }

  def heartbeatInterval(length: Long, unit: TimeUnit): SettingsBuilder = heartbeatInterval(FiniteDuration(length, unit))

  def heartbeatInterval(seconds: Long): SettingsBuilder = heartbeatInterval(seconds, SECONDS)

  def heartbeatTimeout(x: FiniteDuration): SettingsBuilder = set { _heartbeatTimeout = x }

  def heartbeatTimeout(length: Long, unit: TimeUnit): SettingsBuilder = heartbeatTimeout(FiniteDuration(length, unit))

  def heartbeatTimeout(seconds: Long): SettingsBuilder = heartbeatTimeout(seconds, SECONDS)

  def operationTimeout(x: FiniteDuration): SettingsBuilder = set { _operationTimeout = x }

  def limitAttemptsForOperationTo(limit: Int): SettingsBuilder = {
    require(limit >= 1, s"limit must be >= 1 but is $limit")
    operationMaxRetries(limit - 1)
  }

  def limitRetriesForOperationTo(limit: Int): SettingsBuilder = {
    require(limit >= 0, s"limit must be >= 0 but is $limit")
    operationMaxRetries(limit)
  }

  def keepRetrying(): SettingsBuilder = {
    operationMaxRetries(-1)
  }

  def operationMaxRetries(x: Int): SettingsBuilder = set { _operationMaxRetries = x }

  def operationTimeout(length: Long, unit: TimeUnit): SettingsBuilder = operationTimeout(FiniteDuration(length, unit))

  def operationTimeout(seconds: Long): SettingsBuilder = operationTimeout(seconds, SECONDS)

  override def resolveLinkTos(x: Boolean) = super.resolveLinkTos(x)

  override def performOnAnyNode: SettingsBuilder = super.performOnAnyNode
  override def performOnMasterOnly: SettingsBuilder = super.performOnMasterOnly
  override def requireMaster(x: Boolean): SettingsBuilder = super.requireMaster(x)

  def readBatchSize(x: Int): SettingsBuilder = set { _readBatchSize = x }

  def bufferSize(x: Int): SettingsBuilder = set { _bufferSize = x }

  def cluster(x: ClusterSettings): SettingsBuilder = set { _cluster = Some(x) }

  def http(x: HttpSettings): SettingsBuilder = set { _http = x }

  def build: Settings = Settings(
    address = _address,
    maxReconnections = _maxReconnections,
    reconnectionDelayMin = _reconnectionDelayMin,
    reconnectionDelayMax = _reconnectionDelayMax,
    defaultCredentials = _defaultCredentials,
    heartbeatInterval = _heartbeatInterval,
    heartbeatTimeout = _heartbeatTimeout,
    operationMaxRetries = _operationMaxRetries,
    connectionTimeout = _connectionTimeout,
    operationTimeout = _operationTimeout,
    resolveLinkTos = _resolveLinkTos,
    requireMaster = _requireMaster,
    readBatchSize = _readBatchSize,
    bufferSize = _bufferSize,
    cluster = _cluster,
    http = _http)
}