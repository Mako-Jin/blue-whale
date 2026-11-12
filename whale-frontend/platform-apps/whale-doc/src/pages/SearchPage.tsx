// 文档搜索：按关键字检索公开空间与我参与空间中的已发布页面

import { useCallback, useEffect, useState } from 'react';
import {
  App,
  Card,
  Empty,
  Input,
  List,
  Pagination,
  Select,
  Space,
  Spin,
  Tag,
  Typography,
} from 'antd';
import { FileTextOutlined, SearchOutlined } from '@ant-design/icons';
import { Link } from 'react-router-dom';
import { searchApi } from '../api/search';
import { spaceApi } from '../api/space';
import { tagApi } from '../api/tag';
import type { DocSearchItem, DocSpace, DocTag } from '../api/types';
import { formatTime } from '../utils/docContent';

const { Text, Paragraph } = Typography;

/** 每页条数 */
const PAGE_SIZE = 10;

/** 转义正则元字符 */
function escapeRegExp(text: string): string {
  return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

/** 高亮关键词（标题与摘要） */
function highlight(text: string, keyword: string) {
  const trimmed = keyword.trim();
  if (!trimmed) {
    return text;
  }
  const parts = text.split(new RegExp(`(${escapeRegExp(trimmed)})`, 'gi'));
  return parts.map((part, index) =>
    part.toLowerCase() === trimmed.toLowerCase() ? <mark key={index}>{part}</mark> : part,
  );
}

export default function SearchPage() {
  const { message } = App.useApp();
  const [keyword, setKeyword] = useState('');
  const [current, setCurrent] = useState(1);
  const [items, setItems] = useState<DocSearchItem[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [searched, setSearched] = useState(false);
  const [spaces, setSpaces] = useState<DocSpace[]>([]);
  const [tags, setTags] = useState<DocTag[]>([]);
  const [spaceId, setSpaceId] = useState<number | undefined>();
  const [tagId, setTagId] = useState<number | undefined>();

  useEffect(() => {
    spaceApi
      .page({ current: 1, size: 200 })
      .then((result) => setSpaces(result.records))
      .catch(() => undefined);
  }, []);

  // 选择空间后加载其标签，切换空间时清空标签筛选
  useEffect(() => {
    setTagId(undefined);
    if (!spaceId) {
      setTags([]);
      return;
    }
    tagApi
      .list(spaceId)
      .then(setTags)
      .catch(() => setTags([]));
  }, [spaceId]);

  const run = useCallback(
    async (value: string, page: number, space?: number, tag?: number) => {
      const trimmed = value.trim();
      if (!trimmed) {
        setItems([]);
        setTotal(0);
        setSearched(false);
        return;
      }
      setLoading(true);
      try {
        const result = await searchApi.search({
          keyword: trimmed,
          current: page,
          size: PAGE_SIZE,
          spaceId: space,
          tagId: tag,
        });
        setItems(result.records);
        setTotal(Number(result.total));
        setSearched(true);
      } catch (error) {
        message.error((error as Error).message);
      } finally {
        setLoading(false);
      }
    },
    [message],
  );

  const submit = (value: string, space = spaceId, tag = tagId) => {
    setKeyword(value);
    setCurrent(1);
    void run(value, 1, space, tag);
  };

  /** 空间/标签筛选变化时按当前关键词重新检索 */
  const changeSpace = (value?: number) => {
    setSpaceId(value);
    submit(keyword, value, undefined);
  };

  const changeTag = (value?: number) => {
    setTagId(value);
    submit(keyword, spaceId, value);
  };

  return (
    <div className="doc-search">
      <Card>
        <Space direction="vertical" size={12} style={{ width: '100%' }}>
          <Input.Search
            allowClear
            enterButton={<SearchOutlined />}
            size="large"
            placeholder="搜索页面标题或正文，例如「发布流程」"
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            onSearch={(value) => submit(value)}
            loading={loading}
          />
          <Space wrap>
            <Select
              allowClear
              placeholder="全部空间"
              style={{ minWidth: 200 }}
              value={spaceId}
              options={spaces.map((space) => ({ value: space.id, label: space.name }))}
              onChange={changeSpace}
            />
            <Select
              allowClear
              placeholder="全部标签"
              style={{ minWidth: 180 }}
              value={tagId}
              disabled={!spaceId}
              options={tags.map((tag) => ({ value: tag.id, label: tag.name }))}
              onChange={changeTag}
            />
          </Space>
          <Text type="secondary">
            仅检索已发布页面：公开空间对所有人生效，成员空间仅对空间成员生效； 受限页面仅对 ACL
            授权用户可见，标签筛选需先选择空间。
          </Text>
        </Space>
      </Card>

      <Spin spinning={loading}>
        {searched ? (
          <Card style={{ marginTop: 16 }}>
            <Text type="secondary" style={{ display: 'block', marginBottom: 12 }}>
              共 {total} 条结果
            </Text>
            <List
              dataSource={items}
              locale={{ emptyText: <Empty description="没有匹配的页面，换个关键字试试" /> }}
              renderItem={(item) => (
                <List.Item className="doc-result-item">
                  <List.Item.Meta
                    avatar={<FileTextOutlined />}
                    title={
                      <Space size={8} wrap>
                        <Link to={`/space/${item.spaceId}?pageId=${item.pageId}`}>
                          {highlight(item.title, keyword)}
                        </Link>
                        <Tag color="green">已发布</Tag>
                        <Text type="secondary">{item.spaceName}</Text>
                      </Space>
                    }
                    description={
                      <Space direction="vertical" size={4}>
                        <Paragraph style={{ margin: 0 }}>
                          {item.snippet ? highlight(item.snippet, keyword) : '暂无摘要'}
                        </Paragraph>
                        <Text type="secondary">
                          r{item.revision} · 更新于 {formatTime(item.updateTime)} · /{item.slug}
                        </Text>
                      </Space>
                    }
                  />
                </List.Item>
              )}
            />
            {total > PAGE_SIZE && (
              <div style={{ marginTop: 16, textAlign: 'right' }}>
                <Pagination
                  current={current}
                  pageSize={PAGE_SIZE}
                  total={total}
                  showSizeChanger={false}
                  onChange={(page) => {
                    setCurrent(page);
                    void run(keyword, page, spaceId, tagId);
                  }}
                />
              </div>
            )}
          </Card>
        ) : (
          <Empty description="输入关键字开始搜索" style={{ padding: 48 }} />
        )}
      </Spin>
    </div>
  );
}
