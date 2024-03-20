---
layout: docs
title: "Get RecordSet"
section: "api"
---

# Get RecordSet

Gets a RecordSet in a specified zone.

#### HTTP REQUEST

> GET /zones/{zoneId}/recordsets/{recordSetId}

#### HTTP RESPONSE TYPES

Code          | description |
 ------------ | :---------- |
200           | **OK** - The record set is returned |
401           | **Unauthorized** - The authentication information provided is invalid.  Typically the request was not signed properly, or the access key and secret used to sign the request are incorrect |
403           | **Forbidden** - The user does not have the access required to perform the action |
404           | **Not Found** -  The zone with the id specified was not found, or the record set with id was not found |

#### HTTP RESPONSE ATTRIBUTES
The returned json object has all the fields from the RecordSet as well as an added accessLevel field

name          | type          | description |
 ------------ | ------------- | :---------- |
type          | string        | Type of record set |
zoneId        | string        | The zone the record is stored in |
name          | string        | The name of the record set |
ttl           | integer       | The TTL of the record set in seconds |
status        | string        | The status of the record set |
created       | string        | The timestamp (UTC) the change was initiated |
updated       | string        | The timestamp (UTC) the change was last updated |
records       | array of record data | Array of record data objects |
id            | string        | The unique ID of the record set |
account       | string        | **DEPRECATED** the ID of the account that created the record set |
accessLevel   | string        | accessLevel that user has to record set based off acl rules and whether or not user is in Zone Admin Group |
ownerGroupId  | string        | Record ownership assignment, if found, applicable if the recordset is in a [shared zone](zone-model.html#shared-zones) |
ownerGroupName   | string        | Name of assigned owner group, if found |

#### EXAMPLE RESPONSE

```json
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
  "accessLevel": "Delete",
  "ownerGroupId": "f42385e4-5675-38c0-b42f-64105e743bfe",
  "ownerGroupName": "Shared Group"
}
```
