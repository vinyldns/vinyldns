## Bind Test Configuration

This folder contains test configuration for BIND zones. The zones are partitioned into four distinct partitions to allow
for four parallel testing threads that won't interfere with one another.

### Layout

| Directory | Detail |
|:---|:---|
| `etc/` | Contains zone configurations separated by partition |
| `etc/_template` | Contains the template file for creating the partitioned `conf` files. Currently this is just a find and replace operation - finding `{placeholder}` and replacing it with the desired placeholder. |
| `zones/` | Contains zone definitions separated by partition |
| `zones/_template` |Contains the template file for creating the partitioned zone files. Currently this is just a find and replace operation - finding `{placeholder}` and replacing it with the desired placeholder. |

### Target Directories

When used in a container, or to run `named`, the files in this directory should be copied to the following directories:

| Directory | Target |
|:---|:---|
| `etc/named.conf.local` | `/etc/bind/` |
| `etc/named.partition*.conf` | `/var/bind/config/` |
| `zones/` | `/var/bind/` |
