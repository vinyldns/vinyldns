# Docker Images

This folder contains the tools to create Docker images for the VinylDNS API and Portal


## Docker Images

- `vinyldns/api` - this is the heart of the VinylDNS system, the backend API
- `vinyldns/portal` - the VinylDNS web UI

### `vinyldns/api`

The default build for vinyldns api assumes an **ALL MYSQL** installation.

#### Environment Variables

- `VINYLDNS_VERSION` - this is the version of VinylDNS the API is running, typically you will not set this as it is set
  as part of the container build

#### Volumes

- `/opt/vinyldns/conf/` - if you need to have your own application config file. This is **MANDATORY** for any production
  environments. Typically, you will add your own `application.conf` file in here with your settings.
- `/opt/vinyldns/lib_extra/` - if you need to have additional jar files available to your VinylDNS instance. Rarely
  used, but if you want to bring your own message queue or database you can put the `jar` files there

### `vinyldns/portal`

The default build for vinyldns portal assumes an **ALL MYSQL** installation.

#### Environment Variables

- `VINYLDNS_VERSION` - this is the version of VinylDNS the API is running, typically you will not set this as it is set
  as part of the container build

#### Volumes

- `/opt/vinyldns/conf/` - if you need to have your own application config file. This is **MANDATORY** for any production
  environments. Typically, you will add your own `application.conf` file in here with your settings.
- `/opt/vinyldns/lib_extra/` - if you need to have additional jar files available to your VinylDNS instance. Rarely
  used, but if you want to bring your own message queue or database you can put the `jar` files there
