package eventstore
package util

import scala.concurrent.duration.{FiniteDuration, MILLISECONDS}
import com.typesafe.config.Config

object ConfigHelpers {

  implicit class ConfigOps(val self: Config) extends AnyVal {
    def duration(path: String): FiniteDuration = {
      FiniteDuration(self.getDuration(path, MILLISECONDS), MILLISECONDS).toCoarsest
    }

    def durationOpt(path: String): Option[FiniteDuration] = {
      if (self hasPath path) Some(duration(path)) else None
    }
  }
}
