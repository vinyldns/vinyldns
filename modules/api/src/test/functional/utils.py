import json
import traceback
import uuid

import dns.query
import dns.tsigkeyring
import dns.update
from dns.resolver import *
from hamcrest import *

from vinyldns_context import VinylDNSTestContext


def verify_recordset(actual, expected):
    """
    Runs basic assertions on the recordset to ensure that actual matches the expected
    """
    assert_that(actual["name"], is_(expected["name"]))
    assert_that(actual["zoneId"], is_(expected["zoneId"]))
    assert_that(actual["type"], is_(expected["type"]))
    assert_that(actual["ttl"], is_(expected["ttl"]))
    assert_that(actual, has_key("created"))
    assert_that(actual["status"], is_not(none()))
    assert_that(actual["id"], is_not(none()))
    actual_records = [json.dumps(x) for x in actual["records"]]
    expected_records = [json.dumps(x) for x in expected["records"]]
    for expected_record in expected_records:
        assert_that(actual_records, has_item(expected_record))


def gen_zone():
    """
    Generates a random zone
    """
    return {
        "name": str(uuid.uuid4()) + ".",
        "email": "test@test.com",
        "adminGroupId": "test-group-id"
    }


def verify_acl_rule_is_present_once(rule, acl):
    def match(acl_rule):
        # remove displayName if it exists (allows for aclRule and aclRuleInfo comparison)
        acl_rule.pop("displayName", None)
        return acl_rule == rule

    matches = list(filter(match, acl["rules"]))
    assert_that(matches, has_length(1), "Did not find exactly one match for acl rule")


def verify_acl_rule_is_not_present(rule, acl):
    def match(acl_rule):
        return acl_rule != rule

    matches = list(filter(match, acl["rules"]))
    assert_that(matches, has_length(len(acl["rules"])), "ACL Rule was found but should not have been present")


def rdata(dns_answers):
    """
    Converts the answers from a dns python query to a sequence of string containing the rdata
    :param dns_answers: the results of running the dns_resolve utility function
    :return: a sequence containing the rdata sections for each record in the answers
    """
    rdata_strings = []
    if dns_answers:
        rdata_strings = [x["rdata"] for x in dns_answers]

    return rdata_strings


def dns_server_port(zone):
    """
    Parses the server and port based on the connection info on the zone
    :param zone: a populated zone model
    :return: a tuple (host, port), port is an int
    """
    name_server = zone["connection"]["primaryServer"]
    name_server_port = 53
    if VinylDNSTestContext.resolver_ip is not None:
        name_server = VinylDNSTestContext.resolver_ip

    if ":" in name_server:
        parts = name_server.split(":")
        name_server = parts[0]
        name_server_port = int(parts[1])

    return name_server, name_server_port


def dns_do_command(zone, record_name, record_type, command, ttl=0, rdata=""):
    """
    Helper for dns add, update, delete
    """
    # Get the algorithm name from the DNS library (vinylDNS uses "-" in the name and dnspython uses "_")
    algo_name = getattr(dns.tsig, VinylDNSTestContext.dns_key_algo.replace("-", "_"))
    keyring = dns.tsigkeyring.from_text({
        zone["connection"]["keyName"]: (algo_name, VinylDNSTestContext.dns_key)
    })

    (name_server, name_server_port) = dns_server_port(zone)
    fqdn = record_name + "." + zone["name"]
    update = dns.update.Update(zone["name"], keyring=keyring)

    if command == "add":
        update.add(fqdn, ttl, record_type, rdata)
    elif command == "update":
        update.replace(fqdn, ttl, record_type, rdata)
    elif command == "delete":
        update.delete(fqdn, record_type)

    response = dns.query.udp(update, name_server, port=name_server_port, ignore_unexpected=True)
    return response


def dns_update(zone, record_name, ttl, record_type, rdata):
    """
    Issues a DNS update to the backend server
    :param zone: a populated zone model
    :param record_name: the name of the record to update
    :param ttl: the ttl value of the record
    :param record_type: the type of record being updated
    :param rdata: the rdata string
    :return:
    """
    return dns_do_command(zone, record_name, record_type, "update", ttl, rdata)


