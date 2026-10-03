#!/usr/bin/env python3
"""Check local DB-backed reads plus the public HTML, JS and deployed revision."""
import argparse
import hashlib
import json
import re
import time
from urllib.parse import urljoin
from urllib.request import Request, urlopen


def fetch(url):
    request = Request(url, headers={"Cache-Control": "no-cache"})
    with urlopen(request, timeout=5) as response:
        return response.read(), response.headers.get("Content-Type", "")


def read_json(url):
    body, content_type = fetch(url)
    if "application/json" not in content_type:
        raise ValueError("Expected a JSON response")
    return json.loads(body)


def verify(backend, public, revision=None, index_sha256=None):
    nonce = str(time.time_ns())
    health = read_json(f"{backend}/api/health")
    if health.get("success") is not True or health.get("data", {}).get("status") != "ok":
        raise ValueError("Backend health check failed")
    # /api/health alone does not access the DB. This read runs the actual public query.
    posts = read_json(f"{backend}/api/v1/blog/posts?size=1")
    if posts.get("success") is not True or not isinstance(posts.get("data", {}).get("items"), list):
        raise ValueError("Database-backed post list check failed")
    html, content_type = fetch(f"{public}/?deployment_check={nonce}")
    if "text/html" not in content_type:
        raise ValueError("Public index did not return HTML")
    if index_sha256 and hashlib.sha256(html).hexdigest() != index_sha256:
        raise ValueError("Public index does not match the deployed HTML")
    script = re.search(r'<script\b[^>]*\bsrc=["\']([^"\']+)', html.decode())
    if not script:
        raise ValueError("Public index does not reference a script")
    script_url = urljoin(public + "/", script.group(1))
    if not script_url.startswith(public + "/"):
        raise ValueError("Public application script must be on the same origin")
    javascript, content_type = fetch(script_url)
    if not javascript or not any(kind in content_type for kind in ("javascript", "ecmascript")):
        raise ValueError("Public application script is missing or returned the SPA fallback")
    if revision:
        marker = read_json(f"{public}/deployment.json?deployment_check={nonce}")
        if marker.get("revision") != revision:
            raise ValueError("Public frontend revision does not match this deployment")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--backend", required=True)
    parser.add_argument("--public", required=True)
    parser.add_argument("--revision")
    parser.add_argument("--index-sha256")
    parser.add_argument("--attempts", type=int, default=12)
    parser.add_argument("--delay", type=float, default=3)
    args = parser.parse_args()
    if args.attempts < 1 or args.delay < 0:
        parser.error("attempts must be positive and delay must be non-negative")
    for attempt in range(args.attempts):
        try:
            verify(args.backend.rstrip("/"), args.public.rstrip("/"), args.revision, args.index_sha256)
            print("Backend, DB query, public HTML and script checks passed.")
            return
        except Exception as error:
            # HTTP bodies and connection settings may contain operational details.
            print(f"Readiness attempt {attempt + 1}/{args.attempts}: {type(error).__name__}")
            if attempt + 1 == args.attempts:
                raise SystemExit("Deployment smoke checks did not pass") from None
            time.sleep(args.delay)


if __name__ == "__main__":
    main()
