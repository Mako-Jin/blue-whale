// 单条消息：文本 / Markdown / 图片 / 文件 / 确认卡片 + 引用条 + 撤回 + 机器人操作条

import { useEffect, useState } from 'react';
import { App, Avatar, Button, Popconfirm, Space, Tag, Typography } from 'antd';
import {
  DislikeFilled,
  DislikeOutlined,
  DownloadOutlined,
  LikeFilled,
  LikeOutlined,
  RobotOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { createLogger } from '@bluewhale/whale-common-tools';
import type { AttachmentData, ConfirmationCardData, MessageVO } from '../api/im';
import { imApi } from '../api/im';
import { downloadFile, previewBlob } from '../api/file';
import { formatSize, formatTime, previewText } from '../utils/format';
import { renderMarkdown } from '../utils/markdown';
import ConfirmationCard from './ConfirmationCard';
import AgentTracePanel from './AgentTracePanel';

const logger = createLogger('whale-im › message');
const { Text } = Typography;

const FEEDBACK_KEY = 'whale_im_feedback';

interface MessageItemProps {
  message: MessageVO;
  isMe: boolean;
  senderName: string;
  replied?: MessageVO | null;
  runId?: string;
  onResolveRunId: (messageId: string) => Promise<string | undefined>;
  onRecall: (messageId: string) => void;
  onJumpTo: (messageId: string) => void;
}

type FeedbackValue = 'up' | 'down';

function readFeedback(): Record<string, FeedbackValue> {
  const raw = localStorage.getItem(FEEDBACK_KEY);
  if (!raw) {
    return {};
  }
  try {
    return JSON.parse(raw) as Record<string, FeedbackValue>;
  } catch {
    return {};
  }
}

function parseAttachment(content: string): AttachmentData | null {
  try {
    const data = JSON.parse(content) as AttachmentData;
    return data && data.fileId !== undefined ? data : null;
  } catch {
    return null;
  }
}

function parseCard(content: string): ConfirmationCardData | null {
  try {
    const data = JSON.parse(content) as ConfirmationCardData;
    return data && data.type === 'platform_op' ? data : null;
  } catch {
    return null;
  }
}

/** 图片消息：携带鉴权头拉取字节流后生成 objectURL */
function AttachmentImage({ fileId, name }: { fileId: string; name: string }) {
  const [url, setUrl] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    let objectUrl: string | undefined;
    previewBlob(fileId)
      .then((blob) => {
        if (cancelled) {
          return;
        }
        objectUrl = URL.createObjectURL(blob);
        setUrl(objectUrl);
      })
      .catch((error) => {
        logger.warn('图片预览失败', error);
      });
    return () => {
      cancelled = true;
      if (objectUrl) {
        URL.revokeObjectURL(objectUrl);
      }
    };
  }, [fileId]);

  if (!url) {
    return <Text type="secondary">图片加载中…</Text>;
  }
  return (
    <img
      className="im-image"
      src={url}
      alt={name}
      onClick={() => window.open(url, '_blank', 'noopener')}
    />
  );
}