def dns_delete(zone, record_name, record_type):
    """
    Issues a DNS delete to the backend server
    :param zone: a populated zone model
    :param record_name: the name of the record to delete
    :param record_type: the type of record being delete
    :return:
    """
    return dns_do_command(zone, record_name, record_type, "delete")


def dns_add(zone, record_name, ttl, record_type, rdata):
    """
    Issues a DNS update to the backend server
    :param zone: a populated zone model
    :param record_name: the name of the record to add
    :param ttl: the ttl value of the record
    :param record_type: the type of record being added
    :param rdata: the rdata string
    :return:
    """
    return dns_do_command(zone, record_name, record_type, "add", ttl, rdata)


def dns_resolve(zone, record_name, record_type):
    """
    Performs a dns query to find the record name and type against the zone
    :param zone:  a populated zone model
    :param record_name:  the name of the record to lookup
    :param record_type:  the type of record to lookup
    :return: An array of dictionaries, each dict containing fields rdata, type, name, ttl, dclass
    """
    vinyldns_resolver = dns.resolver.Resolver(configure=False)

    name_server, name_server_port = dns_server_port(zone)

    vinyldns_resolver.nameservers = [name_server]
    vinyldns_resolver.port = name_server_port
    vinyldns_resolver.domain = zone["name"]

    fqdn = record_name + "." + vinyldns_resolver.domain
    if record_name == vinyldns_resolver.domain:
        # assert that we are looking up the zone name / @ symbol
        fqdn = vinyldns_resolver.domain

    try:
        answers = vinyldns_resolver.resolve(fqdn, record_type)
    except NXDOMAIN:
        print("query returned NXDOMAIN")
        answers = []
    except dns.resolver.NoAnswer:
        print("query returned NoAnswer")
        answers = []

    if answers:
        # dns python is goofy, looks like we have to parse text
        # each record in the rrset is delimited by a \n
        records = str(answers.rrset).split("\n")

        # for each record, we have exactly 4 fields in order: 1 record name; 2 TTL; 3 DCLASS; 4 TYPE; 5 RDATA
        # construct a simple dictionary based on that split
        return [parse_record(x) for x in records]
    else:
        return []


def parse_record(record_string):
    # for each record, we have exactly 4 fields in order: 1 record name; 2 TTL; 3 DCLASS; 4 TYPE; 5 RDATA
    parts = record_string.split(" ")

    # any parts over 4 have to be kept together
    offset = record_string.find(parts[3]) + len(parts[3]) + 1
    length = len(record_string) - offset
    record_data = record_string[offset:offset + length]

    record = {
        "name": parts[0],
        "ttl": int(str(parts[1])),
        "dclass": parts[2],
        "type": parts[3],
        "rdata": record_data
    }

    return record


def generate_acl_rule(access_level, **kw):
    acl_rule = {
        "accessLevel": access_level,
        "description": "some_test_rule"
    }
    if "userId" in kw:
        acl_rule["userId"] = kw["userId"]
    if "groupId" in kw:
        acl_rule["groupId"] = kw["groupId"]
    if "recordTypes" in kw:
        acl_rule["recordTypes"] = kw["recordTypes"]
    if "recordMask" in kw:
        acl_rule["recordMask"] = kw["recordMask"]

    return acl_rule


def add_rules_to_zone(zone, new_rules):
    import copy

    updated_zone = copy.deepcopy(zone)
    updated_rules = updated_zone["acl"]["rules"]
    rules_to_add = [x for x in new_rules if x not in updated_rules]
    updated_rules.extend(rules_to_add)
    updated_zone["acl"]["rules"] = updated_rules
    return updated_zone


def remove_rules_from_zone(zone, deleted_rules):
    import copy

    updated_zone = copy.deepcopy(zone)
    existing_rules = updated_zone["acl"]["rules"]
    trimmed_rules = [x for x in deleted_rules if x in existing_rules]
    updated_zone["acl"]["rules"] = trimmed_rules

    return updated_zone


def add_ok_acl_rules(test_context, rules):
    updated_zone = add_rules_to_zone(test_context.ok_zone, rules)
    update_change = test_context.ok_vinyldns_client.update_zone(updated_zone, status=202)
    test_context.ok_vinyldns_client.wait_until_zone_change_status_synced(update_change)


