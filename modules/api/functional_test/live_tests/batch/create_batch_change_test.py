from hamcrest import *
from utils import *

def does_not_contain(x):
    is_not(contains(x))

def validate_change_error_response_basics(input_json, change_type, input_name, record_type, ttl, record_data):
    assert_that(input_json['changeType'], is_(change_type))
    assert_that(input_json['inputName'], is_(input_name))
    assert_that(input_json['type'], is_(record_type))
    assert_that(record_type, is_in(['A', 'AAAA', 'CNAME', 'PTR', 'TXT', 'MX']))
    if change_type=="Add":
        assert_that(input_json['ttl'], is_(ttl))
        if record_type in ["A", "AAAA"]:
            assert_that(input_json['record']['address'], is_(record_data))
        elif record_type=="CNAME":
            assert_that(input_json['record']['cname'], is_(record_data))
        elif record_type=="PTR":
            assert_that(input_json['record']['ptrdname'], is_(record_data))
        elif record_type=="TXT":
            assert_that(input_json['record']['text'], is_(record_data))
        elif record_type=="MX":
            assert_that(input_json['record']['preference'], is_(record_data['preference']))
            assert_that(input_json['record']['exchange'], is_(record_data['exchange']))
    return

def assert_failed_change_in_error_response(input_json, change_type="Add", input_name="fqdn.", record_type="A", ttl=200, record_data="1.1.1.1", error_messages=[]):
    validate_change_error_response_basics(input_json, change_type, input_name, record_type, ttl, record_data)
    assert_error(input_json, error_messages)
    return

def assert_successful_change_in_error_response(input_json, change_type="Add", input_name="fqdn.", record_type="A", ttl=200, record_data="1.1.1.1"):
    validate_change_error_response_basics(input_json, change_type, input_name, record_type, ttl, record_data)
    assert_that('errors' in input_json, is_(False))
    return

def assert_change_success_response_values(changes_json, zone, index, record_name, input_name, record_data, ttl=200, record_type="A", change_type="Add"):
    assert_that(changes_json[index]['zoneId'], is_(zone['id']))
    assert_that(changes_json[index]['zoneName'], is_(zone['name']))
    assert_that(changes_json[index]['recordName'], is_(record_name))
    assert_that(changes_json[index]['inputName'], is_(input_name))
    if change_type=="Add":
        assert_that(changes_json[index]['ttl'], is_(ttl))
    assert_that(changes_json[index]['type'], is_(record_type))
    assert_that(changes_json[index]['id'], is_not(none()))
    assert_that(changes_json[index]['changeType'], is_(change_type))
    assert_that(record_type, is_in(['A', 'AAAA', 'CNAME', 'PTR', 'TXT', 'MX']))
    if record_type in ["A", "AAAA"] and change_type=="Add":
        assert_that(changes_json[index]['record']['address'], is_(record_data))
    elif record_type=="CNAME" and change_type=="Add":
        assert_that(changes_json[index]['record']['cname'], is_(record_data))
    elif record_type=="PTR" and change_type=="Add":
        assert_that(changes_json[index]['record']['ptrdname'], is_(record_data))
    elif record_type=="TXT" and change_type=="Add":
        assert_that(changes_json[index]['record']['text'], is_(record_data))
    elif record_type=="MX" and change_type=="Add":
        assert_that(changes_json[index]['record']['preference'], is_(record_data['preference']))
        assert_that(changes_json[index]['record']['exchange'], is_(record_data['exchange']))
    return

def assert_error(input_json, error_messages):
    for error in error_messages:
        assert_that(input_json['errors'], has_item(error))
    assert_that(len(input_json['errors']), is_(len(error_messages)))


def test_create_batch_change_with_adds_success(shared_zone_test_context):
    """
    Test successfully creating a batch change with adds
    """
    client = shared_zone_test_context.ok_vinyldns_client
    parent_zone = shared_zone_test_context.parent_zone
    ok_zone = shared_zone_test_context.ok_zone
    classless_delegation_zone = shared_zone_test_context.classless_zone_delegation_zone
    classless_base_zone = shared_zone_test_context.classless_base_zone
    ip6_reverse_zone = shared_zone_test_context.ip6_reverse_zone

    batch_change_input = {
        "comments": "this is optional",
        "changes": [
            get_change_A_AAAA_json("parent.com.", address="4.5.6.7"),
            get_change_A_AAAA_json("parent.com", address="4.5.6.7"),
            get_change_A_AAAA_json("ok.", record_type="AAAA", address="fd69:27cc:fe91::60"),
            get_change_A_AAAA_json("relative.parent.com.", address="1.1.1.1"),
            get_change_A_AAAA_json("relative.parent.com", address="2.2.2.2"),
            get_change_CNAME_json("cname.parent.com", cname="nice.parent.com"),
            get_change_CNAME_json("2cname.parent.com", cname="nice.parent.com"),
            get_change_CNAME_json("4.2.0.192.in-addr.arpa.", cname="4.4/30.2.0.192.in-addr.arpa."),
            get_change_PTR_json("192.0.2.193", ptrdname="www.vinyldns"),
            get_change_PTR_json("192.0.2.44"),
            get_change_PTR_json("fd69:27cc:fe91::60", ptrdname="www.vinyldns"),
            get_change_TXT_json("txt.ok."),
            get_change_TXT_json("ok."),
            get_change_TXT_json("txt-unique-characters.ok.", text='a\\\\`=` =\\"Cat\\"\nattr=val'),
            get_change_TXT_json("txt.2.0.192.in-addr.arpa."),
            get_change_MX_json("mx.ok.", preference=0),
            get_change_MX_json("mx.ok.", preference=65535),
            get_change_MX_json("ok.", preference=1000, exchange="bar.foo.")
        ]
    }

    to_delete = []
    try:
        result = client.create_batch_change(batch_change_input, status=202)
        completed_batch = client.wait_until_batch_change_completed(result)
        record_set_list = [(change['zoneId'], change['recordSetId']) for change in completed_batch['changes']]
        to_delete = set(record_set_list) # set here because multiple items in the batch combine to one RS

        ## validate initial response
        assert_that(result['comments'], is_("this is optional"))
        assert_that(result['userName'], is_("ok"))
        assert_that(result['userId'], is_("ok"))
        assert_that(result['id'], is_not(none()))
        assert_that(completed_batch['status'], is_("Complete"))

        assert_change_success_response_values(result['changes'], zone=parent_zone, index=0, record_name="parent.com.",
                                              input_name="parent.com.", record_data="4.5.6.7")
        assert_change_success_response_values(result['changes'], zone=parent_zone, index=1, record_name="parent.com.",
                                              input_name="parent.com.", record_data="4.5.6.7")
        assert_change_success_response_values(result['changes'], zone=ok_zone, index=2, record_name="ok.",
                                              input_name="ok.", record_data="fd69:27cc:fe91::60", record_type="AAAA")
        assert_change_success_response_values(result['changes'], zone=parent_zone, index=3, record_name="relative",
                                              input_name="relative.parent.com.", record_data="1.1.1.1")
        assert_change_success_response_values(result['changes'], zone=parent_zone, index=4, record_name="relative",
                                              input_name="relative.parent.com.", record_data="2.2.2.2"),
        assert_change_success_response_values(result['changes'], zone=parent_zone, index=5, record_name="cname",
                                              input_name="cname.parent.com.", record_data="nice.parent.com.", record_type="CNAME")
        assert_change_success_response_values(result['changes'], zone=parent_zone, index=6, record_name="2cname",
                                              input_name="2cname.parent.com.", record_data="nice.parent.com.", record_type="CNAME")
        assert_change_success_response_values(result['changes'], zone=classless_base_zone, index=7, record_name="4",
                                              input_name="4.2.0.192.in-addr.arpa.", record_data="4.4/30.2.0.192.in-addr.arpa.", record_type="CNAME")
        assert_change_success_response_values(result['changes'], zone=classless_delegation_zone, index=8, record_name="193",
                                              input_name="192.0.2.193", record_data="www.vinyldns.", record_type="PTR")
        assert_change_success_response_values(result['changes'], zone=classless_base_zone, index=9, record_name="44",
                                              input_name="192.0.2.44", record_data="test.com.", record_type="PTR")
        assert_change_success_response_values(result['changes'], zone=ip6_reverse_zone, index=10, record_name="0.6.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0",
                                              input_name="fd69:27cc:fe91::60", record_data="www.vinyldns.", record_type="PTR")
        assert_change_success_response_values(result['changes'], zone=ok_zone, index=11, record_name="txt",
                                              input_name="txt.ok.", record_data="test", record_type="TXT")
        assert_change_success_response_values(result['changes'], zone=ok_zone, index=12, record_name="ok.",
                                              input_name="ok.", record_data="test", record_type="TXT")
        assert_change_success_response_values(result['changes'], zone=ok_zone, index=13, record_name="txt-unique-characters",
                                              input_name="txt-unique-characters.ok.", record_data='a\\\\`=` =\\"Cat\\"\nattr=val', record_type="TXT")
        assert_change_success_response_values(result['changes'], zone=classless_base_zone, index=14, record_name="txt",
                                              input_name="txt.2.0.192.in-addr.arpa.", record_data="test", record_type="TXT")
        assert_change_success_response_values(result['changes'], zone=ok_zone, index=15, record_name="mx",
                                              input_name="mx.ok.", record_data={'preference': 0, 'exchange': 'foo.bar.'}, record_type="MX")
        assert_change_success_response_values(result['changes'], zone=ok_zone, index=16, record_name="mx",
                                              input_name="mx.ok.", record_data={'preference': 65535, 'exchange': 'foo.bar.'}, record_type="MX")
        assert_change_success_response_values(result['changes'], zone=ok_zone, index=17, record_name="ok.",
                                              input_name="ok.", record_data={'preference': 1000, 'exchange': 'bar.foo.'}, record_type="MX")

        completed_status = [change['status'] == 'Complete' for change in completed_batch['changes']]
        assert_that(all(completed_status), is_(True))

        ## get all the recordsets created by this batch, validate
        rs1 = client.get_recordset(record_set_list[0][0], record_set_list[0][1])['recordSet']
        expected1 = {'name': 'parent.com.',
                    'zoneId': parent_zone['id'],
                    'type': 'A',
                    'ttl': 200,
                    'records': [{'address': '4.5.6.7'}]}
        verify_recordset(rs1, expected1)

        rs2 = client.get_recordset(record_set_list[1][0], record_set_list[1][1])['recordSet']
        assert_that(rs2, is_(rs1)) # duplicate entry, should get same thing

        rs3 = client.get_recordset(record_set_list[2][0], record_set_list[2][1])['recordSet']
        expected3 = {'name': 'ok.',
                     'zoneId': ok_zone['id'],
                     'type': 'AAAA',
                     'ttl': 200,
                     'records': [{'address': 'fd69:27cc:fe91::60'}]}
        verify_recordset(rs3, expected3)

        rs4 = client.get_recordset(record_set_list[3][0], record_set_list[3][1])['recordSet']
        expected4 = {'name': 'relative',
                     'zoneId': parent_zone['id'],
                     'type': 'A',
                     'ttl': 200,
                     'records': [{'address': '1.1.1.1'}, {'address': '2.2.2.2'}]}
        verify_recordset(rs4, expected4)

        rs5 = client.get_recordset(record_set_list[5][0], record_set_list[5][1])['recordSet']
        expected5 = {'name': 'cname',
                     'zoneId': parent_zone['id'],
                     'type': 'CNAME',
                     'ttl': 200,
                     'records': [{'cname': 'nice.parent.com.'}]}
        verify_recordset(rs5, expected5)

        rs6 = client.get_recordset(record_set_list[6][0], record_set_list[6][1])['recordSet']
        expected6 = {'name': '2cname',
                     'zoneId': parent_zone['id'],
                     'type': 'CNAME',
                     'ttl': 200,
                     'records': [{'cname': 'nice.parent.com.'}]}
        verify_recordset(rs6, expected6)

        rs7 = client.get_recordset(record_set_list[7][0], record_set_list[7][1])['recordSet']
        expected7 = {'name': '4',
                     'zoneId': classless_base_zone['id'],
                     'type': 'CNAME',
                     'ttl': 200,
                     'records': [{'cname': '4.4/30.2.0.192.in-addr.arpa.'}]}
        verify_recordset(rs7, expected7)

        rs8 = client.get_recordset(record_set_list[8][0], record_set_list[8][1])['recordSet']
        expected8 = {'name': '193',
                     'zoneId': classless_delegation_zone['id'],
                     'type': 'PTR',
                     'ttl': 200,
                     'records': [{'ptrdname': 'www.vinyldns.'}]}
        verify_recordset(rs8, expected8)

        rs9 = client.get_recordset(record_set_list[9][0], record_set_list[9][1])['recordSet']
        expected9 = {'name': '44',
                     'zoneId': classless_base_zone['id'],
                     'type': 'PTR',
                     'ttl': 200,
                     'records': [{'ptrdname': 'test.com.'}]}
        verify_recordset(rs9, expected9)

        rs10 = client.get_recordset(record_set_list[10][0], record_set_list[10][1])['recordSet']
        expected10 = {'name': '0.6.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0',
                     'zoneId': ip6_reverse_zone['id'],
                     'type': 'PTR',
                     'ttl': 200,
                     'records': [{'ptrdname': 'www.vinyldns.'}]}
        verify_recordset(rs10, expected10)

        rs11 = client.get_recordset(record_set_list[11][0], record_set_list[11][1])['recordSet']
        expected11 = {'name': 'txt',
                      'zoneId': ok_zone['id'],
                      'type': 'TXT',
                      'ttl': 200,
                      'records': [{'text': 'test'}]}
        verify_recordset(rs11, expected11)

        rs12 = client.get_recordset(record_set_list[12][0], record_set_list[12][1])['recordSet']
        expected12 = {'name': 'ok.',
                      'zoneId': ok_zone['id'],
                      'type': 'TXT',
                      'ttl': 200,
                      'records': [{'text': 'test'}]}
        verify_recordset(rs12, expected12)

        rs13 = client.get_recordset(record_set_list[13][0], record_set_list[13][1])['recordSet']
        expected13 = {'name': 'txt-unique-characters',
                      'zoneId': ok_zone['id'],
                      'type': 'TXT',
                      'ttl': 200,
                      'records': [{'text': 'a\\\\`=` =\\"Cat\\"\nattr=val'}]}
        verify_recordset(rs13, expected13)

        rs14 = client.get_recordset(record_set_list[14][0], record_set_list[14][1])['recordSet']
        expected14 = {'name': 'txt',
                      'zoneId': classless_base_zone['id'],
                      'type': 'TXT',
                      'ttl': 200,
                      'records': [{'text': 'test'}]}
        verify_recordset(rs14, expected14)

        rs15 = client.get_recordset(record_set_list[15][0], record_set_list[15][1])['recordSet']
        expected15 = {'name': 'mx',
                      'zoneId': ok_zone['id'],
                      'type': 'MX',
                      'ttl': 200,
                      'records': [{'preference': 0, 'exchange': 'foo.bar.'}, {'preference': 65535, 'exchange': 'foo.bar.'}]}
        verify_recordset(rs15, expected15)

        rs16 = client.get_recordset(record_set_list[17][0], record_set_list[17][1])['recordSet']
        expected16 = {'name': 'ok.',
                      'zoneId': ok_zone['id'],
                      'type': 'MX',
                      'ttl': 200,
                      'records': [{'preference': 1000, 'exchange': 'bar.foo.'}]}
        verify_recordset(rs16, expected16)

    finally:
        clear_zoneid_rsid_tuple_list(to_delete, client)


