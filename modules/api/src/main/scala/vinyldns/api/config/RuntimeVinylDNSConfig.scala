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
import cats.implicits._
import com.typesafe.config.{Config, ConfigFactory}
import org.slf4j.{Logger, LoggerFactory}
import vinyldns.core.domain.backend.{Backend, BackendConfigs, BackendResolver}
import vinyldns.core.domain.config.AppConfigRepository
import vinyldns.core.domain.record.RecordType
import vinyldns.core.domain.zone.Zone
import vinyldns.core.health.HealthCheck
import vinyldns.core.health.HealthCheck.HealthCheck
import vinyldns.core.notifier.NotifierConfig
import vinyldns.api.domain.access.{GlobalAcl, GlobalAcls}
import vinyldns.api.domain.zone.ZoneRecordValidations
import pureconfig._
import pureconfig.generic.auto._

import scala.collection.JavaConverters._
import scala.util.matching.Regex

object RuntimeVinylDNSConfig {

  private val logger: Logger = LoggerFactory.getLogger(getClass)

  @volatile private var rawConfig: Config = ConfigFactory.load()
  @volatile private var runtimeRef: Ref[IO, VinylDNSConfig] = _
  @volatile private var _current: VinylDNSConfig = _

  // In-memory snapshot of the app_config DB table (key → value)
  private var appConfigRef: Ref[IO, Map[String, String]] = _

  // ---------------------------------------------------------------
  // Volatile vars for complex structured configs.
  // Updated by reload() when POST /appconfig/refresh is called.
  // Services read these synchronously per-request.
  // ---------------------------------------------------------------
  @volatile private var _highValueDomainConfig: HighValueDomainConfig =
    ConfigSource.fromConfig(rawConfig).at("vinyldns.high-value-domains")
      .load[HighValueDomainConfig].getOrElse(HighValueDomainConfig(Nil, Nil))
  @volatile private var _dottedHostsConfig: DottedHostsConfig =
    ConfigSource.fromConfig(rawConfig).at("vinyldns.dotted-hosts")
      .load[DottedHostsConfig].getOrElse(DottedHostsConfig(Nil))
  @volatile private var _validEmailConfig: ValidEmailConfig =
    ConfigSource.fromConfig(rawConfig).at("vinyldns.valid-email-config")
      .load[ValidEmailConfig].getOrElse(ValidEmailConfig(Nil, 0))
  @volatile private var _limitsConfig: LimitsConfig =
    ConfigSource.fromConfig(rawConfig).at("vinyldns.api.limits")
      .load[LimitsConfig].getOrElse(LimitsConfig(0, 0, 0, 0, 0, 0, 0))
  @volatile private var _sharedApprovedTypes: List[RecordType.Value] =
    ConfigSource.fromConfig(rawConfig).at("vinyldns")
      .load[BatchChangeConfig].map(_.allowedRecordTypes).getOrElse(Nil)
  @volatile private var _manualReviewConfig: ManualReviewConfig =
    ConfigSource.fromConfig(rawConfig).at("vinyldns")
      .load[ManualReviewConfig].getOrElse(ManualReviewConfig(enabled = false, Nil, Nil, Set.empty))
  @volatile private var _approvedNameServers: List[Regex] = Nil
  @volatile private var _globalAcls: GlobalAcls = GlobalAcls(Nil)
  // BackendResolver updated by applyDbOverrides(); all services hold a reference to the
  // dynamicBackendResolver proxy which reads the volatile var on every call.
  @volatile private var _backendResolver: BackendResolver = _

  /**
   * Stable-reference proxy for BackendResolver.
   * Pass this to all services at startup — method calls delegate through
   * the volatile `_backendResolver`, so a DB update + POST /appconfig/refresh
   * transparently swaps the underlying resolver without restarting.
   */
  val dynamicBackendResolver: BackendResolver = new BackendResolver {
    def resolve(zone: Zone): Backend         = _backendResolver.resolve(zone)
    def healthCheck(timeout: Int): HealthCheck = _backendResolver.healthCheck(timeout)
    def isRegistered(backendId: String): Boolean = _backendResolver.isRegistered(backendId)
    def ids: cats.data.NonEmptyList[String]  = _backendResolver.ids
  }