def add_shared_zone_acl_rules(test_context, rules):
    updated_zone = add_rules_to_zone(test_context.shared_zone, rules)
    update_change = test_context.shared_zone_vinyldns_client.update_zone(updated_zone, status=202)
    test_context.shared_zone_vinyldns_client.wait_until_zone_change_status_synced(update_change)


def add_ip4_acl_rules(test_context, rules):
    updated_zone = add_rules_to_zone(test_context.ip4_reverse_zone, rules)
    update_change = test_context.ok_vinyldns_client.update_zone(updated_zone, status=202)
    test_context.ok_vinyldns_client.wait_until_zone_change_status_synced(update_change)


def add_ip6_acl_rules(test_context, rules):
    updated_zone = add_rules_to_zone(test_context.ip6_reverse_zone, rules)
    update_change = test_context.ok_vinyldns_client.update_zone(updated_zone, status=202)
    test_context.ok_vinyldns_client.wait_until_zone_change_status_synced(update_change)


def add_classless_acl_rules(test_context, rules):
    updated_zone = add_rules_to_zone(test_context.classless_zone_delegation_zone, rules)
    update_change = test_context.ok_vinyldns_client.update_zone(updated_zone, status=202)
    test_context.ok_vinyldns_client.wait_until_zone_change_status_synced(update_change)


def remove_ok_acl_rules(test_context, rules):
    zone = test_context.ok_vinyldns_client.get_zone(test_context.ok_zone["id"])["zone"]
    updated_zone = remove_rules_from_zone(zone, rules)
    update_change = test_context.ok_vinyldns_client.update_zone(updated_zone, status=202)
    test_context.ok_vinyldns_client.wait_until_zone_change_status_synced(update_change)


def remove_ip4_acl_rules(test_context, rules):
    zone = test_context.ok_vinyldns_client.get_zone(test_context.ip4_reverse_zone["id"])["zone"]
    updated_zone = remove_rules_from_zone(zone, rules)
    update_change = test_context.ok_vinyldns_client.update_zone(updated_zone, status=202)
    test_context.ok_vinyldns_client.wait_until_zone_change_status_synced(update_change)


def remove_ip6_acl_rules(test_context, rules):
    zone = test_context.ok_vinyldns_client.get_zone(test_context.ip6_reverse_zone["id"])["zone"]
    updated_zone = remove_rules_from_zone(zone, rules)
    update_change = test_context.ok_vinyldns_client.update_zone(updated_zone, status=202)
    test_context.ok_vinyldns_client.wait_until_zone_change_status_synced(update_change)


def remove_classless_acl_rules(test_context, rules):
    zone = test_context.ok_vinyldns_client.get_zone(test_context.classless_zone_delegation_zone["id"])["zone"]
    updated_zone = remove_rules_from_zone(zone, rules)
    update_change = test_context.ok_vinyldns_client.update_zone(updated_zone, status=202)
    test_context.ok_vinyldns_client.wait_until_zone_change_status_synced(update_change)


def clear_ok_acl_rules(test_context):
    zone = test_context.ok_zone
    if zone is not None and "acl" in zone and "rules" in zone["acl"]:
        zone["acl"]["rules"] = []
        update_change = test_context.ok_vinyldns_client.update_zone(zone, status=(202, 404))
        test_context.ok_vinyldns_client.wait_until_zone_change_status_synced(update_change)


def clear_shared_zone_acl_rules(test_context):
    zone = test_context.shared_zone
    if zone is not None and "acl" in zone and "rules" in zone["acl"]:
        zone["acl"]["rules"] = []
        update_change = test_context.shared_zone_vinyldns_client.update_zone(zone, status=(202, 404))
        test_context.shared_zone_vinyldns_client.wait_until_zone_change_status_synced(update_change)


def clear_ip4_acl_rules(test_context):
    zone = test_context.ip4_reverse_zone
    if zone is not None and "acl" in zone and "rules" in zone["acl"]:
        zone["acl"]["rules"] = []
        update_change = test_context.ok_vinyldns_client.update_zone(zone, status=(202, 404))
        test_context.ok_vinyldns_client.wait_until_zone_change_status_synced(update_change)


def clear_ip6_acl_rules(test_context):
    zone = test_context.ip6_reverse_zone
    if zone is not None and "acl" in zone and "rules" in zone["acl"]:
        zone["acl"]["rules"] = []
        update_change = test_context.ok_vinyldns_client.update_zone(zone, status=(202, 404))
        test_context.ok_vinyldns_client.wait_until_zone_change_status_synced(update_change)


