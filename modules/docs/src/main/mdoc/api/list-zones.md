---
layout: docs
title: "List / Search Zones"
section: "api"
---

# List / Search Zone

Retrieves the list of zones a user has access to.  The zone name is only sorted alphabetically.

#### HTTP REQUEST

> GET /zones?nameFilter={yoursearchhere}&startFrom={response.nextId}&maxItems={1 - 100}&ignoreAccess={true | false}&searchByAdminGroup={true | false}&includeReverse={true | false}

#### HTTP REQUEST PARAMS

name          | type          | required?   | description |
 ------------ | ------------- | ----------- | :---------- |
nameFilter    | string        | no          | Characters that are part of the zone name to search for.  The wildcard character `*` is supported, for example `www*`.  Omit the wildcard character when searching for an exact zone name. |
startFrom     | string        | no          | In order to advance through pages of results, the startFrom is set to the `nextId` that is returned on the previous response.  It is up to the client to maintain previous pages if the client wishes to advance forward and backward.   If not specified, will return the first page of results |
maxItems      | int           | no          | The number of items to return in the page.  Valid values are 1 - 100. Defaults to 100 if not provided. |
ignoreAccess        | boolean       | no          | If false, returns only zones the requesting user owns or has ACL access to. If true, returns zones in the system, regardless of ownership. Defaults to false if not provided. |
searchByAdminGroup  | boolean       | no          | Used along with `nameFilter`. If false, returns a list of zones based on `nameFilter` value. If true, uses `nameFilter` value and filters the zone based on a group. Returns all the zones that is owned by a group given in the `nameFilter`. Defaults to false if not provided. |
includeReverse      | boolean       | no          | If false, returns only the forward zones. If true, returns both forward and reverse zones. |

#### HTTP RESPONSE TYPES

Code          | description |
 ------------ | :---------- |
200           | **OK** - The zones and search info are returned in response body |
401           | **Unauthorized** - The authentication information provided is invalid.  Typically the request was not signed properly, or the access key and secret used to sign the request are incorrect |
403           | **Forbidden** - The user does not have the access required to perform the action |

#### HTTP RESPONSE ATTRIBUTES

name          | type          | description |
 ------------ | ------------- | :---------- |
zones         | Array of [Zones](zone-model.html#zone-attributes) | An array of the zones found.  The zones are sorted alphabetically by zone name. |
startFrom     | string         | (optional) The startFrom parameter that was sent in on the HTTP request.  Will not be present if the startFrom parameter was not sent |
nextId        | string         | (optional) The identifier to be passed in as the *startFrom* parameter to retrieve the next page of results.  If there are no results left, this field will not be present.|
maxItems      | int            | The maxItems parameter that was sent in the HTTP request.  This will be 100 if not sent. |
ignoreAccess       | boolean   | The ignoreAccess parameter that was sent in the HTTP request. This will be false if not sent. |
searchByAdminGroup | boolean   | The searchByAdminGroup parameter that was sent in the HTTP request. This will be false if not sent. |
includeReverse     | boolean   | The includeReverse parameter that was sent in the HTTP request. This will be false if not sent. |

#### EXAMPLE RESPONSE

```json
{
  "zones": [
    {
      "status": "Active",
      "account": "a0b5ea74-cc05-4932-a294-9bf935d52744",
      "name": "list-zones-test-searched-1.",
      "created": "2016-12-16T15:21:47Z",
      "adminGroupId": "a0b5ea74-cc05-4932-a294-9bf935d52744",
      "email": "test@test.com",
      "shared": false,
      "acl": {
        "rules": []
      },
      "id": "31a3d8a9-bea0-458f-9c24-3d39d4b929d6",
      "latestSync": "2016-12-16T15:27:26Z",
      "accessLevel": "NoAccess"
    },
    {
      "status": "Active",
      "account": "a0b5ea74-cc05-4932-a294-9bf935d52744",
      "name": "list-zones-test-searched-2.",
      "created": "2016-12-16T15:21:47Z",
      "adminGroupId": "a0b5ea74-cc05-4932-a294-9bf935d52744",
      "email": "test@test.com",
      "shared": false,
      "acl": {
        "rules": []
      },
      "id": "f1a376b2-2d8f-41f3-b8c8-9c9fba308f5d",
      "latestSync": "2016-12-16T15:27:26Z",
      "accessLevel": "Delete"
    },
    {
      "status": "Active",
      "account": "a0b5ea74-cc05-4932-a294-9bf935d52744",
      "name": "list-zones-test-searched-3.",
      "created": "2016-12-16T15:21:47Z",
      "adminGroupId": "a0b5ea74-cc05-4932-a294-9bf935d52744",
      "email": "test@test.com",
      "shared": false,
      "acl": {
        "rules": []
      },
      "id": "568de57d-cb34-4f05-a9b5-35f9187490af",
      "latestSync": "2016-12-16T15:27:26Z",
      "accessLevel": "Read"
    },
    {
      "status": "Active",
      "account": "a0b5ea74-cc05-4932-a294-9bf935d52744",
      "name": "list-zones-test-unfiltered-1.",
      "created": "2016-12-16T15:21:47Z",
      "adminGroupId": "a0b5ea74-cc05-4932-a294-9bf935d52744",
      "email": "test@test.com",
      "shared": false,
      "acl": {
        "rules": []
      },
      "id": "98dac90c-236e-4171-8729-c977ad38717e",
      "latestSync": "2016-12-16T15:27:26Z",
      "accessLevel": "NoAccess"
    },
    {
      "status": "Active",
      "account": "a0b5ea74-cc05-4932-a294-9bf935d52744",
      "name": "list-zones-test-unfiltered-2.",
      "created": "2016-12-16T15:21:47Z",
      "adminGroupId": "a0b5ea74-cc05-4932-a294-9bf935d52744",
      "email": "test@test.com",
      "shared": false,
      "acl": {
        "rules": []
      },
      "id": "e4942020-b85a-421f-a8e2-124d8ba79422",
      "latestSync": "2016-12-16T15:27:26Z",
      "accessLevel": "Read"
    }
  ],
  "maxItems": 100,
  "ignoreAccess": true
}
```
