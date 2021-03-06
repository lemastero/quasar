/*
 * Copyright 2014–2018 SlamData Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package quasar.std

import slamdata.Predef._
import quasar.{Func, Type}
import quasar.contrib.shapeless._
import quasar.fp.ski._
import quasar.frontend.logicalplan.LogicalPlan

import matryoshka._
import scalaz._, Validation.success
import shapeless.{:: => _, _}

trait Library {
  import Func._

  protected val noSimplification: Simplifier = new Simplifier {
    def apply[T]
      (orig: LogicalPlan[T])
      (implicit TR: Recursive.Aux[T, LogicalPlan], TC: Corecursive.Aux[T, LogicalPlan]) =
      None
  }

  protected def constTyper[N <: Nat](codomain: Codomain): Typer[N] =
    _ => Some(success(codomain))

  def partialTyperOV[N <: Nat](f: Domain[N] => Option[VCodomain]): Typer[N] = f

  def partialTyperV[N <: Nat](f: PartialFunction[Domain[N], VCodomain]): Typer[N] =
    partialTyperOV[N](f.lift)

  def basicTyper[N <: Nat]: Typer[N] = { _ => None }

  def widenConstTyper[N <: Nat](select: Domain[N] => Type): Typer[N] = {
    domain => Some(success(select(domain).widenConst))
  }

  protected def partialTyper[N <: Nat](f: PartialFunction[Domain[N], Codomain]): Typer[N] =
    partialTyperOV[N](g => f.lift(g).map(success))

  protected def basicUntyper[N <: Nat]: Untyper[N] = {
    case ((funcDomain, _), _) => Some(success(funcDomain))
  }

  def untyper[N <: Nat](f: Codomain => VDomain[N]): Untyper[N] =
    partialUntyperOV[N](f.andThen(Some(_)))

  protected def partialUntyperOV[N <: Nat](f: Codomain => Option[VDomain[N]]): Untyper[N] = {
    case ((funcDomain, funcCodomain), rez) => Type.typecheck(rez, funcCodomain).fold(
      κ(f(rez)),
      κ(Some(success(funcDomain))))
  }

  protected def partialUntyperV[N <: Nat](f: PartialFunction[Codomain, VDomain[N]]): Untyper[N] =
    partialUntyperOV(f.lift)

  protected def partialUntyper[N <: Nat](f: PartialFunction[Codomain, Domain[N]]): Untyper[N] =
    partialUntyperOV(f.lift(_).map(success))

  protected def numericWidening = {
    def mapFirst[A, B](f: A => A, p: PartialFunction[A, B]) = new PartialFunction[A, B] {
      def isDefinedAt(a: A) = p.isDefinedAt(f(a))
      def apply(a: A) = p(f(a))
    }

    val half: PartialFunction[Domain[nat._2], Codomain] = {
      case Sized(Type.Dec, t2) if Type.Int contains t2 => Type.Dec
      case Sized(Type.Int, t2) if Type.Dec contains t2 => Type.Dec
      case Sized(t1, t2)       if Type.Numeric.contains(t1) && t1.contains(t2) => t1
    }

    partialTyper[nat._2](half orElse mapFirst[Domain[nat._2], Codomain](_.reverse, half))
  }

  protected implicit class TyperW[N <: Nat](self: Typer[N]) {
    def ||| (that: Typer[N]): Typer[N] = { args =>
      self(args).orElse(that(args))
    }
  }
}
