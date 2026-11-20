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
import { useI18n } from '../../i18n';

type Translate = ReturnType<typeof useI18n>['t'];

const buildContentTypeOptions = (t: Translate) => [
  { value: 'SHORT_VIDEO', label: t('video.content.type.shortVideo') },
  { value: 'VIDEO', label: t('video.content.type.video') },
  { value: 'MOVIE', label: t('video.content.type.movie') },
];

const buildPublishStateOptions = (t: Translate) => [
  { value: 'DRAFT', label: t('video.content.state.draft') },
  { value: 'PENDING_REVIEW', label: t('video.content.state.pendingReview') },
  { value: 'PUBLISHED', label: t('video.content.state.published') },
  { value: 'OFFLINE', label: t('video.content.state.offline') },
];

const buildVisibilityOptions = (t: Translate) => [
  { value: 'PUBLIC', label: t('video.content.visibility.public') },
  { value: 'PRIVATE', label: t('video.content.visibility.private') },
];

/** 内容类型标签 */
function renderContentType(type: ContentType | undefined, t: Translate) {
  const map: Record<ContentType, { color: string; label: string }> = {
    SHORT_VIDEO: { color: 'cyan', label: t('video.content.type.shortVideo') },
    VIDEO: { color: 'blue', label: t('video.content.type.video') },
    MOVIE: { color: 'purple', label: t('video.content.type.movie') },
  };
  return type ? <Tag color={map[type].color}>{map[type].label}</Tag> : '-';
}

/** 发布状态标签 */
function renderPublishState(state: PublishState | undefined, t: Translate) {
  const map: Record<PublishState, { color: string; label: string }> = {
    DRAFT: { color: 'default', label: t('video.content.state.draft') },
    PENDING_REVIEW: { color: 'gold', label: t('video.content.state.pendingReview') },
    PUBLISHED: { color: 'success', label: t('video.content.state.published') },
    OFFLINE: { color: 'error', label: t('video.content.state.offline') },
  };
  return state ? <Tag color={map[state].color}>{map[state].label}</Tag> : '-';
}

