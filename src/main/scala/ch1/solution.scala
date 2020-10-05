package ch1

object solution {

  def compose[A, B, C](g: B => C, f: A => B): A => C = {
    x => g(f(x))
  }

  def fuse[A, B](a: Option[A], b: Option[B]): Option[(A, B)] = {
    for (x <- a; y <- b) yield (x, y)
  }

  def check[T](xs: Seq[T])(pred: T => Boolean): Boolean = xs.forall { x =>
    try {
      pred(x)
    } catch {
      case _: Exception => false
    }
  }

  def permutations(x: String): Seq[String] = {
    x.toSeq.permutations.map(_.unwrap).toSeq
  }

  def combinations(n: Int, xs: Seq[Int]): Iterator[Seq[Int]] = {
    xs.combinations(n)
  }

  def matcher(regex: String): PartialFunction[String, List[String]] = {
    case s if regex.r.findFirstIn(s).isDefined => regex.r.findAllMatchIn(s).map(_.toString()).toList
  }
}