def test_create_batch_change_with_updates_deletes_success(shared_zone_test_context):
    """
    Test successfully creating a batch change with updates and deletes
    """
    ok_client = shared_zone_test_context.ok_vinyldns_client
    dummy_client = shared_zone_test_context.dummy_vinyldns_client
    dummy_zone = shared_zone_test_context.dummy_zone
    ok_zone = shared_zone_test_context.ok_zone
    classless_zone_delegation_zone = shared_zone_test_context.classless_zone_delegation_zone

    ok_zone_acl = generate_acl_rule('Delete', groupId=shared_zone_test_context.dummy_group['id'], recordMask='.*', recordTypes=['CNAME'])
    classless_zone_delegation_zone_acl = generate_acl_rule('Write', groupId=shared_zone_test_context.dummy_group['id'], recordTypes=['PTR'])

    rs_delete_dummy = get_recordset_json(dummy_zone, "delete", "AAAA", [{"address": "1:2:3:4:5:6:7:8"}])
    rs_update_dummy = get_recordset_json(dummy_zone, "update", "A", [{"address": "1.2.3.4"}])
    rs_delete_ok = get_recordset_json(ok_zone, "delete", "CNAME", [{"cname": "delete.cname."}])
    rs_update_classless = get_recordset_json(classless_zone_delegation_zone, "193", "PTR", [{"ptrdname": "will.change."}])
    txt_delete_dummy = get_recordset_json(dummy_zone, "delete-txt", "TXT", [{"text": "test"}])
    mx_delete_dummy = get_recordset_json(dummy_zone, "delete-mx", "MX", [{"preference": 1, "exchange": "foo.bar."}])
    mx_update_dummy = get_recordset_json(dummy_zone, "update-mx", "MX", [{"preference": 1, "exchange": "foo.bar."}])

    batch_change_input = {
        "comments": "this is optional",
        "changes": [
            get_change_A_AAAA_json("delete.dummy.", record_type="AAAA", change_type="DeleteRecordSet"),
            get_change_A_AAAA_json("update.dummy.", ttl=300, address="1.2.3.4"),
            get_change_A_AAAA_json("Update.dummy.", change_type="DeleteRecordSet"),
            get_change_CNAME_json("delete.ok.", change_type="DeleteRecordSet"),
            get_change_PTR_json("192.0.2.193", ttl=300, ptrdname="has.changed."),
            get_change_PTR_json("192.0.2.193", change_type="DeleteRecordSet"),
            get_change_TXT_json("delete-txt.dummy.", change_type="DeleteRecordSet"),
            get_change_MX_json("delete-mx.dummy.", change_type="DeleteRecordSet"),
            get_change_MX_json("update-mx.dummy.", change_type="DeleteRecordSet"),
            get_change_MX_json("update-mx.dummy.", preference=1000)
        ]
    }

    to_create = [rs_delete_dummy, rs_update_dummy, rs_delete_ok, rs_update_classless, txt_delete_dummy, mx_delete_dummy, mx_update_dummy]
    to_delete = []

    try:
        for rs in to_create:
            if rs['zoneId'] == dummy_zone['id']:
                create_client = dummy_client
            else:
                create_client = ok_client

            create_rs = create_client.create_recordset(rs, status=202)
            create_client.wait_until_recordset_change_status(create_rs, 'Complete')

        # Configure ACL rules
        add_ok_acl_rules(shared_zone_test_context, [ok_zone_acl])
        add_classless_acl_rules(shared_zone_test_context, [classless_zone_delegation_zone_acl])

        result = dummy_client.create_batch_change(batch_change_input, status=202)
        completed_batch = dummy_client.wait_until_batch_change_completed(result)

        record_set_list = [(change['zoneId'], change['recordSetId']) for change in completed_batch['changes']]

        to_delete = set(record_set_list) # set here because multiple items in the batch combine to one RS

        ## validate initial response
        assert_that(result['comments'], is_("this is optional"))
        assert_that(result['userName'], is_("dummy"))
        assert_that(result['userId'], is_("dummy"))
        assert_that(result['id'], is_not(none()))
        assert_that(completed_batch['status'], is_("Complete"))

        assert_change_success_response_values(result['changes'], zone=dummy_zone, index=0, record_name="delete",
                                              input_name="delete.dummy.", record_data=None, record_type="AAAA", change_type="DeleteRecordSet")
        assert_change_success_response_values(result['changes'], zone=dummy_zone, index=1, record_name="update", ttl=300,
                                              input_name="update.dummy.", record_data="1.2.3.4")
        assert_change_success_response_values(result['changes'], zone=dummy_zone, index=2, record_name="Update",
                                              input_name="Update.dummy.", record_data=None, change_type="DeleteRecordSet")
        assert_change_success_response_values(result['changes'], zone=ok_zone, index=3, record_name="delete",
                                              input_name="delete.ok.", record_data=None, record_type="CNAME", change_type="DeleteRecordSet")
        assert_change_success_response_values(result['changes'], zone=classless_zone_delegation_zone, index=4, record_name="193", ttl=300,
                                              input_name="192.0.2.193", record_data="has.changed.", record_type="PTR")
        assert_change_success_response_values(result['changes'], zone=classless_zone_delegation_zone, index=5, record_name="193",
                                              input_name="192.0.2.193", record_data=None, record_type="PTR", change_type="DeleteRecordSet")
        assert_change_success_response_values(result['changes'], zone=dummy_zone, index=6, record_name="delete-txt",
                                              input_name="delete-txt.dummy.", record_data=None, record_type="TXT", change_type="DeleteRecordSet")
        assert_change_success_response_values(result['changes'], zone=dummy_zone, index=7, record_name="delete-mx",
                                              input_name="delete-mx.dummy.", record_data=None, record_type="MX", change_type="DeleteRecordSet")
        assert_change_success_response_values(result['changes'], zone=dummy_zone, index=8, record_name="update-mx",
                                              input_name="update-mx.dummy.", record_data=None, record_type="MX", change_type="DeleteRecordSet")
        assert_change_success_response_values(result['changes'], zone=dummy_zone, index=9, record_name="update-mx",
                                              input_name="update-mx.dummy.", record_data={'preference': 1000, 'exchange': 'foo.bar.'}, record_type="MX")

        rs1 = dummy_client.get_recordset(record_set_list[0][0], record_set_list[0][1], status=404)
        assert_that(rs1, is_("RecordSet with id " + record_set_list[0][1] + " does not exist in zone dummy."))

        rs2 = dummy_client.get_recordset(record_set_list[1][0], record_set_list[1][1])['recordSet']
        expected2 = {'name': 'update',
                      'zoneId': dummy_zone['id'],
                      'type': 'A',
                      'ttl': 300,
                      'records': [{'address': '1.2.3.4'}]}
        verify_recordset(rs2, expected2)

        # since this is an update, record_set_list[1] and record_set_list[2] are the same record
        rs3 = dummy_client.get_recordset(record_set_list[2][0], record_set_list[2][1])['recordSet']
        verify_recordset(rs3, expected2)

        rs4 = dummy_client.get_recordset(record_set_list[3][0], record_set_list[3][1], status=404)
        assert_that(rs4, is_("RecordSet with id " + record_set_list[3][1] + " does not exist in zone ok."))

        rs5 = dummy_client.get_recordset(record_set_list[4][0], record_set_list[4][1])['recordSet']
        expected5 = {'name': '193',
                     'zoneId': classless_zone_delegation_zone['id'],
                     'type': 'PTR',
                     'ttl': 300,
                     'records': [{'ptrdname': 'has.changed.'}]}
        verify_recordset(rs5, expected5)

        # since this is an update, record_set_list[5] and record_set_list[4] are the same record
        rs6 = dummy_client.get_recordset(record_set_list[5][0], record_set_list[5][1])['recordSet']
        verify_recordset(rs6, expected5)

        rs7 = dummy_client.get_recordset(record_set_list[6][0], record_set_list[6][1], status=404)
        assert_that(rs7, is_("RecordSet with id " + record_set_list[6][1] + " does not exist in zone dummy."))

        rs8 = dummy_client.get_recordset(record_set_list[7][0], record_set_list[7][1], status=404)
        assert_that(rs8, is_("RecordSet with id " + record_set_list[7][1] + " does not exist in zone dummy."))

        rs9 = dummy_client.get_recordset(record_set_list[8][0], record_set_list[8][1])['recordSet']
        expected9 = {'name': 'update-mx',
                     'zoneId': dummy_zone['id'],
                     'type': 'MX',
                     'ttl': 200,
                     'records': [{'preference': 1000, 'exchange': 'foo.bar.'}]}
        verify_recordset(rs9, expected9)

    finally:
        # Clean up updates
        dummy_deletes = [rs for rs in to_delete if rs[0] == dummy_zone['id']]
        ok_deletes = [rs for rs in to_delete if rs[0] != dummy_zone['id']]
        clear_zoneid_rsid_tuple_list(dummy_deletes, dummy_client)
        clear_zoneid_rsid_tuple_list(ok_deletes, ok_client)

        # Clean up ACL rules
        clear_ok_acl_rules(shared_zone_test_context)
        clear_classless_acl_rules(shared_zone_test_context)


def test_create_batch_change_without_comments_succeeds(shared_zone_test_context):
    """
    Test successfully creating a batch change without comments
    Test successfully creating a batch using inputName without a trailing dot, and that the
    returned inputName is dotted
    """
    client = shared_zone_test_context.ok_vinyldns_client
    parent_zone = shared_zone_test_context.parent_zone
    batch_change_input = {
        "changes": [
            get_change_A_AAAA_json("parent.com", address="4.5.6.7"),
        ]
    }
    to_delete = []

    try:
        result = client.create_batch_change(batch_change_input, status=202)
        completed_batch = client.wait_until_batch_change_completed(result)
        to_delete = [(change['zoneId'], change['recordSetId']) for change in completed_batch['changes']]

        assert_change_success_response_values(result['changes'], zone=parent_zone, index=0, record_name="parent.com.",
                                              input_name="parent.com.", record_data="4.5.6.7")
    finally:
        clear_zoneid_rsid_tuple_list(to_delete, client)

def test_create_batch_change_with_owner_group_id_succeeds(shared_zone_test_context):
    """
    Test successfully creating a batch change with owner group ID specified
    """
    client = shared_zone_test_context.ok_vinyldns_client
    ok_zone = shared_zone_test_context.ok_zone
    batch_change_input = {
        "changes": [
            get_change_A_AAAA_json("owner-group-id.ok.", address="4.3.2.1")
        ],
        "ownerGroupId": shared_zone_test_context.ok_group['id']
    }
    to_delete = []

    try:
        result = client.create_batch_change(batch_change_input, status=202)
        completed_batch = client.wait_until_batch_change_completed(result)
        to_delete = [(change['zoneId'], change['recordSetId']) for change in completed_batch['changes']]

        assert_change_success_response_values(result['changes'], zone=ok_zone, index=0, record_name="owner-group-id",
                                              input_name="owner-group-id.ok.", record_data="4.3.2.1")
        assert_that(completed_batch['ownerGroupId'], is_(shared_zone_test_context.ok_group['id']))

    finally:
        clear_zoneid_rsid_tuple_list(to_delete, client)

def test_create_batch_change_without_owner_group_id_succeeds(shared_zone_test_context):
    """
    Test successfully creating a batch change without owner group ID specified
    """
    client = shared_zone_test_context.ok_vinyldns_client
    ok_zone = shared_zone_test_context.ok_zone
    batch_change_input = {
        "changes": [
            get_change_A_AAAA_json("no-owner-group-id.ok.", address="4.3.2.1")
        ]
    }
    to_delete = []

    try:
        result = client.create_batch_change(batch_change_input, status=202)
        completed_batch = client.wait_until_batch_change_completed(result)
        to_delete = [(change['zoneId'], change['recordSetId']) for change in completed_batch['changes']]

        assert_change_success_response_values(result['changes'], zone=ok_zone, index=0, record_name="no-owner-group-id",
                                              input_name="no-owner-group-id.ok.", record_data="4.3.2.1")
        assert_that(completed_batch, is_not(has_key('ownerGroupId')))

    finally:
        clear_zoneid_rsid_tuple_list(to_delete, client)

def test_create_batch_change_partial_failure(shared_zone_test_context):
    """
    Test batch change status with partial failures
    """
    client = shared_zone_test_context.ok_vinyldns_client

    batch_change_input = {
        "comments": "this is optional",
        "changes": [
            get_change_A_AAAA_json("will-succeed.ok.", address="4.5.6.7"),
            get_change_A_AAAA_json("direct-to-backend.ok.", address="4.5.6.7") # this record will fail in processing
        ]
    }

    to_delete = []

    try:
        dns_add(shared_zone_test_context.ok_zone, "direct-to-backend", 200, "A", "1.2.3.4")
        result = client.create_batch_change(batch_change_input, status=202)
        completed_batch = client.wait_until_batch_change_completed(result)
        record_set_list = [(change['zoneId'], change['recordSetId']) for change in completed_batch['changes'] if change['status'] == "Complete"]
        to_delete = set(record_set_list) # set here because multiple items in the batch combine to one RS

        assert_that(completed_batch['status'], is_("PartialFailure"))

    finally:
        clear_zoneid_rsid_tuple_list(to_delete, client)
        dns_delete(shared_zone_test_context.ok_zone, "direct-to-backend", "A")


def test_create_batch_change_failed(shared_zone_test_context):
    """
    Test batch change status with all failures
    """
    client = shared_zone_test_context.ok_vinyldns_client

    batch_change_input = {
        "comments": "this is optional",
        "changes": [
            get_change_A_AAAA_json("backend-foo.ok.", address="4.5.6.7"),
            get_change_A_AAAA_json("backend-already-exists.ok.", address="4.5.6.7")
        ]
    }

    try:
        # both of these records already exist in the backend, but are not synced in zone
        dns_add(shared_zone_test_context.ok_zone, "backend-foo", 200, "A", "1.2.3.4")
        dns_add(shared_zone_test_context.ok_zone, "backend-already-exists", 200, "A", "1.2.3.4")
        result = client.create_batch_change(batch_change_input, status=202)
        completed_batch = client.wait_until_batch_change_completed(result)

        assert_that(completed_batch['status'], is_("Failed"))

    finally:
        dns_delete(shared_zone_test_context.ok_zone, "backend-foo", "A")
        dns_delete(shared_zone_test_context.ok_zone, "backend-already-exists", "A")


def test_empty_batch_fails(shared_zone_test_context):
    """
    Test creating batch without any changes fails with
    """

    batch_change_input = {
        "comments": "this should fail processing",
        "changes": []
    }

    error = shared_zone_test_context.ok_vinyldns_client.create_batch_change(batch_change_input, status=422)
    assert_that(error, is_("Batch change contained no changes. Batch change must have at least one change, up to a maximum of 20 changes."))


def test_create_batch_exceeding_change_limit_fails(shared_zone_test_context):
    """
    Test that creating a batch exceeding the change limit fails with ChangeLimitExceeded
    """
    client = shared_zone_test_context.ok_vinyldns_client
    batch_change_input = {
        "changes": []
    }
    for x in range(100):
        batch_change_input['changes'].append(get_change_A_AAAA_json("ok.", address=("1.2.3." + str(x))))

    errors = client.create_batch_change(batch_change_input, status=413)

    assert_that(errors, is_("Cannot request more than 20 changes in a single batch change request"))


def test_create_batch_change_without_changes_fails(shared_zone_test_context):
    """
    Test creating a batch change with missing changes fails
    """
    client = shared_zone_test_context.ok_vinyldns_client
    batch_change_input = {
        "comments": "this is optional"
    }
    errors = client.create_batch_change(batch_change_input, status=400)

    assert_error(errors, error_messages=["Missing BatchChangeInput.changes"])


def test_create_batch_change_with_missing_change_type_fails(shared_zone_test_context):
    """
    Test creating a batch change with missing change type fails
    """
    client = shared_zone_test_context.ok_vinyldns_client
    batch_change_input = {
        "comments": "this is optional",
        "changes": [
            {
                "inputName": "thing.thing.com.",
                "type": "A",
                "ttl": 200,
                "record": {
                    "address": "4.5.6.7"
                }
            }
        ]
    }
    errors = client.create_batch_change(batch_change_input, status=400)

    assert_error(errors, error_messages=["Missing BatchChangeInput.changes.changeType"])


def test_create_batch_change_with_invalid_change_type_fails(shared_zone_test_context):
    """
    Test creating a batch change with invalid change type fails
    """
    client = shared_zone_test_context.ok_vinyldns_client
    batch_change_input = {
        "comments": "this is optional",
        "changes": [
            {
                "changeType": "InvalidChangeType",
                "data": {
                    "inputName": "thing.thing.com.",
                    "type": "A",
                    "ttl": 200,
                    "record": {
                        "address": "4.5.6.7"
                    }
                }
            }
        ]
    }
    errors = client.create_batch_change(batch_change_input, status=400)

    assert_error(errors, error_messages=["Invalid ChangeInputType"])


def test_create_batch_change_with_missing_input_name_fails(shared_zone_test_context):
    """
    Test creating a batch change without an inputName fails
    """
    client = shared_zone_test_context.ok_vinyldns_client
    batch_change_input = {
        "comments": "this is optional",
        "changes": [
            {
                "changeType": "Add",
                "type": "A",
                "ttl": 200,
                "record": {
                    "address": "4.5.6.7"
                }
            }
        ]
    }
    errors = client.create_batch_change(batch_change_input, status=400)

    assert_error(errors, error_messages=["Missing BatchChangeInput.changes.inputName"])


def test_create_batch_change_with_unsupported_record_type_fails(shared_zone_test_context):
    """
    Test creating a batch change with unsupported record type fails
    """
    client = shared_zone_test_context.ok_vinyldns_client
    batch_change_input = {
        "comments": "this is optional",
        "changes": [
            {
                "changeType": "Add",
                "inputName": "thing.thing.com.",
                "type": "UNKNOWN",
                "ttl": 200,
                "record": {
                    "address": "4.5.6.7"
                }
            }
        ]
    }

    errors = client.create_batch_change(batch_change_input, status=400)

    assert_error(errors, error_messages=["Unsupported type UNKNOWN, valid types include: A, AAAA, CNAME, PTR, TXT, and MX"])


def test_create_batch_change_with_high_value_domain_fails(shared_zone_test_context):
    """
    Test creating a batch change with a high value domain as an inputName fails
    """

    client = shared_zone_test_context.ok_vinyldns_client
    batch_change_input = {

        "comments": "this is optional",
        "changes": [
            get_change_A_AAAA_json("high-value-domain-add.ok."),
            get_change_A_AAAA_json("high-value-domain-update.ok.", change_type="DeleteRecordSet"),
            get_change_A_AAAA_json("high-value-domain-update.ok."),
            get_change_A_AAAA_json("high-value-domain-delete.ok.", change_type="DeleteRecordSet"),
            get_change_PTR_json("192.0.2.252"),
            get_change_PTR_json("192.0.2.253", change_type="DeleteRecordSet"), # 253 exists already
            get_change_PTR_json("192.0.2.253"),
            get_change_PTR_json("192.0.2.253", change_type="DeleteRecordSet"),
            get_change_PTR_json("fd69:27cc:fe91:0:0:0:0:ffff"),
            get_change_PTR_json("fd69:27cc:fe91:0:0:0:ffff:0", change_type="DeleteRecordSet"), # ffff:0 exists already
            get_change_PTR_json("fd69:27cc:fe91:0:0:0:ffff:0"),
            get_change_PTR_json("fd69:27cc:fe91:0:0:0:ffff:0", change_type="DeleteRecordSet"),

            get_change_A_AAAA_json("i-can-be-touched.ok.", address="1.1.1.1")
        ]
    }

    response = client.create_batch_change(batch_change_input, status=400)

    assert_error(response[0], error_messages=['Record name "high-value-domain-add.ok." is configured as a High Value Domain, so it cannot be modified.'])
    assert_error(response[1], error_messages=['Record name "high-value-domain-update.ok." is configured as a High Value Domain, so it cannot be modified.'])
    assert_error(response[2], error_messages=['Record name "high-value-domain-update.ok." is configured as a High Value Domain, so it cannot be modified.'])
    assert_error(response[3], error_messages=['Record name "high-value-domain-delete.ok." is configured as a High Value Domain, so it cannot be modified.'])
    assert_error(response[4], error_messages=['Record name "192.0.2.252" is configured as a High Value Domain, so it cannot be modified.'])
    assert_error(response[5], error_messages=['Record name "192.0.2.253" is configured as a High Value Domain, so it cannot be modified.'])
    assert_error(response[6], error_messages=['Record name "192.0.2.253" is configured as a High Value Domain, so it cannot be modified.'])
    assert_error(response[7], error_messages=['Record name "192.0.2.253" is configured as a High Value Domain, so it cannot be modified.'])
    assert_error(response[8], error_messages=['Record name "fd69:27cc:fe91:0:0:0:0:ffff" is configured as a High Value Domain, so it cannot be modified.'])
    assert_error(response[9], error_messages=['Record name "fd69:27cc:fe91:0:0:0:ffff:0" is configured as a High Value Domain, so it cannot be modified.'])
    assert_error(response[10], error_messages=['Record name "fd69:27cc:fe91:0:0:0:ffff:0" is configured as a High Value Domain, so it cannot be modified.'])
    assert_error(response[11], error_messages=['Record name "fd69:27cc:fe91:0:0:0:ffff:0" is configured as a High Value Domain, so it cannot be modified.'])

    assert_that(response[12], is_not(has_key("errors")))


