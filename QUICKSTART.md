# Quickstart

Docker images for VinylDNS live on Docker Hub at <https://hub.docker.com/u/vinyldns/>.
To start up a local instance of VinylDNS on your machine with docker:

1. Ensure that you have [docker](https://docs.docker.com/install/) and [docker-compose](https://docs.docker.com/compose/install/)
1. Clone the repo: `git clone https://github.com/vinyldns/vinyldns.git`
1. Navigate to repo: `cd vinyldns`
1. Run `.bin/docker-up-vinyldns.sh`. This will start up the api at `localhost:9000` and the portal at `localhost:9001`
1. To stop the local setup, run `./bin/remove-vinyl-containers.sh`.

There is a preloaded testuser with the username "testuser", and password "testpassword". If you view the portal in a web browser at 
<http://localhost:9001>, you can use those credentials to login.

There exist several clients at <https://github.com/vinyldns> that can be used to make API requests, using the endpoint `http://localhost:9000`

## Loading test data

Normally the portal can be used for all VinylDNS requests, but an alternative client needs to be used for `testuser` to connect to a zone

An example if you have Node installed:

``` 
git clone https://github.com/vinyldns/vinyldns-js.git
cd vinyldns-js
npm install
export VINYLDNS_API_SERVER=http://localhost:9000
export VINYLDNS_ACCESS_KEY_ID=testUserAccessKey
export VINYLDNS_SECRET_ACCESS_KEY=testUserSecretKey
> Have the ID of a vinyldns group ready, you can either create one in the portal or using another client
npm run repl
vinyl.createZone ({name: "ok.", isTest: true, adminGroupId: "your-group-id", email: "test@test.com"}).then(res => { console.log(res) }).catch(err => { console.log(err) })

other test zone names: "dummy.", "vinyldns."
```

## Things to try in the portal after you connect to a zone

1. View the portal at <http://localhost:9001> in a web browser
1. Login with the credentials ***testuser*** and ***testpassword***
1. Navigate to the `groups` tab: <http://localhost:9001/groups>
1. Click on the **New Group** button and create a new group, the group id is the uuid in the url after you view the group
1. View zones you connected to in the `zones` tab: <http://localhost:9001/zones>
1. You will see that some records are preloaded in the zone already, this is because these records are preloaded in the local docker DNS server 
and VinylDNS automatically syncs records with the backend DNS server upon zone connection
1. From here, you can create DNS record sets in the **Manage Records** tab, and manage zone settings and ***ACL rules***
in the **Manage Zone** tab
1. To try creating a DNS record, click on the **Create Record Set** button under Records, `Record Type = A, Record Name = my-test-a,
TTL = 300, IP Addressess = 1.1.1.1`
1. Click on the **Refresh** button under Records, you should see your new record created

## Other things to note

1. Upon connecting to a zone for the first time, a zone sync is ran to provide VinylDNS a copy of the records in the zone
1. Changes made via VinylDNS are made against the DNS backend, you do not need to sync the zone further to push those changes out
1. If changes to the zone are made outside of VinylDNS, then the zone will have to be re-synced to give VinylDNS a copy of those records
1. If you wish to modify the url used in the creation process from `http://localhost:9000`, to say `http://vinyldns.yourdomain.com:9000`, you can modify the `bin/.env` file before execution.
1. A similar `docker/.env` can be modified to change the default ports for the Portal and API. You must also modify their config files with the new port: https://www.vinyldns.io/operator/config-portal & https://www.vinyldns.io/operator/config-api

