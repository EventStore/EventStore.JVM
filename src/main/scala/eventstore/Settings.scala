package eventstore

import scala.concurrent.duration._
import java.net.InetSocketAddress

/**
 * @author Yaroslav Klymko
 */
case class Settings(address: InetSocketAddress = new InetSocketAddress("127.0.0.1", 1113),
//                    clientAddress: InetSocketAddress = new InetSocketAddress("127.0.0.1", 0),

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
maxRetries: Int = 10 ,


//                             /// <summary>
//                             /// The maximum number of times to allow for reconnection
//                             /// </summary>
//                             public readonly int MaxReconnections;
maxReconnections: Int = 10,


//                             /// <summary>
//                             /// Whether or not to require EventStore to refuse serving read or write request if it is not master
//                             /// </summary>
//                             public readonly bool RequireMaster;
requireMaster:Boolean  = true,


//                             /// <summary>
//                             /// The amount of time to delay before attempting to reconnect
//                             /// </summary>
//                             public readonly TimeSpan ReconnectionDelay;
reconnectionDelay: FiniteDuration = 100.millis


//                             /// <summary>
//                             /// The amount of time before an operation is considered to have timed out
//                             /// </summary>
//                             public readonly TimeSpan OperationTimeout;
//                             /// <summary>
//                             /// The amount of time that timeouts are checked in the system.
//                             /// </summary>
//                             public readonly TimeSpan OperationTimeoutCheckPeriod;
//
//                             public readonly UserCredentials DefaultUserCredentials;
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
//                             public readonly TimeSpan HeartbeatInterval;
//                             public readonly TimeSpan HeartbeatTimeout;
//                             public readonly TimeSpan ClientConnectionTimeout;
                     )

object Settings {
  val default = Settings(
    maxRetries = 10,
    maxReconnections = 10,
    requireMaster = true,
    reconnectionDelay = 100.millis)

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
        private TimeSpan _heartbeatInterval = TimeSpan.FromMilliseconds(750);
        private TimeSpan _heartbeatTimeout = TimeSpan.FromMilliseconds(1500);
        private TimeSpan _clientConnectionTimeout = TimeSpan.FromMilliseconds(1000);


        */
}