package eventstore
package core

import org.specs2.mutable.Specification

class ExpectedVersionSpec extends Specification {

  "ExpectedVersion" should {

    "return Exact version for value >= 0" in {
      ExpectedVersion(1) mustEqual ExpectedVersion.Exact(1)
    }

    "return NoStream version for -1" in {
      ExpectedVersion(-1) mustEqual ExpectedVersion.NoStream
    }

    "return Any version for value -2" in {
      ExpectedVersion(-2) mustEqual ExpectedVersion.Any
    }
  }

  "ExpectedVersion.Exact" should {

    "throw exception for value < 0" in {
      ExpectedVersion.Exact(-1) must throwAn[IllegalArgumentException]
    }

    "support EventNumber.Exact" in {
      ExpectedVersion.Exact(EventNumber.Exact(1)) mustEqual ExpectedVersion.Exact(1)
    }

    "have readable toString" in {
      ExpectedVersion.Exact(1).toString mustEqual "Expected.Version(1)"
    }
  }

  "ExpectedVersion.Any" should {

    "have readable toString" in {
      ExpectedVersion.Any.toString mustEqual "Expected.AnyVersion"
    }
  }

  "ExpectedVersion.NoStream" should {

    "have readable toString" in {
      ExpectedVersion.NoStream.toString mustEqual "Expected.NoStream"
    }
  }
}
