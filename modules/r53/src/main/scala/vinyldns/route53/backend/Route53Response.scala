package vinyldns.route53.backend

import vinyldns.core.domain.backend.BackendResponse

sealed trait Route53Response extends BackendResponse
object Route53Response {
  case object NoError extends Route53Response
}
