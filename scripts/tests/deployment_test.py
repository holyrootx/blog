"""Offline deployment contract tests. All privileged commands target temporary files."""
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from unittest.mock import patch

SCRIPTS = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("smoke", SCRIPTS / "check-deployment.py")
SMOKE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(SMOKE)


class SmokeTest(unittest.TestCase):
    INDEX = b'''<html><head><link rel="stylesheet" href="/assets/app.css">
<link rel="modulepreload" href="/assets/vendor.js"></head>
<body><script type="module" src="/assets/app.js"></script></body></html>'''

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.index = Path(self.temp.name) / "index.html"
        self.index.write_bytes(self.INDEX)

    def tearDown(self):
        self.temp.cleanup()

    def responses(self, revision="current", database_ok=True, script_type="application/javascript"):
        return [
            (json.dumps({"success": True, "data": {"status": "ok"}}).encode(), "application/json"),
            (json.dumps({"success": database_ok, "data": {"items": []}}).encode(), "application/json"),
            (self.INDEX, "text/html"),
            (b"window.ready = true", script_type),
            (json.dumps({"revision": revision}).encode(), "application/json"),
        ]

    def test_matching_release_passes(self):
        with patch.object(SMOKE, "fetch", side_effect=self.responses()):
            SMOKE.verify("http://backend", "https://public", "current", self.index)

    def test_requests_identify_checker_and_bypass_cache(self):
        with patch.object(SMOKE, "urlopen") as opener:
            response = opener.return_value.__enter__.return_value
            response.read.return_value = b"ok"
            response.headers = {"Content-Type": "text/plain"}
            SMOKE.fetch("https://public/")
        request = opener.call_args.args[0]
        self.assertEqual("jsjlog-deployment-check/1.0 (+https://github.com/holyrootx/blog)",
                         request.get_header("User-agent"))
        self.assertEqual("no-cache", request.get_header("Cache-control"))
        self.assertEqual(5, opener.call_args.kwargs["timeout"])

    def verify_html(self, html, expected=True):
        responses = self.responses()
        responses[2] = (html, "text/html")
        with patch.object(SMOKE, "fetch", side_effect=responses) as fetch:
            SMOKE.verify("http://backend", "https://public", "current", self.index if expected else None)
        return fetch

    def test_cloudflare_beacon_before_app_script_is_allowed(self):
        beacon = b'<script defer src="https://static.cloudflareinsights.com/beacon.min.js/x"></script>'
        fetch = self.verify_html(self.INDEX.replace(b"<head>", b"<head>" + beacon))
        self.assertEqual("https://public/assets/app.js", fetch.call_args_list[3].args[0])

    def test_precheck_without_index_ignores_external_first_script(self):
        beacon = b'<script src="https://static.cloudflareinsights.com/beacon.min.js"></script>'
        fetch = self.verify_html(beacon + self.INDEX, expected=False)
        self.assertEqual("https://public/assets/app.js", fetch.call_args_list[3].args[0])

    def test_equivalent_relative_and_absolute_asset_urls_match(self):
        html = self.INDEX.replace(b'/assets/app.js', b'https://PUBLIC:443/assets/app.js#ignored')
        html = html.replace(b'/assets/app.css', b'./assets/app.css')
        self.verify_html(html)

    def test_changed_css_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "asset references"):
            self.verify_html(self.INDEX.replace(b'app.css', b'old.css'))

    def test_missing_modulepreload_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "asset references"):
            self.verify_html(self.INDEX.replace(b'<link rel="modulepreload" href="/assets/vendor.js">', b''))

    def test_unexpected_same_origin_script_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "unexpected"):
            self.verify_html(self.INDEX + b'<script src="/assets/stale.js"></script>')

    def test_changed_base_cannot_redirect_relative_app_assets(self):
        self.index.write_bytes(self.INDEX.replace(b'/assets/', b'assets/'))
        html = self.index.read_bytes().replace(b'<head>', b'<head><base href="https://other.example/">')
        with self.assertRaisesRegex(ValueError, "asset references"):
            self.verify_html(html)

    def test_original_external_app_asset_must_remain(self):
        self.index.write_bytes(self.INDEX + b'<script src="https://cdn.example/app.js"></script>')
        with self.assertRaisesRegex(ValueError, "asset references"):
            self.verify_html(self.INDEX)

    def test_html_whitespace_changes_do_not_change_asset_contract(self):
        self.verify_html(self.INDEX.replace(b'<body>', b'<body>\n  '))

    def test_healthy_process_with_failed_database_is_rejected(self):
        with patch.object(SMOKE, "fetch", side_effect=self.responses(database_ok=False)):
            with self.assertRaisesRegex(ValueError, "Database"):
                SMOKE.verify("http://backend", "https://public", "current")

    def test_stale_frontend_revision_is_rejected(self):
        with patch.object(SMOKE, "fetch", side_effect=self.responses(revision="old")):
            with self.assertRaisesRegex(ValueError, "revision"):
                SMOKE.verify("http://backend", "https://public", "current")

    def test_spa_fallback_for_missing_script_is_rejected(self):
        with patch.object(SMOKE, "fetch", side_effect=self.responses(script_type="text/html")):
            with self.assertRaisesRegex(ValueError, "script"):
                SMOKE.verify("http://backend", "https://public", "current")

    def test_stale_html_is_rejected_even_when_revision_marker_is_current(self):
        with self.assertRaisesRegex(ValueError, "HTML"):
            self.verify_html(self.INDEX.replace(b'app.js', b'old-build.js'))


class RecoveryTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.app = self.root / "app"
        self.web = self.root / "www"
        self.release_id = "a" * 40 + "-1-1"
        self.release = self.app / "releases" / self.release_id
        for directory in [self.app / "backend", self.web, self.release / "frontend", self.release / "scripts", self.root / "bin"]:
            directory.mkdir(parents=True, exist_ok=True)
        (self.app / "backend/app.jar").write_text("old")
        (self.web / "index.html").write_text("old frontend")
        (self.web / "deployment.json").write_text('{"revision":"old"}')
        (self.release / "app.jar").write_text("new")
        (self.release / "frontend/index.html").write_text("new frontend")
        (self.release / "frontend/deployment.json").write_text(json.dumps({"revision": self.release_id}))
        (self.release / "scripts/check-deployment.py").write_text('''import json, os, pathlib, sys
current = (pathlib.Path(os.environ["BLOG_APP_ROOT"]) / "backend/app.jar").read_text()
args = sys.argv[1:]
index = pathlib.Path(args[args.index("--index-file") + 1])
assert index.read_text() == (pathlib.Path(os.environ["BLOG_WEB_ROOT"]) / "index.html").read_text()
with open(os.environ["CHECK_LOG"], "a") as log:
    log.write(json.dumps(args) + "\\n")
if os.environ.get("FAIL_CHECK") == "1" and current == "new":
    sys.exit(1)
if os.environ.get("FAIL_PRECHECK") == "1":
    sys.exit(1)
''')
        lines = [f"{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.relative_to(self.release)}" for path in self.release.rglob("*") if path.is_file()]
        (self.release / "SHA256SUMS").write_text("\n".join(lines) + "\n")
        fake = self.root / "bin/fake"
        fake.write_text('''#!/usr/bin/env python3
import hashlib, os, pathlib, shutil, sys
command = pathlib.Path(sys.argv[0]).name
args = sys.argv[1:]
if command == "sudo":
    os.execvp(args[1], args[1:])
elif command == "rsync":
    source, target = map(pathlib.Path, args[-2:])
    if "--delete" in args and target.exists():
        shutil.rmtree(target)
    shutil.copytree(source, target, dirs_exist_ok=True)
elif command == "sha256sum":
    for line in pathlib.Path(args[-1]).read_text().splitlines():
        expected, name = line.split("  ", 1)
        if hashlib.sha256(pathlib.Path(name).read_bytes()).hexdigest() != expected:
            sys.exit(1)
elif command == "systemctl":
    current = (pathlib.Path(os.environ["BLOG_APP_ROOT"]) / "backend/app.jar").read_text()
    if os.environ.get("FAIL_RESTART") == "1" and current == "new":
        sys.exit(1)
# flock is deliberately a no-op; actual OS locking needs the Linux server.
''')
        fake.chmod(0o755)
        for command in ["sudo", "rsync", "sha256sum", "systemctl", "flock"]:
            (fake.parent / command).symlink_to(fake)
        self.check_log = self.root / "checks.jsonl"
        self.env = {**os.environ, "PATH": f"{fake.parent}:{os.environ['PATH']}", "BLOG_APP_ROOT": str(self.app), "BLOG_WEB_ROOT": str(self.web), "PUBLIC_BASE_URL": "https://test.invalid", "CHECK_LOG": str(self.check_log)}

    def tearDown(self):
        self.temp.cleanup()

    def run_deployment(self, **env):
        return subprocess.run(["bash", str(SCRIPTS / "deploy-release.sh"), self.release_id], env={**self.env, **env}, text=True, capture_output=True)

    def assert_restored(self):
        self.assertEqual("old", (self.app / "backend/app.jar").read_text())
        self.assertEqual("old frontend", (self.web / "index.html").read_text())
        self.assertEqual("ROLLED_BACK", (self.release / "status").read_text().strip())

    def test_success_retains_previous_files(self):
        result = self.run_deployment()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("new", (self.app / "backend/app.jar").read_text())
        self.assertEqual("new frontend", (self.web / "index.html").read_text())
        self.assertEqual("old", (self.release / "rollback/app.jar").read_text())
        self.assertEqual("ACTIVE", (self.release / "status").read_text().strip())
        checks = [json.loads(line) for line in self.check_log.read_text().splitlines()]
        self.assertIn(str(self.web / "index.html"), checks[0])
        self.assertIn(str(self.release / "frontend/index.html"), checks[1])

    def test_failed_readiness_restores_both_artifacts_and_fails_job(self):
        result = self.run_deployment(FAIL_CHECK="1")
        self.assertNotEqual(0, result.returncode)
        self.assert_restored()
        last_check = json.loads(self.check_log.read_text().splitlines()[-1])
        self.assertIn(str(self.release / "rollback/frontend/index.html"), last_check)
        self.assertEqual("old", last_check[last_check.index("--revision") + 1])

    def test_rollback_without_previous_revision_still_checks_index(self):
        (self.web / "deployment.json").unlink()
        result = self.run_deployment(FAIL_CHECK="1")
        self.assertNotEqual(0, result.returncode)
        self.assert_restored()
        last_check = json.loads(self.check_log.read_text().splitlines()[-1])
        self.assertIn(str(self.release / "rollback/frontend/index.html"), last_check)
        self.assertNotIn("--revision", last_check)

    def test_failed_precheck_stops_before_backup_or_mutation(self):
        result = self.run_deployment(FAIL_PRECHECK="1")
        self.assertNotEqual(0, result.returncode)
        self.assertEqual("old", (self.app / "backend/app.jar").read_text())
        self.assertEqual("old frontend", (self.web / "index.html").read_text())
        self.assertFalse((self.release / "rollback").exists())

    def test_failed_restart_restores_both_artifacts(self):
        result = self.run_deployment(FAIL_RESTART="1")
        self.assertNotEqual(0, result.returncode)
        self.assert_restored()

    def test_bad_checksum_stops_before_mutation(self):
        (self.release / "app.jar").write_text("corrupted")
        result = self.run_deployment()
        self.assertNotEqual(0, result.returncode)
        self.assertEqual("old", (self.app / "backend/app.jar").read_text())
        self.assertFalse((self.release / "rollback").exists())


if __name__ == "__main__":
    unittest.main()
