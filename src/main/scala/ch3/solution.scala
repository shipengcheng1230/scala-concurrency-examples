package ch3

import scala.util.{Failure, Success, Try}
import java.util.concurrent.atomic.AtomicReference

import scala.annotation.tailrec
import scala.collection.concurrent.TrieMap
import scala.collection.{concurrent, mutable}

object solution {

  import scala.concurrent._

  class PiggybackContext extends ExecutionContext {

    override def execute(runnable: Runnable): Unit = Try(runnable.run()) match {
      case Success(r) => println(s"Result: $r")
      case Failure(e) => reportFailure(e)
    }

    override def reportFailure(cause: Throwable): Unit = println(s"Error: ${cause.getMessage}")
  }

  object TestPiggybackContext {
    def main(args: Array[String]): Unit = {
      val e = new PiggybackContext
      e.execute(() => {
        println("run (exception)")
        throw new Exception("test exception")
      })
      e.execute(() => println("run"))
    }
  }

  class TreiberStack[T] {
    var r = new AtomicReference[List[T]](List.empty[T])

    @tailrec
    final def push(x: T): Unit = {
      val oldList = r.get()
      val newList = x :: oldList
      if (!r.compareAndSet(oldList, newList)) push(x)
    }

    @tailrec
    final def pop(): T = {
      val oldList = r.get()
      val newList = oldList.tail
      if (r.compareAndSet(oldList, newList)) oldList.head
      else pop()
    }
  }

  object TestTreiberStack {
    def main(args: Array[String]): Unit = {
      val s = new TreiberStack[Int]
      val t1 = new Thread {
        for (i <- 1 to 10) {
          s.push(i)
          Thread.sleep(1)
        }
      }

      val t2 = new Thread {
        for (i <- 1 to 10) {
          s.push(i * 10)
          Thread.sleep(1)
        }
      }

      t1.join()
      t2.join()
      for (i <- 1 to 20) println(s"s[$i] = ${s.pop()}")
    }
  }

  class ConcurrentSortedList[T](implicit val ord: Ordering[T]) {

    case class Node(head: T,
                    tail: AtomicReference[Option[Node]] = new AtomicReference[Option[Node]](None))

    val root = new AtomicReference[Option[Node]](None)

    @tailrec
    private def add(r: AtomicReference[Option[Node]], x: T): Unit = {
      val optNode = r.get

      optNode match {
        case None =>
          if (!r.compareAndSet(optNode, Some(Node(x)))) add(r, x)
        case Some(Node(head, tail)) =>
          if (ord.compare(x, head) <= 0) {
            //prepend new node
            val newNode = Node(x)
            newNode.tail.set(optNode)
            if (!r.compareAndSet(optNode, Some(newNode))) add(r, x)
          } else {
            //add to tail
            add(tail, x)
          }
      }
    }

    def add(x: T): Unit = {
      add(root, x)
    }

    def iterator: Iterator[T] = new Iterator[T] {
      var rIter: Option[Node] = root.get

      override def hasNext: Boolean = rIter.isDefined

      override def next(): T = {
        rIter match {
          case Some(node) =>
            rIter = node.tail.get
            node.head
          case None => throw new NoSuchElementException("next on empty iterator")
        }
      }
    }
  }

  object TestConcurrentSortedList {
    def main(args: Array[String]): Unit = {
      val csl = new ConcurrentSortedList[Int]()

      (1 to 10).map(_ => new Thread {
        override def run(): Unit = {
          Thread.sleep((Math.random() * 100).toInt)
          for (i <- 1 to 10) {
            Thread.sleep((Math.random() * 10).toInt)
            csl.add((math.random * 100 + i).toInt)
          }
        }
      }
      ).foreach(x => {
        x.start()
        x.join()
      })

      println(s"length = ${csl.iterator.length}")

      var prev = 0
      var length = 0
      for (a <- csl.iterator) {
        println(a.toString)
        if (prev > a) throw new Exception(s"$prev > $a")
        prev = a
        length += 1
      }

      if (csl.iterator.length != length) throw new Exception(s"${csl.iterator.length} != $length")

      println(s"length = ${csl.iterator.length} ($length)")
    }
  }

  class LazyCell[T](initialization: => T) {
    @volatile var r: Option[T] = None

    def apply(): T = r match {
      case Some(v) => v
      case None => this.synchronized {
        r match {
          case Some(v) => v
          case None =>
            r = Some(initialization)
            r.get
        }
      }
    }
  }

