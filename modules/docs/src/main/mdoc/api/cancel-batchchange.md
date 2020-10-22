---
layout: docs
title: "Cancel Batch Change"
section: "api"
---

# Cancel Batch Change

Cancels a batch change that is PendingReview. Only the user who created the batch change can cancel it.

Note: If [manual review is disabled](../operator/config-api.md#manual-review) in the VinylDNS instance,
users trying to access this endpoint will encounter a **404 Not Found** response since it will not exist.

#### HTTP REQUEST

> POST /zones/batchrecordchanges/{id}/cancel


#### HTTP REQUEST PARAMS

name          | type          | required?   | description |
 ------------ | :------------ | ----------- | :---------- |
id            | string        | yes         | Unique identifier assigned to each created batch change. |


#### HTTP RESPONSE TYPES

Code          | description |
 ------------ | :---------- |
200           | **OK** - The batch change is returned in response body. |
403           | **Forbidden** - The user does not have the access required to perform the action. |
404           | **Not Found** - Batch change not found. |


#### HTTP RESPONSE ATTRIBUTES <a id="http-response-attributes" />

See [Batch Change Model attributes](batchchange-model.md#batchchange-attributes)
