---
layout: docs
title: "Get Group Change"
section: "api"
---

# Get RecordSet Change

Retrieves a group change given the group change ID.

#### HTTP REQUEST

> GET /groups/change/{groupChangeId}

#### HTTP RESPONSE TYPES

Code          | description |
 ------------ | :---------- |
200           | **OK** - The group change is returned in the response body
401           | **Unauthorized** - The authentication information provided is invalid.  Typically the request was not signed properly, or the access key and secret used to sign the request are incorrect |
403           | **Forbidden** - The user does not have the access required to perform the action |
404           | **Not Found** - The group change was not found |

#### HTTP RESPONSE ATTRIBUTES

name                | type          | description |
 -----------------  | ------------- | :---------- |
newGroup            | map           | The new group as a result of the change. Refer to [Membership Model](membership-model.html) |
oldGroup            | map           | The old group before the change. Refer to [Membership Model](membership-model.html) |
created             | string        | Millisecond timestamp that change was created
userId              | string        | User Id of user who made the change |
id                  | string        | Id of the group change |
userName            | string        | Username of user who made the change |
groupChangeMessage  | string        | The description of the changes made to the group |
changeType          | string        | The type change, either Create, Update, or Delete |

#### EXAMPLE RESPONSE

```json
{
  "newGroup": {
    "id": "7f420e07-3043-46f3-97e2-8d1ac47d08db",
    "name": "test-group",
    "email": "test@test.com",
    "created": "2023-09-29T06:37:04Z",
    "status": "Active",
    "members": [
      {
        "id": "d4f47898-7a41-4b0d-ba18-de4788b9f102"
      },
      {
        "id": "6c4bfd61-d1c6-426e-8123-6f7c28f89d2d"
      }
    ],
    "admins": [
      {
        "id": "d4f47898-7a41-4b0d-ba18-de4788b9f102"
      }
    ]
  },
  "changeType": "Update",
  "userId": "d4f47898-7a41-4b0d-ba18-de4788b9f102",
  "oldGroup": {
    "id": "7f420e07-3043-46f3-97e2-8d1ac47d08db",
    "name": "test-group",
    "email": "test@test.com",
    "created": "2023-09-29T06:37:04Z",
    "status": "Active",
    "members": [
      {
        "id": "d4f47898-7a41-4b0d-ba18-de4788b9f102"
      }
    ],
    "admins": [
      {
        "id": "d4f47898-7a41-4b0d-ba18-de4788b9f102"
      }
    ]
  },
  "id": "a3227632-a166-407e-8a74-c0624d967b58",
  "created": "2023-09-29T06:37:12.061Z",
  "userName": "testuser",
  "groupChangeMessage": "Group member/s with user name/s 'hermes' added."
}
```
