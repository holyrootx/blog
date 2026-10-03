#!/usr/bin/env python3
"""Check local DB-backed reads plus the public HTML, JS and deployed revision."""
import argparse
from collections import Counter
from html.parser import HTMLParser
import json
from pathlib import Path
import time
from urllib.parse import urljoin, urlsplit, urlunsplit
from urllib.request import Request, urlopen


def fetch(url):
    request = Request(url, headers={
        "Cache-Control": "no-cache",
        "User-Agent": "jsjlog-deployment-check/1.0 (+https://github.com/holyrootx/blog)",
    })
    with urlopen(request, timeout=5) as response:
        return response.read(), response.headers.get("Content-Type", "")


def read_json(url):
    body, content_type = fetch(url)
    if "application/json" not in content_type:
        raise ValueError("Expected a JSON response")
    return json.loads(body)


def normalize_url(base, reference):
    parsed = urlsplit(urljoin(base, reference.strip()))
    scheme = parsed.scheme.lower()
    host = (parsed.hostname or "").lower()
    if scheme not in ("http", "https") or not host or parsed.username or parsed.password:
        raise ValueError("Application asset URL must use HTTP or HTTPS without credentials")
    port = parsed.port
    if ":" in host:
        host = f"[{host}]"
    if port is not None and port != {"http": 80, "https": 443}[scheme]:
        host += f":{port}"
    return urlunsplit((scheme, host, parsed.path or "/", parsed.query, ""))


def same_origin(first, second):
    a, b = urlsplit(first), urlsplit(second)
    return (a.scheme, a.netloc) == (b.scheme, b.netloc)


class IndexAssets(HTMLParser):
    def __init__(self, html, public):
        super().__init__(convert_charrefs=True)
        self.references = []
        self.base_href = None
        self.feed(html.decode("utf-8"))
        self.close()
        base = normalize_url(public + "/", self.base_href or "./")
        self.assets = [(kind, normalize_url(base, reference)) for kind, reference in self.references]

    def handle_starttag(self, tag, attrs):
        attributes = dict(attrs)
        if tag == "base" and self.base_href is None and attributes.get("href") is not None:
            self.base_href = attributes["href"]
        if tag == "script" and attributes.get("src"):
            self.references.append(("script", attributes["src"]))
        if tag == "link" and attributes.get("href"):
            relations = (attributes.get("rel") or "").lower().split()
            for kind in ("stylesheet", "modulepreload"):
                if kind in relations:
                    self.references.append((kind, attributes["href"]))


def check_index_assets(expected, actual, public):
    expected_assets, actual_assets = Counter(expected.assets), Counter(actual.assets)
    if expected_assets - actual_assets:
        raise ValueError("Public HTML is missing or has changed application asset references")
    # CDN scripts such as Cloudflare's external beacon may be inserted. New
    # same-origin scripts, stylesheets and modulepreloads must still match.
    for kind, url in actual_assets - expected_assets:
        if kind != "script" or same_origin(url, public):
            raise ValueError("Public HTML has unexpected application asset references")


def verify(backend, public, revision=None, index_file=None):
    public = normalize_url(public + "/", "./").rstrip("/")
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
    actual = IndexAssets(html, public)
    if index_file:
        check_index_assets(IndexAssets(Path(index_file).read_bytes(), public), actual, public)
    script_url = next((url for kind, url in actual.assets
                       if kind == "script" and same_origin(url, public)), None)
    if not script_url:
        raise ValueError("Public index does not reference a same-origin application script")
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
    parser.add_argument("--index-file", help="Local index.html whose application asset references must match")
    parser.add_argument("--attempts", type=int, default=12)
    parser.add_argument("--delay", type=float, default=3)
    args = parser.parse_args()
    if args.attempts < 1 or args.delay < 0:
        parser.error("attempts must be positive and delay must be non-negative")
    for attempt in range(args.attempts):
        try:
            verify(args.backend.rstrip("/"), args.public.rstrip("/"), args.revision, args.index_file)
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
