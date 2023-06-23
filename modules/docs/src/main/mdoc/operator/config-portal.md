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
VinylDNS supports a MySQL backend (see [API Database Configuration](config-api.html#database-configuration)).

Follow the [MySQL Setup Guide](setup-mysql.html) first to get the values you need to configure here.

The Portal uses the following tables:

* `user`
* `userChanges`

Note that the user table is shared between the API and the portal, and *must* be configured with
the same values in both configs:

```yaml
vinyldns {

  # this list should include only the datastores being used by your portal instance (user and userChange repo)
  data-stores = ["mysql"]
  
  mysql {
    
    # this is the path to the mysql provider. This should not be edited
    # from the default in reference.conf
    class-name = "vinyldns.mysql.repository.MySqlDataStoreProvider"
    
    settings {
      # the name of the database, recommend to leave this as is
      name = "vinyldns"
      
      # the jdbc driver, recommended to leave this as is
      driver = "org.mariadb.jdbc.Driver"
  
      # the URL used to create the schema, typically this will be without the "database" name
      migration-url = "jdbc:mariadb://localhost:19002/?user=root&password=pass"
  
      # the main connection URL
      url = "jdbc:mariadb://localhost:19002/vinyldns?user=root&password=pass"
  
      # the user to connect to MySQL
      user = "root"
  
      # the password to connect to MySQL
      password = "pass"
  
      ## see https://github.com/brettwooldridge/HikariCP for more detail on the following fields
      # the maximum number of connections to scale the connection pool to
      maximum-pool-size = 20
  
      # the maximum number of milliseconds to wait for a connection from the connection pool
      connection-timeout-millis = 1000
      
      # the minimum number of idle connections that HikariCP tries to maintain in the pool
      minimum-idle = 10
            
      # the maximum number of milliseconds that a connection is can sit idle in the pool
      idle-timeout = 10000
  
      # The max lifetime of a connection in a pool.  Should be several seconds shorter than the database imposed connection time limit
      max-lifetime = 600000
      
      # controls whether JMX MBeans are registered
      register-mbeans = true
      
      # my-sql-properties allows you to include any additional mysql performance settings you want.
      # Note that the properties within my-sql-properties must be camel case!
      # see https://github.com/brettwooldridge/HikariCP/wiki/MySQL-Configuration for guidance
      my-sql-properties {
        prepStmtCacheSize = 300
        prepStmtCacheSqlLimit = 2048
        cachePrepStmts = true
        useServerPrepStmts = true
        rewriteBatchedStatements = true
      }
    }
    
    repositories {
      # all repositories with config sections here will be enabled in mysql
      user {
      # no additional settings for repositories enabled in mysql
      }
    }
  }
}
```

## LDAP
Be sure to follow the [LDAP Setup Guide](setup-ldap.html) first to get the values you need to configure here.

LDAP configuration connects VinylDNS to your Directory where user information is stored.

The default value for user sync enabled is _false_ and user sync hours polling interval is 24 (max value). If the hours polling interval
is configured higher than 24, 24 hours will be used.

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
  
  user-sync {
    enabled = true # Default value is false
    hours-polling-interval = 12 # Default value is 24
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
    href = "https://vinyldns.io"

    # a fa icon to display
    icon = "fa fa-file-text-o"
  }
]
```

## Additional Configuration Settings

### Play Secret
The play secret must be set to a secret value, and should be an environment variable

```yaml
# See https://www.playframework.com/documentation/latest/ApplicationSecret for more details.
play.http.secret.key = "vinyldnsportal-change-this-for-production"
```

### Play Allowed Hosts Filter
Play provides a filter that lets you configure which hosts can access your application. The filter introduces a 
whitelist of allowed hosts and sends a 400 (Bad Request) response to all requests with a host that do not match 
the whitelist.

```yaml
# See https://www.playframework.com/documentation/2.8.x/AllowedHostsFilter for more details.
# Note: allowed = ["."] matches all hosts hence would not be recommended in a production environment.
play.filters.hosts {
  allowed = ["."]
}
```

### Test Login
The test login should not be used for production environments.  It is useful to tinker with VinylDNS.  If this
setting is true, then you can login with `testuser` and `testpassword`.  Logging in using the `testuser` will _not_
contact LDAP.

`portal.test_login = false`

### HTTP Port
The HTTP Port that the Portal server will bind to

`http.port=9001`

### Shared Zones Display / Record Owner Selection
Necessary to enable shared zones submission and record ownership

`shared-display-enabled = true`

### Batch Change Limit
How many changes are allowable in a single DNS change request

`batch-change-limit = 1000`

### Manual Review Enabled
Triggers the manual review process for certain DNS requests

`manual-batch-review-enabled = true`

### Scheduled Changes
Allows users to schedule changes to be run sometime in the future

`scheduled-changes-enabled = true`

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
# See https://www.playframework.com/documentation/latest/ApplicationSecret for more details.
play.http.secret.key = "vinyldnsportal-change-this-for-production"

# See https://www.playframework.com/documentation/2.8.x/AllowedHostsFilter for more details.
# Note: allowed = ["."] matches all hosts hence would not be recommended in a production environment.
play.filters.hosts {
  allowed = ["."]
}

# The application languages
# ~~~~~
play.i18n.langs = [ "en" ]
portal.vinyldns.backend.url = "http://vinyldns-api:9000"
portal.test_login = false

# configuration for the users and groups store
data-stores = ["mysql"]

mysql {
  class-name = "vinyldns.mysql.repository.MySqlDataStoreProvider"
  
  settings {
    name = "vinyldns"
    driver = "org.mariadb.jdbc.Driver"
    migration-url = "jdbc:mariadb://localhost:19002/?user=root&password=pass"
    url = "jdbc:mariadb://localhost:19002/vinyldns?user=root&password=pass"
    user = "root"
    password = "pass"
    maximum-pool-size = 20
    minimum-idle = 10
    connection-timeout-millis = 1000
    idle-timeout = 10000
    max-lifetime = 600000
    register-mbeans = true
    my-sql-properties {
      prepStmtCacheSize = 300
      prepStmtCacheSqlLimit = 2048
      cachePrepStmts = true
      useServerPrepStmts = true
      rewriteBatchedStatements = true
    }
  }
  
  repositories {
    user {
    }
  }
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
    href = "https://vinyldns.io"
    icon = "fa fa-file-text-o"
  }
]
```
