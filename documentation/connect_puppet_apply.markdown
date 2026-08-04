---
title: "Connecting standalone OpenVox nodes to OpenVoxDB"
layout: default
canonical: "/puppetdb/latest/connect_puppet_apply.markdown"
---

# Connecting standalone OpenVox nodes to OpenVoxDB

[exported]: https://docs.openvoxproject.org/openvox/latest/lang_exported.html
[package]: https://docs.openvoxproject.org/openvox/latest/type.html#package
[file]: https://docs.openvoxproject.org/openvox/latest/type.html#file
[yumrepo]: https://docs.openvoxproject.org/openvox/latest/type.html#yumrepo
[apt]: http://forge.puppetlabs.com/puppetlabs/apt
[puppetdb_download]: https://github.com/OpenVoxProject/openvoxdb/releases
[puppetdb_conf]: https://docs.openvoxproject.org/openvox/latest/config_file_puppetdb.html
[routes_yaml]: https://docs.openvoxproject.org/openvox/latest/config_file_routes.html
[exported]: https://docs.openvoxproject.org/openvox/latest/lang_exported.html
[jetty]: ./configure.markdown#jetty-http-settings
[ssl_script]: ./maintain_and_tune.markdown#redo-ssl-setup-after-changing-certificates
[settings_namespace]: https://docs.openvoxproject.org/openvox/latest/lang_facts_and_builtin_vars.html#puppet-master-variables
[package_repos]: https://docs.openvoxproject.org/openvox/latest/install_linux.html

OpenVoxDB can be used with standalone OpenVox deployments where each node runs `puppet apply`. When connected to OpenVoxDB, `puppet apply` does the following:

* Send the node's catalog to OpenVoxDB
* Query OpenVoxDB when compiling catalogs that collect [exported resources][exported]
* Store facts in OpenVoxDB
* Send reports to OpenVoxDB (optional)

You must take the following steps to configure your standalone nodes to connect to OpenVoxDB. Note that because you must change OpenVox's configuration on every managed node, **we strongly recommend that you do so with OpenVox itself.**

## Step 1: Configure SSL

OpenVoxDB requires client authentication (CA) for its SSL connections, and the OpenVoxDB-termini require SSL to talk to OpenVoxDB. You must configure OpenVox and OpenVoxDB to work around this double-bind by using one of the following options:

### Option A (Recommended): Issue certificates to all OpenVox nodes

This option is recommended. The OpenVox team optimizes for this option when they're developing, and it is better tested.

When talking to OpenVoxDB, `puppet apply` can use the certificates issued by an OpenVox Server's certificate authority. You can issue certificates to every node by setting up an OpenVox Server with dummy manifests, running `puppet agent --test` one time on every node, signing every certificate request on the OpenVox Server, and running `puppet agent --test` again on every node.

Do the same on your OpenVoxDB node, then [re-run the SSL setup script][ssl_script] (which usually runs automatically during installation). OpenVoxDB will now trust connections from your OpenVox nodes.

You will have to sign a certificate for every new node you add to your site.

### Option B: Set up an SSL proxy for OpenVoxDB

