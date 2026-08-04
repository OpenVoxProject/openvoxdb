---
title: "Installing from packages"
layout: default
---
# Installing from packages

[connect_server]: ./connect_puppet_server.markdown
[connect_apply]: ./connect_puppet_apply.markdown
[ssl_script]: ./maintain_and_tune.markdown#redo-ssl-setup-after-changing-certificates
[configure_postgres]: ./configure.markdown#using-postgresql
[configure_heap]: ./configure.markdown#configuring-the-java-heap-size
[configure_jetty]: ./configure.markdown#jetty-http-settings
[requirements]: ./overview.markdown#standard-install-rhel-centos-debian-and-ubuntu
[install_module]: ./install_via_module.markdown
[module]: https://forge.puppet.com/puppet/openvoxdb
[postgres_ssl]: ./postgres_ssl.markdown
[package_repos]: https://docs.openvoxproject.org/openvox/latest/install_linux.html
[known-issues]: ./known_issues.markdown

This page describes how to manually install and configure OpenVoxDB
from the official packages. Users are encouraged to install OpenVoxDB
via the [OpenVoxDB module][module] instead of installing the packages
directly. Using the module for setting up OpenVoxDB is much easier and
less error prone. See [Installing OpenVoxDB via OpenVox
module][install_module] for more info.

Additionally, these instructions may be useful for understanding OpenVoxDB's
various moving parts, and can be helpful if you need to create your own OpenVoxDB
module.

> **Notes:**
>
> * After following these instructions, you must
>   [connect your OpenVox Server(s) to OpenVoxDB][connect_server]. (If you use a
>   standalone OpenVox deployment, you will need to
>   [connect every node to OpenVoxDB][connect_apply].)
> * These instructions are for
>   [platforms with official OpenVoxDB packages][requirements]. To install on
>   other systems, follow
>   [our instructions for installing from source](./install_from_source.markdown).
> * If this is a production deployment,
>   [review the scaling recommendations](./scaling_recommendations.markdown) before
>   installing. You should ensure that your OpenVoxDB server will be able to
>   comfortably handle your site's load.

## Platform specific install notes

**Ubuntu 18.04**
* Enable the [universe repository](https://help.ubuntu.com/community/Repositories/Ubuntu), which contains packages necessary for OpenVoxDB
* Ensure Java 8 is installed

**RHEL 8**
* RedHat's openjdk 11 package's dependency on tzdata-java was broken, see
  OpenVoxDB's [known issues][known-issues] for more more information and a
  workaround.

## Step 1: Install and configure OpenVox

If OpenVox isn't fully installed and configured on your OpenVoxDB server,
[install it][installpuppet] and request/sign/retrieve a certificate for the
node.

[installpuppet]: https://docs.openvoxproject.org/openvox/latest/install_pre.html

Your OpenVoxDB server should be running OpenVox agent and have a signed
certificate from your OpenVox Server. If you run `puppet agent --test`, it
should successfully complete a run, ending with `Notice: Applied catalog in X.XX
seconds`.

> Note: If OpenVox doesn't have a valid certificate when OpenVoxDB is installed,
> you will have to
> [run the SSL config script and edit the config file][ssl_script], or
> [manually configure OpenVoxDB's SSL credentials][postgres_ssl] before
> the OpenVox Server will be able to connect to OpenVoxDB.

## Step 2: Enable the OpenVox Platform package repository

If you didn't already use it to install OpenVox, you will need to
[enable the OpenVox Platform package repository][package_repos].

## Step 3: Install OpenVoxDB

Use OpenVox to install OpenVoxDB:

    $ sudo puppet resource package openvoxdb ensure=latest

## Step 4: Configure database

- [Set up a PostgreSQL server and configure OpenVoxDB to use it][configure_postgres].
  If your PostgreSQL node is on a separate server than OpenVoxDB, you should
  [configure an SSL connection][postgres_ssl], otherwise your database
  communication will happen in plaintext over the network. This can be made
  much simpler by installing using the [OpenVoxDB module][module].

## Step 5: Start the OpenVoxDB service

Use OpenVox to start the OpenVoxDB service and enable it on startup.

    $ sudo puppet resource service puppetdb ensure=running enable=true

You must also configure your OpenVoxDB server's firewall to accept incoming
connections on port 8081.

> OpenVoxDB is now fully functional and ready to receive facts, catalogs, and
> reports from any number of OpenVox Servers.

## Finish: Connect OpenVox to OpenVoxDB

[You should now configure your OpenVox Server(s) to connect to OpenVoxDB][connect_server].

If you use a standalone OpenVox site,
[you should configure every node to connect to OpenVoxDB][connect_apply].

## Troubleshooting installation problems

* Check the log file (`/var/log/puppetlabs.puppetdb/puppetdb.log`), and see
  whether OpenVoxDB knows what the problem is.
* If OpenVoxDB is running but the OpenVox Server can't reach it, check
  [OpenVoxDB's `[jetty]` configuration][configure_jetty] to see which port(s) it
  is listening on, then attempt to reach it by Telnet (`telnet <HOST> <PORT>`)
  from the OpenVox Server. If you can't connect, the firewall may be
  blocking connections. If you can, OpenVox may be attempting to use the wrong
  port, or OpenVoxDB's keystore may be misconfigured (see below).
* Check whether any other service is using OpenVoxDB's port and interfering with
  traffic.
* Check [OpenVoxDB's `[jetty]` configuration][configure_jetty] and the
  `/etc/puppetlabs/puppetdb/ssl` directory, and make sure it has the necesary
  SSL files created. If it didn't create these during installation, you will
  need to [run the SSL config script and edit the config file][ssl_script]
  before an OpenVox Server can contact OpenVoxDB.
