// 视频运营 - 内容管理（M3）：分页查询 / 发布 / 下架 / 版权下架
// 权限码：sys:video:content:list / publish / offline / takedown

import {
  App,
  Button,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Tag,
  Tooltip,
  Typography,
  type TableProps,
} from 'antd';
import { useEffect, useRef, useState } from 'react';
import { ProTable, SearchForm, usePermission, type ProTableRef } from '@bluewhale/whale-ui';
import {
  listVideoCategories,
  offlineContent,
  pageContents,
  publishContent,
  takedownContent,
  type CategoryView,
  type ContentQuery,
  type ContentType,
  type ContentView,
  type ContentVisibility,
  type CopyrightState,
  type PublishState,
} from '../../api/video';

const CONTENT_TYPE_OPTIONS = [
  { value: 'SHORT_VIDEO', label: '短视频' },
  { value: 'VIDEO', label: '视频' },
  { value: 'MOVIE', label: '电影' },
];

const PUBLISH_STATE_OPTIONS = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'PENDING_REVIEW', label: '待审核' },
  { value: 'PUBLISHED', label: '已发布' },
  { value: 'OFFLINE', label: '已下架' },
];

const VISIBILITY_OPTIONS = [
  { value: 'PUBLIC', label: '公开' },
  { value: 'PRIVATE', label: '私密' },
];

/** 内容类型标签 */
function renderContentType(type?: ContentType) {
  const map: Record<ContentType, { color: string; label: string }> = {
    SHORT_VIDEO: { color: 'cyan', label: '短视频' },
    VIDEO: { color: 'blue', label: '视频' },
    MOVIE: { color: 'purple', label: '电影' },
  };
  return type ? <Tag color={map[type].color}>{map[type].label}</Tag> : '-';
}

/** 发布状态标签 */
function renderPublishState(state?: PublishState) {
  const map: Record<PublishState, { color: string; label: string }> = {
    DRAFT: { color: 'default', label: '草稿' },
    PENDING_REVIEW: { color: 'gold', label: '待审核' },
    PUBLISHED: { color: 'success', label: '已发布' },
    OFFLINE: { color: 'error', label: '已下架' },
  };
  return state ? <Tag color={map[state].color}>{map[state].label}</Tag> : '-';
}

/** 版权状态标签 */
function renderCopyrightState(state?: CopyrightState, reason?: string) {
  if (state === 'TAKEDOWN') {
    const tag = <Tag color="error">版权下架</Tag>;
    return reason ? <Tooltip title={`下架原因：${reason}`}>{tag}</Tooltip> : tag;
  }
  return <Tag color="success">版权有效</Tag>;
}