def test_create_batch_change_with_invalid_record_type_fails(shared_zone_test_context):
    """
    Test creating a batch change with invalid record type fails
    """
    client = shared_zone_test_context.ok_vinyldns_client
    batch_change_input = {
        "comments": "this is optional",
        "changes": [
            get_change_A_AAAA_json("thing.thing.com.", "B", address="4.5.6.7")
        ]
    }
    errors = client.create_batch_change(batch_change_input, status=400)

    assert_error(errors, error_messages=["Invalid RecordType"])


def test_create_batch_change_with_missing_ttl_fails(shared_zone_test_context):
    """
    Test creating a batch change without a ttl fails
    """
    client = shared_zone_test_context.ok_vinyldns_client
    batch_change_input = {
        "comments": "this is optional",
        "changes": [
            {
                "changeType": "Add",
                "inputName": "thing.thing.com.",
                "type": "A",
                "record": {
                    "address": "4.5.6.7"
                }
            }
        ]
    }
    errors = client.create_batch_change(batch_change_input, status=400)

    assert_error(errors, error_messages=["Missing BatchChangeInput.changes.ttl"])


def test_create_batch_change_with_missing_record_fails(shared_zone_test_context):
    """
    Test creating a batch change without a record fails
    """
    client = shared_zone_test_context.ok_vinyldns_client
    batch_change_input = {
        "comments": "this is optional",
        "changes": [
            {
                "changeType": "Add",
                "inputName": "thing.thing.com.",
                "type": "A",
                "ttl": 200
        }
        ]
    }
    errors = client.create_batch_change(batch_change_input, status=400)

    assert_error(errors, error_messages=["Missing BatchChangeInput.changes.record.address"])


def test_create_batch_change_with_empty_record_fails(shared_zone_test_context):
    """
    Test creating a batch change with empty record fails
    """
    client = shared_zone_test_context.ok_vinyldns_client
    batch_change_input = {
        "comments": "this is optional",
        "changes": [
            {
                "changeType": "Add",
                "inputName": "thing.thing.com.",
                "type": "A",
                "ttl": 200,
                "record": {}
            }
        ]
    }
    errors = client.create_batch_change(batch_change_input, status=400)

    assert_error(errors, error_messages=["Missing A.address"])


def test_create_batch_change_with_bad_A_record_data_fails(shared_zone_test_context):
    """
    Test creating a batch change with malformed A record address fails
    """
    client = shared_zone_test_context.ok_vinyldns_client
    bad_A_data_request = {
        "comments": "this is optional",
        "changes": [
            get_change_A_AAAA_json("thing.thing.com.", address="bad address")
        ]
    }
    errors = client.create_batch_change(bad_A_data_request, status=400)

    assert_error(errors, error_messages=["A must be a valid IPv4 Address"])


def test_create_batch_change_with_bad_AAAA_record_data_fails(shared_zone_test_context):
    """
    Test creating a batch change with malformed AAAA record address fails
    """
    client = shared_zone_test_context.ok_vinyldns_client
    bad_AAAA_data_request = {
        "comments": "this is optional",
        "changes": [
            get_change_A_AAAA_json("thing.thing.com.", record_type="AAAA", address="bad address")
        ]
    }
    errors = client.create_batch_change(bad_AAAA_data_request, status=400)

    assert_error(errors, error_messages=["AAAA must be a valid IPv6 Address"])


def test_create_batch_change_with_incorrect_CNAME_record_attribute_fails(shared_zone_test_context):
    """
    Test creating a batch change with incorrect CNAME record attribute fails
    """
    client = shared_zone_test_context.ok_vinyldns_client
    bad_CNAME_data_request = {
        "comments": "this is optional",
        "changes": [
            {
                "changeType": "Add",
                "inputName": "bizz.bazz.",
                "type": "CNAME",
                "ttl": 200,
                "record": {
                    "address": "buzz."
                }
            }
        ]
    }
    errors = client.create_batch_change(bad_CNAME_data_request, status=400)['errors']

    assert_that(errors, contains("Missing CNAME.cname"))


def test_create_batch_change_with_incorrect_PTR_record_attribute_fails(shared_zone_test_context):
    """
    Test creating a batch change with incorrect PTR record attribute fails
    """
    client = shared_zone_test_context.ok_vinyldns_client
    bad_PTR_data_request = {
        "comments": "this is optional",
        "changes": [
            {
                "changeType": "Add",
                "inputName": "4.5.6.7",
                "type": "PTR",
                "ttl": 200,
                "record": {
                    "address": "buzz."
                }
            }
        ]
    }
    errors = client.create_batch_change(bad_PTR_data_request, status=400)['errors']

    assert_that(errors, contains("Missing PTR.ptrdname"))


def test_create_batch_change_with_bad_CNAME_record_attribute_fails(shared_zone_test_context):
    """
    Test creating a batch change with malformed CNAME record fails
    """
    client = shared_zone_test_context.ok_vinyldns_client
    bad_CNAME_data_request = {
        "comments": "this is optional",
        "changes": [
            get_change_CNAME_json(input_name="bizz.baz.", cname="s." + "s" * 256)
        ]
    }
    errors = client.create_batch_change(bad_CNAME_data_request, status=400)

    assert_error(errors, error_messages=["CNAME domain name must not exceed 255 characters"])


def test_create_batch_change_with_bad_PTR_record_attribute_fails(shared_zone_test_context):
    """
    Test creating a batch change with malformed PTR record fails
    """
    client = shared_zone_test_context.ok_vinyldns_client
    bad_PTR_data_request = {
        "comments": "this is optional",
        "changes": [
            get_change_PTR_json("4.5.6.7", ptrdname="s" * 256)
        ]
    }
    errors = client.create_batch_change(bad_PTR_data_request, status=400)

    assert_error(errors, error_messages=["PTR must be less than 255 characters"])


def test_create_batch_change_with_missing_input_name_for_delete_fails(shared_zone_test_context):
    """
    Test creating a batch change without an inputName for DeleteRecordSet fails
    """
    client = shared_zone_test_context.ok_vinyldns_client
    batch_change_input = {
        "comments": "this is optional",
        "changes": [
            {
                "changeType": "DeleteRecordSet",
                "type": "A"
            }
        ]
    }
    errors = client.create_batch_change(batch_change_input, status=400)

    assert_error(errors, error_messages=["Missing BatchChangeInput.changes.inputName"])


def test_create_batch_change_with_missing_record_type_for_delete_fails(shared_zone_test_context):
    """
    Test creating a batch change without record type for DeleteRecordSet fails
    """
    client = shared_zone_test_context.ok_vinyldns_client
    batch_change_input = {
        "comments": "this is optional",
        "changes": [
            {
                "changeType": "DeleteRecordSet",
                "inputName": "thing.thing.com."
            }
        ]
    }
    errors = client.create_batch_change(batch_change_input, status=400)

    assert_error(errors, error_messages=["Missing BatchChangeInput.changes.type"])


def test_mx_recordtype_cannot_have_invalid_preference(shared_zone_test_context):
    """
    Test batch fails with bad mx preference
    """
    ok_client = shared_zone_test_context.ok_vinyldns_client

    batch_change_input_low = {
        "comments": "this is optional",
        "changes": [
            get_change_MX_json("too-small.ok.", preference=-1)
        ]
    }

    batch_change_input_high = {
        "comments": "this is optional",
        "changes": [
            get_change_MX_json("too-big.ok.", preference=65536)
        ]
    }

    error_low = ok_client.create_batch_change(batch_change_input_low, status=400)
    error_high = ok_client.create_batch_change(batch_change_input_high, status=400)

    assert_error(error_low, error_messages=["MX.preference must be a 16 bit integer"])
    assert_error(error_high, error_messages=["MX.preference must be a 16 bit integer"])


def test_create_batch_change_with_invalid_duplicate_record_names_fails(shared_zone_test_context):
    """
    Test creating a batch change that contains a CNAME record and another record with the same name fails
    """
    client = shared_zone_test_context.ok_vinyldns_client

    rs_A_delete = get_recordset_json(shared_zone_test_context.ok_zone, "delete", "A", [{"address": "10.1.1.1"}])
    rs_CNAME_delete = get_recordset_json(shared_zone_test_context.ok_zone, "delete-this", "CNAME", [{"cname": "cname."}])

    to_create = [rs_A_delete, rs_CNAME_delete]
    to_delete = []

    batch_change_input = {
        "comments": "this is optional",
        "changes": [
            get_change_A_AAAA_json("thing.ok.", address="4.5.6.7"),
            get_change_CNAME_json("thing.ok"),
            get_change_A_AAAA_json("delete.ok", change_type="DeleteRecordSet"),
            get_change_CNAME_json("delete.ok"),
            get_change_A_AAAA_json("delete-this.ok", address="4.5.6.7"),
            get_change_CNAME_json("delete-this.ok", change_type="DeleteRecordSet")
        ]
    }

    try:
        for create_json in to_create:
            create_result = client.create_recordset(create_json, status=202)
            to_delete.append(client.wait_until_recordset_change_status(create_result, 'Complete'))

        response = client.create_batch_change(batch_change_input, status=400)
        assert_successful_change_in_error_response(response[0], input_name="thing.ok.", record_data="4.5.6.7")
        assert_failed_change_in_error_response(response[1], input_name="thing.ok.", record_type="CNAME", record_data="test.com.",
                                               error_messages=['Record Name "thing.ok." Not Unique In Batch Change:'
                                                               ' cannot have multiple "CNAME" records with the same name.'])
        assert_successful_change_in_error_response(response[2], input_name="delete.ok.", change_type="DeleteRecordSet")
        assert_successful_change_in_error_response(response[3], input_name="delete.ok.", record_type="CNAME", record_data="test.com.")
        assert_successful_change_in_error_response(response[4], input_name="delete-this.ok.", record_data="4.5.6.7")
        assert_successful_change_in_error_response(response[5], input_name="delete-this.ok.", change_type="DeleteRecordSet", record_type="CNAME")

    finally:
        clear_recordset_list(to_delete, client)


def test_create_batch_change_with_readonly_user_fails(shared_zone_test_context):
    """
    Test creating a batch change with an read-only user fails (acl rules on zone)
    """
    dummy_client = shared_zone_test_context.dummy_vinyldns_client
    ok_client = shared_zone_test_context.ok_vinyldns_client

    acl_rule = generate_acl_rule('Read', groupId=shared_zone_test_context.dummy_group['id'], recordMask='.*', recordTypes=['A', 'AAAA'])

    delete_rs = get_recordset_json(shared_zone_test_context.ok_zone, "delete", "A", [{"address": "127.0.0.1"}], 300)
    update_rs = get_recordset_json(shared_zone_test_context.ok_zone, "update", "A", [{"address": "127.0.0.1"}], 300)

    batch_change_input = {
        "comments": "this is optional",
        "changes": [
            get_change_A_AAAA_json("relative.ok.", address="4.5.6.7"),
            get_change_A_AAAA_json("delete.ok.", change_type="DeleteRecordSet"),
            get_change_A_AAAA_json("update.ok.", address="1.2.3.4"),
            get_change_A_AAAA_json("update.ok.", change_type="DeleteRecordSet")
        ]
    }

    to_delete = []
    try:
        add_ok_acl_rules(shared_zone_test_context, acl_rule)

        for rs in [delete_rs, update_rs]:
            create_result = ok_client.create_recordset(rs, status=202)
            to_delete.append(ok_client.wait_until_recordset_change_status(create_result, 'Complete'))

        errors = dummy_client.create_batch_change(batch_change_input, status=400)

        assert_failed_change_in_error_response(errors[0], input_name="relative.ok.", record_data="4.5.6.7", error_messages=['User \"dummy\" is not authorized.'])
        assert_failed_change_in_error_response(errors[1], input_name="delete.ok.", change_type="DeleteRecordSet", record_data="4.5.6.7",
                                               error_messages=['User "dummy" is not authorized.'])
        assert_failed_change_in_error_response(errors[2], input_name="update.ok.", record_data="1.2.3.4", error_messages=['User \"dummy\" is not authorized.'])
        assert_failed_change_in_error_response(errors[3], input_name="update.ok.", change_type="DeleteRecordSet", record_data=None,
                                               error_messages=['User \"dummy\" is not authorized.'])
    finally:
        clear_ok_acl_rules(shared_zone_test_context)
        clear_recordset_list(to_delete, ok_client)


def test_a_recordtype_add_checks(shared_zone_test_context):
    """
    Test all add validations performed on A records submitted in batch changes
    """
    client = shared_zone_test_context.ok_vinyldns_client

    existing_a = get_recordset_json(shared_zone_test_context.parent_zone, "Existing-A", "A", [{"address": "10.1.1.1"}], 100)
    existing_cname = get_recordset_json(shared_zone_test_context.parent_zone, "Existing-Cname", "CNAME", [{"cname": "cname.data."}], 100)

    batch_change_input = {
        "changes": [
            # valid changes
            get_change_A_AAAA_json("good-record.parent.com.", address="1.2.3.4"),
            get_change_A_AAAA_json("summed-record.parent.com.", address="1.2.3.4"),
            get_change_A_AAAA_json("summed-record.parent.com.", address="5.6.7.8"),

            # input validation failures
            get_change_A_AAAA_json("bad-ttl-and-invalid-name$.parent.com.", ttl=29, address="1.2.3.4"),
            get_change_A_AAAA_json("reverse-zone.30.172.in-addr.arpa.", address="1.2.3.4"),

            # zone discovery failures
            get_change_A_AAAA_json("no.subzone.parent.com.", address="1.2.3.4"),
            get_change_A_AAAA_json("no.zone.at.all.", address="1.2.3.4"),

            # context validation failures
            get_change_CNAME_json("cname-duplicate.parent.com."),
            get_change_A_AAAA_json("cname-duplicate.parent.com.", address="1.2.3.4"),
            get_change_A_AAAA_json("existing-a.parent.com.", address="1.2.3.4"),
            get_change_A_AAAA_json("existing-cname.parent.com.", address="1.2.3.4"),
            get_change_A_AAAA_json("user-add-unauthorized.dummy.", address="1.2.3.4")
        ]
    }

    to_create = [existing_a, existing_cname]
    to_delete = []
    try:
        for create_json in to_create:
            create_result = client.create_recordset(create_json, status=202)
            to_delete.append(client.wait_until_recordset_change_status(create_result, 'Complete'))

        response = client.create_batch_change(batch_change_input, status=400)

        # successful changes
        assert_successful_change_in_error_response(response[0], input_name="good-record.parent.com.", record_data="1.2.3.4")
        assert_successful_change_in_error_response(response[1], input_name="summed-record.parent.com.", record_data="1.2.3.4")
        assert_successful_change_in_error_response(response[2], input_name="summed-record.parent.com.", record_data="5.6.7.8")

        # ttl, domain name, reverse zone input validations
        assert_failed_change_in_error_response(response[3], input_name="bad-ttl-and-invalid-name$.parent.com.", ttl=29, record_data="1.2.3.4",
                                            error_messages=['Invalid TTL: "29", must be a number between 30 and 2147483647.',
                                                            'Invalid domain name: "bad-ttl-and-invalid-name$.parent.com.", '
                                                            'valid domain names must be letters, numbers, and hyphens, joined by dots, and terminated with a dot.'])
        assert_failed_change_in_error_response(response[4], input_name="reverse-zone.30.172.in-addr.arpa.", record_data="1.2.3.4",
                                            error_messages=["Invalid Record Type In Reverse Zone: record with name \"reverse-zone.30.172.in-addr.arpa.\" and type \"A\" is not allowed in a reverse zone."])

        # zone discovery failures
        assert_failed_change_in_error_response(response[5], input_name="no.subzone.parent.com.", record_data="1.2.3.4",
                                            error_messages=['Zone Discovery Failed: zone for "no.subzone.parent.com." does not exist in VinylDNS. If zone exists, then it must be created in VinylDNS.'])
        assert_failed_change_in_error_response(response[6], input_name="no.zone.at.all.", record_data="1.2.3.4",
                                            error_messages=['Zone Discovery Failed: zone for "no.zone.at.all." does not exist in VinylDNS. If zone exists, then it must be created in VinylDNS.'])

        # context validations: duplicate name failure is always on the cname
        assert_failed_change_in_error_response(response[7], input_name="cname-duplicate.parent.com.", record_type="CNAME", record_data="test.com.",
                                               error_messages=["Record Name \"cname-duplicate.parent.com.\" Not Unique In Batch Change: cannot have multiple \"CNAME\" records with the same name."])
        assert_successful_change_in_error_response(response[8], input_name="cname-duplicate.parent.com.", record_data="1.2.3.4")

        # context validations: conflicting recordsets, unauthorized error
        assert_failed_change_in_error_response(response[9], input_name="existing-a.parent.com.", record_data="1.2.3.4",
                                            error_messages=["Record \"existing-a.parent.com.\" Already Exists: cannot add an existing record; to update it, issue a DeleteRecordSet then an Add."])
        assert_failed_change_in_error_response(response[10], input_name="existing-cname.parent.com.", record_data="1.2.3.4",
                                            error_messages=["CNAME Conflict: CNAME record names must be unique. Existing record with name \"existing-cname.parent.com.\" and type \"CNAME\" conflicts with this record."])
        assert_failed_change_in_error_response(response[11], input_name="user-add-unauthorized.dummy.", record_data="1.2.3.4",
                                            error_messages=["User \"ok\" is not authorized."])

    finally:
        clear_recordset_list(to_delete, client)


