package eventstore
package core

import org.specs2.mutable.Specification
import ConsumerStrategy._

class ConsumerStrategySpec extends Specification {

  "ConsumerStrategy" should {

    "parse DispatchToSingle strategy" in {
      ConsumerStrategy("DispatchToSingle") shouldEqual DispatchToSingle
    }

    "parse RoundRobin strategy" in {
      ConsumerStrategy("RoundRobin") shouldEqual RoundRobin
    }

    "parse Custom strategy" in {
      ConsumerStrategy("Custom") shouldEqual Custom("Custom")
    }

    "throw an exception if value is null" in {
      ConsumerStrategy(null) should throwA[RuntimeException]
    }

    "throw an exception if value is empty" in {
      ConsumerStrategy("") should throwA[RuntimeException]
    }
  }
}
