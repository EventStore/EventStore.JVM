package eventstore

import scala.concurrent.duration._
import java.net.InetSocketAddress

case class Settings(
    address: InetSocketAddress = new InetSocketAddress("127.0.0.1", 1113),
    // The maximum number of times to allow for reconnection
    maxReconnections: Int = 10,
    // Whether or not to refuse serving read or write request if it is not master
    requireMaster: Boolean = true,
    // The amount of time to delay before attempting to reconnect
    reconnectionDelay: FiniteDuration = 100.millis,
    defaultCredentials: Option[UserCredentials] = Some(UserCredentials.defaultAdmin),
    heartbeatInterval: FiniteDuration = 750.millis,
    heartbeatTimeout: FiniteDuration = 2.seconds,
    connectionTimeout: FiniteDuration = 1.second,
    responseTimeout: FiniteDuration = 5.seconds, // TODO 4J & rename to OperationTimeout
    backpressureSettings: BackpressureSettings = BackpressureSettings()) {
  require(
    heartbeatInterval < heartbeatTimeout,
    s"heartbeatInterval must be < heartbeatTimeout, but $heartbeatInterval >= $heartbeatTimeout")
}

object Settings {
  val Default = Settings()
}

/**
 * see [[akka.io.BackpressureBuffer]]
 */
case class BackpressureSettings(
    lowWatermark: Int = 100,
    highWatermark: Int = 10000,
    maxCapacity: Int = 1000000) {
  require(lowWatermark >= 0, s"lowWatermark must be >= 0, but is $lowWatermark")
  require(highWatermark >= lowWatermark, s"highWatermark must be >= lowWatermark, but $highWatermark < $lowWatermark")
  require(maxCapacity >= highWatermark, s"maxCapacity >= highWatermark, but $maxCapacity < $highWatermark")
}