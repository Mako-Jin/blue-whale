// 帖子详情页：楼主正文 + 楼层列表 + 回帖（支持引用回复与楼层正/倒序）
//
// 正文为服务端白名单消毒后的富文本，用 innerHTML 渲染；危险构造在入库前已被拒绝（30030），
// 前端不再做二次清洗，避免「前端清洗后内容变样」与「两套规则不一致」。

import { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Empty,
  Form,
  Input,
  List,
  Modal,
  Segmented,
  Space,
  Spin,
  Tag,
  Typography,
  message,
} from 'antd';
import {
  addFollow,
  addReaction,
  cancelReaction,
  createPost,
  fetchFollowState,
  fetchPosts,
  fetchThread,
  removeFollow,
  updateThread,
  type PostItem,
  type ReactionState,
  type ThreadDetail,
} from '../api/forum';
import { hasPermission } from '../api/client';

const { Text, Paragraph } = Typography;

interface Props {
  threadId: string;
  onBack: () => void;
}

/** 状态徽标 */
function statusTag(status: string) {
  switch (status) {
    case 'PENDING':
      return <Tag color="orange">待审</Tag>;
    case 'SOLVED':
      return <Tag color="green">已解决</Tag>;
    case 'CLOSED':
      return <Tag>已关闭</Tag>;
    default:
      return null;
  }
}

