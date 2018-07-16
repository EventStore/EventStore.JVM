package eventstore

import eventstore.cluster.ClusterSettings
import eventstore.util.ToCoarsest

import scala.concurrent.duration._
import java.net.InetSocketAddress

import akka.http.scaladsl.model.Uri
import com.typesafe.config.{ Config, ConfigFactory }

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
 * @param bufferSize The size of the buffer in element count
 * @param bufferOverflowStrategy  Strategy that is used when elements cannot fit inside the buffer
 * @param http Url to access eventstore though the Http API
 * @param serializationParallelism The number of serialization/deserialization functions to be run in parallel
 * @param serializationOrdered Serialization done asynchronously and these futures may complete in any order, but results will be used with preserved order if set to true
 * @param connectionName Client identifier used to show a friendly name of client in Event Store.
 */
@SerialVersionUID(1L)
case class Settings(
    address:                  InetSocketAddress       = "127.0.0.1" :: 1113,
    connectionTimeout:        FiniteDuration          = 1.second,
    maxReconnections:         Int                     = 100,
    reconnectionDelayMin:     FiniteDuration          = 250.millis,
    reconnectionDelayMax:     FiniteDuration          = 10.seconds,
    defaultCredentials:       Option[UserCredentials] = Some(UserCredentials.DefaultAdmin),
    heartbeatInterval:        FiniteDuration          = 500.millis,
    heartbeatTimeout:         FiniteDuration          = 5.seconds,
    operationMaxRetries:      Int                     = 10,
    operationTimeout:         FiniteDuration          = 30.seconds,
    resolveLinkTos:           Boolean                 = false,
    requireMaster:            Boolean                 = true,
    readBatchSize:            Int                     = 500,
    bufferSize:               Int                     = 100000,
    bufferOverflowStrategy:   OverflowStrategy        = OverflowStrategy.Fail,
    cluster:                  Option[ClusterSettings] = None,
    http:                     HttpSettings            = HttpSettings(),
    serializationParallelism: Int                     = 8,
    serializationOrdered:     Boolean                 = true,
    connectionName:           Option[String]          = Some("jvm-client")
) {
  require(reconnectionDelayMin > Duration.Zero, "reconnectionDelayMin must be > 0")
  require(reconnectionDelayMax > Duration.Zero, "reconnectionDelayMax must be > 0")
  require(operationTimeout > Duration.Zero, "operationTimeout must be > 0")
  require(serializationParallelism > 0, "serializationParallelism must be > 0")
}

object Settings {
  lazy val Default: Settings = Settings(ConfigFactory.load())

  def apply(conf: Config): Settings = {
    def cluster = ClusterSettings.opt(conf)
    def apply(conf: Config): Settings = {
      def duration(path: String) = ToCoarsest(FiniteDuration(conf.getDuration(path, MILLISECONDS), MILLISECONDS))

      def operationTimeout = {
        val deprecated = "operation-timeout"
        if (conf hasPath deprecated) duration(deprecated)
        else duration("operation.timeout")
      }

      Settings(
        address = (conf getString "address.host") :: (conf getInt "address.port"),
        connectionTimeout = duration("connection-timeout"),
        maxReconnections = conf getInt "max-reconnections",
        reconnectionDelayMin = duration("reconnection-delay.min"),
        reconnectionDelayMax = duration("reconnection-delay.max"),
        defaultCredentials = for {
          l <- Option(conf getString "credentials.login")
          p <- Option(conf getString "credentials.password")
        } yield UserCredentials(login = l, password = p),
        heartbeatInterval = duration("heartbeat.interval"),
        heartbeatTimeout = duration("heartbeat.timeout"),
        operationMaxRetries = conf getInt "operation.max-retries",
        operationTimeout = operationTimeout,
        resolveLinkTos = conf getBoolean "resolve-linkTos",
        requireMaster = conf getBoolean "require-master",
        readBatchSize = conf getInt "read-batch-size",
        bufferSize = conf getInt "buffer-size",
        bufferOverflowStrategy = OverflowStrategy(conf getString "buffer-overflow-strategy"),
        cluster = cluster,
        http = HttpSettings(conf),
        serializationParallelism = conf getInt "serialization-parallelism",
        serializationOrdered = conf getBoolean "serialization-ordered",
        connectionName = Option(conf getString "connection-name").filter(_.nonEmpty)
      )
    }
    apply(conf getConfig "eventstore")
  }

  /**
   * Java API
   */
  def getInstance(): Settings = Default
}

case class HttpSettings(uri: Uri = Uri("http://127.0.0.1:2113")) {
  require(List("http", "https").contains(uri.scheme), s"Scheme must be either http or https but is ${uri.scheme}")
}

object HttpSettings {
  def apply(conf: Config): HttpSettings = {
    val protocol = conf getString "http.protocol"
    val host = conf getString "address.host"
    val port = conf getInt "http.port"
    val prefix = conf getString "http.prefix"

    HttpSettings(uri = Uri(s"$protocol://$host:$port$prefix"))
  }
}
