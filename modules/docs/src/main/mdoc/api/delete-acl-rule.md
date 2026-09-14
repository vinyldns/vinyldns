---
layout: docs
title: "Delete ACL Rule"
section: "api"
---

# Delete ACL Rule

Removes an Access Control List (ACL) rule from a zone. The rule to delete is identified by providing a matching ACL rule object in the request body.

#### HTTP REQUEST

> DELETE /zones/{zoneId}/acl/rules

#### HTTP REQUEST ATTRIBUTES

Provide the ACL rule to delete. The rule must match an existing rule in the zone's ACL.

See [Zone ACL Rule Attributes](zone-model.html#zone-acl-rule-attr) for the full list of ACL rule fields.

name          | type          | required?     | description |
 ------------ | ------------- | ------------- | :---------- |
accessLevel   | string        | yes           | **NoAccess**, **Read**, **Write**, or **Delete** |
recordMask    | string        | no            | Regular expression to match record names |
recordTypes   | Array[String] | no            | Record types this rule applies to |
userId        | string        | no            | UUID of the user this rule applies to |
groupId       | string        | no            | UUID of the group this rule applies to |
description   | string        | no            | Description of the rule |

#### HTTP RESPONSE TYPES

Code          | description |
 ------------ | :---------- |
202           | **Accepted** - The ACL rule deletion has been queued for processing |
400           | **Bad Request** - Invalid ACL rule |
401           | **Unauthorized** - The authentication information provided is invalid. Typically the request was not signed properly, or the access key and secret used to sign the request are incorrect |
403           | **Forbidden** - The user does not have the access required to perform the action |
404           | **Not Found** - Zone not found |
409           | **Conflict** - Zone is not in a state where ACL rules can be modified |

#### EXAMPLE REQUEST

```json
{
  "accessLevel": "Write",
  "groupId": "456e64c0-b34f-4c9b-9e0e-f7f7bcc16f2e",
  "recordTypes": ["A", "AAAA", "CNAME"],
  "description": "Allow web team to manage A, AAAA, and CNAME records"
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
