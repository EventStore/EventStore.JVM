package eventstore
package core

import scala.collection.JavaConverters.{seqAsJavaList, asScalaBuffer, collectionAsScalaIterable, iterableAsScalaIterable, asJavaCollection}
import scala.{collection => c}

private[eventstore] object ScalaCompat {

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

  implicit class ListOps[T](private val list: c.immutable.List[T]) extends AnyVal {
    def toJava: java.util.List[T] = seqAsJavaList(list)
    def toJavaCollection: java.util.Collection[T] = asJavaCollection(list)
  }

  implicit class JListOps[T](private val jlist: java.util.List[T]) extends AnyVal {
    def toScala: c.mutable.Buffer[T] = asScalaBuffer(jlist)
  }

  implicit class JCollectionOps[T](private val jcoll: java.util.Collection[T]) extends AnyVal {
    def toScala: c.immutable.List[T] = collectionAsScalaIterable(jcoll).toList
  }

  implicit class JIterableOps[T](private val jite: java.lang.Iterable[T]) extends AnyVal {
    def toScala: c.Iterable[T] = iterableAsScalaIterable(jite)
  }

}