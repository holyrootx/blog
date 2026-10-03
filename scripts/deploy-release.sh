#!/usr/bin/env bash
set -Eeuo pipefail

# Existing systemd/Nginx paths remain intact; this does not run database DDL.
release_id=${1:?Usage: deploy-release.sh <sha-run-attempt>}
[[ "$release_id" =~ ^[a-f0-9]{40}-[0-9]+-[0-9]+$ ]] || exit 2
app_root=${BLOG_APP_ROOT:-/home/ubuntu/apps/blog}
web_root=${BLOG_WEB_ROOT:-/var/www/blog}
backend_url=${BACKEND_BASE_URL:-http://127.0.0.1:8080}
public_url=${PUBLIC_BASE_URL:?Set PUBLIC_BASE_URL to the verified public HTTPS origin}
service=${BLOG_SERVICE:-blog-backend}
release_dir="$app_root/releases/$release_id"
backup_dir="$release_dir/rollback"
checker="$release_dir/scripts/check-deployment.py"

for command in flock rsync sha256sum python3 sudo; do
  command -v "$command" >/dev/null
done
sudo -n true
test -f "$app_root/backend/app.jar"
test -f "$web_root/index.html"
test -f "$release_dir/app.jar"
test -f "$release_dir/frontend/index.html"
test -f "$checker"
(cd "$release_dir" && sha256sum --check SHA256SUMS)

# Also serialize manual invocations on this server.
exec 9>"$app_root/.deploy.lock"
flock -w 300 9
test ! -e "$backup_dir"

check_release() {
  python3 "$checker" --backend "$backend_url" --public "$public_url" "$@"
}

# Do not replace an installation that cannot first be identified as healthy.
check_release --index-file "$web_root/index.html"
mkdir -p "$backup_dir/frontend"
cp -p "$app_root/backend/app.jar" "$backup_dir/app.jar"
sudo -n rsync -a "$web_root/" "$backup_dir/frontend/"
printf 'PREPARED\n' > "$release_dir/status"

recover() {
  failure_status=$1
  trap - ERR INT TERM
  set +e
  printf 'ROLLING_BACK\n' > "$release_dir/status"
  restore_failed=0
  cp -p "$backup_dir/app.jar" "$app_root/backend/.app.jar.rollback" || restore_failed=1
  if [[ "$restore_failed" -eq 0 ]]; then
    mv -f "$app_root/backend/.app.jar.rollback" "$app_root/backend/app.jar" || restore_failed=1
  fi
  sudo -n rsync -a --delete "$backup_dir/frontend/" "$web_root/" || restore_failed=1
  sudo -n systemctl restart "$service" || restore_failed=1
  previous_revision=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["revision"])' "$backup_dir/frontend/deployment.json" 2>/dev/null)
  if [[ -n "$previous_revision" ]]; then
    check_release --revision "$previous_revision" --index-file "$backup_dir/frontend/index.html" || restore_failed=1
  else
    check_release --index-file "$backup_dir/frontend/index.html" || restore_failed=1
  fi
  if [[ "$restore_failed" -eq 0 ]]; then
    printf 'ROLLED_BACK\n' > "$release_dir/status"
    echo 'Deployment failed; previous application files restored and smoke checks passed.' >&2
  else
    printf 'ROLLBACK_FAILED\n' > "$release_dir/status"
    echo "Rollback needs operator attention. Retained snapshot: $backup_dir" >&2
  fi
  exit "$failure_status"
}
trap 'recover $?' ERR
trap 'recover 130' INT
trap 'recover 143' TERM

install -m 644 "$release_dir/app.jar" "$app_root/backend/.app.jar.next"
mv -f "$app_root/backend/.app.jar.next" "$app_root/backend/app.jar"
sudo -n rsync -a --delete "$release_dir/frontend/" "$web_root/"
sudo -n systemctl restart "$service"
check_release --revision "$release_id" --index-file "$release_dir/frontend/index.html"
printf 'ACTIVE\n' > "$release_dir/status"
printf '%s\n' "$release_id" > "$app_root/releases/current-release"
trap - ERR INT TERM
echo "Deployment verified: $release_id"
