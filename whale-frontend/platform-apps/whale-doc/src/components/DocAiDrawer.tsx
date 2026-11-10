// 文档 AI 助手抽屉：基于文档知识库回答，支持摘要/要点等快捷问题与引用跳转

import { useEffect, useRef, useState } from 'react';
import { Alert, Button, Drawer, Empty, Input, Space, Spin, Tag, Typography } from 'antd';
import { SendOutlined } from '@ant-design/icons';
import { Link } from 'react-router-dom';
import { askDocAssistant, type AssistantCitation } from '../api/assistant';

const { Paragraph, Text } = Typography;

/** 快捷问题 */
const QUICK_PROMPTS = ['总结这篇文档', '列出关键要点', '这篇文档和相关文档有什么联系？'];

export interface DocAiDrawerProps {
  /** 是否打开 */
  open: boolean;
  /** 关闭回调 */
  onClose: () => void;
  /** 当前页面标题（用于聚焦上下文） */
  pageTitle: string;
}

export default function DocAiDrawer({ open, onClose, pageTitle }: DocAiDrawerProps) {
  const [question, setQuestion] = useState('');
  const [answer, setAnswer] = useState('');
  const [citations, setCitations] = useState<AssistantCitation[]>([]);
  const [streaming, setStreaming] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const abortRef = useRef<AbortController | null>(null);

  useEffect(() => {
    if (!open && abortRef.current) {
      abortRef.current.abort();
      abortRef.current = null;
      setStreaming(false);
    }
  }, [open]);

  useEffect(() => () => abortRef.current?.abort(), []);

  /**
   * 发起提问：自动拼接页面标题，回答按 SSE 增量追加。
   *
   * @param prompt 用户问题（快捷问题或输入框内容）
   */
  const ask = async (prompt: string) => {
    const text = prompt.trim();
    if (!text || streaming) {
      return;
    }
    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;
    setStreaming(true);
    setError(null);
    setAnswer('');
    setCitations([]);
    try {
      await askDocAssistant(
        `关于文档《${pageTitle}》：${text}`,
        {
          onMeta: (meta) => setCitations(meta.citations ?? []),
          onDelta: (delta) => setAnswer((prev) => prev + delta),
          onDone: (done) => {
            if (done.citations && done.citations.length > 0) {
              setCitations(done.citations);
            }
          },
          onError: (message) => setError(message),
        },
        controller.signal,
      );
    } catch (err) {
      if ((err as Error).name !== 'AbortError') {
        setError((err as Error).message);
      }
    } finally {
      if (abortRef.current === controller) {
        abortRef.current = null;
        setStreaming(false);
      }
    }
  };

  const stop = () => {
    abortRef.current?.abort();
    abortRef.current = null;
    setStreaming(false);
  };

  return (
    <Drawer width={460} title="AI 文档助手" open={open} onClose={onClose} destroyOnHidden={false}>
      <Space direction="vertical" size={12} style={{ width: '100%' }}>
        <Alert
          type="info"
          showIcon
          message="基于已发布的公开文档知识库回答，结果可能引用其他文档；不会读取草稿或私有空间内容。"
        />
        <Input.TextArea
          value={question}
          onChange={(event) => setQuestion(event.target.value)}
          placeholder={`向 AI 提问《${pageTitle}》相关内容`}
          autoSize={{ minRows: 3, maxRows: 6 }}
          onPressEnter={(event) => {
            if (!event.shiftKey) {
              event.preventDefault();
              void ask(question);
            }
          }}
        />
        <Space wrap size={8}>
          {QUICK_PROMPTS.map((prompt) => (
            <Button key={prompt} size="small" onClick={() => void ask(prompt)} disabled={streaming}>
              {prompt}
            </Button>
          ))}
        </Space>
        <Space>
          <Button
            type="primary"
            icon={<SendOutlined />}
            loading={streaming}
            disabled={!question.trim() && !streaming}
            onClick={() => void ask(question)}
          >
            提问
          </Button>
          {streaming && <Button onClick={stop}>停止</Button>}
        </Space>
        {error && <Alert type="error" showIcon message={error} />}
        {streaming && !answer && (
          <Space>
            <Spin size="small" />
            <Text type="secondary">正在检索文档知识库并生成回答…</Text>
          </Space>
        )}
        {answer && (
          <div className="doc-ai-answer">
            <Paragraph style={{ whiteSpace: 'pre-wrap', marginBottom: 0 }}>{answer}</Paragraph>
          </div>
        )}
        {citations.length > 0 && (
          <div>
            <Text strong>引用</Text>
            <ul className="doc-ai-citations">
              {citations.map((citation) => (
                <li key={citation.sourceId}>
                  <Tag color="blue">{citation.sourceId}</Tag>
                  {citation.url ? (
                    <Link to={citation.url}>{citation.title || citation.path}</Link>
                  ) : (
                    <Text>{citation.title || citation.path}</Text>
                  )}
                  {citation.snippet && (
                    <Paragraph type="secondary" ellipsis={{ rows: 2 }} style={{ marginBottom: 0 }}>
                      {citation.snippet}
                    </Paragraph>
                  )}
                </li>
              ))}
            </ul>
          </div>
        )}
        {!answer && !streaming && citations.length === 0 && (
          <Empty description="输入问题或点击快捷问题开始" image={Empty.PRESENTED_IMAGE_SIMPLE} />
        )}
      </Space>
    </Drawer>
  );
}
