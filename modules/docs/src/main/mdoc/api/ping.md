---
layout: docs
title: "Ping"
section: "api"
---

# Ping

Simple health check endpoint that returns "PONG" if the API server is running.

**Note:** This endpoint does not require authentication.

#### HTTP REQUEST

> GET /ping

#### EXAMPLE HTTP REQUEST

```http
GET /ping
```

#### HTTP RESPONSE TYPES

Code          | description |
 ------------ | :---------- |
200           | **OK** - The API server is running |

#### EXAMPLE RESPONSE

```
PONG
```
