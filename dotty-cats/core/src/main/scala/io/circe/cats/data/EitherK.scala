package io.circe.cats.data

import io.circe.cats.{Applicative, CoflatMap, Comonad, Contravariant, Eval, Foldable, Functor, Traverse, ~>}
import io.circe.cats.arrow.FunctionK
import io.circe.cats.kernel.{Eq, Monoid}

/** `F` on the left and `G` on the right of `scala.util.Either`.
 *
 * @param run The underlying `scala.util.Either`.
 */
final case class EitherK[F[_], G[_], A](run: Either[F[A], G[A]]) {

  import EitherK._

  def map[B](f: A => B) given (F: Functor[F], G: Functor[G]): EitherK[F, G, B] =
    EitherK(
      run match {
        case Left(a) => Left(F.lift(f)(a))
        case Right(a) => Right(G.lift(f)(a))
      }
    )

  /**
   * Modify the right side context `G` using transformation `f`.
   */
  def mapK[H[_]](f: G ~> H): EitherK[F, H, A] =
    EitherK(run.map(f.apply))

  def coflatMap[B](f: EitherK[F, G, A] => B) given (F: CoflatMap[F], G: CoflatMap[G]): EitherK[F, G, B] =
    EitherK(
      run match {
        case Left(a) => Left(F.coflatMap(a)(x => f(leftc(x))))
        case Right(a) => Right(G.coflatMap(a)(x => f(rightc(x))))
      } 
    )

  def coflatten given (F: CoflatMap[F], G: CoflatMap[G]): EitherK[F, G, EitherK[F, G, A]] =
    EitherK(
      run match {
        case Left(a) => Left(F.coflatMap(a)(x => leftc(x)))
        case Right(a) => Right(G.coflatMap(a)(x => rightc(x)))
      }
    )

  def extract given (F: Comonad[F], G: Comonad[G]): A =
    run.fold(F.extract, G.extract)

  def contramap[B](f: B => A) given (F: Contravariant[F], G: Contravariant[G]): EitherK[F, G, B] =
    EitherK(
      run match {
        case Left(a) => Left(F.contramap(a)(f))
        case Right(a) => Right(G.contramap(a)(f))
      }
    )

  def foldRight[B](z: Eval[B])(f: (A, Eval[B]) => Eval[B]) given (F: Foldable[F], G: Foldable[G]): Eval[B] =
    run.fold(a => F.foldRight(a, z)(f), a => G.foldRight(a, z)(f))

  def foldLeft[B](z: B)(f: (B, A) => B) given (F: Foldable[F], G: Foldable[G]): B =
    run.fold(a => F.foldLeft(a, z)(f), a => G.foldLeft(a, z)(f))

  def foldMap[B](f: A => B) given (F: Foldable[F], G: Foldable[G], M: Monoid[B]): B =
    run.fold(F.foldMap(_)(f), G.foldMap(_)(f))

  def traverse[X[_], B](g: A => X[B]) given (F: Traverse[F], G: Traverse[G], A: Applicative[X]): X[EitherK[F, G, B]] =
    run.fold(
      x => A.map(F.traverse(x)(g))(leftc(_)),
      x => A.map(G.traverse(x)(g))(rightc(_))
    )

  def isLeft: Boolean =
    run.isLeft

  def isRight: Boolean =
    run.isRight

  def swap: EitherK[G, F, A] =
    EitherK(run.swap)

  def toValidated: Validated[F[A], G[A]] =
    run match {
      case Left(a) => Validated.invalid(a)
      case Right(a) => Validated.valid(a)
    }

  /**
   * Fold this eitherK into a new type constructor using two natural transformations.
   *
   * Example:
   * {{{
   * scala> import cats.arrow.FunctionK
   * scala> import cats.data.EitherK
   * scala> val listToOption = λ[FunctionK[List, Option]](_.headOption)
   * scala> val optionToOption = FunctionK.id[Option]
   * scala> val cp1: EitherK[List, Option, Int] = EitherK.leftc(List(1,2,3))
   * scala> val cp2: EitherK[List, Option, Int] = EitherK.rightc(Some(4))
   * scala> cp1.fold(listToOption, optionToOption)
   * res0: Option[Int] = Some(1)
   * scala> cp2.fold(listToOption, optionToOption)
   * res1: Option[Int] = Some(4)
   * }}}
   */
  def fold[H[_]](f: FunctionK[F, H], g: FunctionK[G, H]): H[A] =
    run.fold(f.apply, g.apply)
}

object EitherK extends EitherKInstances {

  def leftc[F[_], G[_], A](x: F[A]): EitherK[F, G, A] =
    EitherK(Left(x))

  def rightc[F[_], G[_], A](x: G[A]): EitherK[F, G, A] =
    EitherK(Right(x))

  final class EitherKLeft[G[_]] private[EitherK] {
    def apply[F[_], A](fa: F[A]): EitherK[F, G, A] = EitherK(Left(fa))
  }

  final class EitherKRight[F[_]] private[EitherK] {
    def apply[G[_], A](ga: G[A]): EitherK[F, G, A] = EitherK(Right(ga))
  }

  def left[G[_]]: EitherKLeft[G] = new EitherKLeft[G]

  def right[F[_]]: EitherKRight[F] = new EitherKRight[F]
}

