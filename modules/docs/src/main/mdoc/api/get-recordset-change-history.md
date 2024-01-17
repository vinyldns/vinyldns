---
layout: docs
title: "Get RecordSet Change History"
section: "api"
---

# Get RecordSet Change History

Gets the history of all the changes that has been made to a recordset

#### HTTP REQUEST

> GET /recordsetchange/history?fqdn={recordsetFqdn}&recordType={recordsetType}&startFrom={response.nextId}&maxItems={1 - 100}

#### HTTP REQUEST PARAMS

name          | type          | required?   | description |
 ------------ | ------------- | ----------- | :---------- |
fqdn          | string        | yes         | The fqdn of the recordset whose history will be returned |
recordType    | string        | yes         | The record type of the recordset |
startFrom     | int           | no          | In order to advance through pages of results, the startFrom is set to the `nextId` that is returned on the previous response.  It is up to the client to maintain previous pages if the client wishes to advance forward and backward.   If not specified, will return the first page of results |
maxItems      | int           | no          | The number of items to return in the page.  Valid values are 1 - 100. Defaults to 100 if not provided. |

#### HTTP RESPONSE TYPES

Code          | description |
 ------------ | :---------- |
200           | **OK** - The record set change history is returned in the response body
401           | **Unauthorized** - The authentication information provided is invalid.  Typically the request was not signed properly, or the access key and secret used to sign the request are incorrect |
403           | **Forbidden** - The user does not have the access required to perform the action |
404           | **Not Found** - The history for provided recordset was not found |

#### HTTP RESPONSE ATTRIBUTES

name                 | type          | description |
 ------------------- | ------------- | :---------- |
zoneId               | string        | The id of the zone where the recordset is present |
recordSetChanges     | array of recordset changes | Array of recordset changes sorted by created time in descending order |
startFrom            | int           | (optional) The startFrom parameter that was sent in on the HTTP request.  Will not be present if the startFrom parameter was not sent |
nextId               | int           | (optional) The identifier to be passed in as the *startFrom* parameter to retrieve the next page of results.  If there are no results left, this field will not be present |
maxItems             | int           | The maxItems parameter that was sent in on the HTTP request.  This will be 100 if not sent |

#### EXAMPLE RESPONSE

```json
{
  "zoneId": "56b03014-7f68-4a9b-b5b6-c0e6a212992d",
  "recordSetChanges": [
    {
      "zone": {
        "name": "ok.",
        "email": "test@test.com",
        "status": "Active",
        "created": "2023-09-27T07:41:12Z",
        "id": "56b03014-7f68-4a9b-b5b6-c0e6a212992d",
        "account": "system",
        "shared": false,
        "acl": {
          "rules": []
        },
        "adminGroupId": "7e56dfdf-df1b-4a39-a3e1-2977db13a1fd",
        "latestSync": "2023-09-27T07:41:13Z",
        "isTest": false
      },
      "recordSet": {
        "type": "A",
        "zoneId": "56b03014-7f68-4a9b-b5b6-c0e6a212992d",
        "name": "ok.",
        "ttl": 38400,
        "status": "Active",
        "created": "2023-09-27T07:41:28Z",
        "updated": "2023-09-27T07:41:28Z",
        "records": [
          {
            "address": "5.5.5.6"
          }
        ],
        "id": "cda5f6e1-b103-41ad-af7d-013a4379c591",
        "account": "system"
      },
      "userId": "83bd9eda-145d-4799-98fb-20d0bf1ed1e1",
      "changeType": "Update",
      "status": "Complete",
      "created": "2023-09-27T07:41:28Z",
      "updates": {
        "type": "A",
        "zoneId": "56b03014-7f68-4a9b-b5b6-c0e6a212992d",
        "name": "ok.",
        "ttl": 38400,
        "status": "Active",
        "created": "2023-09-27T07:41:13Z",
        "records": [
          {
            "address": "5.5.5.5"
          }
        ],
        "id": "cda5f6e1-b103-41ad-af7d-013a4379c591",
        "account": "system"
      },
      "id": "55994cb7-9dcb-41ff-980c-44385d1d2cfa",
      "userName": "professor"
    },
    {
      "zone": {
        "name": "ok.",
        "email": "test@test.com",
        "status": "Syncing",
        "created": "2023-09-27T07:41:12Z",
        "id": "56b03014-7f68-4a9b-b5b6-c0e6a212992d",
        "account": "system",
        "shared": false,
        "acl": {
          "rules": []
        },
        "adminGroupId": "7e56dfdf-df1b-4a39-a3e1-2977db13a1fd",
        "isTest": false
      },
      "recordSet": {
        "type": "A",
        "zoneId": "56b03014-7f68-4a9b-b5b6-c0e6a212992d",
        "name": "ok.",
        "ttl": 38400,
        "status": "Active",
        "created": "2023-09-27T07:41:13Z",
        "records": [
          {
            "address": "5.5.5.5"
          }
        ],
        "id": "cda5f6e1-b103-41ad-af7d-013a4379c591",
        "account": "system"
      },
      "userId": "83bd9eda-145d-4799-98fb-20d0bf1ed1e1",
      "changeType": "Create",
      "status": "Complete",
      "created": "2023-09-27T07:41:13Z",
      "systemMessage": "Change applied via zone sync",
      "id": "3575868a-ccde-40ec-aaeb-b33cb8410eaf",
      "userName": "professor"
    }
  ],
  "maxItems": 100
}
```
