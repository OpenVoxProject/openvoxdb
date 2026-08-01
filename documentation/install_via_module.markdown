---
title: "Installing OpenVoxDB via Puppet module"
layout: default
---

[package_repos]: https://docs.openvoxproject.org/openvox/latest/install_linux.html

# Installing OpenVoxDB via Puppet module

[module]: https://forge.puppet.com/puppet/openvoxdb
[config_with_module]: ./configure.markdown#playing-nice-with-the-openvoxdb-module

You can install and configure all of OpenVoxDB's components and prerequisites
(including OpenVoxDB itself, PostgreSQL, firewall rules on RedHat-like systems,
and the OpenVoxDB-termini for your OpenVox Server) using
[the OpenVoxDB module][module] from the Puppet Forge.

* If you are **already familiar with OpenVox** and have a working OpenVox
  deployment, this is the easiest method for installing OpenVoxDB. In this guide,
  we expect that you already know how to assign Puppet classes to nodes.
* If you are **just getting started with OpenVox,** you may find it easier to
  follow our guide to
  [installing OpenVoxDB from packages](./install_from_packages.markdown).

## Step 1: Enable the OpenVox Platform package repository

If you haven't done so already, you will need to do **one** of the following:

* [Enable the OpenVox Platform package repository][package_repos] on
  your OpenVoxDB server and OpenVox Server.
* If you don't use the OpenVox Platform repository, make the OpenVoxDB and
  OpenVoxDB-terminus packages available via your alternate installation strategy.
  For the module install to succeed a command like `yum install openvoxdb`, or the
  equivalent one that uses your system's package manager, needs to be able to
  succeed.

## Step 2: Assign classes to nodes

Using the normal methods for your site, assign the OpenVoxDB module's classes to
your servers. You have three main options for deploying OpenVoxDB:

* If you are installing OpenVoxDB on the same server as your OpenVox Server,
  assign the `openvoxdb` and `openvoxdb::master::config` classes to it.
* If you want to run OpenVoxDB on its own server with a local PostgreSQL
  instance, assign the `openvoxdb` class to it, and assign the
  `openvoxdb::master::config` class to your OpenVox Server. Make sure to set the
  class parameters as necessary.
* If you want OpenVoxDB and PostgreSQL to each run on their own servers, assign
  the `openvoxdb::server` class and the `openvoxdb::database::postgresql` classes
  to different servers, and the `openvoxdb::master::config` class to your OpenVox
  Server. Make sure to set the class parameters as necessary. You should also
  then enable an SSL connection between your PostgreSQL and OpenVoxDB's servers,
  see [the module documentation for how to configure SSL](https://forge.puppet.com/modules/puppet/openvoxdb/readme#enable-ssl-connections).
  This configuration will use the OpenVox Agent certificates on both machines to
  authenticate and encrypt the database communication.

Note: By default, the module sets up the OpenVoxDB dashboard to be accessible
only via `localhost`. If you'd like to allow access to the OpenVoxDB dashboard
via an external network interface, set the `listen_address` parameter on either
of the `openvoxdb` or `openvoxdb::server` classes as follows:

    class { 'openvoxdb':
        listen_address => 'example.foo.com'
    }

These classes automatically configure most aspects of OpenVoxDB. If you need to
adjust additional settings (to change the `node_ttl`, for example), see
[the "Playing nice with the OpenVoxDB module" section][config_with_module] of the
"Configuring OpenVoxDB" page.

For full details on how to use the module, see the
[OpenVoxDB module documentation][module]
on Puppet Forge. The module also includes some sample manifests in the `tests`
directory that demonstrate its basic usage.
