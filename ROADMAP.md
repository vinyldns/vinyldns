# Roadmap

The Roadmap captures the plans for VinylDNS.  There are a few high-level features that are planned for active development:

1. DNS SEC - There is no first-class support for DNS SEC.  That feature set is being defined.
1. Shared Zones - IP space and large common zones are cumbersome to manage using fine-grained ACL rules.  Shared zones
enable self-service management of records via a record ownership model for access controls.  Record ownership assigns
a group as the owner of the record to restrict who can modify that record.
1. Zone Management - Presently VinylDNS _connects to existing zones_ for management.  Zone Management will allow users
to create and manage zones in the authoritative systems themselves.
1. Record meta data - VinylDNS will allow the "tagging" of DNS records with arbitrary key-value pairs
1. DNS GSLB - Integration with various GSLB vendors for common DNS GSLB configurations

In addition to large feature initiatives, we will be looking to improve how VinylDNS is operated.  The current
installation requires the following components:

* At least one VinylDNS API server
* At least one VinylDNS portal server
* AWS DynamoDB
* MySQL Database
* AWS SQS Message Queues

We would like to:
* Run entirely in a single database without MySQL.  This may be necessary as the query requirements of VinylDNS are
exceeding the capabilities of DynamoDB.
* Support alternative message queues, for example RabbitMQ
* Support additional databases, including PostgreSQL and MongoDB
* Support additional languages
* Support additional automation tools
* A new user interface (the existing portal is built using AngularJS, there are new and better ways to UI these days)
