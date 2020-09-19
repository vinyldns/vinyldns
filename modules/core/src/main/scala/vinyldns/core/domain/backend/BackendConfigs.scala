package vinyldns.core.domain.backend

import cats.data.NonEmptyList
import cats.effect.{Blocker, ContextShift, IO}
import com.typesafe.config.Config
import pureconfig._
import pureconfig.generic.auto._
import pureconfig.module.catseffect.syntax._

final case class BackendConfigs(defaultBackendId: String, backends: NonEmptyList[BackendConfig])

object BackendConfigs {
  def load(config: Config)(implicit cs: ContextShift[IO]): IO[BackendConfigs] =
    Blocker[IO].use(
      ConfigSource.fromConfig(config).loadF[IO, BackendConfigs](_)
    )
}