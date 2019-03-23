package eventstore
package core
package util

import scala.concurrent.duration._
import org.specs2.mutable.Specification

class DelayedRetrySpec extends Specification {

  "DelayedRetry.opt" should {

    "return none if 0 retries" in {
      DelayedRetry.opt(0, 1.second, 1.second) must beNone
    }

    "return some if -1 retry" in {
      DelayedRetry.opt(-1, 1.second, 1.second) must beSome
    }

    "return some if 1 retry" in {
      DelayedRetry.opt(1, 1.second, 1.second) must beSome
    }
  }

  "DelayedRetry.next" should {

    "return some with some -1" in {
      val retry = DelayedRetry.opt(-1, 1.second, 1.second).get
      val next = retry.next
      next must beSome
      next.get.left mustEqual -1
    }

    "return some with decreased retries" in {
      val retry = DelayedRetry.opt(2, 1.second, 1.second).get
      val next = retry.next
      next must beSome
      next.get.left mustEqual 1
    }

    "return some with increased delay" in {
      val retry = DelayedRetry.opt(2, 1.second, 5.seconds).get
      val next = retry.next
      next must beSome
      next.get.delay mustEqual 2.seconds
    }

    "return some with increased delay but not bigger then max delay" in {
      val retry = DelayedRetry.opt(2, 1.second, 1.second).get
      val next = retry.next
      next must beSome
      next.get.delay mustEqual 1.second
    }

    "return none" in {
      val retry = DelayedRetry.opt(1, 1.second, 1.second).get
      retry.next must beNone
    }
  }
}
