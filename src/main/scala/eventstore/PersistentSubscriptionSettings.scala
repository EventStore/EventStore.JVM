package eventstore

import com.typesafe.config.{ Config, ConfigException, ConfigFactory }
import eventstore.util.ConfigHelpers._

import scala.concurrent.duration._

/**
 * Represents the settings for persistent subscription
 *
 * You can use [[eventstore.j.PersistentSubscriptionSettingsBuilder)]] from Java
 *
 * @param resolveLinkTos Whether to resolve LinkTo events automatically
 * @param startFrom Where the subscription should start from, [[EventNumber]]
 * @param extraStatistics Whether or not in depth latency statistics should be tracked on this subscription.
 * @param messageTimeout The amount of time after which a message should be considered to be timedout and retried.
 * @param maxRetryCount The maximum number of retries (due to timeout) before a message get considered to be parked
 * @param liveBufferSize The size of the buffer listening to live messages as they happen
 * @param readBatchSize The number of events read at a time when paging in history
 * @param historyBufferSize The number of events to cache when paging through history
 * @param checkPointAfter The amount of time to try to checkpoint after
 * @param minCheckPointCount The minimum number of messages to checkpoint
 * @param maxCheckPointCount maximum number of messages to checkpoint if this number is a reached a checkpoint will be forced.
 * @param maxSubscriberCount The maximum number of subscribers allowed.
 * @param consumerStrategy The [[ConsumerStrategy]] to use for distributing events to client consumers.
 */
@SerialVersionUID(1L) case class PersistentSubscriptionSettings(
  resolveLinkTos:     Boolean          = false,
  startFrom:          EventNumber      = EventNumber.Last,
  extraStatistics:    Boolean          = false,
  messageTimeout:     FiniteDuration   = 30.seconds,
  maxRetryCount:      Int              = 500,
  liveBufferSize:     Int              = 500,
  readBatchSize:      Int              = 10,
  historyBufferSize:  Int              = 20,
  checkPointAfter:    FiniteDuration   = 2.seconds,
  minCheckPointCount: Int              = 10,
  maxCheckPointCount: Int              = 1000,
  maxSubscriberCount: Int              = 0,
  consumerStrategy:   ConsumerStrategy = ConsumerStrategy.RoundRobin
)

object PersistentSubscriptionSettings {
  val Default: PersistentSubscriptionSettings = PersistentSubscriptionSettings(ConfigFactory.load())

  def apply(conf: Config): PersistentSubscriptionSettings = {
    def apply(conf: Config) = {

      def startFrom = {
        val path = "start-from"
        try EventNumber(conf getLong path) catch {
          case e: ConfigException.WrongType => conf getString path match {
            case "last" | "current" => EventNumber.Last
            case "first"            => EventNumber.First
            case _                  => throw e
          }
        }
      }

      PersistentSubscriptionSettings(
        resolveLinkTos = conf getBoolean "resolve-linkTos",
        startFrom = startFrom,
        extraStatistics = conf getBoolean "extra-statistics",
        messageTimeout = conf duration "message-timeout",
        maxRetryCount = conf getInt "max-retry-count",
        liveBufferSize = conf getInt "live-buffer-size",
        readBatchSize = conf getInt "read-batch-size",
        historyBufferSize = conf getInt "history-buffer-size",
        checkPointAfter = conf duration "checkpoint-after",
        minCheckPointCount = conf getInt "min-checkpoint-count",
        maxCheckPointCount = conf getInt "max-checkpoint-count",
        maxSubscriberCount = conf getInt "max-subscriber-count",
        consumerStrategy = ConsumerStrategy(conf getString "consumer-strategy")
      )
    }

    apply(conf getConfig "eventstore.persistent-subscription")
  }
}