def test_a_recordtype_update_delete_checks(shared_zone_test_context):
    """
    Test all update and delete validations performed on A records submitted in batch changes
    """
    ok_client = shared_zone_test_context.ok_vinyldns_client
    dummy_client = shared_zone_test_context.dummy_vinyldns_client
    ok_zone = shared_zone_test_context.ok_zone
    dummy_zone = shared_zone_test_context.dummy_zone

    rs_delete_ok = get_recordset_json(ok_zone, "delete", "A", [{'address': '1.1.1.1'}])
    rs_update_ok = get_recordset_json(ok_zone, "update", "A", [{'address': '1.1.1.1'}])
    rs_delete_dummy = get_recordset_json(dummy_zone, "delete-unauthorized", "A", [{'address': '1.1.1.1'}])
    rs_update_dummy = get_recordset_json(dummy_zone, "update-unauthorized", "A", [{'address': '1.1.1.1'}])

    batch_change_input = {
        "comments": "this is optional",
        "changes": [
            # valid changes
            get_change_A_AAAA_json("delete.ok.", change_type="DeleteRecordSet"),
            get_change_A_AAAA_json("update.ok.", change_type="DeleteRecordSet"),
            get_change_A_AAAA_json("update.ok.", ttl=300),

            # input validations failures
            get_change_A_AAAA_json("$invalid.host.name.", change_type="DeleteRecordSet"),
            get_change_A_AAAA_json("reverse.zone.in-addr.arpa.", change_type="DeleteRecordSet"),
            get_change_A_AAAA_json("$another.invalid.host.name.", ttl=300),
            get_change_A_AAAA_json("$another.invalid.host.name.", change_type="DeleteRecordSet"),
            get_change_A_AAAA_json("another.reverse.zone.in-addr.arpa.", ttl=10),
            get_change_A_AAAA_json("another.reverse.zone.in-addr.arpa.", change_type="DeleteRecordSet"),

            # zone discovery failures
            get_change_A_AAAA_json("zone.discovery.error.", change_type="DeleteRecordSet"),

            # context validation failures: record does not exist, not authorized
            get_change_A_AAAA_json("non-existent.ok.", change_type="DeleteRecordSet"),
            get_change_A_AAAA_json("delete-unauthorized.dummy.", change_type="DeleteRecordSet"),
            get_change_A_AAAA_json("update-unauthorized.dummy.", change_type="DeleteRecordSet"),
            get_change_A_AAAA_json("update-unauthorized.dummy.", ttl=300)
        ]
    }

    to_create = [rs_delete_ok, rs_update_ok, rs_delete_dummy, rs_update_dummy]
    to_delete = []

    try:
        for rs in to_create:
            if rs['zoneId'] == dummy_zone['id']:
                create_client = dummy_client
            else:
                create_client = ok_client

            create_rs = create_client.create_recordset(rs, status=202)
            to_delete.append(create_client.wait_until_recordset_change_status(create_rs, 'Complete'))

        # Confirm that record set doesn't already exist
        ok_client.get_recordset(ok_zone['id'], 'non-existent', status=404)

        response = ok_client.create_batch_change(batch_change_input, status=400)

        # valid changes
        assert_successful_change_in_error_response(response[0], input_name="delete.ok.", change_type="DeleteRecordSet")
        assert_successful_change_in_error_response(response[1], input_name="update.ok.", change_type="DeleteRecordSet")
        assert_successful_change_in_error_response(response[2], input_name="update.ok.", ttl=300)

        # input validations failures
        assert_failed_change_in_error_response(response[3], input_name="$invalid.host.name.", change_type="DeleteRecordSet",
                                               error_messages=['Invalid domain name: "$invalid.host.name.", valid domain names must be letters, numbers, and hyphens, joined by dots, and terminated with a dot.'])
        assert_failed_change_in_error_response(response[4], input_name="reverse.zone.in-addr.arpa.", change_type="DeleteRecordSet",
                                               error_messages=['Invalid Record Type In Reverse Zone: record with name "reverse.zone.in-addr.arpa." and type "A" is not allowed in a reverse zone.'])
        assert_failed_change_in_error_response(response[5], input_name="$another.invalid.host.name.", ttl=300,
                                               error_messages=['Invalid domain name: "$another.invalid.host.name.", valid domain names must be letters, numbers, and hyphens, joined by dots, and terminated with a dot.'])
        assert_failed_change_in_error_response(response[6], input_name="$another.invalid.host.name.", change_type="DeleteRecordSet",
                                               error_messages=['Invalid domain name: "$another.invalid.host.name.", valid domain names must be letters, numbers, and hyphens, joined by dots, and terminated with a dot.'])
        assert_failed_change_in_error_response(response[7], input_name="another.reverse.zone.in-addr.arpa.", ttl=10,
                                               error_messages=['Invalid Record Type In Reverse Zone: record with name "another.reverse.zone.in-addr.arpa." and type "A" is not allowed in a reverse zone.',
                                                               'Invalid TTL: "10", must be a number between 30 and 2147483647.'])
        assert_failed_change_in_error_response(response[8], input_name="another.reverse.zone.in-addr.arpa.", change_type="DeleteRecordSet",
                                               error_messages=['Invalid Record Type In Reverse Zone: record with name "another.reverse.zone.in-addr.arpa." and type "A" is not allowed in a reverse zone.'])

        # zone discovery failures
        assert_failed_change_in_error_response(response[9], input_name="zone.discovery.error.", change_type="DeleteRecordSet",
                                               error_messages=['Zone Discovery Failed: zone for "zone.discovery.error." does not exist in VinylDNS. If zone exists, then it must be created in VinylDNS.'])

        # context validation failures: record does not exist, not authorized
        assert_failed_change_in_error_response(response[10], input_name="non-existent.ok.", change_type="DeleteRecordSet",
                                               error_messages=['Record "non-existent.ok." Does Not Exist: cannot delete a record that does not exist.'])
        assert_failed_change_in_error_response(response[11], input_name="delete-unauthorized.dummy.", change_type="DeleteRecordSet",
                                               error_messages=['User \"ok\" is not authorized.'])
        assert_failed_change_in_error_response(response[12], input_name="update-unauthorized.dummy.", change_type="DeleteRecordSet",
                                               error_messages=['User \"ok\" is not authorized.'])
        assert_failed_change_in_error_response(response[13], input_name="update-unauthorized.dummy.", ttl=300, error_messages=['User \"ok\" is not authorized.'])

    finally:
        # Clean up updates
        dummy_deletes = [rs for rs in to_delete if rs['zone']['id'] == dummy_zone['id']]
        ok_deletes = [rs for rs in to_delete if rs['zone']['id'] != dummy_zone['id']]
        clear_recordset_list(dummy_deletes, dummy_client)
        clear_recordset_list(ok_deletes, ok_client)


def test_aaaa_recordtype_add_checks(shared_zone_test_context):
    """
    Test all add validations performed on AAAA records submitted in batch changes
    """
    client = shared_zone_test_context.ok_vinyldns_client

    existing_aaaa = get_recordset_json(shared_zone_test_context.parent_zone, "Existing-AAAA", "AAAA", [{"address": "1::1"}], 100)
    existing_cname = get_recordset_json(shared_zone_test_context.parent_zone, "Existing-Cname", "CNAME", [{"cname": "cname.data."}], 100)

    batch_change_input = {
        "changes": [
            # valid changes
            get_change_A_AAAA_json("good-record.parent.com.", record_type="AAAA", address="1::1"),
            get_change_A_AAAA_json("summed-record.parent.com.", record_type="AAAA", address="1::1"),
            get_change_A_AAAA_json("summed-record.parent.com.", record_type="AAAA", address="1::2"),

            # input validation failures
            get_change_A_AAAA_json("bad-ttl-and-invalid-name$.parent.com.", ttl=29, record_type="AAAA", address="1::1"),
            get_change_A_AAAA_json("reverse-zone.1.2.3.ip6.arpa.", record_type="AAAA", address="1::1"),

            # zone discovery failures
            get_change_A_AAAA_json("no.subzone.parent.com.", record_type="AAAA", address="1::1"),
            get_change_A_AAAA_json("no.zone.at.all.", record_type="AAAA", address="1::1"),

            # context validation failures
            get_change_CNAME_json("cname-duplicate.parent.com."),
            get_change_A_AAAA_json("cname-duplicate.parent.com.", record_type="AAAA", address="1::1"),
            get_change_A_AAAA_json("existing-aaaa.parent.com.", record_type="AAAA", address="1::1"),
            get_change_A_AAAA_json("existing-cname.parent.com.", record_type="AAAA", address="1::1"),
            get_change_A_AAAA_json("user-add-unauthorized.dummy.", record_type="AAAA", address="1::1")
        ]
    }

    to_create = [existing_aaaa, existing_cname]
    to_delete = []
    try:
        for create_json in to_create:
            create_result = client.create_recordset(create_json, status=202)
            to_delete.append(client.wait_until_recordset_change_status(create_result, 'Complete'))

        response = client.create_batch_change(batch_change_input, status=400)

        # successful changes
        assert_successful_change_in_error_response(response[0], input_name="good-record.parent.com.", record_type="AAAA", record_data="1::1")
        assert_successful_change_in_error_response(response[1], input_name="summed-record.parent.com.", record_type="AAAA", record_data="1::1")
        assert_successful_change_in_error_response(response[2], input_name="summed-record.parent.com.", record_type="AAAA", record_data="1::2")

        # ttl, domain name, reverse zone input validations
        assert_failed_change_in_error_response(response[3], input_name="bad-ttl-and-invalid-name$.parent.com.", ttl=29, record_type="AAAA", record_data="1::1",
                                            error_messages=['Invalid TTL: "29", must be a number between 30 and 2147483647.',
                                                            'Invalid domain name: "bad-ttl-and-invalid-name$.parent.com.", '
                                                            'valid domain names must be letters, numbers, and hyphens, joined by dots, and terminated with a dot.'])
        assert_failed_change_in_error_response(response[4], input_name="reverse-zone.1.2.3.ip6.arpa.", record_type="AAAA", record_data="1::1",
                                            error_messages=["Invalid Record Type In Reverse Zone: record with name \"reverse-zone.1.2.3.ip6.arpa.\" and type \"AAAA\" is not allowed in a reverse zone."])

        # zone discovery failures
        assert_failed_change_in_error_response(response[5], input_name="no.subzone.parent.com.", record_type="AAAA", record_data="1::1",
                                            error_messages=["Zone Discovery Failed: zone for \"no.subzone.parent.com.\" does not exist in VinylDNS. If zone exists, then it must be created in VinylDNS."])
        assert_failed_change_in_error_response(response[6], input_name="no.zone.at.all.", record_type="AAAA", record_data="1::1",
                                            error_messages=["Zone Discovery Failed: zone for \"no.zone.at.all.\" does not exist in VinylDNS. If zone exists, then it must be created in VinylDNS."])

        # context validations: duplicate name failure (always on the cname), conflicting recordsets, unauthorized error
        assert_failed_change_in_error_response(response[7], input_name="cname-duplicate.parent.com.", record_type="CNAME", record_data="test.com.",
                                               error_messages=["Record Name \"cname-duplicate.parent.com.\" Not Unique In Batch Change: cannot have multiple \"CNAME\" records with the same name."])
        assert_successful_change_in_error_response(response[8], input_name="cname-duplicate.parent.com.", record_type="AAAA", record_data="1::1")
        assert_failed_change_in_error_response(response[9], input_name="existing-aaaa.parent.com.", record_type="AAAA", record_data="1::1",
                                            error_messages=["Record \"existing-aaaa.parent.com.\" Already Exists: cannot add an existing record; to update it, issue a DeleteRecordSet then an Add."])
        assert_failed_change_in_error_response(response[10], input_name="existing-cname.parent.com.", record_type="AAAA", record_data="1::1",
                                            error_messages=["CNAME Conflict: CNAME record names must be unique. Existing record with name \"existing-cname.parent.com.\" and type \"CNAME\" conflicts with this record."])
        assert_failed_change_in_error_response(response[11], input_name="user-add-unauthorized.dummy.", record_type="AAAA", record_data="1::1",
                                            error_messages=["User \"ok\" is not authorized."])

    finally:
        clear_recordset_list(to_delete, client)


def test_aaaa_recordtype_update_delete_checks(shared_zone_test_context):
    """
    Test all update and delete validations performed on AAAA records submitted in batch changes
    """
    ok_client = shared_zone_test_context.ok_vinyldns_client
    dummy_client = shared_zone_test_context.dummy_vinyldns_client
    ok_zone = shared_zone_test_context.ok_zone
    dummy_zone = shared_zone_test_context.dummy_zone

    rs_delete_ok = get_recordset_json(ok_zone, "delete", "AAAA", [{"address": "1:2:3:4:5:6:7:8"}], 200)
    rs_update_ok = get_recordset_json(ok_zone, "update", "AAAA", [{"address": "1:1:1:1:1:1:1:1"}], 200)
    rs_delete_dummy = get_recordset_json(dummy_zone, "delete-unauthorized", "AAAA", [{"address": "1::1"}], 200)
    rs_update_dummy = get_recordset_json(dummy_zone, "update-unauthorized", "AAAA", [{"address": "1:2:3:4:5:6:7:8"}], 200)

    batch_change_input = {
        "comments": "this is optional",
        "changes": [
            # valid changes
            get_change_A_AAAA_json("delete.ok.", record_type="AAAA", change_type="DeleteRecordSet"),
            get_change_A_AAAA_json("update.ok.", record_type="AAAA", ttl=300, address="1:2:3:4:5:6:7:8"),
            get_change_A_AAAA_json("update.ok.", record_type="AAAA", change_type="DeleteRecordSet"),

            # input validations failures
            get_change_A_AAAA_json("invalid-name$.ok.", record_type="AAAA", change_type="DeleteRecordSet"),
            get_change_A_AAAA_json("reverse.zone.in-addr.arpa.", record_type="AAAA", change_type="DeleteRecordSet"),
            get_change_A_AAAA_json("bad-ttl-and-invalid-name$-update.ok.", record_type="AAAA", change_type="DeleteRecordSet"),
            get_change_A_AAAA_json("bad-ttl-and-invalid-name$-update.ok.", ttl=29, record_type="AAAA", address="1:2:3:4:5:6:7:8"),

            # zone discovery failures
            get_change_A_AAAA_json("no.zone.at.all.", record_type="AAAA", change_type="DeleteRecordSet"),

            # context validation failures
            get_change_A_AAAA_json("delete-nonexistent.ok.", record_type="AAAA", change_type="DeleteRecordSet"),
            get_change_A_AAAA_json("update-nonexistent.ok.", record_type="AAAA", change_type="DeleteRecordSet"),
            get_change_A_AAAA_json("update-nonexistent.ok.", record_type="AAAA", address="1::1"),
            get_change_A_AAAA_json("delete-unauthorized.dummy.", record_type="AAAA", change_type="DeleteRecordSet"),
            get_change_A_AAAA_json("update-unauthorized.dummy.", record_type="AAAA", address="1::1"),
            get_change_A_AAAA_json("update-unauthorized.dummy.", record_type="AAAA", change_type="DeleteRecordSet")
        ]
    }

    to_create = [rs_delete_ok, rs_update_ok, rs_delete_dummy, rs_update_dummy]
    to_delete = []

    try:
        for rs in to_create:
            if rs['zoneId'] == dummy_zone['id']:
                create_client = dummy_client
            else:
                create_client = ok_client

            create_rs = create_client.create_recordset(rs, status=202)
            to_delete.append(create_client.wait_until_recordset_change_status(create_rs, 'Complete'))

        # Confirm that record set doesn't already exist
        ok_client.get_recordset(ok_zone['id'], 'delete-nonexistent', status=404)

        response = ok_client.create_batch_change(batch_change_input, status=400)

        # successful changes
        assert_successful_change_in_error_response(response[0], input_name="delete.ok.", record_type="AAAA", record_data=None, change_type="DeleteRecordSet")
        assert_successful_change_in_error_response(response[1], ttl=300, input_name="update.ok.", record_type="AAAA", record_data="1:2:3:4:5:6:7:8")
        assert_successful_change_in_error_response(response[2], input_name="update.ok.", record_type="AAAA", record_data=None, change_type="DeleteRecordSet")

        # input validations failures: invalid input name, reverse zone error, invalid ttl
        assert_failed_change_in_error_response(response[3], input_name="invalid-name$.ok.", record_type="AAAA", record_data=None, change_type="DeleteRecordSet",
                                               error_messages=['Invalid domain name: "invalid-name$.ok.", valid domain names must be letters, numbers, and hyphens, joined by dots, and terminated with a dot.'])
        assert_failed_change_in_error_response(response[4], input_name="reverse.zone.in-addr.arpa.", record_type="AAAA", record_data=None, change_type="DeleteRecordSet",
                                               error_messages=["Invalid Record Type In Reverse Zone: record with name \"reverse.zone.in-addr.arpa.\" and type \"AAAA\" is not allowed in a reverse zone."])
        assert_failed_change_in_error_response(response[5], input_name="bad-ttl-and-invalid-name$-update.ok.", record_type="AAAA", record_data=None, change_type="DeleteRecordSet",
                                               error_messages=['Invalid domain name: "bad-ttl-and-invalid-name$-update.ok.", valid domain names must be letters, numbers, and hyphens, joined by dots, and terminated with a dot.'])
        assert_failed_change_in_error_response(response[6], input_name="bad-ttl-and-invalid-name$-update.ok.", ttl=29, record_type="AAAA", record_data="1:2:3:4:5:6:7:8",
                                               error_messages=['Invalid TTL: "29", must be a number between 30 and 2147483647.',
                                                               'Invalid domain name: "bad-ttl-and-invalid-name$-update.ok.", valid domain names must be letters, numbers, and hyphens, joined by dots, and terminated with a dot.'])

        # zone discovery failure
        assert_failed_change_in_error_response(response[7], input_name="no.zone.at.all.", record_type="AAAA", record_data=None, change_type="DeleteRecordSet",
                                               error_messages=["Zone Discovery Failed: zone for \"no.zone.at.all.\" does not exist in VinylDNS. If zone exists, then it must be created in VinylDNS."])

        # context validation failures: record does not exist, not authorized
        assert_failed_change_in_error_response(response[8], input_name="delete-nonexistent.ok.", record_type="AAAA", record_data=None, change_type="DeleteRecordSet",
                                               error_messages=["Record \"delete-nonexistent.ok.\" Does Not Exist: cannot delete a record that does not exist."])
        assert_failed_change_in_error_response(response[9], input_name="update-nonexistent.ok.", record_type="AAAA", record_data=None, change_type="DeleteRecordSet",
                                               error_messages=["Record \"update-nonexistent.ok.\" Does Not Exist: cannot delete a record that does not exist."])
        assert_successful_change_in_error_response(response[10], input_name="update-nonexistent.ok.", record_type="AAAA", record_data="1::1",)
        assert_failed_change_in_error_response(response[11], input_name="delete-unauthorized.dummy.", record_type="AAAA", record_data=None, change_type="DeleteRecordSet",
                                               error_messages=["User \"ok\" is not authorized."])
        assert_failed_change_in_error_response(response[12], input_name="update-unauthorized.dummy.", record_type="AAAA", record_data="1::1",
                                               error_messages=["User \"ok\" is not authorized."])
        assert_failed_change_in_error_response(response[13], input_name="update-unauthorized.dummy.", record_type="AAAA", record_data=None, change_type="DeleteRecordSet",
                                               error_messages=["User \"ok\" is not authorized."])

    finally:
        # Clean up updates
        dummy_deletes = [rs for rs in to_delete if rs['zone']['id'] == dummy_zone['id']]
        ok_deletes = [rs for rs in to_delete if rs['zone']['id'] != dummy_zone['id']]
        clear_recordset_list(dummy_deletes, dummy_client)
        clear_recordset_list(ok_deletes, ok_client)


