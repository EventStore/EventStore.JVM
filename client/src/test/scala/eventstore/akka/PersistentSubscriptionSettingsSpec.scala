package eventstore
package akka

import com.typesafe.config.ConfigFactory
import org.specs2.mutable.Specification
import eventstore.core.settings.{PersistentSubscriptionSettings => PSS}

class PersistentSubscriptionSettingsSpec extends Specification {

  "PersistentSubscriptionSettings" should {

    "defaults should be equal to loaded from reference.conf" in {
      PSS() mustEqual PersistentSubscriptionSettings.Default
    }

    "parse custom consumer strategy" in {
      val actual = settings(""" eventstore.persistent-subscription.consumer-strategy = "custom" """)
      val expected = PSS(consumerStrategy = ConsumerStrategy.Custom("custom"))
      actual shouldEqual expected
    }

    "parse DispatchToSingle strategy" in {
      val actual = settings(""" eventstore.persistent-subscription.consumer-strategy = DispatchToSingle """)
      val expected = PSS(consumerStrategy = ConsumerStrategy.DispatchToSingle)
      actual shouldEqual expected
    }

    "parse `last` as start from" in {
      val actual = settings(""" eventstore.persistent-subscription.start-from = last """)
      val expected = PSS(startFrom = EventNumber.Last)
      actual shouldEqual expected
    }

    "parse `current` as start from" in {
      val actual = settings(""" eventstore.persistent-subscription.start-from = current """)
      val expected = PSS(startFrom = EventNumber.Current)
      actual shouldEqual expected
    }

    "parse `first` as start from" in {
      val actual = settings(""" eventstore.persistent-subscription.start-from = first """)
      val expected = PSS(startFrom = EventNumber.First)
      actual shouldEqual expected
    }

    "parse number as start from" in {
      val actual = settings(""" eventstore.persistent-subscription.start-from = 10 """)
      val expected = PSS(startFrom = EventNumber(10))
      actual shouldEqual expected
    }
  }

  def settings(s: String) =
    PSS((ConfigFactory parseString s) withFallback ConfigFactory.load())
}