  def init(): IO[Unit] =
    for {
      cfg <- VinylDNSConfig.loadFrom(rawConfig)
      ref <- Ref.of[IO, VinylDNSConfig](cfg)
      dbRef <- Ref.of[IO, Map[String, String]](Map.empty)
      _ <- IO {
        runtimeRef = ref
        _current = cfg
        appConfigRef = dbRef
        _highValueDomainConfig = cfg.highValueDomainConfig
        _dottedHostsConfig = cfg.dottedHostsConfig
        _validEmailConfig = cfg.validEmailConfig
        _limitsConfig = cfg.limitsconfig
        _sharedApprovedTypes = cfg.batchChangeConfig.allowedRecordTypes
        _manualReviewConfig = cfg.manualReviewConfig
        _approvedNameServers = cfg.serverConfig.approvedNameServers
        _globalAcls = cfg.globalAcls
        logger.info("[RuntimeConfig:init] Loaded VinylDNSConfig")
      }
    } yield ()

  def loadFromDb(repo: AppConfigRepository): IO[Unit] =
    repo.getAll.flatMap { rows =>
      val snapshot = rows.map(r => r.key -> r.value).toMap
      appConfigRef.set(snapshot)
    }

  def refresh(repo: AppConfigRepository): IO[Unit] =
    repo.getAll
      .flatMap { rows =>
        val updated = rows.map(r => r.key -> r.value).toMap
        appConfigRef.set(updated)
      }
      .handleErrorWith { err =>
        IO(logger.warn(s"[RuntimeConfig:refresh] Failed – retaining stale values. Cause: $err"))
      }

  def getAll: IO[Map[String, String]] = appConfigRef.get
  def get(key: String): IO[Option[String]] = appConfigRef.get.map(_.get(key))
  def getOrElse(key: String, default: String): IO[String] =
    get(key).map(_.filter(_.nonEmpty).getOrElse(default))

  def current: VinylDNSConfig = _current

  def currentIO: IO[VinylDNSConfig] = runtimeRef.get

  def getRaw: Config = rawConfig

