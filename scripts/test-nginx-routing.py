#!/usr/bin/env python3
"""빌드한 프런트엔드와 운영과 같은 Nginx 1.24로 공개 경로를 확인한다."""
from pathlib import Path
import subprocess
import tempfile
import time
from urllib.error import HTTPError, URLError
from urllib.request import urlopen

ROOT = Path(__file__).resolve().parents[1]
CONFIG = '''server {
    listen 8081;
    location /api/ { default_type application/json; return 404 '{"source":"backend"}'; }
    location /oauth2/ { return 204; }
    location /login/oauth2/ { return 204; }
    location / { return 200 'post-proxy'; }
}
server {
    listen 80;
    root /usr/share/nginx/html;
    index index.html;
    location ~ ^/posts/[^/]+/?$ { proxy_pass http://127.0.0.1:8081; proxy_intercept_errors off; }
    location /api/ { proxy_pass http://127.0.0.1:8081; }
    location /oauth2/ { proxy_pass http://127.0.0.1:8081; }
    location /login/oauth2/ { proxy_pass http://127.0.0.1:8081; }
    include /etc/nginx/blog-spa-routes.conf;
}
'''


def command(*args):
    return subprocess.check_output(args, text=True).strip()


def response(origin, path):
    try:
        reply = urlopen(origin + path, timeout=5)
    except HTTPError as error:
        reply = error
    with reply:
        return reply.status, reply.read().decode()


def main():
    dist = ROOT / 'frontend/dist'
    if not (dist / '404.html').exists():
        raise SystemExit('먼저 frontend에서 npm run build를 실행하세요.')
    with tempfile.TemporaryDirectory(prefix='blog-nginx-test-') as temporary:
        config = Path(temporary) / 'site.conf'
        config.write_text(CONFIG)
        container = command('docker', 'run', '--rm', '-d', '-p', '127.0.0.1::80',
                            '-v', f'{dist}:/usr/share/nginx/html:ro',
                            '-v', f'{config}:/etc/nginx/conf.d/default.conf:ro',
                            '-v', f'{ROOT / "scripts/nginx/blog-spa-routes.conf"}:/etc/nginx/blog-spa-routes.conf:ro',
                            'nginx:1.24-alpine')
        try:
            origin = 'http://' + command('docker', 'port', container, '80/tcp')
            for attempt in range(40):
                try:
                    response(origin, '/')
                    break
                except (URLError, ConnectionError):
                    if attempt == 39:
                        raise
                    time.sleep(0.1)
            valid = ['/', '/posts', '/posts/', '/posts?page=2&category=3', '/posts?q=검색',
                     '/login', '/oauth/callback?result=success', '/settings', '/privacy/', '/terms',
                     '/admin', '/admin/login', '/admin/dashboard', '/admin/menus', '/admin/posts',
                     '/admin/posts/new', '/admin/posts/42', '/admin/images', '/admin/categories',
                     '/admin/comments', '/admin/members', '/admin/codes', '/admin/home']
            invalid = ['/missing', '/missing/nested', '/posts/6/extra', '/admin/missing',
                       '/admin/posts/42/extra', '/assets/missing.js', '/missing.css', '/404.html']
            for path in valid:
                status, html = response(origin, path.replace('검색', '%EA%B2%80%EC%83%89'))
                assert status == 200 and '<div id="app"></div>' in html, (path, status)
                assert 'content="noindex,follow"' not in html, path
            for path in invalid:
                status, html = response(origin, path)
                assert status == 404 and 'content="noindex,follow"' in html, (path, status)
                assert '<div id="app"></div>' in html and 'rel="canonical"' not in html, path
            assert response(origin, '/posts/6') == (200, 'post-proxy')
            assert response(origin, '/api/missing') == (404, '{"source":"backend"}')
            assert response(origin, '/oauth2/authorization/google')[0] == 204
            assert response(origin, '/login/oauth2/code/google')[0] == 204
            asset = next((dist / 'assets').glob('index-*.js'))
            assert response(origin, f'/assets/{asset.name}')[0] == 200
            print(f'Nginx 경로 검증 통과: 정상 {len(valid)}, 404 {len(invalid)}, 기존 프록시·정적 파일 5건')
        finally:
            command('docker', 'stop', container)


if __name__ == '__main__':
    main()
