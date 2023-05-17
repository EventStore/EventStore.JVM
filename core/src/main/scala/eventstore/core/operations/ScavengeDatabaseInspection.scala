package eventstore
package core
package operations

import ScavengeError._
import Inspection.Decision._

private[eventstore] object ScavengeDatabaseInspection
    extends ErrorInspection[ScavengeDatabaseResponse, ScavengeError] {

  def decision(error: ScavengeError) = {
    val result = error match {
      case InProgress   => ScavengeInProgressException
      case Unauthorized => ScavengeUnauthorizedException
      case Unrecognized => UnrecognizedException
    }
    Fail(result)
  }
}
