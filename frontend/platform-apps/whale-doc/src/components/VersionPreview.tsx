// 历史版本只读预览

import { EditorContent, useEditor } from '@tiptap/react';
import StarterKit from '@tiptap/starter-kit';
import Image from '@tiptap/extension-image';
import Link from '@tiptap/extension-link';
import Underline from '@tiptap/extension-underline';
import type { JSONContent } from '@tiptap/react';
import { parseDocJson } from '../utils/docContent';

/** 版本预览属性 */
interface VersionPreviewProps {
  /** 后端返回的正文 JSON 字符串 */
  content?: string | null;
}

export default function VersionPreview({ content }: VersionPreviewProps) {
  const parsed = parseDocJson(content) as JSONContent | null;
  const editor = useEditor({
    extensions: [StarterKit, Underline, Link.configure({ openOnClick: false }), Image],
    content: parsed ?? { type: 'doc', content: [{ type: 'paragraph' }] },
    editable: false,
  });

  return (
    <div className="doc-editor-content doc-readonly">
      <EditorContent editor={editor} />
    </div>
  );
}