def test_cname_recordtype_add_checks(shared_zone_test_context):
    """
    Test all add validations performed on CNAME records submitted in batch changes
    """
    client = shared_zone_test_context.ok_vinyldns_client
    ok_zone = shared_zone_test_context.ok_zone

    existing_forward = get_recordset_json(shared_zone_test_context.parent_zone, "Existing-Forward", "A", [{"address": "1.2.3.4"}], 100)
    existing_reverse = get_recordset_json(shared_zone_test_context.classless_base_zone, "0", "PTR", [{"ptrdname": "test.com."}], 100)
    existing_cname = get_recordset_json(shared_zone_test_context.parent_zone, "Existing-Cname", "CNAME", [{"cname": "cname.data."}], 100)
    rs_a_to_cname_ok = get_recordset_json(ok_zone, "a-to-cname", "A", [{'address': '1.1.1.1'}])
    rs_cname_to_A_ok = get_recordset_json(ok_zone, "cname-to-a", "CNAME", [{'cname': 'test.com.'}])

    batch_change_input = {
        "changes": [
            # valid change
            get_change_CNAME_json("forward-zone.parent.com."),
            get_change_CNAME_json("reverse-zone.30.172.in-addr.arpa."),

            # valid changes - delete and add of same record name but different type
            get_change_A_AAAA_json("a-to-cname.ok", change_type="DeleteRecordSet"),
            get_change_CNAME_json("a-to-cname.ok"),
            get_change_A_AAAA_json("cname-to-a.ok"),
            get_change_CNAME_json("cname-to-a.ok", change_type="DeleteRecordSet"),

            # input validations failures
            get_change_CNAME_json("bad-ttl-and-invalid-name$.parent.com.", ttl=29, cname="also$bad.name"),

            # zone discovery failure
            get_change_CNAME_json("no.subzone.parent.com."),

            # cant be apex
            get_change_CNAME_json("parent.com."),

            # context validation failures
            get_change_PTR_json("192.0.2.15"),
            get_change_CNAME_json("15.2.0.192.in-addr.arpa.", cname="duplicate.other.type.within.batch."),
            get_change_CNAME_json("cname-duplicate.parent.com."),
            get_change_CNAME_json("cname-duplicate.parent.com.", cname="duplicate.cname.type.within.batch."),
            get_change_CNAME_json("existing-forward.parent.com."),
            get_change_CNAME_json("existing-cname.parent.com."),
            get_change_CNAME_json("0.2.0.192.in-addr.arpa.", cname="duplicate.in.db."),
            get_change_CNAME_json("user-add-unauthorized.dummy.")
        ]
    }

    to_create = [existing_forward, existing_reverse, existing_cname, rs_a_to_cname_ok, rs_cname_to_A_ok]
    to_delete = []
    try:
        for create_json in to_create:
            create_result = client.create_recordset(create_json, status=202)
            to_delete.append(client.wait_until_recordset_change_status(create_result, 'Complete'))

        response = client.create_batch_change(batch_change_input, status=400)

        # successful changes
        assert_successful_change_in_error_response(response[0], input_name="forward-zone.parent.com.", record_type="CNAME", record_data="test.com.")
        assert_successful_change_in_error_response(response[1], input_name="reverse-zone.30.172.in-addr.arpa.", record_type="CNAME", record_data="test.com.")

        # successful changes - delete and add of same record name but different type
        assert_successful_change_in_error_response(response[2], input_name="a-to-cname.ok.", change_type="DeleteRecordSet")
        assert_successful_change_in_error_response(response[3], input_name="a-to-cname.ok.", record_type="CNAME", record_data="test.com.")
        assert_successful_change_in_error_response(response[4], input_name="cname-to-a.ok.")
        assert_successful_change_in_error_response(response[5], input_name="cname-to-a.ok.", record_type="CNAME", change_type="DeleteRecordSet")

        # ttl, domain name, data
        assert_failed_change_in_error_response(response[6], input_name="bad-ttl-and-invalid-name$.parent.com.", ttl=29, record_type="CNAME", record_data="also$bad.name.",
                                               error_messages=['Invalid TTL: "29", must be a number between 30 and 2147483647.',
                                                               'Invalid domain name: "bad-ttl-and-invalid-name$.parent.com.", '
                                                               'valid domain names must be letters, numbers, and hyphens, '
                                                               'joined by dots, and terminated with a dot.',
                                                               'Invalid domain name: "also$bad.name.", '
                                                               'valid domain names must be letters, numbers, and hyphens, '
                                                               'joined by dots, and terminated with a dot.'])
        # zone discovery failure
        assert_failed_change_in_error_response(response[7], input_name="no.subzone.parent.com.", record_type="CNAME", record_data="test.com.",
                                               error_messages=["Zone Discovery Failed: zone for \"no.subzone.parent.com.\" does not exist in VinylDNS. If zone exists, then it must be created in VinylDNS."])

        # CNAME cant be apex
        assert_failed_change_in_error_response(response[8], input_name="parent.com.", record_type="CNAME", record_data="test.com.",
                                               error_messages=["Record \"parent.com.\" Already Exists: cannot add an existing record; to update it, issue a DeleteRecordSet then an Add."])

        # context validations: duplicates in batch
        assert_successful_change_in_error_response(response[9], input_name="192.0.2.15", record_type="PTR", record_data="test.com.")
        assert_failed_change_in_error_response(response[10], input_name="15.2.0.192.in-addr.arpa.", record_type="CNAME", record_data="duplicate.other.type.within.batch.",
                                                error_messages=["Record Name \"15.2.0.192.in-addr.arpa.\" Not Unique In Batch Change: cannot have multiple \"CNAME\" records with the same name."])

        assert_failed_change_in_error_response(response[11], input_name="cname-duplicate.parent.com.", record_type="CNAME", record_data="test.com.",
                                           error_messages=["Record Name \"cname-duplicate.parent.com.\" Not Unique In Batch Change: cannot have multiple \"CNAME\" records with the same name."])
        assert_failed_change_in_error_response(response[12], input_name="cname-duplicate.parent.com.", record_type="CNAME", record_data="duplicate.cname.type.within.batch.",
                                           error_messages=["Record Name \"cname-duplicate.parent.com.\" Not Unique In Batch Change: cannot have multiple \"CNAME\" records with the same name."])

        # context validations: existing recordsets pre-request, unauthorized, failure on duplicate add
        assert_failed_change_in_error_response(response[13], input_name="existing-forward.parent.com.", record_type="CNAME", record_data="test.com.",
                                               error_messages=["CNAME Conflict: CNAME record names must be unique. Existing record with name \"existing-forward.parent.com.\" and type \"A\" conflicts with this record."])
        assert_failed_change_in_error_response(response[14], input_name="existing-cname.parent.com.", record_type="CNAME", record_data="test.com.",
                                               error_messages=["Record \"existing-cname.parent.com.\" Already Exists: cannot add an existing record; to update it, issue a DeleteRecordSet then an Add.",
                                                               "CNAME Conflict: CNAME record names must be unique. Existing record with name \"existing-cname.parent.com.\" and type \"CNAME\" conflicts with this record."])
        assert_failed_change_in_error_response(response[15], input_name="0.2.0.192.in-addr.arpa.", record_type="CNAME", record_data="duplicate.in.db.",
                                               error_messages=["CNAME Conflict: CNAME record names must be unique. Existing record with name \"0.2.0.192.in-addr.arpa.\" and type \"PTR\" conflicts with this record."])
        assert_failed_change_in_error_response(response[16], input_name="user-add-unauthorized.dummy.", record_type="CNAME", record_data="test.com.",
                                               error_messages=["User \"ok\" is not authorized."])

    finally:
        clear_recordset_list(to_delete, client)


def test_cname_recordtype_update_delete_checks(shared_zone_test_context):
    """
    Test all update and delete validations performed on CNAME records submitted in batch changes
    """
    ok_client = shared_zone_test_context.ok_vinyldns_client
    dummy_client = shared_zone_test_context.dummy_vinyldns_client
    ok_zone = shared_zone_test_context.ok_zone
    dummy_zone = shared_zone_test_context.dummy_zone
    classless_base_zone = shared_zone_test_context.classless_base_zone

    rs_delete_ok = get_recordset_json(ok_zone, "delete", "CNAME", [{'cname': 'test.com.'}])
    rs_update_ok = get_recordset_json(ok_zone, "update", "CNAME", [{'cname': 'test.com.'}])
    rs_delete_dummy = get_recordset_json(dummy_zone, "delete-unauthorized", "CNAME", [{'cname': 'test.com.'}])
    rs_update_dummy = get_recordset_json(dummy_zone, "update-unauthorized", "CNAME", [{'cname': 'test.com.'}])
    rs_delete_base = get_recordset_json(classless_base_zone, "200", "CNAME", [{'cname': '200.192/30.2.0.192.in-addr.arpa.'}])
    rs_update_base = get_recordset_json(classless_base_zone, "201", "CNAME", [{'cname': '201.192/30.2.0.192.in-addr.arpa.'}])
    rs_update_duplicate_add = get_recordset_json(shared_zone_test_context.parent_zone, "Existing-Cname2", "CNAME", [{"cname": "cname.data."}], 100)

    batch_change_input = {
        "comments": "this is optional",
        "changes": [
            # valid changes - forward zone
            get_change_CNAME_json("delete.ok.", change_type="DeleteRecordSet"),
            get_change_CNAME_json("update.ok.", change_type="DeleteRecordSet"),
            get_change_CNAME_json("update.ok.", ttl=300),

            # valid changes - reverse zone
            get_change_CNAME_json("200.2.0.192.in-addr.arpa.", change_type="DeleteRecordSet"),
            get_change_CNAME_json("201.2.0.192.in-addr.arpa.", change_type="DeleteRecordSet"),
            get_change_CNAME_json("201.2.0.192.in-addr.arpa.", ttl=300),

            # input validation failures
            get_change_CNAME_json("$invalid.host.name.", change_type="DeleteRecordSet"),
            get_change_CNAME_json("$another.invalid.host.name", change_type="DeleteRecordSet"),
            get_change_CNAME_json("$another.invalid.host.name", ttl=20, cname="$another.invalid.cname."),

            # zone discovery failures
            get_change_CNAME_json("zone.discovery.error.", change_type="DeleteRecordSet"),

            # context validation failures: record does not exist, not authorized, failure on update with multiple adds
            get_change_CNAME_json("non-existent-delete.ok.", change_type="DeleteRecordSet"),
            get_change_CNAME_json("non-existent-update.ok.", change_type="DeleteRecordSet"),
            get_change_CNAME_json("non-existent-update.ok."),
            get_change_CNAME_json("delete-unauthorized.dummy.", change_type="DeleteRecordSet"),
            get_change_CNAME_json("update-unauthorized.dummy.", change_type="DeleteRecordSet"),
            get_change_CNAME_json("update-unauthorized.dummy.", ttl=300),
            get_change_CNAME_json("existing-cname2.parent.com.", change_type="DeleteRecordSet"),
            get_change_CNAME_json("existing-cname2.parent.com."),
            get_change_CNAME_json("existing-cname2.parent.com.", ttl=350)
        ]
    }

    to_create = [rs_delete_ok, rs_update_ok, rs_delete_dummy, rs_update_dummy, rs_delete_base, rs_update_base, rs_update_duplicate_add]
    to_delete = []

    try:
        for rs in to_create:
            if rs['zoneId'] == dummy_zone['id']:
                create_client = dummy_client
            else:
                create_client = ok_client

            create_rs = create_client.create_recordset(rs, status=202)
            to_delete.append(create_client.wait_until_recordset_change_status(create_rs, 'Complete'))

        # Confirm that record set doesn't already exist
        ok_client.get_recordset(ok_zone['id'], 'non-existent', status=404)

        response = ok_client.create_batch_change(batch_change_input, status=400)

        # valid changes - forward zone
        assert_successful_change_in_error_response(response[0], input_name="delete.ok.", record_type="CNAME", change_type="DeleteRecordSet")
        assert_successful_change_in_error_response(response[1], input_name="update.ok.", record_type="CNAME", change_type="DeleteRecordSet")
        assert_successful_change_in_error_response(response[2], input_name="update.ok.", record_type="CNAME", ttl=300, record_data="test.com.")

        # valid changes - reverse zone
        assert_successful_change_in_error_response(response[3], input_name="200.2.0.192.in-addr.arpa.", record_type="CNAME", change_type="DeleteRecordSet")
        assert_successful_change_in_error_response(response[4], input_name="201.2.0.192.in-addr.arpa.", record_type="CNAME", change_type="DeleteRecordSet")
        assert_successful_change_in_error_response(response[5], input_name="201.2.0.192.in-addr.arpa.", record_type="CNAME", ttl=300, record_data="test.com.")

        # ttl, domain name, data
        assert_failed_change_in_error_response(response[6], input_name="$invalid.host.name.", record_type="CNAME", change_type="DeleteRecordSet",
                                               error_messages=['Invalid domain name: "$invalid.host.name.", valid domain names must be letters, numbers, and hyphens, joined by dots, and terminated with a dot.'])
        assert_failed_change_in_error_response(response[7], input_name="$another.invalid.host.name.", record_type="CNAME", change_type="DeleteRecordSet",
                                               error_messages=['Invalid domain name: "$another.invalid.host.name.", valid domain names must be letters, numbers, and hyphens, joined by dots, and terminated with a dot.'])
        assert_failed_change_in_error_response(response[8], input_name="$another.invalid.host.name.", ttl=20, record_type="CNAME", record_data="$another.invalid.cname.",
                                               error_messages=['Invalid TTL: "20", must be a number between 30 and 2147483647.',
                                                               'Invalid domain name: "$another.invalid.host.name.", valid domain names must be letters, numbers, and hyphens, joined by dots, and terminated with a dot.',
                                                               'Invalid domain name: "$another.invalid.cname.", valid domain names must be letters, numbers, and hyphens, joined by dots, and terminated with a dot.'])

        # zone discovery failures
        assert_failed_change_in_error_response(response[9], input_name="zone.discovery.error.", record_type="CNAME", change_type="DeleteRecordSet",
                                               error_messages=['Zone Discovery Failed: zone for "zone.discovery.error." does not exist in VinylDNS. If zone exists, then it must be created in VinylDNS.'])

        # context validation failures: record does not exist, not authorized
        assert_failed_change_in_error_response(response[10], input_name="non-existent-delete.ok.", record_type="CNAME", change_type="DeleteRecordSet",
                                               error_messages=['Record "non-existent-delete.ok." Does Not Exist: cannot delete a record that does not exist.'])
        assert_failed_change_in_error_response(response[11], input_name="non-existent-update.ok.", record_type="CNAME", change_type="DeleteRecordSet",
                                               error_messages=['Record "non-existent-update.ok." Does Not Exist: cannot delete a record that does not exist.'])
        assert_successful_change_in_error_response(response[12], input_name="non-existent-update.ok.", record_type="CNAME", record_data="test.com.")
        assert_failed_change_in_error_response(response[13], input_name="delete-unauthorized.dummy.", record_type="CNAME", change_type="DeleteRecordSet",
                                               error_messages=['User "ok" is not authorized.'])
        assert_failed_change_in_error_response(response[14], input_name="update-unauthorized.dummy.", record_type="CNAME", change_type="DeleteRecordSet",
                                               error_messages=['User "ok" is not authorized.'])
        assert_failed_change_in_error_response(response[15], input_name="update-unauthorized.dummy.", record_type="CNAME", ttl=300, record_data="test.com.", error_messages=['User "ok" is not authorized.'])
        assert_successful_change_in_error_response(response[16], input_name="existing-cname2.parent.com.", record_type="CNAME", change_type="DeleteRecordSet")
        assert_failed_change_in_error_response(response[17], input_name="existing-cname2.parent.com.", record_type="CNAME", record_data="test.com.",
                                               error_messages=["Record Name \"existing-cname2.parent.com.\" Not Unique In Batch Change: cannot have multiple \"CNAME\" records with the same name."])
        assert_failed_change_in_error_response(response[18], input_name="existing-cname2.parent.com.", record_type="CNAME", record_data="test.com.", ttl=350,
                                               error_messages=["Record Name \"existing-cname2.parent.com.\" Not Unique In Batch Change: cannot have multiple \"CNAME\" records with the same name."])


    finally:
        # Clean up updates
        dummy_deletes = [rs for rs in to_delete if rs['zone']['id'] == dummy_zone['id']]
        ok_deletes = [rs for rs in to_delete if rs['zone']['id'] != dummy_zone['id']]
        clear_recordset_list(dummy_deletes, dummy_client)
        clear_recordset_list(ok_deletes, ok_client)


def test_ptr_recordtype_auth_checks(shared_zone_test_context):
    """
    Test all authorization validations performed on PTR records submitted in batch changes
    """
    dummy_client = shared_zone_test_context.dummy_vinyldns_client
    ok_client = shared_zone_test_context.ok_vinyldns_client

    no_auth_ipv4 = get_recordset_json(shared_zone_test_context.classless_base_zone, "25", "PTR", [{"ptrdname": "ptrdname.data."}], 200)
    no_auth_ipv6 = get_recordset_json(shared_zone_test_context.ip6_reverse_zone, "4.3.2.1.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0", "PTR", [{"ptrdname": "ptrdname.data."}], 200)

    batch_change_input = {
        "changes": [
            get_change_PTR_json("192.0.2.5", ptrdname="not.authorized.ipv4.ptr.base."),
            get_change_PTR_json("192.0.2.196", ptrdname="not.authorized.ipv4.ptr.classless.delegation."),
            get_change_PTR_json("fd69:27cc:fe91::1234", ptrdname="not.authorized.ipv6.ptr."),
            get_change_PTR_json("192.0.2.25", change_type="DeleteRecordSet"),
            get_change_PTR_json("fd69:27cc:fe91::1234", change_type="DeleteRecordSet")
        ]
    }

    to_create = [no_auth_ipv4, no_auth_ipv6]
    to_delete = []

    try:
        for create_json in to_create:
            create_result = ok_client.create_recordset(create_json, status=202)
            to_delete.append(ok_client.wait_until_recordset_change_status(create_result, 'Complete'))

        errors = dummy_client.create_batch_change(batch_change_input, status=400)

        assert_failed_change_in_error_response(errors[0], input_name="192.0.2.5", record_type="PTR", record_data="not.authorized.ipv4.ptr.base.",
                                               error_messages=["User \"dummy\" is not authorized."])
        assert_failed_change_in_error_response(errors[1], input_name="192.0.2.196", record_type="PTR", record_data="not.authorized.ipv4.ptr.classless.delegation.",
                                               error_messages=["User \"dummy\" is not authorized."])
        assert_failed_change_in_error_response(errors[2], input_name="fd69:27cc:fe91::1234", record_type="PTR", record_data="not.authorized.ipv6.ptr.",
                                               error_messages=["User \"dummy\" is not authorized."])
        assert_failed_change_in_error_response(errors[3], input_name="192.0.2.25", record_type="PTR", record_data=None, change_type="DeleteRecordSet",
                                               error_messages=["User \"dummy\" is not authorized."])
        assert_failed_change_in_error_response(errors[4], input_name="fd69:27cc:fe91::1234", record_type="PTR", record_data=None, change_type="DeleteRecordSet",
                                               error_messages=["User \"dummy\" is not authorized."])
    finally:
        clear_recordset_list(to_delete, ok_client)


