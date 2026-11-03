// 视频运营 - 电影管理（M4）：分页 / 入库 / 编辑 / 发布（授权校验）/ 下架 / 版权下架 / 删除
// 权限码：sys:video:content:list / add / edit / delete / publish / offline / takedown + movie:manage

import {
  Alert,
  App,
  Button,
  DatePicker,
  Form,
  Image,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Tag,
  Typography,
  Upload,
  type TableProps,
} from 'antd';
import { useEffect, useRef, useState } from 'react';
import {
  AuthButton,
  FormModal,
  ProTable,
  SearchForm,
  usePermission,
  type ProTableRef,
} from '@bluewhale/whale-ui';
import {
  createMovie,
  deleteMovie,
  listVideoCategories,
  offlineMovie,
  pageMovies,
  publishMovie,
  takedownMovie,
  updateMovie,
  type CategoryView,
  type LicenseSource,
  type MovieAdminView,
  type MovieQuery,
  type MovieSaveInput,
  type PublishState,
} from '../../api/video';
import { uploadFile } from '../../api/file';

/** 授权来源选项 */
const LICENSE_SOURCE_OPTIONS: { value: LicenseSource; label: string }[] = [
  { value: 'OWNED', label: '自有版权' },
  { value: 'LICENSED', label: '授权引进' },
  { value: 'PUBLIC_DOMAIN', label: '公版' },
];

/** 发布状态选项（电影无待审核） */
const PUBLISH_STATE_OPTIONS = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'PUBLISHED', label: '已发布' },
  { value: 'OFFLINE', label: '已下架' },
];

/** 排序选项 */
const SORT_OPTIONS = [
  { value: 'LATEST', label: '最新入库' },
  { value: 'HOT', label: '播放最多' },
];

/** 上映日期格式（LocalDate） */
const DATE_FORMAT = 'YYYY-MM-DD';

/** 授权到期格式（LocalDateTime，展示与回填均用空格分隔） */
const DATE_TIME_FORMAT = 'YYYY-MM-DD HH:mm:ss';

interface MovieFormValues {
  title: string;
  subtitle?: string;
  description?: string;
  videoFileId?: number;
  durationMs?: number;
  width?: number;
  height?: number;
  categoryId?: number;
  creatorId?: number;
  originalTitle?: string;
  director?: string;
  actors?: string;
  region?: string;
  language?: string;
  rating?: number;
  licenseSource: LicenseSource;
  licenseNo?: string;
}

/** ISO 日期时间转展示文本（后端返回带 T，表格与表单一律用空格分隔） */
function toDisplayDateTime(value?: string): string {
  return value ? value.replace('T', ' ') : '-';
}

/** 展示文本转后端可解析的 ISO 日期时间 */
function toIsoDateTime(value?: string): string | undefined {
  const text = value?.trim();
  if (!text) {
    return undefined;
  }
  return text.includes('T') ? text : text.replace(' ', 'T');
}

