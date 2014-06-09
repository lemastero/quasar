package slamdata.engine

import slamdata.engine.std._
import slamdata.engine.sql._
import slamdata.engine.analysis._
import slamdata.engine.analysis.fixplate._
import slamdata.engine.physical.mongodb._
import slamdata.engine.fs._

import scalaz.{Node => _, Tree => _, _}
import scalaz.concurrent.{Node => _, _}
import Scalaz._

import scalaz.stream.{Writer => _, _}

import slamdata.engine.config._

sealed trait Backend {
  // TODO: newtype query & out
  def dataSource: FileSystem

  def run(query: String, out: String): Task[(Cord, String)]

  /**
   * Executes a query, placing the output in the specified resource, returning both
   * a compilation log and a source of values from the result set.
   */
  def eval(query: String, out: String): Task[(Cord, Process[Task, RenderedJson])] = {
    for {
      db    <- dataSource.delete(out)
      t     <- run(query, out)

      val (log, out) = t 

      proc  <- Task.delay(dataSource.scan(out))
    } yield log -> proc
  }

  /**
   * Executes a query, placing the output in the specified resource, returning only
   * a compilation log.
   */
  def evalLog(query: String, out: String): Task[Cord] = eval(query, out).map(_._1)

  /**
   * Executes a query, placing the output in the specified resource, returning only
   * a source of values from the result set.
   */
  def evalResults(query: String, out: String): Process[Task, RenderedJson] = Process.eval(eval(query, out).map(_._2)) flatMap identity
}

object Backend {
  private val sqlParser = new SQLParser()

  def apply[PhysicalPlan: Show, Config](planner: Planner[PhysicalPlan], evaluator: Evaluator[PhysicalPlan], ds: FileSystem) = new Backend {
    private type ProcessTask[A] = Process[Task, A]

    private type WriterCord[A] = Writer[Cord, A]

    private type EitherError[A] = Error \/ A

    private type EitherWriter[A] = EitherT[WriterCord, Error, A]

    private def logged[A: Show](caption: String)(ea: Error \/ A): EitherWriter[A] = {
      var log0 = Cord(caption)

      val log = ea.fold(
        error => log0 ++ Cord(error.fullMessage),
        a     => log0 ++ a.show
      )

      EitherT[WriterCord, Error, A](WriterT.writer[Cord, Error \/ A](log, ea))
    }

    def dataSource = ds

    def run(query: String, out: String): Task[(Cord, String)] = Task.delay {
      import SemanticAnalysis.{fail => _, _}
      import Process.{logged => _, _}

      val either = for {
        select    <- logged("\nSQL AST\n")(sqlParser.parse(query))
        tree      <- logged("\nAnnotated Tree\n")(AllPhases(tree(select)).disjunction.leftMap(ManyErrors.apply))
        logical   <- logged("\nLogical Plan\n")(Compiler.compile(tree))
        physical  <- logged("\nPhysical Plan\n")(planner.plan(logical))
      } yield physical

      val (log, physical) = either.run.run

      physical.fold[Task[(Cord, String)]](
        error => Task.fail(LoggedError(log, error)),
        logical => {
          for {
            out  <- evaluator.execute(logical, out)
          } yield log -> out
        }
      )
    }.join
  }
}

case class BackendDefinition(create: PartialFunction[BackendConfig, Task[Backend]]) extends (BackendConfig => Option[Task[Backend]]) {
  def apply(config: BackendConfig): Option[Task[Backend]] = create.lift(config)
}

object BackendDefinition {
  implicit val BackendDefinitionMonoid = new Monoid[BackendDefinition] {
    def zero = BackendDefinition(PartialFunction.empty)

    def append(v1: BackendDefinition, v2: => BackendDefinition): BackendDefinition = 
      BackendDefinition(v1.create.orElse(v2.create))
  }
}