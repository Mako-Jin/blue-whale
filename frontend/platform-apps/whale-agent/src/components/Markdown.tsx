// 轻量 Markdown 渲染（无第三方依赖）：标题 / 段落 / 列表 / 引用 / 代码块 / 分隔线 +
// 行内粗体、行内代码、链接与 [S1] 引用角标。引用角标点击后由外部打开证据面板。

import type { ReactNode } from 'react';

interface MarkdownProps {
  /** Markdown 原文（回答正文，增量拼接） */
  content: string;
  /** 点击 [Sx] 引用角标 */
  onCitation?: (sourceId: string) => void;
  /** 当前高亮的引用编号 */
  activeCitation?: string | null;
}

type Block =
  | { type: 'code'; lang: string; text: string }
  | { type: 'heading'; level: number; text: string }
  | { type: 'list'; ordered: boolean; items: string[] }
  | { type: 'quote'; text: string }
  | { type: 'hr' }
  | { type: 'paragraph'; text: string };

const HEADING = /^(#{1,4})\s+(.*)$/;
const UNORDERED = /^\s*[-*+]\s+(.*)$/;
const ORDERED = /^\s*\d+\.\s+(.*)$/;
const HR = /^(-{3,}|\*{3,})$/;
const FENCE = /^```(.*)$/;

/** 判断某行是否开启新的块（用于结束段落累积） */
function startsBlock(line: string): boolean {
  return (
    FENCE.test(line) ||
    HEADING.test(line) ||
    UNORDERED.test(line) ||
    ORDERED.test(line) ||
    line.startsWith('>') ||
    HR.test(line)
  );
}

/** 按块解析 Markdown（仅支持工作台需要的子集） */
function parseBlocks(source: string): Block[] {
  const lines = source.replace(/\r\n/g, '\n').split('\n');
  const blocks: Block[] = [];
  let index = 0;

  while (index < lines.length) {
    const line = lines[index];
    if (!line.trim()) {
      index += 1;
      continue;
    }

    const fence = FENCE.exec(line);
    if (fence) {
      const lang = fence[1].trim();
      const code: string[] = [];
      index += 1;
      while (index < lines.length && !FENCE.test(lines[index])) {
        code.push(lines[index]);
        index += 1;
      }
      index += 1;
      blocks.push({ type: 'code', lang, text: code.join('\n') });
      continue;
    }

    const heading = HEADING.exec(line);
    if (heading) {
      blocks.push({ type: 'heading', level: heading[1].length, text: heading[2] });
      index += 1;
      continue;
    }

    if (HR.test(line.trim())) {
      blocks.push({ type: 'hr' });
      index += 1;
      continue;
    }

    if (line.startsWith('>')) {
      const quote: string[] = [];
      while (index < lines.length && lines[index].startsWith('>')) {
        quote.push(lines[index].replace(/^>\s?/, ''));
        index += 1;
      }
      blocks.push({ type: 'quote', text: quote.join('\n') });
      continue;
    }

    if (UNORDERED.test(line) || ORDERED.test(line)) {
      const ordered = ORDERED.test(line);
      const items: string[] = [];
      while (index < lines.length) {
        const current = lines[index];
        const matched = ordered ? ORDERED.exec(current) : UNORDERED.exec(current);
        if (!matched) {
          break;
        }
        items.push(matched[1]);
        index += 1;
      }
      blocks.push({ type: 'list', ordered, items });
      continue;
    }

    const paragraph: string[] = [];
    while (index < lines.length && lines[index].trim() && !(paragraph.length > 0 && startsBlock(lines[index]))) {
      paragraph.push(lines[index]);
      index += 1;
    }
    blocks.push({ type: 'paragraph', text: paragraph.join('\n') });
  }

  return blocks;
}

const INLINE = /(\*\*[^*]+\*\*|`[^`]+`|\[S\d+\]|\[[^\]]+\]\([^)]+\))/g;

/** 行内渲染：粗体 / 行内代码 / [Sx] 引用角标 / 链接 */
function renderInline(
  text: string,
  keyPrefix: string,
  onCitation?: (sourceId: string) => void,
  activeCitation?: string | null,
): ReactNode[] {
  const nodes: ReactNode[] = [];
  let cursor = 0;
  let match: RegExpExecArray | null;
  INLINE.lastIndex = 0;

  while ((match = INLINE.exec(text)) !== null) {
    if (match.index > cursor) {
      nodes.push(text.slice(cursor, match.index));
    }
    const token = match[0];
    const key = `${keyPrefix}-${match.index}`;

    if (token.startsWith('**')) {
      nodes.push(<strong key={key}>{token.slice(2, -2)}</strong>);
    } else if (token.startsWith('`')) {
      nodes.push(
        <code key={key} className="md-inline-code">
          {token.slice(1, -1)}
        </code>,
      );
    } else if (/^\[S\d+\]$/.test(token)) {
      const sourceId = token.slice(1, -1);
      nodes.push(
        <button
          key={key}
          type="button"
          className={`md-citation${activeCitation === sourceId ? ' md-citation-active' : ''}`}
          onClick={() => onCitation?.(sourceId)}
          title={`查看证据 ${sourceId}`}
        >
          {sourceId}
        </button>,
      );
    } else {
      const link = /^\[([^\]]+)\]\(([^)]+)\)$/.exec(token);
      nodes.push(
        <a key={key} href={link?.[2]} target="_blank" rel="noreferrer">
          {link?.[1]}
        </a>,
      );
    }
    cursor = match.index + token.length;
  }

  if (cursor < text.length) {
    nodes.push(text.slice(cursor));
  }
  return nodes;
}

export default function Markdown({ content, onCitation, activeCitation }: MarkdownProps) {
  const blocks = parseBlocks(content);

  return (
    <div className="md-body">
      {blocks.map((block, index) => {
        const key = `b${index}`;
        switch (block.type) {
          case 'code':
            return (
              <pre key={key} className="md-code" data-lang={block.lang}>
                <code>{block.text}</code>
              </pre>
            );
          case 'heading': {
            const Tag = (`h${Math.min(block.level + 1, 6)}` as unknown) as 'h2';
            return (
              <Tag key={key} className="md-heading">
                {renderInline(block.text, key, onCitation, activeCitation)}
              </Tag>
            );
          }
          case 'list': {
            const ListTag = block.ordered ? 'ol' : 'ul';
            return (
              <ListTag key={key} className="md-list">
                {block.items.map((item, itemIndex) => (
                  <li key={`${key}-${itemIndex}`}>
                    {renderInline(item, `${key}-${itemIndex}`, onCitation, activeCitation)}
                  </li>
                ))}
              </ListTag>
            );
          }
          case 'quote':
            return (
              <blockquote key={key} className="md-quote">
                {renderInline(block.text, key, onCitation, activeCitation)}
              </blockquote>
            );
          case 'hr':
            return <hr key={key} className="md-hr" />;
          default:
            return (
              <p key={key} className="md-paragraph">
                {renderInline(block.text, key, onCitation, activeCitation)}
              </p>
            );
        }
      })}
    </div>
  );
}
