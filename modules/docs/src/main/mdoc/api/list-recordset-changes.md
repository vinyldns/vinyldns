---
layout: docs
title: "List RecordSet Changes"
section: "api"
---

# List RecordSet Changes

RecordSet changes (Create, Update, Delete) are not immediately applied to the DNS backend; they are queued up for processing.  Most changes are applied within a few seconds.
When you submit a change for processing, the response is a Change model.  You can use the information in that change model in order to poll for the status of the change until it completes (status = Complete) or fails (status = Failed).
<br><br>
Retrieves a list of RecordSet changes in a zone. All RecordSet changes are stored, including those coming from zone syncs. RecordSet changes come in max page sizes of 100 changes, paging must be done independently using startFrom and nextId parameters.

#### HTTP REQUEST

> GET /zones/{zoneId}/recordsetchanges?startFrom={response.nextId}&maxItems={1 - 100}

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
zoneId        | string        | Id of zone used for request |
recordSetChanges   | array of recordset changes | array of recordset changes sorted by created time in descending order |
startFrom     | int           | (optional) The startFrom parameter that was sent in on the HTTP request.  Will not be present if the startFrom parameter was not sent |
nextId        | int           | (optional) The identifier to be passed in as the *startFrom* parameter to retrieve the next page of results.  If there are no results left, this field will not be present |
maxItems      | int           | The maxItems parameter that was sent in on the HTTP request.  This will be 100 if not sent |
status        | string        | The status of the change (Pending, Complete, Failed) |

#### EXAMPLE RESPONSE

