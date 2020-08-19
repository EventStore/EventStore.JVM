package eventstore
package akka

import java.net.InetSocketAddress
import scala.concurrent.duration._
import _root_.akka.http.scaladsl.model.Uri
import com.typesafe.config.{Config, ConfigFactory}
import eventstore.core.syntax._
import eventstore.core.{settings => cs}
import eventstore.{akka => a}

/**
 * @param address IP & port of Event Store
 * @param connectionTimeout The desired connection timeout
 * @param maxReconnections Maximum number of reconnections before backing off, -1 to reconnect forever
 * @param reconnectionDelayMin Delay before first reconnection
 * @param reconnectionDelayMax Maximum delay on reconnections
 * @param defaultCredentials The [[UserCredentials]] to use for operations where other [[UserCredentials]] are not explicitly supplied.
 * @param heartbeatInterval The interval at which to send heartbeat messages.
 * @param heartbeatTimeout The interval after which an unacknowledged heartbeat will cause the connection to be considered faulted and disconnect.
 * @param operationMaxRetries The maximum number of operation retries
 * @param operationTimeout The amount of time before an operation is considered to have timed out
 * @param resolveLinkTos Whether to resolve LinkTo events automatically
 * @param requireMaster Whether or not to require Event Store to refuse serving read or write request if it is not master
 * @param readBatchSize Number of events to be retrieved by client as single message
 * @param enableTcpTls Whether TLS should be enabled for TCP connections. TLS setup is loaded from akka.ssl-config when enabled.
 * @param http Url to access eventstore though the Http API. When https is used, TLS setup is loaded from akka.ssl-config.
 * @param serializationParallelism The number of serialization/deserialization functions to be run in parallel
 * @param serializationOrdered Serialization done asynchronously and these futures may complete in any order, but results will be used with preserved order if set to true
 * @param connectionName Client identifier used to show a friendly name of client in Event Store.
 * @param bufferSize The size of the buffer in element count
 * @param bufferOverflowStrategy  Strategy that is used when elements cannot fit inside the buffer
 */
@SerialVersionUID(1L)
final case class Settings(
  address:                  InetSocketAddress          = "127.0.0.1" :: 1113,
  connectionTimeout:        FiniteDuration             = 1.second,
  maxReconnections:         Int                        = 100,
  reconnectionDelayMin:     FiniteDuration             = 250.millis,
  reconnectionDelayMax:     FiniteDuration             = 10.seconds,
  defaultCredentials:       Option[UserCredentials]    = Some(UserCredentials.DefaultAdmin),
  heartbeatInterval:        FiniteDuration             = 500.millis,
  heartbeatTimeout:         FiniteDuration             = 5.seconds,
  operationMaxRetries:      Int                        = 10,
  operationTimeout:         FiniteDuration             = 30.seconds,
  resolveLinkTos:           Boolean                    = false,
  requireMaster:            Boolean                    = true,
  readBatchSize:            Int                        = 500,
  enableTcpTls:             Boolean                    = false,
  cluster:                  Option[cs.ClusterSettings] = None,
  http:                     HttpSettings               = HttpSettings(),
  serializationParallelism: Int                        = 8,
  serializationOrdered:     Boolean                    = true,
  connectionName:           Option[String]             = Some("jvm-client"),
  bufferSize:               Int                        = 100000,
  bufferOverflowStrategy:   a.OverflowStrategy         = a.OverflowStrategy.Fail
) {
  require(reconnectionDelayMin > Duration.Zero, "reconnectionDelayMin must be > 0")
  require(reconnectionDelayMax > Duration.Zero, "reconnectionDelayMax must be > 0")
  require(operationTimeout > Duration.Zero, "operationTimeout must be > 0")
  require(serializationParallelism > 0, "serializationParallelism must be > 0")
}

object Settings {

  lazy val Default = Settings(ConfigFactory.load())

  def apply(conf: Config): Settings = {

    def es = cs.EsSettings(conf)
    def hs = HttpSettings(es.http)

    def load(c: Config): Settings = {

      val bufferSize             = c getInt "buffer-size"
      val bufferOverflowStrategy = a.OverflowStrategy(c getString "buffer-overflow-strategy")

      Settings(
        address                  = es.address,
        connectionTimeout        = es.connectionTimeout,
        maxReconnections         = es.maxReconnections,
        reconnectionDelayMin     = es.reconnectionDelayMin,
        reconnectionDelayMax     = es.reconnectionDelayMax,
        defaultCredentials       = es.defaultCredentials,
        heartbeatInterval        = es.heartbeatInterval,
        heartbeatTimeout         = es.heartbeatTimeout,
        operationMaxRetries      = es.operationMaxRetries,
        operationTimeout         = es.operationTimeout,
        resolveLinkTos           = es.resolveLinkTos,
        requireMaster            = es.requireMaster,
        readBatchSize            = es.readBatchSize,
        enableTcpTls             = es.enableTcpTls,
        cluster                  = es.cluster,
        http                     = hs,
        serializationParallelism = es.serializationParallelism,
        serializationOrdered     = es.serializationOrdered,
        connectionName           = es.connectionName,
        bufferSize               = bufferSize,
        bufferOverflowStrategy   = bufferOverflowStrategy
      )
    }

    load(conf getConfig "eventstore")
  }

  /**
   * Java API
   */
  def getInstance(): Settings = Default
}

@SerialVersionUID(1L)
final case class HttpSettings(uri: Uri = Uri("http://127.0.0.1:2113")) {
  require(List("http", "https").contains(uri.scheme), s"Scheme must be either http or https but is ${uri.scheme}")
}

object HttpSettings {
  def apply(hs: cs.HttpSettings): HttpSettings =
    HttpSettings(Uri(s"${hs.protocol}://${hs.host}:${hs.port}${hs.prefix}"))
}
