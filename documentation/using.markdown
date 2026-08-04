---
title: "Using OpenVoxDB"
layout: default
canonical: "/puppetdb/latest/using.html"
---

# Using OpenVoxDB

[exported]: https://docs.openvoxproject.org/openvox/latest/lang_exported.html


Currently, OpenVoxDB's primary use is enabling advanced OpenVox features. As use becomes more widespread, we expect additional applications to be built on OpenVoxDB.

If you wish to build applications on OpenVoxDB, see the navigation sidebar for links to the API specifications.

## Checking node status

The OpenVoxDB plugins [installed on your OpenVox Server(s)](./connect_puppet_server.markdown) include a `status` action for the `node` face. On your OpenVox Server, run:

    $ sudo puppet node status <NODE>

where `<NODE>` is the name of the node you wish to investigate. This will tell you whether the node is active, when its last catalog was submitted, and when its last facts were submitted.

## Using exported resources

OpenVoxDB lets you use exported resources, which allows your nodes to publish information for use by other nodes.

[Learn more about using exported resources here.][exported]

