# VinylDNS QuickStart

To get started with VinylDNS, you can experiment with the QuickStart.

QuickStart utilizes Docker Compose to start up the VinylDNS API and Portal along with required dependencies such as:

- MySQL
- OpenLDAP
- SQS
- SNS
- BIND 9

## Running

To run the QuickStart, you will need the following prerequisites:

- Docker
- Docker Compose

From a shell in the `quickstart/` directory, simply run:

```shell script
./quickstart-vinyldns.sh
```

The `quickstart-vinyldns.sh` script takes a number of optional arguments:

| Flag              | Description                                                                                         |
|:------------------|:----------------------------------------------------------------------------------------------------|
| -a, --api         | start the API, but not the Portal and its dependencies (e.g., LDAP)                                 |
| -b, --build       | force a rebuild of the Docker images with the local code                                            |
| -c, --clean       | stops all VinylDNS containers and exits                                                             |
| -d, --deps        | start up the dependencies, but not the API or Portal                                                |
| -sh, --shell      | loads the .env file into a new BASH shell. The .env file can be overridden with -e                  |
| -e, --env-file    | specifies the path (relative to the docker-compose file) to the .env file to load (e.g., .env.dev). |
| -r, --reset       | stops any the running containers before starting new containers                                     |
| -s, --service     | specify the service to run                                                                          |
| -t, --timeout     | the time to wait (in seconds) for the Portal and API to start (default: 60)                         |
| -u, --update      | remove the local quickstart images to force a rebuild                                               |
| -v, --version-tag | specify Docker image tag version (default: latest)                                                  |

## Environment Settings

You can control which environment variables are loaded by using the `--env-file` flag. All `.env.*` files are ignored by `git`
and won't run the risk of being committed.

> It's important to note that due to the way that `docker-compose` works, the custom `.env.*`
> should be in the same directory as the `docker-compose file`. In this case, that would be the `quickstart/` directory.

You can even create a new shell with the environment variables loaded.

Let's say you have a `.env.dev` file in the `quickstart/` directory. You can load those variables into a new
shell with

```shell
$ ./quickstart/quickstart.sh --env-file .env.dev --sh
Please wait.. creating a new shell with the environment variables set.
To return, simply exit the new shell with 'exit' or ^D.
$
```

With these setting loaded you can now run, for example, `sbt` locally and VinylDNS will pull in all of those values.
