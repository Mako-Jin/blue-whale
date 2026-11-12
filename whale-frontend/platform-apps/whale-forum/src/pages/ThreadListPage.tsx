// 帖子列表页：版块内列表 + 排序/搜索 + 发帖入口
//
// 列表只展示 PUBLISHED/SOLVED/CLOSED（后端已过滤），待审帖子仅作者在详情页可见。

import { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Empty,
  Form,
  Input,
  List,
  Modal,
  Segmented,
  Space,
  Tag,
  Typography,
  message,
} from 'antd';
import { createThread, fetchThreads, type BoardItem, type ThreadListItem } from '../api/forum';
import { hasPermission } from '../api/client';

const { Text, Paragraph } = Typography;

/** 排序项（值与后端 ThreadQuery.sort 对齐） */
const SORTS = [
  { label: '最新', value: 'latest' },
  { label: '热帖', value: 'hot' },
  { label: '精华', value: 'essence' },
];

interface Props {
  board: BoardItem;
  onOpenThread: (threadId: string) => void;
}

/** 状态徽标 */
function statusTag(status: string) {
  if (status === 'PENDING') {
    return <Tag color="orange">待审</Tag>;
  }
  if (status === 'SOLVED') {
    return <Tag color="green">已解决</Tag>;
  }
  if (status === 'CLOSED') {
    return <Tag>已关闭</Tag>;
  }
  return null;
}

export default function ThreadListPage({ board, onOpenThread }: Props) {
  const [items, setItems] = useState<ThreadListItem[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [sort, setSort] = useState('latest');
  const [keyword, setKeyword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [editorOpen, setEditorOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm<{ title: string; content: string }>();

  const load = useCallback(
    (page: number, activeSort: string, activeKeyword: string) => {
      setLoading(true);
      fetchThreads({ boardId: board.id, sort: activeSort, keyword: activeKeyword, current: page })
        .then((data) => {
          setItems(data.records);
          setTotal(Number(data.total));
          setCurrent(Number(data.current));
          setError(null);
        })
        .catch((err: unknown) => setError(err instanceof Error ? err.message : '加载失败'))
        .finally(() => setLoading(false));
    },
    [board.id],
  );

  useEffect(() => {
    load(1, sort, keyword);
    // 切版块时重置到第一页
  }, [load, sort, keyword]);

  const submit = async () => {
    const values = await form.validateFields();
    setSubmitting(true);
    try {
      const created = await createThread({
        boardId: board.id,
        title: values.title,
        content: values.content,
      });
      setEditorOpen(false);
      form.resetFields();
      if (created.status === 'PENDING') {
        void message.success('已提交，等待版主审核');
        onOpenThread(created.id);
      } else {
        void message.success('发布成功');
        load(1, sort, keyword);
      }
    } catch (err: unknown) {
      void message.error(err instanceof Error ? err.message : '发布失败');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="forum-page">
      <div className="forum-page-head">
        <div>
          <h2>{board.name}</h2>
          {board.description ? <Paragraph type="secondary">{board.description}</Paragraph> : null}
        </div>
        <Space wrap>
          <Segmented
            options={SORTS}
            value={sort}
            onChange={(value) => {
              setSort(String(value));
              setCurrent(1);
            }}
          />
          <Input.Search
            allowClear
            placeholder="搜索标题"
            style={{ width: 220 }}
            onSearch={(value) => {
              setKeyword(value.trim());
              setCurrent(1);
            }}
          />
          {hasPermission('sys:forum:thread:add') ? (
            <Button type="primary" onClick={() => setEditorOpen(true)}>
              发帖
            </Button>
          ) : null}
        </Space>
      </div>

      {error ? (
        <Empty description={error} />
      ) : (
        <List
          loading={loading}
          dataSource={items}
          locale={{ emptyText: '还没有帖子，来发第一帖吧' }}
          pagination={{
            current,
            total,
            pageSize: 20,
            showSizeChanger: false,
            onChange: (page) => load(page, sort, keyword),
          }}
          renderItem={(item) => (
            <List.Item
              key={item.id}
              className="forum-thread-item"
              onClick={() => onOpenThread(item.id)}
            >
              <List.Item.Meta
                title={
                  <Space size={4} wrap>
                    {item.pinned === 1 ? <Tag color="red">悬榜</Tag> : null}
                    {item.essence === 1 ? <Tag color="gold">金榜</Tag> : null}
                    {statusTag(item.status)}
                    <a>{item.title}</a>
                  </Space>
                }
                description={
                  <Space size={12} wrap>
                    <Text type="secondary">
                      {item.anonymous === 1 ? `匿名鲸友 ${item.anonymousNo ?? ''}` : `鲸友 #${item.authorId}`}
                    </Text>
                    <Text type="secondary">{item.publishTime ?? item.lastReplyTime ?? ''}</Text>
                    <Text type="secondary">回复 {item.replyCount}</Text>
                    <Text type="secondary">浏览 {item.viewCount}</Text>
                  </Space>
                }
              />
              {item.summary ? <Paragraph className="forum-thread-summary">{item.summary}</Paragraph> : null}
            </List.Item>
          )}
        />
      )}

      <Modal
        title={`在「${board.name}」发帖`}
        open={editorOpen}
        onCancel={() => setEditorOpen(false)}
        onOk={submit}
        confirmLoading={submitting}
        okText="发布"
        destroyOnHidden
        width={720}
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="title"
            label="标题"
            rules={[{ required: true, message: '请输入标题' }, { max: 120, message: '标题不超过 120 字' }]}
          >
            <Input placeholder="一句话说清主题" />
          </Form.Item>
          <Form.Item
            name="content"
            label="正文"
            rules={[{ required: true, message: '请输入正文' }]}
            extra="支持简单 HTML（p/strong/em/ul/li/pre/code/a）。含 script、on* 事件等危险内容会被服务端拒绝。"
          >
            <Input.TextArea rows={10} placeholder="<p>正文…</p>" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
