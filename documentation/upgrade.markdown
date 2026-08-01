---
title: "Upgrading OpenVoxDB"
layout: default
---

# Upgrading OpenVoxDB

[dashboard]: ./maintain_and_tune.markdown#monitor-the-performance-dashboard
[connect_server]: ./connect_puppet_server.markdown
[connect_apply]: ./connect_puppet_apply.markdown
[tracker]: https://github.com/OpenVoxProject/openvoxdb/issues
[start_source]: ./install_from_source.markdown#step-4-start-the-puppetdb-service
[plugin_source]: ./connect_puppet_server.markdown#on-platforms-without-packages
[module]: ./install_via_module.markdown
[versioning]: ./versioning_policy.markdown#upgrades

## Checking for updates

OpenVoxDB's [performance dashboard][dashboard] displays the current version in
the upper right corner. New releases are announced on the
[OpenVoxDB releases page](https://github.com/OpenVoxProject/openvoxdb/releases).

## Migrating existing data

If you are not planning to change your underlying OpenVoxDB database
configuration prior to upgrading, you don't need to worry about migrating your
existing data: OpenVoxDB will handle this automatically. If you are upgrading
your Postgres between minor versions, no changes are needed as well. To upgrade
your PostgreSQL database from one major version to another consult the
[PostgreSQL upgrade
docs](https://www.postgresql.org/docs/current/upgrading.html) for information
on your options.

## Upgrading with the OpenVoxDB module

If you [installed OpenVoxDB with the module][module], you only need to do the
following to upgrade between major versions of OpenVoxDB. The module does not
automate major version upgrades of the PostgreSQL database.

1. Make sure that the OpenVox Server has an updated version of the
   [puppet-openvoxdb](https://forge.puppet.com/puppet/openvoxdb)
   module installed.
2. If you imported the official packages into your local package repositories,
   import the new versions of the OpenVoxDB and OpenVoxDB-termini packages.
3. Change the value of the `puppetdb_version` parameter for the `openvoxdb` or
   `openvoxdb::server` and `openvoxdb::master::config` classes, unless it was set
   to `latest`.
4. If you are doing a large version jump, trigger an OpenVox run on the OpenVoxDB
   server before the OpenVox Server has a chance to do an OpenVox run. (It's
   possible for a new version of the OpenVoxDB-termini to use API commands
   unsupported by old OpenVoxDB versions, which would cause OpenVox failures until
   OpenVoxDB was upgraded, but this should be very rare.)

## Manually upgrading OpenVoxDB

### What to upgrade

When a new version of OpenVoxDB is released, you will need to upgrade:

1. OpenVoxDB itself
2. The [OpenVoxDB-termini][connect_server] on every OpenVox Server (or
   [every node][connect_apply], if using a standalone deployment).

You should **upgrade OpenVoxDB first.** Because OpenVoxDB will be down for a few
minutes during the upgrade and OpenVox Server will not be able to serve catalogs
until it comes back, you should schedule upgrades during a maintenance window
during which no new nodes will be brought online.

If you upgrade OpenVoxDB without upgrading the OpenVoxDB-termini, your OpenVox
deployment should continue to function identically, with no loss of
functionality. However, you may not be able to take advantage of new OpenVoxDB
features until you upgrade the OpenVoxDB-termini.

### Upgrading OpenVoxDB

**On your OpenVoxDB server:** stop the OpenVoxDB service, upgrade the OpenVoxDB
package, then restart the OpenVoxDB service.

    $ sudo puppet resource service puppetdb ensure=stopped
    $ sudo puppet resource package openvoxdb ensure=latest
    $ sudo puppet resource service puppetdb ensure=running

#### On platforms without packages

If you installed OpenVoxDB by running `rake install`, you should obtain a fresh
copy of the source, stop the service, and run `rake install` again. Note that
this workflow is not well tested; if you run into problems, please report them
on the [OpenVoxDB issue tracker][tracker].

If you are running OpenVoxDB from source, you should stop the service, replace
the source, and
[start the service as described in the advanced installation guide][start_source].

### Upgrading the terminus plugins

**On your OpenVox Servers:** upgrade the OpenVoxDB-termini package, then
restart the OpenVox Server's web server:

    $ sudo puppet resource package openvoxdb-termini ensure=latest

The command to restart the OpenVox Server will vary, depending on which web
server you are using.

#### On platforms without packages

Obtain a fresh copy of the OpenVoxDB source, and follow
[the instructions for installing the termini][plugin_source].

The command to restart the OpenVox Server will vary, depending on which web
server you are using.

### Upgrading across multiple major versions

As stated by the [versioning policy][versioning], you cannot "skip"
major versions of OpenVoxDB when upgrading.  For example, if you need
to upgrade from PuppetDB 7 to OpenVoxDB 9, you must run some version of
OpenVoxDB 8 at least long enough for it to upgrade your existing data.

The upgrade subcommand can help with this.  When specified, OpenVoxDB
will quit as soon as it has finished all of the necessary work:

    $ puppetdb upgrade -c /path/to/config.ini

## Truncate your reports table

Some OpenVoxDB versions contain long database migrations that can be avoided by
deleting all the reports and resource events from your database. Release notes
will call out upgrades where this applies.

**WARNING:** This is a permanent destructive action and should be done with care.

Truncating the reports table will delete all your reports and all their
associated resource events.  This is primarily helpful for users with large
databases when upgrades involve expensive database migrations, such as
upgrading PostgreSQL versions.

### Monolithic installs

For standard installs, where OpenVoxDB and Postgres run on the same machine, and
you use OpenVox's default user and database names you can delete your reports
and resource events by running `/opt/puppetlabs/bin/puppetdb delete-reports` as
root.

### Non-default user/database names, PostgreSQL port, or `psql` location

If you are not running a standard install you can follow the general outline
below.  Be sure to run `puppetdb delete-reports --help` to see if you need to
customize any of the user or database names for your own install.

### Postgres on another server

The `delete-reports` subcommand lives on the server that runs OpenVoxDB at
`/opt/puppetlabs/server/apps/puppetdb/cli/apps/delete-reports`. In order for
this command to work, you'll need to manually transfer it to the server that is
running OpenVoxDB's PostgreSQL and execute it there. It will fail to stop the
OpenVoxDB service, because one doesn't exist there, but it will continue and
delete the reports anyways.
