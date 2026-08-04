---
title: "Known issues"
layout: default
canonical: "/puppetdb/latest/known_issues.html"
---
# Known issues

## Issues with specific operating systems

### RHEL 8 - Java 11

RedHat's openjdk 11 package dropped its dependency on tzdata-java. This is a
bug upstream, see
[Red Hat bug 2224427](https://bugzilla.redhat.com/show_bug.cgi?id=2224427) for
more information and to see if this issue has been resolved. If you run into a
problem starting the application due to a missing timezone database file,
install `tzdata-java` manually and retry.

## Bugs and feature requests

[tracker]: https://github.com/OpenVoxProject/openvoxdb/issues

OpenVoxDB's bugs and feature requests are managed in [OpenVoxDB's issue tracker][tracker]. Search this database if you're having problems and please report any new issues to us!

## OpenVoxDB fact-contents queries take longer than usual

The fact-contents query is written to reduce the reads required by Postgres
and improve performance on larger datasets, but it can perform poorly when
PostgreSQL JIT compilation is enabled. If you have JIT enabled, either by
setting it in PostgreSQL 11 or running the default settings on PostgreSQL
12+, you should disable it by setting `jit = off` in your `postgresql.conf`.

## Hash projection has character limit of 63

Dot notation is supported for projections, which allows queries like the one below.
```
inventory[facts.os.family] {
  certname = "host-1"
}
```
The dotted hash projection `facts.os.family` must be 63, or fewer, characters.

## Broader issues

### Autorequire relationships are opaque

Puppet resource types can "autorequire" other resources when certain conditions are met, but we don't correctly model these relationships in OpenVoxDB. (For example, if you manage two file resources where one is a parent directory of the other, OpenVox will automatically make the child dependent on the parent.) The problem is that these dependencies are not written to the catalog; the OpenVox agent creates these relationships on the fly when it reads the catalog. Getting these relationships into OpenVoxDB will require a significant change to OpenVox's core.
