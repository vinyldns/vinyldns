---
layout: docs
title: "List Zone Changes"
section: "api"
---

# List Zone Changes

Retrieves a list of zone changes to a zone. All zone changes are stored, including those coming from zone syncs. Zone changes come in max page sizes of 100 changes, paging must be done independently using startFrom and nextId parameters.

#### HTTP REQUEST

> GET /zones/{zoneId}/changes?startFrom={response.nextId}&maxItems={1 - 100}

#### HTTP REQUEST PARAMS

name          | type          | required?   | description |
 ------------ | ------------- | ----------- | :---------- |
startFrom     | string        | no          | In order to advance through pages of results, the startFrom is set to the `nextId` that is returned on the previous response.  It is up to the client to maintain previous pages if the client wishes to advance forward and backward.   If not specified, will return the first page of results |
maxItems      | int           | no          | The number of items to return in the page.  Valid values are 1 - 100. Defaults to 100 if not provided. |

#### HTTP RESPONSE TYPES

Code          | description |
 ------------ | :---------- |
200           | **Accepted** - The zone changes will be returned in the response body|
401           | **Unauthorized** - The authentication information provided is invalid.  Typically the request was not signed properly, or the access key and secret used to sign the request are incorrect |
403           | **Forbidden** - The user does not have the access required to perform the action |
404           | **Not Found** - Zone not found |

#### HTTP RESPONSE ATTRIBUTES

name          | type                  | description |
 ------------ | --------------------- | :---------- |
zoneId        | string                | Id of zone used for request |
zoneChanges   | array of zone changes | Array of zone changes sorted by created time in descending order. Refer to [Zone Change](#zone-change) |
startFrom     | string                | (optional) The startFrom parameter that was sent in on the HTTP request.  Will not be present if the startFrom parameter was not sent |
nextId        | string                | (optional) The identifier to be passed in as the *startFrom* parameter to retrieve the next page of results.  If there are no results left, this field will not be present |
maxItems      | int                   | The maxItems parameter that was sent in on the HTTP request.  This will be 100 if not sent |

#### ZONE CHANGE ATTRIBUTES <a id="zone-change"></a>

name                | type          | description |
 -----------------  | ------------- | :---------- |
zone                | map           | Refer to [zone model](zone-model.html) |
status              | string        | The status of the change. Either Pending, Failed or Synced |
changeType          | string        | The type of change. Either Create, Update, Delete, Sync or AutomatedSync |
systemMessage       | string        | (optional) A message regarding the change.  Will not be present if the string is empty |
created             | string        | Millisecond timestamp that change was created
userId              | string        | User Id of user who made the change |
id                  | string        | Id of the group change |

#### EXAMPLE RESPONSE

```json
{
  "zoneId": "2467dc05-68eb-4498-a9d5-78d24bb0893c",
  "zoneChanges": [
      {
        "status": "Synced",
        "zone": {
          "status": "Active",
          "updated": "2016-12-30T15:37:57Z",
          "name": "system-test-history.",
          "adminGroupId": "67b4da23-6832-4600-8450-9fa0664caeeb",
          "created": "2016-12-30T15:37:56Z",
          "account": "67b4da23-6832-4600-8450-9fa0664caeeb",
          "email": "i.changed.this.10.times@history-test.com",
          "shared": true,
          "acl": {
            "rules": []
          },
          "id": "9f353bc7-cb8d-491c-b074-34afafc97c5f"
        },
        "created": "2016-12-30T15:37:57Z",
        "changeType": "Update",
        "userId": "history-id",
        "id": "6d4deccb-4632-475e-9ebc-3f6bace5fe68"
      },
      {
        "status": "Synced",
        "zone": {
          "status": "Active",
          "updated": "2016-12-30T15:37:57Z",
          "name": "system-test-history.",
          "adminGroupId": "67b4da23-6832-4600-8450-9fa0664caeeb",
          "created": "2016-12-30T15:37:56Z",
          "account": "67b4da23-6832-4600-8450-9fa0664caeeb",
          "email": "i.changed.this.9.times@history-test.com",
          "shared": true,
          "acl": {
            "rules": []
          },
          "id": "9f353bc7-cb8d-491c-b074-34afafc97c5f"
        },
        "created": "2016-12-30T15:37:57Z",
        "changeType": "Update",
        "userId": "history-id",
        "id": "59c2db90-41aa-49ae-8c56-e13a2ada918d"
      }
  ],
  "startFrom": "2910234",
  "nextId": "1034932",
  "maxItems": 2
}
```
