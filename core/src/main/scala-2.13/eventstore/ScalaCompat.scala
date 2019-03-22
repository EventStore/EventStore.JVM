package eventstore

private[eventstore] object ScalaCompat {
  implicit class IterableOps[T](private val iterable: Iterable[T]) extends AnyVal {
    def toLazyList: LazyList[T] = iterable.to(LazyList)
  }
}