/** 授权是否已过期（空值视为未知，不标红） */
function isLicenseExpired(value?: string): boolean {
  if (!value) {
    return false;
  }
  const parsed = Date.parse(toDisplayDateTime(value));
  return Number.isNaN(parsed) ? false : parsed <= Date.now();
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

/** 授权来源标签 */
function renderLicenseSource(source?: LicenseSource) {
  if (!source) {
    return '-';
  }
  const item = LICENSE_SOURCE_OPTIONS.find((option) => option.value === source);
  const color = source === 'OWNED' ? 'blue' : source === 'LICENSED' ? 'purple' : 'cyan';
  return <Tag color={color}>{item?.label ?? source}</Tag>;
}

/** 时长毫秒 → mm:ss */
function formatDuration(value?: number): string {
  if (!value || value <= 0) {
    return '-';
  }
  const totalSeconds = Math.floor(value / 1000);
  return `${Math.floor(totalSeconds / 60)}:${String(totalSeconds % 60).padStart(2, '0')}`;
}

export default function MovieList() {
  const { message } = App.useApp();
  const { has } = usePermission();
  const tableRef = useRef<ProTableRef>(null);
  const [form] = Form.useForm<MovieFormValues>();

  const [categories, setCategories] = useState<CategoryView[]>([]);
  const [keyword, setKeyword] = useState<string | undefined>(undefined);
  const [categoryId, setCategoryId] = useState<number | undefined>(undefined);
  const [publishState, setPublishState] = useState<PublishState | undefined>(undefined);
  const [sortBy, setSortBy] = useState<'LATEST' | 'HOT' | undefined>(undefined);

  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<MovieAdminView | null>(null);
  const [releaseDateValue, setReleaseDateValue] = useState<string | undefined>(undefined);
  const [licenseExpireValue, setLicenseExpireValue] = useState<string | undefined>(undefined);
  const [posterFileId, setPosterFileId] = useState<number | undefined>(undefined);
  const [posterName, setPosterName] = useState('');
  const [backdropFileId, setBackdropFileId] = useState<number | undefined>(undefined);
  const [backdropName, setBackdropName] = useState('');
  const [uploadingVideo, setUploadingVideo] = useState(false);
  const [uploadingPoster, setUploadingPoster] = useState(false);
  const [uploadingBackdrop, setUploadingBackdrop] = useState(false);

  const [takedownTarget, setTakedownTarget] = useState<MovieAdminView | null>(null);
  const [takedownReason, setTakedownReason] = useState('');

  useEffect(() => {
    listVideoCategories()
      .then(setCategories)
      .catch(() => setCategories([]));
  }, []);

  const resetMediaState = () => {
    setReleaseDateValue(undefined);
    setLicenseExpireValue(undefined);
    setPosterFileId(undefined);
    setPosterName('');
    setBackdropFileId(undefined);
    setBackdropName('');
  };

  const openCreate = () => {
    setEditing(null);
    resetMediaState();
    setModalOpen(true);
  };

  const openEdit = (row: MovieAdminView) => {
    setEditing(row);
    resetMediaState();
    setReleaseDateValue(row.releaseDate);
    setLicenseExpireValue(row.licenseExpireAt ? toDisplayDateTime(row.licenseExpireAt) : undefined);
    setModalOpen(true);
  };

  const handleUpload = (
    file: File,
    done: (fileId: number, name: string) => void,
    setUploading: (value: boolean) => void,
    label: string,
  ) => {
    setUploading(true);
    uploadFile(file)
      .then((uploaded) => {
        done(uploaded.id, uploaded.originalName ?? file.name);
        message.success(`${label}成功`);
      })
      .catch((error) => message.error(error instanceof Error ? error.message : `${label}失败`))
      .finally(() => setUploading(false));
  };

  const handleSave = async (values: MovieFormValues) => {
    const expireAt = toIsoDateTime(licenseExpireValue);
    if (!expireAt) {
      message.warning('请选择授权到期时间');
      throw new Error('missing license expire at');
    }
    if (!editing && (!values.videoFileId || values.videoFileId <= 0)) {
      message.warning('请上传视频文件或填写视频文件 ID');
      throw new Error('missing video file');
    }
    const payload: MovieSaveInput = {
      title: values.title,
      subtitle: values.subtitle || undefined,
      description: values.description || undefined,
      videoFileId: values.videoFileId,
      durationMs: values.durationMs,
      width: values.width,
      height: values.height,
      categoryId: values.categoryId ?? 0,
      creatorId: values.creatorId ?? 0,
      originalTitle: values.originalTitle || undefined,
      director: values.director || undefined,
      actors: values.actors || undefined,
      region: values.region || undefined,
      language: values.language || undefined,
      rating: values.rating,
      licenseSource: values.licenseSource,
      licenseNo: values.licenseNo || undefined,
      licenseExpireAt: expireAt,
      releaseDate: releaseDateValue || undefined,
    };
    if (posterFileId !== undefined) {
      payload.posterFileId = posterFileId;
    }
    if (backdropFileId !== undefined) {
      payload.backdropFileId = backdropFileId;
    }
    try {
      if (editing) {
        await updateMovie(editing.id, payload);
        message.success('电影已更新');
      } else {
        await createMovie(payload);
        message.success('电影已入库（草稿），授权有效时可发布');
      }
      setModalOpen(false);
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存失败');
      throw error;
    }
  };

  const handlePublish = async (row: MovieAdminView) => {
    try {
      await publishMovie(row.id);
      message.success('电影已发布');
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '发布失败（请检查授权是否仍在有效期）');
    }
  };

  const handleOffline = async (row: MovieAdminView) => {
    try {
      await offlineMovie(row.id);
      message.success('电影已下架');
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '下架失败');
    }
  };

  const handleDelete = async (row: MovieAdminView) => {
    try {
      await deleteMovie(row.id);
      message.success('电影已删除');
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '删除失败');
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
      await takedownMovie(takedownTarget.id, takedownReason.trim());
      message.success('已完成版权下架');
      setTakedownTarget(null);
      setTakedownReason('');
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '版权下架失败');
    }
  };

  const categoryName = (value?: number) =>
    categories.find((item) => item.id === value)?.name ?? (value ? `#${value}` : '-');

  const columns: TableProps<MovieAdminView>['columns'] = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    {
      title: '海报',
      dataIndex: 'posterUrl',
      width: 90,
      render: (value?: string) =>
        value ? <Image src={value} alt="海报" width={56} height={78} style={{ objectFit: 'cover' }} /> : '-',
    },
    {
      title: '电影',
      dataIndex: 'title',
      render: (value: string, row) => (
        <Space direction="vertical" size={0}>
          <Typography.Text strong>{value}</Typography.Text>
          {row.originalTitle ? <Typography.Text type="secondary">{row.originalTitle}</Typography.Text> : null}
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {row.director ? `导演：${row.director}` : '导演未填写'} · {formatDuration(row.durationMs)}
            {row.rating !== undefined && row.rating !== null ? ` · 评分 ${row.rating}` : ''}
          </Typography.Text>
        </Space>
      ),
    },
    {
      title: '分类',
      dataIndex: 'categoryId',
      width: 110,
      render: (value?: number) => categoryName(value),
    },
    {
      title: '状态',
      dataIndex: 'publishState',
      width: 100,
      render: (value?: PublishState) => renderPublishState(value),
    },
    {
      title: '授权',
      key: 'license',
      width: 200,
      render: (_, row) => (
        <Space direction="vertical" size={0}>
          {renderLicenseSource(row.licenseSource)}
          {row.licenseNo ? (
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              编号 {row.licenseNo}
            </Typography.Text>
          ) : null}
          <Typography.Text
            type={isLicenseExpired(row.licenseExpireAt) ? 'danger' : 'secondary'}
            style={{ fontSize: 12 }}
          >
            至 {toDisplayDateTime(row.licenseExpireAt)}
          </Typography.Text>
        </Space>
      ),
    },
    {
      title: '播放',
      dataIndex: 'playCount',
      width: 90,
      render: (value?: number) => value ?? 0,
    },
    {
      title: '操作',
      key: 'action',
      width: 260,
      render: (_, row) => (
        <Space size={4} wrap>
          {has('sys:video:content:edit') ? (
            <Button type="link" size="small" onClick={() => openEdit(row)}>
              编辑
            </Button>
          ) : null}
          {row.publishState !== 'PUBLISHED' && row.copyrightState !== 'TAKEDOWN' && has('sys:video:content:publish') ? (
            <Popconfirm title="发布前将校验授权仍在有效期，确认发布？" onConfirm={() => void handlePublish(row)}>
              <Button type="link" size="small">
                发布
              </Button>
            </Popconfirm>
          ) : null}
          {row.publishState === 'PUBLISHED' && has('sys:video:content:offline') ? (
            <Popconfirm title="确认下架该电影？" onConfirm={() => void handleOffline(row)}>
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
          {has('sys:video:content:delete') ? (
            <Popconfirm title="确认删除该电影？" onConfirm={() => void handleDelete(row)}>
              <Button type="link" size="small" danger>
                删除
              </Button>
            </Popconfirm>
          ) : null}
        </Space>
      ),
    },
  ];

  const movieCategories = categories.filter(
    (category) => category.contentTypeScope === 'ALL' || category.contentTypeScope === 'MOVIE',
  );

  return (
    <>
      <Alert
        type="info"
        showIcon
        message="电影仅运营入库与发布"
        description="授权来源、授权到期时间与视频文件必填；授权到期后定时任务自动下线，发布前再次校验授权有效期。"
        style={{ marginBottom: 16 }}
      />

      <ProTable<MovieAdminView, MovieQuery>
        ref={tableRef}
        rowKey="id"
        title="电影管理"
        columns={columns}
        request={pageMovies}
        params={{ keyword, categoryId, publishState, sortBy }}
        onError={(error) => message.error(error instanceof Error ? error.message : '电影列表加载失败')}
        search={
          <SearchForm
            items={[
              { name: 'keyword', label: '关键词', element: <Input allowClear placeholder="标题 / 原始片名" /> },
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
                    options={movieCategories.map((category) => ({ value: category.id, label: category.name }))}
                  />
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
                name: 'sortBy',
                label: '排序',
                element: (
                  <Select allowClear placeholder="默认最新" style={{ width: 140 }} options={SORT_OPTIONS} />
                ),
              },
            ]}
            onSearch={(values) => {
              setKeyword((values.keyword as string) || undefined);
              setCategoryId((values.categoryId as number) || undefined);
              setPublishState((values.publishState as PublishState) || undefined);
              setSortBy((values.sortBy as 'LATEST' | 'HOT') || undefined);
            }}
            onReset={() => {
              setKeyword(undefined);
              setCategoryId(undefined);
              setPublishState(undefined);
              setSortBy(undefined);
            }}
          />
        }
        toolbar={
          <AuthButton perm="sys:video:content:add" type="primary" onClick={openCreate}>
            电影入库
          </AuthButton>
        }
      />

      <FormModal<MovieFormValues>
        open={modalOpen}
        title={editing ? `编辑电影：${editing.title}` : '电影入库'}
        form={form}
        width={720}
        okText={editing ? '保存' : '入库'}
        initialValues={{
          title: editing?.title,
          subtitle: editing?.subtitle,
          description: editing?.description,
          videoFileId: editing?.videoFileId,
          durationMs: editing?.durationMs,
          width: editing?.width,
          height: editing?.height,
          categoryId: editing?.categoryId && editing.categoryId > 0 ? editing.categoryId : undefined,
          creatorId: editing?.creatorId && editing.creatorId > 0 ? editing.creatorId : undefined,
          originalTitle: editing?.originalTitle,
          director: editing?.director,
          actors: editing?.actors,
          region: editing?.region,
          language: editing?.language,
          rating: editing?.rating,
          licenseSource: editing?.licenseSource ?? 'LICENSED',
          licenseNo: editing?.licenseNo,
        }}
        onOk={handleSave}
        onCancel={() => setModalOpen(false)}
      >
        <Typography.Text type="secondary">基础信息</Typography.Text>
        <Form.Item name="title" label="电影标题" rules={[{ required: true, message: '请输入电影标题' }]}>
          <Input maxLength={200} showCount />
        </Form.Item>
        <Form.Item name="subtitle" label="副标题">
          <Input maxLength={200} showCount />
        </Form.Item>
        <Form.Item name="description" label="剧情简介">
          <Input.TextArea maxLength={2000} showCount autoSize={{ minRows: 3, maxRows: 6 }} />
        </Form.Item>
        <Space size={16} align="start" style={{ display: 'flex' }}>
          <Form.Item
            name="categoryId"
            label="分类"
            style={{ flex: 1 }}
            extra="仅可选适用于电影的启用分类"
          >
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              placeholder="选择分类"
              options={movieCategories.map((category) => ({ value: category.id, label: category.name }))}
            />
          </Form.Item>
          <Form.Item name="creatorId" label="创作者 ID" style={{ flex: 1 }} extra="可选，运营入库通常留空">
            <InputNumber min={1} precision={0} style={{ width: '100%' }} placeholder="留空表示未登记" />
          </Form.Item>
        </Space>

        <Typography.Text type="secondary">影片素材</Typography.Text>
        <Form.Item
          name="videoFileId"
          label="视频文件 ID"
          rules={editing ? [] : [{ required: true, message: '请填写视频文件 ID' }]}
          extra={editing ? '留空表示保留原视频文件；填写则替换并重新校验格式与大小' : '可上传视频或直接填写 whale-file 文件 ID'}
        >
          <InputNumber min={1} precision={0} style={{ width: '100%' }} placeholder="whale-file 文件 ID" />
        </Form.Item>
        <Space size={16} align="start" style={{ display: 'flex' }}>
          <Form.Item
            name="durationMs"
            label="时长（毫秒）"
            style={{ flex: 1 }}
            rules={editing ? [] : [{ required: true, message: '请填写时长' }]}
          >
            <InputNumber min={1} precision={0} style={{ width: '100%' }} placeholder="如 7200000" />
          </Form.Item>
          <Form.Item name="width" label="宽度（像素）" style={{ flex: 1 }}>
            <InputNumber min={1} precision={0} style={{ width: '100%' }} placeholder="可选" />
          </Form.Item>
          <Form.Item name="height" label="高度（像素）" style={{ flex: 1 }}>
            <InputNumber min={1} precision={0} style={{ width: '100%' }} placeholder="可选" />
          </Form.Item>
        </Space>
        <Form.Item label="上传视频文件">
          <Upload
            showUploadList={false}
            accept="video/mp4, video/quicktime, video/x-matroska"
            beforeUpload={(file) => {
              handleUpload(
                file,
                (fileId) => form.setFieldValue('videoFileId', fileId),
                setUploadingVideo,
                '视频上传',
              );
              return false;
            }}
          >
            <Button loading={uploadingVideo}>选择视频并上传</Button>
          </Upload>
        </Form.Item>

        <Typography.Text type="secondary">影片元数据</Typography.Text>
        <Form.Item name="originalTitle" label="原始片名">
          <Input maxLength={200} showCount />
        </Form.Item>
        <Space size={16} align="start" style={{ display: 'flex' }}>
          <Form.Item name="director" label="导演" style={{ flex: 1 }} extra="多人用英文逗号分隔">
            <Input maxLength={200} />
          </Form.Item>
          <Form.Item name="actors" label="主演" style={{ flex: 2 }} extra="多人用英文逗号分隔">
            <Input maxLength={500} />
          </Form.Item>
        </Space>
        <Space size={16} align="start" style={{ display: 'flex' }}>
          <Form.Item name="region" label="地区" style={{ flex: 1 }}>
            <Input maxLength={64} placeholder="如 中国大陆" />
          </Form.Item>
          <Form.Item name="language" label="语言" style={{ flex: 1 }}>
            <Input maxLength={64} placeholder="如 普通话" />
          </Form.Item>
          <Form.Item
            name="rating"
            label="评分"
            style={{ flex: 1 }}
            extra="0.0 – 10.0，可留空"
          >
            <InputNumber min={0} max={10} step={0.1} precision={1} style={{ width: '100%' }} />
          </Form.Item>
        </Space>
        <Form.Item
          label="上映日期"
          extra={editing?.releaseDate ? `当前：${editing.releaseDate}（重新选择后生效）` : '可选'}
        >
          <DatePicker
            key={editing ? `release-${editing.id}` : 'release-new'}
            format={DATE_FORMAT}
            style={{ width: '100%' }}
            placeholder="选择上映日期"
            onChange={(value) => setReleaseDateValue(value?.format(DATE_FORMAT))}
          />
        </Form.Item>

        <Typography.Text type="secondary">授权信息</Typography.Text>
        <Space size={16} align="start" style={{ display: 'flex' }}>
          <Form.Item
            name="licenseSource"
            label="授权来源"
            style={{ flex: 1 }}
            rules={[{ required: true, message: '请选择授权来源' }]}
          >
            <Select placeholder="选择来源" options={LICENSE_SOURCE_OPTIONS} />
          </Form.Item>
          <Form.Item name="licenseNo" label="授权编号" style={{ flex: 2 }} extra="授权合同 / 登记编号，可选">
            <Input maxLength={128} />
          </Form.Item>
        </Space>
        <Form.Item
          label="授权到期时间"
          required
          extra={
            editing?.licenseExpireAt
              ? `当前：${toDisplayDateTime(editing.licenseExpireAt)}；保存需重新选择（到期后自动下线）`
              : '到期后定时任务自动下线并阻断播放'
          }
        >
          <DatePicker
            key={editing ? `license-${editing.id}` : 'license-new'}
            showTime
            format={DATE_TIME_FORMAT}
            style={{ width: '100%' }}
            placeholder="选择授权到期时间"
            onChange={(value) => setLicenseExpireValue(value?.format(DATE_TIME_FORMAT))}
          />
        </Form.Item>

        <Typography.Text type="secondary">图片素材</Typography.Text>
        <Form.Item label="海报">
          <Space direction="vertical" size={6}>
            {posterFileId === 0 ? (
              <Typography.Text type="secondary">已清除海报（保存后生效）</Typography.Text>
            ) : posterFileId ? (
              <Typography.Text type="secondary">已上传海报：{posterName || `文件 ID ${posterFileId}`}</Typography.Text>
            ) : editing?.posterUrl ? (
              <Image src={editing.posterUrl} width={96} height={134} style={{ objectFit: 'cover' }} alt="当前海报" />
            ) : (
              <Typography.Text type="secondary">暂无海报</Typography.Text>
            )}
            <Space>
              <Upload
                showUploadList={false}
                accept="image/*"
                beforeUpload={(file) => {
                  handleUpload(
                    file,
                    (fileId, name) => {
                      setPosterFileId(fileId);
                      setPosterName(name);
                    },
                    setUploadingPoster,
                    '海报上传',
                  );
                  return false;
                }}
              >
                <Button loading={uploadingPoster}>
                  {posterFileId !== 0 && (posterFileId || editing?.posterUrl) ? '重新上传海报' : '上传海报'}
                </Button>
              </Upload>
              {editing?.posterUrl || posterFileId ? (
                <Button
                  danger
                  onClick={() => {
                    setPosterFileId(0);
                    setPosterName('');
                  }}
                >
                  移除海报
                </Button>
              ) : null}
            </Space>
          </Space>
        </Form.Item>
        <Form.Item label="背景图">
          <Space direction="vertical" size={6}>
            {backdropFileId === 0 ? (
              <Typography.Text type="secondary">已清除背景图（保存后生效）</Typography.Text>
            ) : backdropFileId ? (
              <Typography.Text type="secondary">
                已上传背景图：{backdropName || `文件 ID ${backdropFileId}`}
              </Typography.Text>
            ) : editing?.backdropUrl ? (
              <Image src={editing.backdropUrl} width={240} height={135} style={{ objectFit: 'cover' }} alt="当前背景图" />
            ) : (
              <Typography.Text type="secondary">暂无背景图</Typography.Text>
            )}
            <Space>
              <Upload
                showUploadList={false}
                accept="image/*"
                beforeUpload={(file) => {
                  handleUpload(
                    file,
                    (fileId, name) => {
                      setBackdropFileId(fileId);
                      setBackdropName(name);
                    },
                    setUploadingBackdrop,
                    '背景图上传',
                  );
                  return false;
                }}
              >
                <Button loading={uploadingBackdrop}>
                  {backdropFileId !== 0 && (backdropFileId || editing?.backdropUrl) ? '重新上传背景图' : '上传背景图'}
                </Button>
              </Upload>
              {editing?.backdropUrl || backdropFileId ? (
                <Button
                  danger
                  onClick={() => {
                    setBackdropFileId(0);
                    setBackdropName('');
                  }}
                >
                  移除背景图
                </Button>
              ) : null}
            </Space>
          </Space>
        </Form.Item>
      </FormModal>

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
