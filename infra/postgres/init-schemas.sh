#!/usr/bin/env bash
# Runs once, on first start of an empty data volume (ADR 004).
# One schema + one login role per service. Each role can only use its own schema.
set -e

SCHEMAS="identity catalog orders payments restaurants deliveries notifications"
PSQL="psql -v ON_ERROR_STOP=1 --username $POSTGRES_USER --dbname $POSTGRES_DB"

# Nobody gets anything by default.
$PSQL <<EOSQL
REVOKE ALL ON SCHEMA public FROM PUBLIC;
REVOKE CONNECT ON DATABASE ${POSTGRES_DB} FROM PUBLIC;
EOSQL

for schema in $SCHEMAS; do
  role="${schema}_svc"
  var="$(echo "$schema" | tr '[:lower:]' '[:upper:]')_DB_PASSWORD"
  password="${!var:-${schema}_dev_pw}"

  $PSQL <<EOSQL
CREATE ROLE ${role} LOGIN PASSWORD '${password}';
GRANT CONNECT ON DATABASE ${POSTGRES_DB} TO ${role};
CREATE SCHEMA ${schema} AUTHORIZATION ${role};
ALTER ROLE ${role} SET search_path = ${schema};
EOSQL
done

# Defense in depth: fail the initialization if any role can reach another service's schema.
for schema in $SCHEMAS; do
  for other in $SCHEMAS; do
    [ "$schema" = "$other" ] && continue
    leaked="$($PSQL -tAc "SELECT has_schema_privilege('${schema}_svc', '${other}', 'USAGE')")"
    if [ "$leaked" = "t" ]; then
      echo "ISOLATION ERROR: ${schema}_svc can access schema ${other}" >&2
      exit 1
    fi
  done
done
echo "EatsNow schemas created and isolation verified: $SCHEMAS"
