package eventstore

import scala.concurrent.duration._
import java.net.InetSocketAddress
import com.typesafe.config.{ ConfigFactory, Config }

case class Settings(
  address: InetSocketAddress = new InetSocketAddress("127.0.0.1", 1113),
  maxReconnections: Int = 100,
  reconnectionDelayMin: FiniteDuration = 250.millis,
  reconnectionDelayMax: FiniteDuration = 10.seconds,
  defaultCredentials: Option[UserCredentials] = Some(UserCredentials.defaultAdmin),
  heartbeatInterval: FiniteDuration = 500.millis,
  heartbeatTimeout: FiniteDuration = 2.seconds,
  connectionTimeout: FiniteDuration = 1.second,
  operationTimeout: FiniteDuration = 5.seconds,
  backpressure: BackpressureSettings = BackpressureSettings())

object Settings {
  lazy val config: Config = ConfigFactory.load()
  lazy val default: Settings = Settings(config)
  lazy val readBatchSize = config.getInt("eventstore.read-batch-size")

  def apply(conf: Config): Settings = {
    def apply(conf: Config): Settings = {
      def duration(path: String) = FiniteDuration(conf.getDuration(path, MILLISECONDS), MILLISECONDS)
      Settings(
        address = new InetSocketAddress(conf getString "address.host", conf getInt "address.port"),
        maxReconnections = conf getInt "max-reconnections",
        reconnectionDelayMin = duration("reconnection-delay.min"),
        reconnectionDelayMax = duration("reconnection-delay.max"),
        defaultCredentials = for {
          l <- Option(conf getString "credentials.login")
          p <- Option(conf getString "credentials.password")
        } yield UserCredentials(login = l, password = p),
        heartbeatInterval = duration("heartbeat.interval"),
        heartbeatTimeout = duration("heartbeat.timeout"),
        connectionTimeout = duration("connection-timeout"),
        operationTimeout = duration("operation-timeout"),
        backpressure = BackpressureSettings(conf))
    }
    apply(conf.getConfig("eventstore"))
  }

  /**
   * Java API
   */
  def getInstance(): Settings = default
}

/**
 * see [[eventstore.pipeline.BackpressureBuffer]]
 */
case class BackpressureSettings(lowWatermark: Int = 100, highWatermark: Int = 10000, maxCapacity: Int = 1000000) {
  require(lowWatermark >= 0, s"lowWatermark must be >= 0, but is $lowWatermark")
  require(highWatermark >= lowWatermark, s"highWatermark must be >= lowWatermark, but $highWatermark < $lowWatermark")
  require(maxCapacity >= highWatermark, s"maxCapacity >= highWatermark, but $maxCapacity < $highWatermark")
}

object BackpressureSettings {
  def apply(conf: Config): BackpressureSettings = BackpressureSettings(
    lowWatermark = conf getInt "backpressure.low-watermark",
    highWatermark = conf getInt "backpressure.high-watermark",
    maxCapacity = conf getInt "backpressure.max-capacity")
}