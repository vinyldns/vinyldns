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

| Flag | Description                                                                  |
|:---|:-----------------------------------------------------------------------------|
| `-a, --api-only`     | do not start up the VinylDNS Portal"                                         |
| `-b, --build`        | force a rebuild of the Docker images with the local code"                    |
| `-c, --clean`        | stops all VinylDNS containers"                                               |
| `-d, --deps-only`    | only start up the dependencies, not the API or Portal"                       |
| `-r, --reset`        | reset any the running containers"                                            |
| `-s, --service`      | specify the service to run"                                                  |
| `-t, --timeout`      | the time to wait (in seconds) for the Portal and API to start (default: 60)" |
| `-u, --update`       | remove the local quickstart images to force a re-pull from Docker Hub"       |
| `-v, --version-tag`  | specify Docker image tag version (default: latest)"                          |