```json
{
  "recordSetChanges": [
      {
        "status": "Complete",
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
        "created": "2016-12-30T15:37:58Z",
        "recordSet": {
          "status": "Active",
          "updated": "2016-12-30T15:37:58Z",
          "name": "test-create-cname-ok",
          "created": "2016-12-30T15:37:57Z",
          "account": "history-id",
          "zoneId": "9f353bc7-cb8d-491c-b074-34afafc97c5f",
          "records": [
            {
              "cname": "changed-cname."
            }
          ],
          "ttl": 200,
          "type": "CNAME",
          "id": "f62235df-5372-443c-9ba4-bdd3fca452f4"
        },
        "changeType": "Delete",
        "userId": "history-id",
        "updates": {
          "status": "Active",
          "updated": "2016-12-30T15:37:58Z",
          "name": "test-create-cname-ok",
          "created": "2016-12-30T15:37:57Z",
          "account": "history-id",
          "zoneId": "9f353bc7-cb8d-491c-b074-34afafc97c5f",
          "records": [
            {
              "cname": "changed-cname."
            }
          ],
          "ttl": 200,
          "type": "CNAME",
          "id": "f62235df-5372-443c-9ba4-bdd3fca452f4"
        },
        "id": "68fd6dbe-0da8-4280-bcf3-37f54528dc41"
      },
      {
        "status": "Complete",
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
        "created": "2016-12-30T15:37:58Z",
        "recordSet": {
          "status": "Active",
          "updated": "2016-12-30T15:37:58Z",
          "name": "test-create-aaaa-ok",
          "created": "2016-12-30T15:37:57Z",
          "account": "history-id",
          "zoneId": "9f353bc7-cb8d-491c-b074-34afafc97c5f",
          "records": [
            {
              "address": "2003:db8:0:0:0:0:0:4"
            },
            {
              "address": "2002:db8:0:0:0:0:0:3"
            }
          ],
          "ttl": 200,
          "type": "AAAA",
          "id": "9559103d-4cb4-4d34-9d3f-eab3fe2e8aed"
        },
        "changeType": "Delete",
        "userId": "history-id",
        "updates": {
          "status": "Active",
          "updated": "2016-12-30T15:37:57Z",
          "name": "test-create-aaaa-ok",
          "created": "2016-12-30T15:37:57Z",
          "account": "history-id",
          "zoneId": "9f353bc7-cb8d-491c-b074-34afafc97c5f",
          "records": [
            {
              "address": "2003:db8:0:0:0:0:0:4"
            },
            {
              "address": "2002:db8:0:0:0:0:0:3"
            }
          ],
          "ttl": 200,
          "type": "AAAA",
          "id": "9559103d-4cb4-4d34-9d3f-eab3fe2e8aed"
        },
        "id": "dabf1e57-49e7-4d2d-8a00-814d88546b0c"
      },
      {
        "status": "Complete",
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
        "created": "2016-12-30T15:37:58Z",
        "recordSet": {
          "status": "Active",
          "updated": "2016-12-30T15:37:58Z",
          "name": "test-create-a-ok",
          "created": "2016-12-30T15:37:57Z",
          "account": "history-id",
          "zoneId": "9f353bc7-cb8d-491c-b074-34afafc97c5f",
          "records": [
            {
              "address": "9.9.9.9"
            },
            {
              "address": "10.2.2.2"
            }
          ],
          "ttl": 200,
          "type": "A",
          "id": "f1fd620e-5ff3-4ee9-839f-bc747a9867d9"
        },
        "changeType": "Delete",
        "userId": "history-id",
        "updates": {
          "status": "Active",
          "updated": "2016-12-30T15:37:57Z",
          "name": "test-create-a-ok",
          "created": "2016-12-30T15:37:57Z",
          "account": "history-id",
          "zoneId": "9f353bc7-cb8d-491c-b074-34afafc97c5f",
          "records": [
            {
              "address": "9.9.9.9"
            },
            {
              "address": "10.2.2.2"
            }
          ],
          "ttl": 200,
          "type": "A",
          "id": "f1fd620e-5ff3-4ee9-839f-bc747a9867d9"
        },
        "id": "23ae1487-bc7f-481b-a544-10ceb7a87540"
      },
      {
        "status": "Complete",
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
        "recordSet": {
          "status": "Active",
          "updated": "2016-12-30T15:37:58Z",
          "name": "test-create-cname-ok",
          "created": "2016-12-30T15:37:57Z",
          "account": "history-id",
          "zoneId": "9f353bc7-cb8d-491c-b074-34afafc97c5f",
          "records": [
            {
              "cname": "changed-cname."
            }
          ],
          "ttl": 200,
          "type": "CNAME",
          "id": "f62235df-5372-443c-9ba4-bdd3fca452f4"
        },
        "changeType": "Update",
        "userId": "history-id",
        "updates": {
          "status": "Active",
          "updated": "2016-12-30T15:37:57Z",
          "name": "test-create-cname-ok",
          "created": "2016-12-30T15:37:57Z",
          "account": "history-id",
          "zoneId": "9f353bc7-cb8d-491c-b074-34afafc97c5f",
          "records": [
            {
              "cname": "cname."
            }
          ],
          "ttl": 100,
          "type": "CNAME",
          "id": "f62235df-5372-443c-9ba4-bdd3fca452f4"
        },
        "id": "5c722555-c7be-4620-a1fd-8ca53a5b8683"
      },
      {
        "status": "Complete",
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
        "recordSet": {
          "status": "Active",
          "updated": "2016-12-30T15:37:57Z",
          "name": "test-create-aaaa-ok",
          "created": "2016-12-30T15:37:57Z",
          "account": "history-id",
          "zoneId": "9f353bc7-cb8d-491c-b074-34afafc97c5f",
          "records": [
            {
              "address": "2003:db8:0:0:0:0:0:4"
            },
            {
              "address": "2002:db8:0:0:0:0:0:3"
            }
          ],
          "ttl": 200,
          "type": "AAAA",
          "id": "9559103d-4cb4-4d34-9d3f-eab3fe2e8aed"
        },
        "changeType": "Update",
        "userId": "history-id",
        "updates": {
          "status": "Active",
          "updated": "2016-12-30T15:37:57Z",
          "name": "test-create-aaaa-ok",
          "created": "2016-12-30T15:37:57Z",
          "account": "history-id",
          "zoneId": "9f353bc7-cb8d-491c-b074-34afafc97c5f",
          "records": [
            {
              "address": "2001:db8:0:0:0:0:0:3"
            },
            {
              "address": "2002:db8:0:0:0:0:0:3"
            }
          ],
          "ttl": 100,
          "type": "AAAA",
          "id": "9559103d-4cb4-4d34-9d3f-eab3fe2e8aed"
        },
        "id": "480fff34-61d3-4a1d-9696-f5007842b38a"
      },
      {
        "status": "Complete",
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
        "recordSet": {
          "status": "Active",
          "updated": "2016-12-30T15:37:57Z",
          "name": "test-create-a-ok",
          "created": "2016-12-30T15:37:57Z",
          "account": "history-id",
          "zoneId": "9f353bc7-cb8d-491c-b074-34afafc97c5f",
          "records": [
            {
              "address": "9.9.9.9"
            },
            {
              "address": "10.2.2.2"
            }
          ],
          "ttl": 200,
          "type": "A",
          "id": "f1fd620e-5ff3-4ee9-839f-bc747a9867d9"
        },
        "changeType": "Update",
        "userId": "history-id",
        "updates": {
          "status": "Active",
          "updated": "2016-12-30T15:37:57Z",
          "name": "test-create-a-ok",
          "created": "2016-12-30T15:37:57Z",
          "account": "history-id",
          "zoneId": "9f353bc7-cb8d-491c-b074-34afafc97c5f",
          "records": [
            {
              "address": "10.1.1.1"
            },
            {
              "address": "10.2.2.2"
            }
          ],
          "ttl": 100,
          "type": "A",
          "id": "f1fd620e-5ff3-4ee9-839f-bc747a9867d9"
        },
        "id": "999d8674-e59b-478e-95c0-9d4eb964f2be"
      },
      {
        "status": "Complete",
        "zone": {
          "status": "Active",
          "updated": "2016-12-30T15:37:57Z",
          "name": "system-test-history.",
          "adminGroupId": "67b4da23-6832-4600-8450-9fa0664caeeb",
          "created": "2016-12-30T15:37:56Z",
          "account": "67b4da23-6832-4600-8450-9fa0664caeeb",
          "email": "i.changed.this.10.times@history-test.com",
          "connection": {
            "primaryServer": "127.0.0.1:5301",
            "keyName": "vinyl.",
            "name": "system-test-history.",
            "key": "OBF:1:YVgGogd/Y+oAABAAIp4s3z7FAn92uvfOci9v0jMjihQ+uV3bOCyNwpMPh78tL4q/A8dR7A=="
          },
          "transferConnection": {
            "primaryServer": "127.0.0.1:5301",
            "keyName": "vinyl.",
            "name": "system-test-history.",
            "key": "OBF:1:Pq3UqxiceV4AABAAdu90et1pkNn2ZO3MuYstki5BkQVm3T50RQLarpVhIgaoOKLi2CdL6Q=="
          },
          "shared": true,
          "acl": {
            "rules": []
          },
          "id": "9f353bc7-cb8d-491c-b074-34afafc97c5f"
        },
        "created": "2016-12-30T15:37:57Z",
        "recordSet": {
          "status": "Active",
          "updated": "2016-12-30T15:37:57Z",
          "name": "test-create-cname-ok",
          "created": "2016-12-30T15:37:57Z",
          "account": "history-id",
          "zoneId": "9f353bc7-cb8d-491c-b074-34afafc97c5f",
          "records": [
            {
              "cname": "cname."
            }
          ],
          "ttl": 100,
          "type": "CNAME",
          "id": "f62235df-5372-443c-9ba4-bdd3fca452f4"
        },
        "changeType": "Create",
        "userId": "history-id",
        "id": "b05f0837-84bd-47aa-8a95-7bde91046268"
      },
      {
        "status": "Complete",
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
        "recordSet": {
          "status": "Active",
          "updated": "2016-12-30T15:37:57Z",
          "name": "test-create-aaaa-ok",
          "created": "2016-12-30T15:37:57Z",
          "account": "history-id",
          "zoneId": "9f353bc7-cb8d-491c-b074-34afafc97c5f",
          "records": [
            {
              "address": "2001:db8:0:0:0:0:0:3"
            },
            {
              "address": "2002:db8:0:0:0:0:0:3"
            }
          ],
          "ttl": 100,
          "type": "AAAA",
          "id": "9559103d-4cb4-4d34-9d3f-eab3fe2e8aed"
        },
        "changeType": "Create",
        "userId": "history-id",
        "id": "e7e6b7f9-5253-4947-9580-3f0b81a48717"
      },
      {
        "status": "Complete",
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
        "recordSet": {
          "status": "Active",
          "updated": "2016-12-30T15:37:57Z",
          "name": "test-create-a-ok",
          "created": "2016-12-30T15:37:57Z",
          "account": "history-id",
          "zoneId": "9f353bc7-cb8d-491c-b074-34afafc97c5f",
          "records": [
            {
              "address": "10.1.1.1"
            },
            {
              "address": "10.2.2.2"
            }
          ],
          "ttl": 100,
          "type": "A",
          "id": "f1fd620e-5ff3-4ee9-839f-bc747a9867d9"
        },
        "changeType": "Create",
        "userId": "history-id",
        "id": "6743d428-7748-4348-a6c9-ae59e9eeaf97"
      }
    ],
  "maxItems": 100
}
```
