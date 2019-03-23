package eventstore
package core

import org.specs2.mutable.Specification

class EventNumberSpec extends Specification {

  "EventNumber" should {

    "return First for Forward" in {
      EventNumber(ReadDirection.Forward) mustEqual EventNumber.First
    }

    "return Last for Backward" in {
      EventNumber(ReadDirection.Backward) mustEqual EventNumber.Last
    }

    "return Last for number < 0" in {
      EventNumber(-1) mustEqual EventNumber.Last
    }
  }

  "EventNumber.Exact" should {

    "throw exception if number < 0" in {
      EventNumber.Exact(-1) must throwAn[IllegalArgumentException]
    }

    "return None if number < 0" in {
      EventNumber.Exact.opt(-1) must beNone
    }

    "have readable toString" in {
      EventNumber.Exact(0).toString mustEqual "EventNumber(0)"
    }
  }

  "EventNumber.Last" should {

    "have readable toString" in {
      EventNumber.Last.toString mustEqual "EventNumber.Last"
    }
  }

  "EventNumber.Range" should {

    "return range for start only" in {
      EventNumber.Range(1) mustEqual EventNumber.Range(1, 1)
    }

    "throw exception if start > end" in {
      EventNumber.Range(2, 1) must throwAn[IllegalArgumentException]
    }

    "return none start > end" in {
      EventNumber.Range.opt(2, 1) must beNone
    }

    "have readable toString" in {
      EventNumber.Range(1, 2).toString mustEqual "EventNumber.Range(1 to 2)"
    }
  }
}