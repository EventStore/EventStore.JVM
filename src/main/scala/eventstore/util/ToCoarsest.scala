package eventstore.util

import scala.concurrent.duration._

// it is a backport of method introduced in Scala 2.11 - Duration.toCoarsest
object ToCoarsest {
  def apply(x: FiniteDuration): FiniteDuration = {
    def loop(length: Long, unit: TimeUnit): FiniteDuration = {
      def coarserOrThis(coarser: TimeUnit, divider: Int) =
        if (length % divider == 0) loop(length / divider, coarser)
        else if (unit == x.unit) x
        else FiniteDuration(length, unit)

      unit match {
        case DAYS         => FiniteDuration(length, unit)
        case HOURS        => coarserOrThis(DAYS, 24)
        case MINUTES      => coarserOrThis(HOURS, 60)
        case SECONDS      => coarserOrThis(MINUTES, 60)
        case MILLISECONDS => coarserOrThis(SECONDS, 1000)
        case MICROSECONDS => coarserOrThis(MILLISECONDS, 1000)
        case NANOSECONDS  => coarserOrThis(MICROSECONDS, 1000)
      }
    }

    if (x.unit == DAYS || x.length == 0) x
    else loop(x.length, x.unit)
  }
}