def clear_classless_acl_rules(test_context):
    zone = test_context.classless_zone_delegation_zone
    if zone is not None and "acl" in zone and "rules" in zone["acl"]:
        zone["acl"]["rules"] = []
        update_change = test_context.ok_vinyldns_client.update_zone(zone, status=(202, 404))
        test_context.ok_vinyldns_client.wait_until_zone_change_status_synced(update_change)


def seed_text_recordset(client, record_name, zone, records=[{"text": "someText"}]):
    new_rs = {
        "zoneId": zone["id"],
        "name": record_name,
        "type": "TXT",
        "ttl": 100,
        "records": records
    }
    result = client.create_recordset(new_rs, status=202)
    result_rs = result["recordSet"]
    client.wait_until_recordset_exists(result_rs["zoneId"], result_rs["id"])

    return result_rs


def seed_ptr_recordset(client, record_name, zone, records=[{"ptrdname": "foo.com."}]):
    new_rs = {
        "zoneId": zone["id"],
        "name": record_name,
        "type": "PTR",
        "ttl": 100,
        "records": records
    }
    result = client.create_recordset(new_rs, status=202)
    result_rs = result["recordSet"]
    client.wait_until_recordset_exists(result_rs["zoneId"], result_rs["id"])

    return result_rs


def clear_zones(client):
    # Get the groups for the ok user
    groups = client.list_all_my_groups()
    group_ids = [x["id"] for x in groups]

    zones = client.list_zones()["zones"]
    if len(zones) > 0:
        # we only want to delete zones that the ok user "owns"
        zones_to_delete = [x for x in zones if (x["adminGroupId"] in group_ids or x["account"] in group_ids) and x["accessLevel"] == "Delete"]
        zone_ids_to_delete = [x["id"] for x in zones_to_delete]
        if len(zone_ids_to_delete) > 0:
            client.abandon_zones(zone_ids_to_delete)


def clear_groups(client, exclude=[]):
    groups = client.list_all_my_groups()
    group_ids = [x["id"] for x in groups]

    for group_id in group_ids:
        if group_id not in exclude:
            client.delete_group(group_id, status=200)


def get_change_A_AAAA_json(input_name, record_type="A", ttl=200, address=None, change_type="Add"):
    json = {
        "changeType": change_type,
        "inputName": input_name,
        "type": record_type,

    }
    if change_type == "Add":
        json["ttl"] = ttl
        json["record"] = {
            "address": address or "1.1.1.1"
        }
    else:
        if address is not None:
            json["record"] = {
                "address": address
            }
    return json


def get_change_CNAME_json(input_name, ttl=200, cname=None, change_type="Add"):
    json = {
        "changeType": change_type,
        "inputName": input_name,
        "type": "CNAME",

    }
    if change_type == "Add":
        json["ttl"] = ttl
        json["record"] = {
            "cname": cname or "test.com"
        }
    else:
        if cname is not None:
            json["record"] = {
                "cname": cname
            }
    return json


def get_change_PTR_json(ip, ttl=200, ptrdname=None, change_type="Add"):
    json = {
        "changeType": change_type,
        "inputName": ip,
        "type": "PTR"
    }
    if change_type == "Add":
        json["ttl"] = ttl
        json["record"] = {
            "ptrdname": ptrdname or "test.com"
        }
    else:
        if ptrdname is not None:
            json["record"] = {
                "ptrdname": ptrdname
            }
    return json


def get_change_TXT_json(input_name, record_type="TXT", ttl=200, text=None, change_type="Add"):
    json = {
        "changeType": change_type,
        "inputName": input_name,
        "type": "TXT",

    }
    if change_type == "Add":
        json["ttl"] = ttl
        json["record"] = {
            "text": text or "test"
        }
    else:
        if text is not None:
            json["record"] = {
                "text": text
            }
    return json


