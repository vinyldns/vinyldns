# VinylDNS documentation site

https://www.vinyldns.io/

## Publication

The VinylDNS documentation is published to the `gh-pages` branch after each successful master branch build. This is
configured through Travis CI.

## Documentation Structure

- The documentation site is built with the [sbt-microsites](https://47deg.github.io/sbt-microsites/) plugin.
- The [docs module](https://github.com/vinyldns/vinyldns/tree/master/modules/docs/src/main) contains most content for
  the documentation site:
    - The text content is in the `src/main/mdoc` directory
    - The primary menu is built through setting a position value in the linked file `src/main/modc/index.md`
    - The sidebar menu is maintained in the `src/main/resources/microsite/data/menu.yml` file
    - Images are stored in `/src/main/resources/microsite/img/` directory
    - Custom CSS is stored in the `src/main/resources/microsite/css/custom.css` file
- The [Contributing Guide](https://www.vinyldns.io/contributing.html) is
  the [CONTRIBUTING.md](https://github.com/vinyldns/vinyldns/blob/master/CONTRIBUTING.md) file at the root of the
  VinylDNS project.
- The sbt-microsite configuration is in the docSettings section of
  the  [build.sbt](https://github.com/vinyldns/vinyldns/blob/master/build.sbt) in the root of the VinylDNS project.

## Build with Docker

To build with Docker, from the `modules/docs` director you can run `make`. This will provide you with a prompt in a
container that is configured with all of the prerequisites and the `/build` directory will be mapped to the VinylDNS
root directory. From there you can follow the [steps below](#Build Locally).

Example:

```bash
$ make
root@1e7375bec453:/build# sbt
sbt:root> project docs
[info] set current project to docs (in build file:/build/)
sbt:docs> makeMicrosite
```

## Build Locally

To build the documentation site you will need `Jekyll 4.0+` installed. This is installed by default in
the [Docker container](#Build with Docker).

In the terminal enter:

1. `sbt`
1. `project docs`
1. `makeMicrosite`

In a separate tab enter:

1. `cd modules/docs/target/site`
2. `jekyll serve --host 0.0.0.0`
    - By default `jekyll` listens on `127.0.0.1` which will cause problems whe using Docker, so we specify that it
      should listen on interfaces by providing  `--host 0.0.0.0`
3. View in the browser at http://localhost:4000/
    - Note: port 4000 is mapped to localhost by the Docker container as well

Tips:

- If you make any changes to the documentation you'll need to run `makeMicrosite` again. You don't need to restart
  Jekyll.
- If you only need to build the microsite once you can run `sbt ";project docs ;makeMicrosite"` then follow the jekyll
  steps from the same tab. -If you delete files you may need to stop Jekyll and delete the target directory before
  running `makeMicrosite` again to see the site as expected locally.
