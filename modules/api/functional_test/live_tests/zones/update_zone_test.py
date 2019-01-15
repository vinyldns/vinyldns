import pytest
import uuid

from hamcrest import *
from vinyldns_python import VinylDNSClient
from vinyldns_context import VinylDNSTestContext
from utils import *


def test_update_zone_success(shared_zone_test_context):
    """
    Test updating a zone
    """
    client = shared_zone_test_context.ok_vinyldns_client
    result_zone = None
    try:
        zone_name = 'one-time'

        acl_rule = {
            'accessLevel': 'Read',
            'description': 'test-acl-updated-by-updatezn',
            'userId': 'ok',
            'recordMask': 'www-*',
            'recordTypes': ['A', 'AAAA', 'CNAME']
        }

        zone = {
            'name': zone_name,
            'email': 'test@test.com',
            'adminGroupId': shared_zone_test_context.ok_group['id'],
            'connection': {
                'name': 'vinyldns.',
                'keyName': VinylDNSTestContext.dns_key_name,
                'key': VinylDNSTestContext.dns_key,
                'primaryServer': VinylDNSTestContext.dns_ip
            },
            'transferConnection': {
                'name': 'vinyldns.',
                'keyName': VinylDNSTestContext.dns_key_name,
                'key': VinylDNSTestContext.dns_key,
                'primaryServer': VinylDNSTestContext.dns_ip
            }
        }
        result = client.create_zone(zone, status=202)
        result_zone = result['zone']
        client.wait_until_zone_exists(result)

        result_zone['email'] = 'foo@bar.com'
        result_zone['acl']['rules'] = [acl_rule]
        update_result = client.update_zone(result_zone, status=202)
        client.wait_until_zone_change_status_synced(update_result)

        assert_that(update_result['changeType'], is_('Update'))
        assert_that(update_result['userId'], is_('ok'))
        assert_that(update_result, has_key('created'))

        get_result = client.get_zone(result_zone['id'])

        uz = get_result['zone']
        assert_that(uz['email'], is_('foo@bar.com'))
        assert_that(uz['updated'], is_not(none()))

        acl = uz['acl']
        verify_acl_rule_is_present_once(acl_rule, acl)

    finally:
        if result_zone:
            client.abandon_zones([result_zone['id']], status=202)

def test_update_bad_acl_fails(shared_zone_test_context):
    """
    Test that updating a zone with a bad ACL rule fails
    """
    client = shared_zone_test_context.ok_vinyldns_client
    zone = shared_zone_test_context.ok_zone

    acl_bad_regex = {
        'accessLevel': 'Read',
        'description': 'test-acl-updated-by-updatezn-bad',
        'userId': 'ok',
        'recordMask': '*',
        'recordTypes': ['A', 'AAAA', 'CNAME']
    }

    zone['acl']['rules'] = [acl_bad_regex]

    client.update_zone(zone, status=400)


def test_update_acl_no_group_or_user_fails(shared_zone_test_context):
    """
    Test that updating a zone with an ACL with no user/group fails
    """
    client = shared_zone_test_context.ok_vinyldns_client
    zone = shared_zone_test_context.ok_zone

    bad_acl = {
        'accessLevel': 'Read',
        'description': 'test-acl-updated-by-updatezn-bad-ids',
        'recordMask': 'www-*',
        'recordTypes': ['A', 'AAAA', 'CNAME']
    }

    zone['acl']['rules'] = [bad_acl]

    client.update_zone(zone, status=400)



def test_update_missing_zone_data(shared_zone_test_context):
    """
    Test that updating a zone without providing necessary data returns errors and fails the update
    """

    client = shared_zone_test_context.ok_vinyldns_client
    result_zone = None
    try:
        zone_name = 'one-time.'

        zone = {
            'name': zone_name,
            'email': 'test@test.com',
            'adminGroupId': shared_zone_test_context.ok_group['id'],
            'connection': {
                'name': 'vinyldns.',
                'keyName': VinylDNSTestContext.dns_key_name,
                'key': VinylDNSTestContext.dns_key,
                'primaryServer': VinylDNSTestContext.dns_ip
            },
            'transferConnection': {
                'name': 'vinyldns.',
                'keyName': VinylDNSTestContext.dns_key_name,
                'key': VinylDNSTestContext.dns_key,
                'primaryServer': VinylDNSTestContext.dns_ip
            }
        }
        result = client.create_zone(zone, status=202)
        result_zone = result['zone']
        client.wait_until_zone_exists(result)

        update_zone = {
            'id': result_zone['id'],
            'name': result_zone['name'],
            'random_key': 'some_value',
            'another_key': 'meaningless_data',
            'adminGroupId': zone['adminGroupId']
        }

        errors = client.update_zone(update_zone, status=400)['errors']
        assert_that(errors, contains_inanyorder('Missing Zone.email'))

        # Check that the failed update didn't go through
        zone_get = client.get_zone(result_zone['id'])['zone']
        assert_that(zone_get['name'], is_(zone_name))

    finally:
        if result_zone:
            client.abandon_zones([result_zone['id']], status=202)


