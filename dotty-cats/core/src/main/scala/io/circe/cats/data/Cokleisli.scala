package io.circe.cats.data

import io.circe.cats.{CoflatMap, Comonad, Contravariant, Functor, Id, Monad, MonoidK, SemigroupK}
import io.circe.cats.arrow.{Arrow, Category, CommutativeArrow, Compose, Profunctor}

import scala.annotation.tailrec

/**
 * Represents a function `F[A] => B`.
 */
final case class Cokleisli[F[_], A, B](run: F[A] => B) { self =>

  /**
   * Example:
   * {{{
   * scala> import cats._, data._
   * scala> val f = Cokleisli((xs: NonEmptyList[Int]) => xs.reverse.head)
   * scala> def before(x: Double) = x.toInt
   * scala> def after(x: Int) = x.toString
   * scala> f.dimap(before)(after).run(NonEmptyList.of(1.0,2.0))
   * res0: String = 2
   * }}}
   */
  def dimap[C, D](f: C => A)(g: B => D) given (F: Functor[F]): Cokleisli[F, C, D] =
    Cokleisli(fc => g(run(F.map(fc)(f))))

  /**
   * Example:
   * {{{
   * scala> import cats._, data._, implicits._
   * scala> val f = Cokleisli((xs: NonEmptyList[Int]) => xs.reverse.head)
   * scala> def before(x: Double) = x.toInt
   * scala> def after(x: Int) = x.toString
   * scala> f.lmap(before).rmap(after).run(NonEmptyList.of(1.0,2.0))
   * res0: String = 2
   * }}}
   */
  def lmap[C](f: C => A) given (F: Functor[F]): Cokleisli[F, C, B] =
    Cokleisli(fc => run(F.map(fc)(f)))

  def map[C](f: B => C): Cokleisli[F, A, C] =
    Cokleisli(f.compose(run))

  /**
   * Example:
   * {{{
   * scala> import cats._, data._
   * scala> val sum = Cokleisli((xs: NonEmptyList[Int]) => xs.reduceLeft(_ + _))
   *
   * scala> sum.contramapValue((xs: NonEmptyList[String]) => xs.map(_.toInt)).run(NonEmptyList.of("1","2","3"))
   * res4: Int = 6
   * }}}
   */
  def contramapValue[C](f: F[C] => F[A]): Cokleisli[F, C, B] =
    Cokleisli(run.compose(f))

  def flatMap[C](f: B => Cokleisli[F, A, C]): Cokleisli[F, A, C] =
    Cokleisli(fa => f(self.run(fa)).run(fa))

  def compose[C](c: Cokleisli[F, C, A]) given (F: CoflatMap[F]): Cokleisli[F, C, B] =
    Cokleisli(fc => run(F.coflatMap(fc)(c.run)))

  def andThen[C](c: Cokleisli[F, B, C]) given (F: CoflatMap[F]): Cokleisli[F, A, C] =
    c.compose(this)

  def first[C] given (F: Comonad[F]): Cokleisli[F, (A, C), (B, C)] =
    Cokleisli(fac => run(F.map(fac)(_._1)) -> F.extract(F.map(fac)(_._2)))

  def second[C] given (F: Comonad[F]): Cokleisli[F, (C, A), (C, B)] =
    Cokleisli(fca => F.extract(F.map(fca)(_._1)) -> run(F.map(fca)(_._2)))
}

object Cokleisli extends CokleisliInstances0 {
  def pure[F[_], A, B](x: B): Cokleisli[F, A, B] =
    Cokleisli(_ => x)

  given as CommutativeArrow[[x, y] =>> Cokleisli[Id, x, y]] =
    new CokleisliArrow[Id] with CommutativeArrow[[x, y] =>> Cokleisli[Id, x, y]] with CokleisliCompose[Id] with CokleisliProfunctor[Id]