Before you head down this path, please consider if signing certificates with OpenVox Server will work for you.
This option requires more work on your part to set up, and does not allow you to provide
SSL to OpenVoxDB without a signed certificate, but it will allow you to provide SSL connections to OpenVoxDB using an existing CA.
If you have an existing CA you would like to use, you can [set up OpenVox Server as an intermediate CA](https://docs.openvoxproject.org/openvox-server/latest/intermediate_ca.html#set-up-puppet-as-an-intermediate-ca-with-an-external-root) and then follow the instructions in Option A.

1. Edit [the `[jetty]` section of the OpenVoxDB config files][jetty] to remove all SSL-related settings.
2. Install a general-purpose web server (like Apache or NGINX) on the OpenVoxDB server.
3. Configure the web server to listen on port 8081 with SSL enabled and proxy all traffic to `localhost:8080` (or whatever unencrypted hostname and port were set in [jetty.ini][jetty]).
   The cacert used will need to be signed.

If you use this option, you'll need to add these settings in addition
to the general ones specified below. The cacert supplied to `localcacert`
should be the one that signs the SSL proxy cert for OpenVoxDB.

Add this to your `puppetdb.conf`
```
[main]
verify_client_certificate = false
```

Add this to your `puppet.conf`
```
[main]
localcacert = /etc/path/to/cacert/ca.crt
certificate_revocation = false
```


## Step 2: Install terminus plugins on every OpenVox node

Currently, OpenVox needs extra Ruby plugins in order to use OpenVoxDB. Unlike custom facts or functions, these cannot be loaded from a module and must be installed in OpenVox's main source directory.

* First, ensure that the appropriate [OpenVox platform repository][package_repos]
  repository is enabled. You can use a [package][] resource to do this or the
  `apt::source` (from the [puppetlabs-apt][apt] module) and [`yumrepo`][yumrepo] types.
* Next, use OpenVox to ensure that the `openvoxdb-termini` package is installed:

~~~ ruby
    package {'openvoxdb-termini':
      ensure => installed,
    }
~~~

### On platforms without packages

If your OpenVox Server isn't running OpenVox from a supported package, you will need to install the plugins using [file][] resources.

* [Download the OpenVoxDB source code][puppetdb_download]; unzip it, locate the `puppet/lib/puppet` directory, and put it in the `files` directory of the Puppet module you are using to enable OpenVoxDB integration.
* Identify the install location of OpenVox on your nodes.
* Create a [file][] resource in your manifest(s) for each of the plugin files, to move them into place on each node.

~~~ ruby
    # <modulepath>/puppetdb/manifests/terminus.pp
    class puppetdb::terminus {
      $puppetdir = "$rubysitedir/puppet"

      file { $puppetdir:
        ensure => directory,
        recurse => remote, # Copy these files without deleting the existing files
        source => "puppet:///modules/puppetdb/puppet",
        owner => root,
        group => root,
        mode => 0644,
      }
    }
~~~

## Step 3: Manage configuration files on every OpenVox node

All of the config files you need to manage will be in OpenVox's config directory (`confdir`). When managing these files with `puppet apply`, you can use the [`$settings::confdir`][settings_namespace] variable to automatically discover the location of this directory.

### Manage puppetdb.conf

You can specify the contents of [puppetdb.conf][puppetdb_conf] directly in your manifests. It should contain the OpenVoxDB server's hostname and port:

    [main]
    server = puppetdb.example.com
    port = 8081

OpenVoxDB's port for secure traffic defaults to 8081. OpenVox **requires** use of OpenVoxDB's
secure HTTPS port. You cannot use the unencrypted, plain HTTP port.
If you are providing SSL via a proxy like nginx (Option B in Step 1) refer there for
a few extra configuration options that you will need.

For availability reasons, there is a setting named `soft_write_failure` that will cause the OpenVoxDB-termini to fail in a soft manner if OpenVoxDB is not accessible for command submission. This means that users who are either not using storeconfigs or only exporting resources will still have their catalogs compile during an OpenVoxDB outage.

If no puppetdb.conf file exists, the following default values will be used:

    server = puppetdb
    port = 8081
    soft_write_failure = false

### Manage puppet.conf

You will need to create a template for puppet.conf based on your existing configuration. Then, modify the template by adding the following settings to the `[main]` block:

    [main]
      storeconfigs = true
      storeconfigs_backend = puppetdb
      # Optional settings to submit reports to OpenVoxDB:
      report = true
      reports = puppetdb

> **Note:** The `thin_storeconfigs` and `async_storeconfigs` settings should be absent or set to `false`.

### Manage routes.yaml

Typically, you can specify the contents of [routes.yaml][routes_yaml] directly in your manifests; if you are already using routes.yaml for some other purpose, you will need to manage it with a template based on your existing configuration. The path to this OpenVox configuration file can be found with the command `puppet config print route_file`.

Ensure that the following keys are present:

    ---
    apply:
      catalog:
        terminus: compiler
        cache: puppetdb
      resource:
        terminus: ral
        cache: puppetdb
      facts:
        terminus: facter
        cache: puppetdb_apply

This is necessary to keep OpenVox from using stale facts and to keep the `puppet resource` subcommand from malfunctioning. Note that the `puppetdb_apply` terminus is specifically for `puppet apply` nodes, and differs from the configuration of OpenVox Servers using OpenVoxDB.
