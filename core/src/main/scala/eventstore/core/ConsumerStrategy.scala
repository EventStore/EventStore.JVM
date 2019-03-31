package eventstore
package core

sealed trait ConsumerStrategy

/**
 * System supported consumer strategies for use with persistent subscriptions.
 */
object ConsumerStrategy {
  val Values: Set[ConsumerStrategy] = Set(DispatchToSingle, RoundRobin)

  def apply(name: String): ConsumerStrategy = {
    Values find { _.toString equalsIgnoreCase name } getOrElse Custom(name)
  }

  /**
   * Distributes events to a single client until it is full. Then round robin to the next client.
   */
  @SerialVersionUID(1L) case object DispatchToSingle extends ConsumerStrategy

  /**
   * Distribute events to each client in a round robin fashion.
   */
  @SerialVersionUID(1L) case object RoundRobin extends ConsumerStrategy

  /**
   * Unknown, not predefined strategy
   */
  @SerialVersionUID(1L) final case class Custom private[eventstore] (value: String) extends ConsumerStrategy {
    require(value != null, "value must not be null")
    require(value.nonEmpty, "value must not be empty")

    override def toString = value
  }
}