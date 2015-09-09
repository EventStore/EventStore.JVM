package eventstore.j

import eventstore.PersistentSubscriptionSettings.Default
import eventstore.{ ConsumerStrategy, EventNumber, PersistentSubscriptionSettings }

import scala.concurrent.duration.{ FiniteDuration, _ }

class PersistentSubscriptionSettingsBuilder
    extends Builder[PersistentSubscriptionSettings]
    with ChainSet[PersistentSubscriptionSettingsBuilder] {

  protected var _resolveLinkTos = Default.resolveLinkTos
  protected var _startFrom = Default.startFrom
  protected var _extraStatistics = Default.extraStatistics
  protected var _messageTimeout = Default.messageTimeout
  protected var _maxRetryCount = Default.maxRetryCount
  protected var _liveBufferSize = Default.liveBufferSize
  protected var _readBatchSize = Default.readBatchSize
  protected var _historyBufferSize = Default.historyBufferSize
  protected var _checkPointAfter = Default.checkPointAfter
  protected var _minCheckPointCount = Default.minCheckPointCount
  protected var _maxCheckPointCount = Default.maxCheckPointCount
  protected var _maxSubscriberCount = Default.maxSubscriberCount
  protected var _consumerStrategy = Default.consumerStrategy

  def resolveLinkTos(x: Boolean): PersistentSubscriptionSettingsBuilder = set {
    _resolveLinkTos = x
  }

  def resolveLinkTos: PersistentSubscriptionSettingsBuilder = resolveLinkTos(true)

  def doNotResolveLinkTos(): PersistentSubscriptionSettingsBuilder = resolveLinkTos(false)

  def startFrom(x: EventNumber): PersistentSubscriptionSettingsBuilder = set {
    _startFrom = x
  }

  def startFrom(x: Int): PersistentSubscriptionSettingsBuilder = {
    startFrom(EventNumber(x))
  }

  def startFromBeginning: PersistentSubscriptionSettingsBuilder = {
    startFrom(EventNumber.First)
  }

  def startFromCurrent: PersistentSubscriptionSettingsBuilder = {
    startFrom(EventNumber.Current)
  }

  def withExtraStatistic: PersistentSubscriptionSettingsBuilder = set {
    _extraStatistics = true
  }

  def messageTimeout(x: FiniteDuration): PersistentSubscriptionSettingsBuilder = set {
    _messageTimeout = x
  }

  def messageTimeout(length: Long, unit: TimeUnit): PersistentSubscriptionSettingsBuilder = {
    messageTimeout(FiniteDuration(length, unit))
  }

  def messageTimeout(millis: Long): PersistentSubscriptionSettingsBuilder = {
    messageTimeout(millis, MILLISECONDS)
  }

  def maxRetryCount(x: Int): PersistentSubscriptionSettingsBuilder = set {
    _maxRetryCount = x
  }

  def liveBufferSize(x: Int): PersistentSubscriptionSettingsBuilder = set {
    _liveBufferSize = x
  }

  def readBatchSize(x: Int): PersistentSubscriptionSettingsBuilder = set {
    _readBatchSize = x
  }

  def historyBufferSize(x: Int): PersistentSubscriptionSettingsBuilder = set {
    _historyBufferSize = x
  }

  def checkPointAfter(x: FiniteDuration): PersistentSubscriptionSettingsBuilder = set {
    _checkPointAfter = x
  }

  def checkPointAfter(length: Long, unit: TimeUnit): PersistentSubscriptionSettingsBuilder = {
    checkPointAfter(FiniteDuration(length, unit))
  }

  def checkPointAfter(seconds: Long): PersistentSubscriptionSettingsBuilder = {
    checkPointAfter(seconds, SECONDS)
  }

  def minCheckPointCount(x: Int): PersistentSubscriptionSettingsBuilder = set {
    _minCheckPointCount = x
  }

  def maxCheckPointCount(x: Int): PersistentSubscriptionSettingsBuilder = set {
    _maxCheckPointCount = x
  }

  def maxSubscriberCount(x: Int): PersistentSubscriptionSettingsBuilder = set {
    _maxSubscriberCount = x
  }

  def consumerStrategy(x: ConsumerStrategy): PersistentSubscriptionSettingsBuilder = set {
    _consumerStrategy = x
  }

  def consumerStrategy(x: String): PersistentSubscriptionSettingsBuilder = {
    consumerStrategy(ConsumerStrategy(x))
  }

  def roundRobin: PersistentSubscriptionSettingsBuilder = {
    consumerStrategy(ConsumerStrategy.RoundRobin)
  }

  def dispatchToSingle: PersistentSubscriptionSettingsBuilder = {
    consumerStrategy(ConsumerStrategy.DispatchToSingle)
  }

  def build: PersistentSubscriptionSettings = {
    PersistentSubscriptionSettings(
      resolveLinkTos = _resolveLinkTos,
      startFrom = _startFrom,
      extraStatistics = _extraStatistics,
      messageTimeout = _messageTimeout,
      maxRetryCount = _maxRetryCount,
      liveBufferSize = _liveBufferSize,
      readBatchSize = _readBatchSize,
      historyBufferSize = _historyBufferSize,
      checkPointAfter = _checkPointAfter,
      minCheckPointCount = _minCheckPointCount,
      maxCheckPointCount = _maxCheckPointCount,
      maxSubscriberCount = _maxSubscriberCount,
      consumerStrategy = _consumerStrategy
    )
  }
}
