// 登录内分享页面（M3）：只读渲染分享链接对应的页面正文

import { useEffect, useState } from 'react';
import { Alert, Card, Space, Spin, Tag, Typography } from 'antd';
import { LinkOutlined } from '@ant-design/icons';
import { EditorContent, useEditor } from '@tiptap/react';
import StarterKit from '@tiptap/starter-kit';
import Link from '@tiptap/extension-link';
import Underline from '@tiptap/extension-underline';
import { useParams } from 'react-router-dom';
import { shareApi } from '../api/share';
import type { DocPage } from '../api/types';
import { formatTime, parseDocJson } from '../utils/docContent';

const { Text, Title } = Typography;

export default function SharedPage() {
  const { token = '' } = useParams();
  const [page, setPage] = useState<DocPage | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const editor = useEditor({
    extensions: [StarterKit, Underline, Link.configure({ openOnClick: true })],
    content: { type: 'doc', content: [] },
    editable: false,
  });

  useEffect(() => {
    if (!editor) {
      return;
    }
    void (async () => {
      setLoading(true);
      try {
        const loaded = await shareApi.resolve(token);
        setPage(loaded);
        editor.commands.setContent(parseDocJson(loaded.contentJson) ?? { type: 'doc', content: [] });
      } catch (err) {
        setError((err as Error).message);
      } finally {
        setLoading(false);
      }
    })();
  }, [token, editor]);

  if (loading) {
    return (
      <div style={{ padding: 48, textAlign: 'center' }}>
        <Spin />
      </div>
    );
  }

  if (error) {
    return <Alert type="error" showIcon message="分享链接不可用" description={error} />;
  }

  if (!page) {
    return <Alert type="warning" showIcon message="页面不存在或无权访问" />;
  }

  return (
    <Card
      style={{ maxWidth: 980, margin: '0 auto' }}
      title={
        <Space>
          <LinkOutlined />
          <Text type="secondary">来自分享链接</Text>
        </Space>
      }
    >
      <Title level={2} style={{ marginTop: 0 }}>
        {page.title}
      </Title>
      <Space size={8} wrap style={{ marginBottom: 16 }}>
        <Tag color="blue">只读分享</Tag>
        {page.spaceName && <Tag>{page.spaceName}</Tag>}
        {page.updateTime && <Text type="secondary">更新于 {formatTime(page.updateTime)}</Text>}
        {page.archiveItemId && <Tag color="purple">已入档</Tag>}
      </Space>
      <div className="doc-shared-content">
        <EditorContent editor={editor} />
      </div>
    </Card>
  );
}
