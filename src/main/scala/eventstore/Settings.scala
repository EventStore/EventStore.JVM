package eventstore

import scala.concurrent.duration._
import java.net.InetSocketAddress

/**
 * @author Yaroslav Klymko
 */
case class Settings(
    address: InetSocketAddress = new InetSocketAddress("127.0.0.1", 1113),
    //                             /// <summary>
    //                             /// The <see cref="ILogger"/> that this connection will use
    //                             /// </summary>
    //                             public readonly ILogger Log;
    //                             /// <summary>
    //                             /// Whether or not do excessive logging of <see cref="EventStoreConnection"/> internal logic.
    //                             /// </summary>
    //                             public readonly bool VerboseLogging;
    //                             /// <summary>
    //                             /// The maximum number of outstanding items allowed in the queue
    //                             /// </summary>
    //                             public readonly int MaxQueueSize;
    //                             /// <summary>
    //                             /// The maximum number of allowed asynchronous operations to be in process
    //                             /// </summary>
    //                             public readonly int MaxConcurrentItems;

    //                             /// <summary>
    //                             /// The maximum number of retry attempts
    //                             /// </summary>
    //                             public readonly int MaxRetries;
    //                    maxRetries: Int = 10,

    // The maximum number of times to allow for reconnection
    maxReconnections: Int = 10,

    // Whether or not to refuse serving read or write request if it is not master
    requireMaster: Boolean = true,

    // The amount of time to delay before attempting to reconnect
    reconnectionDelay: FiniteDuration = 100.millis,

    //                             /// <summary>
    //                             /// The amount of time before an operation is considered to have timed out
    //                             /// </summary>
    //                             public readonly TimeSpan OperationTimeout;
    //                             /// <summary>
    //                             /// The amount of time that timeouts are checked in the system.
    //                             /// </summary>
    //                             public readonly TimeSpan OperationTimeoutCheckPeriod;
    //
    userCredentials: Option[UserCredentials] = Some(UserCredentials.defaultAdmin),
    //                             public readonly bool UseSslConnection;
    //                             public readonly string TargetHost;
    //                             public readonly bool ValidateServer;
    //
    //                             /// <summary>
    //                             /// Raised whenever the internal error occurs
    //                             /// </summary>
    //                             public Action<IEventStoreConnection, Exception> ErrorOccurred;
    //                             /// <summary>
    //                             /// Raised whenever the connection is closed
    //                             /// </summary>
    //                             public Action<IEventStoreConnection, string> Closed;
    //                             /// <summary>
    //                             /// Raised whenever the internal connection is connected to the event store
    //                             /// </summary>
    //                             public Action<IEventStoreConnection, IPEndPoint> Connected;
    //                             /// <summary>
    //                             /// Raised whenever the internal connection is disconnected from the event store
    //                             /// </summary>
    //                             public Action<IEventStoreConnection, IPEndPoint> Disconnected;
    //                             /// <summary>
    //                             /// Raised whenever the internal connection is reconnecting to the event store
    //                             /// </summary>
    //                             public Action<IEventStoreConnection> Reconnecting;
    //                             /// <summary>
    //                             /// Raised whenever the connection default user credentials authentication fails
    //                             /// </summary>
    //                             public Action<IEventStoreConnection, string> AuthenticationFailed;
    //
    //                             public readonly bool FailOnNoServerResponse;

    heartbeatInterval: FiniteDuration = 750.millis,
    heartbeatTimeout: FiniteDuration = 2.seconds,
    connectionTimeout: FiniteDuration = 1.second,
    backpressureSettings: BackpressureSettings = BackpressureSettings()) {
  require(
    heartbeatInterval < heartbeatTimeout,
    s"heartbeatInterval must be < heartbeatTimeout, but $heartbeatInterval >= $heartbeatTimeout")
}

object Settings {
  /*public const int DefaultMaxQueueSize = 5000;
        public const int DefaultMaxConcurrentItems = 5000;
        public const int DefaultMaxOperationRetries = 10;
        public const int DefaultMaxReconnections = 10;

        public const bool DefaultRequireMaster = true;

        public static readonly TimeSpan DefaultReconnectionDelay = TimeSpan.FromMilliseconds(100);
        public static readonly TimeSpan DefaultOperationTimeout = TimeSpan.FromSeconds(7);
        public static readonly TimeSpan DefaultOperationTimeoutCheckPeriod = TimeSpan.FromSeconds(1);

        public static readonly TimeSpan TimerPeriod = TimeSpan.FromMilliseconds(200);

        public const int DefaultMaxClusterDiscoverAttempts = 10;
        public const int DefaultClusterManagerExternalHttpPort = 30778;


        private bool _failOnNoServerResponse;
        */
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