def test_ipv4_ptr_recordtype_add_checks(shared_zone_test_context):
    """
    Perform all add, non-authorization validations performed on IPv4 PTR records submitted in batch changes
    """
    client = shared_zone_test_context.ok_vinyldns_client

    existing_ipv4 = get_recordset_json(shared_zone_test_context.classless_zone_delegation_zone, "193", "PTR", [{"ptrdname": "ptrdname.data."}])
    existing_cname = get_recordset_json(shared_zone_test_context.classless_base_zone, "199", "CNAME", [{"cname": "cname.data."}], 300)

    batch_change_input = {
        "changes": [
            # valid change
            get_change_PTR_json("192.0.2.44", ptrdname="base.vinyldns"),
            get_change_PTR_json("192.0.2.198", ptrdname="delegated.vinyldns"),
            get_change_PTR_json("192.0.2.197"),
            get_change_PTR_json("192.0.2.197", ptrdname="ptrdata."),

            # input validation failures
            get_change_PTR_json("invalidip.111."),
            get_change_PTR_json("4.5.6.7", ttl=29, ptrdname="-1.2.3.4"),

            # delegated and non-delegated PTR duplicate name checks
            get_change_PTR_json("192.0.2.196"), # delegated zone
            get_change_CNAME_json("196.2.0.192.in-addr.arpa"), # non-delegated zone
            get_change_CNAME_json("196.192/30.2.0.192.in-addr.arpa"), # delegated zone

            get_change_PTR_json("192.0.2.55"), # non-delegated zone
            get_change_CNAME_json("55.2.0.192.in-addr.arpa"), # non-delegated zone
            get_change_CNAME_json("55.192/30.2.0.192.in-addr.arpa"), # delegated zone

            # zone discovery failure
            get_change_PTR_json("192.0.1.192"),

            # context validation failures
            get_change_PTR_json("192.0.2.193", ptrdname="existing-ptr."),
            get_change_PTR_json("192.0.2.199", ptrdname="existing-cname.")
        ]
    }

    to_create = [existing_ipv4, existing_cname]
    to_delete = []
    try:
        for create_json in to_create:
            create_result = client.create_recordset(create_json, status=202)
            to_delete.append(client.wait_until_recordset_change_status(create_result, 'Complete'))

        response = client.create_batch_change(batch_change_input, status=400)

        # successful changes
        assert_successful_change_in_error_response(response[0], input_name="192.0.2.44", record_type="PTR", record_data="base.vinyldns.")
        assert_successful_change_in_error_response(response[1], input_name="192.0.2.198", record_type="PTR", record_data="delegated.vinyldns.")


        # duplicate names succeed for ptr
        assert_successful_change_in_error_response(response[2], input_name="192.0.2.197", record_type="PTR", record_data="test.com.")
        assert_successful_change_in_error_response(response[3], input_name="192.0.2.197", record_type="PTR", record_data="ptrdata.")

        # input validation failures: invalid ip, ttl, data
        assert_failed_change_in_error_response(response[4], input_name="invalidip.111.", record_type="PTR", record_data="test.com.",
                                               error_messages=['Invalid IP address: "invalidip.111.".'])
        assert_failed_change_in_error_response(response[5], input_name="4.5.6.7", ttl=29, record_type="PTR", record_data="-1.2.3.4.",
                                               error_messages=['Invalid TTL: "29", must be a number between 30 and 2147483647.',
                                                               'Invalid domain name: "-1.2.3.4.", '
                                                               'valid domain names must be letters, numbers, and hyphens, joined by dots, and terminated with a dot.'])

        # delegated and non-delegated PTR duplicate name checks
        assert_successful_change_in_error_response(response[6], input_name="192.0.2.196", record_type="PTR", record_data="test.com.")
        assert_successful_change_in_error_response(response[7], input_name="196.2.0.192.in-addr.arpa.", record_type="CNAME", record_data="test.com.")
        assert_failed_change_in_error_response(response[8], input_name="196.192/30.2.0.192.in-addr.arpa.", record_type="CNAME", record_data="test.com.",
                                               error_messages=['Record Name "196.192/30.2.0.192.in-addr.arpa." Not Unique In Batch Change: cannot have multiple "CNAME" records with the same name.'])
        assert_successful_change_in_error_response(response[9], input_name="192.0.2.55", record_type="PTR", record_data="test.com.")
        assert_failed_change_in_error_response(response[10], input_name="55.2.0.192.in-addr.arpa.", record_type="CNAME", record_data="test.com.",
                                               error_messages=['Record Name "55.2.0.192.in-addr.arpa." Not Unique In Batch Change: cannot have multiple "CNAME" records with the same name.'])
        assert_successful_change_in_error_response(response[11], input_name="55.192/30.2.0.192.in-addr.arpa.", record_type="CNAME", record_data="test.com.")

        # zone discovery failure
        assert_failed_change_in_error_response(response[12], input_name="192.0.1.192", record_type="PTR", record_data="test.com.",
                                               error_messages=['Zone Discovery Failed: zone for "192.0.1.192" does not exist in VinylDNS. If zone exists, then it must be created in VinylDNS.'])

        # context validations: existing cname recordset
        assert_failed_change_in_error_response(response[13], input_name="192.0.2.193", record_type="PTR", record_data="existing-ptr.",
                                               error_messages=['Record "192.0.2.193" Already Exists: cannot add an existing record; to update it, issue a DeleteRecordSet then an Add.'])
        assert_failed_change_in_error_response(response[14], input_name="192.0.2.199", record_type="PTR", record_data="existing-cname.",
                                               error_messages=['CNAME Conflict: CNAME record names must be unique. Existing record with name "192.0.2.199" and type "CNAME" conflicts with this record.'])

    finally:
        clear_recordset_list(to_delete, client)


def test_ipv4_ptr_recordtype_update_delete_checks(shared_zone_test_context):
    """
    Test all update and delete validations performed on ipv4 PTR records submitted in batch changes
    """
    ok_client = shared_zone_test_context.ok_vinyldns_client
    base_zone = shared_zone_test_context.classless_base_zone
    delegated_zone = shared_zone_test_context.classless_zone_delegation_zone

    rs_delete_ipv4 = get_recordset_json(base_zone, "25", "PTR", [{"ptrdname": "delete.ptr."}], 200)
    rs_update_ipv4 = get_recordset_json(delegated_zone, "193", "PTR", [{"ptrdname": "update.ptr."}], 200)
    rs_replace_cname = get_recordset_json(base_zone, "21", "CNAME", [{"cname": "replace.cname."}], 200)
    rs_replace_ptr = get_recordset_json(base_zone, "17", "PTR", [{"ptrdname": "replace.ptr."}], 200)
    rs_update_ipv4_fail = get_recordset_json(base_zone, "9", "PTR", [{"ptrdname": "failed-update.ptr."}], 200)
    rs_update_ipv4_double_update = get_recordset_json(shared_zone_test_context.classless_base_zone, "50", "PTR", [{"ptrdname": "ptrdname.data."}])

    batch_change_input = {
        "comments": "this is optional",
        "changes": [
            # valid changes ipv4
            get_change_PTR_json("192.0.2.25", change_type="DeleteRecordSet"),
            get_change_PTR_json("192.0.2.193", ttl=300, ptrdname="has-updated.ptr."),
            get_change_PTR_json("192.0.2.193", change_type="DeleteRecordSet"),

            # valid changes: delete and add of same record name but different type
            get_change_CNAME_json("21.2.0.192.in-addr.arpa", change_type="DeleteRecordSet"),
            get_change_PTR_json("192.0.2.21", ptrdname="replace-cname.ptr."),
            get_change_CNAME_json("17.2.0.192.in-addr.arpa", cname="replace-ptr.cname."),
            get_change_PTR_json("192.0.2.17", change_type="DeleteRecordSet"),

            # input validations failures
            get_change_PTR_json("1.1.1", change_type="DeleteRecordSet"),
            get_change_PTR_json("192.0.2.", change_type="DeleteRecordSet"),
            get_change_PTR_json("192.0.2.", ttl=29, ptrdname="failed-update$.ptr"),

            # zone discovery failures
            get_change_PTR_json("192.0.1.25", change_type="DeleteRecordSet"),

            # context validation failures
            get_change_PTR_json("192.0.2.199", change_type="DeleteRecordSet"),
            get_change_PTR_json("192.0.2.200", ttl=300, ptrdname="has-updated.ptr."),
            get_change_PTR_json("192.0.2.200", change_type="DeleteRecordSet"),
            get_change_PTR_json("192.0.2.50", change_type="DeleteRecordSet"),
            get_change_PTR_json("192.0.2.50"),
            get_change_PTR_json("192.0.2.50", ttl=350)
        ]
    }

    to_create = [rs_delete_ipv4, rs_update_ipv4, rs_replace_cname, rs_replace_ptr, rs_update_ipv4_fail, rs_update_ipv4_double_update]
    to_delete = []

    try:
        for rs in to_create:
            create_rs = ok_client.create_recordset(rs, status=202)
            to_delete.append(ok_client.wait_until_recordset_change_status(create_rs, 'Complete'))

        response = ok_client.create_batch_change(batch_change_input, status=400)

        # successful changes
        assert_successful_change_in_error_response(response[0], input_name="192.0.2.25", record_type="PTR", record_data=None, change_type="DeleteRecordSet")
        assert_successful_change_in_error_response(response[1], ttl=300, input_name="192.0.2.193", record_type="PTR", record_data="has-updated.ptr.")
        assert_successful_change_in_error_response(response[2], input_name="192.0.2.193", record_type="PTR", record_data=None, change_type="DeleteRecordSet")

        #successful changes: add and delete of same record name but different type
        assert_successful_change_in_error_response(response[3], input_name="21.2.0.192.in-addr.arpa.", record_type="CNAME", record_data=None, change_type="DeleteRecordSet")
        assert_successful_change_in_error_response(response[4], input_name="192.0.2.21", record_type="PTR", record_data="replace-cname.ptr.")
        assert_successful_change_in_error_response(response[5], input_name="17.2.0.192.in-addr.arpa.", record_type="CNAME", record_data="replace-ptr.cname.")
        assert_successful_change_in_error_response(response[6], input_name="192.0.2.17", record_type="PTR", record_data=None, change_type="DeleteRecordSet")

        #successful changes: record input_name does not have to be unique
        assert_successful_change_in_error_response(response[14], input_name="192.0.2.50", record_type="PTR", change_type="DeleteRecordSet")
        assert_successful_change_in_error_response(response[15], input_name="192.0.2.50", record_type="PTR", record_data="test.com.")
        assert_successful_change_in_error_response(response[16], input_name="192.0.2.50", record_type="PTR", record_data="test.com.", ttl=350)

        # input validations failures: invalid IP, ttl, and record data
        assert_failed_change_in_error_response(response[7], input_name="1.1.1", record_type="PTR", record_data=None, change_type="DeleteRecordSet",
                                               error_messages=['Invalid IP address: "1.1.1".'])
        assert_failed_change_in_error_response(response[8], input_name="192.0.2.", record_type="PTR", record_data=None, change_type="DeleteRecordSet",
                                               error_messages=['Invalid IP address: "192.0.2.".'])
        assert_failed_change_in_error_response(response[9], ttl=29, input_name="192.0.2.", record_type="PTR", record_data="failed-update$.ptr.",
                                               error_messages=['Invalid TTL: "29", must be a number between 30 and 2147483647.',
                                                               'Invalid IP address: "192.0.2.".',
                                                               'Invalid domain name: "failed-update$.ptr.", valid domain names must be letters, numbers, and hyphens, joined by dots, and terminated with a dot.'])

        # zone discovery failure
        assert_failed_change_in_error_response(response[10], input_name="192.0.1.25", record_type="PTR", record_data=None, change_type="DeleteRecordSet",
                                               error_messages=["Zone Discovery Failed: zone for \"192.0.1.25\" does not exist in VinylDNS. If zone exists, then it must be created in VinylDNS."])

        # context validation failures: record does not exist
        assert_failed_change_in_error_response(response[11], input_name="192.0.2.199", record_type="PTR", record_data=None, change_type="DeleteRecordSet",
                                               error_messages=["Record \"192.0.2.199\" Does Not Exist: cannot delete a record that does not exist."])
        assert_successful_change_in_error_response(response[12], ttl=300, input_name="192.0.2.200", record_type="PTR", record_data="has-updated.ptr.")
        assert_failed_change_in_error_response(response[13], input_name="192.0.2.200", record_type="PTR", record_data=None, change_type="DeleteRecordSet",
                                               error_messages=["Record \"192.0.2.200\" Does Not Exist: cannot delete a record that does not exist."])

    finally:
        clear_recordset_list(to_delete, ok_client)


def test_ipv6_ptr_recordtype_add_checks(shared_zone_test_context):
    """
    Test all add, non-authorization validations performed on IPv6 PTR records submitted in batch changes
    """
    client = shared_zone_test_context.ok_vinyldns_client

    existing_ptr = get_recordset_json(shared_zone_test_context.ip6_reverse_zone, "a.a.a.a.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0", "PTR", [{"ptrdname": "test.com."}], 100)

    batch_change_input = {
        "changes": [
            # valid change
            get_change_PTR_json("fd69:27cc:fe91::1234"),
            get_change_PTR_json("fd69:27cc:fe91::abc", ptrdname="duplicate.record1."),
            get_change_PTR_json("fd69:27cc:fe91::abc", ptrdname="duplicate.record2."),

            # input validation failures
            get_change_PTR_json("fd69:27cc:fe91::abe", ttl=29),
            get_change_PTR_json("fd69:27cc:fe91::bae", ptrdname="$malformed.hostname."),
            get_change_PTR_json("fd69:27cc:fe91de::ab", ptrdname="malformed.ip.address."),

            # zone discovery failure
            get_change_PTR_json("fedc:ba98:7654::abc", ptrdname="zone.discovery.error."),

            # context validation failures
            get_change_PTR_json("fd69:27cc:fe91::aaaa", ptrdname="existing.ptr.")
        ]
    }

    to_create = [existing_ptr]
    to_delete = []
    try:
        for create_json in to_create:
            create_result = client.create_recordset(create_json, status=202)
            to_delete.append(client.wait_until_recordset_change_status(create_result, 'Complete'))

        response = client.create_batch_change(batch_change_input, status=400)

        # successful changes
        assert_successful_change_in_error_response(response[0], input_name="fd69:27cc:fe91::1234", record_type="PTR", record_data="test.com.")

        # successful changes: input_name does not have to be unique
        assert_successful_change_in_error_response(response[1], input_name="fd69:27cc:fe91::abc", record_type="PTR", record_data="duplicate.record1.")
        assert_successful_change_in_error_response(response[2], input_name="fd69:27cc:fe91::abc", record_type="PTR", record_data="duplicate.record2.")

        # independent validations: bad TTL, malformed host name/IP address, duplicate record
        assert_failed_change_in_error_response(response[3], input_name="fd69:27cc:fe91::abe", ttl=29, record_type="PTR", record_data="test.com.",
                                               error_messages=['Invalid TTL: "29", must be a number between 30 and 2147483647.'])
        assert_failed_change_in_error_response(response[4], input_name="fd69:27cc:fe91::bae", record_type="PTR", record_data="$malformed.hostname.",
                                               error_messages=['Invalid domain name: "$malformed.hostname.", valid domain names must be letters, numbers, and hyphens, joined by dots, and terminated with a dot.'])
        assert_failed_change_in_error_response(response[5], input_name="fd69:27cc:fe91de::ab", record_type="PTR", record_data="malformed.ip.address.",
                                               error_messages=['Invalid IP address: "fd69:27cc:fe91de::ab".'])

        # zone discovery failure
        assert_failed_change_in_error_response(response[6], input_name="fedc:ba98:7654::abc", record_type="PTR", record_data="zone.discovery.error.",
                                               error_messages=["Zone Discovery Failed: zone for \"fedc:ba98:7654::abc\" does not exist in VinylDNS. If zone exists, then it must be created in VinylDNS."])

        # context validations: existing record sets pre-request
        assert_failed_change_in_error_response(response[7], input_name="fd69:27cc:fe91::aaaa", record_type="PTR", record_data="existing.ptr.",
                                                   error_messages=["Record \"fd69:27cc:fe91::aaaa\" Already Exists: cannot add an existing record; to update it, issue a DeleteRecordSet then an Add."])

    finally:
        clear_recordset_list(to_delete, client)


