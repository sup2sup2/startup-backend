#!/usr/bin/env bash
set -euo pipefail

mysql -uroot -p"$MYSQL_ROOT_PASSWORD" <<SQL
CREATE USER IF NOT EXISTS 'replicator'@'%' IDENTIFIED BY '${MYSQL_REPLICATION_PASSWORD}';
GRANT REPLICATION SLAVE ON *.* TO 'replicator'@'%';
CREATE USER IF NOT EXISTS 'report_reader'@'%' IDENTIFIED BY '${MYSQL_REPLICA_READ_PASSWORD}';
GRANT SELECT ON startup_app.* TO 'report_reader'@'%';
FLUSH PRIVILEGES;
SQL
