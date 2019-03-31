package eventstore
package core
package settings

import java.net.InetSocketAddress
import scala.concurrent.duration._
import com.typesafe.config.Config
import syntax._

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
 * @param cluster see [[ClusterSettings]]
 * @param http see [[HttpSettings]]
 * @param serializationParallelism The number of serialization/deserialization functions to be run in parallel
 * @param serializationOrdered Serialization done asynchronously and these futures may complete in any order, but results will be used with preserved order if set to true
 * @param connectionName Client identifier used to show a friendly name of client in Event Store.
 */
@SerialVersionUID(1L)
final case class EsSettings(
    address:                  InetSocketAddress,
    connectionTimeout:        FiniteDuration,
    maxReconnections:         Int,
    reconnectionDelayMin:     FiniteDuration,
    reconnectionDelayMax:     FiniteDuration,
    defaultCredentials:       Option[UserCredentials],
    heartbeatInterval:        FiniteDuration,
    heartbeatTimeout:         FiniteDuration,
    operationMaxRetries:      Int,
    operationTimeout:         FiniteDuration,
    resolveLinkTos:           Boolean,
    requireMaster:            Boolean,
    readBatchSize:            Int,
    cluster:                  Option[ClusterSettings],
    http:                     HttpSettings,
    serializationParallelism: Int,
    serializationOrdered:     Boolean,
    connectionName:           Option[String]
) {
  require(reconnectionDelayMin > Duration.Zero, "reconnectionDelayMin must be > 0")
  require(reconnectionDelayMax > Duration.Zero, "reconnectionDelayMax must be > 0")
  require(operationTimeout > Duration.Zero, "operationTimeout must be > 0")
  require(serializationParallelism > 0, "serializationParallelism must be > 0")
}


object EsSettings {

  def apply(conf: Config): EsSettings = {

    def cluster = ClusterSettings.opt(conf)

    def load(c: Config): EsSettings = {

      def operationTimeout = {
        val deprecated = "operation-timeout"
        if (c hasPath deprecated) c duration deprecated
        else c duration "operation.timeout"
      }

      def credentials = for {
        l <- Option(c getString "credentials.login")
        p <- Option(c getString "credentials.password")
      } yield UserCredentials(login = l, password = p)

      def address =
        (c getString "address.host") :: (c getInt "address.port")

      def connectionName =
        Option(c getString "connection-name").filter(_.nonEmpty)

      EsSettings(
        address                  = address,
        connectionTimeout        = c duration "connection-timeout",
        maxReconnections         = c getInt "max-reconnections",
        reconnectionDelayMin     = c duration "reconnection-delay.min",
        reconnectionDelayMax     = c duration "reconnection-delay.max",
        defaultCredentials       = credentials,
        heartbeatInterval        = c duration "heartbeat.interval",
        heartbeatTimeout         = c duration "heartbeat.timeout",
        operationMaxRetries      = c getInt "operation.max-retries",
        operationTimeout         = operationTimeout,
        resolveLinkTos           = c getBoolean "resolve-linkTos",
        requireMaster            = c getBoolean "require-master",
        readBatchSize            = c getInt "read-batch-size",
        cluster                  = cluster,
        http                     = HttpSettings(c),
        serializationParallelism = c getInt "serialization-parallelism",
        serializationOrdered     = c getBoolean "serialization-ordered",
        connectionName           = connectionName
      )
    }

    load(conf getConfig "eventstore")
  }
}
