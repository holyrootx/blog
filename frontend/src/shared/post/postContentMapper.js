export function toPostBody(content) {
  const source = String(content ?? '').trim();

  if (!source) {
    return [];
  }

  const blocks = [];
  let paragraphLines = [];
  let headingIndex = 1;

  function flushParagraph() {
    if (paragraphLines.length === 0) {
      return;
    }

    blocks.push({
      type: 'paragraph',
      text: paragraphLines.join(' '),
    });
    paragraphLines = [];
  }

  for (const line of source.split(/\r?\n/)) {
    const trimmedLine = line.trim();

    if (!trimmedLine) {
      flushParagraph();
      continue;
    }

    const heading = trimmedLine.match(/^#{1,3}\s+(.+)$/);
    if (heading) {
      flushParagraph();
      blocks.push({
        type: 'heading',
        id: `section-${headingIndex}`,
        text: heading[1],
      });
      headingIndex += 1;
      continue;
    }

    if (trimmedLine.startsWith('> ')) {
      flushParagraph();
      blocks.push({
        type: 'quote',
        text: trimmedLine.replace(/^>\s*/, ''),
      });
      continue;
    }

    paragraphLines.push(trimmedLine);
  }

  flushParagraph();
  return blocks;
}

export function toPostToc(body) {
  return body
    .filter((block) => block.type === 'heading')
    .map((block) => ({
      id: block.id,
      text: block.text,
    }));
}
