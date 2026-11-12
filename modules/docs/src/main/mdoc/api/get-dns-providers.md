---
layout: docs
title: "Get DNS Providers and Name Servers"
section: "api"
---

# Get DNS Providers and Name Servers

These endpoints return information you need before calling [Generate Zone](generate-zone.html). Both come from the
operator's [DNS Provider API configuration](../operator/config-api.html#dns-provider-api). Any authenticated user can
call them.

## Get DNS Providers

Returns the DNS providers that zones can be generated on, in no particular order. Use one of these values as `provider`
in a generate zone request.

#### HTTP REQUEST

> GET /zones/generate/allowedDNSProviders

#### EXAMPLE RESPONSE

```json
["bind", "powerdns"]
```

## Get Name Servers

Returns the name servers configured for generated zones.

#### HTTP REQUEST

> GET /zones/generate/nameservers

#### EXAMPLE RESPONSE

```json
["172.17.42.1.", "ns1.parent.com."]
```

#### HTTP RESPONSE TYPES

Code          | description |
 ------------ | :---------- |
200           | **OK** - The list is returned in the response body. It is empty if nothing is configured. |
401           | **Unauthorized** - The authentication information provided is invalid.  Typically the request was not signed properly, or the access key and secret used to sign the request are incorrect |
