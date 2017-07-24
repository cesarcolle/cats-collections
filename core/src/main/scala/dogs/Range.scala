package dogs

import scala.List

/**
 * Represent an inclusive range [x, y] that can be generated by using discrete operations
 */
final case class Range[A](val start: A, val end: A) {

  /**
   * Subtract a Range from this range.
   * The result will be 0, 1 or 2 ranges
   */
  def -(range: Range[A])(implicit enum: Enum[A], order: Order[A]): Option[(Range[A], Option[Range[A]])] =
    if(order.lteqv(range.start, start)) {
      if(order.lt(range.end, start))
        Some((this, none))  // they are completely to the left of us
      else if(order.gteqv(range.end, end))
        // we are completely removed
        none
      else Some((Range(enum.succ(range.end), end), none))
    } else {
      if(order.gt(range.start, end))
        Some((this, none)) // they are completely to the right of us
      else {
        val r1 = Range(start, enum.pred(range.start))
        val r2: Option[Range[A]] = if(order.lt(range.end, end)) Some(Range(enum.succ(range.end), end)) else none
          Some((r1,r2))
      }
    }



  def +(other: Range[A])(implicit order: Order[A], enum: Enum[A]): (Range[A], Option[Range[A]]) = {
    val (l,r) = if(order.lt(this.start,other.start)) (this,other) else (other,this)

    if(order.gteqv(l.end, r.start) || enum.adj(l.end, r.start))
      (Range(l.start, order.max(l.end,r.end)), none)
    else
      (Range(l.start, l.end), Some(Range(r.start,r.end)))

  }

  def &(other: Range[A])(implicit order: Order[A]): Option[Range[A]] = {
    val start = order.max(this.start, other.start)
    val end = order.min(this.end, other.end)
    if(order.lteqv(start,end)) Some(Range(start,end)) else None
  }

  /**
    * Verify that the passed range is a sub-range
    */
  def contains(range: Range[A])(implicit order: Order[A]): Boolean =
  order.lteqv(start, range.start) && order.gteqv(end, range.end)

  /**
    * return a stream of the elements in the range
    */
  def toStreaming(implicit enum: Enum[A], order: Order[A]): Streaming[A] =
    order.compare(start,end) match {
      case 0 => Streaming(start)
      case x if x < 0 => Streaming.cons(start, Streaming.defer(Range(enum.succ(start), end).toStreaming))
      case _ => Streaming.cons(start, Streaming.defer(Range(enum.pred(start), end).toStreaming))
    }

  /**
    * Return all the values in the Range as a List
    */
  def toList(implicit enum: Enum[A], order: Order[A]): List[A] = toStreaming.toList

  /**
    * Returns range [end, start]
    */
  def reverse(implicit discrete: Enum[A], order: Order[A]): Range[A] = Range(end, start)

  /**
    * Verify is x is in range [start, end]
    */
  def contains(x: A)(implicit A: Order[A]): Boolean = A.gteqv(x, start) && A.lteqv(x, end)

  /**
    * Apply function f to each element in range [star, end]
    */
  def foreach(f: A => Unit)(implicit enum: Enum[A], order: Order[A]): Unit = {
    var i = start
    while(order.lteqv(i,end)) {
      f(i)
      i = enum.succ(i)
    }
  }

  def map[B](f: A => B): Range[B] = Range[B](f(start), f(end))

  def foldLeft[B](s: B, f: (B, A) => B)(implicit discrete: Enum[A], order: Order[A]): B = {
    var b = s
    foreach { a =>
      b = f(b,a)
    }
    b
  }
}

object Range {
  implicit def rangeShowable[A](implicit s: Show[A]): Show[Range[A]] = new Show[Range[A]] {
    override def show(f: Range[A]): String = {
      val (a, b) = (s.show(f.start), s.show(f.end))
      s"[$a, $b]"
    }
  }

}
