package eventstore
package akka

import scala.concurrent.duration._
import com.typesafe.config.ConfigFactory
import org.specs2.mutable.Specification

class SettingsSpec extends Specification {

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
        """.stripMargin
      ).withFallback(ConfigFactory.load())
      Settings(config).operationTimeout mustEqual 1.minute
    }

    "throw exception if reconnectionDelayMin <= Zero" in {
      Settings(reconnectionDelayMin = Duration.Zero) must throwAn[IllegalArgumentException]
    }

    "throw exception if reconnectionDelayMax <= Zero" in {
      Settings(reconnectionDelayMax = Duration.Zero) must throwAn[IllegalArgumentException]
    }

    "throw exception if operationTimeout <= Zero" in {
      Settings(operationTimeout = Duration.Zero) must throwAn[IllegalArgumentException]
    }

    "throw exception if serializationParallelism <= 0" in {
      Settings(serializationParallelism = 0) must throwAn[IllegalArgumentException]
    }
  }
}
