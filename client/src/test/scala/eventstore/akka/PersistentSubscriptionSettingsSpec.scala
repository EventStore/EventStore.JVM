package eventstore
package akka

import com.typesafe.config.ConfigFactory
import org.specs2.mutable.Specification

class PersistentSubscriptionSettingsSpec extends Specification {

  "PersistentSubscriptionSettings" should {

    "defaults should be equal to loaded from reference.conf" in {
      PersistentSubscriptionSettings() mustEqual PersistentSubscriptionSettings.Default
    }

    "parse custom consumer strategy" in {
      val actual = settings(""" eventstore.persistent-subscription.consumer-strategy = "custom" """)
      val expected = PersistentSubscriptionSettings(consumerStrategy = ConsumerStrategy.Custom("custom"))
      actual shouldEqual expected
    }

    "parse DispatchToSingle strategy" in {
      val actual = settings(""" eventstore.persistent-subscription.consumer-strategy = DispatchToSingle """)
      val expected = PersistentSubscriptionSettings(consumerStrategy = ConsumerStrategy.DispatchToSingle)
      actual shouldEqual expected
    }

    "parse `last` as start from" in {
      val actual = settings(""" eventstore.persistent-subscription.start-from = last """)
      val expected = PersistentSubscriptionSettings(startFrom = EventNumber.Last)
      actual shouldEqual expected
    }

    "parse `current` as start from" in {
      val actual = settings(""" eventstore.persistent-subscription.start-from = current """)
      val expected = PersistentSubscriptionSettings(startFrom = EventNumber.Current)
      actual shouldEqual expected
    }

    "parse `first` as start from" in {
      val actual = settings(""" eventstore.persistent-subscription.start-from = first """)
      val expected = PersistentSubscriptionSettings(startFrom = EventNumber.First)
      actual shouldEqual expected
    }

    "parse number as start from" in {
      val actual = settings(""" eventstore.persistent-subscription.start-from = 10 """)
      val expected = PersistentSubscriptionSettings(startFrom = EventNumber(10))
      actual shouldEqual expected
    }
  }

  def settings(s: String) = {
    val conf = (ConfigFactory parseString s) withFallback ConfigFactory.load()
    PersistentSubscriptionSettings(conf)
  }
}