---
layout: docs
title: "Membership Model"
section: "api"
---

# Membership Model

#### Table of Contents

- [Group Attributes](#group-attributes)
- [Group Example](#group-example)
- [User Attributes](#user-attributes)
- [User Example](#user-example)

#### MEMBERSHIP BREAKDOWN

Every zone can be connected to by only one group in VinylDNS. That initial group will be the admin group for that zone,
which can be changed later on in a [Zone Update](update-zone.html). Every member of the admin group
will be an admin of that zone, and can preform zone syncs, zone updates, zone deletes, and record set changes regardless
of any Access Control Rules set on them.
<br><br>
While users in the admin group will have complete zone access, further users can be given limited membership through [Zone
ACL Rules](zone-model.html#zone-acl-rule-attr).

#### GROUP ATTRIBUTES <a id="group-attributes"></a>

field         | type        | description |
 ------------ | :---------- | :---------- |
name          | string      | This should be a single word name used for the groups. Use hyphens if needed, no spaces |
email         | string      | The email distribution list for the group |
description   | string      | A short description of the group, if more info is needed other than the name. The group will not have this attribute if it was not included in the create request |
id            | string      | Unique UUID of the group |
created       | date-time   | The timestamp (UTC) when the group was created |
status        | string      | **Active** or **Deleted** |
members       | Array of User id objects | Set of User ids in the group |
admins        | Array of User id objects | Set of User ids that are admins of the group. All admin user ids should also be in the members array |

**Being in the admin set of a group has no impact on zone privileges when the group is the zone's admin group. Being a group admin allows adding users to
the group, deleting users from the group, toggling other users' admin statuses (including your own), and deleting the group**

#### GROUP EXAMPLE <a id="group-example"></a>

```json
{
  "id": "dc4c7c79-5bbc-41bf-992e-8d6c4ec574c6",
  "name": "some-group",
  "email": "test@example.com",
  "created": "2017-01-30T20:05:24Z",
  "status": "Active",
  "members": [
    {
      "id": "2764183c-5e75-4ae6-8833-503cd5f4dcb0"
    },
    {
      "id": "a6d35b1a-57d7-4a65-bec2-d7ed30a7c430"
    }
  ],
  "admins": [
    {
      "id": "a6d35b1a-57d7-4a65-bec2-d7ed30a7c430"
    }
  ]
}
```

#### USER ATTRIBUTES <a id="user-attributes"></a>

field         | type        | description |
 ------------ | :---------- | :---------- |
userName      | string      | This should be the AD username of the user |
firstName     | string      | First name of the user |
lastName      | string      | Last name of the user |
email         | string      | Email address of the user |
created       | date-time   | The timestamp (UTC) when the user was created |
id            | string      | Unique UUID of the user |
isTest        | boolean     | Defaults to **false**. Used for restricted access during VinylDNS testing, can be ignored by clients |

To get your access and secret keys, log into the VinylDNS portal and then with the top right drop-down select **Download Credentials**

#### USER EXAMPLE <a id="user-example"></a>

```json
{
  "userName": "jdoe201",
  "firstName": "John",
  "lastName": "Doe",
  "email": "john_doe@example.com",
  "id": "1764183c-5e75-4ae6-8833-503cd5f4dcb3",
  "isTest": false
}
```
