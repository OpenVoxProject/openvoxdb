---
title: "Status endpoint"
layout: default
canonical: "/puppetdb/latest/api/status/v1/status.html"
---

# Status endpoint

[curl]: ../curl.markdown#using-curl-from-localhost-non-sslhttp
[status-api]: https://github.com/puppetlabs/trapperkeeper-status

The `/status` endpoint implements the status API used across OpenVox services
for coordinated monitoring. See the [trapperkeeper-status
documentation][status-api] for detailed information.

## `/status/v1/services/puppetdb-status`

This query endpoint will return status about the OpenVoxDB instance on a host.

### Response format

The response will be in `application/json`, and will return a JSON map like the
following:

    {
        "detail_level": "info",
         "service_status_version": 1,
         "service_version": "4.0.0-SNAPSHOT",
         "state": "running",
         "status": {
             "maintenance_mode?": false,
             "read_db_up?": true,
             "write_db_up?": true,
             "queue_depth": 0
         }
    }

* `detail_level`: info is currently the only level.
* `service_status_version`: version of the status API.
* `service_version`: version of OpenVoxDB.
* `state`: short description of OpenVoxDB's current state:
    * "starting" if OpenVoxDB is in maintenance mode.
    * "running" if not in maintenance mode and read and write databases are up.
    * "error" if the read or write databases are down.
* `status`:
    * `maintenance_mode?`: indicates whether OpenVoxDB is in maintenance mode.
    OpenVoxDB enters maintenance mode at startup and exits it after completing any
    pending migrations and initial data synchronization (when using HA).
    While in maintenance mode, OpenVoxDB will not respond to queries.
    * `read_db_up?`: indicates whether the read database is responding to queries.
    * `write_db_up?`: boolean indicating whether at least one write
      database is responding to queries.
    * `write_dbs_up?`: boolean indicating whether all of the write
      databases are responding to queries.
    * `write_dbs_up?`: map of database (string) names to `{"up?":
      boolean}` values indicating whether the database is responding
      to queries.  If there is only one write database, the name will
      be an empty string.
    * `queue_depth`: depth of the command queue. If the queue is not yet
      initialized, this field will be null.
