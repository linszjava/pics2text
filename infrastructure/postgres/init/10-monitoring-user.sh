#!/usr/bin/env bash
set -Eeuo pipefail

monitor_user="${POSTGRES_MONITOR_USER:-pics2text_monitor}"
monitor_password="${POSTGRES_MONITOR_PASSWORD:-pics2text_monitor_dev}"

psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  --set=monitor_user="$monitor_user" \
  --set=monitor_password="$monitor_password" <<'SQL'
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'monitor_user', :'monitor_password')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = :'monitor_user') \gexec
SELECT format('ALTER ROLE %I PASSWORD %L', :'monitor_user', :'monitor_password') \gexec
SELECT format('GRANT pg_monitor TO %I', :'monitor_user') \gexec
SQL