def test_update_invalid_zone_data(shared_zone_test_context):
    """
    Test that creating a zone with invalid data returns errors and fails the update
    """
    client = shared_zone_test_context.ok_vinyldns_client
    result_zone = None
    try:
        zone_name = 'one-time.'

        zone = {
            'name': zone_name,
            'email': 'test@test.com',
            'adminGroupId': shared_zone_test_context.ok_group['id'],
            'connection': {
                'name': 'vinyldns.',
                'keyName': VinylDNSTestContext.dns_key_name,
                'key': VinylDNSTestContext.dns_key,
                'primaryServer': VinylDNSTestContext.dns_ip
            },
            'transferConnection': {
                'name': 'vinyldns.',
                'keyName': VinylDNSTestContext.dns_key_name,
                'key': VinylDNSTestContext.dns_key,
                'primaryServer': VinylDNSTestContext.dns_ip
            }
        }
        result = client.create_zone(zone, status=202)
        result_zone = result['zone']
        client.wait_until_zone_exists(result)

        update_zone = {
            'id': result_zone['id'],
            'name': result_zone['name'],
            'email': 'test@test.com',
            'adminGroupId': True
        }

        errors = client.update_zone(update_zone, status=400)['errors']
        assert_that(errors, contains_inanyorder('Do not know how to convert JBool(true) into class java.lang.String'))

        # Check that the failed update didn't go through
        zone_get = client.get_zone(result_zone['id'])['zone']
        assert_that(zone_get['name'], is_(zone_name))

    finally:
        if result_zone:
            client.abandon_zones([result_zone['id']], status=202)


def test_update_zone_returns_404_if_zone_not_found(shared_zone_test_context):
    """
    Test updating a zone returns a 404 if the zone was not found
    """
    client = shared_zone_test_context.ok_vinyldns_client
    zone = {
        'name': 'one-time.',
        'email': 'test@test.com',
        'id': 'nothere',
        'connection': {
            'name': 'old-shared.',
            'keyName': VinylDNSTestContext.dns_key_name,
            'key': VinylDNSTestContext.dns_key,
            'primaryServer': VinylDNSTestContext.dns_ip
        },
        'transferConnection': {
            'name': 'old-shared.',
            'keyName': VinylDNSTestContext.dns_key_name,
            'key': VinylDNSTestContext.dns_key,
            'primaryServer': VinylDNSTestContext.dns_ip
        },
        'adminGroupId': shared_zone_test_context.ok_group['id']
    }
    client.update_zone(zone, status=404)


def test_create_acl_group_rule_success(shared_zone_test_context):
    """
    Test creating an acl rule successfully
    """
    client = shared_zone_test_context.ok_vinyldns_client

    acl_rule = {
        'accessLevel': 'Read',
        'description': 'test-acl-group-id',
        'groupId': shared_zone_test_context.ok_group['id'],
        'recordMask': 'www-*',
        'recordTypes': ['A', 'AAAA', 'CNAME']
    }
    result = client.add_zone_acl_rule_with_wait(shared_zone_test_context.system_test_zone['id'], acl_rule, status=202)

    # This is async, we get a zone change back
    acl = result['zone']['acl']

    verify_acl_rule_is_present_once(acl_rule, acl)

    # make sure that our acl rule appears on the zone
    zone = client.get_zone(result['zone']['id'])['zone']

    acl = zone['acl']

    verify_acl_rule_is_present_once(acl_rule, acl)


