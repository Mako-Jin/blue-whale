// 发现页：精华榜（F-06-06）与标签页（F-06-07），共用一套筛选（F-06-04）
//
// 精华榜不单独开接口：复用 /forum/thread/page?essenceOnly=true，
// 避免「同一个查询两个契约」后两处排序/可见性慢慢分叉。
// 标签筛选走服务端的 fm_thread_tag 精确匹配，不在前端对逗号串做 includes。

import { useCallback, useEffect, useState } from 'react';
import { Button, Card, Empty, Input, List, Segmented, Select, Space, Tag, Typography } from 'antd';
import {
  fetchTags,
  fetchThreads,
  searchThreads,
  type BoardItem,
  type TagStat,
  type ThreadListItem,
} from '../api/forum';

const { Text, Paragraph } = Typography;

type Mode = 'essence' | 'tag';

interface Props {
  boards: BoardItem[];
  onOpenThread: (threadId: string) => void;
}

export default function DiscoveryPage({ boards, onOpenThread }: Props) {
  const [mode, setMode] = useState<Mode>('essence');
  const [boardId, setBoardId] = useState<string | undefined>();
  const [authorId, setAuthorId] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [keyword, setKeyword] = useState('');
  const [tag, setTag] = useState<string | undefined>();
  const [tags, setTags] = useState<TagStat[]>([]);
  const [items, setItems] = useState<ThreadListItem[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  /** 标签云：只统计可见版块，取前 60 个；失败静默降级为空 */
  useEffect(() => {
    let cancelled = false;
    fetchTags(60)
      .then((data) => {
        if (!cancelled) {
          setTags(data);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setTags([]);
        }
      });
    return () => {
      cancelled = true;
    };
  }, []);

  // 标签页在未选标签时没有查询对象：此时只展示标签云，不发空查询
  const enabled = mode === 'essence' || Boolean(tag);

  const load = useCallback(
    (page = 1) => {
      if (!enabled) {
        return;
      }
      if (from && to && from > to) {
        setError('起始日期不能晚于截止日期');
        return;
      }
      setLoading(true);
      const params = {
        boardId,
        essenceOnly: mode === 'essence' ? true : undefined,
        authorId: authorId.trim() || undefined,
        from: from || undefined,
        to: to || undefined,
        tag: mode === 'tag' ? tag : undefined,
        current: page,
      };
      const trimmed = keyword.trim();
      // 与列表页同一口径：有关键词走检索通道，否则走列表通道
      const task = trimmed ? searchThreads({ keyword: trimmed, ...params }) : fetchThreads(params);
      task
        .then((data) => {
          setItems(data.records);
          setTotal(Number(data.total));
          setCurrent(Number(data.current));
          setError(null);
        })
        .catch((err: unknown) => setError(err instanceof Error ? err.message : '加载失败'))
        .finally(() => setLoading(false));
    },
    [enabled, mode, boardId, authorId, from, to, tag, keyword],
  );

  useEffect(() => {
    load(1);
  }, [load]);

  const reset = () => {
    setBoardId(undefined);
    setAuthorId('');
    setFrom('');
    setTo('');
    setKeyword('');
    setTag(undefined);
    setError(null);
  };

  return (
    <div className="forum-page">
      <div className="forum-page-head">
        <div>
          <h2>发现</h2>
          <Paragraph type="secondary">
            精华榜与标签页跨版块聚合，只包含当前账号有权限查看的版块。
          </Paragraph>
        </div>
        <Segmented
          value={mode}
          onChange={(value) => {
            setMode(value as Mode);
            setTag(undefined);
            setCurrent(1);
          }}
          options={[
            { label: '精华榜', value: 'essence' },
            { label: '标签页', value: 'tag' },
          ]}
        />
      </div>

      <Card bordered={false} style={{ marginBottom: 12 }}>
        <Space wrap>
          <Select
            allowClear
            placeholder="全部版块"
            style={{ width: 180 }}
            value={boardId}
            onChange={(value) => setBoardId(value)}
            options={boards.map((board) => ({ label: board.name, value: board.id }))}
          />
          <Input
            placeholder="作者 ID"
            style={{ width: 140 }}
            value={authorId}
            onChange={(event) => setAuthorId(event.target.value)}
          />
          <Input
            type="date"
            aria-label="起始日期"
            style={{ width: 150 }}
            value={from}
            onChange={(event) => setFrom(event.target.value)}
          />
          <Input
            type="date"
            aria-label="截止日期"
            style={{ width: 150 }}
            value={to}
            onChange={(event) => setTo(event.target.value)}
          />
          <Input.Search
            allowClear
            placeholder="搜索标题与正文"
            style={{ width: 220 }}
            onSearch={(value) => {
              setKeyword(value.trim());
              setCurrent(1);
            }}
          />
          <Button onClick={reset}>重置</Button>
        </Space>
      </Card>

      {mode === 'tag' ? (
        <Card bordered={false} title="热门标签" style={{ marginBottom: 12 }}>
          {tags.length > 0 ? (
            <Space wrap>
              {tags.map((item) => (
                <Tag.CheckableTag
                  key={item.tag}
                  checked={tag === item.tag}
                  onChange={() => {
                    setTag(item.tag);
                    setCurrent(1);
                  }}
                >
                  {item.tag} · {Number(item.threadCount)}
                </Tag.CheckableTag>
              ))}
            </Space>
          ) : (
            <Text type="secondary">还没有标签，等第一帖带上标签后再来看</Text>
          )}
        </Card>
      ) : null}

      {error ? (
        <Empty description={error} />
      ) : !enabled ? (
        <Empty description="选择一个标签，查看该标签下的帖子" />
      ) : (
        <List
          loading={loading}
          dataSource={items}
          locale={{ emptyText: mode === 'essence' ? '还没有被题金榜的帖子' : '该标签下还没有帖子' }}
          pagination={{
            current,
            total,
            pageSize: 20,
            showSizeChanger: false,
            onChange: (page) => load(page),
          }}
          renderItem={(item) => (
            <List.Item key={item.id} onClick={() => onOpenThread(item.id)}>
              <List.Item.Meta
                title={
                  <Space size={4} wrap>
                    {item.pinned === 1 ? <Tag color="red">悬榜</Tag> : null}
                    {item.essence === 1 ? <Tag color="gold">金榜</Tag> : null}
                    <a>{item.title}</a>
                  </Space>
                }
                description={
                  <Space size={12} wrap>
                    <Text type="secondary">
                      {item.anonymous === 1
                        ? `匿名鲸友 ${item.anonymousNo ?? ''}`
                        : `鲸友 #${item.authorId}`}
                    </Text>
                    <Text type="secondary">{item.publishTime ?? item.lastReplyTime ?? ''}</Text>
                    <Text type="secondary">回复 {item.replyCount}</Text>
                    <Text type="secondary">浏览 {item.viewCount}</Text>
                  </Space>
                }
              />
            </List.Item>
          )}
        />
      )}
    </div>
  );
}
