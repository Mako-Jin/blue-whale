// 视频首页：聚合最新短视频 / 视频 / 电影 + 分类入口（M2 起接入真实数据）

import { Alert, App, Button, Card, Col, Empty, Result, Row, Space, Tag, Typography } from 'antd';
import { useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { fetchHome, fetchMeta, type HomeView, type PublicContentView, type VideoMeta } from '../api/video';
import { hasPermission } from '../api/client';
import { formatCount, formatDuration } from '../utils/videoDisplay';

export default function HomePage() {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const canBrowse = hasPermission('sys:video:browse');

  const [home, setHome] = useState<HomeView | null>(null);
  const [meta, setMeta] = useState<VideoMeta | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!canBrowse) {
      return;
    }
    fetchHome()
      .then((data) => {
        setHome(data);
        setError(null);
      })
      .catch((reason: unknown) => {
        const text = reason instanceof Error ? reason.message : '首页数据加载失败';
        setError(text);
        message.error(text);
      });
    fetchMeta()
      .then(setMeta)
      .catch(() => message.error('服务元信息加载失败'));
  }, [canBrowse, message]);

  if (!canBrowse) {
    return (
      <Result
        status="403"
        title="暂无浏览权限"
        subTitle="浏览视频内容需要 sys:video:browse 权限，请联系租户管理员开通"
      />
    );
  }

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Typography.Title level={3} style={{ margin: 0 }}>
        视频平台
      </Typography.Title>
      {error ? <Alert type="warning" showIcon message={error} /> : null}
      <Alert
        type="info"
        showIcon
        message="短视频 / 视频 / 电影已开放：feed、系列、榜单、片单与播放票据"
        description="电影由运营入库并校验授权有效期；上传请先签署内容合法与版权无争议声明。"
        action={<Button onClick={() => navigate('/shorts')}>去看短视频</Button>}
      />

      {home?.categories?.length ? (
        <Card title="分类" size="small">
          <Space wrap>
            {home.categories.map((category) => (
              <Tag
                key={category.id}
                color="blue"
                style={{ cursor: category.contentTypeScope === 'MOVIE' ? 'default' : 'pointer' }}
                onClick={() => {
                  if (category.contentTypeScope !== 'MOVIE') {
                    navigate('/shorts');
                  }
                }}
              >
                {category.name}
              </Tag>
            ))}
          </Space>
        </Card>
      ) : null}

      <ContentSection
        title="最新短视频"
        records={home?.shorts}
        emptyText="还没有短视频，上传第一条吧"
        onOpen={(record) => navigate(`/shorts/${record.id}`)}
        extra={<Button type="link" onClick={() => navigate('/shorts')}>全部短视频</Button>}
      />
      <ContentSection
        title="视频"
        records={home?.videos}
        emptyText="还没有视频，稍后再来看看"
        onOpen={() => navigate('/videos')}
        extra={<Button type="link" onClick={() => navigate('/videos')}>全部视频</Button>}
      />
      <ContentSection
        title="电影"
        records={home?.movies}
        emptyText="还没有已上架电影"
        onOpen={() => navigate('/movies')}
        extra={<Button type="link" onClick={() => navigate('/movies')}>全部电影</Button>}
      />

      {meta ? (
        <Card title="服务能力" size="small">
          <Space wrap size={24}>
            <Typography.Text type="secondary">服务：{meta.service}</Typography.Text>
            <Typography.Text type="secondary">声明版本：{meta.declarationVersion}</Typography.Text>
            <Typography.Text type="secondary">
              票据有效期：{meta.playTicketTtlSeconds}s（建议 {meta.playTicketRefreshSeconds}s 刷新）
            </Typography.Text>
            <Typography.Text type="secondary">评论先审：{meta.commentPreAudit ? '是' : '否'}</Typography.Text>
          </Space>
        </Card>
      ) : null}
    </Space>
  );
}

function ContentSection({
  title,
  records,
  emptyText,
  onOpen,
  extra,
}: {
  title: string;
  records?: PublicContentView[];
  emptyText: string;
  onOpen: (record: PublicContentView) => void;
  extra?: ReactNode;
}) {
  return (
    <Card title={title} size="small" extra={extra}>
      {records?.length ? (
        <Row gutter={[16, 16]}>
          {records.map((record) => (
            <Col key={record.id} xs={24} sm={12} md={8} lg={6}>
              <Card size="small" hoverable onClick={() => onOpen(record)}>
                <Space direction="vertical" size={4} style={{ width: '100%' }}>
                  <Typography.Text strong ellipsis>
                    {record.title}
                  </Typography.Text>
                  <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                    {record.creatorName || '创作者'} · {formatDuration(record.durationMs)} ·{' '}
                    {formatCount(record.playCount)} 次播放
                  </Typography.Text>
                  <Space size={4}>
                    <Tag color={record.playable ? 'green' : 'default'}>{record.playable ? '可播放' : '暂不可播'}</Tag>
                    {record.tags?.slice(0, 1).map((tag) => (
                      <Tag key={tag}>{tag}</Tag>
                    ))}
                  </Space>
                </Space>
              </Card>
            </Col>
          ))}
        </Row>
      ) : (
        <Empty description={emptyText} image={Empty.PRESENTED_IMAGE_SIMPLE} />
      )}
    </Card>
  );
}
