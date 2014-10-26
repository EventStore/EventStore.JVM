package eventstore

import com.typesafe.config.ConfigFactory
import org.specs2.mutable.Specification
import scala.concurrent.duration._

class SettingsSpec extends Specification with util.NoConversions {
  "Settings" should {
    "defaults should be equal to loaded from reference.conf" in {
      Settings() mustEqual Settings.Default
    }

    "use deprecated 'operation-timeout' prior new 'operation.timeout' for backward compatibility" in {
      val config = ConfigFactory.parseString(
        """
          |eventstore {
          |   operation-timeout=1m
          |   operation.timeout=1s
          |}
        """.stripMargin).withFallback(ConfigFactory.load())
      Settings(config).operationTimeout mustEqual 1.minute
    }
  }
}
