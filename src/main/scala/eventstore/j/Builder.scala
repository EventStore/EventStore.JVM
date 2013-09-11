package eventstore
package j

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

trait ReadBuilder[T] extends Builder[T] {
  protected var _resolveLinkTos = false

  def resolveLinkTos(x: Boolean) = set {
    _resolveLinkTos = x
  }

  protected var _requireMaster: Boolean = true

  def requireMaster(x: Boolean) = set {
    _requireMaster = x
  }
}