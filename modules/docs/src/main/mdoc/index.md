---
layout: home
title:  "Home"
section: "home"
position: 1
---

# Welcome

![VinylDNS logo](../img/vinyldns-fulllogoDARK-300.png)

VinylDNS is a vendor agnostic front-end for enabling self-service DNS and streamlining DNS operations.  It is designed to integrate with your existing DNS infrastructure, and provides extensibility to fit your installation.
VinylDNS manages millions of DNS records supporting thousands of engineers in production at [Comcast](http://www.comcast.com).
The platform provides fine-grained access controls, auditing of changes, a self-service user interface,
secure RESTful API, and integration with infrastructure automation tools like Ansible and Terraform.

VinylDNS helps secure DNS management via:
* AWS Sig4 signing of all messages to ensure that the message that was sent was not altered in transit
* Throttling of DNS updates to rate limit concurrent updates against your DNS systems
* Encrypting user secrets and TSIG keys at rest and in-transit
* Recording every change made to DNS records and zones

Integration is simple with first-class language support including:
* Java
* JavaScript
* Python
* Go
