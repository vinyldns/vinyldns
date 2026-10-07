---
layout: docs
title:  "Setup LDAP"
section: "operator_menu"
---

# Setup LDAP
VinylDNS supports LDAP for authenticating users in the portal and as a source of user information. LDAP is
required when OIDC is not enabled. When OIDC is enabled, LDAP is only needed if you are using LDAP as
the [user sync provider](config-portal.html#user-sync).

VinylDNS also supports service accounts, which are useful for automation.

**Important Note: VinylDNS presently maintains its own user, group, and membership repository. The only way for a
user to be created is to login to the portal. Implementers can choose to out-of-band manage the VinylDNS repositories.**

There are no steps necessary for setup other than having a Directory that can communicate via LDAP, and a user (account) that
can read data from the Directory. Once you have that information, proceed to the [Portal Configuration](config-portal.html).

**Considerations**
You _should_ communicate to your Directory over LDAP using TLS. To do so, the SSL certs should be installed
on the portal servers, or provided via a java trust store (key store). The portal provides an option to specify
a java key store when it starts up. For more information: [Using Java Key Store In VinylDNS](https://github.com/vinyldns/vinyldns/tree/master/modules/portal#building-locally)

## Configuring LDAP
Before you can configure LDAP, make note of the host, username, and password that you will be using.
Follow the [Portal Configuration](config-portal.html#ldap) to complete the setup.

## Syncing users against LDAP
VinylDNS can perform a recurring LDAP lookup against all non-test users in the database and lock
users that no longer exist in the directory. Automated user locking of deprecated accounts both streamlines
the user management process and enforces an extra layer of security around VinylDNS.

User sync can be configured via the `user-sync.provider` setting. Set it to `"ldap"` to use LDAP for sync,
or `"graph-api"` to sync via the Microsoft Graph API instead. See the [User Sync documentation](config-portal.html#user-sync)
for details.
