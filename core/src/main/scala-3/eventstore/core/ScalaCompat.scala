package eventstore
package core

import scala.{collection => c}

private[eventstore] object ScalaCompat {

  val JavaConverters = scala.jdk.CollectionConverters

  type IterableOnce[T] = c.IterableOnce[T]
  val IterableOnce     = c.IterableOnce

  implicit class IterableOps[T](private val iterable: c.Iterable[T]) extends AnyVal {
    def toLazyList: LazyList[T] = iterable.to(LazyList)
  }

}