def test_create_acl_user_rule_success(shared_zone_test_context):
    """
    Test creating an acl rule successfully
    """
    client = shared_zone_test_context.ok_vinyldns_client

    acl_rule = {
        'accessLevel': 'Read',
        'description': 'test-acl-user-id',
        'userId': 'ok',
        'recordMask': 'www-*',
        'recordTypes': ['A', 'AAAA', 'CNAME']
    }
    result = client.add_zone_acl_rule_with_wait(shared_zone_test_context.system_test_zone['id'], acl_rule, status=202)

    # This is async, we get a zone change back
    acl = result['zone']['acl']

    verify_acl_rule_is_present_once(acl_rule, acl)

    # make sure that our acl rule appears on the zone
    zone = client.get_zone(result['zone']['id'])['zone']

    acl = zone['acl']

    verify_acl_rule_is_present_once(acl_rule, acl)


def test_create_acl_user_rule_invalid_regex_failure(shared_zone_test_context):
    """
    Test creating an acl rule with an invalid regex mask fails
    """
    client = shared_zone_test_context.ok_vinyldns_client

    acl_rule = {
        'accessLevel': 'Read',
        'description': 'test-acl-user-id',
        'userId': '789',
        'recordMask': 'x{5,-3}',
        'recordTypes': ['A', 'AAAA', 'CNAME']
    }

    errors = client.add_zone_acl_rule(shared_zone_test_context.system_test_zone['id'], acl_rule, status=400)
    assert_that(errors,contains_string("record mask x{5,-3} is an invalid regex"))


def test_create_acl_user_rule_invalid_cidr_failure(shared_zone_test_context):
    """
    Test creating an acl rule with an invalid cidr mask fails
    """
    client = shared_zone_test_context.ok_vinyldns_client

    acl_rule = {
        'accessLevel': 'Read',
        'description': 'test-acl-user-id',
        'userId': '789',
        'recordMask': '10.0.0.0/50',
        'recordTypes': ['PTR']
    }

    errors = client.add_zone_acl_rule(shared_zone_test_context.ip4_reverse_zone['id'], acl_rule, status=400)
    assert_that(errors,contains_string("PTR types must have no mask or a valid CIDR mask: IPv4 mask must be between 0 and 32"))


def test_create_acl_user_rule_valid_cidr_success(shared_zone_test_context):
    """
    Test creating an acl rule with a valid cidr mask passes
    """
    client = shared_zone_test_context.ok_vinyldns_client

    acl_rule = {
        'accessLevel': 'Read',
        'description': 'test-acl-user-id',
        'userId': 'ok',
        'recordMask': '10.0.0.0/20',
        'recordTypes': ['PTR']
    }

    result = client.add_zone_acl_rule_with_wait(shared_zone_test_context.ip4_reverse_zone['id'], acl_rule, status=202)

    # This is async, we get a zone change back
    acl = result['zone']['acl']

    verify_acl_rule_is_present_once(acl_rule, acl)

    # make sure that our acl rule appears on the zone
    zone = client.get_zone(result['zone']['id'])['zone']

    acl = zone['acl']

    verify_acl_rule_is_present_once(acl_rule, acl)


def test_create_acl_user_rule_multiple_cidr_failure(shared_zone_test_context):
    """
    Test creating an acl rule with multiple record types including PTR and a cidr mask fails
    """
    client = shared_zone_test_context.ok_vinyldns_client

    acl_rule = {
        'accessLevel': 'Read',
        'description': 'test-acl-user-id',
        'userId': '789',
        'recordMask': '10.0.0.0/20',
        'recordTypes': ['PTR','A','AAAA']
    }

    errors = client.add_zone_acl_rule(shared_zone_test_context.ip4_reverse_zone['id'], acl_rule, status=400)
    assert_that(errors,contains_string("Multiple record types including PTR must have no mask"))


def test_create_acl_user_rule_multiple_none_success(shared_zone_test_context):
    """
    Test creating an acl rule with multiple record types and no mask passes
    """
    client = shared_zone_test_context.ok_vinyldns_client

    acl_rule = {
        'accessLevel': 'Read',
        'description': 'test-acl-user-id',
        'userId': 'ok',
        'recordTypes': ['PTR','A','AAAA']
    }

    result = client.add_zone_acl_rule_with_wait(shared_zone_test_context.ip4_reverse_zone['id'], acl_rule, status=202)

    # This is async, we get a zone change back
    acl = result['zone']['acl']

    verify_acl_rule_is_present_once(acl_rule, acl)

    # make sure that our acl rule appears on the zone
    zone = client.get_zone(result['zone']['id'])['zone']

    acl = zone['acl']

    verify_acl_rule_is_present_once(acl_rule, acl)


