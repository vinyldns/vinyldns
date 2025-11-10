---
layout: docs
title: "Global List / Search RecordSets"
section: "api"
---

# Global List / Search RecordSets

Retrieves a list of RecordSets globally in the VinylDNS database based on search criteria. A minimum of two alpha-numeric characters is required.

#### HTTP REQUEST

> GET /recordsets?startFrom={response.nextId}&maxItems={1 - 100}&recordNameFilter={recordNameFilter}&recordTypeFilter={recordTypeFilter}&recordOwnerGroupFilter={recordOwnerGroupFilter}&nameSort={nameSort}

#### HTTP REQUEST PARAMS

name          | type          | required?   | description |
 ------------ | ------------- | ----------- | :---------- |
recordNameFilter | string     | yes         | Characters that are part of the record name to search for.  The wildcard character `*` is supported, for example `www*`.  Omit the wildcard when searching for an exact record name. At least two alphanumeric characters are **required** for searching. |
recordTypeFilter | Array of RecordType | no | An array of record types to filter for listing record sets.  Refer to [recordset mode](recordset-model.html) for supported types.  Invalid record types will be ignored.  If left empty or no valid record types are provided, then all record types will be returned. |
recordOwnerGroupFilter | string | no        | Owner group ID for record set. |
nameSort      | string        | no          | Name sort order for record sets returned by list record set response.  Valid values are `ASC` (ascending; default) and `DESC` (descending). |
startFrom     | string        | no          | In order to advance through pages of results, the startFrom is set to the `nextId` that is returned on the previous response.  It is up to the client to maintain previous pages if the client wishes to advance forward and backward.   If not specified, will return the first page of results |
maxItems      | integer       | no          | The number of items to return in the page.  Valid values are 1 to 100. Defaults to 100 if not provided. |

#### HTTP RESPONSE TYPES

Code          | description |
 ------------ | :---------- |
200           | **OK** - The record sets are returned in the response body |
422           | **Unprocessable Entity** - `recordNameFilter` was either omitted or provided but does not contain at least two alphanumeric characters |

#### HTTP RESPONSE ATTRIBUTES

name          | type          | description |
 ------------ | ------------- | :---------- |
recordSets    | Array of RecordSets | refer to [recordset model](recordset-model.html) |
startFrom     | string        | startFrom sent in request, will not be returned if not provided |
nextId        | string        | nextId, used as startFrom parameter of next page request, will not be returned if record sets are exhausted |
maxItems      | integer       | `maxItems` sent in request, default is 100 |
recordNameFilter    | string  | name filter sent in request |
recordTypeFilter    | Array of RecordType | record type filter sent in request |
recordOwnerGroupFilter | string | record owner group sent in request |
nameSort      | string  | name sort order sent in request

#### EXAMPLE RESPONSE

```json
{
  "recordSets": [
    {
      "type": "A",
      "zoneId": "5f5304ba-c81f-456c-9d33-bb6179c8b1f1",
      "name": "foo",
      "ttl": 7200,
      "status": "Active",
      "created": "2019-04-26T17:15:35Z",
      "records": [
        {
          "address": "1.1.1.1"
        }
      ],
      "id": "f802596f-4f0e-4e65-bb43-c7ca439d2608",
      "account": "system",
      "fqdn": "foo.example.com.",
      "zoneName": "example.com.",
      "zoneShared": true
    }
  ],
  "maxItems": 100,
  "recordNameFilter": "foo*",
  "recordTypeFilter": [
    "A"
  ],
  "nameSort": "ASC"
}
```
