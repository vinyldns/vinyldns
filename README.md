[![Join the chat at https://gitter.im/vinyldns/Lobby](https://badges.gitter.im/vinyldns/vinyldns.svg)](https://gitter.im/vinyldns/Lobby)

<p align="left">
  <a href="http://www.vinyldns.io/">
    <img
      alt="VinylDNS"
      src="img/vinyldns-logo-full.png"
      width="400"
    />
  </a>
</p>

# VinylDNS
**(we are in the midst of setting up our projects, backlog, and everything else.  Will be in tip top shape in the coming days)**

VinylDNS is a vendor agnostic front-end for managing self-service DNS across your DNS systems.
The platform provides fine-grained access controls, auditing of all changes, a self-service user interface,
secure REST based API, and integration with infrastructure automation tools like Ansible and Terraform.
It is designed to integrate with your existing DNS infrastructure, and provides extensibility to fit your installation.

Currently, VinylDNS supports:
* Connecting to existing DNS Zones
* Creating, updating, deleting DNS Records
* Working with forward and reverse zones
* Working with IP4 and IP6 records
* Governing access with fine-grained controls at the record and zone level
* Bulk updating of DNS records across zones

VinylDNS helps secure DNS management via:
* AWS Sig4 signing of all messages to ensure that the message that was sent was not altered in transit
* Throttling of DNS updates to rate limit concurrent updates against your DNS systems
* Encrypting user secrets and TSIG keys at rest and in-transit
* Recording every change made to DNS records and zones

Integration is simple with first-class language support including:
* java
* ruby
* python
* go-lang

VinylDNS is currently used in Production managing millions of DNS records.

## Table of Contents
- [Quickstart](#quickstart)
- [Roadmap](#roadmap)
- [Code of Conduct](#code-of-conduct)
- [Developer Guide](#developer-guide)
- [Project Layout](#project-layout)
- [Contributing](#contributing)
- [Contact](#contact)
- [Maintainers and Contributors](#maintainers-and-contributors)
- [Credits](#credits)

## Quickstart
Docker images for VinylDNS live on Docker Hub at https://hub.docker.com/u/vinyldns/dashboard/. 
To start up a local instance of VinylDNS on your machine with docker:

1. Ensure that you have docker and docker-compose
1. Clone the repo: `git clone https://github.com/vinyldns/vinyldns.git`
1. Navigate to repo: `cd vinyldns`
1. Run `bin/docker-up-vinyldns.sh`
1. This will start up the api at `localhost:9000`, and the portal at `localhost:9001`

Things to try after VinylDNS is running:

1. View the portal at <http://localhost:9001> in a web browser
1. Login with the credentials ***testuser*** and ***testpassword***
1. Navigate to the `groups` tab: <http://localhost:9001/groups>
1. Click on the **New Group** button and create a new group
1. Navigate to the `zones` tab: <http://localhost:9001/zones>
1. Click on the **Connect** button to connect to zone, the `bin/docker-up-vinyldns.sh` started up a local bind9 DNS server 
with a few test zones preloaded, 
connect to `Zone Name = dummy.`, `Email = sometest@vinyldns.com`, `Admin Group = the group you just created`. The DNS
Server and Zone Transfer Server can be left blank as the test zones use the defaults 
1. This is async, so refresh the zones page to view the newly created zone
1. Click the **View** button under the **Actions** column for the `dummy.` zone
1. You will see that some records are preloaded already, this is because these records existed in the bind9 server 
and VinylDNS automatically syncs records with the backend DNS server upon zone connection
1. From here, you can create DNS record sets in the **Manage Records** tab, and manage zone settings and ***ACL rules***
in the **Manage Zone** tab
1. To try creating a DNS record, click on the **Create Record Set** button under Records, `Record Type = A, Record Name = my-test-a,
TTL = 300, IP Addressess = 1.1.1.1`
1. Click on the **Refresh** button under Records, you should see your new record created

Things to note: 

1. Upon connecting to a zone for the first time, a zone sync is ran to provide VinylDNS a copy of the records in the zone
1. Changes made via VinylDNS are made against the DNS backend, you do not need to sync the zone further to push those changes out
1. If changes to the zone are made outside of VinylDNS, then the zone will have to be re-synced to give VinylDNS a copy of those records

## Roadmap
See [ROADMAP.md](ROADMAP.md) for the future plans for VinylDNS.

## Code of Conduct
This project and everyone participating in it are governed by the [VinylDNS Code Of Conduct](CODE_OF_CONDUCT.md).  By
participating, you agree to this Code.  Please report any violations to the code of conduct to vinyldns-core@googlegroups.com.

## Developer Guide
### Requirements
- sbt
- Java 8
- Python 2.7
- virtualenv
- docker
- wget
- Protobuf 2.6.1

See [DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md) for instructions on setting up VinylDNS locally.

## Project Layout
* [API](modules/api): the API is the main engine for all of VinylDNS.  This is the most active area of the codebase, as everything else typically just funnels through
the API.  More detail on the API can be provided below.
* [Portal](modules/portal): The portal is a user interface wrapper around the API.  Most of the business rules, logic, and processing can be found in the API.  The
_only_ feature in the portal not found in the API is creation of users and user authentication.
* [Documentation](modules/docs): The documentation is primarily in support of the API.

For more details see the [project structure](DEVELOPER_GUIDE.md#project-structure) in the Developer Guide.

## Contributing
See the [Contributing Guide](CONTRIBUTING.md).

## Contact
- [Gitter](https://gitter.im/vinyldns/Lobby)
- [Mailing List](https://groups.google.com/forum/#!forum/vinyldns)
- If you have any security concerns please contact the maintainers directly vinyldns-core@googlegroups.com

## Maintainers and Contributors
The current maintainers (people who can merge pull requests) are:
- Paul Cleary
- Nima Eskandary
- Michael Ly
- Rebecca Star
- Britney Wright

See [AUTHORS.md](AUTHORS.md) for the full list of contributors to VinylDNS.

## Credits
VinylDNS would not be possible without the help of many other pieces of open source software. Thank you open source world!

Initial development of DynamoDBHelper done by [Roland Kuhn](https://github.com/rkuhn) from https://github.com/akka/akka-persistence-dynamodb/blob/8d7495821faef754d97759f0d3d35ed18fc17cc7/src/main/scala/akka/persistence/dynamodb/journal/DynamoDBHelper.scala

Given the Apache 2.0 license of VinylDNS, we specifically want to call out the following libraries and their corresponding licenses shown below.
- [logback-classic](https://github.com/qos-ch/logback) - [Eclipse Public License 1.0](https://www.eclipse.org/legal/epl-v10.html)
- [logback-core](https://github.com/qos-ch/logback) - [Eclipse Public License 1.0](https://www.eclipse.org/legal/epl-v10.html)
- [h2 database](http://h2database.com) - [Mozilla Public License, version 2.0](https://www.mozilla.org/MPL/2.0/)
- [pureconfig](https://github.com/pureconfig/pureconfig) - [Mozilla Public License, version 2.0](https://www.mozilla.org/MPL/2.0/)
- [pureconfig-macros](https://github.com/pureconfig/pureconfig) - [Mozilla Public License, version 2.0](https://www.mozilla.org/MPL/2.0/)
- [junit](https://junit.org/junit4/) - [Eclipse Public License 1.0](https://www.eclipse.org/legal/epl-v10.html)
