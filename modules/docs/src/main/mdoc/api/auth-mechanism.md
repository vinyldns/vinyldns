---
layout: docs 
title: "Authentication"
section: "api"
---

# API Authentication

The API Authentication for VinylDNS is modeled after the AWS Signature Version 4 Signing process. The AWS documentation
for it can be found
[here](https://docs.aws.amazon.com/general/latest/gr/signature-version-4.html). Similar to how the AWS Signature Version
4 signing process adds authentication information to AWS requests, VinylDNS's API Authenticator also adds authentication
information to every API request.

#### VinylDNS API Authentication Process

1. Retrieve the Authorization HTTP Header (Auth Header) from the HTTP Request Context.
2. Parse the retrieved Auth Header into an
   AWS *[String to Sign](https://docs.aws.amazon.com/general/latest/gr/sigv4-create-string-to-sign.html)* structure
   which should be in the form:

```plaintext
StringToSign =
    Algorithm + \n +
    RequestDateTime + \n +
    CredentialScope + \n +
    HashedCanonicalRequest
```

*String to Sign* Example:

```plaintext
AWS4-HMAC-SHA256
20150830T123600Z
20150830/us-east-1/iam/aws4_request
f536975d06c0309214f805bb90ccff089219ecd68b2577efef23edd43b7e1a59
```

3. Extract the access key from the Auth Header and search for the account associated with the access key.
4. Validate the signature of the request.
5. Build the authentication information, which essentially contains all the authorized accounts for the signed in user.

#### Authentication Failure Response

If any these validations fail, a 401 (Unauthorized) or a 403 (Forbidden) error will be thrown; otherwise unanticipated
exceptions will simply bubble out and result as 500s or 503s.

1. If the Auth Header is not found, then a 401 (Unauthorized) error is returned.
2. If the Auth Header cannot be parsed, then a 403 (Forbidden) error is returned.
3. If the access key cannot be found, then a 401 (Unauthorized) error is returned.
4. If the request signature cannot be validated, then a 403 (Forbidden) error is returned.
