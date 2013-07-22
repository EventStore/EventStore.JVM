package eventstore.util

/**
 * @author Yaroslav Klymko
 */
case class BidirectionalMap[X, Y](xy: Map[X, Y] = Map.empty[X, Y],
                                  yx: Map[Y, X] = Map.empty[Y, X]) {

  def +(x: X, y: Y) =
    if ((xy contains x) && (yx contains y)) this
    else copy(xy = xy + (x -> y), yx = yx + (y -> x))

  def byX(x: X): Option[Y] = xy.get(x)
  def byY(y: Y): Option[X] = yx.get(y)
}