def test_create_acl_user_rule_multiple_non_cidr_failure(shared_zone_test_context):
    """
    Test creating an acl rule with multiple record types including PTR and non cidr mask fails
    """
    client = shared_zone_test_context.ok_vinyldns_client

    acl_rule = {
        'accessLevel': 'Read',
        'description': 'test-acl-user-id',
        'userId': '789',
        'recordMask': 'www-*',
        'recordTypes': ['PTR','A','AAAA']
    }

    errors = client.add_zone_acl_rule(shared_zone_test_context.ip4_reverse_zone['id'], acl_rule, status=400)
    assert_that(errors,contains_string("Multiple record types including PTR must have no mask"))


def test_create_acl_idempotent(shared_zone_test_context):
    """
    Test creating the same acl rule multiple times results in only one rule added
    """
    client = shared_zone_test_context.ok_vinyldns_client

    acl_rule = {
        'accessLevel': 'Write',
        'description': 'test-acl-idempotent',
        'userId': 'ok',
        'recordMask': 'www-*',
        'recordTypes': ['A', 'AAAA', 'CNAME']
    }
    result1 = client.add_zone_acl_rule_with_wait(shared_zone_test_context.system_test_zone['id'], acl_rule, status=202)
    result2 = client.add_zone_acl_rule_with_wait(shared_zone_test_context.system_test_zone['id'], acl_rule, status=202)
    result3 = client.add_zone_acl_rule_with_wait(shared_zone_test_context.system_test_zone['id'], acl_rule, status=202)

    zone = client.get_zone(shared_zone_test_context.system_test_zone['id'])['zone']

    acl = zone['acl']

    # we should only have one rule that we created
    verify_acl_rule_is_present_once(acl_rule, acl)


def test_delete_acl_group_rule_success(shared_zone_test_context):
    """
    Test deleting an acl rule successfully
    """
    client = shared_zone_test_context.ok_vinyldns_client

    acl_rule = {
        'accessLevel': 'Read',
        'description': 'test-acl-delete-group-id',
        'groupId': shared_zone_test_context.ok_group['id'],
        'recordMask': 'www-*',
        'recordTypes': ['A', 'AAAA', 'CNAME']
    }
    result = client.add_zone_acl_rule_with_wait(shared_zone_test_context.system_test_zone['id'], acl_rule, status=202)

    # make sure that our acl rule appears on the zone
    zone = client.get_zone(result['zone']['id'])['zone']

    acl = zone['acl']

    verify_acl_rule_is_present_once(acl_rule, acl)

    # delete the rule
    result = client.delete_zone_acl_rule_with_wait(shared_zone_test_context.system_test_zone['id'], acl_rule, status=202)

    # make sure that our acl is not on the zone
    zone = client.get_zone(result['zone']['id'])['zone']

    verify_acl_rule_is_not_present(acl_rule, zone['acl'])


def test_delete_acl_user_rule_success(shared_zone_test_context):
    """
    Test deleting an acl rule successfully
    """
    client = shared_zone_test_context.ok_vinyldns_client

    acl_rule = {
        'accessLevel': 'Read',
        'description': 'test-acl-delete-user-id',
        'userId': 'ok',
        'recordMask': 'www-*',
        'recordTypes': ['A', 'AAAA', 'CNAME']
    }
    result = client.add_zone_acl_rule_with_wait(shared_zone_test_context.system_test_zone['id'], acl_rule, status=202)

    # make sure that our acl rule appears on the zone
    zone = client.get_zone(result['zone']['id'])['zone']

    acl = zone['acl']

    verify_acl_rule_is_present_once(acl_rule, acl)

    # delete the rule
    result = client.delete_zone_acl_rule_with_wait(shared_zone_test_context.system_test_zone['id'], acl_rule, status=202)

    # make sure that our acl is not on the zone
    zone = client.get_zone(result['zone']['id'])['zone']

    verify_acl_rule_is_not_present(acl_rule, zone['acl'])


