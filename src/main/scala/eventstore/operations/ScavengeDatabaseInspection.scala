package eventstore
package operations

import ScavengeError._
import Inspection.Decision._

private[eventstore] object ScavengeDatabaseInspection
    extends AbstractInspection[ScavengeDatabaseCompleted, ScavengeError] {

  def decision(error: ScavengeError) = {
    val result = error match {
      case InProgress => ScavengeInProgressException
      case Failed(x)  => new ScavengeFailedException(x.orNull)
    }
    Fail(result)
  }
}
