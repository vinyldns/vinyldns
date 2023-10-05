---
layout: docs
title: "List Group Members"
section: "api"
---

# List Group Members

Retrieves a list of group members.

#### HTTP REQUEST

> GET /groups/{groupId}/members?startFrom={response.nextId}&maxItems={1 - 100}

#### HTTP REQUEST PARAMS

name          | type          | required?   | description |
 ------------ | ------------- | ----------- | :---------- |
startFrom     | string        | no          | In order to advance through pages of results, the startFrom is set to the `nextId` that is returned on the previous response.  It is up to the client to maintain previous pages if the client wishes to advance forward and backward.   If not specified, will return the first page of results |
maxItems      | integer       | no          | The number of items to return in the page.  Valid values are 1 to 100. Defaults to 100 if not provided. |

#### HTTP RESPONSE TYPES

Code          | description |
 ------------ | :---------- |
200           | **OK** - The members have been returned in the response body|
401           | **Unauthorized** - The authentication information provided is invalid.  Typically the request was not signed properly, or the access key and secret used to sign the request are incorrect |
404           | **Not Found** - The group was not found |

#### HTTP RESPONSE ATTRIBUTES

name          | type           | description |
 ------------ | -------------  | :---------- |
members       | Array of Users | refer to [membership model](membership-model.html), these Users will also include an isAdmin attribute |
startFrom     | string         | startFrom sent in request, will not be returned if not provided |
nextId        | string         | nextId, used as startFrom parameter of next page request, will not be returned if members are exhausted |
maxItems      | integer        | maxItems sent in request, default is 100 |

#### EXAMPLE RESPONSE

```json
{
  "members": [
    {
      "id": "0b1acc37-7d97-4da7-8a28-f1770bb99643",
      "userName": "jdoe201",
      "firstName": "John",
      "lastName": "Doe",
      "email": "John_Doe@example.com",
      "created": "2017-03-02T18:42:31Z",
      "isAdmin": true
    },
    {
      "id": "0cb85121-671a-4920-ab02-0c17a7b40874",
      "userName": "bwayne300",
      "firstName": "Bruce",
      "lastName": "Wayne",
      "email": "Bruce_Wayne@cable.example.com",
      "created": "2017-03-02T18:42:54Z",
      "isAdmin": false
    }
  ],
  "maxItems": 100
}
```
