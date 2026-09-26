#!/usr/bin/env bash
set -euo pipefail

until MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqladmin ping -h mysql-replica -uroot --silent; do
  sleep 2
done

MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -h mysql-replica -uroot <<SQL
SET sql_log_bin=0;
CREATE USER IF NOT EXISTS 'report_reader'@'%' IDENTIFIED BY '${MYSQL_REPLICA_READ_PASSWORD}';
GRANT SELECT ON startup_app.* TO 'report_reader'@'%';
SET sql_log_bin=1;
STOP REPLICA;
RESET REPLICA ALL;
CHANGE REPLICATION SOURCE TO
  SOURCE_HOST='mysql-primary',
  SOURCE_PORT=3306,
  SOURCE_USER='replicator',
  SOURCE_PASSWORD='${MYSQL_REPLICATION_PASSWORD}',
  SOURCE_AUTO_POSITION=1;
START REPLICA;
SQL

echo "MySQL replica configured."