def get_change_MX_json(input_name, ttl=200, preference=None, exchange=None, change_type="Add"):
    json = {
        "changeType": change_type,
        "inputName": input_name,
        "type": "MX",

    }
    if change_type == "Add":
        json["ttl"] = ttl
        json["record"] = {
            "preference": preference,
            "exchange": exchange or "foo.bar."
        }
        if preference is None:
            json["record"]["preference"] = 1
    else:
        if preference is not None or exchange is not None:
            json["record"] = {
                "preference": preference,
                "exchange": exchange or "foo.bar."
            }
            if preference is None:
                json["record"]["preference"] = 1

    return json


def get_change_NS_json(input_name, ttl=200, nsdname=None, change_type="Add"):
    json = {
        "changeType": change_type,
        "inputName": input_name,
        "type": "NS"
    }
    if change_type == "Add":
        json["ttl"] = ttl
        json["record"] = {
            "nsdname": nsdname
        }
    return json


def get_change_SRV_json(input_name, ttl=200, priority=None, weight=None, port=None, target=None, change_type="Add"):
    json = {
        "changeType": change_type,
        "inputName": input_name,
        "type": "SRV",
    }
    if change_type == "Add":
        json["ttl"] = ttl
        json["record"] = {
            "priority": priority,
            "weight": weight,
            "port": port,
            "target": target
        }
    return json


def get_change_NAPTR_json(input_name, ttl=200, order=None, preference=None, flags=None, service=None, regexp=None, replacement=None, change_type="Add"):
    json = {
        "changeType": change_type,
        "inputName": input_name,
        "type": "NAPTR",

    }
    if change_type == "Add":
        json["ttl"] = ttl
        json["record"] = {
            "order": order,
            "preference": preference,
            "flags": flags,
            "service": service,
            "regexp": regexp,
            "replacement": replacement
        }
    return json


def create_recordset(zone, rname, recordset_type, rdata_list, ttl=200, ownergroup_id=None):
    recordset_data = {
        "zoneId": zone["id"],
        "name": rname,
        "type": recordset_type,
        "ttl": ttl,
        "records": rdata_list
    }
    if ownergroup_id is not None:
        recordset_data["ownerGroupId"] = ownergroup_id

    return recordset_data


def clear_recordset_list(to_delete, client):
    delete_changes = []
    for result_rs in to_delete:
        try:
            delete_result = client.delete_recordset(result_rs["zone"]["id"], result_rs["recordSet"]["id"], status=202)
            delete_changes.append(delete_result)
        except AssertionError:
            pass
        except Exception:
            traceback.print_exc()
            raise
    for change in delete_changes:
        try:
            client.wait_until_recordset_change_status(change, "Complete")
        except AssertionError:
            pass
        except Exception:
            traceback.print_exc()
            raise


def clear_zoneid_rsid_tuple_list(to_delete, client):
    delete_changes = []
    for tup in to_delete:
        try:
            delete_result = client.delete_recordset(tup[0], tup[1], status=202)
            delete_changes.append(delete_result)
        except AssertionError:
            pass
        except Exception:
            traceback.print_exc()
            raise
    for change in delete_changes:
        try:
            client.wait_until_recordset_change_status(change, "Complete")
        except AssertionError:
            pass
        except Exception:
            traceback.print_exc()
            raise


def get_group_json(group_name, email="test@test.com", description="this is a description", members=[{"id": "ok"}],
                   admins=[{"id": "ok"}]):
    return {
        "name": group_name,
        "email": email,
        "description": description,
        "members": members,
        "admins": admins
    }


def generate_record_name(zone_name=None):
    import inspect
    previous_frame = inspect.currentframe().f_back
    (filename, line_number, function_name, lines, index) = inspect.getframeinfo(previous_frame)
    if zone_name:
        return "{0}-{1}.{2}".format(function_name[:58], line_number, zone_name).replace("_", "-")
    else:
        return "{0}-{1}".format(function_name[:58], line_number).replace("_", "-")


def find_recordset_by_name(zone_id, rs_name, client):
    r = client.list_recordsets_by_zone(zone_id, record_name_filter=rs_name, status=200)
    if r and "recordSets" in r and len(r["recordSets"]) > 0:
        return r["recordSets"][0]
    else:
        return None


def delete_recordset_by_name(zone_id, rs_name, client):
    rs = find_recordset_by_name(zone_id, rs_name, client)
    if rs:
        client.delete_recordset(rs["zoneId"], rs["id"])
        client.wait_until_recordset_deleted(rs["zoneId"], rs["id"])
        return rs
    else:
        return None
