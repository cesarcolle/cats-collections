package cats.collections
package tests

import cats.{Order, Show, UnorderedFoldable}
import cats.laws.discipline.UnorderedFoldableTests
import cats.kernel.laws.discipline.OrderTests
import cats.tests.CatsSuite
import org.scalacheck.{Arbitrary, Cogen, Gen}

class HeapSpec extends CatsSuite {

  implicit override val generatorDrivenConfig: PropertyCheckConfiguration =
    checkConfiguration.copy(
      minSuccessful = 1000
    )

  def heapGen[A: Order](size: Int, agen: Gen[A]): Gen[Heap[A]] = {
    val listA = Gen.listOfN(size, agen)
    val startWith1 =
      listA.map {
        case Nil => Heap.empty[A]
        case h :: tail => tail.foldLeft(Heap(h))(_.add(_))
      }
    val addOnly = listA.map(_.foldLeft(Heap.empty[A])(_.add(_)))
    val heapify = listA.map(Heap.fromIterable(_))
    // This one is recursive and with small probability can get quite deep
    val addMoreAndRemove: Gen[Heap[A]] =
      for {
        extraSize <- Gen.choose(1, size + 1)
        withExtra <- Gen.lzy(heapGen[A](size + extraSize, agen))
      } yield (0 until extraSize).foldLeft(withExtra) { (h, _) => h.remove }
    // we can also make smaller one and add to it:
    val smallerAdd =
      if (size > 0) {
        for {
          a <- agen
          heap <- heapGen(size - 1, agen)
        } yield heap + a
      }
      else Gen.const(Heap.empty[A])

    Gen.frequency((2, addOnly), (3, startWith1), (5, heapify), (1, addMoreAndRemove), (1, smallerAdd))
  }

  implicit def arbHeap[A: Arbitrary: Order]: Arbitrary[Heap[A]] =
    Arbitrary {
      Gen.sized(heapGen[A](_, Arbitrary.arbitrary[A]))
    }

  implicit def cogenHeap[A: Cogen: Order]: Cogen[Heap[A]] =
    Cogen[List[A]].contramap { h: Heap[A] => h.toList }

  checkAll("UnorderedFoldable[Heap]",
    UnorderedFoldableTests[Heap].unorderedFoldable[Long, Int])

  checkAll("Order[Heap[Int]]", OrderTests[Heap[Int]].order)

  test("sorted")(
    forAll { (list: List[Int]) =>

      val heap = list.foldLeft(Heap.empty[Int])((h, i) => h.add(i))

      heap.toList should be(list.sorted)

    })

  test("heapify is sorted") {
    forAll { (list: List[Int]) =>
      val heap = Heap.heapify(list)
      val heapList = heap.toList
      val heap1 = Heap.heapify(heapList)

      assert(heapList == list.sorted)
      assert(Order[Heap[Int]].eqv(heap, heap1))
    }
  }

  test("adding increases size") {
    forAll { (heap: Heap[Int], x: Int) =>
      val heap1 = heap + x
      assert(heap1.size == (heap.size + 1))
    }
  }

  test("remove decreases size") {
    forAll { (heap: Heap[Int]) =>
      val heap1 = heap.remove
      assert((heap1.size == (heap.size - 1)) || (heap1.isEmpty && heap.isEmpty))
    }

    assert(Heap.empty[Int].remove == Heap.empty[Int])
  }

  test("size is consistent with isEmpty/nonEmpty") {
    forAll { (heap: Heap[Int]) =>
      assert(heap.isEmpty == (heap.size == 0))
      assert(heap.nonEmpty == (heap.size > 0))
      assert(heap.isEmpty == (!heap.nonEmpty))
    }
  }

  test("height is O(log N) for all heaps") {
    forAll { (heap: Heap[Int]) =>
      val bound = math.log(heap.size.toDouble) / math.log(2.0) + 1.0
      assert(heap.isEmpty || heap.height.toDouble <= bound)
    }
  }

  test("heapify is the same as adding") {
    forAll { (init: List[Int]) =>
      val heap1 = Heap.fromIterable(init)
      val heap2 = init.foldLeft(Heap.empty[Int])(_.add(_))
      assert(heap1.toList == heap2.toList)
    }
  }

  test("getMin after removing one is >= before") {
    forAll { (heap: Heap[Int]) =>
      val min0 = heap.getMin
      val min1 = heap.remove.getMin

      (min0, min1) match {
        case (None, next) => assert(next.isEmpty)
        case (_, None) => assert(heap.size == 1)
        case (Some(m0), Some(m1)) =>
          assert(m0 <= m1)
      }
    }
  }

  test("Heap.getMin is the real minimum") {
    def heapLaw(heap: Heap[Int]) =
      heap.getMin match {
        case None => assert(heap.isEmpty)
        case Some(min) =>
          val heap1 = heap.remove
          assert(heap1.isEmpty || {
            min <= heap1.toList.min
          })
      }

    forAll { (heap: Heap[Int]) =>
      heapLaw(heap)
      // even after removing this is true
      heapLaw(heap.remove)
      heapLaw(heap.remove.remove)
      heapLaw(heap.remove.remove.remove)
    }

    assert(Heap.empty[Int].getMin.isEmpty)
  }

  test("Heap.foldLeft is consistent with toList.foldLeft") {
    forAll { (heap: Heap[Int], init: Long, fn: (Long, Int) => Long) =>
      assert(heap.foldLeft(init)(fn) == heap.toList.foldLeft(init)(fn))
    }
  }

  test("Show[Heap[Int]] works like toList.mkString") {
    forAll { (heap: Heap[Int]) =>
      assert(Show[Heap[Int]].show(heap) == heap.toList.mkString("Heap(", ", ", ")"))
    }
  }

  test("Order[Heap[Int]] works like List[Int]") {
    forAll { (a: Heap[Int], b: Heap[Int]) =>
      assert(Order[Heap[Int]].compare(a, b) == Order[List[Int]].compare(a.toList, b.toList))
    }
  }

  test("UnorderedFoldable[Heap].size is correct") {
    forAll { (a: Heap[Int]) =>
      val uof = UnorderedFoldable[Heap]
      assert(uof.size(a) == a.size)
      assert(uof.unorderedFoldMap(a)(_ => 1L) == a.size)
      assert(a.size == a.toList.size.toLong)
    }
  }

  test("Heap.exists is correct") {
    forAll { (a: Heap[Int], fn: Int => Boolean) =>
      assert(a.exists(fn) == a.toList.exists(fn))
    }
  }

  test("Heap.forall is correct") {
    forAll { (a: Heap[Int], fn: Int => Boolean) =>
      assert(a.forall(fn) == a.toList.forall(fn))
    }
  }

  test("Heap.empty is less than nonEmpty") {
    forAll { (item: Int, heap: Heap[Int]) =>
      val ord = Order[Heap[Int]]
      assert(ord.lteqv(Heap.empty, heap))
      assert(ord.lt(Heap.empty, Heap.empty + item))
      assert(ord.gteqv(heap, Heap.empty))
      assert(ord.gt(Heap.empty + item, Heap.empty))
    }
  }
}
