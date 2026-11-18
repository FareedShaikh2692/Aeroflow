#!/bin/sh
# Creates the application role and hands each service database to it (docker-entrypoint-initdb.d).
set -eu
psql -v ON_ERROR_STOP=1 -U postgres <<SQL
CREATE ROLE aeroflow LOGIN PASSWORD '${AEROFLOW_DB_PASSWORD}';
SQL
for db in auth flight passenger booking checkin notification reporting events; do
  psql -v ON_ERROR_STOP=1 -U postgres -c "ALTER DATABASE aeroflow_${db} OWNER TO aeroflow;"
done
