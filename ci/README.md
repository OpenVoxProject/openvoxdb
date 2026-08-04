# OpenVoxDB Testing and CI

## Jargon

### spec
A testing specification string. The element is the test flavor and the trailing elements describe dependency information (JDK, PostgreSQL, OpenVox, OpenVox Server). Examples:
- `core/openjdk8/pg-9.6`
- `int/openjdk11/pup-6.x/srv-6.x/pg-11`
- `core+ext/openjdk11/pg-11/rich`
- `rspec/pup-6.x`

### flavor
The first element of the spec string describing which test suite to run. One of:
- `core` (`lein test`)
- `ext` (external tests on OpenVoxDB jar)
- `core+ext` (both core and external tests)
- `int` (integration tests with OpenVox and OpenVox Server)
- `lint` (check Clojure code with Eastwood and clj-kondo)
- `rspec`

### ref
The version number or branch name of an element of the spec. Anything that comes after the hyphen if there is one. The ref of `pg-9.6` is `9.6`. The ref of `pup-6.x` is `6.x`.

### JDK flavor
The JDK package name like `openjdk8`.

## Testing Scripts

### `ext/bin/symlink-relative-to`
Creates symbolic link to a relative path. Relative path is forced even if absolute is given.

### `ext/bin/sha256sum`
Outputs sha256 checksum of STDIN

### `ext/bin/config-openvox-test-ref`
Modify local testing environment to use specific version of OpenVox.

### `ext/bin/config-openvox-server-test-ref`
Modify local testing environment to use specific version of OpenVox Server.

### `ext/bin/test-config`
Get, set, or reset persistent test configuration values by storing them in local files in the directory `ext/test-conf`

### `ext/bin/flavor-from-spec`
Parses a test spec string and prints the test flavor (type of tests to run like core or lint)

### `ext/bin/check-spec-env`
Ensures that correct JDK is installed and test flavor is valid.

### `ext/bin/spec-includes`
Prints search string if its a component of test spec.

### `ext/bin/jdk-from-spec`
Parses a test spec string and prints the JDK package name

### `ext/bin/prefixed-ref-from-spec`
Parses a test spec for a given element (like pg-13) and prints the ref (version number)

### `ext/bin/jdk-info`
Prints information about installed JDK
- Can output full version (`1.8.0_172`), major version (`8`), or package/spec (`openjdk8`)

### `ext/bin/require-jdk`
Installs JDK onto machine

### `ext/bin/require-leiningen`
Installs Leiningen onto machine

### `ext/bin/require-pgbox`
Installs pgbox onto machine

### `ext/bin/prep-debianish`
Prepares Debian-like Linux machines for running OpenVoxDB tests

### `ext/bin/prep-macos`
Prepares MacOS machines for running OpenVoxDB tests

### `ext/bin/pdbbox-init`
Creates a local OpenVoxDB sandbox directory which contains a PostgreSQL sandbox

### `ext/bin/pdbbox-env`
Sets up environment variables and runs a command using  `pgbox env`

### `ext/bin/with-pdbbox`
Runs a command using new, empheral OpenVoxDB sandbox (with an ephemeral PostgreSQL sandbox inside of it). Starts PostgreSQL and runs a command using the sandboxes. Destroys sandboxes and stops PostgreSQL afterwards.

### `ext/bin/boxed-core-tests`
Runs a command using ephemeral Leiningen and pgbox executables, as well as ephemeral OpenVoxDB and PostgreSQL sandboxes. User still needs PostgreSQL and JDK installed. Passes command to `ext/bin/with-pdbbox` which creates the ephemeral sandboxes.

### `ext/bin/boxed-integration-tests`
Exactly the same as `ext/bin/boxed-core-tests` except uses this for the temporary directory name (starts with `int-test`).

### `ext/bin/run-external-tests`
Creates an OpenVoxDB uberjar and runs some tests on its behavior and output.

### `ext/bin/run-rspec-tests`
Run Ruby rspec tests on Ruby code in `puppet` directory.

### `ext/bin/run-normal-tests`
Runs core, integration, and external tests with `boxed-core-tests`, `boxed-integration-tests`, and `run-external-tests` respectively. Also prints log lines saying which script is running.

### `ext/test/oom-causes-shutdown`
Tests an existing OpenVoxDB jar to to see if it gracefully shuts down on an OutOfMemoryError when forced to allocate a giant amount of memory.

### `ext/test/top-level-cli`
Tests an existing OpenVoxDB jar's command line interface by running subcommands and grepping the output.

### `ext/test/schema-mismatch-causes-pdb-shutdown`
Verifies that an OpenVoxDB jar will periodically check and shut down when the database is at an unrecognized schema (migration) number.

### `ext/test/database-migration-config`
Ensures OpenVoxDB jar fails to start and logs error message when the OpenVoxDB PostgreSQL database is not migrated to the most recent migration that OpenVoxDB knows about.

### `ext/bin/contributors-in-git-log`
Given two git tags, prints out a list of contributors who authored commits in that commit range.

### `ext/bin/render-pdb-schema`
Creates an SVG graph of the OpenVoxDB PostgreSQL schema for educational purposes. Requires postgresql-autodoc and graphiz.

### `pdb`
In top-level of source tree. Starts an existing OpenVoxDB uberjar and passes all arguments. Allows overriding of Bouncy Castle jars.

## CI Scripts

### `ci/bin/prep-and-run-in`
Prepares the CI machine for tests and runs tests using `ci/bin/run`.

### `ci/bin/run`
Runs tests on a prepared CI machine.

## CI Configuration

###  `.github/workflows/main.yml`
On pull request and push runs core, external, integration, and rspec tests on MacOS machines. Also runs lint test on an Ubuntu machine.
