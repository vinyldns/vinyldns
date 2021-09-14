[![Join the chat at https://gitter.im/vinyldns](https://badges.gitter.im/vinyldns/vinyldns.svg)](https://gitter.im/vinyldns)
![Build](https://github.com/vinyldns/vinyldns/workflows/Continuous%20Integration/badge.svg)
[![CodeCov ](https://codecov.io/gh/vinyldns/vinyldns/branch/master/graph/badge.svg)](https://codecov.io/gh/vinyldns/vinyldns)
[![CII Best Practices](https://bestpractices.coreinfrastructure.org/projects/2682/badge)](https://bestpractices.coreinfrastructure.org/projects/2682)
[![License](https://img.shields.io/github/license/vinyldns/vinyldns)](https://github.com/vinyldns/vinyldns/blob/master/LICENSE)
[![conduct](https://img.shields.io/badge/%E2%9D%A4-code%20of%20conduct-blue.svg)](https://github.com/vinyldns/vinyldns/blob/master/CODE_OF_CONDUCT.md)

<p align="left">
  <a href="http://www.vinyldns.io/">
    <img
      alt="VinylDNS"
      src="img/vinyldns_optimized.svg"
      width="400"
    />
  </a>
</p>

# VinylDNS
VinylDNS is a vendor agnostic front-end for enabling self-service DNS and streamlining DNS operations.
VinylDNS manages millions of DNS records supporting thousands of engineers in production at [Comcast](http://www.comcast.com).
The platform provides fine-grained access controls, auditing of all changes, a self-service user interface,
secure RESTful API, and integration with infrastructure automation tools like Ansible and Terraform.
It is designed to integrate with your existing DNS infrastructure, and provides extensibility to fit your installation.

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
* javascript

## Table of Contents
- [Quickstart](#quickstart)
- [Code of Conduct](#code-of-conduct)
- [Developer Guide](#developer-guide)
- [Contributing](#contributing)
- [Contact](#contact)
- [Maintainers and Contributors](#maintainers-and-contributors)
- [Credits](#credits)

## Quickstart
Docker images for VinylDNS live on Docker Hub at <https://hub.docker.com/u/vinyldns/>.
To start up a local instance of VinylDNS on your machine with docker:

1. Ensure that you have [docker](https://docs.docker.com/install/) and [docker-compose](https://docs.docker.com/compose/install/)
1. Clone the repo: `git clone https://github.com/vinyldns/vinyldns.git`
1. Navigate to repo: `cd vinyldns`
1. Run `./bin/docker-up-vinyldns.sh`. This will start up the api at `localhost:9000` and the portal at `localhost:9001`
1. See [Developer Guide](DEVELOPER_GUIDE.md#loading-test-data) for how to load a test DNS zone
1. To stop the local setup, run `./bin/remove-vinyl-containers.sh`.

There exist several clients at <https://github.com/vinyldns> that can be used to make API requests, using the endpoint `http://localhost:9000`

## Things to try in the portal
1. View the portal at <http://localhost:9001> in a web browser
1. Login with the credentials ***professor*** and ***professor***
1. Navigate to the `groups` tab: <http://localhost:9001/groups>
1. Click on the **New Group** button and create a new group, the group id is the uuid in the url after you view the group
1. View zones you connected to in the `zones` tab: <http://localhost:9001/zones>.  For a quick test, create a new zone named "ok" with an email of "test@test.com" and choose a group you created from the previous step. (Note, see [Developer Guide](DEVELOPER_GUIDE.md#loading-test-data) for creating a zone)
1. You will see that some records are preloaded in the zoned already, this is because these records are preloaded in the local docker DNS server 
and VinylDNS automatically syncs records with the backend DNS server upon zone connection
1. From here, you can create DNS record sets in the **Manage Records** tab, and manage zone settings and ***ACL rules***
in the **Manage Zone** tab
1. To try creating a DNS record, click on the **Create Record Set** button under Records, `Record Type = A, Record Name = my-test-a,
TTL = 300, IP Addressess = 1.1.1.1`
1. Click on the **Refresh** button under Records, you should see your new record created

## Other things to note
1. Upon connecting to a zone for the first time, a zone sync is executed to provide VinylDNS a copy of the records in the zone
1. Changes made via VinylDNS are made against the DNS backend, you do not need to sync the zone further to push those changes out
1. If changes to the zone are made outside of VinylDNS, then the zone will have to be re-synced to give VinylDNS a copy of those records
1. If you wish to modify the url used in the creation process from `http://localhost:9000`, to say `http://vinyldns.yourdomain.com:9000`, you can modify the `bin/.env` file before execution.
1. A similar `docker/.env.quickstart` can be modified to change the default ports for the Portal and API. You must also modify their config files with the new port: https://www.vinyldns.io/operator/config-portal & https://www.vinyldns.io/operator/config-api

## Code of Conduct
This project and everyone participating in it are governed by the [VinylDNS Code Of Conduct](CODE_OF_CONDUCT.md).  By
participating, you agree to this Code.  Please report any violations to the code of conduct to vinyldns-core@googlegroups.com.

## Developer Guide
See [DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md) for instructions on setting up VinylDNS locally.

## Contributing
See the [Contributing Guide](CONTRIBUTING.md).

## Contact
- [Gitter](https://gitter.im/vinyldns)
- If you have any security concerns please contact the maintainers directly vinyldns-core@googlegroups.com

## Maintainers and Contributors
The current maintainers (people who can merge pull requests) are:
- Paul Cleary
- Ryan Emerle
- Sriram Ramakrishnan
- Jim Wakemen

See [AUTHORS.md](AUTHORS.md) for the full list of contributors to VinylDNS.

See [MAINTAINERS.md](MAINTAINERS.md) for documentation specific to maintainers 

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
