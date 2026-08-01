---
title: "Connecting OpenVox Servers to OpenVoxDB"
layout: default
canonical: "/puppetdb/latest/connect_puppet_server.html"
---

# Connecting OpenVox Servers to OpenVoxDB

[puppetdb_download]: https://github.com/OpenVoxProject/openvoxdb/releases
[puppetdb_conf]: ./puppetdb_connection.markdown
[routes_yaml]: https://docs.openvoxproject.org/openvox/latest/config_file_routes.html
[exported]: https://docs.openvoxproject.org/openvox/latest/lang_exported.html
[install_via_module]: ./install_via_module.markdown
[report_processors]: https://docs.openvoxproject.org/openvox/latest/reporting_about.html
[event]: ./api/query/v4/events.markdown
[report]: ./api/query/v4/reports.markdown
[store_report]: ./api/command/v1/commands.markdown#store-report-version-7
[report_format]: ./api/wire_format/report_format_v5.markdown
[puppetdb_server_urls]: ./puppetdb_connection.markdown#serverurls
[package_repos]: https://docs.openvoxproject.org/openvox/latest/install_linux.html


After OpenVoxDB is installed and running, configure your OpenVox Server to use it. When properly connected to OpenVoxDB, the OpenVox Server does the following:

* Send every node's catalog, facts, and reports to OpenVoxDB
* Query OpenVoxDB when compiling node catalogs that collect [exported resources][exported]

> Note: if you've [installed OpenVoxDB using the OpenVoxDB module][install_via_module], then the `openvoxdb::master::config` class is taking care of all of this for you.

 **Working on your OpenVox Server(s),** follow all of the instructions below:

## Step 1: Install plug-ins

Currently, OpenVox Servers need additional Ruby plug-ins in order to use OpenVoxDB. Unlike custom facts or functions, these cannot be loaded from a module and must be installed in OpenVox's main source directory.

### On platforms with packages

[Enable the OpenVox Platform package repository][package_repos] and then install the `openvoxdb-termini` package:

    $ sudo puppet resource package openvoxdb-termini ensure=latest

### On platforms without packages

If your OpenVox Server isn't running OpenVox from a supported package, you will need to install the plugins manually:

* [Download the OpenVoxDB source code][puppetdb_download], unzip it, and navigate into the resulting directory in your terminal.

* Run `sudo cp -R puppet/lib/puppet/ /opt/puppetlabs/puppet/lib/ruby/vendor_ruby/puppet`

## Step 2: Edit configuration files

### Locate OpenVox's config directory

Find your OpenVox Server's config directory by running `sudo puppet config print confdir`. It will usually be at either `/etc/puppet/` or `/etc/puppetlabs/puppet/`.

You will edit (or create) three files in this directory:

### 1. Edit puppetdb.conf

The [puppetdb.conf][puppetdb_conf] file will probably not yet exist. Create it, and add the OpenVoxDB server's URL that includes the hostname and port:

    [main]
    server_urls = https://puppetdb.example.com:8081

OpenVoxDB's port for secure traffic defaults to 8081 with the context root of '/'. If you have not changed the defaults, the above configuration (with the correct hostname) is sufficient. For more information on configuring `server_urls`, including support for multiple OpenVoxDB backends, see [configuring the OpenVoxDB server_urls][puppetdb_server_urls].

### 2. Edit puppet.conf

To enable saving facts and catalogs in OpenVoxDB, edit the `[master]` block of puppet.conf to reflect the following settings:

    [master]
      storeconfigs = true
      storeconfigs_backend = puppetdb

> Note: The `thin_storeconfigs` and `async_storeconfigs` settings should be absent or set to `false`. If you previously used the OpenVox queue daemon (puppetqd), you should now disable it.

#### Enabling report storage

OpenVoxDB includes support for storing OpenVox reports. This feature can be
enabled by simply adding the `puppetdb` report processor in your `puppet.conf`
file. If you don't already have a `reports` setting in your `puppet.conf`
file, you'll probably want to add a line like this:

    reports = store,puppetdb

This will retain OpenVox's default behavior of storing the reports to disk as YAML,
while also sending the reports to OpenVoxDB.

You can configure how long OpenVoxDB stores these reports, and you can do some
very basic querying. For more information, see:

* [The `event` query endpoint][event]
* [The `report` query endpoint][report]
* [The `store report` command][store_report]
* [The report wire format][report_format]

More information about OpenVox report processors in general can be found
[here][report_processors].

### 3. Edit routes.yaml

The [routes.yaml][routes_yaml] file will probably not yet exist. Find the path to this OpenVox configuration file by running `puppet config print route_file`.

Create the file, if necessary, and add the following:

    ---
    master:
      facts:
        terminus: puppetdb
        cache: yaml

### Ensure proper ownership of the config files

The files created above need to be owned by the `puppet` user. Ensure that
this ownership is applied by running the following command:

    $ sudo chown -R puppet:puppet `sudo puppet config print confdir`

## Step 3: Set security policy

OpenVoxDB listens on TCP port 8081 (HTTPS). Ensure that this port is open between
the OpenVox Server and OpenVoxDB services. If the services run on the same server, additional configuration might not be needed. If the services are on separate
servers, ensure that the server and network firewalls allow for traffic flow.

OpenVoxDB works without modification with SELinux in enforcing mode.

## Step 4: Restart OpenVox Server

Use your system's service tools to restart the OpenVox Server service. For open source OpenVox users, the command to do this will vary, depending on the frontend web server being used.

> Your OpenVox Server is now using OpenVoxDB to store and retrieve catalogs, facts, and exported resources. You can test your setup by triggering an OpenVox agent run on an arbitrary node, then logging into your OpenVoxDB server and viewing the `/var/log/puppetlabs/puppetdb/puppetdb.log` file, which will include calls to the "replace facts", "replace catalog", and "store report" commands:
>
>     2012-05-17 13:08:41,664 INFO  [command-proc-67] [puppetdb.command] [85beb105-5f4a-4257-a5ed-cdf0d07aa1a5] [replace facts] screech.example.com
>     2012-05-17 13:08:45,993 INFO  [command-proc-67] [puppetdb.command] [3a910863-6b33-4717-95d2-39edf92c8610] [replace catalog] screech.example.com
