# Vinyl Portal
Supplies a UI for and offers authentication into Vinyl, a DNSaaS offering.

# Running Unit Tests
First, startup sbt: `sbt`.

Next, you can run all tests by simply running `test`, or you can run an individual test by running `test-only *MySpec`

# Running Frontend Tests
The frontend code is tested using Jasmine, spec files are stored in the same directory as the angular js files.
For example, the public/lib/controllers has both the controller files and the specs for those controllers. To run
these tests the command is `grunt unit`

# Running Functional Tests
As of now, we have a functional testing harness that gets things set up, and a single test which tests if the login page
loads successfully. Run the following commands from the vinyl-portal folder as well, we are not using a VM for testing
at this time.

`./run_all_tests.sh` will run the unit tests (`sbt clean coverage test`), and then set up and run the func tests

`./run_func_tests.sh` will only set up and run the func tests

# Building Locally

1. You must have npm, if you don't have npm, follow instructions here <https://www.npmjs.com/get-npm>.
2. Run `npm install` to install all dependencies, this includes those needed for testing. If you just want to run the portal then `npm install --production` would suffice
3. You must have grunt, if you don't have grunt, run `npm install -g grunt`. Then run `grunt default` from the root of the portal project
4. Create a local.conf file in the portal conf folder for your settings if desired.
5. Follow the instructions for building vinyl locally on the vinyl readme
6. Start vinyl with `sbt run`. Vinyl will start on localhost on port 9000.
7. Run the portal with `sbt -Djavax.net.ssl.trustStore="./private/trustStore.jks" -Dhttp.port=8080 run`
8. In a web browser go to localhost:9001

# Working locally
Often times as a developer you want to work with the portal locally in a "real" setting against your own LDAP
server.  If your LDAP server requires SSL certs, you will need to create a trust store (or register the
SSL certs on your local machine).  If you create a trust store, you will need to make the trust store
available so that you can start the portal locally and test.

1. Create a trust store and save your certs.
1. Pass the trust store in when you start sbt.  This can be on the command line like...
`sbt -Djavax.net.ssl.trustStore="./private/trustStore.jks"`

# Updating the trustStore Certificates
Sometime on or before May 05, 2020 the certificates securing the AD servers will need to be renewed and updated.
When this happens or some other event causes the LDAP lookup to fail because of SSL certificate issues, follow
the following steps to update the trustStore with the new certificates.
- Get the new certificate with `openssl s_client -connect <ldap server>:<port>`. This will display the certificate on the screen.
- Copy everything from `-----BEGIN CERTIFICATE-----` to `-----END CERTIFICATE-----` including the begin and end markers to the clipboard.
- Open a new file in your favorite text editor
- Paste the clipboard contents into the file
- Save the file and give it a name to be used in the next steps. (ex. `new-ad-ssl-cert.pem`)
- `keytool -printcert -file <your file name>` to view the file and check for errors
- `keytool -importcert -file <your file name> -keystore trustStore.jks -alias <alias for this cert>` (ex. `new-ad-cert`)
- Enter the trustStore password when prompted (look in application.conf)
- Answer yes to trust the certificate

The trustStore is now updated with the new certificate. You can delete the certificate file it is no longer needed.

# Credits

* [logback-classic](https://github.com/qos-ch/logback) - [Eclipse Public License 1.0](https://www.eclipse.org/legal/epl-v10.html)
* [logback-core](https://github.com/qos-ch/logback) - [Eclipse Public License 1.0](https://www.eclipse.org/legal/epl-v10.html)
* [htmlunit](http://htmlunit.sourceforge.net/)
    * [htmlunit-core-js](https://github.com/HtmlUnit/htmlunit-core-js) - [Mozilla Public License v2.0](https://www.mozilla.org/en-US/MPL/2.0/)