def test_ipv6_ptr_recordtype_update_delete_checks(shared_zone_test_context):
    """
    Test all update and delete validations performed on ipv6 PTR records submitted in batch changes
    """
    ok_client = shared_zone_test_context.ok_vinyldns_client
    ip6_reverse_zone = shared_zone_test_context.ip6_reverse_zone

    rs_delete_ipv6 = get_recordset_json(ip6_reverse_zone, "a.a.a.a.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0", "PTR", [{"ptrdname": "delete.ptr."}], 200)
    rs_update_ipv6 = get_recordset_json(ip6_reverse_zone, "2.6.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0", "PTR", [{"ptrdname": "update.ptr."}], 200)
    rs_update_ipv6_fail = get_recordset_json(ip6_reverse_zone, "8.1.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0", "PTR", [{"ptrdname": "failed-update.ptr."}], 200)
    rs_doubly_updated = get_recordset_json(shared_zone_test_context.ip6_reverse_zone, "2.2.1.1.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0", "PTR", [{"ptrdname": "test.com."}], 100)

    batch_change_input = {
        "comments": "this is optional",
        "changes": [
            # valid changes ipv6
            get_change_PTR_json("fd69:27cc:fe91::aaaa", change_type="DeleteRecordSet"),
            get_change_PTR_json("fd69:27cc:fe91::62", ttl=300, ptrdname="has-updated.ptr."),
            get_change_PTR_json("fd69:27cc:fe91::62", change_type="DeleteRecordSet"),

            # input validations failures
            get_change_PTR_json("fd69:27cc:fe91de::ab", change_type="DeleteRecordSet"),
            get_change_PTR_json("fd69:27cc:fe91de::ba", change_type="DeleteRecordSet"),
            get_change_PTR_json("fd69:27cc:fe91de::ba", ttl=29, ptrdname="failed-update$.ptr"),

            # zone discovery failures
            get_change_PTR_json("fedc:ba98:7654::abc", change_type="DeleteRecordSet"),

            # context validation failures
            get_change_PTR_json("fd69:27cc:fe91::60", change_type="DeleteRecordSet"),
            get_change_PTR_json("fd69:27cc:fe91::65", ttl=300, ptrdname="has-updated.ptr."),
            get_change_PTR_json("fd69:27cc:fe91::65", change_type="DeleteRecordSet"),
            get_change_PTR_json("fd69:27cc:fe91::1122", change_type="DeleteRecordSet"),
            get_change_PTR_json("fd69:27cc:fe91::1122"),
            get_change_PTR_json("fd69:27cc:fe91::1122", ttl=350)

        ]
    }

    to_create = [rs_delete_ipv6, rs_update_ipv6, rs_update_ipv6_fail, rs_doubly_updated]
    to_delete = []

    try:
        for rs in to_create:
            create_rs = ok_client.create_recordset(rs, status=202)
            to_delete.append(ok_client.wait_until_recordset_change_status(create_rs, 'Complete'))

        response = ok_client.create_batch_change(batch_change_input, status=400)

        # successful changes
        assert_successful_change_in_error_response(response[0], input_name="fd69:27cc:fe91::aaaa", record_type="PTR", record_data=None, change_type="DeleteRecordSet")
        assert_successful_change_in_error_response(response[1], ttl=300, input_name="fd69:27cc:fe91::62", record_type="PTR", record_data="has-updated.ptr.")
        assert_successful_change_in_error_response(response[2], input_name="fd69:27cc:fe91::62", record_type="PTR", record_data=None, change_type="DeleteRecordSet")

        # successful changes: input_name does not have to be unique
        assert_successful_change_in_error_response(response[11], input_name="fd69:27cc:fe91::1122", record_type="PTR", record_data="test.com.")
        assert_successful_change_in_error_response(response[12], input_name="fd69:27cc:fe91::1122", record_type="PTR", record_data="test.com.", ttl=350)

        # input validations failures: invalid IP, ttl, and record data
        assert_failed_change_in_error_response(response[3], input_name="fd69:27cc:fe91de::ab", record_type="PTR", record_data=None, change_type="DeleteRecordSet",
                                               error_messages=['Invalid IP address: "fd69:27cc:fe91de::ab".'])
        assert_failed_change_in_error_response(response[4], input_name="fd69:27cc:fe91de::ba", record_type="PTR", record_data=None, change_type="DeleteRecordSet",
                                               error_messages=['Invalid IP address: "fd69:27cc:fe91de::ba".'])
        assert_failed_change_in_error_response(response[5], ttl=29, input_name="fd69:27cc:fe91de::ba", record_type="PTR", record_data="failed-update$.ptr.",
                                               error_messages=['Invalid TTL: "29", must be a number between 30 and 2147483647.',
                                                               'Invalid IP address: "fd69:27cc:fe91de::ba".',
                                                               'Invalid domain name: "failed-update$.ptr.", valid domain names must be letters, numbers, and hyphens, joined by dots, and terminated with a dot.'])

        # zone discovery failure
        assert_failed_change_in_error_response(response[6], input_name="fedc:ba98:7654::abc", record_type="PTR", record_data=None, change_type="DeleteRecordSet",
                                               error_messages=["Zone Discovery Failed: zone for \"fedc:ba98:7654::abc\" does not exist in VinylDNS. If zone exists, then it must be created in VinylDNS."])

        # context validation failures: record does not exist, failure on update with double add
        assert_failed_change_in_error_response(response[7], input_name="fd69:27cc:fe91::60", record_type="PTR", record_data=None, change_type="DeleteRecordSet",
                                               error_messages=["Record \"fd69:27cc:fe91::60\" Does Not Exist: cannot delete a record that does not exist."])
        assert_successful_change_in_error_response(response[8], ttl=300, input_name="fd69:27cc:fe91::65", record_type="PTR", record_data="has-updated.ptr.")
        assert_failed_change_in_error_response(response[9], input_name="fd69:27cc:fe91::65", record_type="PTR", record_data=None, change_type="DeleteRecordSet",
                                               error_messages=["Record \"fd69:27cc:fe91::65\" Does Not Exist: cannot delete a record that does not exist."])
        assert_successful_change_in_error_response(response[10], input_name="fd69:27cc:fe91::1122", record_type="PTR", change_type="DeleteRecordSet")


    finally:
        clear_recordset_list(to_delete, ok_client)


def test_txt_recordtype_add_checks(shared_zone_test_context):
    """
    Test all add validations performed on TXT records submitted in batch changes
    """
    client = shared_zone_test_context.ok_vinyldns_client

    existing_txt = get_recordset_json(shared_zone_test_context.ok_zone, "existing-txt", "TXT", [{"text": "test"}], 100)
    existing_cname = get_recordset_json(shared_zone_test_context.ok_zone, "existing-cname", "CNAME", [{"cname": "test."}], 100)

    batch_change_input = {
        "changes": [
            # valid change
            get_change_TXT_json("good-record.ok."),

            # input validation failures
            get_change_TXT_json("bad-ttl-and-invalid-name$.ok.", ttl=29),
            get_change_TXT_json("summed-fail.ok."),
            get_change_TXT_json("summed-fail.ok.", text="test2"),

            # zone discovery failures
            get_change_TXT_json("no.subzone.ok."),
            get_change_TXT_json("no.zone.at.all."),

            # context validation failures
            get_change_CNAME_json("cname-duplicate.ok."),
            get_change_TXT_json("cname-duplicate.ok."),
            get_change_TXT_json("existing-txt.ok."),
            get_change_TXT_json("existing-cname.ok."),
            get_change_TXT_json("user-add-unauthorized.dummy.")
        ]
    }

    to_create = [existing_txt, existing_cname]
    to_delete = []
    try:
        for create_json in to_create:
            create_result = client.create_recordset(create_json, status=202)
            to_delete.append(client.wait_until_recordset_change_status(create_result, 'Complete'))

        response = client.create_batch_change(batch_change_input, status=400)

        # successful changes
        assert_successful_change_in_error_response(response[0], input_name="good-record.ok.", record_type="TXT", record_data="test")

        # ttl, domain name, record data
        assert_failed_change_in_error_response(response[1], input_name="bad-ttl-and-invalid-name$.ok.", ttl=29, record_type="TXT", record_data="test",
                                               error_messages=['Invalid TTL: "29", must be a number between 30 and 2147483647.',
                                                               'Invalid domain name: "bad-ttl-and-invalid-name$.ok.", '
                                                               'valid domain names must be letters, numbers, and hyphens, joined by dots, and terminated with a dot.'])
        assert_failed_change_in_error_response(response[2], input_name="summed-fail.ok.", record_type="TXT", record_data="test",
                                               error_messages=['Record Name "summed-fail.ok." Not Unique In Batch Change: cannot have multiple "TXT" records with the same name.'])
        assert_failed_change_in_error_response(response[3], input_name="summed-fail.ok.", record_type="TXT", record_data="test2",
                                               error_messages=['Record Name "summed-fail.ok." Not Unique In Batch Change: cannot have multiple "TXT" records with the same name.'])

        # zone discovery failures
        assert_failed_change_in_error_response(response[4], input_name="no.subzone.ok.", record_type="TXT", record_data="test",
                                               error_messages=['Zone Discovery Failed: zone for "no.subzone.ok." does not exist in VinylDNS. If zone exists, then it must be created in VinylDNS.'])
        assert_failed_change_in_error_response(response[5], input_name="no.zone.at.all.", record_type="TXT", record_data="test",
                                               error_messages=['Zone Discovery Failed: zone for "no.zone.at.all." does not exist in VinylDNS. If zone exists, then it must be created in VinylDNS.'])

        # context validations: cname duplicate
        assert_failed_change_in_error_response(response[6], input_name="cname-duplicate.ok.", record_type="CNAME", record_data="test.com.",
                                               error_messages=["Record Name \"cname-duplicate.ok.\" Not Unique In Batch Change: cannot have multiple \"CNAME\" records with the same name."])

        # context validations: conflicting recordsets, unauthorized error
        assert_failed_change_in_error_response(response[8], input_name="existing-txt.ok.", record_type="TXT", record_data="test",
                                               error_messages=["Record \"existing-txt.ok.\" Already Exists: cannot add an existing record; to update it, issue a DeleteRecordSet then an Add."])
        assert_failed_change_in_error_response(response[9], input_name="existing-cname.ok.", record_type="TXT", record_data="test",
                                               error_messages=["CNAME Conflict: CNAME record names must be unique. Existing record with name \"existing-cname.ok.\" and type \"CNAME\" conflicts with this record."])
        assert_failed_change_in_error_response(response[10], input_name="user-add-unauthorized.dummy.", record_type="TXT", record_data="test",
                                               error_messages=["User \"ok\" is not authorized."])

    finally:
        clear_recordset_list(to_delete, client)


def test_txt_recordtype_update_delete_checks(shared_zone_test_context):
    """
    Test all update and delete validations performed on TXT records submitted in batch changes
    """
    ok_client = shared_zone_test_context.ok_vinyldns_client
    dummy_client = shared_zone_test_context.dummy_vinyldns_client
    ok_zone = shared_zone_test_context.ok_zone
    dummy_zone = shared_zone_test_context.dummy_zone

    rs_delete_ok = get_recordset_json(ok_zone, "delete", "TXT", [{"text": "test"}], 200)
    rs_update_ok = get_recordset_json(ok_zone, "update", "TXT", [{"text": "test"}], 200)
    rs_delete_dummy = get_recordset_json(dummy_zone, "delete-unauthorized", "TXT", [{"text": "test"}], 200)
    rs_update_dummy = get_recordset_json(dummy_zone, "update-unauthorized", "TXT", [{"text": "test"}], 200)

    batch_change_input = {
        "comments": "this is optional",
        "changes": [
            # valid changes
            get_change_TXT_json("delete.ok.", change_type="DeleteRecordSet"),
            get_change_TXT_json("update.ok.", change_type="DeleteRecordSet"),
            get_change_TXT_json("update.ok.", ttl=300),

            # input validations failures
            get_change_TXT_json("invalid-name$.ok.", change_type="DeleteRecordSet"),
            get_change_TXT_json("delete.ok.", ttl=29, text="bad-ttl"),

            # zone discovery failures
            get_change_TXT_json("no.zone.at.all.", change_type="DeleteRecordSet"),

            # context validation failures
            get_change_TXT_json("delete-nonexistent.ok.", change_type="DeleteRecordSet"),
            get_change_TXT_json("update-nonexistent.ok.", change_type="DeleteRecordSet"),
            get_change_TXT_json("update-nonexistent.ok.", text="test"),
            get_change_TXT_json("delete-unauthorized.dummy.", change_type="DeleteRecordSet"),
            get_change_TXT_json("update-unauthorized.dummy.", text="test"),
            get_change_TXT_json("update-unauthorized.dummy.", change_type="DeleteRecordSet")
        ]
    }

    to_create = [rs_delete_ok, rs_update_ok, rs_delete_dummy, rs_update_dummy]
    to_delete = []

    try:
        for rs in to_create:
            if rs['zoneId'] == dummy_zone['id']:
                create_client = dummy_client
            else:
                create_client = ok_client

            create_rs = create_client.create_recordset(rs, status=202)
            to_delete.append(create_client.wait_until_recordset_change_status(create_rs, 'Complete'))

        # Confirm that record set doesn't already exist
        ok_client.get_recordset(ok_zone['id'], 'delete-nonexistent', status=404)

        response = ok_client.create_batch_change(batch_change_input, status=400)

        # successful changes
        assert_successful_change_in_error_response(response[0], input_name="delete.ok.", record_type="TXT", record_data=None, change_type="DeleteRecordSet")
        assert_successful_change_in_error_response(response[1], input_name="update.ok.", record_type="TXT", record_data=None, change_type="DeleteRecordSet")
        assert_successful_change_in_error_response(response[2], ttl=300, input_name="update.ok.", record_type="TXT", record_data="test")

        # input validations failures: invalid input name, reverse zone error, invalid ttl
        assert_failed_change_in_error_response(response[3], input_name="invalid-name$.ok.", record_type="TXT", record_data="test", change_type="DeleteRecordSet",
                                               error_messages=['Invalid domain name: "invalid-name$.ok.", valid domain names must be letters, numbers, and hyphens, joined by dots, and terminated with a dot.'])
        assert_failed_change_in_error_response(response[4], input_name="delete.ok.", ttl=29, record_type="TXT", record_data="bad-ttl",
                                               error_messages=['Invalid TTL: "29", must be a number between 30 and 2147483647.'])

        # zone discovery failure
        assert_failed_change_in_error_response(response[5], input_name="no.zone.at.all.", record_type="TXT", record_data=None, change_type="DeleteRecordSet",
                                               error_messages=["Zone Discovery Failed: zone for \"no.zone.at.all.\" does not exist in VinylDNS. If zone exists, then it must be created in VinylDNS."])

        # context validation failures: record does not exist, not authorized
        assert_failed_change_in_error_response(response[6], input_name="delete-nonexistent.ok.", record_type="TXT", record_data=None, change_type="DeleteRecordSet",
                                               error_messages=["Record \"delete-nonexistent.ok.\" Does Not Exist: cannot delete a record that does not exist."])
        assert_failed_change_in_error_response(response[7], input_name="update-nonexistent.ok.", record_type="TXT", record_data=None, change_type="DeleteRecordSet",
                                               error_messages=["Record \"update-nonexistent.ok.\" Does Not Exist: cannot delete a record that does not exist."])
        assert_successful_change_in_error_response(response[8], input_name="update-nonexistent.ok.", record_type="TXT", record_data="test",)
        assert_failed_change_in_error_response(response[9], input_name="delete-unauthorized.dummy.", record_type="TXT", record_data=None, change_type="DeleteRecordSet",
                                               error_messages=["User \"ok\" is not authorized."])
        assert_failed_change_in_error_response(response[10], input_name="update-unauthorized.dummy.", record_type="TXT", record_data="test",
                                               error_messages=["User \"ok\" is not authorized."])
        assert_failed_change_in_error_response(response[11], input_name="update-unauthorized.dummy.", record_type="TXT", record_data=None, change_type="DeleteRecordSet",
                                               error_messages=["User \"ok\" is not authorized."])

    finally:
        # Clean up updates
        dummy_deletes = [rs for rs in to_delete if rs['zone']['id'] == dummy_zone['id']]
        ok_deletes = [rs for rs in to_delete if rs['zone']['id'] != dummy_zone['id']]
        clear_recordset_list(dummy_deletes, dummy_client)
        clear_recordset_list(ok_deletes, ok_client)


def test_mx_recordtype_add_checks(shared_zone_test_context):
    """
    Test all add validations performed on MX records submitted in batch changes
    """
    client = shared_zone_test_context.ok_vinyldns_client

    existing_mx = get_recordset_json(shared_zone_test_context.ok_zone, "existing-mx", "MX", [{"preference": 1, "exchange": "foo.bar."}], 100)
    existing_cname = get_recordset_json(shared_zone_test_context.ok_zone, "existing-cname", "CNAME", [{"cname": "test."}], 100)

    batch_change_input = {
        "changes": [
            # valid change
            get_change_MX_json("good-record.ok."),

            # input validation failures
            get_change_MX_json("bad-ttl-and-invalid-name$.ok.", ttl=29),
            get_change_MX_json("bad-exchange.ok.", exchange="foo$.bar."),
            get_change_MX_json("mx.2.0.192.in-addr.arpa."),

            # zone discovery failures
            get_change_MX_json("no.subzone.ok."),
            get_change_MX_json("no.zone.at.all."),

            # context validation failures
            get_change_CNAME_json("cname-duplicate.ok."),
            get_change_MX_json("cname-duplicate.ok."),
            get_change_MX_json("existing-mx.ok."),
            get_change_MX_json("existing-cname.ok."),
            get_change_MX_json("user-add-unauthorized.dummy.")
        ]
    }

    to_create = [existing_mx, existing_cname]
    to_delete = []
    try:
        for create_json in to_create:
            create_result = client.create_recordset(create_json, status=202)
            to_delete.append(client.wait_until_recordset_change_status(create_result, 'Complete'))

        response = client.create_batch_change(batch_change_input, status=400)

        # successful changes
        assert_successful_change_in_error_response(response[0], input_name="good-record.ok.", record_type="MX", record_data={"preference": 1, "exchange": "foo.bar."})

        # ttl, domain name, record data
        assert_failed_change_in_error_response(response[1], input_name="bad-ttl-and-invalid-name$.ok.", ttl=29, record_type="MX", record_data={"preference": 1, "exchange": "foo.bar."},
                                               error_messages=['Invalid TTL: "29", must be a number between 30 and 2147483647.',
                                                               'Invalid domain name: "bad-ttl-and-invalid-name$.ok.", '
                                                               'valid domain names must be letters, numbers, and hyphens, joined by dots, and terminated with a dot.'])
        assert_failed_change_in_error_response(response[2], input_name="bad-exchange.ok.", record_type="MX", record_data={"preference": 1, "exchange": "foo$.bar."},
                                               error_messages=['Invalid domain name: "foo$.bar.", valid domain names must be letters, numbers, and hyphens, joined by dots, and terminated with a dot.'])
        assert_failed_change_in_error_response(response[3], input_name="mx.2.0.192.in-addr.arpa.", record_type="MX", record_data={"preference": 1, "exchange": "foo.bar."},
                                       error_messages=['Invalid Record Type In Reverse Zone: record with name "mx.2.0.192.in-addr.arpa." and type "MX" is not allowed in a reverse zone.'])

        # zone discovery failures
        assert_failed_change_in_error_response(response[4], input_name="no.subzone.ok.", record_type="MX", record_data={"preference": 1, "exchange": "foo.bar."},
                                               error_messages=['Zone Discovery Failed: zone for "no.subzone.ok." does not exist in VinylDNS. If zone exists, then it must be created in VinylDNS.'])
        assert_failed_change_in_error_response(response[5], input_name="no.zone.at.all.", record_type="MX", record_data={"preference": 1, "exchange": "foo.bar."},
                                               error_messages=['Zone Discovery Failed: zone for "no.zone.at.all." does not exist in VinylDNS. If zone exists, then it must be created in VinylDNS.'])

        # context validations: cname duplicate
        assert_failed_change_in_error_response(response[6], input_name="cname-duplicate.ok.", record_type="CNAME", record_data="test.com.",
                                               error_messages=["Record Name \"cname-duplicate.ok.\" Not Unique In Batch Change: cannot have multiple \"CNAME\" records with the same name."])

        # context validations: conflicting recordsets, unauthorized error
        assert_failed_change_in_error_response(response[8], input_name="existing-mx.ok.", record_type="MX", record_data={"preference": 1, "exchange": "foo.bar."},
                                               error_messages=["Record \"existing-mx.ok.\" Already Exists: cannot add an existing record; to update it, issue a DeleteRecordSet then an Add."])
        assert_failed_change_in_error_response(response[9], input_name="existing-cname.ok.", record_type="MX", record_data={"preference": 1, "exchange": "foo.bar."},
                                               error_messages=["CNAME Conflict: CNAME record names must be unique. Existing record with name \"existing-cname.ok.\" and type \"CNAME\" conflicts with this record."])
        assert_failed_change_in_error_response(response[10], input_name="user-add-unauthorized.dummy.", record_type="MX", record_data={"preference": 1, "exchange": "foo.bar."},
                                               error_messages=["User \"ok\" is not authorized."])

    finally:
        clear_recordset_list(to_delete, client)


