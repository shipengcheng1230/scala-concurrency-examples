package ch2

import scala.collection.mutable

object solution {

  def parallel[A, B](a: => A, b: => B): (A, B) = {
    var aVal: A = null.asInstanceOf[A]
    var bVal: B = null.asInstanceOf[B]

    val at = new Thread {
      override def run(): Unit = aVal = a
    }
    val bt = new Thread {
      override def run(): Unit = bVal = b
    }
    at.start()
    bt.start()
    at.join()
    bt.join()
    (aVal, bVal)
  }

  def periodically(duration: Long)(b: => Unit): Unit = {
    val worker = new Thread {
      while (true) {
        b
        Thread.sleep(duration)
      }
    }
    worker.setName("Worker")
    worker.setDaemon(true)
    worker.start()
  }

  object SyncVarModule {
    class SyncVar[T] {
      private var x: T = _

      var empty: Boolean = true

      def isEmpty: Boolean = synchronized { empty }

      def nonEmpty: Boolean = synchronized { !empty }

      def get(): T = this.synchronized {
        if (isEmpty) throw new Exception("Empty SyncVar")
        else {
          empty = true
          val v = x
          x = null.asInstanceOf[T]
          v
        }
      }

      def put(x: T): Unit = this.synchronized {
        if (nonEmpty) throw new Exception("Nonempty SyncVar")
        else {
          empty = false
          this.x = x
        }
      }

      def getWait: T = this.synchronized {
        while (isEmpty) this.wait()
        empty = true
        this.notify()
        x
      }

      def putWait(x: T):  Unit = this.synchronized {
        while (nonEmpty) this.wait()
        empty = false
        this.x = x
        this.notify()
      }
    }

    class SyncQueue[T](val n: Int) {
      private val q = mutable.Queue.empty[T]

      def getWait: T = this.synchronized {
        while (q.isEmpty) this.wait()
        val x = q.dequeue()
        this.notify()
        x
      }

      def putWait(x: T): Unit = this.synchronized {
        while (q.length == n) this.wait()
        q.enqueue(x)
        this.notify()
      }
    }

    def main(args: Array[String]): Unit = {
      val sv = new SyncVar[Int]

      val producer = new Thread {
        override def run(): Unit = {
          var x = 0
          while (x < 15) {
            sv.putWait(x)
            println(s"Feed: $x")
            x += 1
          }
        }
      }

      val consumer: Thread = new Thread {
        var x: Int = -1

        override def run(): Unit = {
          while (x < 14) {
            x = sv.getWait
            println(s"Get: $x")
          }
        }
      }

      producer.start()
      consumer.start()

      producer.join()
      consumer.join()
    }
  }

  object SyncAccount {
    var uidCount = 0L

    def getUniqueId: Long = this.synchronized {
      val newId = uidCount + 1
      uidCount = newId
      newId
    }

    class Account(val name: String, var money: Int) {
      val uid: Long = getUniqueId
    }

    def sendAll(accounts: Set[Account], target: Account): Unit = {
      def adjust(): Unit = target.money =
        accounts.foldLeft(0)((s, a) => {
          val money = a.money
          a.money = 0
          s + money
        })

      def sendWithLock(as: List[Account]): Unit = as match {
        case x :: xs => x synchronized { sendWithLock(xs) }
        case Nil => adjust()
      }

      sendWithLock((target :: accounts.toList).sortBy(_.uid))
    }

    def main(args: Array[String]): Unit = {
      val accounts = (1 to 10).map(i => new Account(s"Account: $i", i * 10)).toSet
      val target = new Account("Target", 0)
      sendAll(accounts, target)
      accounts.foreach(a => println(s"${a.name}, money = ${a.money}"))
      println(s"${target.name}, money = ${target.money}")
    }
  }

  object TaskPools {
    type Task = () => Unit

    class PriorityTaskPool(val p: Int, val importance: Int) {
      private val pq = mutable.PriorityQueue.empty[(Int, Task)](Ordering.by(_._1))

      @volatile private var terminated = false

      def asynchronous(priority: Int)(task: => Unit): Unit = pq.synchronized {
        pq.enqueue((priority, () => task))
        pq.notify()
      }

      def shutdown(): Unit = pq.synchronized {
        terminated = true
        println(s"Discard: ${pq.map(_._1).mkString(", ")}")
        pq.notify()
      }

      class Worker extends Thread {
        setDaemon(true)

        def poll(): (Int, Task) = pq.synchronized {
          while (pq.isEmpty) pq.wait()
          println("Remaining: " + pq.map(_._1).mkString(", "))
          pq.dequeue()
        }

        override def run(): Unit = {
          while (true) {
            poll() match {
              case (p, task) if p > importance || !terminated => task()
              case _ =>
            }
          }
        }
      }

      (1 to p).map(_ => new Worker).foreach(_.start())
    }

    def main(args: Array[String]): Unit = {
      val tasks = new PriorityTaskPool(4, 300)
      (1 to 100).foreach(i => {
        val a = (math.random() * 100).toInt
        tasks.asynchronous(a)({ println(s"<- $a") })
      })
      Thread.sleep(1)
      tasks.shutdown()
    }
  }

  object ConcurrentBiMapModule {

    class ConcurrentBiMap[K, V] {
      val map = mutable.Map.empty[K, V]

      def put(k: K, v: V): Option[(K, V)] = map.synchronized {
        map.get(k) match {
          case None => map.update(k, v)
          case _ =>
        }
        Some((k, v))
      }

      def removeKey(k: K): Option[V] = map.synchronized {
        map.get(k) match {
          case None => None
          case Some(v) => map.remove(k); Some(v)
        }
      }

      def removeValue(v: V): Option[K] = map.synchronized {
        getKey(v) match {
          case Some(k) => map.remove(k); Some(k)
          case None => None
        }
      }

      def getValue(k: K): Option[V] = map.synchronized {
        map.get(k)
      }

      def getKey(v: V): Option[K] = map.synchronized {
        map.keys.find(k => map(k) == v)
      }

      def size: Int = map.synchronized {
        map.size
      }

      def iterator: Iterator[(K, V)] = map.synchronized {
        map.iterator
      }

      def replace(k1: K, v1: V, k2: K, v2: V): Unit = map.synchronized {
        if (map.get(k1).contains(v1) && !map.contains(k2)) {
          removeKey(k1)
          put(k2, v2)
        }
      }
    }

    def cache[K, V](f: K => V): K => V = new Function1[K, V] {
      val memo = new ConcurrentBiMap[K, V]

      override def apply(k: K): V = {
        memo.getValue(k) match {
          case None => val v = f(k); memo.put(k, v); v;
          case Some(v) => v
        }
      }
    }
  }

  def main(args: Array[String]): Unit = {
    SyncVarModule.main(args)
    SyncAccount.main(args)
    TaskPools.main(args)
  }
}
