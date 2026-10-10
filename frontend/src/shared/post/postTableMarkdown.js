/**
 * 표의 저장 형식. GitHub 표(GFM)를 그대로 쓴다 — 다른 마크다운 도구로 옮겨도 표로 읽힌다.
 *
 *   | 이름 | 값 |
 *   | --- | :---: |
 *   | a | 1 |
 *
 * 제목 행: GFM 표는 첫 줄이 늘 제목 행이다. 노션처럼 제목 행을 끈 표는 첫 줄을 빈 칸으로 두고
 * 실제 내용은 둘째 줄부터 쓴다. 읽을 때 첫 줄이 모두 비어 있으면 제목 행이 없는 표로 본다.
 *
 * 칸 안의 | 는 \| 로 적는다. 칸 안의 줄바꿈은 GFM 표가 담지 못해 띄어쓰기로 바꾼다.
 */

const DELIMITER_CELL = /^:?-+:?$/;

/** 표 한 줄을 칸으로 나눈다. 앞뒤 | 는 있어도 없어도 된다 */
export function splitTableRow(line) {
  let text = line.trim();

  if (text.startsWith('|')) {
    text = text.slice(1);
  }

  if (text.endsWith('|') && !text.endsWith('\\|')) {
    text = text.slice(0, -1);
  }

  const cells = [];
  let cell = '';

  for (let index = 0; index < text.length; index += 1) {
    if (text[index] === '\\' && text[index + 1] === '|') {
      cell += '|';
      index += 1;
    } else if (text[index] === '|') {
      cells.push(cell.trim());
      cell = '';
    } else {
      cell += text[index];
    }
  }

  cells.push(cell.trim());

  return cells;
}

function readAlign(cell) {
  if (cell.startsWith(':') && cell.endsWith(':')) {
    return 'center';
  }

  if (cell.endsWith(':')) {
    return 'right';
  }

  return cell.startsWith(':') ? 'left' : '';
}

/** lines[index] 에서 표가 시작하는지. 제목 줄 다음 줄이 같은 칸 수의 --- 줄이어야 한다 */
export function isTableStart(lines, index) {
  const head = lines[index] ?? '';
  const delimiter = lines[index + 1] ?? '';

  if (!head.includes('|') || !delimiter.includes('-')) {
    return false;
  }

  const marks = splitTableRow(delimiter);

  return marks.every((mark) => DELIMITER_CELL.test(mark)) && marks.length === splitTableRow(head).length;
}

/**
 * lines[index] 부터 표 하나를 읽는다. 빈 줄이나 | 가 없는 줄에서 끝난다.
 *
 * @returns {{ rows: string[][], align: string[], header: boolean, end: number }}
 *          rows 는 화면에 보이는 줄들이다(제목 행이 있으면 첫 줄이 제목 행). end 는 표의 마지막 줄 번호
 */
export function readTable(lines, index) {
  const head = splitTableRow(lines[index]);
  const columns = head.length;
  const align = splitTableRow(lines[index + 1]).map(readAlign);
  const body = [];
  let end = index + 1;

  while (end + 1 < lines.length && lines[end + 1].trim() !== '' && lines[end + 1].includes('|')) {
    end += 1;
    // 칸 수는 제목 줄을 따른다. 모자라면 빈 칸을 채우고 넘치면 버린다 (GFM 과 같다)
    const cells = splitTableRow(lines[end]).slice(0, columns);
    body.push([...cells, ...Array(columns - cells.length).fill('')]);
  }

  const header = head.some((cell) => cell !== '');

  return { rows: header ? [head, ...body] : body, align, header, end };
}

function writeCell(text) {
  return String(text ?? '').replace(/\n+/g, ' ').replace(/\|/g, '\\|').trim();
}

const ALIGN_MARKS = { left: ':---', center: ':---:', right: '---:' };

/** 표 → GFM 표 문자열 */
export function writeTable({ rows, align = [], header = true }) {
  const columns = Math.max(1, ...rows.map((row) => row.length));
  const line = (cells) => `| ${Array.from({ length: columns }, (_, column) => writeCell(cells[column])).join(' | ')} |`;
  const head = header ? rows[0] ?? [] : [];
  const body = header ? rows.slice(1) : rows;
  const marks = Array.from({ length: columns }, (_, column) => ALIGN_MARKS[align[column]] ?? '---');

  return [line(head), `| ${marks.join(' | ')} |`, ...body.map(line)].join('\n');
}
