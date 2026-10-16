---
layout: docs
title: "Get Batch Change Count"
section: "api"
---

# Get Batch Change Count

Gets the total number of matching batch changes and counts grouped by batch status.
By default, only batch changes submitted by the calling user are counted.

#### HTTP REQUEST

> GET /zones/batchrecordchanges/count

#### HTTP REQUEST QUERY PARAMETERS

name               | type    | required | description |
 ----------------- | ------- | -------- | :---------- |
userName           | string  | no       | Filter by exact submitter user name. An empty value is ignored. |
dateTimeRangeStart | string  | no       | Inclusive lower bound on the batch creation time, for example `2026-01-01 00:00:00`. An empty value is ignored. |
dateTimeRangeEnd   | string  | no       | Inclusive upper bound on the batch creation time, for example `2026-01-31 23:59:59`. An empty value is ignored. |
ignoreAccess       | boolean | no       | Defaults to `false`. When `true`, system administrators (super and support users) can count batch changes across all users. For other users, this flag is ignored rather than rejected, and counts remain restricted to their own batch changes. Other query filters still apply. |
approvalStatus     | string  | no       | Filter by approval status: **AutoApproved**, **PendingReview**, **ManuallyApproved**, **ManuallyRejected**, or **Cancelled**. Values are case-insensitive. An unrecognized value, including an empty value, returns HTTP 400. |

#### EXAMPLE HTTP REQUEST

```http
GET /zones/batchrecordchanges/count?userName=vinyl201&dateTimeRangeStart=2026-01-01%2000%3A00%3A00&dateTimeRangeEnd=2026-01-31%2023%3A59%3A59&ignoreAccess=true&approvalStatus=AutoApproved
```

#### HTTP RESPONSE TYPES

Code          | description |
 ------------ | :---------- |
200           | **OK** - The total and per-status batch change counts are returned. |
400           | **Bad Request** - An unrecognized `approvalStatus` or an invalid boolean value for `ignoreAccess` was provided. |
401           | **Unauthorized** - The authentication information provided is invalid. Typically the request was not signed properly, or the access key and secret used to sign the request are incorrect. |

#### HTTP RESPONSE ATTRIBUTES

name              | type    | description |
 ---------------- | ------- | :---------- |
total             | integer | Total number of matching batch changes, including rows with unrecognized stored batch statuses. |
complete          | integer | Number with batch status `Complete`. |
failed            | integer | Number with batch status `Failed`. |
partialFailure    | integer | Number with batch status `PartialFailure`. |
rejected          | integer | Number with batch status `Rejected`. |
cancelled         | integer | Number with batch status `Cancelled`. |
pendingReview     | integer | Number with batch status `PendingReview`. |
scheduled         | integer | Number with batch status `Scheduled`. |
pendingProcessing | integer | Number with batch status `PendingProcessing`. |

All nine fields are returned, with zero values when no batch changes match.
Counts use the stored batch status. Legacy rows with an empty status, or rows with
another unrecognized status, contribute to `total` but not to any status bucket.
As a result, `total` can exceed the sum of the eight status buckets.

#### EXAMPLE RESPONSE

```json
{
  "total": 10,
  "complete": 4,
  "failed": 1,
  "partialFailure": 1,
  "rejected": 0,
  "cancelled": 0,
  "pendingReview": 0,
  "scheduled": 1,
  "pendingProcessing": 3
}
```
