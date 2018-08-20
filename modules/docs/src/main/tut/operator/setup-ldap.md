---
layout: docs
title:  "AWS SQS Setup Guide"
section: "operator_menu"
---

# LDAP Setup Guide
VinylDNS uses LDAP for authenticating users in the portal as well as the source of user information loaded into
VinylDNS.  VinylDNS does support service accounts, which are useful for automation.

**Important Note: VinylDNS presently maintains its own user, group, and membership repository.  There is no "syncing"
with LDAP at the present time.  The only way for a user to be created is to login to the portal.  Implementers can
choose to out-of-band manage the VinylDNS repositories.**

There are no steps necessary for setup than having a Directory that can communicate via LDAP, and a user (account) that
can read data from the Directory.  Once you have that information, proceed to the [Portal Configuration](config-portal)

**Considerations**
You _should_ communicate to your Directory over LDAP using TLS.  To do so, the SSL certs should be installed
on the portal servers, or provided via a java trust store (key store).  The portal provides an option to specific
a java key store when it starts up.

## Configuring LDAP
Before you can configure LDAP, make note of the host, username, and password that you will be using.
Follow the [Portal Configuration](config-portal) to complete the setup.
