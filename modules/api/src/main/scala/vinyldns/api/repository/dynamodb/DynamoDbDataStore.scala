package vinyldns.api.repository.dynamodb

import vinyldns.api.domain.batch.BatchChangeRepository
import vinyldns.api.domain.membership.{GroupChangeRepository, GroupRepository, MembershipRepository, UserRepository}
import vinyldns.api.domain.record.{RecordChangeRepository, RecordSetRepository}
import vinyldns.api.domain.zone.{ZoneChangeRepository, ZoneRepository}
import vinyldns.api.repository.DataStore

// TODO add config parameter, load based on that
class DynamoDbDataStore() extends DataStore {
  val userRepository: Option[UserRepository] = Some(UserRepository())
  val groupRepository: Option[GroupRepository] = Some(GroupRepository())
  val membershipRepository: Option[MembershipRepository] = Some(MembershipRepository())
  val groupChangeRepository: Option[GroupChangeRepository] = Some(GroupChangeRepository())
  val recordSetRepository: Option[RecordSetRepository] = Some(RecordSetRepository())
  val recordChangeRepository: Option[RecordChangeRepository] =  Some(RecordChangeRepository())
  val zoneChangeRepository: Option[ZoneChangeRepository] = Some(ZoneChangeRepository())
  val zoneRepository: Option[ZoneRepository] = None
  val batchChangeRepository: Option[BatchChangeRepository] = None
}
