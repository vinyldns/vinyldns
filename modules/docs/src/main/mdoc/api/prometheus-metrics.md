---
layout: docs
title: "Prometheus Metrics"
section: "api"
---

# Prometheus Metrics

Exports metrics in Prometheus format for monitoring and observability.

**Note:** This endpoint does not require authentication.

#### HTTP REQUEST

> GET /metrics/prometheus

#### HTTP REQUEST PARAMETERS

name          | type          | required?     | description |
 ------------ | ------------- | ------------- | :---------- |
name          | string        | no            | Optional metric name filter. Can be specified multiple times to filter to specific metrics. If not provided, all metrics are returned. |

#### HTTP RESPONSE TYPES

Code          | description |
 ------------ | :---------- |
200           | **OK** - Metrics returned in Prometheus text format |

#### EXAMPLE REQUEST

Get all metrics:
```
GET /metrics/prometheus
```

Get specific metrics:
```
GET /metrics/prometheus?name=jvm_memory_bytes_used
```

#### EXAMPLE RESPONSE

```
# HELP jvm_memory_bytes_used Used bytes of a given JVM memory area.
# TYPE jvm_memory_bytes_used gauge
jvm_memory_bytes_used{area="heap",} 1.073741824E9
jvm_memory_bytes_used{area="nonheap",} 1.048576E8
```
