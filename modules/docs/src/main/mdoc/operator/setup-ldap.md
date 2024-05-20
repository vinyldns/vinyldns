---
layout: docs
title:  "Setup LDAP"
section: "operator_menu"
---

# Setup LDAP
VinylDNS uses LDAP for authenticating users in the portal as well as the source of user information loaded into
VinylDNS.  VinylDNS does support service accounts, which are useful for automation.

**Important Note: VinylDNS presently maintains its own user, group, and membership repository.  The only way for a
user to be created is to login to the portal.  Implementers can choose to out-of-band manage the VinylDNS repositories.**

There are no steps necessary for setup than having a Directory that can communicate via LDAP, and a user (account) that
can read data from the Directory.  Once you have that information, proceed to the [Portal Configuration](config-portal.html).

**Considerations**
You _should_ communicate to your Directory over LDAP using TLS.  To do so, the SSL certs should be installed
on the portal servers, or provided via a java trust store (key store).  The portal provides an option to specific
a java key store when it starts up. For more information: [Using Java Key Store In VinylDNS](https://github.com/vinyldns/vinyldns/tree/master/modules/portal#building-locally)

## Configuring LDAP
Before you can configure LDAP, make note of the host, username, and password that you will be using.
Follow the [Portal Configuration](config-portal.html) to complete the setup.

## Syncing users against LDAP
VinylDNS has implemented an optional feature to perform a recurring LDAP lookup against all non-test users in the database and perform
a user lock for users that no longer exist in the directory.  Automated user locking of deprecated accounts both streamlines
the user management process and enforces an extra layer of security around VinylDNS.
