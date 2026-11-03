// 我的作品：短视频 / 视频分页，编辑元数据、发布 / 下架、删除

import {
  Alert,
  App,
  Button,
  Form,
  Input,
  InputNumber,
  Popconfirm,
  Result,
  Select,
  Space,
  Tabs,
  Tooltip,
  Typography,
} from 'antd';
import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  FormModal,
  ProTable,
  SearchForm,
  type ProTableRef,
} from '@bluewhale/whale-ui';
import {
  deleteShortVideo,
  deleteVideo,
  fetchCategories,
  fetchMyShorts,
  fetchMyVideos,
  fetchOwnerContent,
  fetchSeriesPage,
  fetchTags,
  offlineShortVideo,
  offlineVideo,
  publishShortVideo,
  publishVideo,
  updateShortVideo,
  updateVideo,
  type CategoryView,
  type ContentEditView,
  type ContentVisibility,
  type OwnerContentView,
  type PageQuery,
  type PublishState,
  type SeriesView,
  type TagView,
} from '../api/video';
import { hasPermission } from '../api/client';
import {
  formatCount,
  formatDateTime,
  formatDuration,
  renderCopyrightState,
  renderPublishState,
  renderVisibility,
} from '../utils/videoDisplay';

type MineType = 'SHORT_VIDEO' | 'VIDEO';

interface EditFormValues {
  title: string;
  subtitle?: string;
  description?: string;
  categoryId?: number;
  seriesId?: number;
  episodeNo?: number;
  tagIds?: number[];
  visibility: ContentVisibility;
}

const VISIBILITY_OPTIONS = [
  { value: 'PUBLIC', label: '公开' },
  { value: 'PRIVATE', label: '私密' },
];

const PUBLISH_STATE_OPTIONS = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'PUBLISHED', label: '已发布' },
  { value: 'OFFLINE', label: '已下架' },
];

