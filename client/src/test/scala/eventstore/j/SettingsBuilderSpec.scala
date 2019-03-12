package eventstore.j

import eventstore.Settings
import org.specs2.mutable.Specification

class SettingsBuilderSpec extends Specification {

  "SettingsBuilder" should {
    "defaults should be equal to loaded from reference.conf" in {
      new SettingsBuilder().build mustEqual Settings.Default
    }
  }
}