sealed abstract private[data] class EitherKInstances3 {

  implicit def catsDataEqForEitherK[F[_], G[_], A](implicit E: Eq[Either[F[A], G[A]]]): Eq[EitherK[F, G, A]] =
    Eq.by(_.run)

  implicit def catsDataFunctorForEitherK[F[_], G[_]] given (F0: Functor[F],
                                                     G0: Functor[G]): Functor[[a] =>> EitherK[F, G, a]] =
    new EitherKFunctor[F, G] {
      implicit def F: Functor[F] = F0

      implicit def G: Functor[G] = G0
    }

  implicit def catsDataFoldableForEitherK[F[_], G[_]] given (F0: Foldable[F],
                                                      G0: Foldable[G]): Foldable[[a] =>> EitherK[F, G, a]] =
    new EitherKFoldable[F, G] {
      implicit def F: Foldable[F] = F0

      implicit def G: Foldable[G] = G0
    }
}

sealed abstract private[data] class EitherKInstances2 extends EitherKInstances3 {

  implicit def catsDataContravariantForEitherK[F[_], G[_]] given (F0: Contravariant[F],
                                                           G0: Contravariant[G]): Contravariant[[a] =>> EitherK[F, G, a]] =
    new EitherKContravariant[F, G] {
      implicit def F: Contravariant[F] = F0

      implicit def G: Contravariant[G] = G0
    }
}

sealed abstract private[data] class EitherKInstances1 extends EitherKInstances2 {
  implicit def catsDataCoflatMapForEitherK[F[_], G[_]] given (F0: CoflatMap[F],
                                                       G0: CoflatMap[G]): CoflatMap[[a] =>> EitherK[F, G, a]] =
    new EitherKCoflatMap[F, G] with EitherKFunctor[F, G] {
      implicit def F: CoflatMap[F] = F0

      implicit def G: CoflatMap[G] = G0
    }
}

sealed abstract private[data] class EitherKInstances0 extends EitherKInstances1 {
  implicit def catsDataTraverseForEitherK[F[_], G[_]] given (F0: Traverse[F],
                                                      G0: Traverse[G]): Traverse[[a] =>> EitherK[F, G, a]] =
    new EitherKTraverse[F, G] with EitherKFunctor[F, G] {
      implicit def F: Traverse[F] = F0

      implicit def G: Traverse[G] = G0
    }
}

sealed abstract private[data] class EitherKInstances extends EitherKInstances0 {

  implicit def catsDataComonadForEitherK[F[_], G[_]] given (F0: Comonad[F],
                                                     G0: Comonad[G]): Comonad[[a] =>> EitherK[F, G, a]] =
    new EitherKComonad[F, G] with EitherKFunctor[F, G] {
      implicit def F: Comonad[F] = F0

      implicit def G: Comonad[G] = G0
    }
}

private[data] trait EitherKFunctor[F[_], G[_]] extends Functor[[a] =>> EitherK[F, G, a]] {
  implicit def F: Functor[F]

  implicit def G: Functor[G]

  override def map[A, B](a: EitherK[F, G, A])(f: A => B): EitherK[F, G, B] =
    a.map(f)
}

private[data] trait EitherKContravariant[F[_], G[_]] extends Contravariant[[a] =>> EitherK[F, G, a]] {
  implicit def F: Contravariant[F]

  implicit def G: Contravariant[G]

  def contramap[A, B](a: EitherK[F, G, A])(f: B => A): EitherK[F, G, B] =
    a.contramap(f)
}

private[data] trait EitherKFoldable[F[_], G[_]] extends Foldable[[a] =>> EitherK[F, G, a]] {
  implicit def F: Foldable[F]

  implicit def G: Foldable[G]

  def foldRight[A, B](fa: EitherK[F, G, A], z: Eval[B])(f: (A, Eval[B]) => Eval[B]): Eval[B] =
    fa.foldRight(z)(f)

  def foldLeft[A, B](fa: EitherK[F, G, A], z: B)(f: (B, A) => B): B =
    fa.foldLeft(z)(f)

  override def size[A](fa: EitherK[F, G, A]): Long =
    fa.run.fold(F.size, G.size)

  override def get[A](fa: EitherK[F, G, A])(idx: Long): Option[A] =
    fa.run.fold(F.get(_)(idx), G.get(_)(idx))

  override def foldMap[A, B](fa: EitherK[F, G, A])(f: A => B)(implicit M: Monoid[B]): B =
    fa.foldMap(f)
}

private[data] trait EitherKTraverse[F[_], G[_]] extends EitherKFoldable[F, G] with Traverse[[a] =>> EitherK[F, G, a]] {
  implicit def F: Traverse[F]

  implicit def G: Traverse[G]

  override def map[A, B](a: EitherK[F, G, A])(f: A => B): EitherK[F, G, B] =
    a.map(f)

  override def traverse[X[_]: Applicative, A, B](fa: EitherK[F, G, A])(f: A => X[B]): X[EitherK[F, G, B]] =
    fa.traverse(f)
}

private[data] trait EitherKCoflatMap[F[_], G[_]] extends CoflatMap[[a] =>> EitherK[F, G, a]] {
  implicit def F: CoflatMap[F]

  implicit def G: CoflatMap[G]

  def map[A, B](a: EitherK[F, G, A])(f: A => B): EitherK[F, G, B] =
    a.map(f)

  def coflatMap[A, B](a: EitherK[F, G, A])(f: EitherK[F, G, A] => B): EitherK[F, G, B] =
    a.coflatMap(f)

  override def coflatten[A](fa: EitherK[F, G, A]): EitherK[F, G, EitherK[F, G, A]] =
    fa.coflatten
}

private[data] trait EitherKComonad[F[_], G[_]] extends Comonad[[a] =>> EitherK[F, G, a]] with EitherKCoflatMap[F, G] {
  implicit def F: Comonad[F]

  implicit def G: Comonad[G]

  def extract[A](p: EitherK[F, G, A]): A =
    p.extract
}
