---
layout: docs
title: "Batch Change Model"
section: "api"
---

# Batch Change Model

#### Table of Contents

- [Batch Change Information](#batchchange-info)
- [Batch Change Attributes](#batchchange-attributes)
- [Single Change Attributes](#singlechange-attributes)
- [Batch Change Example](#batchchange-example)

#### BATCH CHANGE INFORMATION <a id="batchchange-info" />

Batch change is an alternative to submitting individual [RecordSet](../api/recordset-model) changes and provides the following:

-   The ability to accept multiple changes in a single API call.
-   The ability to include records of multiple record types across multiple zones.
-   Input names are entered as fully-qualified domain names (or IP addresses for **PTR** records), so users don't have to think in record/zone context.
-   All record validations are processed simultaneously. [Hard errors](../api/batchchange-errors/#hard-errors) for any
change in the batch will result in a **400** response and none will be applied.
-   Support for manual review if configured on your VinylDNS instance where batches that contain only [soft errors](../api/batchchange-errors/#soft-errors)
enter a manual review state. Batch change will remain in limbo until a system administrator (ie. support or super user)
either rejects it resulting in an immediate failure or approves it resulting in revalidation and submission for processing.

A batch change consists of multiple single changes which can be a combination of [SingleAddChanges](#singleaddchange-attributes) and [SingleDeleteChanges](#singledeletechange-attributes).

To update an existing record, you must delete the record first and add the record again with the updated changes.

Batch changes are also susceptible to the following restrictions:
-   Current supported record types for batch change are: **A**, **AAAA**, **CNAME**, and **PTR**.
-   Batch change requests must contain at least one change.
-   The maximum number of single changes within a batch change is currently set to 20.
-   Access permissions will follow existing rules (admin group or ACL access). Note that an update (delete and add of the same record name, zone and record type combination) requires **Write** access.


#### BATCH CHANGE ATTRIBUTES <a id="batchchange-attributes" />

name          | type        | description |
 ------------ | :---------- | :---------- |
userId        | string      | The unique identifier of the user that created the batch change. |
userName      | string      | The username of the user that created the batch change. |
comments      | string      | Optional comments about the batch change. |
createdTimestamp | date-time      | The timestamp (in GMT) when the batch change was created. |
changes       | Array of SingleChange | Array of single changes within a batch change. A *SingleChange* can either be a [SingleAddChange](#singleaddchange-attributes) or a [SingleDeleteChange](#singledeletechange-attributes). |
status        | BatchChangeStatus | **Pending** - at least one change in batch in still in pending state; **Complete** - all changes are in complete state; **Failed** - all changes are in failure state; **PartialFailure** - some changes have failed and the rest are complete. |
id            | string      | The unique identifier for this batch change. |
ownerGroupId  | string      | Record ownership assignment. Required if any records in the batch change are in shared zones and are new or unowned. |
approvalStatus | string      | Whether the batch change is currently awaiting manual review. Can be one of **AutoApproved**, **PendingReview**, **ManuallyApproved** or **Rejected**. |
reviewerId    | string      | Optional unique identifier for the reviewer of the batch change. Required if batch change was manually rejected or approved. |
reviewerUserName  | string      | Optional user name for the reviewer of the batch change. Required if batch change was manually rejected or approved. |
reviewComment | string      | Optional comment for the reviewer of the batch change. Only applicable if batch change was manually rejected or approved. |
reviewTimestamp | date-time  | Optional timestamp (in GMT) of when the batch change was manually reviewed. Required if batch change was manually rejected or approved. |

#### SINGLE CHANGE ATTRIBUTES <a id="singlechange-attributes" />

A successful batch change response consists of a corresponding [SingleAddChange](#singleaddchange-attributes) or [SingleDeleteChange](#singledeletechange-attributes) for each batch change input. See the [batch change create](../api/create-batchchange) page for details on constructing a batch change request.

#### SingleAddChange <a id="singleaddchange-attributes" />

name          | type          | description |
 ------------ | :------------ | :---------- |
changeType    | ChangeInputType | Type of change input. Can either be an **Add** or **DeleteRecordSet**. |
inputName     | string        | The fully-qualified domain name of the record which was provided in the create batch request. |
type          | RecordType    | Type of DNS record, supported records for batch changes are currently: **A**, **AAAA**, **CNAME**, and **PTR**. |
ttl           | long          | The time-to-live in seconds. |
record        | [RecordData](../api/recordset-model/#record-data)    | The data added for this record, which varies by record type. | 
status        | SingleChangeStatus | Status for this change. Can be one of: **Pending**, **Complete**, **Failed**, **NeedsReview** or **Rejected**. |
recordName    | string        | The name of the record. Record names for the apex will be match the zone name (including terminating dot). |
zoneName      | string        | The name of the zone. |
zoneId        | string        | The unique identifier for the zone. |
systemMessage | string        | Conditional: Returns system message relevant to corresponding batch change input. |
recordChangeId| string        | Conditional: The unique identifier for the record change; only returned on successful batch creation. |
recordSetId   | string        | Conditional: The unique identifier for the record set; only returned on successful batch creation, |
validationErrors | Array of BatchChangeError | Array containing any validation errors associated with this SingleAddChange. *Note: These will only exist on `NeedsReview` or `Rejected` `SingleChange`s* |
id            | string        | The unique identifier for this change. |

#### SingleDeleteChange <a id="singledeletechange-attributes" />

name          | type          | description |
 ------------ | :------------ | :---------- |
changeType    | ChangeInputType | Type of change input. Can either be an **Add** or **DeleteRecordSet**. |
inputName     | string        | The fully-qualified domain name of the record which was provided in the create batch request. |
type          | RecordType    | Type of DNS record, supported records for batch changes are currently: **A**, **AAAA**, **CNAME**, and **PTR**. | 
status        | SingleChangeStatus | Status for this change. Can be one of: **Pending**, **Complete**, **Failed**, **NeedsReview** or **Rejected**. |
recordName    | string        | The name of the record. Record names for the apex will be match the zone name (including terminating dot). |
zoneName      | string        | The name of the zone. |
zoneId        | string        | The unique identifier for the zone. |
systemMessage | string        | Conditional: Returns system message relevant to corresponding batch change input. |
recordChangeId| string        | Conditional: The unique identifier for the record change; only returned on successful batch creation. |
recordSetId   | string        | Conditional: The unique identifier for the record set; only returned on successful batch creation, |
validationErrors | Array of BatchChangeError | Array containing any validation errors associated with this SingleDeleteChange. *Note: These will only exist on `NeedsReview` or `Rejected` `SingleChange`s* |
id            | string        | The unique identifier for this change. |


#### BATCH CHANGE EXAMPLE <a id="batchchange-example" />

Successful batch change response example with a [SingleAddChange](#singleaddchange-attributes) and a [SingleDeleteChange](#singledeletechange-attributes). 

```
{
    "userId": "vinyl", 
    "userName": "vinyl201", 
    "comments": "this is optional", 
    "createdTimestamp": "2018-05-08T18:46:34Z", 
    "ownerGroupId": "f42385e4-5675-38c0-b42f-64105e743bfe"
    "changes": [
        {
            "changeType": "Add",
            "inputName": "recordName.zoneName.", 
            "type": "A", 
            "ttl": 3600,  
            "record": {
                "address": "1.1.1.1"
            },
            "status": "Complete", 
            "recordName": "recordName", 
            "zoneName": "zoneName.", 
            "zoneId": "8f8f649f-998e-4428-a029-b4ba5f5bd4ca",
            "recordChangeId": "4754ac4c-5f81-11e8-9c2d-fa7ae01bbebc",
            "recordSetId": "4754b084-5f81-11e8-9c2d-fa7ae01bbebc",
            "validationErrors": [],
            "id": "17350028-b2b8-428d-9f10-dbb518a0364d"
        }, 
        {
            "changeType": "DeleteRecordSet",
            "inputName": "recordName.zoneName.", 
            "type": "AAAA", 
            "status": "Complete", 
            "recordName": "recordName", 
            "zoneName": "zoneName.", 
            "zoneId": "9cbdd3ac-9752-4d56-9ca0-6a1a14fc5562",
            "recordChangeId": "4754b322-5f81-11e8-9c2d-fa7ae01bbebc",
            "recordSetId": "4754b084-5f81-11e8-9c2d-fa7ae01bbebc",
            "validationErrors": [],
            "id": "c29d33e4-9bee-4417-a99b-6e815fdeb748"
        }
    ], 
    "status": "Complete", 
    "id": "937191c4-b1fd-4ab5-abb4-9553a65b44ab",
    "approvalStatus": "AutoApproved"
}
```
