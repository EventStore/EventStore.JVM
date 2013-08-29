package eventstore.util

/**
 * @author Yaroslav Klymko
 */
case class BidirectionalMap[X, Y](
    xy: Map[X, Y] = Map.empty[X, Y],
    yx: Map[Y, X] = Map.empty[Y, X]) {

  def +(x: X, y: Y) =
    if ((xy contains x) && (yx contains y)) this
    else copy(xy = xy + (x -> y), yx = yx + (y -> x))

  def -(y: Y): BidirectionalMap[X, Y] = yx.get(y).fold(this)(x => BidirectionalMap(xy - x, yx - y))

  def x(x: X): Option[Y] = xy.get(x)
  def y(y: Y): Option[X] = yx.get(y)

  def isEmpty: Boolean = xy.isEmpty && yx.isEmpty
}