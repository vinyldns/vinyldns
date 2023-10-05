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
changes        | Array of Group Changes | refer to [Group Change](#group-change) |
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
  "maxItems": 100,
  "changes": [
    {
      "newGroup": {
        "status": "Active",
        "name": "test-list-group-activity-max-item-success",
        "created": "2017-03-02T18:49:58Z",
        "id": "1555bac7-0343-4d11-800f-955afb481818",
        "admins": [
          {
            "id": "ok"
          }
        ],
        "members": [
          {
            "id": "dummy199"
          },
          {
            "id": "ok"
          }
        ],
        "email": "test@test.com"
      },
      "created": "1488480605378",
      "userId": "some-user",
      "changeType": "Update",
      "oldGroup": {
        "status": "Active",
        "name": "test-list-group-activity-max-item-success",
        "created": "2017-03-02T18:49:58Z",
        "id": "1555bac7-0343-4d11-800f-955afb481818",
        "admins": [
          {
            "id": "ok"
          }
        ],
        "members": [
          {
            "id": "dummy198"
          },
          {
            "id": "ok"
          }
        ],
        "email": "test@test.com"
      },
      "id": "11abb88b-c47d-469b-bc2d-6656e00711cf"
    }
  ]
}
```
