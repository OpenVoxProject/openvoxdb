---
title: "Configuring OpenVoxDB"
layout: default
canonical: "/puppetdb/latest/configure.html"
---

# Configuring OpenVoxDB

[configure-postgres]: ./configure_postgres.markdown
[java-patterns]: https://docs.oracle.com/javase/8/docs/api/java/util/regex/Pattern.html
[logback]: http://logback.qos.ch/manual/configuration.html
[dashboard]: ./maintain_and_tune.markdown#monitor-the-performance-dashboard
[repl]: ./repl.markdown
[pg_trgm]: http://www.postgresql.org/docs/current/static/pgtrgm.html
[postgres_ssl]: ./postgres_ssl.markdown
[migration-coordination]: ./migration_coordination.markdown
[module]: ./install_via_module.markdown
[puppetdb.conf]: ./connect_puppet_server.markdown#edit-puppetdbconf
[node-ttl]: #node-ttl
[admin-cmd]: ./api/admin/v1/cmd.markdown
[query-timeout-parameter]: ./api/query/v4/overview.markdown#url-parameters

OpenVoxDB has three main groups of settings:

* The init script's configuration file, which sets the JVM heap size and the location of OpenVoxDB's main config file.
* Logging settings, which go in the [logback.xml](#logging-config) file and can be changed without restarting OpenVoxDB.
* All other settings, which go in OpenVoxDB's configuration file(s) and take effect after the service is restarted.

## Init Script Config File

If you installed OpenVoxDB from packages or used the `rake install`
installation method, an init script was created for OpenVoxDB. This
script has its own configuration file, the location of which varies by
platform and by package:

OS and Package               | File
-----------------------------|----------------------------
Red Hat-like                 | `/etc/sysconfig/puppetdb`
Debian/Ubuntu                | `/etc/default/puppetdb`

 In this file, you can change the following settings:

- **`JAVA_BIN`**: the location of the Java binary.
- **`JAVA_ARGS`**: command line options for the Java binary, most notably the `-Xmx` (max heap size) flag.
- **`USER`**: the user OpenVoxDB should be running as.
- **`INSTALL_DIR`**: the directory into which OpenVoxDB is installed.
- **`CONFIG`**: the location of the OpenVoxDB config file, which may be a single file or a directory of .ini files.

### Configuring the Java heap size

To change the JVM heap size for OpenVoxDB, edit the [init script config
file](#init-script-config-file) by setting a new value for the `-Xmx`
flag in the `JAVA_ARGS` variable.

For example, to cap OpenVoxDB at 192MB of memory:

    JAVA_ARGS="-Xmx192m"

To use 1GB of memory:

    JAVA_ARGS="-Xmx1g"

### Configuring JMX access

While all JMX metrics are exposed using the `/metrics` namespace, you can also
expose direct JMX access using standard JVM means as documented
[here](http://docs.oracle.com/javase/6/docs/technotes/guides/management/agent.html).
This can be done using the `JAVA_ARGS` init script setting, similar to configuring the heap size.

WARNING: DO NOT DO THIS WITH A PRODUCTION OR INTERNET-ACCESSIBLE INSTANCE! 
This gives remote access to the JVM internals, including potentially secrets.
If you absolutely must (you don't), read about using certs with JMX to do it
securely. You are better off using the metrics API or Grafana metrics
exporter.

For example, adding the following JVM options will open
up a JMX socket on port 1099:

    JAVA_ARGS="-Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.port=1099"


## The Logback logging-config file

Logging is configured with a logback.xml file, whose location is
defined with the [`logging-config`](#logging-config) setting. If you
change the log settings while OpenVoxDB is running, it will apply the
new settings without requiring a restart.

[See the Logback documentation][logback] for more information about logging options.


## The OpenVoxDB configuration file(s)

OpenVoxDB is configured using an INI-style config format with several
`[sections]`. This is very similar to the format used by OpenVox. All
of the sections and settings described below belong in the OpenVoxDB
config file(s).

> **Note:** Whenever you change OpenVoxDB's configuration settings, you must restart the service for the changes to take effect.

You can change the location of the main config file in [the init
script config file](#init-script-config-file). This location can point
to a single configuration file or a directory of .ini files. If you
specify a directory (in _conf.d_ style), OpenVoxDB will merge the .ini
files in alphabetical order.

If you've installed OpenVoxDB from a package, by default it will use
the _conf.d_ config style. The default config directory is
`/etc/puppetlabs/puppetdb/conf.d`. If you're running from source, you
may use the `-c` command-line argument to specify your config file or
directory.

An example configuration file:

    [global]
    vardir = /var/lib/puppetdb
    logging-config = /var/lib/puppetdb/logback.xml

    [database]
    subname = //localhost:5432/puppetdb

    [puppetdb]
    certificate-allowlist = /path/to/file/containing/certnames

    [jetty]
    port = 8080

### Playing nice with the OpenVoxDB module

If you [installed OpenVoxDB with the puppet-openvoxdb
module][module], OpenVoxDB's settings will be managed by OpenVox. Most
of the settings you care about can be configured with the module's
class parameters; see [the module's
documentation](https://forge.puppet.com/puppet/openvoxdb) for
details.

If you _do_ need to change those rare settings that the module doesn't
manage, you can do the following:

Create a new class in a new module (something like
`site::openvoxdb::server::extra`), declare any number of `ini_setting`
resources as shown below, set the class to refresh the
`openvoxdb::server` class, and assign it to your OpenVoxDB server.

~~~ ruby
    # Site-specific OpenVoxDB settings. Declare this class on any node that gets the openvoxdb::server class.
    class site::openvoxdb::server::extra {

      # Restart the OpenVoxDB service if settings change
      Class[site::openvoxdb::server::extra] ~> Class[openvoxdb::server]

      # Get OpenVoxDB confdir
      include openvoxdb::params
      $confdir = $openvoxdb::params::confdir

      # Set resource defaults
      Ini_setting {
        ensure  => present,
        require => Class['openvoxdb::server::validate_db'],
      }

      ini_setting {'openvoxdb-extra-setting':
        path    => "${confdir}/global.ini",
        section => 'global',
        setting => <some-extra-setting>,
        value   => 'true',
      }
    }
~~~

## `[global]` settings

The `[global]` section is used to configure application-wide behavior.

### `vardir`

This defines the parent directory for the MQ's data directory. The
directory must exist and be writable by the OpenVoxDB user in order for
the application to run.

### `logging-config`

This describes the full path to a
[logback.xml](http://logback.qos.ch/manual/configuration.html)
file. Covering all the options available for configuring Logback is
outside the scope of this guide: see the [Logback documentation][logback] for
exhaustive information.

If this setting isn't provided, OpenVoxDB defaults to logging at INFO
level to standard out.

If you installed from packages, OpenVoxDB will use the logback.xml file
in the `/etc/puppetdb/` or `/etc/puppetlabs/puppetdb`
directory. Otherwise, you can find an example file in the `ext`
directory of the source.

You can edit the logging configuration file while OpenVoxDB is running,
and it will automatically react to changes after a few seconds.

## `[puppetdb]` settings

The `[puppetdb]` section is used to configure OpenVoxDB
application-specific behavior.

### `query-timeout-default`

A limit on the number of seconds that a query will be allowed to run,
defaulting to 10 minutes.  Setting it to zero disables the timeout.
This limit applies only if the incoming query has not specified its
own [`timeout`][query-timeout-parameter].  See the `query-timeout-max`
(below) for a hard upper limit.

If the limit is reached, the query will be interrupted.  At the
moment, that will result in either a 500 HTTP response status, or
(more likely) a truncated JSON result if the result has begun
streaming.  Specifying this parameter is strongly encouraged.
Lingering queries can consume substantial server resources
(particularly on the PostgreSQL server) decreasing performance, for
example, and increasing the maximum required storage space.

At the moment, this limit only applies to the `/pdb//query/..`
endpoints.

### `query-timeout-max`

An optional limit on the number of seconds that any query will be
allowed to run.  This limit applies to all queries, and provides an
upper bound with respect to any `query-timeout-default` setting
(above) or query-specific [`timeout`][query-timeout-parameter].  See
the `query-timeout-default` description for additional information.

At the moment, this limit only applies to the `/pdb//query/..`
endpoints.

Note that this maximum does not apply to queries with
`origin=puppet:puppetdb-sync-*`, which specify their own timeouts.

### `certificate-allowlist`

Optional. This describes the path to a file that contains a list of
certificate names, one per line. Incoming requests will have
their certificates validated against this list of names and only those
with an **exact** matching entry will be allowed through. (For an OpenVox
Server, this compares against the value of the `certname` setting,
rather than the `dns_alt_names` setting.)

The allowlist applies to every request that is subject to certificate
authentication, including requests that arrive on the cleartext HTTP port. A
cleartext client cannot present a certificate, so it can never satisfy the
allowlist; see [`allow-unauthenticated-cleartext`](#allow-unauthenticated-cleartext)
for the exemptions that apply to that port.

If not supplied, OpenVoxDB requires every client to present a certificate
signed by its CA, but does not restrict which certnames may connect.

### `allow-unauthenticated-cleartext`

Optional, defaults to `false`. Requests that arrive on the cleartext HTTP port
(`[jetty] port`) cannot present a client certificate, so they cannot be
authenticated. By default they are accepted only when they come from a loopback
address, which is what the shipped configuration expects: the cleartext port is
bound to `localhost` and is there for local administration and for the
performance dashboard.

Set this to `true` if you terminate authentication somewhere else, for example
when OpenVoxDB sits behind a reverse proxy running on another host, and you have
restricted the cleartext port to that proxy. Every cleartext request is then
accepted without authentication, and OpenVoxDB logs a warning at startup saying
so. Anything that can reach the port can read the whole database, submit
commands for any node, and delete node data, so do not enable this on a port
that untrusted clients can reach.

Requests on the HTTPS port are always authenticated, regardless of this
setting.

### `log-queries`
Optional. Setting this to `true` will enable debug level logging of the internal
AST and SQL that OpenVoxDB generates for all queries. This can be useful when
debugging query performance. If unset, the default value is `false`.
[See the Logback documentation][logback] for more information about logging
options and how to enable debug level logging.

## `[database]` settings

The `[database]` section configures OpenVoxDB's database settings.
OpenVoxDB stores its data in PostgreSQL.

> **FAQ: Why no MySQL or Oracle support?**
>
> MySQL lacks several features that OpenVoxDB relies on, most notably including recursive queries. We have no plans to ever support MySQL.
>
> Depending on demand, Oracle support may be forthcoming in a future version of OpenVoxDB. This hasn't been decided yet.

### `gc-interval`

This controls how often, in minutes, to compact the database. The
compaction process reclaims space and deletes unnecessary rows. If not
supplied, the default is every 60 minutes.  If set to zero, all
database GC processes will be disabled.

### `gc-interval-expire-nodes`

This controls how often, in minutes, to expire nodes that are no longer
submitting commands. If not supplied, this value defaults to `gc-interval`.

### `gc-interval-purge-nodes`

This controls how often, in minutes, to remove nodes that have been expired or
deactivated for longer than `node-purge-ttl`. If not supplied, this value
defaults to `gc-interval`.

### `gc-interval-purge-reports`

This controls how often, in minutes, to remove reports and that have been
expired or deactivated for longer than `reports-ttl` or `resource-events-ttl`
respectively. If not supplied, this value defaults to `gc-interval`.

### `gc-interval-packages`

This controls how often, in minutes, to remove packages that are no longer
associated with any nodes. If not supplied, this value defaults to
`gc-interval`.

### `gc-interval-catalogs`

This controls how often, in minutes, to remove catalog data that is no longer
associated with any nodes. If not supplied, this value defaults to
`gc-interval`.

### `gc-interval-fact-paths`

This controls how often, in minutes, to remove fact paths that are no longer
associated with any factsets. If not supplied, this value defaults to 24 hours
or `gc-interval`, which ever is longer.

### `node-ttl`

Mark as 'expired' nodes that haven't seen any activity (no new catalogs,
facts, or reports) in the specified amount of time. Expired nodes behave the same
as manually-deactivated nodes.

You may specify the time as a string using any of the following suffixes:

    `d`  - days
    `h`  - hours
    `m`  - minutes
    `s`  - seconds
    `ms` - milliseconds

For example, a value of `30d` would set the time-to-live to 30 days, and a value of
`48h` would set the time-to-live to 48 hours.

Nodes will be checked for staleness every `gc-interval` minutes. Manual
deactivation will continue to work as always.

If unset, nodes are auto-expired after 7 days of inactivity. If set to 0s,
auto-expiration of nodes is disabled.

### `node-purge-ttl`

Automatically delete nodes that have been deactivated or expired for the
specified amount of time. This will also delete all facts, catalogs, and reports
for the relevant nodes. This TTL may be specified the same way as `node-ttl` above.

If unset, nodes are purged after 14 days. If set to 0s, auto-deletion of nodes
is disabled.

### `node-purge-gc-batch-limit`

Nodes will be purged in batches of this size, one batch per
`gc-interval`.  If unset, the batch limit will be 25, and if you
expect to generate eligible nodes faster than that (on average), you
should either increase this limit so that OpenVoxDB will be able to
keep up, or complement the automatic GC process with manual
`purge_node` requests to the [cmd endpoint][admin-cmd] to cover the
excess.

### `report-ttl`

Automatically delete reports that are older than the specified amount of time.
You may specify the time as a string using any of the suffixes described in the
`node-ttl` section above.

Outdated reports will be deleted during the database garbage collection, which
runs every `gc-interval` minutes.

If unset, the default value is 14 days.

### `resource-events-ttl`

Automatically delete report events older than the specified time. Reports are
unaffected until their respective TTLs.

This allows for more fine-grained control of the expiration of reports. If
unset, it will default to the value of `report-ttl`. It may not be set to a
value larger than `report-ttl`.

Note: this value is in UTC days - any value will be rounded to the nearest day.
Example: 14 hours will be 1 day, 25 hours will be 2 days.

See `node-ttl` above for the format of this value.

### `subname`

This describes where to find the database. Set this to
`//<HOST>:<PORT>/<DATABASE>` when using PostgreSQL, replacing `<HOST>` with the
DB server's hostname, `<PORT>` with the port on which PostgreSQL is listening,
and `<DATABASE>` with the name of the database.  See the [Postgres SSL
doc][postgres_ssl] for details on configuring the `subname` option for an
encrypted connection between OpenVoxDB and Postgres.  Without additional
parameters, OpenVoxDB's database connections will communicate in plaintext.

### `username`

The database user specified for connections performing normal
operations (queries, command processing, etc.).

### `connection-username`
The database user for special cases when the database connection username
is different from the username reported by the database.
An example is managed PostgreSQL in Azure, in order to connect we need to specify
`username@hostname`, but the user reported by the database is `username`.
If not specified, it will default to `username`


### `password`

This is the password to use when connecting. Only used with PostgreSQL. This
password is stored in plain-text in the configuration file. You can remove this
configuration option if you [configure an SSL connection][postgres_ssl] and
configure postgres to use certificates to authorize the database connections.
If your PostgreSQL server is on a different server, you should configure an SSL
connection between OpenVoxDB and Postgres, otherwise the database communication
is done via plaintext.

### `migrator-username`

The database user specified for database migration operations, in
particular the database validation and migration at startup.  Defaults
to the `username`.  See the [PostgreSQL configuration
section](configure-postgres) for some important requirements for the
privileges of this user (role), and the [migration coordination
section](migration-coordination) for an overview of the process.

### `connection-migrator-username`
The database migrator user for special cases when the database connection username
is different from the username reported by the database.
An example is managed PostgreSQL in Azure, in order to connect we need to specify
`username@hostname`, but the user reported by the database is `username`.
If not specified, it will default to `migrator-username`

### `migrator-password`

This is the password to use when connecting for database migrations.  Defaults
to `password`. This password is stored in plain-text in the configuration file.
You can remove this configuration option if you [configure an SSL
connection][postgres_ssl] and configure postgres to use certificates to
authorize the database connection. If your PostgreSQL server is on a different
server, you should configure an SSL connection between OpenVoxDB and Postgres,
otherwise the database communication is done via plaintext.

### `migrate`

When set to `true` (the default), OpenVoxDB will upgrade the data in
the database to the latest format at startup.  When `false`, OpenVoxDB
will exit with an error status if the format version is not the one it
expects, whether newer or older.  See the [PostgreSQL configuration
section](configure-postgres) for some important requirements for the
privileges of this user (role), and the [migration coordination
section](migration-coordination) for an overview of the process.

### `maximum-pool-size`

From the [HikariCP documentation](https://github.com/brettwooldridge/HikariCP):

> "This property controls the maximum size that the pool is allowed to reach,
> including both idle and in-use connections. Basically this value will
> determine the maximum number of actual connections to the database backend. A
> reasonable value for this is best determined by your execution environment."

When the pool reaches this size, and no idle connections are available, attempts
to get a connection will wait for `connection-timeout` milliseconds before timing
out.

The default value is 25. Note that OpenVoxDB will use one pool for writes and another
for reads, so the total number of connections used will be twice this setting.

### `conn-max-age`

The maximum time (in minutes), for a pooled connection to remain
unused before it is closed off.

If not supplied, the default value is 60 minutes.

### `conn-lifetime`

The maximum time (in minutes) a pooled connection should remain
open. Any connections older than this setting will be closed
off. Connections currently in use will not be affected until they are
returned to the pool.

If not supplied, we won't terminate connections based on their age
alone.

### `connection-timeout`

The maximum time to wait (in milliseconds) to acquire a connection
from the pool of database connections. If not supplied, defaults to
3000.

### `facts-blocklist`

Optional.  A list of fact names to be ignored whenever submitted.  The
`facts-blocklist-type` determines whether the names are matched
literally or as [Java regular expresions][java-patterns].

The names must be comma-separated in an INI configuration file, or a
list in a HOCON file:

 * INI: `facts-blocklist = fact1, fact2, fact3`
 * HOCON: `facts-blocklist = ["fact1", "fact2", "fact3"]`

When matching lterally, the entire fact name (not including the path)
must completely match one of the `facts-blocklist` entries in order to
be blocklisted.  When matching regular expressions, the name must
match the entire pattern.  For example the pattern "xyz" will not
match the fact "123xyzabc", but ".\*xyz.\*" will.

### `facts-blocklist-type`

Optional.  When set to `literal` (or not set) the `facts-blocklist`
names will be matched literally.  When set to `regex` (the only other
legal value), the names will be matched as [Java regular
expresions][java-patterns].  See the `facts-blocklist` description
above for additional information.

### `schema-check-interval`

This controls how often, in milliseconds, to check the schema version OpenVoxDB
is compatible with against the database's schema version. The default is every
30 seconds. If a mismatch is detected OpenVoxDB will exit with an error message
suggesting appropriate action. If set to zero, this check is disabled.

## `[read-database]` settings

The `[read-database]` section configures OpenVoxDB's _read-database_
settings, useful when running a PostgreSQL [Hot
Standby](http://wiki.postgresql.org/wiki/Hot_Standby) cluster.
Currently, only configuring a PostgreSQL read-database is supported.  See
the PostgreSQL documentation [here](http://wiki.postgresql.org/wiki/Hot_Standby)
for details on configuring the cluster. The `[read-database]` portion
of the configuration is in addition to the `[database]` settings. If
`[read-database]` is specified, `[database]` must also be specified.

To configure OpenVoxDB to use a read-only database from the cluster,
add the following to the `[read-database]` section:

    subname = //<HOST>:<PORT>/<DATABASE>
    username = <USERNAME>
    password = <PASSWORD>

Replace `<HOST>` with the DB server's hostname. Replace `<PORT>` with
the port on which PostgreSQL is listening. Replace `<DATABASE>` with
the name of the database you've created for use with OpenVoxDB.

### `subname`

This describes where to find the database. Set this to
`//<HOST>:<PORT>/<DATABASE>` when using PostgreSQL, replacing `<HOST>` with the
DB server's hostname, `<PORT>` with the port on which PostgreSQL is listening,
and `<DATABASE>` with the name of the database.  See the [Postgres SSL
doc][postgres_ssl] for details on configuring the `subname` option for an
encrypted connection between OpenVoxDB and Postgres.  Without additional
parameters, OpenVoxDB's database connections will communicate in plaintext.

### `username`

This is the username to use when connecting.  See the [PostgreSQL
configuration section](configure-postgres) for some important
requirements for the privileges of this user (role).

### `password`

This is the password to use when connecting. Only used with PostgreSQL. This
password is stored in plain-text in the configuration file. You can remove this
configuration option if you [configure an SSL connection][postgres_ssl] and
configure postgres to use certificates to authorize the database connection. If
your PostgreSQL server is on a different server, you should configure an SSL
connection between OpenVoxDB and Postgres, otherwise the database communication
is done via plaintext.

### `maximum-pool-size`

From the [HikariCP documentation](https://github.com/brettwooldridge/HikariCP):

> "This property controls the maximum size that the pool is allowed to reach,
> including both idle and in-use connections. Basically this value will
> determine the maximum number of actual connections to the database backend. A
> reasonable value for this is best determined by your execution environment."

When the pool reaches this size, and no idle connections are available, attempts
to get a connection will wait for `connection-timeout` milliseconds before timing
out.

The default value is 10.

### `conn-max-age`

The maximum time (in minutes) for a pooled connection to remain
unused before it is closed off.

If not supplied, the default value is 60 minutes.

### `conn-lifetime`

The maximum time (in minutes) a pooled connection should remain
open. Any connections older than this setting will be closed
off. Connections currently in use will not be affected until they are
returned to the pool.

If not supplied, we won't terminate connections based on their age
alone.

### `connection-timeout`

The maximum time to wait (in milliseconds) to acquire a connection
from the pool of database connections. If not supplied, defaults to
500.


## `[command-processing]` Settings

The `[command-processing]` section configures the command-processing
subsystem.

Every change to OpenVoxDB's data stores arrives via **commands** that
are inserted into a message queue (MQ). Command processor threads pull
items off of that queue, persisting those changes.

### `threads`

This defines how many command processing threads to use. Each thread
can process a single command at a time. [The number of threads can be
tuned based on what you see in the performance dashboard.][dashboard]

This setting defaults to half the number of cores in your system.

### `concurrent-writes`

This sets a limit on the number of threads that can write to the disk at
any one time. The default value is the smaller number of half the number
of CPU cores and 4.

If your load is low, your disk is fast (i.e. an SSD), and commands
aren't being processed quickly enough, then you could increasing this
value in order to alleviate that, but this is unlikely to be the
bottleneck for command processing.

### `reject-large-commands`

This is a Boolean that enables rejecting (returning an HTTP 413 error)
commands that are too large to process, such as a
catalog that is too large, causing OpenVoxDB to run out of
memory. This setting can be used along with `max-command-size`.

This setting is false by default.

### `max-command-size`

This is an integer that specifies (in bytes) which commands are "too
large" to process with OpenVoxDB. By default this setting is a fraction
of the total heap space. It is strongly recommended that users set
this manually as the default is probably too conservative. To help
determine the current size of commands being processed, enable debug
logging for the `puppetlabs.puppetdb.middleware` appender in the
[logback.xml](#logging-config). This setting has no effect when
`reject-large-commands` is set to false.


## `[jetty]` (HTTP) settings

The `[jetty]` section configures HTTP for OpenVoxDB.

### `host`

Sets the IP interface to listen on for **unencrypted** HTTP
traffic. If not supplied, we bind to `localhost`, which will reject
connections from anywhere but the OpenVoxDB server itself. To listen on
all available interfaces, use `0.0.0.0`.

To avoid DNS resolution confusion, if you wish to set this to something other than `localhost`, we reccomend using an IP address instead of a hostname.

> **Note:** Unencrypted HTTP is the only way to view the [performance
    dashboard][dashboard], because OpenVoxDB uses host verification for
    SSL. However, it can also be used to make any call to OpenVoxDB's
    API, including inserting exported resources and retrieving
    arbitrary data about your OpenVox-managed nodes. **If you enable
    cleartext HTTP, you MUST configure your firewall to protect
    unverified access to OpenVoxDB.**

### `port`

Establishes which port to use for **unencrypted** HTTP traffic. If not
supplied, we won't listen for unencrypted traffic at all.

### `max-threads`

Sets the maximum number of threads assigned to responding to HTTP
and HTTPS requests, effectively changing how many concurrent requests
can be made at one time. Defaults to 50.

> **Note:** Due to the behaviour of our web server (Jetty 10), this setting
    must be higher then the number of CPUs on your system or it will
    stop processing any HTTP requests.

### `ssl-host`

Sets which IP interface to listen on for **encrypted** HTTPS traffic. If
not supplied, we bind to `localhost`. To listen on all available
interfaces, use `0.0.0.0`.

To avoid DNS resolution confusion, if you wish to set this to something other than `localhost`, we reccomend using an IP address instead of a hostname

### `ssl-port`

Establishes which port to use for **encrypted** HTTPS traffic. If not
supplied, we won't listen for encrypted traffic at all.

### `ssl-cert`

Sets the path to the server certificate PEM file used by the
OpenVoxDB web service for HTTPS. During the SSL handshake for a
connection, certificates extracted from this file are presented to the
client for the client's use in validating the server. This file may
contain a single certificate or a chain of certificates ordered from
the end certificate first to the most-root certificate last. For
example, a certificate chain could contain:

* An end certificate.
* An intermediate CA certificate with which the end certificate was issued.
* A root CA certificate with which the intermediate CA certificate was issued.

In the PEM file, the end certificate should appear first, the
intermediate CA certificate should appear second, and the root CA
certificate should appear last.

If a chain is present, it is not required to be complete. If a path
has been specified for the `ssl-cert-chain` setting, the server will
construct the cert chain starting with the first certificate found in
the `ssl-cert` PEM and followed by any certificates in the
`ssl-cert-chain` PEM. In the latter case, any certificates in the
`ssl-cert` PEM beyond the first one are ignored.

### `ssl-key`

This sets the path to the private key PEM file that corresponds with
the `ssl-cert`, if used by the OpenVoxDB web service for HTTPS.

### `ssl-ca-cert`

This sets the path to the CA certificate PEM file used for client
authentication. Authorized clients must be signed by the CA that corresponds to this certificate.

### `cipher-suites`

Optional. A comma-separated list of cryptographic ciphers to allow for
incoming SSL connections. Valid names are listed in the
[official JVM cryptographic providers documentation](http://docs.oracle.com/javase/8/docs/technotes/guides/security/SunProviders.html#SupportedCipherSuites). Note
that you must use the all-caps cipher suite name.

If not supplied, OpenVoxDB will use only non-DHE cipher suites.

### `ssl-protocols`

Optional. A comma-separated list of protocols to allow for incoming
SSL connections. Valid names are listed in the
[official JVM cryptographic protocol documentation](http://docs.oracle.com/javase/8/docs/technotes/guides/security/SunProviders.html#SunJSSEProvider). Note
that you must use the names with verbatim capitalization. For example:
`TLSv1.2, TLSv1.3`.

If not supplied, OpenVoxDB uses a default of `TLSv1, TLSv1.1, TLSv1.2`. By default, SSLv3 is not included in that list due to known vulnerabilities. Users wanting to use SSLv3 need to explicitly specify it in their list.

### `ssl-crl-path`

Optional. This describes a path to a Certificate Revocation List
file. Incoming SSL connections will be rejected if the client
certificate matches a revocation entry in the file.

### `ssl-cert-chain`

This sets the path to a PEM with CA certificates for use in presenting a
client with the server's chain of trust. Certs found in this PEM file are
appended after the first certificate from the `ssl-cert` PEM in the
construction of the certificate chain. This is an optional setting. The
certificates in the `ssl-cert-chain` PEM file should be ordered from the
least-root CA certificate first to the most-root CA certificate last. For
example, a certificate chain could contain:

* An end certificate.
* An intermediate CA certificate with which the end certificate was issued.
* A root CA certificate with which the intermediate CA certificate was issued.

The end certificate should appear in the `ssl-cert` PEM file. In the
`ssl-cert-chain` PEM file, the intermediate CA certificate should appear
first and the root CA certificate should appear last.

The chain is not required to be complete.

> **Note:** This setting overrides the alternate configuration settings
`keystore` and `key-password`.

### `access-log-config`

Optional. This is a path to an XML file containing configuration
information for the `logback-access` module. If present, a logger will
be set up to log information about any HTTP requests Jetty receives
according to the logging configuration, as long as the XML file
pointed to exists and is valid. Information on configuring the
`logback-access` module is available
[here](http://logback.qos.ch/access.html#configuration) and additional
information about configuring the pattern layout can be found
[here](http://logback.qos.ch/manual/layouts.html#AccessPatternLayout).

A configuration file may resemble the following:

    <configuration debug="false">
      <appender name="FILE" class="ch.qos.logback.core.FileAppender">
        <file>./dev-resources/access.log</file>
          <encoder>
            <pattern>%h %l %u %user %date "%r" %s %b</pattern>
          </encoder>
        </appender>
        <appender-ref ref="FILE" />
    </configuration>

This example configures a `FileAppender` that outputs to a file,
`access.log`, in the `dev-resources` directory. It will log the remote
host making the request, the log name, the remote user making the
request, the date/time of the request, the URL and method of the
request, the status of the response, and the size in bytes of the
response.

### `graceful-shutdown-timeout`

After receiving a shutdown, this is the number of milliseconds the
server will wait for in-flight requests to complete before actually
shutting down. New requests will be blocked during this time. Defaults
to 30000.

### `request-header-max-size`

This sets the maximum size of an HTTP request header. If a header is
sent that exceeds this value, Jetty will return an HTTP 413 error
response. This defaults to 8192 bytes, and only needs to be configured
if an exceedingly large header is being sent in an HTTP request.

## `[nrepl]` settings

The `[nrepl]` section configures remote runtime modification. For
more detailed info, see [our guide to debugging with the remote REPL][repl].

Enabling a remote
[REPL](http://en.wikipedia.org/wiki/Read%E2%80%93eval%E2%80%93print_loop)
allows you to manipulate the behavior of OpenVoxDB at runtime. This
should only be done for debugging purposes, and is thus disabled by
default. An example configuration stanza:

    [nrepl]
    type = nrepl
    port = 8082
    host = 127.0.0.1

### `enabled`

To enable the REPL, set to true. Defaults to false.

### `port`

The port to use for the REPL.

### `host`

Specifies the host or IP address for the REPL service to listen on. By
default this is `127.0.0.1` only. As this is an insecure channel this
is the only recommended setting for production environments.

If you wish to listen on all interfaces, you can specify `0.0.0.0`, for example, although this is generally not recommended for production.

## `[developer]` settings

The `[developer]` section contains configuration items that may be useful to
users developing against the OpenVoxDB API. These settings may impede
performance, and are not recommended for production use.

### `pretty-print`

Enables/disables default pretty-printing of API responses. Defaults to false.
Enabling default pretty-printing is not recommended in production because it
incurs a penalty in data transfer speed and size. Users may override this
setting on a per-query basis by supplying a `?pretty=` parameter in the URL,
valued `true` or `false`.

## Experimental environment variables

> *Note*: these settings are experimental and are likely to be altered
> or removed in a future release.

### `PDB_COMMAND_SQL_STATEMENT_TIMEOUT_MS`

Controls how many milliseconds (by default 10 minutes) OpenVoxDB will
wait for an SQL command to complete during an attempt to process a
command (store a report, update a factset, etc.).  When set to `0` it
will wait forever (the default before 6.13), and when set to `system`,
it won't specify any timeout, deferring to PostgreSQL's configuration.

### `PDB_GC_DAILY_PARTITION_DROP_LOCK_TIMEOUT_MS`

Controls how many milliseconds (by default 5 minutes) OpenVoxDB will
wait for the required lock when it attempts to drop a report or
resource event partition that has expired.  When set to `0` it will
wait forever (the default before 6.13), and when set to `system`, it
won't specify any timeout, deferring to PostgreSQL's configuration.

### `PDB_FACT_PATH_GC_SQL_LOCK_TIMEOUT_MS`

Controls how many milliseconds OpenVoxDB's fact path garbage collection
process will wait for the lock it needs.  When set to `0` it will wait
forever (the default), and when set to `system`, it won't specify
any timeout, deferring to PostgreSQL's configuration.

### `PDB_GC_QUERY_BULLDOZER_TIMEOUT_MS`

Controls how many milliseconds (by default 5 minutes) OpenVoxDB GC
will wait for the query bulldozer thread it spawns to be cleaned
up before logging an error. When set to `0` the query bulldozer
thread is disabled and OpenVoxDB GC will wait in line to get
the AccessExclusiveLock it needs to drop partitioned tables.
