---
layout: docs
title: "Portal Configuration Guide"
section: "operator_menu"
---

# Portal Configuration Guide
The portal configuration is much smaller than the API Server.

**Note: Unlike the API server, not all configuration resides under a `vinyldns` namespace.**

## Configuration
- [Database Configuration](#database-configuration)
- [LDAP](#ldap)
- [Cryptography](#cryptography-settings)
- [Custom Links](#custom-links)
- [Additional Configuration Settings](#additional-configuration-settings)
- [Full Example Config](#full-example-config)

## Database Configuration
VinylDNS supports both DynamoDB and MySQL backends (see [API Database Configuration](config-api#database-configuration)).

If using DynamoDB, follow the [AWS DynamoDB Setup Guide](setup-dynamodb) first to get the values you need to configure here.

If using MySQL, follow the [MySQL Setup Guide](setup-mysql) first to get the values you need to configure here.


The Portal uses the following tables:

* `user`
* `userChanges`

Note that the user table is shared between the api and the portal, and *must* be configured with
the same values in both configs. **At the moment, the user and userChange repository are only implemented in DynamoDB, but we are actively
working on MySQL implementations**:

```yaml
vinyldns {

  # this list should include only the datastores being used by your portal instance (user and userChange repo)
  data-stores = ["dynamodb"]
  
  dynamodb {
      
    # this is the path to the DynamoDB provider. This should not be edited
    # from the default in reference.conf
    class-name = "vinyldns.dynamodb.repository.DynamoDBDataStoreProvider"
    
    settings {
      # AWS_ACCESS_KEY, credential needed to access the SQS queue
      key = "x"
    
      # AWS_SECRET_ACCESS_KEY, credential needed to access the SQS queue
      secret = "x"
    
      # DynamoDB url for the region you are running in, this example is in us-east-1
      endpoint = "https://dynamodb.us-east-1.amazonaws.com"
      
      # DynamoDB region
      region = "us-east-1"
    }
    
    repositories {
      # all repositories with config sections here will be enabled in dynamodb
      user {
        # Name of the table where recordsets are saved
        table-name = "usersTest"
        # Provisioned throughput for reads
        provisioned-reads = 30
        # Provisioned throughput for writes
        provisioned-writes = 20
      }
      user-change {
        table-name = "userChangeTest"
        provisioned-reads = 30
        provisioned-writes = 20
      }
    }
  }
}
```

## LDAP
Be sure to follow the [LDAP Setup Guide](setup-ldap) first to get the values you need to configure here.

LDAP configuration connects VinylDNS to your Directory where user information is stored.

```yaml
LDAP {
  # The name of the user to connect VinylDNS to LDAP
  user="test"

  # The password for the user to connect VinylDNS to LDAP
  password="test"

  # The domain for the user connecting VinylDNS to LDAP
  domain="test"

  # One or more search bases to find users.  If users come from multiple domains, list them here
  searchBase = [{organization = "someDomain", domainName = "DC=test,DC=test,DC=com"}, {organization = "anotherDomain", domainName = "DC=test,DC=com"}]

  # Connection information to LDAP
  context {
    initialContextFactory = "com.sun.jndi.ldap.LdapCtxFactory"
    securityAuthentication = "simple"

    # Set this to point to your LDAP
    providerUrl = "ldaps://somedomain.com:9999"
  }
}
```

## Cryptography Settings
The Portal encrypts user secrets at rest using the same mechanism as the API server.

**Note: It is extremely important that these settings match the API server, otherwise the API server will not
be able to decrypt user secrets and your installation will fail!**

Cryptography is _pluggable_, meaning you can bring your own crypto with you.  All that is required is to provide an
implementation of [CryptoAlgebra](https://github.com/vinyldns/vinyldns/blob/master/modules/core/src/main/scala/vinyldns/core/crypto/CryptoAlgebra.scala)
using a crypto library of choice.  The default implementation is `NoOpCrypto`, which does not do any encryption (not recommended for production).

The following are the configuration settings for crypto.  Notice here the _only_ thing we see is the `type`.  The `type`
is the fully qualified class name for the `CryptoAlgebra` you will be using.  If your crypto implementation requires
additional settings, they will be configured inside the `crypto` element, adjacent to the `type`.

```yaml
crypto {
  type = "vinyldns.core.crypto.JavaCrypto"
  secret = "8B06A7F3BC8A2497736F1916A123AA40E88217BE9264D8872597EF7A6E5DCE61"
}
```

## Custom Links
Custom links display either on the nav bar once logged in, or on the log in screen.  These links are useful to point
to internal documentation and procedures.  For example, how to raise certain tickets with engineering, or
internal slack channels.

```yaml
# an array of links
links = [
  {
    # indicates that this link should be display on the sidebar once logged in
    displayOnSidebar = true

    # display this link also on the login screen
    displayOnLoginScreen = true

    # text to display for the link
    title = "API Documentation"

    # the hyperlink address being linked to
    href = "http://vinyldns.io"

    # a fa icon to display
    icon = "fa fa-file-text-o"
  }
]
```

## Additional Configuration Settings

### Play Secret
The play secret must be set to a secret value, and should be an environment variable

```yaml
# See http://www.playframework.com/documentation/latest/ApplicationSecret for more details.
play.http.secret.key = "vinyldnsportal-change-this-for-production"
```

### Test Login
The test login should not be used for production environments.  It is useful to tinker with VinylDNS.  If this
setting is true, then you can login with `testuser` and `testpassword`.  Logging in using the `testuser` will _not_
contact LDAP.

`portal.test_login = false`

### HTTP Port
The HTTP Port that the Portal server will bind to

`http.port=9001`

## Full Example Config
```yaml
# This is the main configuration file for the application.
# ~~~~~

# Secret key
# ~~~~~
# The secret key is used to secure cryptographics functions.
#
# This must be changed for production, but we recommend not changing it in this file.
#
# See http://www.playframework.com/documentation/latest/ApplicationSecret for more details.
play.http.secret.key = "vinyldnsportal-change-this-for-production"

# The application languages
# ~~~~~
play.i18n.langs = [ "en" ]
portal.vinyldns.backend.url = "http://vinyldns-api:9000"
portal.test_login = false

# configuration for the users and groups store
data-stores = ["dynamodb"]

dynamodb {
  class-name = "vinyldns.dynamodb.repository.DynamoDBDataStoreProvider"
  
  settings {
    key = "x"
    secret = "x"
    endpoint = "http://vinyldns-dynamodb:8000"
    region = "us-east-1"
  }
  
  repositories {
    user {
      table-name = "usersTest"
      provisioned-reads = 30
      provisioned-writes = 20
    }
    user-change {
      table-name = "userChangeTest"
      provisioned-reads = 30
      provisioned-writes = 20
    }
  }

LDAP {
  user="test"
  password="test"
  domain="test"

  searchBase = [{organization = "someDomain", domainName = "DC=test,DC=test,DC=com"}, {organization = "anotherDomain", domainName = "DC=test,DC=com"}]

  context {
    initialContextFactory = "com.sun.jndi.ldap.LdapCtxFactory"
    securityAuthentication = "simple"
    providerUrl = "ldaps://somedomain.com:9999"
  }

}

play.filters.enabled += "play.filters.csrf.CSRFFilter"

# Expire session after 10 hours
play.http.session.maxAge = 10h

# session secure should be false in order to run properly locally, this is set properly on deployment
play.http.session.secure = false
play.http.session.httpOnly = true

# use no-op by default
crypto {
  type = "vinyldns.core.crypto.NoOpCrypto"
}

http.port=9001

links = [
  {
    displayOnSidebar = true
    displayOnLoginScreen = true
    title = "API Documentation"
    href = "http://vinyldns.io"
    icon = "fa fa-file-text-o"
  }
]
```