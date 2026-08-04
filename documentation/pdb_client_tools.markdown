---
title: "OpenVoxDB CLI"
layout: default
---

# OpenVoxDB CLI

[installpuppet]: https://docs.openvoxproject.org/openvox/latest/install_pre.html
[export]: ./anonymization.markdown

## Installation

### Step 1: Install and configure OpenVox

If OpenVox isn't fully installed and configured [install it][installpuppet] and
request, sign, and retrieve a certificate for the node.

Your node should be running the OpenVox agent and have a signed certificate from
your OpenVox Server. If you run `puppet agent --test`, it should
successfully complete a run, ending with `Notice: Applied catalog in X.XX
seconds`.

> **Note:** It is helpful to add the OpenVox bin, `/opt/puppetlabs/bin`, and man,
`/opt/puppetlabs//client-tools/share/man`, directories to your `PATH` and
`MANPATH` directories respectively. For example,
>
> ```bash
> $ export PATH=/opt/puppetlabs/bin:$PATH
> $ export MANPATH=/opt/puppetlabs/client-tools/share/man:$MANPATH
> ```
>
> The rest of this documentation assumes that these two directories have been
added to their proper path configurations.

### Step 2: Install and configure the OpenVoxDB CLI

Install the OpenVoxDB CLI from Rubygems:

    $ gem install --bindir /opt/puppetlabs/bin puppetdb_cli

If you are installing the OpenVoxDB CLI on a machine that does not have OpenVox
installed, such as your own workstation, you can install the executables to Ruby's
standard bindir by omitting the `--bindir` option.

    $ gem install puppetdb_cli

If the node you installed the CLI on is not the same node as your OpenVoxDB
server, you will need to add the CLI node's certname to the OpenVoxDB
certificate-allowlist and specify the paths to the CLI node's cacert, cert, and
private key when using the CLI either with flags or a configuration file.

To configure the OpenVoxDB CLI to talk to your OpenVoxDB with flags, add a
configuration file at `$HOME/.puppetlabs/client-tools/puppetdb.conf` (or
`%USERPROFILE%\.puppetlabs\client-tools\puppetdb.conf` for Windows). For more
details see the installed man page:

    $ man puppetdb_conf

The OpenVoxDB CLI configuration files (the user-specified or global files) can
take the following settings:

- `server_urls` Either a JSON String (for a single url) or Array (for multiple
  urls) of your OpenVoxDB servers to query or manage via the CLI commands.

  Default value: https://127.0.0.1:8080

- `cacert` The path for the CA cert.

  *nix sytems - /etc/puppetlabs/puppet/ssl/certs/ca.pem

  Windows - C:\ProgramData\PuppetLabs\puppet\etc\ssl\certs\ca.pem

- `cert` An SSL certificate signed by your site's OpenVox CA.

- `key` The private key for that certificate.

#### Example configuration file

The OpenVoxDB CLI requires certificate authentication
for SSL connections to OpenVoxDB. To configure certificate authentication set
`cacert`, `cert` and `key`.

```json
{
  "puppetdb": {
    "server_urls": "https://<PUPPETDB_HOST>:8081",
    "cacert": "/etc/puppetlabs/puppet/ssl/certs/ca.pem",
    "cert": "/etc/puppetlabs/puppet/ssl/certs/<WORKSTATION_HOST>.pem",
    "key": "/etc/puppetlabs/puppet/ssl/private_keys/<WORKSTATION_HOST>.pem"
  }
}
```

On Windows, escape slashes in paths.

```json
{
  "puppetdb": {
    "server_urls": "https://<PUPPETDB_HOST>:8081",
    "cacert": "C:\\ProgramData\\PuppetLabs\\puppet\\ssl\\certs\\ca.pem",
    "cert": "C:\\ProgramData\\PuppetLabs\\puppet\\ssl\\certs\\<WORKSTATION_HOST>.pem",
    "key": "C:\\ProgramData\\PuppetLabs\\puppet\\ssl\\private_keys\\<WORKSTATION_HOST>.pem"
  }
}
```

### Step 3: Enjoy!

Here are some examples of using the CLI.

#### Using `puppet query`

Query OpenVoxDB using PQL:

    $ puppet query "nodes [ certname ]{ limit 1 }"

Or query OpenVoxDB using the AST syntax:

    $ puppet query "['from', 'nodes', ['extract', 'certname'], ['limit', 1]]"

For more information on the `query` command:

    $ man puppet-query

#### Using `puppet db`

Handle your OpenVoxDB exports:

    $ puppet db export pdb-archive.tgz --anonymization full

Or handle your OpenVoxDB imports:

    $ puppet db import pdb-archive.tgz

For more information on the `db` command:

    $ man puppet-db

For more information about OpenVoxDB exports, imports, and anonymization
[see][export].
