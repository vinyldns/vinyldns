---
layout: docs
title: "Add ACL Rule"
section: "api"
---

# Add ACL Rule

Adds an Access Control List (ACL) rule to a zone. ACL rules govern user and group access to record operations within the zone.

#### HTTP REQUEST

> PUT /zones/{zoneId}/acl/rules

#### HTTP REQUEST ATTRIBUTES

See [Zone ACL Rule Attributes](zone-model.html#zone-acl-rule-attr) for the full list of ACL rule fields.

name          | type          | required?     | description |
 ------------ | ------------- | ------------- | :---------- |
accessLevel   | string        | yes           | **NoAccess**, **Read**, **Write**, or **Delete** |
recordMask    | string        | no            | Regular expression to match record names. If empty, matches all records |
recordTypes   | Array[String] | no            | Record types this rule applies to. If empty, applies to all types |
userId        | string        | no            | UUID of the user this rule applies to (cannot use with groupId) |
groupId       | string        | no            | UUID of the group this rule applies to (cannot use with userId) |
description   | string        | no            | User-provided description of the rule |

#### HTTP RESPONSE TYPES

Code          | description |
 ------------ | :---------- |
202           | **Accepted** - The ACL rule update has been queued for processing |
400           | **Bad Request** - Invalid ACL rule |
401           | **Unauthorized** - The authentication information provided is invalid. Typically the request was not signed properly, or the access key and secret used to sign the request are incorrect |
403           | **Forbidden** - The user does not have the access required to perform the action |
404           | **Not Found** - Zone not found |
409           | **Conflict** - Zone is not in a state where ACL rules can be modified |

#### EXAMPLE REQUESTS

**Grant read/write/delete access to `www.*` records of type `A`, `AAAA`, `CNAME` to one user**

Under this rule, the specified user can view, create, edit, and delete records in the zone that match the expression `www.*` and are of type `A`, `AAAA`, or `CNAME`.

```json
{
  "recordMask": "www.*",
  "accessLevel": "Delete",
  "userId": "123e64c0-b34f-4c9b-9e0e-f7f7bcc16f2e",
  "recordTypes": ["A", "AAAA", "CNAME"],
  "description": "Allow a single user to fully manage matching web records"
}
```

**Grant read-only access to all VinylDNS users for `A`, `AAAA`, `CNAME` records**

```json
{
  "accessLevel": "Read",
  "recordTypes": ["A", "AAAA", "CNAME"],
  "description": "Allow all users to view common web record types"
}
```

**Grant read/write/delete access to records of type `A`, `AAAA`, `CNAME` to one group**

```json
{
  "accessLevel": "Delete",
  "groupId": "456e64c0-b34f-4c9b-9e0e-f7f7bcc16f2e",
  "recordTypes": ["A", "AAAA", "CNAME"],
  "description": "Allow the web team to fully manage common web record types"
}
```

**Grant read access to all `PTR` records**

```json
{
  "recordTypes": ["PTR"],
  "accessLevel": "Read",
  "description": "Allow all users to view PTR records"
}
```

**Grant read access to IPv4 `PTR` records within a CIDR range**

For `PTR` records, `recordMask` must use CIDR notation rather than a regular expression. See [Zone ACL Rule Attributes](zone-model.html#ptr-acl-rule) for more detail.

```json
{
  "recordTypes": ["PTR"],
  "accessLevel": "Read",
  "recordMask": "100.100.100.100/16",
  "description": "Allow read access to PTR records in a specific IPv4 range"
}
```

**Grant read access to IPv6 `PTR` records within a CIDR range**

```json
{
  "recordTypes": ["PTR"],
  "accessLevel": "Read",
  "recordMask": "1000:1000:1000:1000:1000:1000:1000:1000/64",
  "description": "Allow read access to PTR records in a specific IPv6 range"
}
```

#### EXAMPLE RESPONSE

The response contains a [Zone Command Result](zone-model.html) indicating the change has been accepted.

```json
{
  "zone": { ... },
  "userId": "123e64c0-b34f-4c9b-9e0e-f7f7bcc16f2e",
  "changeType": "Update",
  "status": "Pending",
  "created": "2023-01-15T10:30:00Z",
  "id": "789e64c0-b34f-4c9b-9e0e-f7f7bcc16f2e"
}
```
