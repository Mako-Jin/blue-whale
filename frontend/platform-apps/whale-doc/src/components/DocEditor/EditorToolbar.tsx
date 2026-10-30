// 编辑器工具栏：撤销/重做、标题、行内格式、列表、引用、链接与图片

import { Button, Divider, Space, Tooltip } from 'antd';
import {
  BoldOutlined,
  CodeOutlined,
  DeleteOutlined,
  ItalicOutlined,
  LinkOutlined,
  OrderedListOutlined,
  PictureOutlined,
  RedoOutlined,
  StrikethroughOutlined,
  UnderlineOutlined,
  UndoOutlined,
  UnorderedListOutlined,
} from '@ant-design/icons';
import type { Editor } from '@tiptap/react';

/** 编辑器工具栏 */
export default function EditorToolbar({ editor }: { editor: Editor | null }) {
  if (!editor) {
    return null;
  }
  const setLink = () => {
    const previous = editor.getAttributes('link').href as string | undefined;
    const url = window.prompt('链接地址（留空移除链接）', previous ?? 'https://');
    if (url === null) {
      return;
    }
    if (!url) {
      editor.chain().focus().extendMarkRange('link').unsetLink().run();
      return;
    }
    editor.chain().focus().extendMarkRange('link').setLink({ href: url }).run();
  };
  const setImage = () => {
    const url = window.prompt('图片地址');
    if (url) {
      editor.chain().focus().setImage({ src: url }).run();
    }
  };
  return (
    <Space wrap size={2} className="doc-editor-toolbar">
      <Tooltip title="撤销">
        <Button size="small" type="text" icon={<UndoOutlined />} disabled={!editor.can().undo()}
          aria-label="撤销" onClick={() => editor.chain().focus().undo().run()} />
      </Tooltip>
      <Tooltip title="重做">
        <Button size="small" type="text" icon={<RedoOutlined />} disabled={!editor.can().redo()}
          aria-label="重做" onClick={() => editor.chain().focus().redo().run()} />
      </Tooltip>
      <Divider type="vertical" />
      <Button size="small" type={editor.isActive('heading', { level: 1 }) ? 'primary' : 'text'}
        onClick={() => editor.chain().focus().toggleHeading({ level: 1 }).run()}>H1</Button>
      <Button size="small" type={editor.isActive('heading', { level: 2 }) ? 'primary' : 'text'}
        onClick={() => editor.chain().focus().toggleHeading({ level: 2 }).run()}>H2</Button>
      <Button size="small" type={editor.isActive('heading', { level: 3 }) ? 'primary' : 'text'}
        onClick={() => editor.chain().focus().toggleHeading({ level: 3 }).run()}>H3</Button>
      <Divider type="vertical" />
      <Button size="small" type={editor.isActive('bold') ? 'primary' : 'text'} icon={<BoldOutlined />}
        aria-label="加粗" onClick={() => editor.chain().focus().toggleBold().run()} />
      <Button size="small" type={editor.isActive('italic') ? 'primary' : 'text'} icon={<ItalicOutlined />}
        aria-label="斜体" onClick={() => editor.chain().focus().toggleItalic().run()} />
      <Button size="small" type={editor.isActive('underline') ? 'primary' : 'text'} icon={<UnderlineOutlined />}
        aria-label="下划线" onClick={() => editor.chain().focus().toggleUnderline().run()} />
      <Button size="small" type={editor.isActive('strike') ? 'primary' : 'text'} icon={<StrikethroughOutlined />}
        aria-label="删除线" onClick={() => editor.chain().focus().toggleStrike().run()} />
      <Divider type="vertical" />
      <Button size="small" type={editor.isActive('bulletList') ? 'primary' : 'text'}
        icon={<UnorderedListOutlined />} aria-label="无序列表"
        onClick={() => editor.chain().focus().toggleBulletList().run()} />
      <Button size="small" type={editor.isActive('orderedList') ? 'primary' : 'text'}
        icon={<OrderedListOutlined />} aria-label="有序列表"
        onClick={() => editor.chain().focus().toggleOrderedList().run()} />
      <Button size="small" type={editor.isActive('blockquote') ? 'primary' : 'text'}
        onClick={() => editor.chain().focus().toggleBlockquote().run()}>引用</Button>
      <Button size="small" type={editor.isActive('codeBlock') ? 'primary' : 'text'} icon={<CodeOutlined />}
        aria-label="代码块" onClick={() => editor.chain().focus().toggleCodeBlock().run()} />
      <Divider type="vertical" />
      <Button size="small" type={editor.isActive('link') ? 'primary' : 'text'} icon={<LinkOutlined />}
        aria-label="链接" onClick={setLink} />
      <Button size="small" type="text" icon={<PictureOutlined />} aria-label="插入图片" onClick={setImage} />
      <Button size="small" type="text" icon={<UnorderedListOutlined />} aria-label="分隔线"
        onClick={() => editor.chain().focus().setHorizontalRule().run()} />
      <Button size="small" type="text" icon={<DeleteOutlined />} danger aria-label="清除格式"
        onClick={() => editor.chain().focus().clearNodes().run()} />
    </Space>
  );
}
