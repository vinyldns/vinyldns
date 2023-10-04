---
layout: docs
title: "List RecordSet Changes Failures"
section: "api"
---

# List RecordSet Changes

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
200           | **OK** - the recordset changes are returned in response body|
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
        "scheduleRequestor": "ashah403",
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
    },
    {
      "zone": {
        "name": "ok.",
        "email": "test@test.com",
        "status": "Active",
        "created": "2020-11-17T18:50:46Z",
        "updated": "2023-06-01T14:13:22Z",
        "id": "fbf7a440-891c-441a-ad09-e1cbc861fda1",
        "account": "system",
        "shared": true,
        "acl": {
          "rules": []
        },
        "adminGroupId": "7611734a-8409-4827-9e8b-960d115b9f9c",
        "scheduleRequestor": "ashah403",
        "latestSync": "2023-06-01T14:13:22Z",
        "isTest": true,
        "backendId": "func-test-backend"
      },
      "recordSet": {
        "type": "A",
        "zoneId": "fbf7a440-891c-441a-ad09-e1cbc861fda1",
        "name": "dotted.a",
        "ttl": 38400,
        "status": "Inactive",
        "created": "2023-09-28T13:51:16Z",
        "updated": "2023-09-28T13:51:16Z",
        "records": [
          {
            "address": "7.7.7.9"
          }
        ],
        "id": "ea56629c-3a64-41ee-ace1-3a760b0e2736",
        "account": "system",
        "ownerGroupId": "7611734a-8409-4827-9e8b-960d115b9f9c"
      },
      "userId": "6741d4df-81b7-40bd-9856-896c730c189b",
      "changeType": "Update",
      "status": "Failed",
      "created": "2023-09-28T13:51:16Z",
      "systemMessage": "Failed validating update to DNS for change \"55b269c0-8a85-49b8-ba9d-45f9eeed5ee8\": \"dotted.a\": This record set is out of sync with the DNS backend; sync this zone before attempting to update this record set.",
      "updates": {
        "type": "A",
        "zoneId": "fbf7a440-891c-441a-ad09-e1cbc861fda1",
        "name": "dotted.a",
        "ttl": 38400,
        "status": "Active",
        "created": "2023-08-03T15:02:29Z",
        "updated": "2023-08-03T15:02:29Z",
        "records": [
          {
            "address": "7.7.7.8"
          }
        ],
        "id": "ea56629c-3a64-41ee-ace1-3a760b0e2736",
        "account": "system",
        "ownerGroupId": "7611734a-8409-4827-9e8b-960d115b9f9c"
      },
      "id": "55b269c0-8a85-49b8-ba9d-45f9eeed5ee8",
      "singleBatchChangeIds": []
    },
    {
      "zone": {
        "name": "ok.",
        "email": "test@test.com",
        "status": "Active",
        "created": "2020-11-17T18:50:46Z",
        "updated": "2020-11-17T18:51:11Z",
        "id": "fbf7a440-891c-441a-ad09-e1cbc861fda1",
        "connection": {
          "name": "ok.",
          "keyName": "vinyldns.",
          "key": "OBF:1:y5nYhKJw814AABAACr9MsZ9sBj22GkE446KkrVc2az9sQOrEHnKT595TuRhY7gSmdw4z6ZNQmYMtP8NOar423Hac5yiCReQNB7XTt65y1IvVCXswKl7xTzbwZTcSFWu4sB93MrCc7P/04t4l/IFY+kyKmMF7",
          "primaryServer": "96.115.238.13",
          "algorithm": "HMAC-MD5"
        },
        "transferConnection": {
          "name": "ok.",
          "keyName": "vinyldns.",
          "key": "OBF:1:HIX6SfJs490AABAAFK00YLMl20SjLUNFNla9X584uZAqnv6lFIXR8vrDzmyrvWeD6O+gVMpgq+l8eWZEjLjTMTuXgGymv+g4+G8kF4wEQ0lVngp3/8sm5QN6ZSwcMd/ekWqsn6T5ebjmBkwPqe27/JNoMSOI",
          "primaryServer": "96.115.238.13",
          "algorithm": "HMAC-MD5"
        },
        "account": "system",
        "shared": false,
        "acl": {
          "rules": []
        },
        "adminGroupId": "fc080a97-ead6-4869-b32f-2b97fd27e1ab",
        "latestSync": "2020-11-17T18:50:46Z",
        "isTest": true
      },
      "recordSet": {
        "type": "A",
        "zoneId": "fbf7a440-891c-441a-ad09-e1cbc861fda1",
        "name": "backend-conflict",
        "ttl": 38400,
        "status": "Inactive",
        "created": "2020-11-17T18:51:41Z",
        "updated": "2020-11-17T18:51:41Z",
        "records": [
          {
            "address": "7.7.7.7"
          }
        ],
        "id": "a9735fdf-f1fc-4666-bdc0-fc345e288a70",
        "account": "system"
      },
      "userId": "ok",
      "changeType": "Create",
      "status": "Failed",
      "created": "2020-11-17T18:51:41Z",
      "systemMessage": "Failed validating update to DNS for change 6cacfe9a-ffb2-4c63-bcf4-3af6e727b54e:backend-conflict: Incompatible record already exists in DNS.",
      "id": "6cacfe9a-ffb2-4c63-bcf4-3af6e727b54e",
      "singleBatchChangeIds": []
    },
    {
      "zone": {
        "name": "ok.",
        "email": "test@test.com",
        "status": "Active",
        "created": "2020-11-17T18:50:46Z",
        "updated": "2020-11-17T18:51:06Z",
        "id": "fbf7a440-891c-441a-ad09-e1cbc861fda1",
        "connection": {
          "name": "ok.",
          "keyName": "vinyldns.",
          "key": "OBF:1:y5nYhKJw814AABAACr9MsZ9sBj22GkE446KkrVc2az9sQOrEHnKT595TuRhY7gSmdw4z6ZNQmYMtP8NOar423Hac5yiCReQNB7XTt65y1IvVCXswKl7xTzbwZTcSFWu4sB93MrCc7P/04t4l/IFY+kyKmMF7",
          "primaryServer": "96.115.238.13",
          "algorithm": "HMAC-MD5"
        },
        "transferConnection": {
          "name": "ok.",
          "keyName": "vinyldns.",
          "key": "OBF:1:HIX6SfJs490AABAAFK00YLMl20SjLUNFNla9X584uZAqnv6lFIXR8vrDzmyrvWeD6O+gVMpgq+l8eWZEjLjTMTuXgGymv+g4+G8kF4wEQ0lVngp3/8sm5QN6ZSwcMd/ekWqsn6T5ebjmBkwPqe27/JNoMSOI",
          "primaryServer": "96.115.238.13",
          "algorithm": "HMAC-MD5"
        },
        "account": "system",
        "shared": false,
        "acl": {
          "rules": [
            {
              "accessLevel": "Write",
              "description": "some_test_rule",
              "userId": "list-batch-summaries-id",
              "recordTypes": []
            }
          ]
        },
        "adminGroupId": "fc080a97-ead6-4869-b32f-2b97fd27e1ab",
        "latestSync": "2020-11-17T18:50:46Z",
        "isTest": true
      },
      "recordSet": {
        "type": "A",
        "zoneId": "fbf7a440-891c-441a-ad09-e1cbc861fda1",
        "name": "direct-to-backend",
        "ttl": 200,
        "status": "Inactive",
        "created": "2020-11-17T18:51:08Z",
        "updated": "2020-11-17T18:51:08Z",
        "records": [
          {
            "address": "4.5.6.7"
          }
        ],
        "id": "c401cd27-2116-441d-85fd-0c8bb89a2dfb",
        "account": "system"
      },
      "userId": "ok",
      "changeType": "Create",
      "status": "Failed",
      "created": "2020-11-17T18:51:08Z",
      "systemMessage": "Failed validating update to DNS for change 101ab18e-1c75-45db-af65-f3c993adbd09:direct-to-backend: Incompatible record already exists in DNS.",
      "id": "101ab18e-1c75-45db-af65-f3c993adbd09",
      "singleBatchChangeIds": [
        "5ee8c027-5e69-46fb-bbbb-a75af00bed4b"
      ]
    },
    {
      "zone": {
        "name": "ok.",
        "email": "test@test.com",
        "status": "Active",
        "created": "2020-11-17T18:50:46Z",
        "updated": "2020-11-17T18:51:06Z",
        "id": "fbf7a440-891c-441a-ad09-e1cbc861fda1",
        "connection": {
          "name": "ok.",
          "keyName": "vinyldns.",
          "key": "OBF:1:y5nYhKJw814AABAACr9MsZ9sBj22GkE446KkrVc2az9sQOrEHnKT595TuRhY7gSmdw4z6ZNQmYMtP8NOar423Hac5yiCReQNB7XTt65y1IvVCXswKl7xTzbwZTcSFWu4sB93MrCc7P/04t4l/IFY+kyKmMF7",
          "primaryServer": "96.115.238.13",
          "algorithm": "HMAC-MD5"
        },
        "transferConnection": {
          "name": "ok.",
          "keyName": "vinyldns.",
          "key": "OBF:1:HIX6SfJs490AABAAFK00YLMl20SjLUNFNla9X584uZAqnv6lFIXR8vrDzmyrvWeD6O+gVMpgq+l8eWZEjLjTMTuXgGymv+g4+G8kF4wEQ0lVngp3/8sm5QN6ZSwcMd/ekWqsn6T5ebjmBkwPqe27/JNoMSOI",
          "primaryServer": "96.115.238.13",
          "algorithm": "HMAC-MD5"
        },
        "account": "system",
        "shared": false,
        "acl": {
          "rules": [
            {
              "accessLevel": "Write",
              "description": "some_test_rule",
              "userId": "list-batch-summaries-id",
              "recordTypes": []
            }
          ]
        },
        "adminGroupId": "fc080a97-ead6-4869-b32f-2b97fd27e1ab",
        "latestSync": "2020-11-17T18:50:46Z",
        "isTest": true
      },
      "recordSet": {
        "type": "A",
        "zoneId": "fbf7a440-891c-441a-ad09-e1cbc861fda1",
        "name": "backend-foo",
        "ttl": 200,
        "status": "Inactive",
        "created": "2020-11-17T18:51:07Z",
        "updated": "2020-11-17T18:51:08Z",
        "records": [
          {
            "address": "4.5.6.7"
          }
        ],
        "id": "62ff7f1b-c9d7-416c-b0dd-eb08d0adfa73",
        "account": "system"
      },
      "userId": "ok",
      "changeType": "Create",
      "status": "Failed",
      "created": "2020-11-17T18:51:07Z",
      "systemMessage": "Failed validating update to DNS for change 5b30ed83-735d-41b9-a99f-ce32fc4e9c16:backend-foo: Incompatible record already exists in DNS.",
      "id": "5b30ed83-735d-41b9-a99f-ce32fc4e9c16",
      "singleBatchChangeIds": [
        "714778f8-7ad2-45fb-aaad-1d34a8914df1"
      ]
    },
    {
      "zone": {
        "name": "ok.",
        "email": "test@test.com",
        "status": "Active",
        "created": "2020-11-17T18:50:46Z",
        "updated": "2020-11-17T18:51:06Z",
        "id": "fbf7a440-891c-441a-ad09-e1cbc861fda1",
        "connection": {
          "name": "ok.",
          "keyName": "vinyldns.",
          "key": "OBF:1:y5nYhKJw814AABAACr9MsZ9sBj22GkE446KkrVc2az9sQOrEHnKT595TuRhY7gSmdw4z6ZNQmYMtP8NOar423Hac5yiCReQNB7XTt65y1IvVCXswKl7xTzbwZTcSFWu4sB93MrCc7P/04t4l/IFY+kyKmMF7",
          "primaryServer": "96.115.238.13",
          "algorithm": "HMAC-MD5"
        },
        "transferConnection": {
          "name": "ok.",
          "keyName": "vinyldns.",
          "key": "OBF:1:HIX6SfJs490AABAAFK00YLMl20SjLUNFNla9X584uZAqnv6lFIXR8vrDzmyrvWeD6O+gVMpgq+l8eWZEjLjTMTuXgGymv+g4+G8kF4wEQ0lVngp3/8sm5QN6ZSwcMd/ekWqsn6T5ebjmBkwPqe27/JNoMSOI",
          "primaryServer": "96.115.238.13",
          "algorithm": "HMAC-MD5"
        },
        "account": "system",
        "shared": false,
        "acl": {
          "rules": [
            {
              "accessLevel": "Write",
              "description": "some_test_rule",
              "userId": "list-batch-summaries-id",
              "recordTypes": []
            }
          ]
        },
        "adminGroupId": "fc080a97-ead6-4869-b32f-2b97fd27e1ab",
        "latestSync": "2020-11-17T18:50:46Z",
        "isTest": true
      },
      "recordSet": {
        "type": "A",
        "zoneId": "fbf7a440-891c-441a-ad09-e1cbc861fda1",
        "name": "backend-already-exists",
        "ttl": 200,
        "status": "Inactive",
        "created": "2020-11-17T18:51:07Z",
        "updated": "2020-11-17T18:51:08Z",
        "records": [
          {
            "address": "4.5.6.7"
          }
        ],
        "id": "72582d0b-68c6-42e4-bb03-c7ef8653b51a",
        "account": "system"
      },
      "userId": "ok",
      "changeType": "Create",
      "status": "Failed",
      "created": "2020-11-17T18:51:07Z",
      "systemMessage": "Failed validating update to DNS for change f7abdacd-ba33-418f-9e8f-13ac08504914:backend-already-exists: Incompatible record already exists in DNS.",
      "id": "f7abdacd-ba33-418f-9e8f-13ac08504914",
      "singleBatchChangeIds": [
        "72f09313-9e64-4b9a-9212-02d28592a1da"
      ]
    }
  ],
  "nextId": 0,
  "startFrom": 0,
  "maxItems": 100
}
```