def test_delete_non_existent_acl_rule_success(shared_zone_test_context):
    """
    Test deleting an acl rule that doesn't exist still returns successfully
    """
    client = shared_zone_test_context.ok_vinyldns_client

    acl_rule = {
        'accessLevel': 'Read',
        'description': 'test-acl-delete-non-existent-user-id',
        'userId': '789',
        'recordMask': 'www-*',
        'recordTypes': ['A', 'AAAA', 'CNAME']
    }
    # delete the rule
    result = client.delete_zone_acl_rule_with_wait(shared_zone_test_context.system_test_zone['id'], acl_rule, status=202)

    # make sure that our acl is not on the zone
    zone = client.get_zone(result['zone']['id'])['zone']

    verify_acl_rule_is_not_present(acl_rule, zone['acl'])


def test_delete_acl_idempotent(shared_zone_test_context):
    """
    Test deleting the same acl rule multiple times results in only one rule remomved
    """
    client = shared_zone_test_context.ok_vinyldns_client

    acl_rule = {
        'accessLevel': 'Write',
        'description': 'test-delete-acl-idempotent',
        'userId': 'ok',
        'recordMask': 'www-*',
        'recordTypes': ['A', 'AAAA', 'CNAME']
    }
    result = client.add_zone_acl_rule_with_wait(shared_zone_test_context.system_test_zone['id'], acl_rule, status=202)

    zone = client.get_zone(shared_zone_test_context.system_test_zone['id'])['zone']

    acl = zone['acl']

    # we should only have one rule that we created
    verify_acl_rule_is_present_once(acl_rule, acl)

    result1 = client.delete_zone_acl_rule_with_wait(shared_zone_test_context.system_test_zone['id'], acl_rule, status=202)
    result2 = client.delete_zone_acl_rule_with_wait(shared_zone_test_context.system_test_zone['id'], acl_rule, status=202)
    result3 = client.delete_zone_acl_rule_with_wait(shared_zone_test_context.system_test_zone['id'], acl_rule, status=202)

    zone = client.get_zone(result['zone']['id'])['zone']

    verify_acl_rule_is_not_present(acl_rule, zone['acl'])


def test_delete_acl_removes_permissions(shared_zone_test_context):
    """
    Test that a user (who previously had permissions to view a zone via acl rules) can not view the zone once
    the acl rule is deleted
    """

    ok_client = shared_zone_test_context.ok_vinyldns_client    # ok adds and deletes acl rule
    dummy_client = shared_zone_test_context.dummy_vinyldns_client  # dummy should not be able to see ok_zone once acl rule is deleted
    ok_zone = ok_client.get_zone(shared_zone_test_context.ok_zone['id'])['zone']

    ok_view = ok_client.list_zones()['zones']
    assert_that(ok_view, has_item(ok_zone))    # ok can see ok_zone

    # verify dummy cannot see ok_zone
    dummy_view = dummy_client.list_zones()['zones']
    assert_that(dummy_view, is_not(has_item(ok_zone))) # cannot view zone

    # add acl rule
    acl_rule = {
        'accessLevel': 'Read',
        'description': 'test_delete_acl_removes_permissions',
        'userId': 'dummy',  # give dummy permission to see ok_zone
        'recordMask': 'www-*',
        'recordTypes': ['A', 'AAAA', 'CNAME']
    }
    result = ok_client.add_zone_acl_rule_with_wait(shared_zone_test_context.ok_zone['id'], acl_rule, status=202)
    ok_zone = ok_client.get_zone(shared_zone_test_context.ok_zone['id'])['zone']
    verify_acl_rule_is_present_once(acl_rule, ok_zone['acl'])

    ok_view = ok_client.list_zones()['zones']
    assert_that(ok_view, has_item(ok_zone))    # ok can still see ok_zone

    # verify dummy can see ok_zone
    dummy_view = dummy_client.list_zones()['zones']
    assert_that(dummy_view, has_item(ok_zone)) # can view zone

    # delete acl rule
    result = ok_client.delete_zone_acl_rule_with_wait(shared_zone_test_context.ok_zone['id'], acl_rule, status=202)
    ok_zone = ok_client.get_zone(shared_zone_test_context.ok_zone['id'])['zone']
    verify_acl_rule_is_not_present(acl_rule, ok_zone['acl'])

    ok_view = ok_client.list_zones()['zones']
    assert_that(ok_view, has_item(ok_zone))    # ok can still see ok_zone

    # verify dummy can not see ok_zone
    dummy_view = dummy_client.list_zones()['zones']
    assert_that(dummy_view, is_not(has_item(ok_zone))) # cannot view zone


