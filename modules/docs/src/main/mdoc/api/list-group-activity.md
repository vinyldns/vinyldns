---
layout: docs
title: "List Group Activity"
section: "api"
---

# List Group Activity

Retrieves a list of group activity.

#### HTTP REQUEST

> GET /groups/{groupId}/activity?startFrom={response.nextId}&maxItems={1 - 100}

#### HTTP REQUEST PARAMS

name          | type          | required?   | description |
 ------------ | ------------- | ----------- | :---------- |
startFrom     | integer       | no          | In order to advance through pages of results, the startFrom is set to the `nextId` that is returned on the previous response.  It is up to the client to maintain previous pages if the client wishes to advance forward and backward.   If not specified, will return the first page of results |
maxItems      | integer       | no          | The number of items to return in the page.  Valid values are 1 to 100. Defaults to 100 if not provided. |

#### HTTP RESPONSE TYPES

Code          | description |
 ------------ | :---------- |
200           | **OK** - The changes have been returned in the response body|
401           | **Unauthorized** - The authentication information provided is invalid.  Typically the request was not signed properly, or the access key and secret used to sign the request are incorrect |
404           | **Not Found** - The group was not found |

#### HTTP RESPONSE ATTRIBUTES

name          | type          | description |
 ------------ | ------------- | :---------- |
changes       | Array of Group Changes | refer to [Group Change](#group-change) |
startFrom     | integer       | startFrom sent in request, will not be returned if not provided |
nextId        | integer       | nextId, used as startFrom parameter of next page request, will not be returned if activity is exhausted |
maxItems      | integer       | maxItems sent in request, default is 100 |

#### GROUP CHANGE ATTRIBUTES <a id="group-change"></a>

name                | type          | description |
 -----------------  | ------------- | :---------- |
newGroup            | map           | The new group as a result of the change, refer to [Membership Model](membership-model.html) |
oldGroup            | map           | The old group before the change, refer to [Membership Model](membership-model.html) |
created             | string        | Millisecond timestamp that change was created
userId              | string        | User Id of user who made the change |
id                  | string        | Id of the group change |
userName            | string        | Username of user who made the change |
groupChangeMessage  | string        | The description of the changes made to the group |
changeType          | string        | The type change, either Create, Update, or Delete |

#### EXAMPLE RESPONSE

```json
{
  "changes": [
    {
      "newGroup": {
        "id": "6edb08fe-8179-4e18-aa08-2acc1785c364",
        "name": "test-group",
        "email": "test@test.com",
        "created": "2024-02-22T07:32:51Z",
        "status": "Active",
        "members": [
          {
            "id": "6a8545e7-cbab-47c9-8aa2-c56e413c44b6"
          },
          {
            "id": "6c83a035-cc1b-4d94-acd6-bb2da351edca"
          },
          {
            "id": "864f7002-e48e-451c-9909-50567ecdc1a5"
          }
        ],
        "admins": [
          {
            "id": "6a8545e7-cbab-47c9-8aa2-c56e413c44b6"
          }
        ]
      },
      "changeType": "Update",
      "userId": "6a8545e7-cbab-47c9-8aa2-c56e413c44b6",
      "oldGroup": {
        "id": "6edb08fe-8179-4e18-aa08-2acc1785c364",
        "name": "test-group",
        "email": "test@test.com",
        "created": "2024-02-22T07:32:51Z",
        "status": "Active",
        "members": [
          {
            "id": "6a8545e7-cbab-47c9-8aa2-c56e413c44b6"
          },
          {
            "id": "6c83a035-cc1b-4d94-acd6-bb2da351edca"
          },
          {
            "id": "864f7002-e48e-451c-9909-50567ecdc1a5"
          }
        ],
        "admins": [
          {
            "id": "6a8545e7-cbab-47c9-8aa2-c56e413c44b6"
          },
          {
            "id": "6c83a035-cc1b-4d94-acd6-bb2da351edca"
          }
        ]
      },
      "id": "1c1151e9-099f-4cb8-aa24-bf43d21e5fd5",
      "created": "2024-02-22T07:33:09.262Z",
      "userName": "professor",
      "groupChangeMessage": "Group admin/s with user name/s 'fry' removed."
    }
  ],
  "startFrom": 1,
  "nextId": 2,
  "maxItems": 1
}
```