  class PureLazyCell[T](initialization: => T) {
    val r = new AtomicReference[Option[T]](None)

    @tailrec
    final def apply(): T = r.get() match {
      case Some(v) => v
      case None =>
        val v = initialization
        if (!r.compareAndSet(None, Some(v))) apply()
        else v
    }
  }

  class SyncConcurrentMap[A, B] extends scala.collection.concurrent.Map[A, B] {

    private val m = mutable.Map.empty[A, B]

    override def putIfAbsent(k: A, v: B): Option[B] = m synchronized {
      m.get(k) match {
        case optV @ Some(_) => optV
        case None => m.put(k, v)
      }
    }

    def replace(k: A, oldValue: B, newValue: B): Boolean = m synchronized {
      m.get(k) match {
        case Some(v) if ((v != null) && v.equals(oldValue)) || ((v == null) && (oldValue == null)) =>
          m.put(k, newValue); true
        case _ =>
          false
      }
    }

    def remove(k: A, v: B): Boolean = m synchronized {
      m.get(k) match {
        case Some(oldValue) if ((oldValue != null) && oldValue.equals(v)) || ((v == null) && (oldValue == null)) =>
          m.remove(k)
          true
        case _ =>
          false
      }
    }

    override def replace(k: A, v: B): Option[B] = m synchronized {
      m.get(k) match {
        case old @ Some(_) =>
          m.put(k, v)
          old
        case None =>
          None
      }
    }

    override def get(key: A): Option[B] = m synchronized {
      m.get(key)
    }

    override def iterator: scala.Iterator[(A, B)] = m synchronized {
      m.iterator
    }

    override def subtractOne(elem: A): SyncConcurrentMap.this.type = {
      remove(elem)
      this
    }

    override def addOne(elem: (A, B)): SyncConcurrentMap.this.type = {
      putIfAbsent(elem._1, elem._2)
      this
    }
  }

  def spawn[T](block: => T): T = {
    ???
  }

  object LockFreePool {

    class Pool[T] {
      val parallelism: Int = Runtime.getRuntime.availableProcessors * 32
      val buckets = new Array[AtomicReference[(List[T], Long)]](parallelism)
      for (i <- buckets.indices)
        buckets(i) = new AtomicReference((Nil, 0L))

      val nonEmptyBucket: TrieMap[Int, Unit] = scala.collection.concurrent.TrieMap.empty[Int, Unit]

      def add(x: T): Unit = {
        val i = (Thread.currentThread.getId * x.## % buckets.length).toInt

        @tailrec
        def retry() {
          val bucket = buckets(i)
          val v = bucket.get
          val (lst, stamp) = v
          val nlst = x :: lst
          val nstamp = stamp + 1
          val nv = (nlst, nstamp)
          if (!bucket.compareAndSet(v, nv)) retry() else nonEmptyBucket.putIfAbsent(i, ())
        }

        retry()
      }

      def foreach(f: List[T] => Unit): Unit = {
        buckets.foreach(b => {
          f(b.get()._1)
        })
      }

      def remove(): Option[T] = {
        val start = (Thread.currentThread.getId % buckets.length).toInt

        @tailrec
        def scan(witness: Long): Option[T] = {
          //var i = (start + 1) % buckets.length
          var sum = 0L
          while (nonEmptyBucket.nonEmpty) {
            // randomly pick one, O(1) expected
            val i = nonEmptyBucket.head._1
            val bucket = buckets(i)

            @tailrec def retry(): Option[T] = {
              bucket.get match {
                case (Nil, stamp) =>
                  sum += stamp
                  None
                case v @ (lst, stamp) =>
                  val nv = (lst.tail, stamp + 1)
                  if (bucket.compareAndSet(v, nv)) {
                    if (nv._1.isEmpty) nonEmptyBucket.remove(i)
                    Some(lst.head)
                  }
                  else retry()
              }
            }

            retry() match {
              case Some(v) => return Some(v)
              case None =>
            }
            //this is where O(p)
            //i = (i + 1) % buckets.length
          }

          if (sum == witness) None
          else scan(sum)
        }

        scan(-1L)
      }
    }
  }
  def main(args: Array[String]): Unit = {
    //TestPiggybackContext.main(args)
    //TestTreiberStack.main(args)
    TestConcurrentSortedList.main(args)
  }
}
