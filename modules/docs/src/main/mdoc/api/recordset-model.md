---
layout: docs
title: "RecordSet Model"
section: "api"
---

# RecordSet Model

#### Table of Contents

- [RecordSet Attributes](#recordset-attributes)
- [Record Data Information](#record-data)
- [Record Data Example](#record-data-example)

#### RecordSet ATTRIBUTES <a id="recordset-attributes"></a>

field         | type        | description |
 ------------ | :---------- | :---------- |
zoneId        | string      | the id of the zone to which this recordset belongs |
name          | string      | The name of the RecordSet |
type          | string      | Type of DNS record, supported records are currently: `A`, `AAAA`, `CNAME`, `DS`, `MX`, `NAPTR`, `NS`, `PTR`, `SOA`, `SRV`, `TXT`, `SSHFP`, and `SPF`. Unsupported types will be given the type `UNKNOWN` |
ttl           | long        |  the TTL in seconds for the recordset |
status        | string      | *Active* - RecordSet is added is created and ready for use, *Inactive* - RecordSet effects are not applied, *Pending* - RecordSet is queued for creation, *PendingUpdate* - RecordSet is queued for update, *PendingDelete* - RecordSet is queued for delete |
created       | date-time   | The timestamp (UTC) when the recordset was created   |
updated       | date-time   | The timestamp (UTC) when the recordset was last updated |
records       | Array of [RecordData](#record-data) | Array of record data, a single RecordSet can have multiple DNS records as long as they are all the same type|
id            | string      |  the id of the recordset.  This is important as you will use it for other recordset operations |
account       | string      | **DEPRECATED** The account that created the RecordSet |

#### RecordSet EXAMPLE <a id="recordset-example"></a>

```json
{
    "type": "A",
    "zoneId": "8f8f649f-998e-4428-a029-b4ba5f5bd4ca",
    "name": "foo",
    "ttl": 38400,
    "status": "Active",
    "created": "2017-02-22T21:34:35Z",
    "records": [
        {
            "address": "1.1.1.1"
        },
        {
            "address": "2.2.2.2"
        },
        {
            "address": "3.3.3.3"
        }
    ],
    "id": "8306cce4-e16a-4579-9b19-4af46dc75853",
    "account": "b34f8d18-646f-4843-a80a-7c0d58a22bf5"
}
```

#### RECORD DATA INFORMATION <a id="record-data"></a>
Current supported record types are: `A`, `AAAA`, `CNAME`, `DS`, `MX`, `NAPTR`, `NS`, `PTR`, `SOA`, `SRV`, `TXT`, `SSHFP`, and `SPF`.
Each individual record encodes its data in a record data object, in which each record type has different required attributes
<br><br>
`SOA` records and `NS` origin records (record with the same name as the zone) are currently read-only and cannot be created, updated or deleted.
Non-origin `NS` records can be created or updated for [approved name servers](../operator/config-api.html#additional-configuration-settings) only. Any non-origin `NS` record can be deleted.

record type  | attribute   | type        |
------------ | :---------- | :---------- |
`A`            | `address`     | `string`      |
<br>         |             |             |
`AAAA`         | `address`     | `string`      |
<br>         |             |             |
`CNAME`        | `cname`       | `string`      |
<br>         |             |             |
`DS`           | `keytag`      | `integer`     |
`DS`           | `algorithm`   | `integer`     |
`DS`           | `digesttype` | `integer`      |
`DS`           | `digest`      | `string`      |
<br>         |             |             |
`MX`           | `preference`  | `integer`     |
`MX`           | `exchange`    | `string`      |
<br>         |             |             |
`NAPTR`        | `order`       | `integer`     |
`NAPTR`        | `preference`  | `integer`     |
`NAPTR`        | `flags`       | `string`      |
`NAPTR`        | `service`     | `string`      |
`NAPTR`        | `regexp`      | `string`      |
`NAPTR`        | `replacement` | `string`      |
<br>         |             |             |
`NS`           | `nsdname`     | `string`      |
<br>         |             |             |
`PTR`          | `ptrdname`    | `string`      |
<br>         |             |             |
`SOA`          | `mname`       | `string`      |
`SOA`          | `rname`       | `string`      |
`SOA`          | `serial`      | `long`        |
`SOA`          | `refresh`     | `long`        |
`SOA`          | `retry`       | `long`        |
`SOA`          | `expire`      | `long`        |
`SOA`          | `minimum`     | `long`        |
<br>         |             |             |
`SPF`          | `text`        | `string`      |
<br>         |             |             |
`SRV`          | `priority`    | `integer`     |
`SRV`          | `weight`      | `integer`     |
`SRV`          | `port`        | `integer`     |
`SRV`          | `target`      | `string`      |
<br>         |             |             |
`SSHFP`        | `algorithm`   | `integer`     |
`SSHFP`        | `type`        | `integer`     |
`SSHFP`        | `fingerprint` | `string`      |
<br>         |             |             |
`TXT`          | `text`        | `string`      |

#### RECORD DATA EXAMPLE <a id="record-data-example"></a>

Each record is a map that must include all attributes for the data type, the records are stored in the records field of the RecordSet.
The records must be an array of at least one record map. All records in the records array must be of the type stored in the typ field of the RecordSet

Use the `@` symbol to point to the zone origin

**`CNAME` records cannot point to the zone origin, thus the RecordSet name cannot be `@` nor the zone origin**

Individual `SSHFP` record:

```json
{
    "type": "SSHFP",
    "zoneId": "8f8f649f-998e-4428-a029-b4ba5f5bd4ca",
    "name": "foo",
    "ttl": 38400,
    "status": "Active",
    "created": "2017-02-22T21:34:35Z",
    "records": [
        {
            "algorithm": 1,
            "type": 3,
            "fingerprint": "560c7d19d5da9a3a5c7c19992d1fbde15d8dad31"
        }
    ],
    "id": "8306cce4-e16a-4579-9b19-4af46dc75853",
    "account": "b34f8d18-646f-4843-a80a-7c0d58a22bf5"
}
```

Multiple `SSHFP` records:

```json
{
    "type": "SSHFP",
    "zoneId": "8f8f649f-998e-4428-a029-b4ba5f5bd4ca",
    "name": "foo",
    "ttl": 38400,
    "status": "Active",
    "created": "2017-02-22T21:34:35Z",
    "records": [
        {
          "algorithm": 1,
          "type": 2,
          "fingerprint": "560c7d19d5da9a3a5c7c19992d1fbde15d8dad31"
        },
        {
          "algorithm": 3,
          "type": 1,
          "fingerprint": "160c7d19d5da9a3a5c7c19992d1fbde15d8dad31"
        },
        {
          "algorithm": 4,
          "type": 1,
          "fingerprint": "260c7d19d5da9a3a5c7c19992d1fbde15d8dad31"
        }
    ],
    "id": "8306cce4-e16a-4579-9b19-4af46dc75853",
    "account": "b34f8d18-646f-4843-a80a-7c0d58a22bf5"
}
```
