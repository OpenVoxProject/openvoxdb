---
title: "Configuring an OpenVox/OpenVoxDB connection"
layout: default
canonical: "/puppetdb/latest/puppetdb_connection.html"
---

# Configuring an OpenVox/OpenVoxDB connection

[puppetdb_root]: ./overview.markdown
[connect_to_puppetdb]: ./connect_puppet_server.markdown
[confdir]: https://docs.openvoxproject.org/openvox/latest/dirs_confdir.html
[puppetdb_conf]: ./connect_puppet_server.markdown#edit-puppetdb\.conf

The `puppetdb.conf` file contains the hostname and port of the [OpenVoxDB][puppetdb_root] server. It is only used if you are using OpenVoxDB and have [connected your OpenVox Server to it][connect_to_puppetdb].

The OpenVox Server makes HTTPS connections to OpenVoxDB to store catalogs, facts, and new reports. It also uses OpenVoxDB to answer queries, such as those necessary to support exported resources. If the OpenVoxDB instance is down, depending on the configuration of the OpenVox Server, it could cause the OpenVox run to fail. This document discusses configuration options for the `puppetdb.conf` file, including settings to make the OpenVoxDB terminus more tolerant of failures.

## Location

The `puppetdb.conf` file is always located at `$confdir/puppetdb.conf`. Its location is **not** configurable.

The location of the `confdir` varies, depending on the OS, OpenVox distribution, and user account. [See the configuration directory documentation for details.][confdir]

## Example

    [main]
    server_urls = https://puppetdb.example.com:8081

## Format

The `puppetdb.conf` file uses the same INI-like format as `puppet.conf`, but only uses a `[main]` section.

## `[main]` Settings

The `[main]` section defines all of the OpenVoxDB terminus settings.

### `server_urls`

This setting specifies how the OpenVox Server should connect to OpenVoxDB. The configuration should look something like:

    server_urls = https://puppetdb.example.com:8081

OpenVox **requires** the use of OpenVoxDB's secure HTTPS port. You cannot use the unencrypted HTTP port.

You can use a comma-separated list of URLs if there are multiple OpenVoxDB instances available. A `server_urls` config that supports two OpenVoxDBs would look like:

    server_urls = https://puppetdb1.example.com:8081,https://puppetdb2.example.com:8081

The default value is `https://puppetdb:8081`.

The OpenVoxDB terminus will always attempt to connect to the first OpenVoxDB instance specified (listed above as `puppetdb1`). If a server-side exception occurs, or the request takes too long (see [`server_url_timeout`](#serverurltimeout)), the OpenVoxDB terminus will attempt the same operation on the next instance in the list.

### `submit_only_server_urls`

This setting allows you specify OpenVoxDB instances to which commands should be sent, but which shouldn't ever be queried for data needed during an OpenVox run. It uses the same format as `server_urls`. For example:

    submit_only_server_urls = https://puppetdb-submit-only.example.com:8081

If a server is listed in `submit_only_server_urls`, it shouldn't be listed in `server_urls`; the two lists should be disjoint.

Successful command submission to the OpenVoxDB instances in this list **do** count towards the `min_successful_submissions` setting, so consider incrementing accordingly if you use this setting.

### `server_url_timeout`

The `server_url_timeout` setting sets the maximum amount of time (in seconds) the OpenVoxDB-termini will wait for OpenVoxDB to respond to HTTP requests. If the user has specified multiple OpenVoxDB URLs and a timeout has occurred, it will attempt the same request on the next server in the list.

The default value is 30 seconds.

### `soft_write_failure`

This setting can let the OpenVox Server stay partially available during an OpenVoxDB outage. If set to `true`, OpenVox will keep compiling and serving catalogs even if OpenVoxDB isn't accessible for command submission. (However, any catalogs that need to **query** exported resources from OpenVoxDB will still fail.)

The default value is false.

### `include_catalog_edges`

This setting tells the OpenVoxDB terminus whether or not it should include
resource edges in catalogs sent to OpenVoxDB. For users who do not need catalog
edge information, this can improve the performance of OpenVoxDB command
processing. If you do not want to store information about catalog edges, set
this value to `false`.

The default value is true.

### `include_unchanged_resources` (PE only)

> **Warning:** This setting is intended for use only in Puppet Enterprise (PE).
> Using this setting without a PE PuppetDB package will only result in degraded
> OpenVoxDB performance, and OpenVoxDB will not store the unchanged resources data.

This setting tells the OpenVoxDB terminus whether or not it should include
unchanged resources data in a report when sending it to OpenVoxDB. If you do not
want to store information about unchanged resources in a report, set this value
to `false`.

The default value in PE is `true`.

#### `sticky_read_failover`

When using multiple `server_urls`, this flag can be set to `true` to cause queries to be made to the last OpenVoxDB instance that was successfully contacted.

The default value is false.

#### `command_broadcast`

When set to `true` in installations using multiple `server_urls`, commands are sent to all configured OpenVoxDB instances. 

The default setting is `false`.

#### `min_successful_submissions`

When writing data (submitting commands) to OpenVoxDB, this is the minimum number of machines to which the command must be successfully sent to consider the write successful. If the configured number of machines cannot be reached, OpenVox runs will fail.

The default value is one, which should be appropriate for most single- or dual-OpenVoxDB deployments.

This setting must be used in conjunction with `command_broadcast`.

### `fact_names_blocklist`

This setting prevents selected facts from being sent to OpenVoxDB. It accepts a
comma-separated list of exact fact paths. Top-level fact names can be listed
directly, while keys in structured facts use dot notation:

    fact_names_blocklist = secret, networking.interfaces.eth0.mac

The example removes the complete `secret` fact and only the `mac` key below the
`eth0` interface. Other keys in the `networking` structured fact are preserved.
Array indexes are part of a structured path, so a path such as
`disks.0.serial` identifies the `serial` key in the first `disks` element.

The default value is an empty list, which sends all facts.

### `fact_names_blocklist_regex`

This setting accepts a comma-separated list of Ruby regular expressions. Each
expression is matched against the complete dot-separated path of every fact key.
For example, the following removes a `password` key at any nesting level,
including from hashes contained in arrays:

    fact_names_blocklist_regex = (^|\.)password$

Regular expressions are validated when `puppetdb.conf` is loaded. Because commas
separate entries in this setting, an expression cannot contain a comma.

The default value is an empty list, which does not block any facts by pattern.
