---
layout: docs
title: "List RecordSet Change Failures"
section: "api"
---

# List Recordset Change Failures

Retrieves a list of RecordSet changes that failed in a zone.

#### HTTP REQUEST

> GET metrics/health/zones/{zoneId}/recordsetchangesfailure?startFrom={response.nextId}&maxItems={1 - 100}

#### HTTP REQUEST PARAMS

name          | type          | required?   | description |
 ------------ | ------------- | ----------- | :---------- |
startFrom     | int           | no          | In order to advance through pages of results, the startFrom is set to the `nextId` that is returned on the previous response.  It is up to the client to maintain previous pages if the client wishes to advance forward and backward.   If not specified, will return the first page of results |
maxItems      | int           | no          | The number of items to return in the page.  Valid values are 1 - 100. Defaults to 100 if not provided. |

#### HTTP RESPONSE TYPES

Code          | description |
 ------------ | :---------- |
200           | **OK** - the list of failed recordset changes are returned in response body |
401           | **Unauthorized** - The authentication information provided is invalid.  Typically the request was not signed properly, or the access key and secret used to sign the request are incorrect |
403           | **Forbidden** - The user does not have the access required to perform the action |
404           | **Not Found** - Zone not found |

#### HTTP RESPONSE ATTRIBUTES

name          | type          | description |
 ------------ | ------------- | :---------- |
failedRecordSetChanges   | array of failed recordset changes | array of recordset changes sorted by created time in descending order |
startFrom     | int           | (optional) The startFrom parameter that was sent in on the HTTP request.  Will not be present if the startFrom parameter was not sent |
nextId        | int           | (optional) The identifier to be passed in as the *startFrom* parameter to retrieve the next page of results.  If there are no results left, this field will not be present |
maxItems      | int           | The maxItems parameter that was sent in on the HTTP request.  This will be 100 if not sent |

#### EXAMPLE RESPONSE

```json
{
  "failedRecordSetChanges": [
    {
      "zone": {
        "name": "ok.",
        "email": "test@test.com",
        "status": "Active",
        "created": "2020-11-17T18:50:46Z",
        "updated": "2023-09-28T13:51:30Z",
        "id": "fbf7a440-891c-441a-ad09-e1cbc861fda1",
        "account": "system",
        "shared": true,
        "acl": {
          "rules": []
        },
        "adminGroupId": "7611734a-8409-4827-9e8b-960d115b9f9c",
        "scheduleRequestor": "testuser",
        "latestSync": "2023-09-28T13:51:30Z",
        "isTest": true,
        "backendId": "func-test-backend"
      },
      "recordSet": {
        "type": "DS",
        "zoneId": "fbf7a440-891c-441a-ad09-e1cbc861fda1",
        "name": "ns-test-2",
        "ttl": 7200,
        "status": "Inactive",
        "created": "2023-09-28T16:44:47Z",
        "updated": "2023-09-28T16:44:47Z",
        "records": [
          {
            "keytag": 1,
            "algorithm": 3,
            "digesttype": 1,
            "digest": "01"
          }
        ],
        "id": "a40de44e-1b13-4cfe-9485-79749bd360fa",
        "account": "system",
        "ownerGroupId": "7611734a-8409-4827-9e8b-960d115b9f9c"
      },
      "userId": "6741d4df-81b7-40bd-9856-896c730c189b",
      "changeType": "Create",
      "status": "Failed",
      "created": "2023-09-28T16:44:47Z",
      "systemMessage": "Failed applying update to DNS for change 8094c9a9-d279-4847-81f4-de08f2454ff1:ns-test-2: Format Error: the server was unable to interpret the query: ;; ->>HEADER<<- opcode: UPDATE, status: FORMERR, id: 41792\n;; flags: qr ; qd: 1 an: 0 au: 0 ad: 0 \n;; TSIG invalid\n;; ZONE:\n;;\tok., type = SOA, class = IN\n\n;; PREREQUISITES:\n\n;; UPDATE RECORDS:\n\n;; ADDITIONAL RECORDS:\n\n;; Message size: 20 bytes",
      "id": "8094c9a9-d279-4847-81f4-de08f2454ff1",
      "singleBatchChangeIds": []
    }
  ],
  "nextId": 0,
  "startFrom": 0,
  "maxItems": 100
}
```
