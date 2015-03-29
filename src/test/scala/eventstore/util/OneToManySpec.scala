package eventstore.util

import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class OneToManySpec extends Specification {
  "OneToMany" should {
    "correctly override values" in new TestScope {
      val result1 = empty +
        Entry(1, "1") + // 1,1
        Entry(1, "2") + // 1,2
        Entry(2, "2") + // 1,2; 2,2
        Entry(2, "1") + // 1,2; 2,1
        Entry(3, "2") + // 1,2; 2,1; 3,2
        Entry(4, "4") // 1,2; 2,1; 3,2; 4,4

      result1.single(1) must beSome(Entry(1, "2"))
      result1.single(2) must beSome(Entry(2, "1"))
      result1.single(3) must beSome(Entry(3, "2"))
      result1.single(4) must beSome(Entry(4, "4"))
      result1.single(5) must beNone
      result1.many("1") mustEqual Set(Entry(2, "1"))
      result1.many("2") mustEqual Set(Entry(1, "2"), Entry(3, "2"))
      result1.many("3") mustEqual Set()
      result1.many("4") mustEqual Set(Entry(4, "4"))
      result1.many("5") mustEqual Set()
      result1.values mustEqual Set(Entry(1, "2"), Entry(2, "1"), Entry(3, "2"), Entry(4, "4"))
      result1.singleSet mustEqual Set(1, 2, 3, 4)
      result1.manySet mustEqual Set("1", "2", "4")

      result1.contains("1") must beTrue
      result1.contains("3") must beFalse

      (result1 - Entry(1, "3")) mustEqual result1
      (result1 -- Set(Entry(1, "3"), Entry(5, "5"), Entry(2, "2"))) mustEqual result1

      val result2 = result1 -- Set(Entry(1, "2"), Entry(2, "1"))
      result2.single(1) must beNone
      result2.single(2) must beNone
      result2.many("1") mustEqual Set()
      result2.many("2") mustEqual Set(Entry(3, "2"))
      result2.values mustEqual Set(Entry(3, "2"), Entry(4, "4"))
      result2.singleSet mustEqual Set(3, 4)
      result2.manySet mustEqual Set("2", "4")

      result2.contains("1") must beFalse
      result2.contains("2") must beTrue
    }
  }

  private trait TestScope extends Scope {
    val empty = OneToMany[Entry, Int, String](_.x1, _.x2)
  }

  case class Entry(x1: Int, x2: String)
}