/** 版权状态标签 */
function renderCopyrightState(
  state: CopyrightState | undefined,
  reason: string | undefined,
  t: Translate,
) {
  if (state === 'TAKEDOWN') {
    const tag = <Tag color="error">{t('video.content.copyright.takedown')}</Tag>;
    return reason ? (
      <Tooltip title={t('video.content.copyright.reason', { reason })}>{tag}</Tooltip>
    ) : (
      tag
    );
  }
  return <Tag color="success">{t('video.content.copyright.valid')}</Tag>;
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
  const { t } = useI18n();
  const tableRef = useRef<ProTableRef>(null);

  const contentTypeOptions = buildContentTypeOptions(t);
  const publishStateOptions = buildPublishStateOptions(t);
  const visibilityOptions = buildVisibilityOptions(t);

  const [categories, setCategories] = useState<CategoryView[]>([]);
  const [keyword, setKeyword] = useState<string | undefined>(undefined);
  const [contentType, setContentType] = useState<ContentType | undefined>(undefined);
  const [publishState, setPublishState] = useState<PublishState | undefined>(undefined);
  const [visibility, setVisibility] = useState<ContentVisibility | undefined>(undefined);
  const [categoryId, setCategoryId] = useState<string | undefined>(undefined);

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
      message.success(t('video.content.msg.published'));
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('video.content.msg.publishFailed'));
    }
  };

  const handleOffline = async (row: ContentView) => {
    try {
      await offlineContent(row.id);
      message.success(t('video.content.msg.offlined'));
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('video.content.msg.offlineFailed'));
    }
  };

  const confirmTakedown = async () => {
    if (!takedownTarget) {
      return;
    }
    if (!takedownReason.trim()) {
      message.warning(t('video.content.msg.takedownReasonRequired'));
      return;
    }
    try {
      await takedownContent(takedownTarget.id, takedownReason.trim());
      message.success(t('video.content.msg.takedownDone'));
      setTakedownTarget(null);
      setTakedownReason('');
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('video.content.msg.takedownFailed'));
    }
  };

  const columns: TableProps<ContentView>['columns'] = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    {
      title: t('video.content.col.content'),
      dataIndex: 'title',
      render: (value: string, row) => (
        <Space direction="vertical" size={0}>
          <Typography.Text strong>{value}</Typography.Text>
          {row.subtitle ? <Typography.Text type="secondary">{row.subtitle}</Typography.Text> : null}
          {/* seriesId 运行时为字符串，'0' 表示不属于任何系列 */}
          {row.seriesId && row.seriesId !== '0' ? (
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              {t('video.content.cell.series', {
                seriesId: row.seriesId,
                episode: row.episodeNo || 1,
              })}
            </Typography.Text>
          ) : null}
        </Space>
      ),
    },
    {
      title: t('video.content.col.type'),
      dataIndex: 'contentType',
      width: 100,
      render: (value: ContentType | undefined) => renderContentType(value, t),
    },
    {
      title: t('video.content.col.status'),
      dataIndex: 'publishState',
      width: 100,
      render: (value: PublishState | undefined) => renderPublishState(value, t),
    },
    {
      title: t('video.content.col.copyright'),
      dataIndex: 'copyrightState',
      width: 110,
      render: (value: CopyrightState | undefined, row) =>
        renderCopyrightState(value, row.takedownReason, t),
    },
    {
      title: t('video.content.col.visibility'),
      dataIndex: 'visibility',
      width: 90,
      render: (value: ContentVisibility | undefined) =>
        value === 'PRIVATE' ? (
          <Tag color="orange">{t('video.content.visibility.private')}</Tag>
        ) : (
          <Tag color="blue">{t('video.content.visibility.public')}</Tag>
        ),
    },
    {
      title: t('video.content.col.duration'),
      dataIndex: 'durationMs',
      width: 90,
      render: (value: number | undefined) => formatDuration(value),
    },
    {
      title: t('video.content.col.uploader'),
      dataIndex: 'uploaderId',
      width: 100,
      render: (value: number | undefined) => value ?? '-',
    },
    {
      title: t('video.content.col.counts'),
      key: 'counts',
      width: 120,
      render: (_, row) => `${row.playCount ?? 0} / ${row.likeCount ?? 0}`,
    },
    {
      title: t('video.content.col.publishedAt'),
      dataIndex: 'publishedAt',
      width: 170,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: t('video.content.col.action'),
      key: 'action',
      width: 230,
      render: (_, row) => (
        <Space size={4} wrap>
          {row.publishState !== 'PUBLISHED' && has('sys:video:content:publish') ? (
            <Popconfirm
              title={t('video.content.confirm.publish')}
              onConfirm={() => void handlePublish(row)}
            >
              <Button type="link" size="small">
                {t('video.content.action.publish')}
              </Button>
            </Popconfirm>
          ) : null}
          {row.publishState === 'PUBLISHED' && has('sys:video:content:offline') ? (
            <Popconfirm
              title={t('video.content.confirm.offline')}
              onConfirm={() => void handleOffline(row)}
            >
              <Button type="link" size="small" danger>
                {t('video.content.action.offline')}
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
              {t('video.content.action.takedown')}
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
        title={t('video.content.list.title')}
        columns={columns}
        request={pageContents}
        params={{ keyword, contentType, publishState, visibility, categoryId }}
        onError={(error) =>
          message.error(error instanceof Error ? error.message : t('video.content.msg.loadFailed'))
        }
        search={
          <SearchForm
            items={[
              {
                name: 'keyword',
                label: t('video.content.search.keyword'),
                element: (
                  <Input allowClear placeholder={t('video.content.search.keywordPlaceholder')} />
                ),
              },
              {
                name: 'contentType',
                label: t('video.content.search.type'),
                element: (
                  <Select
                    allowClear
                    placeholder={t('video.content.search.all')}
                    style={{ width: 120 }}
                    options={contentTypeOptions}
                  />
                ),
              },
              {
                name: 'publishState',
                label: t('video.content.search.status'),
                element: (
                  <Select
                    allowClear
                    placeholder={t('video.content.search.all')}
                    style={{ width: 120 }}
                    options={publishStateOptions}
                  />
                ),
              },
              {
                name: 'visibility',
                label: t('video.content.search.visibility'),
                element: (
                  <Select
                    allowClear
                    placeholder={t('video.content.search.all')}
                    style={{ width: 120 }}
                    options={visibilityOptions}
                  />
                ),
              },
              {
                name: 'categoryId',
                label: t('video.content.search.category'),
                element: (
                  <Select
                    allowClear
                    showSearch
                    optionFilterProp="label"
                    placeholder={t('video.content.search.all')}
                    style={{ width: 160 }}
                    options={categories.map((category) => ({
                      value: category.id,
                      label: category.name,
                    }))}
                  />
                ),
              },
            ]}
            onSearch={(values) => {
              setKeyword((values.keyword as string) || undefined);
              setContentType((values.contentType as ContentType) || undefined);
              setPublishState((values.publishState as PublishState) || undefined);
              setVisibility((values.visibility as ContentVisibility) || undefined);
              setCategoryId((values.categoryId as string) || undefined);
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
        title={
          takedownTarget
            ? t('video.content.takedown.titleWithName', { name: takedownTarget.title })
            : t('video.content.takedown.title')
        }
        okText={t('video.content.takedown.ok')}
        okButtonProps={{ danger: true }}
        onOk={() => void confirmTakedown()}
        onCancel={() => {
          setTakedownTarget(null);
          setTakedownReason('');
        }}
      >
        <Space direction="vertical" style={{ width: '100%' }}>
          <span>{t('video.content.takedown.description')}</span>
          <Input.TextArea
            rows={3}
            maxLength={500}
            showCount
            value={takedownReason}
            onChange={(event) => setTakedownReason(event.target.value)}
            placeholder={t('video.content.takedown.reasonPlaceholder')}
          />
        </Space>
      </Modal>
    </>
  );
}
