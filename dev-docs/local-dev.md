# Local Development Setup

This guide will help you set up a local development environment for OpenVoxDB.

## Prerequisites

- PostgreSQL 16+ installed
- [pgbox](https://gitlab.com/pgbox-org/pgbox) on your `$PATH`
- Leiningen (Clojure build tool)
- Java 17+

## Quick Start

1. **Configure the helper script**

   Copy the example config and set your PostgreSQL path:

   ```bash
   cp dev-resources/ovdb.conf.example .ovdb.conf
   ```

   Edit `.ovdb.conf` and set `pg_bin` to your PostgreSQL bin directory:

   ```bash
   # Mac (Homebrew)
   pg_bin=/opt/homebrew/opt/postgresql@17/bin

   # Mac (Postgres.app)
   pg_bin=/Applications/Postgres.app/Contents/Versions/17/bin

   # Linux (Debian/Ubuntu)
   pg_bin=/usr/lib/postgresql/17/bin

   # Linux (RHEL/CentOS)
   pg_bin=/usr/pgsql-17/bin

   # FreeBSD
   pg_bin=/usr/local/bin
   ```

2. **Validate your configuration**

   ```bash
   ./ovdb doctor
   ```

3. **Initialize and start the database**

   ```bash
   ./ovdb init
   ```

   This creates a default postgres sandbox along with the necessary OpenVoxDB
   configuration inside `dev-resources/sandboxes/tmp_pg`.

   If you wish to store your sandboxes in a different directory that can be
   set with the `OVDB_SANDBOX` environment variable or in the `.ovdb.conf`
   file.

4. **Run OpenVoxDB**

   ```bash
   ./ovdb run
   ```

   OpenVoxDB will be available at `http://localhost:8080`.

## Common Commands

| Command | Description |
|---------|-------------|
| `./ovdb init` | Initialize a new PostgreSQL sandbox |
| `./ovdb run` | Run OpenVoxDB |
| `./ovdb start` | Start the PostgreSQL server |
| `./ovdb stop` | Stop the PostgreSQL server |
| `./ovdb test` | Run unit tests |
| `./ovdb integration` | Run integration tests |
| `./ovdb repl` | Start a Clojure REPL |
| `./ovdb psql` | Connect to the database with psql |
| `./ovdb doctor` | Validate configuration |

Run `./ovdb --help` for the full list of commands.

## Multiple Sandboxes

You can create multiple PostgreSQL sandboxes for different purposes:

```bash
# Create a sandbox named "pg-18-test"
./ovdb --name pg-18-test --pgver 18 init

# Run against that sandbox
./ovdb --name pg-18-test run

# Run tests against it
./ovdb --name pg-18-test test
```

## Running Tests

### Unit Tests

Clojure unit tests that verify individual functions and components. These run
against your PostgreSQL sandbox and test the core logic without requiring
other openvox projects.

```bash
./ovdb test
```

To run a specific test:

```bash
./ovdb test :only puppetlabs.puppetdb.scf.migrate-test/some-test
```

### Integration Tests

Tests that verify OpenVoxDB works correctly with OpenVox and OpenVox-Server.
These tests clone and configure the OpenVox and OpenVox-Server repositories,
then run scenarios that exercise the full command submission and query
pipeline.

```bash
./ovdb integration
```

### External Tests

Black-box tests that run against a built uberjar. These verify the packaged
application behaves correctly, including CLI argument handling, database
migrations, error handling (like OOM and schema mismatches), and upgrade paths.

```bash
lein uberjar
./ovdb ext
```

## Populating Test Data

Use the benchmark command to populate the database with test data:

```bash
# Add 10 nodes (default)
./ovdb benchmark

# Add 100 nodes
./ovdb benchmark -- 100
```

## Configuration Reference

Configuration is read from (in order of precedence):

1. Command-line flags (`--pgver`, `--port`, etc.)
2. Environment variables (`OVDB_PG_BIN`, `OVDB_SANDBOX`, etc.)
3. Config file (`.ovdb.conf` in the repo root)
4. Built-in defaults

See `dev-resources/ovdb.conf.example` for all options.

## Troubleshooting

### "pg_bin is not configured"

Set the `pg_bin` variable in `.ovdb.conf` to point to your PostgreSQL installation's bin directory.

### "pg_ctl not found"

Ensure `pg_bin` points to the directory containing `pg_ctl`, `psql`, and other PostgreSQL binaries.

### Database won't start after reboot

PostgreSQL sandboxes don't persist across reboots. Start the server with:

```bash
./ovdb start
```
