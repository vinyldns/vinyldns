---
layout: docs
title: "Batch Change Errors"
section: "api"
---

# Batch Change Errors
1. [By-Change Accumulated Errors](#by-change-accumulated-errors)
   - [Permissible Errors](#permissible-errors)
   - [Fatal Errors](#fatal-errors)
2. [Full-Request Errors](#full-request-errors)

### BY-CHANGE ACCUMULATED ERRORS <a id="by-change-accumulated-errors" />

Since all of the batch changes are being validated simultaneously, it is possible to encounter a variety of errors for a given change. Each change that is associated with errors will have its own list of **errors** containing one or more errors; any changes without the **errors** list have been fully validated and are good to submit. 

By-change accumulated errors are errors that get collected at different validation stages and correspond to individual change inputs. These types of errors will probably account for the majority of errors that users encounter. By-change accumulated errors are grouped into the following stages:

- Independent input validations: Validate invalid data input formats and values.
- Record and zone discovery: Resolve record and zone from fully-qualified input name.
- Dependent context validations: Check for sufficient user access and conflicts with existing records or other submissions within the batch.

Since by-change accumulated errors are collected at different stages, errors at later stages may exist but will not appear unless errors at earlier stages are addressed.

By-change accumulated errors can be further classified as *permissible* or *fatal* errors. The presence of one or more fatal errors will result in an immediate failure and no changes in the batch being applied. The behavior of permissible errors depends on whether manual review is configured on: if manual review is disabled, permissible errors are treated as fatal errors; if manual review is enabled, batches with only permissible errors will enter a pending review state.

The following chart provides a breakdown of batch change status outcome based on a combination of manual review configuration and error types present in the batch change:

Manual Review Enabled? | Errors in Batch?           | Status Outcome |
 :-------------------: | :------------------------- | :------------- |
Yes                    | Both fatal and permissible | Failed         |
Yes                    | Fatal only                 | Failed         |
Yes                    | Permissible only           | PendingReview  |
Yes                    | No                         | Pending        |
No                     | Both fatal and permissible | Failed         |
No                     | Fatal only                 | Failed         |
No                     | Permissible only           | Failed         |
No                     | No                         | Pending        |

#### EXAMPLE ERROR RESPONSE BY CHANGE <a id="batchchange-error-response-by-change" />


```
[
   {
      "changeType": "Add",
      "inputName": "good-A.another.example.com.",
      "type": "A",
      "ttl": 200,
      "record": {
        "address": "1.2.3.4"
      }
   },
   {
      "changeType": "Add",
      "inputName": "duplicate.example.com",
      "type": "CNAME",
      "ttl": 200,
      "record": {
         "cname": "test.example.com."
      },
      "errors": [
         "Record with name "duplicate.example.com." is not unique in the batch change. CNAME record cannot use duplicate name."
      ]
   },
   {
      "changeType": "Add",
      "inputName": "duplicate.example.com",
      "type": "A",
      "ttl": 300,
      "record": {
         "address": "1.2.3.4"
      }
   },
   {
      "changeType": "Add",
      "inputName": "bad-ttl-and-invalid-name$.sample.com.”,
      "type": "A",
      "ttl": 29,
      "record": {
         "address": "1.2.3.4"
      },
      "errors": [
         "Failed validation 29, TTL must be between 30 and 2147483647.",
         "Failed validation bad-ttl-and-invalid-name$.sample.com., valid domain names are a series of one or more labels joined by dots and terminate on a dot."
      ]
   }
]
```

#### By-Change Errors

##### Permissible Errors
1. [Zone Discovery Failed](#ZoneDiscoveryFailed)

##### Fatal errors
1. [Invalid Domain Name](#InvalidDomainName)
2. [Invalid Length](#InvalidLength)
3. [Invalid Record Type](#InvalidRecordType)
4. [Invalid IPv4 Address](#InvalidIpv4Address)
5. [Invalid IPv6 Address](#InvalidIpv6Address)
6. [Invalid IP Address](#InvalidIPAddress)
7. [Invalid TTL](#InvalidTTL)
8. [Invalid Batch Record Type](#InvalidBatchRecordType)
9. [Record Already Exists](#RecordAlreadyExists)
10. [Record Does Not Exist](#RecordDoesNotExist)
11. [CNAME Conflict](#CNAMEConflict)
12. [User Is Not Authorized](#UserIsNotAuthorized)
13. [Record Name Not Unique In Batch Change](#RecordNameNotUniqueInBatchChange)
14. [Invalid Record Type In Reverse Zone](#InvalidRecordTypeInReverseZone)
15. [Missing Owner Group Id](#MissingOwnerGroupId)
16. [Not a Member of Owner Group](#NotAMemberOfOwnerGroup)
17. [High Value Domain](#HighValueDomain)
18. [RecordSet has Multiple Records](#ExistingMultiRecordError)
19. [Cannot Create a RecordSet with Multiple Records](#NewMultiRecordError)
20. [CNAME Cannot be the Same Name as Zone Name]("CnameApexError")

### Permissible Errors <a id="permissible-errors**"></a>
#### 1. Zone Discovery Failed <a id="ZoneDiscoveryFailed"></a>

##### Error Message:

```
Zone Discovery Failed: zone for "<input>" does not exist in VinylDNS. If zone exists, then it must be connected to in VinylDNS.
```

##### Details:

Given an inputName, VinylDNS will determine the record and zone name for the requested change. For most records, the record
names are the same as the zone name (apex), or split at at the first '.', so the inputName 'rname.zone.name.com' will be split
into record name 'rname' and zone name 'zone.name.com' (or 'rname.zone.name.com' for both the record and zone name if it's an apex record).
For PTR records, there is logic to determine the appropriate reverse zone from the given IP address.

If this logic cannot find a matching zone in VinylDNS, you will see this error.
In that case, you need to connect to the zone in VinylDNS.
Even if the zone already exists outside of VinylDNS, it has to be added to VinylDNS to modify records.

### Fatal errors <a id="fatal-errors"></a>
#### 1. Invalid Domain Name <a id="InvalidDomainName"></a>

##### Error Message:__

```
Invalid domain name: "<input>", valid domain names must be letters, numbers, and hyphens, joined by dots, and terminate with a dot.
```

##### Details:

Fully qualified domain names, must be comprised of **labels**, separated by dots.
A **label** is a combination of letters, digits, and hyphens.
They must also be absolute, which means they end with a dot.

Syntax:

```
<domain> ::= <subdomain> | " "

<subdomain> ::= <label> | <subdomain> "." <label>

<label> ::= <letter> [ [ <ldh-str> ] <let-dig> ]

<ldh-str> ::= <let-dig-hyp> | <let-dig-hyp> <ldh-str>

<let-dig-hyp> ::= <let-dig> | "-"

<let-dig> ::= <letter> | <digit>

<letter> ::= any one of the 52 alphabetic characters A through Z in
upper case and a through z in lower case

<digit> ::= any one of the ten digits 0 through 9
```

More info can be found at:

[RFC 1035, DOMAIN NAMES - IMPLEMENTATION AND SPECIFICATION, Section 2.3.1. Preferred name syntax](https://tools.ietf.org/html/rfc1035)


#### 2. Invalid Length <a id="InvalidLength"></a>

##### Error Message:

```
Invalid length: "<input>", length needs to be between <minLengthInclusive> and <maxLengthInclusive> characters.
```

##### Details:

The length of the input did not fit in the range in \[minLengthInclusive, maxLengthInclusive].


#### 3. Invalid Record Type <a id="InvalidRecordType"></a>

##### Error Message:

```
Invalid record type: "<input>", valid record types include <valid record types>.
```

##### Details:

The record type input must match one of the valid record types. Not all DNS record types are currently supported.


#### 4. Invalid IPv4 Address <a id="InvalidIpv4Address"></a>

##### Error Message:

```
Invalid IPv4 address: "<input>"
```

##### Details:

The IPv4 address input is not a valid IPv4 address. Accepted inputs must be in dotted-decimal notation, with four
groups of three decimal digits, separated by periods. Leading zeros in groups can be omitted.

Range: 0.0.0.0 - 255.255.255.255

Examples:

* 1.1.1.1
* 10.234.0.62


#### 5. Invalid IPv6 Address <a id="InvalidIpv6Address"></a>

##### Error Message:

```
Invalid IPv6 address: "<input>".
```

##### Details:

The IPv6 address input is not a valid IPv6 address. Accepted inputs must be eight groups of four hexadecimal digits,
separated by colons. Leading zeros in groups can be emitted. Consecutive groups of all zeros can be replaced by a double colon.

Range: 0000:0000:0000:0000:0000:0000:0000:0000 - ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff

Examples:

* 2001:0db8:0000:0000:0000:ff00:0042:8329
* 2001:0db8::ff00:0042:8329
* 2001:db8::ff00:42:8329


#### 6. Invalid IP Address <a id="InvalidIPAddress"></a>

##### Error Message:

```
Invalid IP address: "<input>".
```

##### Details:

The IP address input is not a valid IPv4 or IPv6 address.


#### 7. Invalid TTL <a id="InvalidTTL"></a>

##### Error Message:

```
Invalid TTL: "<input>", must be a number between 30 and 2147483647.
```

##### Details:

Time-to-live must be a number in the range \[30, 2147483647].


#### 8. Invalid Batch Record Type <a id="InvalidBatchRecordType"></a>

##### Error Message:

```
Invalid Batch Record Type: "<input>", valid record types for batch changes include <valid record types>.
```

##### Details:

The DNS record type is not currently supported for batch changes.


#### 9. Record Already Exists <a id="RecordAlreadyExists"></a>

##### Error Message:

```
Record "<input>" Already Exists: cannot add an existing record; to update it, issue a DeleteRecordSet then an Add.
```


##### Details:

A record with the given name already exists, and cannot be duplicated for the given type.


#### 10. Record Does Not Exist <a id="RecordDoesNotExist"></a>

##### Error Message:

```
Record "<input>" Does Not Exist: cannot delete a record that does not exist.
```

##### Details:

A record with the given name could not be found in VinylDNS.
If the record exists in DNS, then you should [sync the zone](../api/vinyl-basics/#syncingZone) for that record to bring VinylDNS up to date with what is in the DNS backend.


#### 11. CNAME Conflict <a id="CNAMEConflict"></a>

##### Error Message:

```
CNAME conflict: CNAME record names must be unique. Existing record with name "<name>" and type "<type>" conflicts with this record.
```

##### Details:

A CNAME record with the given name already exists. CNAME records must have unique names.


#### 12. User Is Not Authorized <a id="UserIsNotAuthorized"></a>

##### Error Message:

```
User "<user>" is not authorized.
```

##### Details:

User must either be in the admin group for the zone being changed, or have an ACL rule.


#### 13. Record Name Not Unique In Batch Change <a id="RecordNameNotUniqueInBatchChange"></a>

##### Error Message:

```
Record "<name>" Name Not Unique In Batch Change: cannot have multiple "<type>" records with the same name.
```

##### Details:

Certain record types do not allow multiple records with the same name. If you get this error, it means you have
illegally input two or more records with the same name and one of these types.


#### 14. Invalid Record Type In Reverse Zone <a id="InvalidRecordTypeInReverseZone"></a>

##### Error Message:

```
Invalid Record Type In Reverse Zone: record with name "<name>" and type "<type>" is not allowed in a reverse zone.
```

##### Details:

Not all record types are allowed in a DNS reverse zone. The given type is not supported.


#### 15. Missing Owner Group Id <a id="MissingOwnerGroupId"></a>

##### Error Message:

```
Zone "<zone name>" is a shared zone, so owner group ID must be specified for record "<record name>".
```

##### Details:

You are trying to create a new record or update an existing unowned record in a shared zone. This requires a record owner group ID in the batch change.  


#### 16. Not a Member of Owner Group <a id="NotAMemberOfOwnerGroup"></a>

##### Error Message:

```
User "<user name>" must be a member of group "<group ID>" to apply this group to batch changes.
```

##### Details:

You must be a member of the group you are assigning for record ownership in the batch change. 



#### 17. High Value Domain <a id="HighValueDomain"></a>

##### Error Message:

```
Record Name "<record name>" is configured as a High Value Domain, so it cannot be modified.
```

##### Details:

You are trying to create a record with a name that is not permitted in VinylDNS.
The list of high value domains is specific to each VinylDNS instance.
You should reach out to your VinylDNS administrators for more information.


#### 18. RecordSet has Multiple DNS records <a id="ExistingMultiRecordError"></a>

##### Error Message:

```
RecordSet with name <name> and type <type> cannot be updated in a single Batch Change because it contains multiple DNS records (<count>).
```

##### Details:

This error means that the recordset you are attempting to update/delete has multiple records within it.

Note that this error is configuration-driven and will only appear if your instance of VinylDNS does not support multi-record batch updates.


#### 19. Cannot Create a RecordSet with Multiple Records <a id="NewMultiRecordError"></a>

##### Error Message:

```
Multi-record recordsets are not enabled for this instance of VinylDNS. Cannot create a new record set with multiple records for inputName <name> and type <type>
```

##### Details:

This error means that you have multiple Add entries with the same name and type in a batch change.

Note that this error is configuration-driven and will only appear if your instance of VinylDNS does not support multi-record batch updates.


#### 20. CNAME at the Zone Apex is not Allowed <a id="CnameApexError"></a>

##### Error Message:

```
CNAME cannot be the same name as zone "<zone_name>".
```

##### Details:

CNAME records cannot be `@` or the same name as the zone.


### FULL-REQUEST ERRORS <a id="full-request-errors" />

Fail-request errors cause the batch change processing to abort immediately upon encounter.

1. [Invalid Batch Change Input](#InvalidBatchChangeInput)
2. [Batch Change Not Found](#BatchChangeNotFound)
3. [Malformed JSON Errors](#malformed-json-errors)

#### 1. INVALID BATCH CHANGE INPUT <a id="InvalidBatchChangeInput" />

##### HTTP RESPONSE CODE

Code          | description |
 ------------ | :---------- |
400           | **Bad Request** - There is a top-level issue with batch change, aborting batch processing. |

There are a series of different error messages that can be returned with this error code.

##### EXAMPLE ERROR MESSAGES:

```
Batch change contained no changes. Batch change must have at least one change, up to a maximum of <limit> changes.

Cannot request more than <limit> changes in a single batch change request
```

##### DETAILS:

If there are issues with the batch change input data provided in the batch change request, errors will be returned and batch change validations will abort processing.

#### 2. BATCH CHANGE NOT FOUND <a id="BatchChangeNotFound" />

##### HTTP RESPONSE CODE

Code          | description |
 ------------ | :---------- |
404           | **Not Found** - batch change not found for specified ID in [get batch change](../api/get-batchchange) request. |

##### ERROR MESSAGE:

```
Batch change with id <id> cannot be found
```

##### DETAILS:

The batch ID specified in the [get batch change](../api/get-batchchange) request does not exist.


#### 3. MALFORMED JSON ERRORS <a id="malformed-json-errors" />

##### DETAILS:

If there are issues with the JSON provided in a batch change request, errors will be returned (not in a by-change format) and none of the batch change validations will run.

##### EXAMPLE ERROR MESSAGES:

```
{
   "errors": [
      "Missing BatchChangeInput.changes"
   ]
}

{
   "errors": [
      "Missing BatchChangeInput.changes.inputName",
      "Missing BatchChangeInput.changes.type",
      "Missing BatchChangeInput.changes.ttl"
   ]
}

{
   "errors": [
      “Invalid RecordType”
   ]
}
```
