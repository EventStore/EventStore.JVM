package eventstore
package core

import scala.{collection => c}

private[eventstore] object ScalaCompat {

  val reflectiveCalls = scala.language.reflectiveCalls
  val JavaConverters = scala.collection.JavaConverters

  type IterableOnce[+X] = c.TraversableOnce[X]
  val IterableOnce      = c.TraversableOnce
  type LazyList[+T]     = c.immutable.Stream[T]

  object LazyList {
    val #::                   = c.immutable.Stream.#::
    def empty[T]: LazyList[T] = c.immutable.Stream.empty[T]
  }

  implicit class IterableOps[T](private val iterable: c.Iterable[T]) extends AnyVal {
    def toLazyList: LazyList[T] = iterable.to[LazyList]
  }

}