def test_update_reverse_v4_zone(shared_zone_test_context):
    """
    Test updating a reverse IPv4 zone
    """
    client = shared_zone_test_context.ok_vinyldns_client

    zone = shared_zone_test_context.ip4_reverse_zone
    zone['email'] = 'update-test@bar.com'

    import json
    print json.dumps(zone, indent=4)
    update_result = client.update_zone(zone, status=202)
    client.wait_until_zone_change_status_synced(update_result)

    assert_that(update_result['changeType'], is_('Update'))
    assert_that(update_result['userId'], is_('ok'))
    assert_that(update_result, has_key('created'))

    get_result = client.get_zone(zone['id'])

    uz = get_result['zone']
    assert_that(uz['email'], is_('update-test@bar.com'))
    assert_that(uz['updated'], is_not(none()))



def test_update_reverse_v6_zone(shared_zone_test_context):
    """
    Test updating a reverse IPv6 zone
    """
    client = shared_zone_test_context.ok_vinyldns_client

    zone = shared_zone_test_context.ip6_reverse_zone
    zone['email'] = 'update-test@bar.com'

    update_result = client.update_zone(zone, status=202)
    client.wait_until_zone_change_status_synced(update_result)

    assert_that(update_result['changeType'], is_('Update'))
    assert_that(update_result['userId'], is_('ok'))
    assert_that(update_result, has_key('created'))

    get_result = client.get_zone(zone['id'])

    uz = get_result['zone']
    assert_that(uz['email'], is_('update-test@bar.com'))
    assert_that(uz['updated'], is_not(none()))


def test_activate_reverse_v4_zone_with_bad_key_fails(shared_zone_test_context):
    """
    Test activating a reverse IPv4 zone when using a bad tsig key fails
    """
    client = shared_zone_test_context.ok_vinyldns_client

    update = dict(shared_zone_test_context.ip4_reverse_zone)
    update['connection']['key'] = 'f00sn+4G2ldMn0q1CV3vsg=='
    client.update_zone(update, status=400)


def test_activate_reverse_v6_zone_with_bad_key_fails(shared_zone_test_context):
    """
    Test activating a reverse IPv6 zone with an invalid key fails
    """
    client = shared_zone_test_context.ok_vinyldns_client

    update = dict(shared_zone_test_context.ip6_reverse_zone)
    update['connection']['key'] = 'f00sn+4G2ldMn0q1CV3vsg=='
    client.update_zone(update, status=400)


def test_user_cannot_update_zone_to_nonexisting_admin_group(shared_zone_test_context):
    """
    Test user cannot update a zone adminGroupId to a group that does not exist
    """

    zone_update = shared_zone_test_context.ok_zone
    zone_update['adminGroupId'] = "some-bad-id"
    zone_update['connection']['key'] = VinylDNSTestContext.dns_key

    shared_zone_test_context.ok_vinyldns_client.update_zone(zone_update, status=400)


def test_user_can_update_zone_to_another_admin_group(shared_zone_test_context):
    """
    Test user can update a zone with an admin group they are a member of
    """
    #dummy is member, not admin

    client = shared_zone_test_context.dummy_vinyldns_client
    group = None

    try:
        result = client.create_zone(
            {
                'name': 'one-time.',
                'email': 'test@test.com',
                'adminGroupId': shared_zone_test_context.dummy_group['id'],
                'connection': {
                    'name': 'vinyldns.',
                    'keyName': VinylDNSTestContext.dns_key_name,
                    'key': VinylDNSTestContext.dns_key,
                    'primaryServer': VinylDNSTestContext.dns_ip
                },
                'transferConnection': {
                    'name': 'vinyldns.',
                    'keyName': VinylDNSTestContext.dns_key_name,
                    'key': VinylDNSTestContext.dns_key,
                    'primaryServer': VinylDNSTestContext.dns_ip
                }
            }, status=202
        )
        zone = result['zone']
        client.wait_until_zone_exists(result)

        import json
        print json.dumps(zone, indent=3)

        new_joint_group = {
            'name': 'new-ok-group',
            'email': 'test@test.com',
            'description': 'this is a description',
            'members': [ { 'id': 'ok', 'id': 'dummy'} ],
            'admins': [ { 'id': 'ok'} ]
        }

        group = client.create_group(new_joint_group, status=200)

        #changing the zone
        zone_update = dict(zone)
        zone_update['adminGroupId'] = group['id']

        result = client.update_zone(zone_update, status=202)
        client.wait_until_zone_change_status_synced(result)
    finally:
        if zone:
            client.delete_zone(zone['id'], status=202)
            client.wait_until_zone_deleted(zone['id'])
        if group:
            shared_zone_test_context.ok_vinyldns_client.delete_group(group['id'], status=(200, 404))



