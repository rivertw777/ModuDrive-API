#!/bin/sh
# One database + one login per service; each login can only connect to its own database.
# Runs only on an empty postgres volume — `make reset` to apply it to an existing one.
set -eu

create_db() { # <db> <role> <password>
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres -v pw="$3" <<SQL
CREATE ROLE $2 LOGIN PASSWORD :'pw';
CREATE DATABASE $1 OWNER $2;
REVOKE ALL ON DATABASE $1 FROM PUBLIC;
SQL
}

create_db member_db member_service "$MEMBER_DB_PASSWORD"
create_db file_db file_service "$FILE_DB_PASSWORD"
create_db notification_db notification_service "$NOTIFICATION_DB_PASSWORD"

# Tables and the dev test users come from each service's Flyway migrations (db/migration, db/seed).
