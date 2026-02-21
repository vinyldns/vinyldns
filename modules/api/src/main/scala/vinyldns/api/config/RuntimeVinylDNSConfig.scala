/*
 * Copyright 2018 Comcast Cable Communications Management, LLC
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

package vinyldns.api.config

import cats.effect.IO
import cats.effect.concurrent.Ref
import com.typesafe.config.{Config, ConfigFactory}
import org.slf4j.{Logger, LoggerFactory}

object RuntimeVinylDNSConfig {

  private val logger: Logger = LoggerFactory.getLogger(getClass)

  @volatile private var rawConfig: Config = ConfigFactory.load()
  @volatile private var runtimeRef: Ref[IO, VinylDNSConfig] = _
  @volatile private var _current: VinylDNSConfig = _

  def init(): IO[Unit] =
    for {
      cfg <- VinylDNSConfig.loadFrom(rawConfig)
      ref <- Ref.of[IO, VinylDNSConfig](cfg)
      _ <- IO {
        runtimeRef = ref
        _current = cfg
        logger.info("[RuntimeConfig:init] Loaded VinylDNSConfig")
      }
    } yield ()

  def current: VinylDNSConfig = _current

  def currentIO: IO[VinylDNSConfig] =
    runtimeRef.get

  def getRaw: Config =
    rawConfig

  def reload(): IO[Unit] =
    for {
      _ <- IO {
        ConfigFactory.invalidateCaches()
        rawConfig = ConfigFactory.load()
        logger.info("[RuntimeConfig:reload] Raw config reloaded")
      }
      cfg <- VinylDNSConfig.loadFrom(rawConfig)
      _ <- runtimeRef.set(cfg)
      _ <- IO {
        _current = cfg
        logger.info("[RuntimeConfig:reload] Reload completed successfully")
      }
    } yield ()
}