def test_user_cannot_update_zone_to_nonmember_admin_group(shared_zone_test_context):
    """
    Test user cannot update a zone adminGroupId to a group they are not a member of
    """

    zone_update = shared_zone_test_context.ok_zone
    zone_update['adminGroupId'] = shared_zone_test_context.dummy_group['id']
    zone_update['connection']['key'] = VinylDNSTestContext.dns_key

    shared_zone_test_context.ok_vinyldns_client.update_zone(zone_update, status=400)


def test_user_cannot_update_zone_to_nonexisting_admin_group(shared_zone_test_context):
    """
    Test user cannot update a zone adminGroupId to a group that does not exist
    """

    zone_update = shared_zone_test_context.ok_zone
    zone_update['adminGroupId'] = "some-bad-id"
    zone_update['connection']['key'] = VinylDNSTestContext.dns_key

    shared_zone_test_context.ok_vinyldns_client.update_zone(zone_update, status=400)


def test_acl_rule_missing_access_level(shared_zone_test_context):
    """
    Tests that missing the access level when creating an acl rule returns a 400
    """
    client = shared_zone_test_context.ok_vinyldns_client
    acl_rule = {
        'description': 'test-acl-no-access-level',
        'groupId': '456',
        'recordMask': 'www-*',
        'recordTypes': ['A', 'AAAA', 'CNAME']
    }
    errors = client.add_zone_acl_rule(shared_zone_test_context.system_test_zone['id'], acl_rule, status=400)['errors']
    assert_that(errors, has_length(1))
    assert_that(errors, contains_inanyorder('Missing ACLRule.accessLevel'))


def test_acl_rule_both_user_and_group(shared_zone_test_context):
    """
    Tests that including the user id and the group id when creating an acl rule returns a 400
    """
    client = shared_zone_test_context.ok_vinyldns_client
    acl_rule = {
        'accessLevel': 'Read',
        'userId': '789',
        'groupId': '456',
        'description': 'test-acl-no-user-or-group-level',
        'recordMask': 'www-*',
        'recordTypes': ['A', 'AAAA', 'CNAME']
    }
    errors = client.add_zone_acl_rule(shared_zone_test_context.system_test_zone['id'], acl_rule, status=400)['errors']
    assert_that(errors, has_length(1))
    assert_that(errors, contains_inanyorder('Cannot specify both a userId and a groupId'))


def test_update_zone_no_authorization(shared_zone_test_context):
    """
    Test updating a zone without authorization
    """
    client = shared_zone_test_context.ok_vinyldns_client

    zone = {
        'id': '12345',
        'name': str(uuid.uuid4()),
        'email': 'test@test.com',
    }

    client.update_zone(zone, sign_request=False, status=401)


def test_normal_user_cannot_update_shared_zone_flag(shared_zone_test_context):
    """
    Test updating a zone shared status as a normal user fails
    """
    client = shared_zone_test_context.ok_vinyldns_client

    result = client.get_zone(shared_zone_test_context.ok_zone['id'], status=200)
    zone_update = result['zone']
    zone_update['shared'] = True

    error = shared_zone_test_context.ok_vinyldns_client.update_zone(zone_update, status= 403)
    assert_that(error, contains_string('Not authorized to update zone shared status from false to true.'))


def test_toggle_test_flag(shared_zone_test_context):
    """
    Test the isTest flag is ignored in update requests
    """
    client = shared_zone_test_context.shared_zone_vinyldns_client
    zone_update = shared_zone_test_context.non_test_shared_zone
    zone_update['isTest'] = True

    change = client.update_zone(zone_update, status=202)
    client.wait_until_zone_change_status_synced(change)

    assert_that(change['zone']['isTest'], is_(False))