export default function MinePage() {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const tableRef = useRef<ProTableRef>(null);
  const [editForm] = Form.useForm<EditFormValues>();

  const canUpload = hasPermission('sys:video:upload');
  const [contentType, setContentType] = useState<MineType>('SHORT_VIDEO');
  const [keyword, setKeyword] = useState<string | undefined>(undefined);
  const [visibility, setVisibility] = useState<ContentVisibility | undefined>(undefined);
  const [publishState, setPublishState] = useState<PublishState | undefined>(undefined);

  const [categories, setCategories] = useState<CategoryView[]>([]);
  const [tags, setTags] = useState<TagView[]>([]);
  const [seriesList, setSeriesList] = useState<SeriesView[]>([]);
  const [editing, setEditing] = useState<ContentEditView | null>(null);
  const [editOpen, setEditOpen] = useState(false);

  useEffect(() => {
    if (!canUpload) {
      return;
    }
    fetchCategories()
      .then(setCategories)
      .catch((error) => message.error(error instanceof Error ? error.message : '分类加载失败'));
    fetchTags()
      .then(setTags)
      .catch((error) => message.error(error instanceof Error ? error.message : '标签加载失败'));
    fetchSeriesPage({ size: 100 })
      .then((page) => setSeriesList(page.records))
      .catch(() => setSeriesList([]));
  }, [canUpload, message]);

  const request = (query: PageQuery) =>
    contentType === 'SHORT_VIDEO' ? fetchMyShorts(query) : fetchMyVideos(query);

  const openEdit = async (row: OwnerContentView) => {
    try {
      const data = await fetchOwnerContent(row.id, contentType);
      setEditing(data);
      setEditOpen(true);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '作品详情加载失败');
    }
  };

  const handleSave = async (values: EditFormValues) => {
    if (!editing?.videoFileId) {
      message.error('缺少视频文件标识，无法保存，请联系管理员');
      throw new Error('缺少视频文件标识');
    }
    const input = {
      videoFileId: editing.videoFileId,
      coverFileId: editing.coverFileId,
      title: values.title,
      subtitle: values.subtitle || undefined,
      description: values.description || undefined,
      durationMs: editing.durationMs,
      width: editing.width,
      height: editing.height,
      sizeBytes: editing.sizeBytes,
      categoryId: values.categoryId,
      seriesId: contentType === 'VIDEO' ? values.seriesId ?? 0 : 0,
      episodeNo: contentType === 'VIDEO' && values.seriesId ? values.episodeNo : 0,
      tagIds: values.tagIds?.length ? values.tagIds : undefined,
      visibility: values.visibility,
      source: editing.source,
      publishNow: false,
    };
    try {
      if (contentType === 'SHORT_VIDEO') {
        await updateShortVideo(editing.id, input);
      } else {
        await updateVideo(editing.id, input);
      }
      message.success('作品已更新');
      setEditOpen(false);
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '作品更新失败');
      throw error;
    }
  };

  const handlePublishToggle = async (row: OwnerContentView, publish: boolean) => {
    try {
      if (contentType === 'SHORT_VIDEO') {
        await (publish ? publishShortVideo(row.id) : offlineShortVideo(row.id));
      } else {
        await (publish ? publishVideo(row.id) : offlineVideo(row.id));
      }
      message.success(publish ? '作品已发布' : '作品已下架');
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '操作失败');
    }
  };

  const handleDelete = async (row: OwnerContentView) => {
    try {
      await (contentType === 'SHORT_VIDEO' ? deleteShortVideo(row.id) : deleteVideo(row.id));
      message.success('作品已删除');
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '删除失败');
    }
  };

  if (!canUpload) {
    return (
      <Result
        status="403"
        title="暂无作品管理权限"
        subTitle="管理作品需要 sys:video:upload 权限，请联系租户管理员开通"
        extra={
          <Button type="primary" onClick={() => navigate('/home')}>
            返回首页
          </Button>
        }
      />
    );
  }

  const categoryOptions = categories
    .filter((category) => category.contentTypeScope === 'ALL' || category.contentTypeScope === contentType)
    .map((category) => ({ value: category.id, label: category.name }));

  // 当前作品所属系列可能已下架而不在可选列表中，仍需回填展示
  const seriesOptions = seriesList.map((item) => ({
    value: item.id,
    label: `${item.title}（${item.episodeCount} 集）`,
  }));
  if (editing?.seriesId && editing.seriesId > 0 && !seriesList.some((item) => item.id === editing.seriesId)) {
    seriesOptions.unshift({ value: editing.seriesId, label: `系列 #${editing.seriesId}` });
  }

  return (
    <>
      <Tabs
        activeKey={contentType}
        onChange={(key) => setContentType(key as MineType)}
        items={[
          { key: 'SHORT_VIDEO', label: '我的短视频' },
          { key: 'VIDEO', label: '我的视频' },
        ]}
      />
      <ProTable<OwnerContentView, PageQuery>
        key={contentType}
        ref={tableRef}
        rowKey="id"
        title={contentType === 'SHORT_VIDEO' ? '我的短视频' : '我的视频'}
        request={request}
        params={{ keyword, visibility, publishState }}
        onError={(error) => message.error(error instanceof Error ? error.message : '作品加载失败')}
        search={
          <SearchForm
            items={[
              {
                name: 'keyword',
                label: '关键词',
                element: <Input allowClear placeholder="标题 / 副标题" />,
              },
              {
                name: 'visibility',
                label: '可见性',
                element: (
                  <Select allowClear placeholder="全部" style={{ width: 120 }} options={VISIBILITY_OPTIONS} />
                ),
              },
              {
                name: 'publishState',
                label: '状态',
                element: (
                  <Select allowClear placeholder="全部" style={{ width: 120 }} options={PUBLISH_STATE_OPTIONS} />
                ),
              },
            ]}
            onSearch={(values) => {
              setKeyword((values.keyword as string) || undefined);
              setVisibility((values.visibility as ContentVisibility) || undefined);
              setPublishState((values.publishState as PublishState) || undefined);
            }}
            onReset={() => {
              setKeyword(undefined);
              setVisibility(undefined);
              setPublishState(undefined);
            }}
          />
        }
        toolbar={
          <Button type="primary" onClick={() => navigate('/upload')}>
            上传新作品
          </Button>
        }
        columns={[
          {
            title: '作品',
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
            title: '可见性',
            dataIndex: 'visibility',
            width: 90,
            render: (value: ContentVisibility | undefined) => renderVisibility(value),
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
            render: (value, row) => renderCopyrightState(value, row.takedownReason),
          },
          {
            title: '时长',
            dataIndex: 'durationMs',
            width: 90,
            render: (value: number | undefined) => formatDuration(value),
          },
          {
            title: '播放 / 点赞',
            key: 'counts',
            width: 120,
            render: (_, row) => `${formatCount(row.playCount)} / ${formatCount(row.likeCount)}`,
          },
          {
            title: '创建时间',
            dataIndex: 'createTime',
            width: 150,
            render: (value: string | undefined) => formatDateTime(value),
          },
          {
            title: '操作',
            key: 'action',
            width: 260,
            render: (_, row) => (
              <Space size={4} wrap>
                {contentType === 'SHORT_VIDEO' ? (
                  <Button type="link" size="small" onClick={() => navigate(`/shorts/${row.id}`)}>
                    详情
                  </Button>
                ) : null}
                <Button type="link" size="small" onClick={() => void openEdit(row)}>
                  编辑
                </Button>
                {row.publishState === 'PUBLISHED' ? (
                  <Button type="link" size="small" onClick={() => void handlePublishToggle(row, false)}>
                    下架
                  </Button>
                ) : (
                  <Button type="link" size="small" onClick={() => void handlePublishToggle(row, true)}>
                    发布
                  </Button>
                )}
                <Popconfirm title="确认删除该作品？" onConfirm={() => void handleDelete(row)}>
                  <Button type="link" size="small" danger>
                    删除
                  </Button>
                </Popconfirm>
              </Space>
            ),
          },
        ]}
        scroll={{ x: 960 }}
      />

      <Alert
        style={{ marginTop: 16 }}
        type="info"
        showIcon
        message="版权下架的作品不可播放且不对其他用户可见"
        description={
          <span>
            如认为处理有误，请联系租户运营复核。版权下架原因会展示在「版权」列的提示中。
            {editing?.takedownReason ? `当前编辑作品下架原因：${editing.takedownReason}` : ''}
          </span>
        }
      />

      <FormModal<EditFormValues>
        open={editOpen}
        title={editing ? `编辑作品：${editing.title ?? ''}` : '编辑作品'}
        form={editForm}
        width={640}
        initialValues={{
          title: editing?.title,
          subtitle: editing?.subtitle,
          description: editing?.description,
          categoryId: editing?.categoryId,
          seriesId: editing?.seriesId && editing.seriesId > 0 ? editing.seriesId : undefined,
          episodeNo: editing?.episodeNo && editing.episodeNo > 0 ? editing.episodeNo : undefined,
          tagIds: editing?.tagIds,
          visibility: editing?.visibility ?? 'PUBLIC',
        }}
        onOk={handleSave}
        onCancel={() => setEditOpen(false)}
      >
        <Form.Item name="title" label="标题" rules={[{ required: true, message: '请输入标题' }]}>
          <Input maxLength={200} showCount />
        </Form.Item>
        <Form.Item name="subtitle" label="副标题">
          <Input maxLength={200} />
        </Form.Item>
        <Form.Item name="description" label="简介">
          <Input.TextArea maxLength={2000} showCount autoSize={{ minRows: 3, maxRows: 6 }} />
        </Form.Item>
        <Form.Item name="categoryId" label="分类">
          <Select allowClear placeholder="选择分类" options={categoryOptions} />
        </Form.Item>
        {contentType === 'VIDEO' ? (
          <>
            <Form.Item name="seriesId" label="所属系列">
              <Select allowClear placeholder="不加入系列" options={seriesOptions} />
            </Form.Item>
            <Form.Item noStyle shouldUpdate={(previous, next) => previous.seriesId !== next.seriesId}>
              {({ getFieldValue }) =>
                getFieldValue('seriesId') ? (
                  <Form.Item name="episodeNo" label="集数" rules={[{ required: true, message: '请输入集数' }]}>
                    <InputNumber min={1} precision={0} style={{ width: 160 }} placeholder="第几集" />
                  </Form.Item>
                ) : null
              }
            </Form.Item>
          </>
        ) : null}
        <Form.Item name="tagIds" label="标签">
          <Select
            mode="multiple"
            allowClear
            placeholder="选择标签"
            options={tags.map((tag) => ({ value: tag.id, label: tag.name }))}
          />
        </Form.Item>
        <Form.Item name="visibility" label="可见性" rules={[{ required: true }]}>
          <Select options={VISIBILITY_OPTIONS} />
        </Form.Item>
        {editing?.publishState === 'PUBLISHED' ? (
          <Tooltip title="已发布作品修改后会立即失效缓存，用户侧可见性随可见性设置变化">
            <Typography.Text type="secondary">当前作品已发布</Typography.Text>
          </Tooltip>
        ) : null}
      </FormModal>
    </>
  );
}
