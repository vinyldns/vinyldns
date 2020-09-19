package vinyldns.api.backend

import cats.data.NonEmptyList
import vinyldns.core.domain.backend.BackendConfig

final case class ApiBackendConfig(defaultBackendId: String, backends: NonEmptyList[BackendConfig])
