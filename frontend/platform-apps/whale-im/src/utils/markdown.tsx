// 轻量 Markdown 渲染：仅覆盖 IM 常见语法，全部输出 React 元素，不使用 dangerouslySetInnerHTML
//
// 支持：标题（# ~ ###）、无序/有序列表、围栏代码块、行内代码、加粗、http(s) 链接。

import type { ReactNode } from 'react';

const INLINE_PATTERN = /(\*\*[^*]+\*\*|`[^`]+`|\[[^\]]+\]\((https?:\/\/[^)\s]+)\))/g;

/** 渲染行内标记 */
function renderInline(text: string, keyPrefix: string): ReactNode[] {
  const nodes: ReactNode[] = [];
  let lastIndex = 0;
  let match: RegExpExecArray | null;
  let index = 0;
  INLINE_PATTERN.lastIndex = 0;

  while ((match = INLINE_PATTERN.exec(text)) !== null) {
    if (match.index > lastIndex) {
      nodes.push(text.slice(lastIndex, match.index));
    }
    const token = match[0];
    const key = `${keyPrefix}-inline-${index}`;
    if (token.startsWith('**')) {
      nodes.push(<strong key={key}>{token.slice(2, -2)}</strong>);
    } else if (token.startsWith('`')) {
      nodes.push(<code key={key}>{token.slice(1, -1)}</code>);
    } else {
      const link = /^\[([^\]]+)\]\((https?:\/\/[^)\s]+)\)$/.exec(token);
      nodes.push(
        <a key={key} href={link?.[2]} target="_blank" rel="noreferrer">
          {link?.[1] ?? token}
        </a>,
      );
    }
    lastIndex = match.index + token.length;
    index += 1;
  }
  if (lastIndex < text.length) {
    nodes.push(text.slice(lastIndex));
  }
  return nodes;
}

/** 判断某行是否是块级语法开头（用于段落边界） */
function isBlockStart(line: string): boolean {
  return (
    line.startsWith('```') ||
    /^#{1,3}\s+/.test(line) ||
    /^[-*]\s+/.test(line) ||
    /^\d+\.\s+/.test(line)
  );
}

/** 将 Markdown 文本渲染为 React 节点 */
export function renderMarkdown(text: string): ReactNode {
  const lines = text.split('\n');
  const blocks: ReactNode[] = [];
  let cursor = 0;

  while (cursor < lines.length) {
    const line = lines[cursor];

    if (line.startsWith('```')) {
      const code: string[] = [];
      cursor += 1;
      while (cursor < lines.length && !lines[cursor].startsWith('```')) {
        code.push(lines[cursor]);
        cursor += 1;
      }
      cursor += 1;
      blocks.push(
        <pre key={`code-${blocks.length}`}>
          <code>{code.join('\n')}</code>
        </pre>,
      );
      continue;
    }

    const heading = /^(#{1,3})\s+(.*)$/.exec(line);
    if (heading) {
      const content = renderInline(heading[2], `h-${blocks.length}`);
      const key = `h-${blocks.length}`;
      if (heading[1].length === 1) {
        blocks.push(<h1 key={key}>{content}</h1>);
      } else if (heading[1].length === 2) {
        blocks.push(<h2 key={key}>{content}</h2>);
      } else {
        blocks.push(<h3 key={key}>{content}</h3>);
      }
      cursor += 1;
      continue;
    }

    if (/^[-*]\s+/.test(line)) {
      const items: ReactNode[] = [];
      while (cursor < lines.length && /^[-*]\s+/.test(lines[cursor])) {
        const content = lines[cursor].replace(/^[-*]\s+/, '');
        items.push(<li key={`li-${cursor}`}>{renderInline(content, `li-${cursor}`)}</li>);
        cursor += 1;
      }
      blocks.push(<ul key={`ul-${blocks.length}`}>{items}</ul>);
      continue;
    }

    if (/^\d+\.\s+/.test(line)) {
      const items: ReactNode[] = [];
      while (cursor < lines.length && /^\d+\.\s+/.test(lines[cursor])) {
        const content = lines[cursor].replace(/^\d+\.\s+/, '');
        items.push(<li key={`oli-${cursor}`}>{renderInline(content, `oli-${cursor}`)}</li>);
        cursor += 1;
      }
      blocks.push(<ol key={`ol-${blocks.length}`}>{items}</ol>);
      continue;
    }

    if (line.trim() === '') {
      cursor += 1;
      continue;
    }

    // 段落：连续非空、非块级语法行合并
    const paragraph: string[] = [];
    while (cursor < lines.length && lines[cursor].trim() !== '' && !isBlockStart(lines[cursor])) {
      paragraph.push(lines[cursor]);
      cursor += 1;
    }
    blocks.push(
      <p key={`p-${blocks.length}`}>{renderInline(paragraph.join('\n'), `p-${blocks.length}`)}</p>,
    );
  }

  return <>{blocks}</>;
}
