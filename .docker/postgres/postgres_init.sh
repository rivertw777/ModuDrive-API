#!/bin/sh
# One database + one login per service; each login can only connect to its own database.
# Runs only on an empty postgres volume — `make reset` to apply it to an existing one.
set -eu

create_db() { # <db> <role> <password>
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres -v pw="$3" <<SQL
CREATE ROLE $2 LOGIN PASSWORD :'pw';
-- Owning a database needs SET ROLE on the owner. A superuser (local) has it already; RDS's admin is
-- only CREATEROLE, and since PostgreSQL 16 creating a role no longer grants that — so grant it.
GRANT $2 TO CURRENT_USER;
CREATE DATABASE $1 OWNER $2;
REVOKE ALL ON DATABASE $1 FROM PUBLIC;
-- The pg_stat_statements view (spec 006 2-5-4). The library is preloaded (compose, RDS default), but
-- the view exists only in a database that created the extension.
\connect $1
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;
SQL
}

# DB_SERVICES picks which to make here — on AWS each RDS instance holds only some (terraform
# var.db_instances); locally one Postgres holds them all.
for service in ${DB_SERVICES:-member file notification auth}; do
    password_var="$(echo "$service" | tr 'a-z' 'A-Z')_DB_PASSWORD"
    eval "password=\${$password_var}"
    create_db "${service}_db" "${service}_service" "$password"
done

# Tables and the dev test users come from each service's Flyway migrations (db/migration, db/seed).
