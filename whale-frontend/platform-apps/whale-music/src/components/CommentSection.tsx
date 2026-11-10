// 歌曲评论区：发表 / 回复 / 分页（M4-1）
// 评论正文按纯文本渲染（React 默认转义，不使用 HTML 注入），XSS 不执行。

import { App, Button, Card, Empty, Input, List, Pagination, Space, Spin, Typography } from 'antd';
import { useCallback, useEffect, useState } from 'react';
import { pageComments, submitComment, type CommentView } from '../api/music';

const PAGE_SIZE = 10;
const MAX_CONTENT_LENGTH = 500;

interface CommentSectionProps {
  songId: number;
}

interface EditorState {
  parentId: number;
  placeholder: string;
}

export default function CommentSection({ songId }: CommentSectionProps) {
  const { message } = App.useApp();
  const [comments, setComments] = useState<CommentView[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [loading, setLoading] = useState(true);
  const [content, setContent] = useState('');
  const [editor, setEditor] = useState<EditorState | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const load = useCallback(
    (page: number) => {
      setLoading(true);
      pageComments(songId, { current: page, size: PAGE_SIZE })
        .then((result) => {
          setComments(result.records);
          setTotal(result.total);
          setCurrent(page);
        })
        .catch((error) => message.error(error instanceof Error ? error.message : '评论加载失败'))
        .finally(() => setLoading(false));
    },
    [message, songId],
  );

  useEffect(() => {
    load(1);
  }, [load]);

  const doSubmit = (text: string, parentId: number, onSuccess: () => void) => {
    const trimmed = text.trim();
    if (!trimmed) {
      message.warning('评论内容不能为空');
      return;
    }
    if (trimmed.length > MAX_CONTENT_LENGTH) {
      message.warning('评论不能超过 500 个字符');
      return;
    }
    setSubmitting(true);
    submitComment(songId, trimmed, parentId)
      .then(() => {
        message.success('评论已发表');
        onSuccess();
        load(1);
      })
      .catch((error) => message.error(error instanceof Error ? error.message : '评论发表失败'))
      .finally(() => setSubmitting(false));
  };

  const renderEditor = (state: EditorState, onClose: () => void) => {
    const ReplyEditor = () => {
      const [text, setText] = useState('');
      return (
        <Space direction="vertical" style={{ width: '100%' }} size={8}>
          <Input.TextArea
            rows={2}
            maxLength={MAX_CONTENT_LENGTH}
            showCount
            placeholder={state.placeholder}
            value={text}
            onChange={(event) => setText(event.target.value)}
          />
          <Space>
            <Button
              type="primary"
              size="small"
              loading={submitting}
              onClick={() =>
                doSubmit(text, state.parentId, () => {
                  setText('');
                  onClose();
                })
              }
            >
              发表
            </Button>
            <Button size="small" onClick={onClose}>
              取消
            </Button>
          </Space>
        </Space>
      );
    };
    return <ReplyEditor />;
  };

  const renderComment = (comment: CommentView, isReply: boolean) => (
    <div className={isReply ? 'music-comment music-comment-reply' : 'music-comment'}>
      <Space direction="vertical" size={4} style={{ width: '100%' }}>
        <Space size={8}>
          <Typography.Text strong>用户 {comment.userId}</Typography.Text>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {comment.createTime ?? ''}
          </Typography.Text>
        </Space>
        <Typography.Paragraph style={{ marginBottom: 0, whiteSpace: 'pre-wrap' }}>
          {comment.content}
        </Typography.Paragraph>
        {!isReply && (
          <Button
            type="link"
            size="small"
            style={{ padding: 0 }}
            onClick={() =>
              setEditor({ parentId: comment.id, placeholder: `回复 用户 ${comment.userId}` })
            }
          >
            回复
          </Button>
        )}
        {editor?.parentId === comment.id && renderEditor(editor, () => setEditor(null))}
        {comment.replies && comment.replies.length > 0 && (
          <div className="music-comment-replies">
            {comment.replies.map((reply) => (
              <div key={reply.id}>{renderComment(reply, true)}</div>
            ))}
          </div>
        )}
      </Space>
    </div>
  );

  return (
    <Card title={`评论（${total}）`}>
      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        <Space direction="vertical" size={8} style={{ width: '100%' }}>
          <Input.TextArea
            rows={3}
            maxLength={MAX_CONTENT_LENGTH}
            showCount
            placeholder="写下你的评论（纯文本，≤500 字）"
            value={content}
            onChange={(event) => setContent(event.target.value)}
          />
          <Button
            type="primary"
            loading={submitting}
            onClick={() => doSubmit(content, 0, () => setContent(''))}
          >
            发表评论
          </Button>
        </Space>
        {loading ? (
          <div style={{ textAlign: 'center', padding: 24 }}>
            <Spin />
          </div>
        ) : comments.length === 0 ? (
          <Empty description="暂无评论，来抢沙发" />
        ) : (
          <>
            <List
              dataSource={comments}
              renderItem={(comment) => (
                <List.Item key={comment.id}>{renderComment(comment, false)}</List.Item>
              )}
            />
            {total > PAGE_SIZE && (
              <Pagination
                current={current}
                total={total}
                pageSize={PAGE_SIZE}
                onChange={load}
                showSizeChanger={false}
              />
            )}
          </>
        )}
      </Space>
    </Card>
  );
}
