package io.circe.cats

import io.circe.cats.kernel.Order
import scala.collection.immutable.SortedMap

/**
 * FlatMap type class gives us flatMap, which allows us to have a value
 * in a context (F[A]) and then feed that into a function that takes
 * a normal value and returns a value in a context (A => F[B]).
 *
 * One motivation for separating this out from Monad is that there are
 * situations where we can implement flatMap but not pure.  For example,
 * we can implement map or flatMap that transforms the values of Map[K, *],
 * but we can't implement pure (because we wouldn't know what key to use
 * when instantiating the new Map).
 *
 * @see See [[https://github.com/typelevel/cats/issues/3]] for some discussion.
 *
 * Must obey the laws defined in cats.laws.FlatMapLaws.
 */
trait FlatMap[F[_]] extends Apply[F] {
  def flatMap[A, B](fa: F[A])(f: A => F[B]): F[B]

  /**
   * "flatten" a nested `F` of `F` structure into a single-layer `F` structure.
   *
   * This is also commonly called `join`.
   *
   * Example:
   * {{{
   * scala> import cats.Eval
   * scala> import cats.implicits._
   *
   * scala> val nested: Eval[Eval[Int]] = Eval.now(Eval.now(3))
   * scala> val flattened: Eval[Int] = nested.flatten
   * scala> flattened.value
   * res0: Int = 3
   * }}}
   */
  def flatten[A](ffa: F[F[A]]): F[A] =
    flatMap(ffa)(fa => fa)

  /**
   * Sequentially compose two actions, discarding any value produced by the first. This variant of
   * [[productR]] also lets you define the evaluation strategy of the second action. For instance
   * you can evaluate it only ''after'' the first action has finished:
   *
   * {{{
   * scala> import cats.Eval
   * scala> import cats.implicits._
   * scala> val fa: Option[Int] = Some(3)
   * scala> def fb: Option[String] = Some("foo")
   * scala> fa.productREval(Eval.later(fb))
   * res0: Option[String] = Some(foo)
   * }}}
   */
  def productREval[A, B](fa: F[A])(fb: Eval[F[B]]): F[B] = flatMap(fa)(_ => fb.value)

  /**
   * Sequentially compose two actions, discarding any value produced by the second. This variant of
   * [[productL]] also lets you define the evaluation strategy of the second action. For instance
   * you can evaluate it only ''after'' the first action has finished:
   *
   * {{{
   * scala> import cats.Eval
   * scala> import cats.implicits._
   * scala> var count = 0
   * scala> val fa: Option[Int] = Some(3)
   * scala> def fb: Option[Unit] = Some(count += 1)
   * scala> fa.productLEval(Eval.later(fb))
   * res0: Option[Int] = Some(3)
   * scala> assert(count == 1)
   * scala> none[Int].productLEval(Eval.later(fb))
   * res1: Option[Int] = None
   * scala> assert(count == 1)
   * }}}
   */
  def productLEval[A, B](fa: F[A])(fb: Eval[F[B]]): F[A] = flatMap(fa)(a => map(fb.value)(_ => a))

  override def ap[A, B](ff: F[A => B])(fa: F[A]): F[B] =
    flatMap(ff)(f => map(fa)(f))

  override def product[A, B](fa: F[A], fb: F[B]): F[(A, B)] =
    flatMap(fa)(a => map(fb)(b => (a, b)))

  override def ap2[A, B, Z](ff: F[(A, B) => Z])(fa: F[A], fb: F[B]): F[Z] =
    flatMap(fa)(a => flatMap(fb)(b => map(ff)(_(a, b))))

  override def map2[A, B, Z](fa: F[A], fb: F[B])(f: (A, B) => Z): F[Z] =
    flatMap(fa)(a => map(fb)(b => f(a, b)))

  override def productR[A, B](fa: F[A])(fb: F[B]): F[B] =
    flatMap(fa)(_ => fb)

  override def productL[A, B](fa: F[A])(fb: F[B]): F[A] =
    map2(fa, fb)((a, _) => a)

  /**
   * Pair `A` with the result of function application.
   *
   * Example:
   * {{{
   * scala> import cats.implicits._
   * scala> List("12", "34", "56").mproduct(_.toList)
   * res0: List[(String, Char)] = List((12,1), (12,2), (34,3), (34,4), (56,5), (56,6))
   * }}}
   */
  def mproduct[A, B](fa: F[A])(f: A => F[B]): F[(A, B)] =
    flatMap(fa)(a => map(f(a))((a, _)))

  /**
   * `if` lifted into monad.
   */
  def ifM[B](fa: F[Boolean])(ifTrue: => F[B], ifFalse: => F[B]): F[B] =
    flatMap(fa)(if (_) ifTrue else ifFalse)

  /**
   * Keeps calling `f` until a `scala.util.Right[B]` is returned.
   *
   * Based on Phil Freeman's
   * [[http://functorial.com/stack-safety-for-free/index.pdf Stack Safety for Free]].
   *
   * Implementations of this method should use constant stack space relative to `f`.
   */
  def tailRecM[A, B](a: A)(f: A => F[Either[A, B]]): F[B]

  /**
   * Apply a monadic function and discard the result while keeping the effect.
   *
   * {{{
   * scala> import cats._, implicits._
   * scala> Option(1).flatTap(_ => None)
   * res0: Option[Int] = None
   * scala> Option(1).flatTap(_ => Some("123"))
   * res1: Option[Int] = Some(1)
   * scala> def nCats(n: Int) = List.fill(n)("cat")
   * nCats: (n: Int)List[String]
   * scala> List[Int](0).flatTap(nCats)
   * res2: List[Int] = List()
   * scala> List[Int](4).flatTap(nCats)
   * res3: List[Int] = List(4, 4, 4, 4)
   * }}}
   */
  def flatTap[A, B](fa: F[A])(f: A => F[B]): F[A] =
    flatMap(fa)(a => as(f(a), a))
}

object FlatMap {  
  def apply[F[_]] given (F: FlatMap[F]): FlatMap[F] = F

  private[cats] trait Ops {
    given [F[_], A] given (F: FlatMap[F]) {
      def (fa: F[A]) flatMap[B](f: A => F[B]): F[B] = F.flatMap(fa)(f)
      def (fa: F[A]) productREval[B](fb: Eval[F[B]]): F[B] = F.productREval(fa)(fb)
      def (fa: F[A]) productLEval[B](fb: Eval[F[B]]): F[A] = F.productLEval(fa)(fb)
      def (fa: F[A]) mproduct[B](f: A => F[B]): F[(A, B)] = F.mproduct(fa)(f)
      def (fa: F[A]) flatTap[B](f: A => F[B]): F[A] = F.flatTap(fa)(f)
    }

    given [F[_]] given (F: FlatMap[F]) {
      def (fa: F[Boolean]) ifM[B](ifTrue: => F[B], ifFalse: => F[B]): F[B] = F.ifM(fa)(ifTrue, ifFalse)
    }
  }
}