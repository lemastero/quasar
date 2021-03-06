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

package quasar
package repl

import slamdata.Predef._
import quasar.api.QueryEvaluator
import quasar.api.datasource.Datasources
import quasar.build.BuildInfo
import quasar.common.{PhaseResultListen, PhaseResultTell}
import quasar.common.data.Data
import quasar.ejson.EJson
import quasar.impl.schema.SstConfig
import quasar.run.{MonadQuasarErr, SqlQuery}

import java.io.File
import java.util.UUID

import argonaut.Json
import cats.effect._
import cats.syntax.{applicative, flatMap, functor}, applicative._, flatMap._, functor._
import fs2.{Stream, StreamApp}, StreamApp.ExitCode
import fs2.async.Ref
import matryoshka.data.Fix
import org.apache.commons.io.FileUtils
import org.jline.reader._
import org.jline.terminal._
import scalaz._, Scalaz._

final class Repl[F[_]: ConcurrentEffect: PhaseResultListen](
    prompt: String,
    reader: LineReader,
    evaluator: Command => F[Evaluator.Result]) {

  val F = ConcurrentEffect[F]

  private val read: F[Command] = F.delay(Command.parse(reader.readLine(prompt)))

  private def eval(cmd: Command): F[Evaluator.Result] =
    F.start(evaluator(cmd)) >>= (_.join)

  private def print(string: Option[String]): F[Unit] =
    string.fold(F.unit)(s => F.delay(println(s)))

  val loop: F[ExitCode] =
    for {
      cmd <- read
      res <- eval(cmd)
      Evaluator.Result(exitCode, string) = res
      _ <- print(string)
      next <- exitCode.fold(loop)(_.pure[F])
    } yield next
}

object Repl {
  def apply[F[_]: ConcurrentEffect: PhaseResultListen](
      prompt: String,
      reader: LineReader,
      evaluator: Command => F[Evaluator.Result])
      : Repl[F] =
    new Repl[F](prompt, reader, evaluator)

  def mk[F[_]: ConcurrentEffect: MonadQuasarErr: PhaseResultListen: PhaseResultTell](
      ref: Ref[F, ReplState],
      datasources: Datasources[F, Stream[F, ?], UUID, Json, SstConfig[Fix[EJson], Double]],
      queryEvaluator: QueryEvaluator[F, SqlQuery, Stream[F, Data]])
      : F[Repl[F]] = {
    val evaluator = Evaluator[F](ref, datasources, queryEvaluator)
    historyFile[F].map(f => Repl[F](prompt, mkLineReader(f), evaluator.evaluate))
  }

  ////

  private val prompt = s"(v${BuildInfo.version}) 💪 $$ "

  private def touch[F[_]](f: File)(implicit F: Sync[F]): F[Option[File]] =
    F.delay {
      \/.fromTryCatchNonFatal(FileUtils.touch(f)) match {
        case -\/(err) => none
        case \/-(_) => f.some
      }
    }

  private def historyFile[F[_]](implicit F: Sync[F]): F[Option[File]] =
    Paths.getProp("quasar.historyfile") >>=
      (_ match {
        case Some(p) => touch(new File(p))
        case None =>
          Paths.getUserHome >>=
            (_ match {
              case Some(h) => touch(new File(h.toFile, ".quasar.history"))
              case None => none[File].pure[F]
            })
      })

  private def ifHistoryFile(builder: LineReaderBuilder, historyFile: Option[File])
      : LineReaderBuilder =
    historyFile.map(
      builder.variable(LineReader.HISTORY_FILE, _)).getOrElse(builder)

  private def mkLineReader(historyFile: Option[File]): LineReader = {
    val terminal = TerminalBuilder.terminal()
    ifHistoryFile(
      LineReaderBuilder.builder().terminal(terminal),
      historyFile).build()
  }

}
