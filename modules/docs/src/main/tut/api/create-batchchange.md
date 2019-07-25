---
layout: docs
title: "Create Batch Change"
section: "api"
---

# Create Batch Change

Creates a batch change with [SingleAddChanges](../api/batchchange-model/#singleaddchange-attributes) and/or [SingleDeleteChanges](../api/batchchange-model/#singledeletechange-attributes) across different zones.  A delete and add of the same record will be treated as an update on that record set. Regardless of the input order in the batch change, all deletes for the same recordset will be logically applied before the adds. 
                                                                                                
Current supported record types for creating a batch change are: **A**, **AAAA**, **CNAME**, **MX**, **PTR**, **TXT**. A batch must contain at least one change and no more than 20 changes.
Supported record types for records in shared zones may vary. Contact your VinylDNS administrators to find the allowed record types.
This does not apply to zone administrators or users with specific ACL access rules.

#### HTTP REQUEST

> POST /zones/batchrecordchanges?allowManualReview={true &#124; false}

Note that the batch change request inputs are a subset of the full [batch change model](../api/batchchange-model/#batchchange-attributes).

#### HTTP REQUEST PARAMS

name          | type          | required?   | description |
 ------------ | :------------ | ----------- | :---------- |
comments      | string        | no          | Optional comments about the batch change. |
changes       | Array of ChangeInput| yes         | Set of *ChangeInput*s in the batch change. A *ChangeInput*  is an [AddChangeInput](#addchangeinput-attributes) or [DeleteChangeInput](#deletechangeinput-attributes). Type is inferred from specified *changeType*.|
ownerGroupId  | string        | sometimes   | Record ownership assignment. Required if any records in the batch change are in [shared zones](../api/zone-model#shared-zones) and are new or unowned. |
allowManualReview | boolean       | no          | Optional override to control whether manual review is enabled for the batch change request. Default value is `true`. |

##### AddChangeInput <a id="addchangeinput-attributes" />

name          | type          | required?   | description |
 ------------ | :------------ | ----------- | :---------- |
changeType    | ChangeInputType | yes       | Type of change input. Must be set to **Add** for *AddChangeInput*. |
inputName     | string        | yes         | The fully qualified domain name of the record being added. For **PTR**, the input name is a valid IPv4 or IPv6 address. |
type          | RecordType    | yes         | Type of DNS record. Supported records for batch changes are currently: **A**, **AAAA**, **CNAME**, and **PTR**. |
ttl           | long          | no          | The time-to-live in seconds. The minimum and maximum values are 30 and 2147483647, respectively. If excluded, this will be set to the system default for new adds, or the existing TTL for updates |
record        | [RecordData](../api/recordset-model/#record-data) | yes         | The data for the record. |

##### DeleteChangeInput <a id="deletechangeinput-attributes" />

name          | type          | required?   | description |
 ------------ | :------------ | ----------- | :---------- |
changeType    | ChangeInputType | yes       | Type of change input. Must be **DeleteRecordSet** for *DeleteChangeInput*. |
inputName     | string        | yes         | The fully qualified domain name of the record being deleted. |
type          | RecordType    | yes         | Type of DNS record. Supported records for batch changes are currently: **A**, **AAAA**, **CNAME**, and **PTR**. |


#### EXAMPLE HTTP REQUEST
```
{
    "comments": "this is optional",
    "ownerGroupId": "f42385e4-5675-38c0-b42f-64105e743bfe"
    "changes": [
        {
            "inputName": "example.com.",
            "changeType": "Add",
            "type": "A",  
            "ttl": 3600, 
            "record": {
                "address": "1.1.1.1"
            } 
        }, 
        {
            "inputName": "192.0.2.195",
            "changeType": "Add",
            "type": "PTR", 
            "ttl": 3600,
            "record": {
                "ptrdname": "ptrdata.data."
            }
        }, 
        {
            "inputName": "cname.example.com.",
            "changeType": "DeleteRecordSet",
            "type": "CNAME"
        }, 
        {
            "inputName": "update.another.example.com.",
            "changeType": "DeleteRecordSet",
            "type": "AAAA"
        }, 
        {
            "inputName": "update.another.example.com.",
            "changeType": "Add",
            "type": "AAAA", 
            "ttl": 4000,
            "record": {
                "address": "1:2:3:4:5:6:7:8"
            }
        }
    ]
}
```

The first two items in the changes list are SingleAddChanges of an **A** record and a **PTR** record. Note that for the **PTR** record, the *inputName* is a valid IP address. The third item is a delete of a **CNAME** record. The last two items represent an update (delete & add) of an **AAAA** record with the fully qualified domain name "update.another.example.com.". 


#### HTTP RESPONSE TYPES

Code          | description |
 ------------ | :---------- |
202           | **Accepted** - The batch change is queued and is returned in the response body. |
400           | **Bad Request** - Error in the batch change. See [Batch Change Errors](../api/batchchange-errors) page. |
403           | **Forbidden** - The user does not have the access required to perform the action. |
413           | **Request Entity Too Large** - Cannot request more than <limit> changes in a single batch change request. |
422           | **Unprocessable Entity** - the batch does not contain any changes, thus cannot be processed. |

A batch change goes through numerous validations before it is processed. This results in corresponding BadRequest or error responses. View the full list of batch change errors [here](../api/batchchange-errors).

#### HTTP RESPONSE ATTRIBUTES

On success, the response from create batch change includes the fields the user input, as well as some additional information provided by the system. This response is the same as that of [get batch change](../api/get-batchchange/#http-response-attributes).


#### EXAMPLE RESPONSE

```
{
    "userId": "vinyl", 
    "userName": "vinyl201", 
    "comments": "this is optional", 
    "createdTimestamp": "2018-05-09T14:19:34Z",
    "ownerGroupId": "f42385e4-5675-38c0-b42f-64105e743bfe" 
    "changes": [
        {
            "changeType": "Add",
            "inputName": "example.com.", 
            "type": "A", 
            "ttl": 3600, 
            "record": {
                "address": "1.1.1.1"
            }, 
            "status": "Pending", 
            "recordName": "example.com.", 
            "zoneName": "example.com.", 
            "zoneId": "74e93bfc-7296-4b86-83d3-1ffcb0eb3d13",
            "validationErrors": [],
            "id": "7573ca11-3e30-45a8-9ba5-791f7d6ae7a7"
        }, 
        {
            "changeType": "Add",
            "inputName": "192.0.2.195", 
            "type": "PTR", 
            "ttl": 3600, 
            "record": {
                "ptrdname": "ptrdata.data."
            }, 
            "status": "Pending", 
            "recordName": "195", 
            "zoneName": "2.0.192.in-addr.arpa.", 
            "zoneId": "7fd52634-5a0c-11e8-9c2d-fa7ae01bbebc",
            "validationErrors": [],
            "id": "bece5338-5a0c-11e8-9c2d-fa7ae01bbebc"
        }, 
        {
            "changeType": "DeleteRecordSet", 
            "inputName": "cname.example.com.", 
            "type": "CNAME", 
            "status": "Pending",
            "recordName": "cname", 
            "zoneName": "example.com.", 
            "zoneId": "74e93bfc-7296-4b86-83d3-1ffcb0eb3d13",
            "validationErrors": [],
            "id": "02048500-5a0d-11e8-a10f-fa7ae01bbebc" 
        }, 
        {
            "changeType": "DeleteRecordSet",
            "inputName": "update.example.com.", 
            "type": "AAAA", 
            "status": "Pending",
            "recordName": "update", 
            "zoneName": "example.com.", 
            "zoneId": "74e93bfc-7296-4b86-83d3-1ffcb0eb3d13",
            "validationErrors": [],
            "id": "1cee1c78-5a0d-11e8-9c2d-fa7ae01bbebc" 
        }, 
        {
            "changeType": "Add",
            "inputName": "update.another.example.com.", 
            "type": "AAAA", 
            "ttl": 3600, 
            "record": {
                "address": "1:2:3:4:5:6:7:8"
            }, 
            "status": "Pending", 
            "recordName": "update", 
            "zoneName": "another.example.com.", 
            "zoneId": "7fd52634-5a0c-11e8-9c2d-fa7ae01bbebc",
            "validationErrors": [],
            "id": "43dd1226-5a0d-11e8-9c2d-fa7ae01bbebc"
        }
    ], 
    "status": "Pending", 
    "id": "02bd95f4-a32c-443b-82eb-54dbaa55b31a"
}
```
