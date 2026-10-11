import { readFile, writeFile } from 'node:fs/promises';

// 현재 빌드의 JS·CSS를 그대로 사용하되, 첫 응답부터 404 페이지임을 알린다.
const index = await readFile(new URL('../dist/index.html', import.meta.url), 'utf8');
const block = /<!-- blog-meta:start -->[\s\S]*?<!-- blog-meta:end -->/;
if (!block.test(index)) throw new Error('Frontend index is missing the blog metadata block');
const html = index.replace(block, `<!-- blog-meta:start -->
    <title>페이지를 찾을 수 없습니다 · 정성주의 기록</title>
    <meta name="robots" content="noindex,follow" />
    <meta name="description" content="요청한 페이지가 없거나 주소가 변경되었습니다." />
    <!-- blog-meta:end -->`);
await writeFile(new URL('../dist/404.html', import.meta.url), html);
