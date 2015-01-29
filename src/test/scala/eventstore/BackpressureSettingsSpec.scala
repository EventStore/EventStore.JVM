package eventstore

import org.specs2.mutable.Specification

class BackpressureSettingsSpec extends Specification {
  "BackpressureSettings" should {
    "throw exception if lowWatermark < 0" in {
      BackpressureSettings(lowWatermark = -1) must throwAn[IllegalArgumentException]
    }

    "throw exception if highWatermark < 0" in {
      BackpressureSettings(highWatermark = -1) must throwAn[IllegalArgumentException]
    }

    "throw exception if maxCapacity < 0" in {
      BackpressureSettings(maxCapacity = -1) must throwAn[IllegalArgumentException]
    }

    "throw exception if highWatermark > maxCapacity" in {
      BackpressureSettings(lowWatermark = BackpressureSettings().maxCapacity + 1) must throwAn[IllegalArgumentException]
    }
  }
}