  /** Re-reads application.conf and refreshes ALL non-bootstrap configs including complex ones.
   *  Called by POST /appconfig/refresh so file edits also take effect without restart. */
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
        _highValueDomainConfig = cfg.highValueDomainConfig
        _dottedHostsConfig = cfg.dottedHostsConfig
        _validEmailConfig = cfg.validEmailConfig
        _approvedNameServers = cfg.serverConfig.approvedNameServers
        _globalAcls = cfg.globalAcls
        logger.info("[RuntimeConfig:reload] Reload completed successfully")
      }
      _ <- applyDbOverrides()
    } yield ()

  /**
   * Rebuilds volatile vars that can be overridden by DB values.
   * Called after loadFromDb() at startup and inside reload().
   * Also rebuilds the BackendResolver from the DB blob so
   * POST /appconfig/refresh dynamically swaps DNS backends.
   */
  private def parseManualReviewConfig(snapshot: Map[String, String]): ManualReviewConfig = {
    import ZoneRecordValidations.toCaseIgnoredRegexList
    import com.comcast.ip4s.IpAddress
    val enabled = snapshot.get("manual-batch-review-enabled").forall(_.trim.toBoolean)
    snapshot.get("manual-review-domains") match {
      case Some(json) =>
        import com.typesafe.config.ConfigFactory
        val cfg = ConfigFactory.parseString(json)
        val domainList = if (cfg.hasPath("domain-list")) cfg.getStringList("domain-list").asScala.toList else Nil
        val ipList     = if (cfg.hasPath("ip-list"))     cfg.getStringList("ip-list").asScala.toList.flatMap(IpAddress.fromString(_)) else Nil
        val zoneList   = if (cfg.hasPath("zone-name-list")) cfg.getStringList("zone-name-list").asScala.toSet else Set.empty[String]
        ManualReviewConfig(enabled, toCaseIgnoredRegexList(domainList), ipList, zoneList)
      case None =>
        ManualReviewConfig(enabled, Nil, Nil, Set.empty)
    }
  }

  private def parseFromJson[A: ConfigReader](snapshot: Map[String, String], key: String): Option[A] =
    snapshot.get(key).flatMap { json =>
      ConfigSource.fromConfig(ConfigFactory.parseString(json)).load[A].fold(
        err => { logger.warn(s"[RuntimeConfig] Failed to parse DB key '$key': $err"); None },
        a   => Some(a)
      )
    }

  def applyDbOverrides(): IO[Unit] =
    appConfigRef.get.flatMap { snapshot =>
      IO {
        _limitsConfig = buildLimitsConfig(snapshot)
        snapshot
          .get("shared-approved-types")
          .map(_.split(",").toList.flatMap(s => RecordType.find(s.trim)))
          .foreach(_sharedApprovedTypes = _)
        _manualReviewConfig = parseManualReviewConfig(snapshot)
        parseFromJson[HighValueDomainConfig](snapshot, "high-value-domains").foreach(_highValueDomainConfig = _)
        parseFromJson[DottedHostsConfig](snapshot, "dotted-hosts").foreach(_dottedHostsConfig = _)
        parseFromJson[ValidEmailConfig](snapshot, "valid-email").foreach(_validEmailConfig = _)
        snapshot.get("approved-name-servers").foreach { csv =>
          _approvedNameServers = ZoneRecordValidations.toCaseIgnoredRegexList(
            csv.split(",").map(_.trim).filter(_.nonEmpty).toList
          )
        }
        snapshot.get("global-acl-rules").foreach { json =>
          ConfigSource.fromConfig(ConfigFactory.parseString(s"rules = $json")).at("rules").load[List[GlobalAcl]]
            .fold(err => logger.warn(s"[RuntimeConfig] Failed to parse global-acl-rules: $err"),
                  acls => _globalAcls = GlobalAcls(acls))
        }
      }
    } >> effectiveBackendConfigs.flatMap { cfg =>
      BackendResolver.apply(cfg).flatMap { resolver =>
        IO {
          _backendResolver = resolver
          logger.info("[RuntimeConfig:applyDbOverrides] DB overrides applied (incl. BackendResolver)")
        }
      }
    }.handleErrorWith { err =>
      IO(logger.warn(s"[RuntimeConfig:applyDbOverrides] BackendResolver from DB failed – falling back to static config. Cause: $err")) >>
        BackendResolver.apply(_current.backendConfigs).flatMap { resolver =>
          IO {
            _backendResolver = resolver
            logger.info("[RuntimeConfig:applyDbOverrides] BackendResolver built from static config (DB fallback)")
          }
        }
    }

  /** High-value domain config. Refreshed when reload() is called (POST /appconfig/refresh). */
  def highValueDomainConfig: HighValueDomainConfig = _highValueDomainConfig

  /** Dotted-hosts config. Refreshed when reload() is called. */
  def dottedHostsConfig: DottedHostsConfig = _dottedHostsConfig

  /** Valid email config. Refreshed when reload() is called. */
  def validEmailConfig: ValidEmailConfig = _validEmailConfig

  /** Manual review config. Updated by applyDbOverrides() from DB key 'manual-review-domains'. */
  def manualReviewConfig: ManualReviewConfig = _manualReviewConfig

  /** Approved name servers. Updated by applyDbOverrides() from DB key 'approved-name-servers' (comma-separated). */
  def approvedNameServers: List[Regex] = _approvedNameServers

  /** Global ACL rules. Updated by applyDbOverrides() from DB key 'global-acl-rules' (JSON array). */
  def globalAcls: GlobalAcls = _globalAcls

  def syncDelay: IO[Int] =
    get("sync-delay").map(_.map(_.toInt).getOrElse(_current.serverConfig.syncDelay))

  def maxZoneSize: IO[Int] =
    get("max-zone-size").map(_.map(_.toInt).getOrElse(_current.serverConfig.maxZoneSize))

  def useRecordSetCache: IO[Boolean] =
    get("use-recordset-cache").map(_.map(_.toBoolean).getOrElse(_current.serverConfig.useRecordSetCache))

  def validateRecordLookupAgainstDnsBackend: IO[Boolean] =
    get("validate-recordset-lookup-against-dns-backend").map(_.map(_.toBoolean).getOrElse(_current.serverConfig.validateRecordLookupAgainstDnsBackend))

  def manualReviewEnabled: IO[Boolean] =
    get("manual-batch-review-enabled").map(_.map(_.toBoolean).getOrElse(_current.manualReviewConfig.enabled))

  def scheduledChangesEnabled: IO[Boolean] =
    get("scheduled-changes-enabled").map(_.map(_.toBoolean).getOrElse(_current.runtime.scheduledChangesConfig.enabled))

  def batchChangeLimit: IO[Int] =
    get("batch-change-limit").map(_.map(_.toInt).getOrElse(_current.batchChangeConfig.limit))

  def defaultTtl: IO[Long] =
    get("default-ttl").map(_.map(_.toLong).getOrElse(_current.serverConfig.defaultTtl.toLong))

  def loadTestData: IO[Boolean] =
    get("load-test-data").map(_.map(_.toBoolean).getOrElse(_current.serverConfig.loadTestData))

  def isZoneSyncScheduleAllowed: IO[Boolean] =
    get("is-zone-sync-schedule-allowed").map(_.map(_.toBoolean).getOrElse(_current.serverConfig.isZoneSyncScheduleAllowed))

  def restHost: IO[String] =
    get("rest.host").map(_.getOrElse(_current.httpConfig.host))

  def restPort: IO[Int] =
    get("rest.port").map(_.map(_.toInt).getOrElse(_current.httpConfig.port))

  def queueMessagesPerPoll: IO[Int] =
    get("queue.messages-per-poll").map(_.map(_.toInt).getOrElse(_current.messageQueueConfig.messagesPerPoll))

  def queuePollingIntervalMillis: IO[Long] =
    get("queue.polling-interval-millis").map(_.map(_.toLong).getOrElse(_current.messageQueueConfig.pollingInterval.toMillis))

  /** Limits config. Volatile var rebuilt by applyDbOverrides() — DB keys override file values. */
  def limitsConfig: LimitsConfig = _limitsConfig

  /** Shared approved record types. Volatile var, DB key: shared-approved-types (comma-separated). */
  def sharedApprovedTypes: List[RecordType.Value] = _sharedApprovedTypes

  /** Processing disabled flag — DB key: processing-disabled. Default: false. */
  def processingDisabled: IO[Boolean] =
    get("processing-disabled").map(_.map(_.toBoolean).getOrElse(_current.serverConfig.processingDisabled))

  // Effective startup configs — DB JSON blob overrides file at startup
  /**
   * Returns BackendConfigs loaded from the DB blob at key "backend.config-json".
   * The application.conf backend section is ignored at runtime — the DB is the
   * single source of truth for backend configuration.
   * Fails with a clear error if the key is missing from the DB.
   */
  def effectiveBackendConfigs: IO[BackendConfigs] =
    get("backend-dns-zone.config-json").flatMap {
      case Some(json) =>
        IO(ConfigFactory.parseString(json))
          .flatMap { cfg =>
            implicit val cs: cats.effect.ContextShift[IO] = IO.contextShift(scala.concurrent.ExecutionContext.global)
            BackendConfigs.load(cfg)
          }
          .handleErrorWith { err =>
            IO.raiseError(new RuntimeException(
              s"[RuntimeConfig] Failed to parse backend-dns-zone.config-json from DB: $err"
            ))
          }
      case None =>
        IO.raiseError(new RuntimeException(
          "[RuntimeConfig] 'backend-dns-zone.config-json' not found in app_config table. " +
          "Insert the backend configuration into the DB before starting."
        ))
    }

  /**
   * Returns notifier configs with DB key overrides applied only to email settings.
   * SNS settings are read exclusively from application.conf (they are secured credentials).
   *
   * DB key format for email: `email.settings.{key}`
   *   e.g. email.settings.from = "VinylDNS <no-reply@example.com>"
   */
  def effectiveNotifierConfigs: IO[List[NotifierConfig]] =
    appConfigRef.get.map { snapshot =>
      _current.notifierConfigs.map { nc =>
        val isEmail = nc.className.toLowerCase.contains("email")
        if (!isEmail) nc
        else {
          val prefix = "email.settings."
          val overrideMap = snapshot
            .filter { case (k, _) => k.startsWith(prefix) }
            .map    { case (k, v) => k.stripPrefix(prefix) -> v }
          if (overrideMap.isEmpty) nc
          else {
            val overrideCfg = ConfigFactory.parseMap(overrideMap.asJava)
            NotifierConfig(nc.className, overrideCfg.withFallback(nc.settings))
          }
        }
      }
    }

  private def buildLimitsConfig(snapshot: Map[String, String]): LimitsConfig = {
    val c = _current.limitsconfig
    LimitsConfig(
      snapshot.get("batchchange-routing-max-items-limit").map(_.toInt).getOrElse(c.BATCHCHANGE_ROUTING_MAX_ITEMS_LIMIT),
      snapshot.get("membership-routing-default-max-items").map(_.toInt).getOrElse(c.MEMBERSHIP_ROUTING_DEFAULT_MAX_ITEMS),
      snapshot.get("membership-routing-max-items-limit").map(_.toInt).getOrElse(c.MEMBERSHIP_ROUTING_MAX_ITEMS_LIMIT),
      snapshot.get("membership-routing-max-groups-list-limit").map(_.toInt).getOrElse(c.MEMBERSHIP_ROUTING_MAX_GROUPS_LIST_LIMIT),
      snapshot.get("recordset-routing-default-max-items").map(_.toInt).getOrElse(c.RECORDSET_ROUTING_DEFAULT_MAX_ITEMS),
      snapshot.get("zone-routing-default-max-items").map(_.toInt).getOrElse(c.ZONE_ROUTING_DEFAULT_MAX_ITEMS),
      snapshot.get("zone-routing-max-items-limit").map(_.toInt).getOrElse(c.ZONE_ROUTING_MAX_ITEMS_LIMIT)
    )
  }
}
