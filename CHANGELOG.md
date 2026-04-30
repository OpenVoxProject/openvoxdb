# Changelog

All notable changes to this project will be documented in this file.

## [8.13.0](https://github.com/openvoxproject/openvoxdb/tree/8.13.0) (2026-04-30)

[Full Changelog](https://github.com/openvoxproject/openvoxdb/compare/8.12.1...8.13.0)

**Implemented enhancements:**

- \[Feature request\]: Migrate joda-time usage to java.time [\#217](https://github.com/OpenVoxProject/openvoxdb/issues/217)
- Depend on latest openvox-agent packages [\#237](https://github.com/OpenVoxProject/openvoxdb/pull/237) ([bastelfreak](https://github.com/bastelfreak))
- ezbake: Update 2.7.3-\>2.7.4 [\#236](https://github.com/OpenVoxProject/openvoxdb/pull/236) ([bastelfreak](https://github.com/bastelfreak))
- Migrate from clj-time/Joda-Time to java.time [\#226](https://github.com/OpenVoxProject/openvoxdb/pull/226) ([austb](https://github.com/austb))

**Merged pull requests:**

- fix: include tarballs in snapshot artifact upload by matching on version [\#254](https://github.com/OpenVoxProject/openvoxdb/pull/254) ([slauger](https://github.com/slauger))
- Add Ubuntu 26.04 to CI [\#235](https://github.com/OpenVoxProject/openvoxdb/pull/235) ([bastelfreak](https://github.com/bastelfreak))
- remove clj-parent as it was archived [\#234](https://github.com/OpenVoxProject/openvoxdb/pull/234) ([corporate-gadfly](https://github.com/corporate-gadfly))
- Refactor rewrite-service to eliminate redundant declaration of DummyProtocol [\#233](https://github.com/OpenVoxProject/openvoxdb/pull/233) ([corporate-gadfly](https://github.com/corporate-gadfly))
- Add Podman compatibility to vox:build task [\#223](https://github.com/OpenVoxProject/openvoxdb/pull/223) ([Sharpie](https://github.com/Sharpie))
- Update README to use OpenVoxDB instead of PuppetDB [\#191](https://github.com/OpenVoxProject/openvoxdb/pull/191) ([sebastianrakel](https://github.com/sebastianrakel))

## [8.12.1](https://github.com/openvoxproject/openvoxdb/tree/8.12.1) (2026-01-23)

[Full Changelog](https://github.com/openvoxproject/openvoxdb/compare/8.12.0...8.12.1)

**Fixed bugs:**

- \[Bug\]: /pdb/meta/v1/version returns empty string in version 8.12.0 [\#183](https://github.com/OpenVoxProject/openvoxdb/issues/183)

**Merged pull requests:**

- Lookup version from openvox group namespace [\#184](https://github.com/OpenVoxProject/openvoxdb/pull/184) ([austb](https://github.com/austb))

## [8.12.0](https://github.com/openvoxproject/openvoxdb/tree/8.12.0) (2026-01-23)

[Full Changelog](https://github.com/openvoxproject/openvoxdb/compare/8.11.0...8.12.0)

**Implemented enhancements:**

- Update Dockerfile for Java 17 and Ruby 3.2.9 [\#97](https://github.com/OpenVoxProject/openvoxdb/pull/97) ([bastelfreak](https://github.com/bastelfreak))
- Add postgresql 18 testing [\#83](https://github.com/OpenVoxProject/openvoxdb/pull/83) ([austb](https://github.com/austb))

**Fixed bugs:**

- \[Bug\]: openvoxdb 8.11.0-1+debian13 Trixie Package Java Dependency [\#76](https://github.com/OpenVoxProject/openvoxdb/issues/76)
- \[Bug\]: openvoxdb 8.11.0-1+debian13 Trixie Package Java Dependency [\#75](https://github.com/OpenVoxProject/openvoxdb/issues/75)
- \[Bug\]: Reports duplicate key value violates unique constraint error [\#16](https://github.com/OpenVoxProject/openvoxdb/issues/16)

**Merged pull requests:**

- Add FIPS release job and update project version machinery [\#182](https://github.com/OpenVoxProject/openvoxdb/pull/182) ([nmburgan](https://github.com/nmburgan))
- Changes for FIPS [\#181](https://github.com/OpenVoxProject/openvoxdb/pull/181) ([nmburgan](https://github.com/nmburgan))
- Downgrade ring-core to version 1.14.2 [\#178](https://github.com/OpenVoxProject/openvoxdb/pull/178) ([nmburgan](https://github.com/nmburgan))
- Fix reflection errors [\#176](https://github.com/OpenVoxProject/openvoxdb/pull/176) ([austb](https://github.com/austb))
- Update build comment if it already exists [\#169](https://github.com/OpenVoxProject/openvoxdb/pull/169) ([nmburgan](https://github.com/nmburgan))
- Refactor Vox rake tasks [\#168](https://github.com/OpenVoxProject/openvoxdb/pull/168) ([nmburgan](https://github.com/nmburgan))
- Fix int tests due to missing BC jars [\#166](https://github.com/OpenVoxProject/openvoxdb/pull/166) ([nmburgan](https://github.com/nmburgan))
- Add annotations for GitHub for test failures [\#165](https://github.com/OpenVoxProject/openvoxdb/pull/165) ([nmburgan](https://github.com/nmburgan))
- Add test summary for the end of lein test [\#164](https://github.com/OpenVoxProject/openvoxdb/pull/164) ([nmburgan](https://github.com/nmburgan))
- Fixes for test task [\#159](https://github.com/OpenVoxProject/openvoxdb/pull/159) ([nmburgan](https://github.com/nmburgan))
- CI: Properly detect if all jobs passed [\#158](https://github.com/OpenVoxProject/openvoxdb/pull/158) ([bastelfreak](https://github.com/bastelfreak))
- Add vox:test task to run CI tests locally  [\#152](https://github.com/OpenVoxProject/openvoxdb/pull/152) ([nmburgan](https://github.com/nmburgan))
- Move versions into managed deps and update openvox components [\#151](https://github.com/OpenVoxProject/openvoxdb/pull/151) ([nmburgan](https://github.com/nmburgan))
- Remove clj-parent [\#111](https://github.com/OpenVoxProject/openvoxdb/pull/111) ([nmburgan](https://github.com/nmburgan))
- Refactor lein profiles [\#110](https://github.com/OpenVoxProject/openvoxdb/pull/110) ([nmburgan](https://github.com/nmburgan))
- CI: comment download link to PR [\#109](https://github.com/OpenVoxProject/openvoxdb/pull/109) ([bastelfreak](https://github.com/bastelfreak))
- Fix transient failure in touch-parameters-test [\#106](https://github.com/OpenVoxProject/openvoxdb/pull/106) ([austb](https://github.com/austb))
- CI: Add support for checking PR branches [\#103](https://github.com/OpenVoxProject/openvoxdb/pull/103) ([bastelfreak](https://github.com/bastelfreak))
- Use shared release action [\#101](https://github.com/OpenVoxProject/openvoxdb/pull/101) ([bastelfreak](https://github.com/bastelfreak))
- ezbake: Migrate from puppetlabs to openvoxproject & drop EL7 / Ubuntu 18.04 builds [\#99](https://github.com/OpenVoxProject/openvoxdb/pull/99) ([bastelfreak](https://github.com/bastelfreak))
- Remove Java 11  [\#98](https://github.com/OpenVoxProject/openvoxdb/pull/98) ([bastelfreak](https://github.com/bastelfreak))
- Update dependencies and namespace [\#96](https://github.com/OpenVoxProject/openvoxdb/pull/96) ([nmburgan](https://github.com/nmburgan))
- Update report update condition to use report ID [\#80](https://github.com/OpenVoxProject/openvoxdb/pull/80) ([nmburgan](https://github.com/nmburgan))
- \(maint\) Drop beaker parameters from beaker\_acceptance.yml call [\#79](https://github.com/OpenVoxProject/openvoxdb/pull/79) ([jpartlow](https://github.com/jpartlow))

## [8.11.0](https://github.com/openvoxproject/openvoxdb/tree/8.11.0) (2025-08-24)

[Full Changelog](https://github.com/openvoxproject/openvoxdb/compare/8.10.0...8.11.0)

**Merged pull requests:**

- Fix for tag rake task [\#74](https://github.com/OpenVoxProject/openvoxdb/pull/74) ([nmburgan](https://github.com/nmburgan))
- Allow override of ezbake version and fix ezbake ref passthrough [\#73](https://github.com/OpenVoxProject/openvoxdb/pull/73) ([nmburgan](https://github.com/nmburgan))
- clj-parent: Switch from puppetlabs to our fork [\#70](https://github.com/OpenVoxProject/openvoxdb/pull/70) ([bastelfreak](https://github.com/bastelfreak))

## [8.10.0](https://github.com/openvoxproject/openvoxdb/tree/8.10.0) (2025-08-04)

[Full Changelog](https://github.com/openvoxproject/openvoxdb/compare/8.9.1...8.10.0)

**Implemented enhancements:**

- CI: Switch tests puppetlabs/puppetserver-\>OpenVoxProject/openvox-server [\#67](https://github.com/OpenVoxProject/openvoxdb/pull/67) ([bastelfreak](https://github.com/bastelfreak))
- ezbake: Update 2.6.3-SNAPSHOT-openvox-\>3.0.1-SNAPSHOT [\#48](https://github.com/OpenVoxProject/openvoxdb/pull/48) ([bastelfreak](https://github.com/bastelfreak))
- \(gh-21\) Enable acceptance testing [\#40](https://github.com/OpenVoxProject/openvoxdb/pull/40) ([jpartlow](https://github.com/jpartlow))
- Remove update checking code [\#37](https://github.com/OpenVoxProject/openvoxdb/pull/37) ([smortex](https://github.com/smortex))
- Remove the update link from the dashboard [\#36](https://github.com/OpenVoxProject/openvoxdb/pull/36) ([corporate-gadfly](https://github.com/corporate-gadfly))
- Add JDK21 to CI [\#35](https://github.com/OpenVoxProject/openvoxdb/pull/35) ([bastelfreak](https://github.com/bastelfreak))
- CI: Add Ruby 3.3 & 3.4 [\#29](https://github.com/OpenVoxProject/openvoxdb/pull/29) ([bastelfreak](https://github.com/bastelfreak))
- Switch from puppet to openvox gem [\#26](https://github.com/OpenVoxProject/openvoxdb/pull/26) ([bastelfreak](https://github.com/bastelfreak))
- puppetdb-terminus: migrate to openvoxdb-terminus [\#24](https://github.com/OpenVoxProject/openvoxdb/pull/24) ([bastelfreak](https://github.com/bastelfreak))
- Switch from facter to openfact [\#23](https://github.com/OpenVoxProject/openvoxdb/pull/23) ([bastelfreak](https://github.com/bastelfreak))
- Rework CI config to match current standards [\#15](https://github.com/OpenVoxProject/openvoxdb/pull/15) ([bastelfreak](https://github.com/bastelfreak))
- Add JMX warning [\#13](https://github.com/OpenVoxProject/openvoxdb/pull/13) ([nmburgan](https://github.com/nmburgan))

**Fixed bugs:**

- \[Bug\]: Release artifacts for openvoxdb 8.9.1 have been overwritten [\#31](https://github.com/OpenVoxProject/openvoxdb/issues/31)

## 8.9.1

* Add obsoletes/replaces/conflicts metadata with puppetdb and puppetdb-termini on the openvoxdb and openvoxdb-termini packages.

## 8.9.0

* Initial openvoxdb release. Based on puppetdb 8.8.1. Supported on all platforms that puppetdb currently supports, but for all architectures rather than just x86_64.


\* *This Changelog was automatically generated by [github_changelog_generator](https://github.com/github-changelog-generator/github-changelog-generator)*
