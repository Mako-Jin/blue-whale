// 视频页（M3）：分类筛选 / 关键词搜索 / 分页列表 + 已上架系列入口

import {
  Alert,
  App,
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
  fetchSeriesPage,
  fetchVideoPage,
  type CategoryView,
  type PublicContentView,
  type SeriesView,
} from '../api/video';
import { hasPermission } from '../api/client';
import ContentCover from '../components/ContentCover';
import { formatCount, formatDuration } from '../utils/videoDisplay';

/** 视频每页条数 */
const PAGE_SIZE = 12;

/** 首页展示的系列数量 */
const SERIES_LIMIT = 8;

export default function VideosPage() {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const canBrowse = hasPermission('sys:video:browse');

  const [categories, setCategories] = useState<CategoryView[]>([]);
  const [series, setSeries] = useState<SeriesView[]>([]);
  const [seriesTotal, setSeriesTotal] = useState(0);
  const [records, setRecords] = useState<PublicContentView[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [keyword, setKeyword] = useState('');
  const [categoryId, setCategoryId] = useState<number | undefined>(undefined);
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
            (item) => item.contentTypeScope === 'ALL' || item.contentTypeScope === 'VIDEO',
          ),
        ),
      )
      .catch((reason: unknown) =>
        message.error(reason instanceof Error ? reason.message : '分类加载失败'),
      );
    fetchSeriesPage({ size: SERIES_LIMIT })
      .then((page) => {
        setSeries(page.records);
        setSeriesTotal(page.total);
      })
      .catch(() => setSeries([]));
  }, [canBrowse, message]);

  useEffect(() => {
    if (!canBrowse) {
      return;
    }
    let cancelled = false;
    setLoading(true);
    fetchVideoPage({
      current,
      size: PAGE_SIZE,
      keyword: keyword || undefined,
      categoryId,
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
        const text = reason instanceof Error ? reason.message : '视频列表加载失败';
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
  }, [canBrowse, current, keyword, categoryId]);

  if (!canBrowse) {
    return (
      <Result
        status="403"
        title="暂无浏览权限"
        subTitle="浏览视频需要 sys:video:browse 权限，请联系租户管理员开通"
        extra={
          <Button type="primary" onClick={() => navigate('/home')}>
            返回首页
          </Button>
        }
      />
    );
  }

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Typography.Title level={3} style={{ margin: 0 }}>
        视频
      </Typography.Title>

      {series.length > 0 ? (
        <Card
          title="系列精选"
          size="small"
          extra={<Typography.Text type="secondary">共 {seriesTotal} 个系列</Typography.Text>}
        >
          <Row gutter={[16, 16]}>
            {series.map((item) => (
              <Col key={item.id} xs={24} sm={12} md={8} lg={6} xl={6}>
                <Card
                  size="small"
                  hoverable
                  onClick={() => navigate(`/series/${item.id}`)}
                  styles={{ body: { padding: 0 } }}
                >
                  <ContentCover url={item.coverUrl} title={item.title} height={120} />
                  <div style={{ padding: '10px 12px' }}>
                    <Typography.Text strong ellipsis style={{ display: 'block' }}>
                      {item.title}
                    </Typography.Text>
                    <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                      {item.creatorName || '系列'} · {item.episodeCount} 集
                    </Typography.Text>
                  </div>
                </Card>
              </Col>
            ))}
          </Row>
        </Card>
      ) : null}

      <Card
        title="全部视频"
        size="small"
        extra={
          <Space wrap>
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
              placeholder="搜索视频标题 / 副标题"
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
            description={keyword ? '没有匹配的视频' : '还没有已发布的视频'}
            image={Empty.PRESENTED_IMAGE_SIMPLE}
          />
        ) : (
          <Row gutter={[16, 16]}>
            {records.map((record) => (
              <Col key={record.id} xs={24} sm={12} md={8} lg={6}>
                <Card
                  size="small"
                  hoverable
                  onClick={() => navigate(`/videos/${record.id}`)}
                  styles={{ body: { padding: 0 } }}
                >
                  <ContentCover url={record.coverUrl} title={record.title} height={150} />
                  <div style={{ padding: '10px 12px' }}>
                    <Typography.Text strong ellipsis style={{ display: 'block' }}>
                      {record.title}
                    </Typography.Text>
                    <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                      {record.creatorName || '创作者'} · {formatDuration(record.durationMs)} ·{' '}
                      {formatCount(record.playCount)} 次播放
                    </Typography.Text>
                    <Space size={4} wrap style={{ marginTop: 6 }}>
                      {record.seriesId && record.seriesId > 0 ? (
                        <Tag color="purple">第 {record.episodeNo || 1} 集</Tag>
                      ) : null}
                      <Tag color={record.playable ? 'green' : 'default'}>
                        {record.playable ? '可播放' : '暂不可播'}
                      </Tag>
                      {record.tags?.slice(0, 1).map((tag) => (
                        <Tag key={tag}>{tag}</Tag>
                      ))}
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
              showTotal={(count) => `共 ${count} 条`}
              onChange={(page) => setCurrent(page)}
            />
          </div>
        ) : null}
      </Card>
    </Space>
  );
}
