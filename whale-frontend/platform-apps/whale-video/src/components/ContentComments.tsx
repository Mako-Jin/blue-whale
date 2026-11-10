// 评论抽屉：分页加载、发表评论、点赞；私密或已下架内容由后端返回 commentable=false

import { App, Button, Drawer, Empty, Input, List, Space, Spin, Typography } from 'antd';
import { useEffect, useState } from 'react';
import {
  fetchComments,
  likeComment,
  submitComment,
  type CommentView,
} from '../api/video';
import { formatCount, formatDateTime } from '../utils/videoDisplay';

export interface ContentCommentsProps {
  contentId: number | null;
  open: boolean;
  commentable?: boolean;
  onClose: () => void;
}

const PAGE_SIZE = 20;

/** 递归展开一级回复，便于平铺展示 */
function flattenReplies(comment: CommentView): CommentView[] {
  return comment.replies?.length ? comment.replies : [];
}

export default function ContentComments({ contentId, open, commentable, onClose }: ContentCommentsProps) {
  const { message } = App.useApp();
  const [records, setRecords] = useState<CommentView[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(false);
  const [body, setBody] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const load = (nextPage: number, append: boolean) => {
    if (!contentId) {
      return;
    }
    setLoading(true);
    fetchComments(contentId, { current: nextPage, size: PAGE_SIZE })
      .then((result) => {
        setRecords((previous) => (append ? [...previous, ...result.records] : result.records));
        setTotal(result.total);
        setPage(result.current);
      })
      .catch((error) => message.error(error instanceof Error ? error.message : '评论加载失败'))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    if (open && contentId) {
      setRecords([]);
      setBody('');
      load(1, false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, contentId]);

  const handleSubmit = async () => {
    if (!contentId) {
      return;
    }
    const text = body.trim();
    if (!text) {
      message.warning('请输入评论内容');
      return;
    }
    setSubmitting(true);
    try {
      await submitComment(contentId, { content: text });
      message.success('评论已发表');
      setBody('');
      load(1, false);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '评论发表失败');
    } finally {
      setSubmitting(false);
    }
  };

  const handleLike = async (comment: CommentView) => {
    try {
      await likeComment(comment.id, !comment.liked);
      setRecords((previous) =>
        previous.map((item) =>
          item.id === comment.id
            ? {
                ...item,
                liked: !item.liked,
                likeCount: Math.max(item.likeCount + (item.liked ? -1 : 1), 0),
              }
            : item,
        ),
      );
    } catch (error) {
      message.error(error instanceof Error ? error.message : '点赞失败');
    }
  };

  return (
    <Drawer title="评论" open={open} onClose={onClose} width={420} destroyOnHidden>
      {commentable === false ? (
        <Empty description="该内容暂不开放评论" image={Empty.PRESENTED_IMAGE_SIMPLE} />
      ) : (
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <Space.Compact style={{ width: '100%' }}>
            <Input.TextArea
              value={body}
              onChange={(event) => setBody(event.target.value)}
              placeholder="发表你的看法…"
              autoSize={{ minRows: 2, maxRows: 4 }}
              maxLength={500}
              showCount
            />
            <Button type="primary" loading={submitting} onClick={() => void handleSubmit()}>
              发表
            </Button>
          </Space.Compact>
          {loading && records.length === 0 ? (
            <div style={{ textAlign: 'center', padding: '24px 0' }}>
              <Spin />
            </div>
          ) : records.length === 0 ? (
            <Empty description="还没有评论，来抢沙发" image={Empty.PRESENTED_IMAGE_SIMPLE} />
          ) : (
            <List<CommentView>
              dataSource={records}
              renderItem={(comment) => (
                <List.Item
                  actions={[
                    <Button key="like" type="link" size="small" onClick={() => void handleLike(comment)}>
                      {comment.liked ? '已赞' : '点赞'} {formatCount(comment.likeCount)}
                    </Button>,
                  ]}
                >
                  <List.Item.Meta
                    title={<Typography.Text type="secondary">用户 {comment.userId}</Typography.Text>}
                    description={
                      <>
                        <div>{comment.content}</div>
                        {flattenReplies(comment).map((reply) => (
                          <div key={reply.id} style={{ marginTop: 6, color: 'rgba(0,0,0,0.65)' }}>
                            <Typography.Text type="secondary">回复：</Typography.Text>
                            {reply.content}
                          </div>
                        ))}
                        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                          {formatDateTime(comment.createTime)}
                        </Typography.Text>
                      </>
                    }
                  />
                </List.Item>
              )}
            />
          )}
          {records.length < total ? (
            <Button block loading={loading} onClick={() => load(page + 1, true)}>
              加载更多（{records.length}/{total}）
            </Button>
          ) : null}
        </Space>
      )}
    </Drawer>
  );
}
