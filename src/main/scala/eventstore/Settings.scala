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
  val Default: Settings = Settings(ConfigFactory.load())

  def apply(conf: Config): Settings = {
    def apply(conf: Config): Settings = Settings(
      address = new InetSocketAddress(conf getString "address.host", conf getInt "address.port"),
      maxReconnections = conf getInt "maxReconnections",
      reconnectionDelayMin = FiniteDuration(conf getMilliseconds "reconnectionDelay.min", MILLISECONDS),
      reconnectionDelayMax = FiniteDuration(conf getMilliseconds "reconnectionDelay.max", MILLISECONDS),
      defaultCredentials = for {
        l <- Option(conf getString "defaultCredentials.login")
        p <- Option(conf getString "defaultCredentials.password")
      } yield UserCredentials(login = l, password = p),
      heartbeatInterval = FiniteDuration(conf getMilliseconds "heartbeat.interval", MILLISECONDS),
      heartbeatTimeout = FiniteDuration(conf getMilliseconds "heartbeat.timeout", MILLISECONDS),
      connectionTimeout = FiniteDuration(conf getMilliseconds "connectionTimeout", MILLISECONDS),
      operationTimeout = FiniteDuration(conf getMilliseconds "operationTimeout", MILLISECONDS),
      backpressure = BackpressureSettings(conf))
    apply(conf.getConfig("eventstore"))
  }
}

/**
 * see [[akka.io.BackpressureBuffer]]
 */
case class BackpressureSettings(lowWatermark: Int = 100, highWatermark: Int = 10000, maxCapacity: Int = 1000000) {
  require(lowWatermark >= 0, s"lowWatermark must be >= 0, but is $lowWatermark")
  require(highWatermark >= lowWatermark, s"highWatermark must be >= lowWatermark, but $highWatermark < $lowWatermark")
  require(maxCapacity >= highWatermark, s"maxCapacity >= highWatermark, but $maxCapacity < $highWatermark")
}

object BackpressureSettings {
  def apply(conf: Config): BackpressureSettings = BackpressureSettings(
    lowWatermark = conf getInt "backpressure.lowWatermark",
    highWatermark = conf getInt "backpressure.highWatermark",
    maxCapacity = conf getInt "backpressure.maxCapacity")
}