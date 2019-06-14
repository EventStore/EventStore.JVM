package eventstore
package core

import scala.jdk.CollectionConverters._
import scala.{collection => c}

private[eventstore] object ScalaCompat {

  type IterableOnce[T] = c.IterableOnce[T]
  val IterableOnce     = c.IterableOnce

  implicit class IterableOps[T](private val iterable: c.Iterable[T]) extends AnyVal {
    def toLazyList: LazyList[T] = iterable.to(LazyList)
  }

  implicit class ListOps[T](private val list: c.immutable.List[T]) extends AnyVal {
    def toJava: java.util.List[T] = list.asJava
    def toJavaCollection: java.util.Collection[T] = list.asJavaCollection
  }

  implicit class JListOps[T](private val jlist: java.util.List[T]) extends AnyVal {
    def toScala: c.mutable.Buffer[T] = jlist.asScala
  }

  implicit class JCollectionOps[T](private val jcoll: java.util.Collection[T]) extends AnyVal {
    def toScala: c.immutable.List[T] = jcoll.asScala.toList
  }

  implicit class JIterableOps[T](private val jite: java.lang.Iterable[T]) extends AnyVal {
    def toScala: c.Iterable[T] = jite.asScala
  }

}