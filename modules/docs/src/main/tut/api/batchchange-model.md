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
-   All record validations are processed simultaneously. [Fatal errors](../api/batchchange-errors#fatal-errors) for any
change in the batch will result in a **400** response and none will be applied.
-   Support for [manual review](../../operator/config-api#additional-configuration-settings) if enabled in your VinylDNS instance.
Batch change will remain in limbo until a system administrator (ie. support or super user) either rejects it resulting in
an immediate failure or approves it resulting in revalidation and submission for processing.
-   Support for [notifications](../../operator/config-api#additional-configuration-settings) when a batch change is rejected or implemented.

A batch change consists of multiple single changes which can be a combination of [SingleAddChanges](#singleaddchange-attributes) and [SingleDeleteRRSetChanges](#singledeleterrsetchange-attributes).

**Note:** In the portal batch change is referred to as [DNS Change](../portal/dns-changes).

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
createdTimestamp | date-time      | The timestamp (UTC) when the batch change was created. |
changes       | Array of SingleChange | Array of single changes within a batch change. A *SingleChange* can either be a [SingleAddChange](#singleaddchange-attributes) or a [SingleDeleteRRSetChange](#singledeleterrsetchange-attributes). |
status        | BatchChangeStatus | - **PendingProcessing** - at least one change in batch has not finished processing<br>- **Complete** - all changes have been processed successfully<br>- **Failed** - all changes failed during processing<br>- **PartialFailure** - some changes have failed and the rest were successful<br>- **PendingReview** - one or more changes requires manual [approval](../api/approve-batchchange)/[rejection](../api/reject-batchchange) by a system administrator (ie. super or support user) to proceed<br>- **Rejected** - the batch change was rejected by a system administrator (ie. super or support user) and no changes were applied<br>- **Scheduled** - the batch change is scheduled for a later date at which time it needs to be approved to proceed.<br>- **Cancelled** - the PendingReview batch change was cancelled by its creator before review.|
id            | string      | The unique identifier for this batch change. |
ownerGroupId  | string      | Record ownership assignment. Required if any records in the batch change are in shared zones and are new or unowned. |
approvalStatus | BatchChangeApprovalStatus | Whether the batch change is currently awaiting manual review. Can be one of **AutoApproved**, **PendingReview**, **ManuallyApproved**, **Rejected**, or **Cancelled**. |
reviewerId    | string      | Optional unique identifier for the reviewer of the batch change. Required if batch change was manually rejected or approved. |
reviewerUserName  | string      | Optional user name for the reviewer of the batch change. Required if batch change was manually rejected or approved. |
reviewComment | string      | Optional comment for the reviewer of the batch change. Only applicable if batch change was manually rejected or approved. |
reviewTimestamp | date-time  | Optional timestamp (UTC) of when the batch change was manually reviewed. Required if batch change was manually rejected or approved. |
scheduledTime  | date-time   | Optional requested date and time to process the batch change. |
cancelledTimestamp | date-time | Optional date and time a batch change was cancelled by its creator. |

#### SINGLE CHANGE ATTRIBUTES <a id="singlechange-attributes" />

A successful batch change response consists of a corresponding [SingleAddChange](#singleaddchange-attributes) or [SingleDeleteRRSetChange](#singledeleterrsetchange-attributes) for each batch change input. See the [batch change create](../api/create-batchchange) page for details on constructing a batch change request.

#### SingleAddChange <a id="singleaddchange-attributes" />

name          | type          | description |
 ------------ | :------------ | :---------- |
changeType    | ChangeInputType | Type of change input. Can either be an **Add** or **DeleteRecordSet**.  [See more details](#changetype-values) about behavior of `changeType` interaction. |
inputName     | string        | The fully-qualified domain name of the record which was provided in the create batch request. |
type          | RecordType    | Type of DNS record, supported records for batch changes are currently: **A**, **AAAA**, **CNAME**, and **PTR**. |
ttl           | long          | The time-to-live in seconds. |
record        | [RecordData](../api/recordset-model#record-data)    | The data added for this record, which varies by record type. |
status        | SingleChangeStatus | Status for this change. Can be one of: **Pending**, **Complete**, **Failed**, **NeedsReview** or **Rejected**. |
recordName    | string        | The name of the record. Record names for the apex will be match the zone name (including terminating dot). |
zoneName      | string        | The name of the zone. |
zoneId        | string        | The unique identifier for the zone. |
systemMessage | string        | Conditional: Returns system message relevant to corresponding batch change input. |
recordChangeId| string        | Conditional: The unique identifier for the record change; only returned on successful batch creation. |
recordSetId   | string        | Conditional: The unique identifier for the record set; only returned on successful batch creation, |
validationErrors | Array of BatchChangeError | Array containing any validation errors associated with this SingleAddChange. *Note: These will only exist on `NeedsReview` or `Rejected` `SingleChange`s* |
id            | string        | The unique identifier for this change. |

#### SingleDeleteRRSetChange <a id="singledeleterrsetchange-attributes" />

name          | type          | description |
 ------------ | :------------ | :---------- |
changeType    | ChangeInputType | Type of change input. Can either be an **Add** or **DeleteRecordSet**.  [See more details](#changetype-values) about behavior of `changeType` interaction. |
inputName     | string        | The fully-qualified domain name of the record which was provided in the create batch request. |
type          | RecordType    | Type of DNS record, supported records for batch changes are currently: **A**, **AAAA**, **CNAME**, and **PTR**. |
record        | [RecordData](../api/recordset-model#record-data)    | Optional. The data deleted for this record, which varies by record type.  If not provided, the entire DNS recordset was deleted. | 
status        | SingleChangeStatus | Status for this change. Can be one of: **Pending**, **Complete**, **Failed**, **NeedsReview** or **Rejected**. |
recordName    | string        | The name of the record. Record names for the apex will be match the zone name (including terminating dot). |
zoneName      | string        | The name of the zone. |
zoneId        | string        | The unique identifier for the zone. |
systemMessage | string        | Conditional: Returns system message relevant to corresponding batch change input. |
recordChangeId| string        | Conditional: The unique identifier for the record change; only returned on successful batch creation. |
recordSetId   | string        | Conditional: The unique identifier for the record set; only returned on successful batch creation, |
validationErrors | Array of BatchChangeError | Array containing any validation errors associated with this SingleDeleteRRSetChange. *Note: These will only exist on `NeedsReview` or `Rejected` SingleChanges* |
id            | string        | The unique identifier for this change. |


#### ChangeType Values <a id="changetype-values" />
There are two valid `changeType`s for a `SingleChange`: **Add** and **DeleteRecordSet**

`changeType`s can be used independently or combined to achieve the desired behavior described below.

- Create a new DNS record: **Add** with record data
- Delete an entire record set: 1. **DeleteRecordSet** without specifying existing record data *or* 2. **DeleteRecordSet** for each entry of the DNS record
- Delete a single entry from DNS record with multiple entries: **DeleteRecordSet** specifying existing record data
- Update an existing record set (i.e. value reassignment): 1. **DeleteRecordSet** specifying existing record data (single entry delete) or not specifying record data (full delete) *and* 2. **Add** with record data

#### BATCH CHANGE EXAMPLE <a id="batchchange-example" />

Successful batch change response example with a [SingleAddChange](#singleaddchange-attributes) and a [SingleDeleteRRSetChange](#singledeleterrsetchange-attributes). 

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
        },
        {
            "changeType": "DeleteRecordSet",
            "inputName": "anotherRecordName.zoneName.",
            "type": "A",
            "record": {
                "address": "1.1.1.1"
            },
            "status": "Complete",
            "recordName": "recordName",
            "zoneName": "zoneName.",
            "zoneId": "9cbdd3ac-9752-4d56-9ca0-6a1a14fc5562",
            "recordChangeId": "9c449026-cffe-4379-beb7-217b8a31aadd",
            "recordSetId": "e68776ab-f56f-41bf-a03e-c288b9469b53",
            "validationErrors": [],
            "id": "e68776ab-f56f-41bf-a03e-c288b9469b53"
        }
    ], 
    "status": "Complete", 
    "id": "937191c4-b1fd-4ab5-abb4-9553a65b44ab",
    "approvalStatus": "AutoApproved"
}
```