def test_mx_recordtype_update_delete_checks(shared_zone_test_context):
    """
    Test all update and delete validations performed on MX records submitted in batch changes
    """
    ok_client = shared_zone_test_context.ok_vinyldns_client
    dummy_client = shared_zone_test_context.dummy_vinyldns_client
    ok_zone = shared_zone_test_context.ok_zone
    dummy_zone = shared_zone_test_context.dummy_zone

    rs_delete_ok = get_recordset_json(ok_zone, "delete", "MX", [{"preference": 1, "exchange": "foo.bar."}], 200)
    rs_update_ok = get_recordset_json(ok_zone, "update", "MX", [{"preference": 1, "exchange": "foo.bar."}], 200)
    rs_delete_dummy = get_recordset_json(dummy_zone, "delete-unauthorized", "MX", [{"preference": 1, "exchange": "foo.bar."}], 200)
    rs_update_dummy = get_recordset_json(dummy_zone, "update-unauthorized", "MX", [{"preference": 1, "exchange": "foo.bar."}], 200)

    batch_change_input = {
        "comments": "this is optional",
        "changes": [
            # valid changes
            get_change_MX_json("delete.ok.", change_type="DeleteRecordSet"),
            get_change_MX_json("update.ok.", change_type="DeleteRecordSet"),
            get_change_MX_json("update.ok.", ttl=300),

            # input validations failures
            get_change_MX_json("invalid-name$.ok.", change_type="DeleteRecordSet"),
            get_change_MX_json("delete.ok.", ttl=29),
            get_change_MX_json("bad-exchange.ok.", exchange="foo$.bar."),
            get_change_MX_json("mx.2.0.192.in-addr.arpa."),

            # zone discovery failures
            get_change_MX_json("no.zone.at.all.", change_type="DeleteRecordSet"),

            # context validation failures
            get_change_MX_json("delete-nonexistent.ok.", change_type="DeleteRecordSet"),
            get_change_MX_json("update-nonexistent.ok.", change_type="DeleteRecordSet"),
            get_change_MX_json("update-nonexistent.ok.", preference=1000, exchange="foo.bar."),
            get_change_MX_json("delete-unauthorized.dummy.", change_type="DeleteRecordSet"),
            get_change_MX_json("update-unauthorized.dummy.", preference= 1000, exchange= "foo.bar."),
            get_change_MX_json("update-unauthorized.dummy.", change_type="DeleteRecordSet")
        ]
    }

    to_create = [rs_delete_ok, rs_update_ok, rs_delete_dummy, rs_update_dummy]
    to_delete = []

    try:
        for rs in to_create:
            if rs['zoneId'] == dummy_zone['id']:
                create_client = dummy_client
            else:
                create_client = ok_client

            create_rs = create_client.create_recordset(rs, status=202)
            to_delete.append(create_client.wait_until_recordset_change_status(create_rs, 'Complete'))

        # Confirm that record set doesn't already exist
        ok_client.get_recordset(ok_zone['id'], 'delete-nonexistent', status=404)

        response = ok_client.create_batch_change(batch_change_input, status=400)

        # successful changes
        assert_successful_change_in_error_response(response[0], input_name="delete.ok.", record_type="MX", record_data=None, change_type="DeleteRecordSet")
        assert_successful_change_in_error_response(response[1], input_name="update.ok.", record_type="MX", record_data=None, change_type="DeleteRecordSet")
        assert_successful_change_in_error_response(response[2], ttl=300, input_name="update.ok.", record_type="MX", record_data={"preference": 1, "exchange": "foo.bar."})

        # input validations failures: invalid input name, reverse zone error, invalid ttl
        assert_failed_change_in_error_response(response[3], input_name="invalid-name$.ok.", record_type="MX", record_data={"preference": 1, "exchange": "foo.bar."}, change_type="DeleteRecordSet",
                                               error_messages=['Invalid domain name: "invalid-name$.ok.", valid domain names must be letters, numbers, and hyphens, joined by dots, and terminated with a dot.'])
        assert_failed_change_in_error_response(response[4], input_name="delete.ok.", ttl=29, record_type="MX", record_data={"preference": 1, "exchange": "foo.bar."},
                                               error_messages=['Invalid TTL: "29", must be a number between 30 and 2147483647.'])
        assert_failed_change_in_error_response(response[5], input_name="bad-exchange.ok.", record_type="MX", record_data={"preference": 1, "exchange": "foo$.bar."},
                                               error_messages=['Invalid domain name: "foo$.bar.", valid domain names must be letters, numbers, and hyphens, joined by dots, and terminated with a dot.'])
        assert_failed_change_in_error_response(response[6], input_name="mx.2.0.192.in-addr.arpa.", record_type="MX", record_data={"preference": 1, "exchange": "foo.bar."},
                                               error_messages=['Invalid Record Type In Reverse Zone: record with name "mx.2.0.192.in-addr.arpa." and type "MX" is not allowed in a reverse zone.'])

        # zone discovery failure
        assert_failed_change_in_error_response(response[7], input_name="no.zone.at.all.", record_type="MX", record_data=None, change_type="DeleteRecordSet",
                                               error_messages=["Zone Discovery Failed: zone for \"no.zone.at.all.\" does not exist in VinylDNS. If zone exists, then it must be created in VinylDNS."])

        # context validation failures: record does not exist, not authorized
        assert_failed_change_in_error_response(response[8], input_name="delete-nonexistent.ok.", record_type="MX", record_data=None, change_type="DeleteRecordSet",
                                               error_messages=["Record \"delete-nonexistent.ok.\" Does Not Exist: cannot delete a record that does not exist."])
        assert_failed_change_in_error_response(response[9], input_name="update-nonexistent.ok.", record_type="MX", record_data=None, change_type="DeleteRecordSet",
                                               error_messages=["Record \"update-nonexistent.ok.\" Does Not Exist: cannot delete a record that does not exist."])
        assert_successful_change_in_error_response(response[10], input_name="update-nonexistent.ok.", record_type="MX", record_data={"preference": 1000, "exchange": "foo.bar."},)
        assert_failed_change_in_error_response(response[11], input_name="delete-unauthorized.dummy.", record_type="MX", record_data=None, change_type="DeleteRecordSet",
                                               error_messages=["User \"ok\" is not authorized."])
        assert_failed_change_in_error_response(response[12], input_name="update-unauthorized.dummy.", record_type="MX", record_data={"preference": 1000, "exchange": "foo.bar."},
                                               error_messages=["User \"ok\" is not authorized."])
        assert_failed_change_in_error_response(response[13], input_name="update-unauthorized.dummy.", record_type="MX", record_data=None, change_type="DeleteRecordSet",
                                               error_messages=["User \"ok\" is not authorized."])

    finally:
        # Clean up updates
        dummy_deletes = [rs for rs in to_delete if rs['zone']['id'] == dummy_zone['id']]
        ok_deletes = [rs for rs in to_delete if rs['zone']['id'] != dummy_zone['id']]
        clear_recordset_list(dummy_deletes, dummy_client)
        clear_recordset_list(ok_deletes, ok_client)


def test_user_validation_ownership(shared_zone_test_context):
    """
    Confirm that test users cannot add/edit/delete records in non-test zones (via zone admin group)
    """
    client = shared_zone_test_context.shared_zone_vinyldns_client
    batch_change_input = {
        "changes": [
            get_change_A_AAAA_json("add-test-batch.non.test.shared.", address="1.1.1.1"),
            get_change_A_AAAA_json("update-test-batch.non.test.shared.", change_type="DeleteRecordSet"),
            get_change_A_AAAA_json("update-test-batch.non.test.shared.", address="1.1.1.1"),
            get_change_A_AAAA_json("delete-test-batch.non.test.shared.", change_type="DeleteRecordSet"),

            get_change_A_AAAA_json("add-test-batch.shared.", address="1.1.1.1"),
            get_change_A_AAAA_json("update-test-batch.shared.", change_type="DeleteRecordSet"),
            get_change_A_AAAA_json("update-test-batch.shared.", address="1.1.1.1"),
            get_change_A_AAAA_json("delete-test-batch.shared.", change_type="DeleteRecordSet"),
        ]
    }

    response = client.create_batch_change(batch_change_input, status=400)
    assert_failed_change_in_error_response(response[0], input_name="add-test-batch.non.test.shared.", record_data="1.1.1.1",
                                           error_messages=["User \"sharedZoneUser\" is not authorized."])
    assert_failed_change_in_error_response(response[1], input_name="update-test-batch.non.test.shared.", change_type="DeleteRecordSet",
                                           error_messages=["User \"sharedZoneUser\" is not authorized."])
    assert_failed_change_in_error_response(response[2], input_name="update-test-batch.non.test.shared.", record_data="1.1.1.1",
                                           error_messages=["User \"sharedZoneUser\" is not authorized."])
    assert_failed_change_in_error_response(response[3], input_name="delete-test-batch.non.test.shared.", change_type="DeleteRecordSet",
                                           error_messages=["User \"sharedZoneUser\" is not authorized."])

    assert_successful_change_in_error_response(response[4], input_name="add-test-batch.shared.")
    assert_successful_change_in_error_response(response[5], input_name="update-test-batch.shared.", change_type="DeleteRecordSet")
    assert_successful_change_in_error_response(response[6], input_name="update-test-batch.shared.")
    assert_successful_change_in_error_response(response[7], input_name="delete-test-batch.shared.", change_type="DeleteRecordSet")



def test_user_validation_shared(shared_zone_test_context):
    """
    Confirm that test users cannot add/edit/delete records in non-test zones (via shared access)
    """
    client = shared_zone_test_context.ok_vinyldns_client
    batch_change_input = {
        "changes": [
            get_change_A_AAAA_json("add-test-batch.non.test.shared.", address="1.1.1.1"),
            get_change_A_AAAA_json("update-test-batch.non.test.shared.", change_type="DeleteRecordSet"),
            get_change_A_AAAA_json("update-test-batch.non.test.shared.", address="1.1.1.1"),
            get_change_A_AAAA_json("delete-test-batch.non.test.shared.", change_type="DeleteRecordSet")
        ]
    }

    response = client.create_batch_change(batch_change_input, status=400)
    assert_failed_change_in_error_response(response[0], input_name="add-test-batch.non.test.shared.", record_data="1.1.1.1",
                                           error_messages=["User \"ok\" is not authorized."])
    assert_failed_change_in_error_response(response[1], input_name="update-test-batch.non.test.shared.", change_type="DeleteRecordSet",
                                           error_messages=["User \"ok\" is not authorized."])
    assert_failed_change_in_error_response(response[2], input_name="update-test-batch.non.test.shared.", record_data="1.1.1.1",
                                           error_messages=["User \"ok\" is not authorized."])
    assert_failed_change_in_error_response(response[3], input_name="delete-test-batch.non.test.shared.", change_type="DeleteRecordSet",
                                           error_messages=["User \"ok\" is not authorized."])

def test_create_batch_change_does_not_save_owner_group_id_for_non_shared_zone(shared_zone_test_context):
    """
    Test successfully creating a batch change with owner group ID doesn't save value for records in non-shared zone
    """
    ok_client = shared_zone_test_context.ok_vinyldns_client
    ok_zone = shared_zone_test_context.ok_zone
    ok_group = shared_zone_test_context.ok_group

    update_rs = get_recordset_json(ok_zone, "update", "A", [{"address": "127.0.0.1"}], 300)

    batch_change_input = {
        "changes": [
            get_change_A_AAAA_json("no-owner-group-id.ok.", address="4.3.2.1"),
            get_change_A_AAAA_json("update.ok.", address="1.2.3.4"),
            get_change_A_AAAA_json("update.ok.", change_type="DeleteRecordSet")
        ],
        "ownerGroupId": ok_group['id']
    }
    to_delete = []

    try:
        create_result = ok_client.create_recordset(update_rs, status=202)
        to_delete.append(ok_client.wait_until_recordset_change_status(create_result, 'Complete'))

        result = ok_client.create_batch_change(batch_change_input, status=202)
        completed_batch = ok_client.wait_until_batch_change_completed(result)
        to_delete = [(change['zoneId'], change['recordSetId']) for change in completed_batch['changes']]

        assert_change_success_response_values(result['changes'], zone=ok_zone, index=0, record_name="no-owner-group-id",
                                              input_name="no-owner-group-id.ok.", record_data="4.3.2.1")
        assert_change_success_response_values(result['changes'], zone=ok_zone, index=1, record_name="update",
                                              input_name="update.ok.", record_data="1.2.3.4")
        assert_change_success_response_values(result['changes'], zone=ok_zone, index=2, record_name="update",
                                              input_name="update.ok.", change_type="DeleteRecordSet", record_data=None)

        for (zoneId, recordSetId) in to_delete:
            get_recordset = ok_client.get_recordset(zoneId, recordSetId, status=200)
            assert_that(get_recordset['recordSet'], is_not(has_key('ownerGroupId')))

    finally:
        clear_zoneid_rsid_tuple_list(to_delete, ok_client)

def test_create_batch_change_for_shared_zone_updates_recordset_owner_group_id_succeeds(shared_zone_test_context):
    """
    Test successfully creating a batch change with owner group ID in shared zone succeeds and sets owner group ID
    on creates and updates (if existing value does not exist)
    """
    shared_client = shared_zone_test_context.shared_zone_vinyldns_client
    shared_zone = shared_zone_test_context.shared_zone

    update_rs = get_recordset_json(shared_zone, "update", "A", [{"address": "127.0.0.1"}], 300)

    batch_change_input = {
        "changes": [
            get_change_A_AAAA_json("no-owner-group-id.shared.", address="4.3.2.1"),
            get_change_A_AAAA_json("update.shared.", address="1.2.3.4"),
            get_change_A_AAAA_json("update.shared.", change_type="DeleteRecordSet")
        ],
        "ownerGroupId": "shared-zone-group"
    }
    to_delete = []

    try:
        create_result = shared_client.create_recordset(update_rs, status=202)
        to_delete.append(shared_client.wait_until_recordset_change_status(create_result, 'Complete'))

        create_result = shared_client.get_recordset(create_result['recordSet']['zoneId'], create_result['recordSet']['id'], status=200)
        assert_that(create_result['recordSet'], is_not(has_key('ownerGroupId')))

        result = shared_client.create_batch_change(batch_change_input, status=202)
        completed_batch = shared_client.wait_until_batch_change_completed(result)
        to_delete = [(change['zoneId'], change['recordSetId']) for change in completed_batch['changes']]

        assert_that(result['ownerGroupId'], is_('shared-zone-group'))
        assert_change_success_response_values(result['changes'], zone=shared_zone, index=0, record_name="no-owner-group-id",
                                              input_name="no-owner-group-id.shared.", record_data="4.3.2.1")
        assert_change_success_response_values(result['changes'], zone=shared_zone, index=1, record_name="update",
                                              input_name="update.shared.", record_data="1.2.3.4")
        assert_change_success_response_values(result['changes'], zone=shared_zone, index=2, record_name="update",
                                              input_name="update.shared.", change_type="DeleteRecordSet", record_data=None)

        for (zoneId, recordSetId) in to_delete:
            get_recordset = shared_client.get_recordset(zoneId, recordSetId, status=200)
            assert_that(get_recordset['recordSet']['ownerGroupId'], is_(batch_change_input['ownerGroupId']))

    finally:
        clear_zoneid_rsid_tuple_list(to_delete, shared_client)

def test_create_batch_change_for_shared_zone_with_invalid_owner_group_id_fails(shared_zone_test_context):
    """
    Test successfully creating a batch change with owner group ID saves value for records in shared zone
    """
    shared_client = shared_zone_test_context.shared_zone_vinyldns_client

    batch_change_input = {
        "changes": [
            get_change_A_AAAA_json("no-owner-group-id.shared.", address="4.3.2.1")
        ],
        "ownerGroupId": "non-existent-owner-group-id"
    }

    errors = shared_client.create_batch_change(batch_change_input, status=400)
    assert_that(errors, is_('Group with ID "non-existent-owner-group-id" was not found'))

def test_create_batch_change_for_shared_zone_with_unauthorized_owner_group_id_fails(shared_zone_test_context):
    """
    Test successfully creating a batch change with owner group ID saves value for records in shared zone
    """
    shared_client = shared_zone_test_context.shared_zone_vinyldns_client
    ok_group = shared_zone_test_context.ok_group

    batch_change_input = {
        "changes": [
            get_change_A_AAAA_json("no-owner-group-id.shared.", address="4.3.2.1")
        ],
        "ownerGroupId": ok_group['id']
    }

    errors = shared_client.create_batch_change(batch_change_input, status=403)
    assert_that(errors, is_('User "sharedZoneUser" must be a member of group ' + ok_group['id'] + ' to apply this group to batch changes'))
