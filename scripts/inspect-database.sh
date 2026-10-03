#!/usr/bin/env bash
set -euo pipefail

# mysql_config_editor stores credentials outside this repository and shell history.
login_path=${1:?Usage: inspect-database.sh <mysql-login-path> <database> <new-output-directory>}
database=${2:?Specify the database to inspect}
output_dir=${3:?Specify a new output directory outside the repository}
[[ "$login_path" =~ ^[A-Za-z0-9_-]+$ ]] || exit 2
[[ "$database" =~ ^[A-Za-z0-9_-]+$ ]] || exit 2
script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
repo_dir=$(cd "$script_dir/.." && pwd)
output_dir=$(python3 -c 'import os,sys; print(os.path.realpath(sys.argv[1]))' "$output_dir")
case "$output_dir/" in "$repo_dir/"*) echo 'Keep schema evidence outside the repository.' >&2; exit 2;; esac
umask 077
mkdir "$output_dir"
mysql --login-path="$login_path" --database="$database" --batch --raw \
  < "$repo_dir/ops/database/inspection/schema-state.sql" > "$output_dir/schema-state.tsv"
# Schema only; view definitions may still include DEFINER account names.
mysqldump --login-path="$login_path" --no-data --skip-triggers --skip-lock-tables \
  --no-tablespaces --set-gtid-purged=OFF --skip-comments "$database" > "$output_dir/schema.sql"
has_history=$(mysql --login-path="$login_path" --database="$database" --batch --skip-column-names \
  --execute="SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='flyway_schema_history'")
if [[ "$has_history" == '1' ]]; then
  mysql --login-path="$login_path" --database="$database" --batch --raw \
    --execute='SELECT installed_rank, version, description, type, script, checksum, installed_on, success FROM flyway_schema_history ORDER BY installed_rank' \
    > "$output_dir/flyway-history.tsv"
fi
printf 'Read-only metadata inspection saved to %s\n' "$output_dir"
