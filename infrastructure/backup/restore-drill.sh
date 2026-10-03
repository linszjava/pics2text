#!/usr/bin/env bash
set -Eeuo pipefail

if [[ $# -ne 1 ]]; then
  echo "用法: $0 <备份目录>" >&2
  exit 2
fi

backup_root="$(cd "$1" && pwd)"
manifest="$backup_root/manifest.env"
if [[ ! -f "$manifest" || ! -f "$backup_root/postgres.dump" || ! -f "$backup_root/object-storage.tar.gz" ]]; then
  echo "备份目录缺少 manifest.env、postgres.dump 或 object-storage.tar.gz" >&2
  exit 1
fi

# manifest.env 只允许由本项目备份脚本生成，加载前拒绝未知键和值格式。
if grep -Ev '^(BACKUP_FORMAT_VERSION|CREATED_AT|POSTGRES_DATABASE|POSTGRES_SHA256|OBJECT_STORAGE_SHA256)=[A-Za-z0-9:._+-]+$' "$manifest" | grep -q .; then
  echo "备份清单格式非法" >&2
  exit 1
fi
# shellcheck disable=SC1090
source "$manifest"
if [[ "$BACKUP_FORMAT_VERSION" != 1 ]]; then
  echo "不支持的备份格式版本: $BACKUP_FORMAT_VERSION" >&2
  exit 1
fi

checksum() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

[[ "$(checksum "$backup_root/postgres.dump")" == "$POSTGRES_SHA256" ]] || {
  echo "PostgreSQL 备份校验和不匹配" >&2; exit 1;
}
[[ "$(checksum "$backup_root/object-storage.tar.gz")" == "$OBJECT_STORAGE_SHA256" ]] || {
  echo "对象存储备份校验和不匹配" >&2; exit 1;
}

suffix="$(date +%s)-$$"
postgres_container="pics2text-restore-postgres-$suffix"
object_container="pics2text-restore-object-$suffix"
object_volume="pics2text-restore-object-$suffix"
started_at="$(date +%s)"

cleanup() {
  docker rm -f "$postgres_container" "$object_container" >/dev/null 2>&1 || true
  docker volume rm "$object_volume" >/dev/null 2>&1 || true
}
trap cleanup EXIT

docker run -d --name "$postgres_container" \
  -e POSTGRES_PASSWORD=restore_drill -e "POSTGRES_DB=$POSTGRES_DATABASE" \
  postgres:18-alpine >/dev/null
for _ in {1..30}; do
  docker exec "$postgres_container" pg_isready -U postgres -d "$POSTGRES_DATABASE" >/dev/null 2>&1 && break
  sleep 1
done
docker exec "$postgres_container" pg_isready -U postgres -d "$POSTGRES_DATABASE" >/dev/null
docker exec -i "$postgres_container" pg_restore \
  --username postgres --dbname "$POSTGRES_DATABASE" --no-owner --no-privileges \
  < "$backup_root/postgres.dump"
table_count="$(docker exec "$postgres_container" psql -U postgres -d "$POSTGRES_DATABASE" -Atc \
  "select count(*) from information_schema.tables where table_schema='public';")"
if [[ "$table_count" -lt 1 ]]; then
  echo "PostgreSQL 恢复后没有业务表" >&2
  exit 1
fi

docker volume create "$object_volume" >/dev/null
docker run --rm \
  --mount "type=volume,src=$object_volume,dst=/restore" \
  --mount "type=bind,src=$backup_root,dst=/backup,readonly" \
  alpine:3.22 tar -C /restore -xzf /backup/object-storage.tar.gz
object_file_count="$(docker run --rm \
  --mount "type=volume,src=$object_volume,dst=/restore,readonly" \
  alpine:3.22 sh -c "find /restore -type f | wc -l")"
if [[ "$object_file_count" -lt 1 ]]; then
  echo "对象存储恢复后没有数据文件" >&2
  exit 1
fi
docker run -d --name "$object_container" \
  --mount "type=volume,src=$object_volume,dst=/data" \
  chrislusf/seaweedfs:4.47 server -s3 -dir=/data >/dev/null
for _ in {1..30}; do
  docker exec "$object_container" wget -q --spider http://127.0.0.1:9333/cluster/status >/dev/null 2>&1 && break
  sleep 1
done
docker exec "$object_container" wget -q --spider http://127.0.0.1:9333/cluster/status

finished_at="$(date +%s)"
echo "恢复演练通过: PostgreSQL public 表=$table_count, 对象文件=$object_file_count, SeaweedFS=healthy, RTO=$((finished_at - started_at))s"
