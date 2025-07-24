---
layout: docs
title: "List Groups"
section: "api"
---

# List Groups

Retrieves a list of groups that you are a part of.

#### HTTP REQUEST

> GET /groups?startFrom={response.nextId}&maxItems={1 - 100}&groupNameFilter={filter}&ignoreAccess={true  &#124; false}&abridged={true  &#124; false}

#### HTTP REQUEST PARAMS

name             | type          | required?   | description |
 ------------    | ------------- | ----------- | :---------- |
groupNameFilter  | string        | no          | One or more characters contained in the name of the group set to search for.  For example `TP`.  This is a contains search only, no wildcards or regular expressions are supported |
startFrom        | string        | no          | In order to advance through pages of results, the startFrom is set to the `nextId` that is returned on the previous response.  It is up to the client to maintain previous pages if the client wishes to advance forward and backward.   If not specified, will return the first page of results |
maxItems         | integer       | no          | The number of items to return in the page.  Valid values are 1 to 100. Defaults to 100 if not provided. |
ignoreAccess     | boolean       | no          | If false, returns only groups the requesting user is a member of. If true, returns groups in the system, regardless of membership. Defaults to false if not provided. Super and support admin see all groups regardless of this value. |
abridged         | boolean       | no          | If false, returns all the group details. If true, returns an abridged version of group details. Defaults to false if not provided. |

#### HTTP RESPONSE TYPES

Code          | description |
 ------------ | :---------- |
200           | **OK** - The groups have been returned in the response body|
401           | **Unauthorized** - The authentication information provided is invalid.  Typically the request was not signed properly, or the access key and secret used to sign the request are incorrect |

#### HTTP RESPONSE ATTRIBUTES

name             | type               | description |
 ------------    | -------------      | :---------- |
groups           | Array of Groups    | refer to [membership model](membership-model.html) |
groupNameFilter  | string             | name filter sent in request |
startFrom        | string             | startFrom sent in request, will not be returned if not provided |
nextId           | string             | nextId, used as startFrom parameter of next page request, will not be returned if groups are exhausted |
maxItems         | integer            | maxItems sent in request, default is 100 |
ignoreAccess     | boolean            | The ignoreAccess parameter that was sent in the HTTP request. This will be false if not sent. |

#### EXAMPLE RESPONSE

```json
{
  "maxItems": 100,
  "groups": [
    {
      "id": "93887728-2b26-4749-ba69-98871dda9cc0",
      "name": "some-other-group",
      "email": "test@example.com",
      "created": "2017-03-02T16:23:07Z",
      "status": "Active",
      "members": [
        {
          "id": "2764183c-5e75-4ae6-8833-503cd5f4dcb0"
        }
      ],
      "admins": [
        {
          "id": "2764183c-5e75-4ae6-8833-503cd5f4dcb0"
        }
      ]
    },
    {
      "id": "aa1ea217-70a7-4350-b22b-c7e2f2158fb9",
      "name": "some-group",
      "email": "test@example.com",
      "created": "2017-03-02T16:22:57Z",
      "status": "Active",
      "members": [
        {
          "id": "2764183c-5e75-4ae6-8833-503cd5f4dcb0"
        }
      ],
      "admins": [
        {
          "id": "2764183c-5e75-4ae6-8833-503cd5f4dcb0"
        }
      ]
    }
  ],
  "ignoreAccess": false
}
```
