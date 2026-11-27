---
layout: docs
title: "List / Search Generated Zones"
section: "api"
---

# List / Search Generated Zones

Lists the generated zones you have access to, sorted by zone name. Super and support users see all generated zones;
other users see only the zones whose admin group they belong to.

#### HTTP REQUEST

> GET /zones/generate/info?nameFilter={yoursearchhere}&startFrom={response.nextId}&maxItems={1 - 100}&searchByAdminGroup={true | false}

#### HTTP REQUEST PARAMS

name                | type          | required?   | description |
 ------------------ | ------------- | ----------- | :---------- |
nameFilter          | string        | no          | Filters the results. By default this matches zone names: a value ending in `.` or containing `*` is a pattern (`*` is a wildcard); any other value matches names that start with it. If `searchByAdminGroup` is `true`, this matches admin group names that start with it instead (`*` is a wildcard). |
startFrom           | string        | no          | To get the next page, set this to the `nextId` from the previous response |
maxItems            | integer       | no          | The number of zones to return, from 1 to 100. The default is 100. |
searchByAdminGroup  | boolean       | no          | If `true`, `nameFilter` matches admin group names instead of zone names. The default is `false`. Ignored if no `nameFilter` is given. |

#### EXAMPLE HTTP REQUEST

```http
GET /zones/generate/info?nameFilter=example&maxItems=2
```

#### HTTP RESPONSE TYPES

Code          | description |
 ------------ | :---------- |
200           | **OK** - The matching zones are returned in the response body |
400           | **Bad Request** - `maxItems` is out of range |
401           | **Unauthorized** - The authentication information provided is invalid.  Typically the request was not signed properly, or the access key and secret used to sign the request are incorrect |

#### HTTP RESPONSE ATTRIBUTES

name          | type          | description |
 ------------ | ------------- | :---------- |
zones         | array of generated zones | The generated zones. Each has the [generated zone attributes](generated-zone-model.html#generated-zone-attributes), plus `groupName` and `accessLevel`. |
nameFilter    | string        | The `nameFilter` sent in the request |
startFrom     | string        | The `startFrom` sent in the request |
nextId        | string        | Send this as `startFrom` to get the next page. Absent when there are no more zones. |
maxItems      | integer       | The `maxItems` used for the request |

#### EXAMPLE RESPONSE

```json
{
  "zones": [
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
        "changeType": "Create"
      },
      "id": "a6c1d0e2-3f1b-4b7a-9a3f-1c2d3e4f5a6b",
      "created": "2026-10-22T14:03:11Z",
      "groupName": "example-group",
      "accessLevel": "Delete"
    },
    {
      "groupId": "9b22b686-54bc-47fb-a8f8-cdc48e6d04ae",
      "email": "test@example.com",
      "provider": "bind",
      "zoneName": "example.net.",
      "status": "Active",
      "providerParams": {
        "nameservers": ["ns1.example.net."],
        "admin_email": "admin@example.net"
      },
      "response": {
        "responseCode": 200,
        "status": "OK",
        "changeType": "Create"
      },
      "id": "0f7e2c55-8a4d-4c1e-b7c2-6d9e8f1a2b3c",
      "created": "2026-10-21T09:12:00Z",
      "groupName": "example-group",
      "accessLevel": "Delete"
    }
  ],
  "nameFilter": "example",
  "nextId": "example.net.",
  "maxItems": 2
}
```