  given [F[_], A] as Monad[[x] =>> Cokleisli[F, A, x]] {
    def pure[B](x: B): Cokleisli[F, A, B] =
      Cokleisli.pure(x)

    def flatMap[B, C](fa: Cokleisli[F, A, B])(f: B => Cokleisli[F, A, C]): Cokleisli[F, A, C] =
     fa.flatMap(f)
 
    override def map[B, C](fa: Cokleisli[F, A, B])(f: B => C): Cokleisli[F, A, C] =
      fa.map(f)

    def tailRecM[B, C](b: B)(fn: B => Cokleisli[F, A, Either[B, C]]): Cokleisli[F, A, C] =
      Cokleisli({ (fa: F[A]) =>
        @tailrec
        def loop(c: Cokleisli[F, A, Either[B, C]]): C = c.run(fa) match {
          case Right(c) => c
          case Left(bb) => loop(fn(bb))
        }
        loop(fn(b))
      })
  }

  given [F[_]] as MonoidK[[x] =>> Cokleisli[F, x, x]] given Comonad[F] =
    the[Category[[x, y] =>> Cokleisli[F, x, y]]].algebraK
}

private[data] sealed abstract class CokleisliInstances0 extends CokleisliInstances1 {
  given foo[F[_]] as Arrow[[x, y] =>> Cokleisli[F, x, y]] given Comonad[F] =
    new CokleisliArrow[F] with CokleisliCompose[F] with CokleisliProfunctor[F]

  protected[this] trait CokleisliArrow[F[_]] given (F: Comonad[F])
      extends Arrow[[x, y] =>> Cokleisli[F, x, y]]
      with CokleisliCompose[F]
      with CokleisliProfunctor[F] {
    def lift[A, B](f: A => B): Cokleisli[F, A, B] =
      Cokleisli(fa => f(F.extract(fa)))

    def first[A, B, C](fa: Cokleisli[F, A, B]): Cokleisli[F, (A, C), (B, C)] =
      fa.first[C]

    override def second[A, B, C](fa: Cokleisli[F, A, B]): Cokleisli[F, (C, A), (C, B)] =
      fa.second[C]

    override def dimap[A, B, C, D](fab: Cokleisli[F, A, B])(f: C => A)(g: B => D): Cokleisli[F, C, D] =
      super[CokleisliProfunctor].dimap(fab)(f)(g)

    override def split[A, B, C, D](f: Cokleisli[F, A, B], g: Cokleisli[F, C, D]): Cokleisli[F, (A, C), (B, D)] =
      Cokleisli(fac => f.run(F.map(fac)(_._1)) -> g.run(F.map(fac)(_._2)))
  }
}

private[data] sealed abstract class CokleisliInstances1 {
  given [F[_]] as Compose[[x, y] =>> Cokleisli[F, x, y]] given CoflatMap[F] =
    new CokleisliCompose[F] {}

  given [F[_]] as Profunctor[[x, y] =>> Cokleisli[F, x, y]] given Functor[F] =
    new CokleisliProfunctor[F] {}

  given [F[_]] as SemigroupK[[x] =>> Cokleisli[F, x, x]] given CoflatMap[F] =
    the[Compose[[x, y] =>> Cokleisli[F, x, y]]].algebraK

  given [F[_], A] as Contravariant[[x] =>> Cokleisli[F, x, A]] given Functor[F] {
    def contramap[B, C](fbc: Cokleisli[F, B, A])(f: C => B): Cokleisli[F, C, A] = fbc.lmap(f)
  }

  protected[this] trait CokleisliCompose[F[_]] given CoflatMap[F] extends Compose[[x, y] =>> Cokleisli[F, x, y]] {
    def compose[A, B, C](f: Cokleisli[F, B, C], g: Cokleisli[F, A, B]): Cokleisli[F, A, C] =
      f.compose(g)
  }

  protected[this] trait CokleisliProfunctor[F[_]] given Functor[F] extends Profunctor[[x, y] =>> Cokleisli[F, x, y]] {
    def dimap[A, B, C, D](fab: Cokleisli[F, A, B])(f: C => A)(g: B => D): Cokleisli[F, C, D] =
      fab.dimap(f)(g)

    override def lmap[A, B, C](fab: Cokleisli[F, A, B])(f: C => A): Cokleisli[F, C, B] =
      fab.lmap(f)

    override def rmap[A, B, C](fab: Cokleisli[F, A, B])(f: B => C): Cokleisli[F, A, C] =
      fab.map(f)
  }
}
