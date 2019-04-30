---
layout: docs
title: "Zone Model"
section: "api"
---

# Zone Model

#### Table of Contents

- [Zone Attributes](#zone-attributes)
- [Zone JSON Example](#zone-example)
- [Zone Connection Attributes](#zone-conn-attr)
- [Zone Connection JSON Example](#zone-conn-example)
- [Zone ACL Rule Attributes](#zone-acl-rule-attr)
- [Zone ACL Rule Examples](#zone-acl-rule-example)
- [PTR ACL Rule](#ptr-acl-rule)
- [PTR ACL Rule Examples](#ptr-acl-rule-example)
- [Shared Zones](#shared-zones) 

#### ZONE ATTRIBUTES <a id="zone-attributes"></a>

field         | type        | description |
 ------------ | :---------- | :---------- |
status        | string      | *Active* - the zone is connected and ready for use; *Syncing* - the zone is currently syncing with the DNS backend and is not available until syncing is complete. |
updated       | date-time   | The last time the zone was changed.  Note: this does not include changes to record sets, only the zone entity itself |
name          | string      | The name of the zone |
adminGroupId  | string      | The id of the administrators group for the zone |
created       | date-time   | The time when the zone was first created |
account       | string      | **DEPRECATED** The account that created the zone |
email         | string      | The distribution email for the zone |
connection    | ZoneConnection | The connection used to issue DDNS updates to the backend zone.  If not provided, default keys will be used.  See the [Zone Connection Attributes](#zone-conn-attr) for more information |
transferConnection | ZoneConnection | The connection that is used to sync the zone with the DNS backend.  This can be different than the update connection.  If not provided, default keys will be used |
shared        | boolean     | An indicator that the zone is shared with anyone. At this time only VinylDNS administrators can set this to true.|
acl           | ZoneACL     | The access control rules governing the zone.  See the [Zone ACL Rule Attributes](#zone-acl-rule-attr) for more information
id            | string      | The unique identifier for this zone
latestSync    | date-time   | The last date and time the zone was synced
isTest        | boolean     | Defaults to **false**. Used for restricted access during VinylDNS testing, can be ignored by clients

#### ZONE EXAMPLE <a id="zone-example"></a>

```
{
  "status": "Active",
  "updated": "2016-12-16T15:27:28Z",
  "name": "ok.",
  "adminGroupId": "92b298e8-97db-4f1b-881b-fd08ca0dd311",
  "created": "2016-12-16T15:27:26Z",
  "account": "92b298e8-97db-4f1b-881b-fd08ca0dd311",
  "email": "test@test.com",
  "connection": {
    "primaryServer": "127.0.0.1:5301",
    "keyName": "vinyl.",
    "name": "ok.",
    "key": "OBF:1:W1FXgpOjjrQAABAARrZmyLjFSOuFYTAw81mhvNEmNAc4RnYzPjJQMEjVQWWLRohu7gRAVw=="
  },
  "transferConnection": {
    "primaryServer": "127.0.0.1:5301",
    "keyName": "vinyl.",
    "name": "ok.",
    "key": "OBF:1:W1FXgpOjjrQAABAARrZmyLjFSOuFYTAw81mhvNEmNAc4RnYzPjJQMEjVQWWLRohu7gRAVw=="
  },
  "shared": false,
  "acl": {
    "rules": [
      {
        "accessLevel": "Write",
        "userId": "<uuid>",
        "description": "some_test_rule",
        "recordTypes": []
      },
      {
        "recordMask": ".*",
        "accessLevel": "Write",
        "userId": "<uuid>",
        "description": "some_test_rule",
        "recordTypes": []
      },
      {
        "recordMask": "test.*",
        "accessLevel": "Read",
        "groupId": "<uuid>",
        "description": "some_test_rule",
        "recordTypes": []
      }
    ]
  },
  "id": "9cbdd3ac-9752-4d56-9ca0-6a1a14fc5562",
  "latestSync": "2016-12-16T15:27:26Z"
}
```

#### ZONE CONNECTION ATTRIBUTES <a id="zone-conn-attr"></a>
Zone Connection specifies the connection information to the backend DNS server.

field        | type        | description |
------------ | :---------- | :---------- |
primaryServer | string      | The ip address or host that is connected to.  This can take a port as well `127.0.0.1:5300`.  If no port is specified, 53 will be assumed. |
keyName       | string      | The name of the DNS key that has access to the DNS server and zone.  **Note:** For the transfer connection, the key must be given *allow-transfer* access to the zone.  For the primary connection, the key must be given *allow-update* access to the zone. |
name          | string      | A user identifier for the connection.
key           | string      | The TSIG secret key used to sign requests when communicating with the primary server.  **Note:** After creating the zone, the key value itself is hashed and obfuscated, so it will be unusable from a client perspective. |

#### ZONE CONNECTION EXAMPLE <a id="zone-conn-example"></a>

```
{
  "primaryServer": "127.0.0.1:5301",
  "keyName": "vinyl.",
  "name": "ok.",
  "key": "OBF:1:W1FXgpOjjrQAABAARrZmyLjFSOuFYTAw81mhvNEmNAc4RnYzPjJQMEjVQWWLRohu7gRAVw=="
}
```

#### ZONE ACL RULE ATTRIBUTES <a id="zone-acl-rule-attr"></a>
ACL Rules are used to govern user and group access to record operations on a zone.  ACL Rules can be associated with a specific user, or all users in a specified group.  If neither a user _or_ a group is attached to an ACL rule, then the rule applies to _all_ users in the system.
<br><br>
Use the [Zone Update](../api/update-zone) endpoint to update the **acl** attribute of the zone

> **Important!** If a user is mentioned on an ACL Rule directly, or is a member of a group that is mentioned on an ACL Rule, that user will be able to see the zone.

> Rules made without selecting a group or user will apply to all users in VinylDNS.

field        | type        | description |
------------ | :---------- | :---------- |
recordMask   | string      | (optional) A regular expression that is used to match against _record names_.  If left empty, then _all_ records will be matched for the rule.  All records matching the match will be governed by this rule. |
recordTypes  | Array[String] | An array of all record types that this rule applies to.  If left empty, then all record types will be governed by this rule. |
accessLevel  | string      | **NoAccess** - cannot see the data for the record; **Read** - can read only the record; **Write** - the user can create and edit records, but cannot delete them; **Delete** - the user can read, create, update, and delete records |
userId       | string      | (optional) The unique identifier for the user the rule applies to.  *Note: this is not the name of the user, but their uuid in VinylDNS* |
groupId      | string      | (optional) The unique identifier for the group the rule applies to.  *Note: you cannot set both the userId and groupId, only one* |
description  | string      | (optional) A user entered description for the rule |

The priority of ACL Rules in descending precedence: <br>
  1. Individual rules placed on a user <br>
  2. Rules placed on groups that a user is in <br>
  3. Rules placed on all users in VinylDNS

> *Note: Being in the admin group of a zone will grant users full access regardless of ACL Rules*

For conflicting rules, the rule that is more specific will take precedence. For example, if the account *jdoe201* was given Read access to all records in a zone
through the rule:

```
{
  "userId": "<uuid>",
  "accessLevel": "Read",
}
```

and then Write access to only A records through the rule:

```
{
  "userId": "<uuid>",
  "accessLevel": "Write",
  "recordTypes": ["A"]
}
```

and then Delete access to only A records that matched the expression \*dev\* through the rule:

```
{
  "userId": "<uuid>",
  "accessLevel": "Delete",
  "recordTypes": ["A"],
  "recordMask": "*dev*"
}
```

then the rule with the recordMask will take precedence and give Delete access to matched A RecordSets, the rule with recordTypes will
take precedence and give Write access to all other A records, and the more broad rule will give Read access to all other record types in the zone

#### ZONE ACL RULE EXAMPLES <a id="zone-acl-rule-example"></a>
**Grant read/write/delete access to www.* records of type A, AAAA, CNAME to one user**
Under this rule, the user specified will be able to view, create, edit, and delete records in the zone that match the expression `www.*` and are of type A, AAAA, or CNAME.

```
{
  "recordMask": "www.*",
  "accessLevel": "Delete",
  "userId": "<uuid>",
  "recordTypes": ["A", "AAAA", "CNAME"]
}
```

**Grant read only access to all VinylDNS users to A, AAAA, CNAME records**

```
{
  "accessLevel": "Read",
  "recordTypes": ["A", "AAAA", "CNAME"]
}
```

**Grant read/write/delete access to records of type A, AAAA, CNAME to one group***

```
{
  "accessLevel": "Delete",
  "groupId": "<uuid>",
  "recordTypes": ["A", "AAAA", "CNAME"]
}
```

### PTR ACL RULES WITH CIDR MASKS <a id="ptr-acl-rule"></a>
ACL rules can be applied to specific record types and can include record masks to further narrow down which records they
apply to. These record masks apply to record names, but because PTR record names are part their reverse zone ip, the use of regular
expressions for record masks are not supported.
<br><br>
Instead PTR record masks must be CIDR rules, which will denote a range of ip addresses that the rule will apply to.
While more information and useful CIDR rule utility tools can be found online, CIDR rules describe how many bits of an ip address' binary representation
must be the same for a match.

### PTR ACL RULES WITH CIDR MASKS EXAMPLE <a id="ptr-acl-rule-example"></a>
The ACL Rule

```
{
    recordTypes: ["PTR"],
    accessLevel: "Read"
}
```

Will give Read permissions to PTR Record Sets to all users in VinylDNS
<br><br>
The **IPv4** ACL Rule

```
{
    recordTypes: ["PTR"],
    accessLevel: "Read",
    recordMask: "100.100.100.100/16"
}
```

Will give Read permissions to PTR Record Sets 100.100.000.000 to 100.100.255.255, as 16 bits is half of an IPv4 address
<br><br>
The **IPv6** ACL Rule

```
{
    recordTypes: ["PTR"],
    accessLevel: "Read",
    recordMask: "1000:1000:1000:1000:1000:1000:1000:1000/64"
}
```

Will give Read permissions to PTR Record Sets 1000:1000:1000:1000:0000:0000:0000:0000 to 1000:1000:1000:1000:FFFF:FFFF:FFFF:FFFF, as 64 bits is half of an IPv6 address.

### SHARED ZONES <a id="shared-zones"></a>

Shared zones allow for a more open management of records in VinylDNS. Zone administrators can assign ownership of records to groups. Any user in VinylDNS can claim existing unowned records in shared zones, as well as create records in those zones. Once a record is owned, only users in the record owner group, the zone administrators and those with relevant ACL rules can modify or delete the record. The [batch change API endpoint](../api/create-batchchange) and [batch change area of the portal](../portal/batch-changes) are where users can create new records in shared zones, modify records they own, or claim unowned records. If a zone's shared state changes to false the record ownership access is no longer applicable.
