---
layout: docs
title: "Color"
section: "api"
---

# Color

Returns the current color of the server for blue/green deployment purposes.

When deploying updates, this endpoint can be used to determine which server group (blue or green) is currently active, allowing traffic to be routed to the inactive group during deployment.

**Note:** This endpoint does not require authentication.

#### HTTP REQUEST

> GET /color

#### HTTP RESPONSE TYPES

Code          | description |
 ------------ | :---------- |
200           | **OK** - Returns the current deployment color |

#### EXAMPLE RESPONSE

```
blue
```

or

```
green
```
