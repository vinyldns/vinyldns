---
layout: docs
title: "Health Check"
section: "api"
---

# Health Check

Comprehensive health check that verifies connectivity to backend services including the zone manager.

**Note:** This endpoint does not require authentication.

#### HTTP REQUEST

> GET /health

#### HTTP RESPONSE TYPES

Code          | description |
 ------------ | :---------- |
200           | **OK** - All health checks passed |
500           | **Internal Server Error** - One or more health checks failed |

#### EXAMPLE RESPONSE

On success, the endpoint returns an empty 200 OK response.

On failure, the endpoint returns a 500 error with details about the failed health check.
