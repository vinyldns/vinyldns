---
layout: docs
title: "Generated Zone Model"
section: "api"
---

# Generated Zone Model

#### Table of Contents

- [Generated Zone Overview](#generated-zone-overview)
- [Generated Zone Attributes](#generated-zone-attributes)
- [Provider Params](#provider-params)
- [Provider Response](#provider-response)
- [Generated Zone Example](#generated-zone-example)

#### GENERATED ZONE OVERVIEW <a id="generated-zone-overview"></a>

A generated zone is a DNS zone that VinylDNS **creates on a DNS server** through that server's management API (for example,
PowerDNS or a BIND management service). This is different from [Create Zone](create-zone.html), which connects VinylDNS to
a zone that already exists.

Making a new zone is a two-step process:

1. **Generate** the zone on the DNS provider with [Generate Zone](generate-zone.html). VinylDNS sends the request to the
   provider and stores a generated zone record.
2. **Connect** to the new zone with [Create Zone](create-zone.html), using the same zone name, so that you can manage its
   records in VinylDNS.

The DNS providers that can be used are set by the VinylDNS operator. See
[DNS Provider API configuration](../operator/config-api.html#dns-provider-api). Use
[Get DNS Providers](get-dns-providers.html) to see which providers are available.

Access to a generated zone is controlled by its admin group (`groupId`):

- Members of the admin group, and super users, can view, update and delete the zone.
- Support users can view the zone, but cannot update or delete it.
- Other users cannot see it, and it is not returned to them when listing generated zones.

#### GENERATED ZONE ATTRIBUTES <a id="generated-zone-attributes"></a>

field          | type          | description |
 ------------- | ------------- | :---------- |
zoneName       | string        | The name of the zone. Must end with a dot and contain only letters, digits, `-` and `.`, for example `example.com.`. Zone names are unique across generated zones. |
groupId        | string        | The ID of the admin group for the zone. Members of this group can update and delete the zone. |
email          | string        | The contact email for the zone. It must pass the configured [email domain validation](../operator/config-api.html#email-domain-configuration). |
provider       | string        | The DNS provider the zone was generated on, for example `bind` or `powerdns`. It cannot be changed after the zone is generated. |
providerParams | map           | Settings specific to the provider, sent to the provider when the zone is generated or updated. See [Provider Params](#provider-params). |
status         | string        | The status of the zone. Always `Active` for a generated zone that exists. |
response       | map           | The response from the provider for the latest change made to the zone. See [Provider Response](#provider-response). Set by VinylDNS. |
id             | string        | The unique ID of the generated zone. Set by VinylDNS. |
created        | date-time     | The time (UTC) the zone was generated. Set by VinylDNS. |
updated        | date-time     | The time (UTC) the zone was last updated. Set by VinylDNS; absent if the zone was never updated. |

`id`, `status`, `response`, `created` and `updated` are set by VinylDNS. If you include them in a request, they are ignored.

When listing generated zones, each zone also includes:

field          | type          | description |
 ------------- | ------------- | :---------- |
groupName      | string        | The name of the admin group, or `Unknown group name` if the group cannot be found. |
accessLevel    | string        | Your access to the zone: `Delete` if you are in the admin group or a super user (you can update and delete it), `Read` if you are a support user (you can only view it). |

#### PROVIDER PARAMS <a id="provider-params"></a>

`providerParams` must be a JSON object. Which params are allowed and which are required depend on the provider. VinylDNS
checks them against a JSON schema that the operator configures for each provider and operation (`create-zone` and
`update-zone`), and rejects the request with **400 Bad Request** if they do not match.

The tables below match the provider configuration shipped with VinylDNS. Your installation may differ. In the shipped
configuration, any param not listed for a provider is rejected.

**PowerDNS**

param          | type          | create   | update   | description |
 ------------- | ------------- | -------- | -------- | :---------- |
kind           | string        | required | optional | `Native` or `Master` |
nameservers    | array         | required | not allowed | Name servers for the zone. Each must be a fully qualified name ending with a dot. At least one is required. |
masters        | array         | optional | optional | Primary servers for the zone |

**BIND**

param              | type          | create   | update   | description |
 ----------------- | ------------- | -------- | -------- | :---------- |
nameservers        | array         | required | required | Name servers for the zone. At least one is required. |
admin_email        | string        | required | required | Email address of the zone administrator |
ttl                | integer       | optional | optional | Default TTL |
refresh            | integer       | optional | optional | SOA refresh |
retry              | integer       | optional | optional | SOA retry |
expire             | integer       | optional | optional | SOA expire |
negative_cache_ttl | integer       | optional | optional | SOA negative cache TTL |

#### PROVIDER RESPONSE <a id="provider-response"></a>

field          | type          | description |
 ------------- | ------------- | :---------- |
responseCode   | integer       | The HTTP status code the provider returned |
status         | string        | The HTTP status message the provider returned |
message        | any           | The body the provider returned, as JSON. Its contents depend on the provider. |
changeType     | string        | The change that produced this response: `Create`, `Update` or `Delete` |

#### GENERATED ZONE EXAMPLE <a id="generated-zone-example"></a>

```json
{
  "groupId": "9b22b686-54bc-47fb-a8f8-cdc48e6d04ae",
  "email": "test@example.com",
  "provider": "powerdns",
  "zoneName": "example.com.",
  "status": "Active",
  "providerParams": {
    "kind": "Master",
    "nameservers": ["ns1.example.com.", "ns2.example.com."]
  },
  "response": {
    "responseCode": 201,
    "status": "Created",
    "message": {
      "id": "example.com.",
      "name": "example.com.",
      "kind": "Master"
    },
    "changeType": "Create"
  },
  "id": "a6c1d0e2-3f1b-4b7a-9a3f-1c2d3e4f5a6b",
  "created": "2026-10-22T14:03:11Z"
}
```
