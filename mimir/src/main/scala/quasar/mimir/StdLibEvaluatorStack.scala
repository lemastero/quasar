
package quasar.mimir

import quasar.yggdrasil.table.cf
import scalaz._

trait StdLibEvaluatorStack[M[+ _]]
    extends EvaluatorModule[M]
    with StdLibModule[M]
    with StdLibOpFinderModule[M]
    with StdLibStaticInlinerModule[M]
    with ReductionFinderModule[M]
    with JoinOptimizerModule[M]
    with PredicatePullupsModule[M] {

  trait Lib extends StdLib with StdLibOpFinder
  object library extends Lib

  abstract class Evaluator[N[+ _]](N0: Monad[N])(implicit mn: M ~> N, nm: N ~> M)
      extends EvaluatorLike[N](N0)(mn, nm)
      with StdLibOpFinder
      with StdLibStaticInliner {

    val Exists = library.Exists
    val Forall = library.Forall
    def concatString(ctx: MorphContext)   = library.Infix.concatString.f2(ctx)
    def coerceToDouble(ctx: MorphContext) = cf.util.CoerceToDouble
  }
}
