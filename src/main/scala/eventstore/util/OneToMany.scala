package eventstore.util

import scala.collection.GenTraversableOnce

trait OneToMany[T, S, M] {
  def +(t: T): OneToMany[T, S, M]

  def -(t: T): OneToMany[T, S, M]

  def --(ts: Traversable[T]): OneToMany[T, S, M]

  def single(s: S): Option[T]

  def singleSet: Set[S]

  def many(m: M): Set[T]

  def contains(m: M): Boolean

  def manySet: Set[M]

  def values: Set[T]

  def flatMap(f: T => GenTraversableOnce[T]): OneToMany[T, S, M]
}

object OneToMany {
  def apply[T, S, M](sf: T => S, mf: T => M): OneToMany[T, S, M] = Impl[T, S, M](Map(), Map(), sf, mf)

  private case class Impl[T, S, M](
      ss: Map[S, T],
      ms: Map[M, Set[S]],
      sf: T => S,
      mf: T => M) extends OneToMany[T, S, M] {

    def +(t: T) = {
      val s = sf(t)
      val m = mf(t)
      val _ms = single(s) match {
        case None | Some(`t`) => ms
        case Some(ot) =>
          val om = mf(ot)
          if (om == m) ms else ms.updatedSet(om, _ - s)
      }
      copy(ss = ss + (s -> t), ms = _ms.updatedSet(m, _ + s))
    }

    def -(t: T) = {
      val s = sf(t)
      single(s) match {
        case Some(`t`) =>
          val m = mf(t)
          copy(ss = ss - s, ms = ms.updatedSet(m, _ - s))
        case _ => this
      }
    }

    def --(ts: Traversable[T]) = ts.foldLeft[OneToMany[T, S, M]](this)((x, t) => x - t)

    def single(s: S) = ss get s

    def singleSet = ss.keySet

    def many(m: M) = ms.getOrElse(m, Set.empty).flatMap(single)

    def contains(m: M) = ms contains m

    def manySet = ms.keySet

    def values = ss.values.toSet

    def flatMap(f: (T) => GenTraversableOnce[T]) = {
      val ts = values.flatMap(f)
      ts.foldLeft[OneToMany[T, S, M]](Impl[T, S, M](Map(), Map(), sf, mf)) { case (otm, t) => otm + t }
    }
  }

  private[eventstore] implicit class RichMap[M, S](val self: Map[M, Set[S]]) extends AnyVal {
    def getOrEmpty(m: M): Set[S] = self.getOrElse(m, Set.empty)

    def updatedSet(m: M, f: Set[S] => Set[S]): Map[M, Set[S]] = {
      val set = f(self.getOrEmpty(m))
      if (set.isEmpty) self - m
      else self + (m -> set)
    }
  }
}