---
layout: docs
title: "List Abandoned Zones"
section: "api"
---

# List Abandoned Zones

Retrieves the list of deleted zones a user has access to.  The zone name is only sorted alphabetically.

#### HTTP REQUEST

> GET /zones/deleted/changes?nameFilter={yoursearchhere}&startFrom={response.nextId}&maxItems={1 - 100}&ignoreAccess={true|false}

#### HTTP REQUEST PARAMS

name          | type          | required?   | description |
 ------------ | ------------- | ----------- | :---------- |
nameFilter    | string        | no          | Characters that are part of the deleted zone name to search for.  The wildcard character `*` is supported, for example `www*`.  Omit the wildcard character when searching for an exact deleted zone name. |
startFrom     | *any*         | no          | In order to advance through pages of results, the startFrom is set to the `nextId` that is returned on the previous response.  It is up to the client to maintain previous pages if the client wishes to advance forward and backward.   If not specified, will return the first page of results |
maxItems      | int           | no          | The number of items to return in the page.  Valid values are 1 - 100. Defaults to 100 if not provided. |
ignoreAccess       | boolean       | no          | If false, returns only zones the requesting user owns or has ACL access to. If true, returns zones in the system, regardless of ownership. Defaults to false if not provided. |

#### HTTP RESPONSE TYPES

Code          | description |
 ------------ | :---------- |
200           | **OK** - The deleted zones and search info are returned in response body |
401           | **Unauthorized** - The authentication information provided is invalid.  Typically the request was not signed properly, or the access key and secret used to sign the request are incorrect |
403           | **Forbidden** - The user does not have the access required to perform the action |

#### HTTP RESPONSE ATTRIBUTES

name          | type          | description |
 ------------ | ------------- | :---------- |
zonesDeletedInfo  | Array of [Deleted Zones](zone-model.html#zone-attributes) | An array of the deleted zones found.  The zones are sorted alphabetically by zone name. |
startFrom     | string        | (optional) The startFrom parameter that was sent in on the HTTP request.  Will not be present if the startFrom parameter was not sent |
nextId        | *any*         | (optional) The identifier to be passed in as the *startFrom* parameter to retrieve the next page of results.  If there are no results left, this field will not be present.|
maxItems      | int           | The maxItems parameter that was sent in the HTTP request.  This will be 100 if not sent. |
ignoreAccess  | boolean       | The ignoreAccess parameter that was sent in the HTTP request. This will be false if not sent. |

#### EXAMPLE RESPONSE

```json
{
  "zonesDeletedInfo": [
    {
      "zoneChange": {
        "zone": {
          "name": "dummy.",
          "email": "test@test.com",
          "status": "Deleted",
          "created": "2023-09-26T09:32:08Z",
          "updated": "2023-09-26T09:32:24Z",
          "id": "01975877-ff13-4605-a940-533b87718726",
          "account": "system",
          "shared": false,
          "acl": {
            "rules": []
          },
          "adminGroupId": "7d034091-d14f-40fa-a42f-5264a71fe6af",
          "latestSync": "2023-09-26T09:32:08Z",
          "isTest": false
        },
        "userId": "4b2f14fc-d57a-4ea3-88ee-602d5cfb533c",
        "changeType": "Delete",
        "status": "Synced",
        "created": "2023-09-26T09:32:24Z",
        "id": "ff28aa95-0b35-469e-8fef-e2fb97b9e247"
      },
      "adminGroupName": "duGroup",
      "userName": "professor",
      "accessLevel": "NoAccess"
    },
    {
      "zoneChange": {
        "zone": {
          "name": "ok.",
          "email": "test@test.com",
          "status": "Deleted",
          "created": "2023-09-25T14:16:56Z",
          "updated": "2023-09-26T09:19:27Z",
          "id": "96b85fed-61d4-41bf-ac81-fdbfe3d1c037",
          "account": "system",
          "shared": false,
          "acl": {
            "rules": []
          },
          "adminGroupId": "1f938110-cd0d-4670-8c64-5f53f1cce2f1",
          "latestSync": "2023-09-25T14:16:57Z",
          "isTest": false
        },
        "userId": "8628a36b-9302-41d1-bd0a-7610cc964086",
        "changeType": "Delete",
        "status": "Synced",
        "created": "2023-09-26T09:19:27Z",
        "id": "8925f690-4586-465d-8309-c3d4ca6dd420"
      },
      "adminGroupName": "name",
      "userName": "fry",
      "accessLevel": "Delete"
    }
  ],
  "maxItems": 100,
  "ignoreAccess": true
}
```
