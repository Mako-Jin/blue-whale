// 电影页（M4）：热门 / 新片榜单 + 已上架片单 + 分类筛选与关键词搜索的电影库

import {
  Alert,
  Button,
  Card,
  Col,
  Empty,
  Input,
  Pagination,
  Result,
  Row,
  Select,
  Space,
  Spin,
  Tag,
  Typography,
} from 'antd';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  fetchCategories,
  fetchCollectionPage,
  fetchMoviePage,
  fetchRankings,
  type CategoryView,
  type FeedSort,
  type MovieCollectionView,
  type MovieView,
  type PublicContentView,
} from '../api/video';
import { hasPermission } from '../api/client';
import ContentCover from '../components/ContentCover';
import { formatCount, formatDuration } from '../utils/videoDisplay';

/** 电影每页条数 */
const PAGE_SIZE = 12;

/** 榜单条数 */
const RANKING_SIZE = 10;

/** 片单展示数量 */
const COLLECTION_LIMIT = 8;

/** 榜单标签文案 */
function rankingTitle(label: string) {
  return <Typography.Text strong>{label}</Typography.Text>;
}

export default function MoviesPage() {
  const navigate = useNavigate();
  const canBrowse = hasPermission('sys:video:browse');

  const [categories, setCategories] = useState<CategoryView[]>([]);
  const [collections, setCollections] = useState<MovieCollectionView[]>([]);
  const [hot, setHot] = useState<PublicContentView[]>([]);
  const [latest, setLatest] = useState<PublicContentView[]>([]);
  const [records, setRecords] = useState<MovieView[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [keyword, setKeyword] = useState('');
  const [categoryId, setCategoryId] = useState<number | undefined>(undefined);
  const [sortBy, setSortBy] = useState<FeedSort>('LATEST');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!canBrowse) {
      return;
    }
    fetchCategories()
      .then((list) =>
        setCategories(
          list.filter(
            (item) => item.contentTypeScope === 'ALL' || item.contentTypeScope === 'MOVIE',
          ),
        ),
      )
      .catch(() => setCategories([]));
    fetchCollectionPage({ size: COLLECTION_LIMIT })
      .then((page) => setCollections(page.records))
      .catch(() => setCollections([]));
    fetchRankings({ type: 'MOVIE', size: RANKING_SIZE })
      .then((view) => {
        setHot(view.hot ?? []);
        setLatest(view.latest ?? []);
      })
      .catch(() => {
        setHot([]);
        setLatest([]);
      });
  }, [canBrowse]);

  useEffect(() => {
    if (!canBrowse) {
      return;
    }
    let cancelled = false;
    setLoading(true);
    fetchMoviePage({
      current,
      size: PAGE_SIZE,
      keyword: keyword || undefined,
      categoryId,
      sortBy,
    })
      .then((page) => {
        if (cancelled) {
          return;
        }
        setRecords(page.records);
        setTotal(page.total);
        setError(null);
      })
      .catch((reason: unknown) => {
        if (cancelled) {
          return;
        }
        const text = reason instanceof Error ? reason.message : '电影列表加载失败';
        setError(text);
        setRecords([]);
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [canBrowse, current, keyword, categoryId, sortBy]);

  if (!canBrowse) {
    return (
      <Result
        status="403"
        title="暂无浏览权限"
        subTitle="浏览电影需要 sys:video:browse 权限，请联系租户管理员开通"
        extra={
          <Button type="primary" onClick={() => navigate('/home')}>
            返回首页
          </Button>
        }
      />
    );
  }

  const renderRanking = (title: string, items: PublicContentView[]) => (
    <Card title={rankingTitle(title)} size="small" style={{ flex: '1 1 420px', minWidth: 320 }}>
      {items.length === 0 ? (
        <Empty description="暂无榜单数据" image={Empty.PRESENTED_IMAGE_SIMPLE} />
      ) : (
        <Space direction="vertical" size={6} style={{ width: '100%' }}>
          {items.map((item, index) => (
            <div
              key={item.id}
              style={{ display: 'flex', alignItems: 'center', gap: 10, cursor: 'pointer' }}
              onClick={() => navigate(`/movies/${item.id}`)}
            >
              <Typography.Text type={index < 3 ? 'danger' : 'secondary'} style={{ width: 22 }}>
                {index + 1}
              </Typography.Text>
              <Typography.Text ellipsis style={{ flex: 1 }}>
                {item.title}
              </Typography.Text>
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                {formatCount(item.playCount)} 次播放
              </Typography.Text>
            </div>
          ))}
        </Space>
      )}
    </Card>
  );

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Typography.Title level={3} style={{ margin: 0 }}>
        电影
      </Typography.Title>

      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 16 }}>
        {renderRanking('热门榜', hot)}
        {renderRanking('新片榜', latest)}
      </div>

      <Card
        title="电影片单"
        size="small"
        extra={<Typography.Text type="secondary">共 {collections.length} 个片单</Typography.Text>}
      >
        {collections.length === 0 ? (
          <Empty description="暂无已上架片单" image={Empty.PRESENTED_IMAGE_SIMPLE} />
        ) : (
          <Row gutter={[16, 16]}>
            {collections.map((item) => (
              <Col key={item.id} xs={24} sm={12} md={8} lg={6}>
                <Card
                  size="small"
                  hoverable
                  onClick={() => navigate(`/collections/${item.id}`)}
                  styles={{ body: { padding: 0 } }}
                >
                  <ContentCover url={item.coverUrl} title={item.title} height={120} />
                  <div style={{ padding: '10px 12px' }}>
                    <Typography.Text strong ellipsis style={{ display: 'block' }}>
                      {item.title}
                    </Typography.Text>
                    <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                      {item.itemCount} 部电影
                    </Typography.Text>
                  </div>
                </Card>
              </Col>
            ))}
          </Row>
        )}
      </Card>

      <Card
        title="电影库"
        size="small"
        extra={
          <Space wrap>
            <Select<FeedSort>
              value={sortBy}
              style={{ width: 120 }}
              options={[
                { value: 'LATEST', label: '最新上架' },
                { value: 'HOT', label: '最热播放' },
              ]}
              onChange={(value) => {
                setSortBy(value);
                setCurrent(1);
              }}
            />
            <Select<number>
              value={categoryId}
              allowClear
              placeholder="全部分类"
              style={{ width: 150 }}
              options={categories.map((category) => ({ value: category.id, label: category.name }))}
              onChange={(value) => {
                setCategoryId(value);
                setCurrent(1);
              }}
            />
            <Input.Search
              value={keyword}
              allowClear
              placeholder="搜索片名 / 原始片名"
              style={{ width: 260 }}
              onChange={(event) => {
                setKeyword(event.target.value);
                if (!event.target.value) {
                  setCurrent(1);
                }
              }}
              onSearch={(value) => {
                setKeyword(value.trim());
                setCurrent(1);
              }}
            />
          </Space>
        }
      >
        {error ? (
          <Alert style={{ marginBottom: 12 }} type="warning" showIcon message={error} />
        ) : null}
        {loading && records.length === 0 ? (
          <div style={{ padding: 48, textAlign: 'center' }}>
            <Spin />
          </div>
        ) : records.length === 0 ? (
          <Empty
            description={keyword ? '没有匹配的电影' : '还没有已上架的电影'}
            image={Empty.PRESENTED_IMAGE_SIMPLE}
          />
        ) : (
          <Row gutter={[16, 16]}>
            {records.map((record) => (
              <Col key={record.id} xs={24} sm={12} md={8} lg={6}>
                <Card
                  size="small"
                  hoverable
                  onClick={() => navigate(`/movies/${record.id}`)}
                  styles={{ body: { padding: 0 } }}
                >
                  <ContentCover url={record.posterUrl} title={record.title} height={190} />
                  <div style={{ padding: '10px 12px' }}>
                    <Typography.Text strong ellipsis style={{ display: 'block' }}>
                      {record.title}
                    </Typography.Text>
                    {record.originalTitle ? (
                      <Typography.Text
                        type="secondary"
                        ellipsis
                        style={{ display: 'block', fontSize: 12 }}
                      >
                        {record.originalTitle}
                      </Typography.Text>
                    ) : null}
                    <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                      {[record.region, record.releaseDate, formatDuration(record.durationMs)]
                        .filter(Boolean)
                        .join(' · ') || '电影'}
                    </Typography.Text>
                    <Space size={4} wrap style={{ marginTop: 6 }}>
                      {record.rating ? <Tag color="gold">{record.rating} 分</Tag> : null}
                      <Tag color={record.playable ? 'green' : 'default'}>
                        {record.playable ? '可播放' : '暂不可播'}
                      </Tag>
                    </Space>
                  </div>
                </Card>
              </Col>
            ))}
          </Row>
        )}

        {total > 0 ? (
          <div style={{ marginTop: 16, textAlign: 'right' }}>
            <Pagination
              current={current}
              pageSize={PAGE_SIZE}
              total={total}
              showSizeChanger={false}
              showTotal={(count) => `共 ${count} 部`}
              onChange={(page) => setCurrent(page)}
            />
          </div>
        ) : null}
      </Card>
    </Space>
  );
}