/** 时长格式化 */
function formatDuration(value?: number): string {
  if (!value || value <= 0) {
    return '-';
  }
  const totalSeconds = Math.floor(value / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}:${String(seconds).padStart(2, '0')}`;
}

export default function ContentList() {
  const { message } = App.useApp();
  const { has } = usePermission();
  const tableRef = useRef<ProTableRef>(null);

  const [categories, setCategories] = useState<CategoryView[]>([]);
  const [keyword, setKeyword] = useState<string | undefined>(undefined);
  const [contentType, setContentType] = useState<ContentType | undefined>(undefined);
  const [publishState, setPublishState] = useState<PublishState | undefined>(undefined);
  const [visibility, setVisibility] = useState<ContentVisibility | undefined>(undefined);
  const [categoryId, setCategoryId] = useState<number | undefined>(undefined);

  const [takedownTarget, setTakedownTarget] = useState<ContentView | null>(null);
  const [takedownReason, setTakedownReason] = useState('');

  useEffect(() => {
    listVideoCategories()
      .then(setCategories)
      .catch(() => setCategories([]));
  }, []);

  const handlePublish = async (row: ContentView) => {
    try {
      await publishContent(row.id);
      message.success('内容已发布');
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '发布失败');
    }
  };

  const handleOffline = async (row: ContentView) => {
    try {
      await offlineContent(row.id);
      message.success('内容已下架');
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '下架失败');
    }
  };

  const confirmTakedown = async () => {
    if (!takedownTarget) {
      return;
    }
    if (!takedownReason.trim()) {
      message.warning('请填写版权下架原因');
      return;
    }
    try {
      await takedownContent(takedownTarget.id, takedownReason.trim());
      message.success('已完成版权下架');
      setTakedownTarget(null);
      setTakedownReason('');
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '版权下架失败');
    }
  };

  const columns: TableProps<ContentView>['columns'] = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    {
      title: '内容',
      dataIndex: 'title',
      render: (value: string, row) => (
        <Space direction="vertical" size={0}>
          <Typography.Text strong>{value}</Typography.Text>
          {row.subtitle ? <Typography.Text type="secondary">{row.subtitle}</Typography.Text> : null}
          {row.seriesId && row.seriesId > 0 ? (
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              系列 #{row.seriesId} · 第 {row.episodeNo || 1} 集
            </Typography.Text>
          ) : null}
        </Space>
      ),
    },
    {
      title: '类型',
      dataIndex: 'contentType',
      width: 100,
      render: (value: ContentType | undefined) => renderContentType(value),
    },
    {
      title: '状态',
      dataIndex: 'publishState',
      width: 100,
      render: (value: PublishState | undefined) => renderPublishState(value),
    },
    {
      title: '版权',
      dataIndex: 'copyrightState',
      width: 110,
      render: (value: CopyrightState | undefined, row) => renderCopyrightState(value, row.takedownReason),
    },
    {
      title: '可见性',
      dataIndex: 'visibility',
      width: 90,
      render: (value: ContentVisibility | undefined) =>
        value === 'PRIVATE' ? <Tag color="orange">私密</Tag> : <Tag color="blue">公开</Tag>,
    },
    {
      title: '时长',
      dataIndex: 'durationMs',
      width: 90,
      render: (value: number | undefined) => formatDuration(value),
    },
    {
      title: '上传者',
      dataIndex: 'uploaderId',
      width: 100,
      render: (value: number | undefined) => value ?? '-',
    },
    {
      title: '播放 / 点赞',
      key: 'counts',
      width: 120,
      render: (_, row) => `${row.playCount ?? 0} / ${row.likeCount ?? 0}`,
    },
    {
      title: '发布时间',
      dataIndex: 'publishedAt',
      width: 170,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: '操作',
      key: 'action',
      width: 230,
      render: (_, row) => (
        <Space size={4} wrap>
          {row.publishState !== 'PUBLISHED' && has('sys:video:content:publish') ? (
            <Popconfirm title="确认发布该内容？" onConfirm={() => void handlePublish(row)}>
              <Button type="link" size="small">
                发布
              </Button>
            </Popconfirm>
          ) : null}
          {row.publishState === 'PUBLISHED' && has('sys:video:content:offline') ? (
            <Popconfirm title="确认下架该内容？" onConfirm={() => void handleOffline(row)}>
              <Button type="link" size="small" danger>
                下架
              </Button>
            </Popconfirm>
          ) : null}
          {row.copyrightState !== 'TAKEDOWN' && has('sys:video:content:takedown') ? (
            <Button
              type="link"
              size="small"
              danger
              onClick={() => {
                setTakedownTarget(row);
                setTakedownReason('');
              }}
            >
              版权下架
            </Button>
          ) : null}
        </Space>
      ),
    },
  ];

  return (
    <>
      <ProTable<ContentView, ContentQuery>
        ref={tableRef}
        rowKey="id"
        title="内容管理"
        columns={columns}
        request={pageContents}
        params={{ keyword, contentType, publishState, visibility, categoryId }}
        onError={(error) => message.error(error instanceof Error ? error.message : '内容列表加载失败')}
        search={
          <SearchForm
            items={[
              { name: 'keyword', label: '关键词', element: <Input allowClear placeholder="标题 / 副标题" /> },
              {
                name: 'contentType',
                label: '类型',
                element: (
                  <Select allowClear placeholder="全部" style={{ width: 120 }} options={CONTENT_TYPE_OPTIONS} />
                ),
              },
              {
                name: 'publishState',
                label: '状态',
                element: (
                  <Select allowClear placeholder="全部" style={{ width: 120 }} options={PUBLISH_STATE_OPTIONS} />
                ),
              },
              {
                name: 'visibility',
                label: '可见性',
                element: (
                  <Select allowClear placeholder="全部" style={{ width: 120 }} options={VISIBILITY_OPTIONS} />
                ),
              },
              {
                name: 'categoryId',
                label: '分类',
                element: (
                  <Select
                    allowClear
                    showSearch
                    optionFilterProp="label"
                    placeholder="全部"
                    style={{ width: 160 }}
                    options={categories.map((category) => ({ value: category.id, label: category.name }))}
                  />
                ),
              },
            ]}
            onSearch={(values) => {
              setKeyword((values.keyword as string) || undefined);
              setContentType((values.contentType as ContentType) || undefined);
              setPublishState((values.publishState as PublishState) || undefined);
              setVisibility((values.visibility as ContentVisibility) || undefined);
              setCategoryId((values.categoryId as number) || undefined);
            }}
            onReset={() => {
              setKeyword(undefined);
              setContentType(undefined);
              setPublishState(undefined);
              setVisibility(undefined);
              setCategoryId(undefined);
            }}
          />
        }
      />

      <Modal
        open={takedownTarget !== null}
        title={takedownTarget ? `版权下架：${takedownTarget.title}` : '版权下架'}
        okText="确认下架"
        okButtonProps={{ danger: true }}
        onOk={() => void confirmTakedown()}
        onCancel={() => {
          setTakedownTarget(null);
          setTakedownReason('');
        }}
      >
        <Space direction="vertical" style={{ width: '100%' }}>
          <span>版权下架将强制下线并拒绝播放，请填写原因（留痕可追溯）。</span>
          <Input.TextArea
            rows={3}
            maxLength={500}
            showCount
            value={takedownReason}
            onChange={(event) => setTakedownReason(event.target.value)}
            placeholder="如：权利人投诉 / 授权到期"
          />
        </Space>
      </Modal>
    </>
  );
}
