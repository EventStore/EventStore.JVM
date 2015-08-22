package eventstore.util

import com.typesafe.config.Config

import scala.concurrent.duration._

object ConfigHelpers {
  implicit class ConfigOps(val self: Config) extends AnyVal {
    def duration(path: String): FiniteDuration = {
      ToCoarsest(FiniteDuration(self.getDuration(path, MILLISECONDS), MILLISECONDS))
    }

    def durationOpt(path: String): Option[FiniteDuration] = {
      if (self hasPath path) Some(duration(path)) else None
    }
  }
}
