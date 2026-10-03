#!/usr/bin/env bash
set -Eeuo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
project_root="$(cd "$script_dir/../.." && pwd)"
backup_root="${1:-$project_root/backups/$(date -u +%Y%m%dT%H%M%SZ)}"
postgres_db="${POSTGRES_DB:-pics2text}"
postgres_user="${POSTGRES_USER:-pics2text}"
object_container=""
object_was_running=false

if [[ ! "$postgres_db" =~ ^[A-Za-z0-9_.-]+$ ]] || [[ ! "$postgres_user" =~ ^[A-Za-z0-9_.-]+$ ]]; then
  echo "POSTGRES_DB 和 POSTGRES_USER 仅允许字母、数字、点、下划线和连字符" >&2
  exit 1
fi

checksum() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

restart_object_storage() {
  if [[ "$object_was_running" == true ]]; then
    (cd "$project_root" && docker compose start object-storage >/dev/null)
    for _ in {1..30}; do
      if [[ "$(cd "$project_root" && docker compose ps --format json object-storage)" == *'"Health":"healthy"'* ]]; then
        return
      fi
      sleep 1
    done
    echo "警告：object-storage 已重新启动，但未在 30 秒内达到 healthy" >&2
  fi
}
trap restart_object_storage EXIT

mkdir -p "$backup_root"
backup_root="$(cd "$backup_root" && pwd)"
if [[ -n "$(find "$backup_root" -mindepth 1 -maxdepth 1 -print -quit)" ]]; then
  echo "备份目录必须为空: $backup_root" >&2
  exit 1
fi

cd "$project_root"
docker compose exec -T postgres pg_dump \
  --username "$postgres_user" --dbname "$postgres_db" \
  --format=custom --compress=6 --no-owner --no-privileges \
  > "$backup_root/postgres.dump"

object_container="$(docker compose ps -q object-storage)"
if [[ -z "$object_container" ]]; then
  echo "找不到 object-storage 容器" >&2
  exit 1
fi
object_volume="$(docker inspect "$object_container" \
  --format '{{range .Mounts}}{{if eq .Destination "/data"}}{{.Name}}{{end}}{{end}}')"
if [[ -z "$object_volume" ]]; then
  echo "找不到 object-storage 的 /data 数据卷" >&2
  exit 1
fi
if [[ "$(docker inspect "$object_container" --format '{{.State.Running}}')" == true ]]; then
  object_was_running=true
  docker compose stop object-storage >/dev/null
fi

docker run --rm \
  --mount "type=volume,src=$object_volume,dst=/source,readonly" \
  --mount "type=bind,src=$backup_root,dst=/backup" \
  alpine:3.22 tar -C /source -czf /backup/object-storage.tar.gz .

postgres_sha="$(checksum "$backup_root/postgres.dump")"
object_sha="$(checksum "$backup_root/object-storage.tar.gz")"
created_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
cat > "$backup_root/manifest.env" <<EOF
BACKUP_FORMAT_VERSION=1
CREATED_AT=$created_at
POSTGRES_DATABASE=$postgres_db
POSTGRES_SHA256=$postgres_sha
OBJECT_STORAGE_SHA256=$object_sha
EOF

echo "备份完成: $backup_root"