export default function ThreadDetailPage({ threadId, onBack }: Props) {
  const [thread, setThread] = useState<ThreadDetail | null>(null);
  const [posts, setPosts] = useState<PostItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [reply, setReply] = useState('');
  const [sending, setSending] = useState(false);
  const [reaction, setReaction] = useState<ReactionState | null>(null);
  const [reacting, setReacting] = useState<'LIKE' | 'COLLECT' | null>(null);
  /** 楼层排序：交给服务端，前端不本地反转（反转会让翻页边界不连续） */
  const [order, setOrder] = useState<'asc' | 'desc'>('asc');
  /** 正在引用的楼层：回帖时带 replyToPostId */
  const [quote, setQuote] = useState<PostItem | null>(null);
  /** 是否已关注楼主（结契） */
  const [authorFollowed, setAuthorFollowed] = useState(false);
  const [followLoading, setFollowLoading] = useState(false);
  /** 编辑弹窗（标题 / 正文 / 标签 / 编辑说明） */
  const [editOpen, setEditOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [editForm] = Form.useForm<{
    title: string;
    content: string;
    tags?: string;
    reason?: string;
  }>();

  /** 详情响应已经带按钮态与计数，直接落地，避免再打一轮接口 */
  const applyThread = useCallback((data: ThreadDetail) => {
    setThread(data);
    setReaction({
      liked: data.liked ?? false,
      collected: data.collected ?? false,
      likeCount: data.likeCount ?? 0,
      collectCount: data.collectCount ?? 0,
    });
  }, []);

  const loadPosts = useCallback(() => {
    fetchPosts(threadId, 1, 100, order)
      .then((page) => setPosts(page.records))
      .catch(() => setPosts([]));
  }, [threadId, order]);

  useEffect(() => {
    setLoading(true);
    fetchThread(threadId)
      .then((data) => {
        applyThread(data);
        setError(null);
      })
      .catch((err: unknown) => setError(err instanceof Error ? err.message : '加载失败'))
      .finally(() => setLoading(false));
  }, [threadId, applyThread]);

  // 楼层单独拉：切换排序时只重取楼层，不重取详情（否则整页会闪一次 loading）
  useEffect(() => {
    loadPosts();
  }, [loadPosts]);

  // 关注态单独拉：详情接口不带关注信息（关注是用户↔作者的跨帖关系）
  useEffect(() => {
    if (!thread || thread.anonymous === 1 || !thread.authorId) {
      setAuthorFollowed(false);
      return;
    }
    let cancelled = false;
    fetchFollowState('USER', thread.authorId)
      .then((state) => {
        if (!cancelled) {
          setAuthorFollowed(state.following);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setAuthorFollowed(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [thread]);

  /** 关注/取关楼主：服务端返回目标态，前端不做本地取反 */
  const onToggleFollowAuthor = async () => {
    if (!thread?.authorId) {
      return;
    }
    setFollowLoading(true);
    try {
      const next = authorFollowed
        ? await removeFollow('USER', thread.authorId)
        : await addFollow('USER', thread.authorId);
      setAuthorFollowed(next.following);
      void message.success(next.following ? '已结契，关注楼主' : '已取关');
    } catch (err: unknown) {
      void message.error(err instanceof Error ? err.message : '操作失败');
    } finally {
      setFollowLoading(false);
    }
  };

  /** 打开编辑弹窗：表单初值取服务端当前值，不拿本地拼的摘要 */
  const openEditor = () => {
    if (!thread) {
      return;
    }
    editForm.setFieldsValue({
      title: thread.title,
      content: thread.content ?? '',
      tags: thread.tags ?? '',
      reason: '',
    });
    setEditOpen(true);
  };

  /** 保存编辑：审核版块会退回待审，提示语要跟状态走 */
  const submitEdit = async () => {
    let values: { title: string; content: string; tags?: string; reason?: string };
    try {
      values = await editForm.validateFields();
    } catch {
      return;
    }
    setSaving(true);
    try {
      const next = await updateThread(threadId, {
        title: values.title.trim(),
        content: values.content,
        tags: values.tags?.trim() ?? '',
        reason: values.reason?.trim() || undefined,
      });
      applyThread(next);
      setEditOpen(false);
      void message.success(next.status === 'PENDING' ? '已提交修改，等待版主审核' : '已保存');
    } catch (err: unknown) {
      void message.error(err instanceof Error ? err.message : '保存失败');
    } finally {
      setSaving(false);
    }
  };

  /** 投花/藏帖：服务端返回最新选中态与计数，前端不本地加减 */
  const onToggleReaction = async (type: 'LIKE' | 'COLLECT') => {
    const active = type === 'LIKE' ? reaction?.liked : reaction?.collected;
    setReacting(type);
    try {
      const next = active
        ? await cancelReaction(threadId, type)
        : await addReaction(threadId, type);
      setReaction(next);
      void message.success(
        type === 'LIKE' ? (next.liked ? '已投花' : '已取消投花') : next.collected ? '已藏帖' : '已取消藏帖',
      );
    } catch (err: unknown) {
      void message.error(err instanceof Error ? err.message : '操作失败');
    } finally {
      setReacting(null);
    }
  };

  const submitReply = async () => {
    const content = reply.trim();
    if (!content) {
      void message.warning('回复内容不能为空');
      return;
    }
    setSending(true);
    try {
      await createPost(threadId, {
        content,
        // 引用只传楼层ID：摘要由服务端回填，前端不把自己拼的文本当权威
        ...(quote ? { replyToPostId: quote.id } : {}),
      });
      setReply('');
      setQuote(null);
      void message.success('回复成功');
      loadPosts();
      // 回复数由服务端累加，重新取详情保证计数与列表一致
      const fresh = await fetchThread(threadId);
      applyThread(fresh);
    } catch (err: unknown) {
      void message.error(err instanceof Error ? err.message : '回复失败');
    } finally {
      setSending(false);
    }
  };

  if (loading) {
    return (
      <div className="forum-loading">
        <Spin tip="正在加载帖子…" />
      </div>
    );
  }
  if (error || !thread) {
    return (
      <div className="forum-page">
        <Button type="link" onClick={onBack}>
          ← 返回列表
        </Button>
        <Empty description={error ?? '帖子不存在或已删除'} />
      </div>
    );
  }

  const replyable = thread.status === 'PUBLISHED' || thread.status === 'SOLVED';
  const canReply = hasPermission('sys:forum:post:add');
  const canReact = hasPermission('sys:forum:reaction:use');
  const canEdit = hasPermission('sys:forum:thread:edit');

  return (
    <div className="forum-page">
      <Space>
        <Button type="link" onClick={onBack}>
          ← 返回列表
        </Button>
        <Text type="secondary">{thread.threadNo}</Text>
        {canEdit ? (
          <Button size="small" onClick={openEditor}>
            编辑
          </Button>
        ) : null}
      </Space>

      {thread.status === 'PENDING' ? (
        <Alert
          type="warning"
          showIcon
          message="该帖正在审核中"
          description="审核通过后才会出现在版块列表；审核版块内编辑已发布帖子会重新回到待审。"
          style={{ marginBottom: 12 }}
        />
      ) : null}

      <Card
        title={
          <Space size={6} wrap>
            {thread.pinned === 1 ? <Tag color="red">悬榜</Tag> : null}
            {thread.essence === 1 ? <Tag color="gold">金榜</Tag> : null}
            {statusTag(thread.status)}
            <span>{thread.title}</span>
          </Space>
        }
        extra={
          <Space size={12}>
            <Text type="secondary">回复 {thread.replyCount}</Text>
            <Text type="secondary">浏览 {thread.viewCount}</Text>
            <Text type="secondary">投花 {reaction?.likeCount ?? thread.likeCount}</Text>
          </Space>
        }
        bordered={false}
      >
        <div className="forum-post-meta">
          <Space size={8}>
            <Text strong>{thread.anonymous === 1 ? `匿名鲸友 ${thread.anonymousNo ?? ''}` : `鲸友 #${thread.authorId}`}</Text>
            {thread.anonymous === 1 ? null : (
              <Button size="small" type={authorFollowed ? 'primary' : 'default'} loading={followLoading} onClick={onToggleFollowAuthor}>
                {authorFollowed ? '已结契' : '结契关注'}
              </Button>
            )}
          </Space>
          <Text type="secondary">1 楼 · {thread.publishTime ?? thread.createTime ?? ''}</Text>
        </div>
        <div
          className="forum-rich-text"
          // 服务端已白名单消毒（危险构造入库前拒绝），此处直接渲染
          dangerouslySetInnerHTML={{ __html: thread.content ?? '' }}
        />
        {thread.tags ? (
          <Paragraph type="secondary">标签：{thread.tags}</Paragraph>
        ) : null}
        <Space style={{ marginTop: 12 }}>
          <Button
            type={reaction?.liked ? 'primary' : 'default'}
            disabled={!canReact}
            loading={reacting === 'LIKE'}
            onClick={() => onToggleReaction('LIKE')}
          >
            {reaction?.liked ? '已投花' : '投花'} {reaction?.likeCount ?? 0}
          </Button>
          <Button
            type={reaction?.collected ? 'primary' : 'default'}
            disabled={!canReact}
            loading={reacting === 'COLLECT'}
            onClick={() => onToggleReaction('COLLECT')}
          >
            {reaction?.collected ? '已藏帖' : '藏帖'} {reaction?.collectCount ?? 0}
          </Button>
        </Space>
      </Card>

      <List
        header={
          <Space size={12} wrap>
            <Text strong>全部回复（{posts.length}）</Text>
            <Segmented
              size="small"
              value={order}
              onChange={(value) => setOrder(value as 'asc' | 'desc')}
              options={[
                { label: '最早在前', value: 'asc' },
                { label: '最新在前', value: 'desc' },
              ]}
            />
          </Space>
        }
        dataSource={posts}
        locale={{ emptyText: '还没有回复' }}
        renderItem={(item) => (
          <List.Item key={item.id}>
            <div className="forum-post">
              <div className="forum-post-meta">
                <Text strong>
                  {item.anonymous === 1 ? `匿名鲸友 ${item.anonymousNo ?? ''}` : `鲸友 #${item.authorId}`}
                </Text>
                <Text type="secondary">
                  {item.floor} 楼 · {item.createTime ?? ''}
                </Text>
              </div>
              {item.quoted ? (
                <div className="forum-quote">
                  {item.quoted.deleted ? (
                    <Text type="secondary">该楼层已删除</Text>
                  ) : (
                    <Text type="secondary">
                      回复 {item.quoted.floor} 楼 · 鲸友 #{item.quoted.authorId}：
                      {item.quoted.excerpt}
                    </Text>
                  )}
                </div>
              ) : null}
              <div
                className="forum-rich-text"
                dangerouslySetInnerHTML={{ __html: item.content }}
              />
              {replyable && canReply ? (
                <Button
                  type="link"
                  size="small"
                  onClick={() => setQuote(item)}
                >
                  引用
                </Button>
              ) : null}
            </div>
          </List.Item>
        )}
      />

      {replyable && canReply ? (
        <Card bordered={false} className="forum-reply-box">
          {quote ? (
            <div className="forum-quote" style={{ marginBottom: 8 }}>
              <Space size={8}>
                <Text type="secondary">
                  正在引用 {quote.floor} 楼 · 鲸友 #{quote.authorId}
                </Text>
                <Button type="link" size="small" onClick={() => setQuote(null)}>
                  取消引用
                </Button>
              </Space>
            </div>
          ) : null}
          <Input.TextArea
            rows={4}
            value={reply}
            onChange={(event) => setReply(event.target.value)}
            placeholder="写下你的回复（支持简单 HTML；10 秒内只能回一条；@昵称 + 空格 可提醒他人）"
          />
          <Space style={{ marginTop: 8 }}>
            <Button type="primary" loading={sending} onClick={submitReply}>
              回复
            </Button>
            <Text type="secondary">下一层将是 {thread.nextFloor} 楼</Text>
          </Space>
        </Card>
      ) : (
        <Alert
          type="info"
          showIcon
          message={replyable ? '当前账号无回帖权限' : '该帖已关闭或不接受回复'}
        />
      )}

      <Modal
        title="编辑帖子"
        open={editOpen}
        confirmLoading={saving}
        onOk={submitEdit}
        onCancel={() => setEditOpen(false)}
        okText="保存"
        cancelText="取消"
        width={720}
        destroyOnClose
      >
        <Form form={editForm} layout="vertical">
          <Form.Item
            name="title"
            label="标题"
            rules={[{ required: true, message: '标题不能为空' }]}
          >
            <Input maxLength={80} showCount />
          </Form.Item>
          <Form.Item
            name="content"
            label="正文（支持简单 HTML）"
            rules={[{ required: true, message: '正文不能为空' }]}
          >
            <Input.TextArea rows={8} />
          </Form.Item>
          <Form.Item
            name="tags"
            label="标签"
            extra="逗号分隔，最多 10 个；清空表示删除全部标签"
          >
            <Input placeholder="如：报销,差旅" />
          </Form.Item>
          <Form.Item
            name="reason"
            label="编辑说明"
            extra="审核版块编辑会退回待审；说明进编辑历史，便于审核人对比"
          >
            <Input maxLength={200} placeholder="改了什么、为什么改" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
