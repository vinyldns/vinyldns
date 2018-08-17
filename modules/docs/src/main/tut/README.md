# VinylDNS documentation site

https://www.vinyldns.io/

## Publication
The VinylDNS documentation is published to the `gh-pages` branch after each successful master branch build. This is configured through Travis CI.

## Documentation Structure
- The documentation site is built with the [sbt-microsites](https://47deg.github.io/sbt-microsites/) plugin.
- The [docs module](https://github.com/vinyldns/vinyldns/tree/master/modules/docs/src/main) contains most content for the documentation site.
- The [Contributing Guide](https://www.vinyldns.io/contributing.html) is the [CONTRIBUTING.md](https://github.com/vinyldns/vinyldns/blob/master/CONTRIBUTING.md) file at the root of the VinylDNS project.
- The sbt-microsite configuration is in [build.sbt](https://github.com/vinyldns/vinyldns/blob/master/build.sbt) in the root of the VinylDNS project.

## Build Locally
In the terminal enter:
1.  `sbt`
1. `project docs`
1. `makeMicrosite`

In a separate tab enter:
1. `cd modules/docs/target/site`
1. `jekyll serve`
1. View in the browser at http://localhost:4000/

Tips:
* If you make changes to the documentation you'll need to run `makeMicrosite` again.
You don't need to restart Jekyll.
* If you delete files you may need to stop Jekyll and delete the target directory before running `makeMicrosite` again to see the site as expected locally.
