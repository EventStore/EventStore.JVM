package eventstore

import org.specs2.mutable.Specification

class SettingsSpec extends Specification {
  "Settings" should {
    "defaults should be equal to loaded from reference.conf" in {
      Settings() mustEqual Settings.Default
    }
  }
}
