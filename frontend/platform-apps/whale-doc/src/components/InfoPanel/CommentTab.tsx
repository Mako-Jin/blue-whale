// 评论页签：评论列表、回复、解决与删除

import { useCallback, useEffect, useState } from 'react';
import { App, Button, Divider, Empty, Input, List, Space, Spin, Tag, Typography } from 'antd';
import { DeleteOutlined, SendOutlined } from '@ant-design/icons';
import { commentApi } from '../../api/comment';
import { getCurrentUser, hasPermission } from '../../api/client';
import type { DocComment, DocPage } from '../../api/types';
import { formatTime } from '../../utils/docContent';

const { Text, Paragraph } = Typography;

/** 评论页签 */
export default function CommentTab({ page, onRead }: { page: DocPage; onRead: () => void }) {
  const { message, modal } = App.useApp();
  const [comments, setComments] = useState<DocComment[]>([]);
  const [loading, setLoading] = useState(false);
  const [content, setContent] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [replyTo, setReplyTo] = useState<DocComment | null>(null);
  const currentUserId = getCurrentUser()?.userId;

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setComments(await commentApi.list(page.id));
    } catch (error) {
      message.error((error as Error).message);
    } finally {
      setLoading(false);
    }
  }, [page.id, message]);

  useEffect(() => {
    void load();
    // 打开评论页签视为已读，清除角标（M2-7）
    commentApi.markRead(page.id).then(() => onRead()).catch(() => undefined);
  }, [load, page.id, onRead]);

  /** 便捷插入 @ 用户ID（后端从正文解析提及） */
  const insertMention = () => {
    const raw = window.prompt('输入要 @ 的用户ID');
    if (!raw || !/^\d+$/.test(raw.trim())) {
      return;
    }
    setContent((value) => `${value}@${raw.trim()} `);
  };

  const submit = async () => {
    if (!content.trim()) {
      return;
    }
    setSubmitting(true);
    try {
      await commentApi.create(page.id, {
        content: content.trim(),
        parentId: replyTo?.id,
      });
      setContent('');
      message.success(replyTo ? '回复已发布' : '评论已发布');
      setReplyTo(null);
      await load();
    } catch (error) {
      message.error((error as Error).message);
    } finally {
      setSubmitting(false);
    }
  };

  const canModify = (comment: DocComment) =>
    String(comment.authorId) === String(currentUserId ?? '')
    || (page.canManage && hasPermission('sys:doc:comment:manage'));
  const canComment = page.canComment && hasPermission('sys:doc:comment:add');

  const toggleResolve = async (comment: DocComment, resolved: boolean) => {
    try {
      await commentApi.resolve(comment.id, resolved);
      await load();
    } catch (error) {
      message.error((error as Error).message);
    }
  };

  const remove = (comment: DocComment) => {
    modal.confirm({
      title: '删除该评论？',
      content: '主评论下的回复会一并删除。',
      okText: '删除',
      okButtonProps: { danger: true },
      onOk: async () => {
        await commentApi.remove(comment.id);
        message.success('评论已删除');
        await load();
      },
    });
  };

  const renderComment = (comment: DocComment, nested = false) => (
    <List.Item
      key={comment.id}
      actions={[
        !nested && canComment && (
          <Button key="reply" type="link" size="small" onClick={() => setReplyTo(comment)}>
            回复
          </Button>
        ),
        ...(canModify(comment)
          ? [
              <Button key="resolve" type="link" size="small"
                onClick={() => void toggleResolve(comment, !comment.resolved)}>
                {comment.resolved ? '重新打开' : '标记解决'}
              </Button>,
              <Button key="delete" type="link" size="small" danger icon={<DeleteOutlined />}
                onClick={() => remove(comment)} />,
            ]
          : []),
      ].filter(Boolean)}
    >
      <List.Item.Meta
        title={
          <Space>
            <Text strong>用户 {comment.authorId}</Text>
            {comment.resolved && <Tag color="green">已解决</Tag>}
            <Text type="secondary">{formatTime(comment.createTime)}</Text>
          </Space>
        }
        description={
          <Space direction="vertical" size={4} style={{ width: '100%' }}>
            <Paragraph style={{ margin: 0 }}>{comment.content}</Paragraph>
            {!nested && comment.replies.length > 0 && (
              <div style={{ paddingInlineStart: 16, borderInlineStart: '2px solid #f0f0f0' }}>
                {comment.replies.map((reply) => renderComment(reply, true))}
              </div>
            )}
          </Space>
        }
      />
    </List.Item>
  );

  return (
    <div>
      {!page.canComment && <Text type="secondary">加入空间后可参与评论。</Text>}
      <Spin spinning={loading}>
        <List
          dataSource={comments}
          locale={{ emptyText: <Empty description="暂无评论" /> }}
          renderItem={(comment) => renderComment(comment)}
        />
      </Spin>
      <Divider />
      {replyTo && (
        <Space style={{ marginBottom: 8 }}>
          <Text type="secondary">回复用户 {replyTo.authorId}</Text>
          <Button size="small" type="link" onClick={() => setReplyTo(null)}>
            取消回复
          </Button>
        </Space>
      )}
      <Space.Compact style={{ width: '100%' }}>
        <Input.TextArea
          rows={2}
          maxLength={2000}
          value={content}
          disabled={!canComment}
          onChange={(event) => setContent(event.target.value)}
          placeholder={replyTo ? '写下回复…' : '写下评论，支持 @用户ID 提及'}
        />
        <Button type="text" disabled={!canComment} onClick={insertMention}>
          @提及
        </Button>
        <Button type="primary" icon={<SendOutlined />} loading={submitting} disabled={!canComment}
          onClick={() => void submit()}>
          发送
        </Button>
      </Space.Compact>
    </div>
  );
}
