---
layout: docs
title: "List / Search RecordSets"
section: "api"
---

# List / Search RecordSets by Zone

Retrieves a list of RecordSets from the zone.

#### HTTP REQUEST

> GET /zones/{zoneId}/recordsets?startFrom={response.nextId}&maxItems={1 - 100}&recordNameFilter={filter}

#### HTTP REQUEST PARAMS

name          | type          | required?   | description |
 ------------ | ------------- | ----------- | :---------- |
recordNameFilter    | string        | no          | Characters that are part of the record name to search for.  The wildcard character `*` is supported, for example `www*`.  Omit the wildcard when searching for an exact record name. |
recordTypeFilter    | Array of RecordType | no | An array of record types to filter for listing record sets.  Refer to [recordset mode](recordset-model.html) for supported types.  Invalid record types will be ignored.  If left empty or no valid record types are provided, then all record types will be returned. |
nameSort          | string        | no          | Name sort order for record sets returned by list record set response.  Valid values are `ASC` (ascending; default) and `DESC` (descending).
startFrom     | string            | no          | In order to advance through pages of results, the startFrom is set to the `nextId` that is returned on the previous response.  It is up to the client to maintain previous pages if the client wishes to advance forward and backward.   If not specified, will return the first page of results |
maxItems      | integer           | no          | The number of items to return in the page.  Valid values are 1 to 100. Defaults to 100 if not provided. |

#### HTTP RESPONSE TYPES

Code          | description |
 ------------ | :---------- |
200           | **OK** - The record sets are returned in the response body |
401           | **Unauthorized** - The authentication information provided is invalid.  Typically the request was not signed properly, or the access key and secret used to sign the request are incorrect |
403           | **Forbidden** - The user does not have the access required to perform the action |
404           | **Not Found** - Zone or RecordSet not found |

#### HTTP RESPONSE ATTRIBUTES

name          | type          | description |
 ------------ | ------------- | :---------- |
recordSets    | Array of RecordSets | refer to [recordset model](recordset-model.html), the RecordSet data will also include the accessLevel the requesting user has based off acl rules and membership in Zone Admin Group |
startFrom     | string        | startFrom sent in request, will not be returned if not provided |
nextId        | string        | nextId, used as startFrom parameter of next page request, will not be returned if record sets are exhausted |
maxItems      | integer       | maxItems sent in request, default is 100 |
recordNameFilter    | string  | name filter sent in request |
recordTypeFilter    | Array of RecordType | record type filter sent in request |
nameSort            | string  | name sort order sent in request

#### EXAMPLE RESPONSE

```json
{
  "recordSets": [
    {
      "type": "A",
      "zoneId": "2467dc05-68eb-4498-a9d5-78d24bb0893c",
      "name": "already-exists",
      "ttl": 38400,
      "status": "Active",
      "created": "2017-02-23T15:12:41Z",
      "updated": "2017-02-23T15:12:41Z",
      "records": [
        {
          "address": "6.6.6.1"
        }
      ],
      "id": "dd9c1120-0594-4e61-982e-8ddcbc8b2d21",
      "account": "9b22b686-54bc-47fb-a8f8-cdc48e6d04ae",
      "accessLevel": "Delete"
    },
    {
      "type": "NS",
      "zoneId": "2467dc05-68eb-4498-a9d5-78d24bb0893c",
      "name": "vinyl",
      "ttl": 38400,
      "status": "Active",
      "created": "2017-02-23T15:12:33Z",
      "records": [
        {
          "nsdname": "172.17.42.2."
        }
      ],
      "id": "daf5ea7b-c28c-422a-ba47-2c37ca567a77",
      "account": "9b22b686-54bc-47fb-a8f8-cdc48e6d04ae",
      "accessLevel": "Delete"
    },
    {
      "type": "SOA",
      "zoneId": "2467dc05-68eb-4498-a9d5-78d24bb0893c",
      "name": "vinyl",
      "ttl": 38400,
      "status": "Active",
      "created": "2017-02-23T15:12:33Z",
      "records": [
        {
          "mname": "172.17.42.2.",
          "rname": "admin.test.com.",
          "serial": 1439234395,
          "refresh": 10800,
          "retry": 3600,
          "expire": 604800,
          "minimum": 38400
        }
      ],
      "id": "9da83158-05ab-4f14-8bd0-0a4d85cdeb30",
      "account": "9b22b686-54bc-47fb-a8f8-cdc48e6d04ae",
      "accessLevel": "Delete"
    },
    {
      "type": "A",
      "zoneId": "2467dc05-68eb-4498-a9d5-78d24bb0893c",
      "name": "vinyl",
      "ttl": 38400,
      "status": "Active",
      "created": "2017-02-23T15:12:33Z",
      "records": [
        {
          "address": "5.5.5.5"
        }
      ],
      "id": "d73275ff-e71e-4024-aef1-1236741443b5",
      "account": "9b22b686-54bc-47fb-a8f8-cdc48e6d04ae",
      "accessLevel": "Delete"
    },
    {
      "type": "A",
      "zoneId": "2467dc05-68eb-4498-a9d5-78d24bb0893c",
      "name": "jenkins",
      "ttl": 38400,
      "status": "Active",
      "created": "2017-02-23T15:12:33Z",
      "records": [
        {
          "address": "10.1.1.1"
        }
      ],
      "id": "0432f63b-3947-4262-9ade-a3311d07a099",
      "account": "9b22b686-54bc-47fb-a8f8-cdc48e6d04ae",
      "accessLevel": "Delete"
    },
    {
      "type": "A",
      "zoneId": "2467dc05-68eb-4498-a9d5-78d24bb0893c",
      "name": "test",
      "ttl": 38400,
      "status": "Active",
      "created": "2017-02-23T15:12:33Z",
      "records": [
        {
          "address": "4.4.4.4"
        },
        {
          "address": "3.3.3.3"
        }
      ],
      "id": "dc0e3ce9-ec01-47f1-9418-461dc1754f48",
      "account": "9b22b686-54bc-47fb-a8f8-cdc48e6d04ae",
      "accessLevel": "Delete"
    }
  ],
  "maxItems": 100,
  "nameSort": "ASC"
}
```
