package eventstore.j

/**
 * @author Yaroslav Klymko
 */
trait Builder[T] {
  def set(f: => Any): this.type = {
    f
    this
  }

  def build: T
}