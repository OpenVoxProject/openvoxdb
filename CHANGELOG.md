## Unreleased

## 7.21.2
* Fixed package metadata to require Java 11 or 17 rather than Java 8.  Puppetdb removed support for Java 8, but still builds with it for some reason. Openvoxdb is built with Java 11.

## 7.21.1
* Fixed package metadata to require openvox-agent >= 7.35.0. Was previously mistakenly set to openvox-agent >= 8.11.0.

## 7.21.0
* Initial openvoxdb release. Based on puppetdb 7.20.1. Supported on all platforms that puppetdb currently supports, but for all architectures rather than just x86_64.