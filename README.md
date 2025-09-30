[![VinylDNS Release](https://img.shields.io/github/v/release/vinyldns/vinyldns?label=latest%20release&logo=github)](https://github.com/vinyldns/vinyldns/releases/latest)
[![VinylDNS API Docker Image](https://img.shields.io/github/v/release/vinyldns/vinyldns?color=brightgreen&label=API%20Image&logo=docker&logoColor=white&cacheSeconds=300)](https://hub.docker.com/r/vinyldns/api/tags?page=1&ordering=last_updated)
[![VinylDNS Portal Docker Image](https://img.shields.io/github/v/release/vinyldns/vinyldns?color=brightgreen&label=Portal%20Image&logo=docker&logoColor=white&cacheSeconds=300)](https://hub.docker.com/r/vinyldns/portal/tags?page=1&ordering=last_updated)

<p align="left">
  <a href="https://www.vinyldns.io/">
    <img
      alt="VinylDNS"
      src="img/vinyldns_optimized.svg"
      width="400"
    />
  </a>
</p>

# VinylDNS

VinylDNS is a vendor-agnostic front-end for enabling self-service DNS and streamlining DNS operations. VinylDNS manages
millions of DNS records supporting thousands of engineers in production at [Comcast](http://www.comcast.com). The
platform provides fine-grained access controls, auditing of all changes, a self-service user interface, secure RESTful
API, and integration with infrastructure automation tools like Ansible and Terraform. It is designed to integrate with
your existing DNS infrastructure, and provides extensibility to fit your installation.

VinylDNS helps secure DNS management via:

- AWS Sig4 signing of all messages to ensure that the message that was sent was not altered in transit
- Throttling of DNS updates to rate limit concurrent updates against your DNS systems
- Encrypting user secrets and TSIG keys at rest and in-transit
- Recording every change made to DNS records and zones

Integration is simple with first-class language support including:

- Java
- Python
- Go
- JavaScript

## Table of Contents

* [Quickstart](#quickstart)
    - [Quickstart Optimization](#quickstart-optimization)
* [Things to Try in the Portal](#things-to-try-in-the-portal)
    + [Verifying Your Changes](#verifying-your-changes)
    + [Other things to note](#other-things-to-note)
* [Code of Conduct](#code-of-conduct)
* [Developer Guide](#developer-guide)
* [Contributing](#contributing)
* [Maintainers and Contributors](#maintainers-and-contributors)
* [Credits](#credits)

## Quickstart

Docker images for VinylDNS live on Docker Hub at <https://hub.docker.com/u/vinyldns/>. To start up a local instance of
VinylDNS on your machine with docker:

1. Ensure that you have [docker](https://docs.docker.com/install/)
   and [docker-compose](https://docs.docker.com/compose/install/)
1. Clone the repo: `git clone https://github.com/vinyldns/vinyldns.git`
1. Navigate to repo: `cd vinyldns`
1. Run `./quickstart/quickstart-vinyldns.sh`. This will start up the api at `localhost:9000` and the portal
   at `localhost:9001`
1. See [Things to Try in the Portal](#things-to-try-in-the-portal) for getting familiar with the Portal
1. To stop the local setup, run `./utils/clean-vinyldns-containers.sh`.

There exist several clients at <https://github.com/vinyldns> that can be used to make API requests, using the
endpoint `http://localhost:9000`.

#### Quickstart Optimization

If you are experimenting with Quickstart, you may encounter a delay each time you run it. This is because the API and
Portal are rebuilt every time you launch Quickstart. If you'd like to cache the builds of the API and Portal, you may
want to first run:

| Script                     | Description                                                                  |
|----------------------------|------------------------------------------------------------------------------|
| `build/assemble_api.sh`    | This will create the API `jar` file which will then be used by Quickstart    |
| `build/assemble_portal.sh` | This will create the Portal `zip` file which will then be used by Quickstart |

Once these scripts are run, the artifacts are placed into the `artifacts/` directory and will be reused for each
Quickstart launch. If you'd like to regenerate the artifacts, simply delete them and rerun the scripts above.

## Things to Try in the Portal

1. View the portal at <http://localhost:9001> in a web browser
2. Login with the credentials `professor` and `professor`
3. Navigate to the `groups` tab: <http://localhost:9001/groups>
4. Click on the **New Group** button and create a new group, the group id is the uuid in the url after you view the
   group
5. Connect a zone by going to the `zones` tab: <http://localhost:9001/zones>.
    1. Click the `-> Connect` button
    2. For `Zone Name` enter `ok` with an email of `test@test.com`
    3. For `Admin Group`, choose a group you created from the previous step
    4. Leave everything else as-is and click the `Connect` button at the bottom of the form
6. A new zone `ok` should appear in your `My Zones` tab _(you may need to refresh your browser)_
7. You will see that some records are preloaded in the zone already, this is because these records are preloaded in the
   local docker DNS server and VinylDNS automatically syncs records with the backend DNS server upon zone connection
8. From here, you can create DNS record sets in the **Manage Records** tab, and manage zone settings and ***ACL rules***
   in the **Manage Zone** tab
9. To try creating a DNS record, click on the **Create Record Set** button under
   Records, `Record Type = A, Record Name = my-test-a, TTL = 300, IP Addressess = 1.1.1.1`
10. Click on the **Refresh** button under Records, you should see your new record created

### Verifying Your Changes

VinylDNS will synchronize with the DNS backend. For the Quickstart this should be running on port `19001` on `localhost`
.

To verify your changes, you can use a DNS resolution utility like `dig`

```bash
$ dig @127.0.0.1 -p 19001 +short my-test-a.ok
1.1.1.1
```

This tells `dig` to use `127.0.0.1` as the resolver on port `19001`. The `+short` just makes the output a bit less
verbose. Finally, the record we're looking up is `my-test-a.ok`. You can see the returned output of `1.1.1.1` matches
the record data we entered.

### Other things to note

1. Upon connecting to a zone for the first time, a zone sync is executed to provide VinylDNS a copy of the records in
   the zone
1. Changes made via VinylDNS are made against the DNS backend, you do not need to sync the zone further to push those
   changes out
1. If changes to the zone are made outside of VinylDNS, then the zone will have to be re-synced to give VinylDNS a copy
   of those records
1. If you wish to modify the url used in the creation process from `http://localhost:9000`, to
   say `http://vinyldns.yourdomain.com:9000`, you can modify the `quickstart/.env` file before execution.
1. Further configuration can be ac https://www.vinyldns.io/operator/config-portal
   & https://www.vinyldns.io/operator/config-api

## Code of Conduct

This project, and everyone participating in it, are governed by the [VinylDNS Code Of Conduct](CODE_OF_CONDUCT.md). By
participating, you agree to this Code.

## Developer Guide

See [DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md) for instructions on setting up VinylDNS locally.

## Contributing

See the [Contributing Guide](CONTRIBUTING.md).

## Maintainers and Contributors

The current maintainers (people who can merge pull requests) are:

- Arpit Shah ([@arpit4ever](https://github.com/arpit4ever))
- Nick Spadaccino ([@nspadaccino](https://github.com/nspadaccino))
- Jayaraj Velkumar ([@Jay07GIT](https://github.com/Jay07GIT))

See [AUTHORS.md](AUTHORS.md) for the full list of contributors to VinylDNS.

See [MAINTAINERS.md](MAINTAINERS.md) for documentation specific to maintainers

## Credits

VinylDNS would not be possible without the help of many other pieces of open source software. Thank you open source
world!

Given the Apache 2.0 license of VinylDNS, we specifically want to call out the following libraries and their
corresponding licenses shown below.

- [h2 database](http://h2database.com)
    - [Mozilla Public License, version 2.0](https://www.mozilla.org/MPL/2.0/)
- [pureconfig](https://github.com/pureconfig/pureconfig)
    - [Mozilla Public License, version 2.0](https://www.mozilla.org/MPL/2.0/)
- [pureconfig-macros](https://github.com/pureconfig/pureconfig)
    - [Mozilla Public License, version 2.0](https://www.mozilla.org/MPL/2.0/)
- [junit](https://junit.org/junit4/)
    - [Eclipse Public License 1.0](https://www.eclipse.org/legal/epl-v10.html)