/** 文件消息：按需下载 */
function AttachmentFile({ data }: { data: AttachmentData }) {
  const { message } = App.useApp();
  const [busy, setBusy] = useState(false);

  const handleDownload = async () => {
    setBusy(true);
    try {
      const result = await downloadFile(String(data.fileId));
      const url = URL.createObjectURL(result.blob);
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = result.filename || data.name;
      anchor.click();
      URL.revokeObjectURL(url);
    } catch (error) {
      logger.warn('附件下载失败', error);
      message.error('附件下载失败');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="im-file-card">
      <Space size={6} wrap>
        <Text>{data.name || '未命名文件'}</Text>
        <Text type="secondary" style={{ fontSize: 12 }}>
          {formatSize(data.size)}
        </Text>
      </Space>
      <Button
        size="small"
        type="link"
        icon={<DownloadOutlined />}
        loading={busy}
        onClick={() => {
          void handleDownload();
        }}
      >
        下载
      </Button>
    </div>
  );
}

/** 机器人反馈：优先写后端运行记录，失败时保留本地记录兜底 */
function BotFeedback({
  messageId,
  runId,
  onResolveRunId,
}: {
  messageId: string;
  runId?: string;
  onResolveRunId: (messageId: string) => Promise<string | undefined>;
}) {
  const [value, setValue] = useState<FeedbackValue | null>(() => readFeedback()[messageId] ?? null);
  const [busy, setBusy] = useState(false);

  const toggle = async (next: FeedbackValue) => {
    const store = readFeedback();
    if (store[messageId] === next) {
      delete store[messageId];
    } else {
      store[messageId] = next;
    }
    localStorage.setItem(FEEDBACK_KEY, JSON.stringify(store));
    setValue(store[messageId] ?? null);
    if (!store[messageId]) {
      return;
    }
    const effectiveRunId = runId ?? (await onResolveRunId(messageId));
    if (!effectiveRunId) {
      return;
    }
    setBusy(true);
    try {
      await imApi.submitFeedback(effectiveRunId, next === 'up' ? 'UP' : 'DOWN');
    } catch (error) {
      logger.warn('反馈提交失败，已保留本地记录', error);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Space size={4}>
      <Button
        size="small"
        type="text"
        aria-label="回答有帮助"
        disabled={busy}
        icon={value === 'up' ? <LikeFilled style={{ color: '#1677ff' }} /> : <LikeOutlined />}
        onClick={() => {
          void toggle('up');
        }}
      />
      <Button
        size="small"
        type="text"
        aria-label="回答无帮助"
        disabled={busy}
        icon={value === 'down' ? <DislikeFilled style={{ color: '#ff4d4f' }} /> : <DislikeOutlined />}
        onClick={() => {
          void toggle('down');
        }}
      />
    </Space>
  );
}

/** 消息内容渲染 */
function MessageContent({ message, runId }: { message: MessageVO; runId?: string }) {
  if (message.contentType === 'CARD') {
    const card = parseCard(message.content);
    if (!card) {
      return <Text>{message.content}</Text>;
    }
    return <ConfirmationCard card={card} runId={runId} />;
  }
  if (message.contentType === 'IMAGE') {
    const data = parseAttachment(message.content);
    return data ? (
      <AttachmentImage fileId={String(data.fileId)} name={data.name} />
    ) : (
      <Text>{message.content}</Text>
    );
  }
  if (message.contentType === 'FILE') {
    const data = parseAttachment(message.content);
    return data ? <AttachmentFile data={data} /> : <Text>{message.content}</Text>;
  }
  if (message.contentType === 'MARKDOWN') {
    return <>{renderMarkdown(message.content)}</>;
  }
  return <>{message.content}</>;
}

export default function MessageItem({
  message,
  isMe,
  senderName,
  replied,
  runId,
  onResolveRunId,
  onRecall,
  onJumpTo,
}: MessageItemProps) {
  if (message.status === 0) {
    return (
      <div className="im-message">
        <div className="im-message-body" style={{ maxWidth: '100%' }}>
          <Text type="secondary" style={{ fontSize: 12 }}>
            {senderName} 撤回了一条消息
          </Text>
        </div>
      </div>
    );
  }

  const isBot = message.senderType === 'BOT';

  return (
    <div
      className={`im-message${isMe ? ' is-me' : ''}${isBot ? ' is-bot' : ''}`}
      data-message-id={message.id}
    >
      <Avatar
        size="small"
        icon={isBot ? <RobotOutlined /> : <UserOutlined />}
        style={{ backgroundColor: isBot ? '#722ed1' : isMe ? '#1677ff' : '#13c2c2', flex: 'none' }}
      />
      <div className="im-message-body">
        <div className="im-message-meta">
          <span>{senderName}</span>
          <span>{formatTime(message.createTime)}</span>
          {message.contentType === 'MARKDOWN' && <Tag color="blue">Markdown</Tag>}
        </div>
        <div className="im-bubble">
          {replied && (
            <div
              className="im-reply-quote"
              onClick={() => onJumpTo(replied.id)}
              role="button"
              tabIndex={0}
              onKeyDown={(event) => {
                if (event.key === 'Enter') {
                  onJumpTo(replied.id);
                }
              }}
            >
              引用 {replied.senderType === 'BOT' ? '蓝鲸助手' : `用户 ${replied.senderId}`}：
              {previewText(replied)}
            </div>
          )}
          <MessageContent message={message} runId={runId} />
        </div>
        {isBot && (runId || message.contentType === 'MARKDOWN') && (
          <div className="im-bot-actions">
            <AgentTracePanel runId={runId} resolveRunId={() => onResolveRunId(message.id)} />
            <BotFeedback messageId={message.id} runId={runId} onResolveRunId={onResolveRunId} />
          </div>
        )}
        {isMe && (
          <div className="im-bot-actions" style={{ justifyContent: 'flex-end' }}>
            <Popconfirm
              title="撤回该消息？"
              okText="撤回"
              cancelText="取消"
              onConfirm={() => onRecall(message.id)}
            >
              <Button size="small" type="link">
                撤回
              </Button>
            </Popconfirm>
          </div>
        )}
      </div>
    </div>
  );
}
