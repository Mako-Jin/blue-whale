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
import { useI18n } from '../../i18n';

type Translate = ReturnType<typeof useI18n>['t'];

/** 授权来源选项 */
const buildLicenseSourceOptions = (t: Translate): { value: LicenseSource; label: string }[] => [
  { value: 'OWNED', label: t('video.movie.license.owned') },
  { value: 'LICENSED', label: t('video.movie.license.licensed') },
  { value: 'PUBLIC_DOMAIN', label: t('video.movie.license.publicDomain') },
];

/** 发布状态选项（电影无待审核） */
const buildPublishStateOptions = (t: Translate) => [
  { value: 'DRAFT', label: t('video.movie.state.draft') },
  { value: 'PUBLISHED', label: t('video.movie.state.published') },
  { value: 'OFFLINE', label: t('video.movie.state.offline') },
];

/** 排序选项 */
const buildSortOptions = (t: Translate) => [
  { value: 'LATEST', label: t('video.movie.sort.latest') },
  { value: 'HOT', label: t('video.movie.sort.hot') },
];

/** 上映日期格式（LocalDate） */
const DATE_FORMAT = 'YYYY-MM-DD';

/** 授权到期格式（LocalDateTime，展示与回填均用空格分隔） */
const DATE_TIME_FORMAT = 'YYYY-MM-DD HH:mm:ss';

interface MovieFormValues {
  title: string;
  subtitle?: string;
  description?: string;
  videoFileId?: string;
  durationMs?: number;
  width?: number;
  height?: number;
  categoryId?: string;
  creatorId?: string;
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
function renderPublishState(state: PublishState | undefined, t: Translate) {
  const map: Record<PublishState, { color: string; label: string }> = {
    DRAFT: { color: 'default', label: t('video.movie.state.draft') },
    PENDING_REVIEW: { color: 'gold', label: t('video.movie.state.pendingReview') },
    PUBLISHED: { color: 'success', label: t('video.movie.state.published') },
    OFFLINE: { color: 'error', label: t('video.movie.state.offline') },
  };
  return state ? <Tag color={map[state].color}>{map[state].label}</Tag> : '-';
}

/** 授权来源标签 */
function renderLicenseSource(
  source: LicenseSource | undefined,
  options: { value: LicenseSource; label: string }[],
) {
  if (!source) {
    return '-';
  }
  const item = options.find((option) => option.value === source);
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
  const { t } = useI18n();
  const tableRef = useRef<ProTableRef>(null);
  const [form] = Form.useForm<MovieFormValues>();

  const licenseSourceOptions = buildLicenseSourceOptions(t);
  const publishStateOptions = buildPublishStateOptions(t);
  const sortOptions = buildSortOptions(t);

  const [categories, setCategories] = useState<CategoryView[]>([]);
  const [keyword, setKeyword] = useState<string | undefined>(undefined);
  const [categoryId, setCategoryId] = useState<string | undefined>(undefined);
  const [publishState, setPublishState] = useState<PublishState | undefined>(undefined);
  const [sortBy, setSortBy] = useState<'LATEST' | 'HOT' | undefined>(undefined);

  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<MovieAdminView | null>(null);
  const [releaseDateValue, setReleaseDateValue] = useState<string | undefined>(undefined);
  const [licenseExpireValue, setLicenseExpireValue] = useState<string | undefined>(undefined);
  const [posterFileId, setPosterFileId] = useState<string | undefined>(undefined);
  const [posterName, setPosterName] = useState('');
  const [backdropFileId, setBackdropFileId] = useState<string | undefined>(undefined);
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
    done: (fileId: string, name: string) => void,
    setUploading: (value: boolean) => void,
    label: string,
  ) => {
    setUploading(true);
    uploadFile(file)
      .then((uploaded) => {
        done(uploaded.id, uploaded.originalName ?? file.name);
        message.success(t('video.movie.upload.success', { label }));
      })
      .catch((error) =>
        message.error(
          error instanceof Error
            ? error.message
            : t('video.movie.upload.failed', { label }),
        ),
      )
      .finally(() => setUploading(false));
  };

  const handleSave = async (values: MovieFormValues) => {
    const expireAt = toIsoDateTime(licenseExpireValue);
    if (!expireAt) {
      message.warning(t('video.movie.msg.licenseExpireRequired'));
      throw new Error('missing license expire at');
    }
    // videoFileId 运行时为字符串，'0' 视为未填写
    if (!editing && (!values.videoFileId || values.videoFileId === '0')) {
      message.warning(t('video.movie.msg.videoFileRequired'));
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
      // 未选分类 / 未填创作者沿用后端约定的 '0' 占位
      categoryId: values.categoryId ?? '0',
      creatorId: values.creatorId ?? '0',
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
        message.success(t('video.movie.msg.updated'));
      } else {
        await createMovie(payload);
        message.success(t('video.movie.msg.created'));
      }
      setModalOpen(false);
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('video.movie.msg.saveFailed'));
      throw error;
    }
  };

  const handlePublish = async (row: MovieAdminView) => {
    try {
      await publishMovie(row.id);
      message.success(t('video.movie.msg.published'));
      tableRef.current?.reload();
    } catch (error) {
      message.error(
        error instanceof Error ? error.message : t('video.movie.msg.publishFailed'),
      );
    }
  };

  const handleOffline = async (row: MovieAdminView) => {
    try {
      await offlineMovie(row.id);
      message.success(t('video.movie.msg.offlined'));
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('video.movie.msg.offlineFailed'));
    }
  };

  const handleDelete = async (row: MovieAdminView) => {
    try {
      await deleteMovie(row.id);
      message.success(t('video.movie.msg.deleted'));
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('video.movie.msg.deleteFailed'));
    }
  };

  const confirmTakedown = async () => {
    if (!takedownTarget) {
      return;
    }
    if (!takedownReason.trim()) {
      message.warning(t('video.movie.msg.takedownReasonRequired'));
      return;
    }
    try {
      await takedownMovie(takedownTarget.id, takedownReason.trim());
      message.success(t('video.movie.msg.takedownDone'));
      setTakedownTarget(null);
      setTakedownReason('');
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('video.movie.msg.takedownFailed'));
    }
  };

  // categoryId 运行时为字符串，'0' 表示未选分类
  const categoryName = (value?: string) =>
    categories.find((item) => item.id === value)?.name ??
    (value && value !== '0' ? `#${value}` : '-');

  const columns: TableProps<MovieAdminView>['columns'] = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    {
      title: t('video.movie.col.poster'),
      dataIndex: 'posterUrl',
      width: 90,
      render: (value?: string) =>
        value ? (
          <Image
            src={value}
            alt={t('video.movie.alt.poster')}
            width={56}
            height={78}
            style={{ objectFit: 'cover' }}
          />
        ) : (
          '-'
        ),
    },
    {
      title: t('video.movie.col.movie'),
      dataIndex: 'title',
      render: (value: string, row) => (
        <Space direction="vertical" size={0}>
          <Typography.Text strong>{value}</Typography.Text>
          {row.originalTitle ? (
            <Typography.Text type="secondary">{row.originalTitle}</Typography.Text>
          ) : null}
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {row.director
              ? t('video.movie.cell.director', { name: row.director })
              : t('video.movie.cell.directorMissing')}{' '}
            · {formatDuration(row.durationMs)}
            {row.rating !== undefined && row.rating !== null
              ? ` · ${t('video.movie.cell.rating', { rating: row.rating })}`
              : ''}
          </Typography.Text>
        </Space>
      ),
    },
    {
      title: t('video.movie.col.category'),
      dataIndex: 'categoryId',
      width: 110,
      render: (value?: string) => categoryName(value),
    },
    {
      title: t('video.movie.col.status'),
      dataIndex: 'publishState',
      width: 100,
      render: (value?: PublishState) => renderPublishState(value, t),
    },
    {
      title: t('video.movie.col.license'),
      key: 'license',
      width: 200,
      render: (_, row) => (
        <Space direction="vertical" size={0}>
          {renderLicenseSource(row.licenseSource, licenseSourceOptions)}
          {row.licenseNo ? (
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              {t('video.movie.cell.licenseNo', { no: row.licenseNo })}
            </Typography.Text>
          ) : null}
          <Typography.Text
            type={isLicenseExpired(row.licenseExpireAt) ? 'danger' : 'secondary'}
            style={{ fontSize: 12 }}
          >
            {t('video.movie.cell.licenseUntil', { time: toDisplayDateTime(row.licenseExpireAt) })}
          </Typography.Text>
        </Space>
      ),
    },
    {
      title: t('video.movie.col.playCount'),
      dataIndex: 'playCount',
      width: 90,
      render: (value?: number) => value ?? 0,
    },
    {
      title: t('video.movie.col.action'),
      key: 'action',
      width: 260,
      render: (_, row) => (
        <Space size={4} wrap>
          {has('sys:video:content:edit') ? (
            <Button type="link" size="small" onClick={() => openEdit(row)}>
              {t('video.movie.action.edit')}
            </Button>
          ) : null}
          {row.publishState !== 'PUBLISHED' &&
          row.copyrightState !== 'TAKEDOWN' &&
          has('sys:video:content:publish') ? (
            <Popconfirm
              title={t('video.movie.confirm.publish')}
              onConfirm={() => void handlePublish(row)}
            >
              <Button type="link" size="small">
                {t('video.movie.action.publish')}
              </Button>
            </Popconfirm>
          ) : null}
          {row.publishState === 'PUBLISHED' && has('sys:video:content:offline') ? (
            <Popconfirm
              title={t('video.movie.confirm.offline')}
              onConfirm={() => void handleOffline(row)}
            >
              <Button type="link" size="small" danger>
                {t('video.movie.action.offline')}
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
              {t('video.movie.action.takedown')}
            </Button>
          ) : null}
          {has('sys:video:content:delete') ? (
            <Popconfirm
              title={t('video.movie.confirm.delete')}
              onConfirm={() => void handleDelete(row)}
            >
              <Button type="link" size="small" danger>
                {t('video.movie.action.delete')}
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
        message={t('video.movie.alert.title')}
        description={t('video.movie.alert.description')}
        style={{ marginBottom: 16 }}
      />

      <ProTable<MovieAdminView, MovieQuery>
        ref={tableRef}
        rowKey="id"
        title={t('video.movie.list.title')}
        columns={columns}
        request={pageMovies}
        params={{ keyword, categoryId, publishState, sortBy }}
        onError={(error) =>
          message.error(error instanceof Error ? error.message : t('video.movie.msg.loadFailed'))
        }
        search={
          <SearchForm
            items={[
              {
                name: 'keyword',
                label: t('video.movie.search.keyword'),
                element: (
                  <Input allowClear placeholder={t('video.movie.search.keywordPlaceholder')} />
                ),
              },
              {
                name: 'categoryId',
                label: t('video.movie.search.category'),
                element: (
                  <Select
                    allowClear
                    showSearch
                    optionFilterProp="label"
                    placeholder={t('video.movie.search.all')}
                    style={{ width: 160 }}
                    options={movieCategories.map((category) => ({
                      value: category.id,
                      label: category.name,
                    }))}
                  />
                ),
              },
              {
                name: 'publishState',
                label: t('video.movie.search.status'),
                element: (
                  <Select
                    allowClear
                    placeholder={t('video.movie.search.all')}
                    style={{ width: 120 }}
                    options={publishStateOptions}
                  />
                ),
              },
              {
                name: 'sortBy',
                label: t('video.movie.search.sort'),
                element: (
                  <Select
                    allowClear
                    placeholder={t('video.movie.search.sortPlaceholder')}
                    style={{ width: 140 }}
                    options={sortOptions}
                  />
                ),
              },
            ]}
            onSearch={(values) => {
              setKeyword((values.keyword as string) || undefined);
              setCategoryId((values.categoryId as string) || undefined);
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
            {t('video.movie.action.create')}
          </AuthButton>
        }
      />

      <FormModal<MovieFormValues>
        open={modalOpen}
        title={
          editing
            ? t('video.movie.form.editTitle', { title: editing.title })
            : t('video.movie.form.createTitle')
        }
        form={form}
        width={720}
        okText={editing ? t('video.movie.form.save') : t('video.movie.form.submit')}
        initialValues={{
          title: editing?.title,
          subtitle: editing?.subtitle,
          description: editing?.description,
          videoFileId: editing?.videoFileId,
          durationMs: editing?.durationMs,
          width: editing?.width,
          height: editing?.height,
          categoryId:
            editing?.categoryId && editing.categoryId !== '0' ? editing.categoryId : undefined,
          creatorId:
            editing?.creatorId && editing.creatorId !== '0' ? editing.creatorId : undefined,
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
        <Typography.Text type="secondary">{t('video.movie.form.sectionBasic')}</Typography.Text>
        <Form.Item
          name="title"
          label={t('video.movie.form.title')}
          rules={[{ required: true, message: t('video.movie.form.titleRequired') }]}
        >
          <Input maxLength={200} showCount />
        </Form.Item>
        <Form.Item name="subtitle" label={t('video.movie.form.subtitle')}>
          <Input maxLength={200} showCount />
        </Form.Item>
        <Form.Item name="description" label={t('video.movie.form.description')}>
          <Input.TextArea maxLength={2000} showCount autoSize={{ minRows: 3, maxRows: 6 }} />
        </Form.Item>
        <Space size={16} align="start" style={{ display: 'flex' }}>
          <Form.Item
            name="categoryId"
            label={t('video.movie.form.category')}
            style={{ flex: 1 }}
            extra={t('video.movie.form.categoryExtra')}
          >
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              placeholder={t('video.movie.form.categoryPlaceholder')}
              options={movieCategories.map((category) => ({
                value: category.id,
                label: category.name,
              }))}
            />
          </Form.Item>
          <Form.Item
            name="creatorId"
            label={t('video.movie.form.creator')}
            style={{ flex: 1 }}
            extra={t('video.movie.form.creatorExtra')}
          >
            <Input placeholder={t('video.movie.form.creatorPlaceholder')} />
          </Form.Item>
        </Space>

        <Typography.Text type="secondary">{t('video.movie.form.sectionMedia')}</Typography.Text>
        <Form.Item
          name="videoFileId"
          label={t('video.movie.form.videoFile')}
          rules={
            editing ? [] : [{ required: true, message: t('video.movie.form.videoFileRequired') }]
          }
          extra={
            editing
              ? t('video.movie.form.videoFileExtraEdit')
              : t('video.movie.form.videoFileExtra')
          }
        >
          <Input placeholder={t('video.movie.form.videoFilePlaceholder')} />
        </Form.Item>
        <Space size={16} align="start" style={{ display: 'flex' }}>
          <Form.Item
            name="durationMs"
            label={t('video.movie.form.duration')}
            style={{ flex: 1 }}
            rules={
              editing
                ? []
                : [{ required: true, message: t('video.movie.form.durationRequired') }]
            }
          >
            <InputNumber
              min={1}
              precision={0}
              style={{ width: '100%' }}
              placeholder={t('video.movie.form.durationPlaceholder')}
            />
          </Form.Item>
          <Form.Item name="width" label={t('video.movie.form.width')} style={{ flex: 1 }}>
            <InputNumber
              min={1}
              precision={0}
              style={{ width: '100%' }}
              placeholder={t('video.movie.form.optional')}
            />
          </Form.Item>
          <Form.Item name="height" label={t('video.movie.form.height')} style={{ flex: 1 }}>
            <InputNumber
              min={1}
              precision={0}
              style={{ width: '100%' }}
              placeholder={t('video.movie.form.optional')}
            />
          </Form.Item>
        </Space>
        <Form.Item label={t('video.movie.form.uploadVideo')}>
          <Upload
            showUploadList={false}
            accept="video/mp4, video/quicktime, video/x-matroska"
            beforeUpload={(file) => {
              handleUpload(
                file,
                (fileId) => form.setFieldValue('videoFileId', fileId),
                setUploadingVideo,
                t('video.movie.upload.video'),
              );
              return false;
            }}
          >
            <Button loading={uploadingVideo}>{t('video.movie.form.chooseVideo')}</Button>
          </Upload>
        </Form.Item>

        <Typography.Text type="secondary">{t('video.movie.form.sectionMetadata')}</Typography.Text>
        <Form.Item name="originalTitle" label={t('video.movie.form.originalTitle')}>
          <Input maxLength={200} showCount />
        </Form.Item>
        <Space size={16} align="start" style={{ display: 'flex' }}>
          <Form.Item
            name="director"
            label={t('video.movie.form.director')}
            style={{ flex: 1 }}
            extra={t('video.movie.form.multiValueHint')}
          >
            <Input maxLength={200} />
          </Form.Item>
          <Form.Item
            name="actors"
            label={t('video.movie.form.actors')}
            style={{ flex: 2 }}
            extra={t('video.movie.form.multiValueHint')}
          >
            <Input maxLength={500} />
          </Form.Item>
        </Space>
        <Space size={16} align="start" style={{ display: 'flex' }}>
          <Form.Item name="region" label={t('video.movie.form.region')} style={{ flex: 1 }}>
            <Input maxLength={64} placeholder={t('video.movie.form.regionPlaceholder')} />
          </Form.Item>
          <Form.Item name="language" label={t('video.movie.form.language')} style={{ flex: 1 }}>
            <Input maxLength={64} placeholder={t('video.movie.form.languagePlaceholder')} />
          </Form.Item>
          <Form.Item
            name="rating"
            label={t('video.movie.form.rating')}
            style={{ flex: 1 }}
            extra={t('video.movie.form.ratingExtra')}
          >
            <InputNumber min={0} max={10} step={0.1} precision={1} style={{ width: '100%' }} />
          </Form.Item>
        </Space>
        <Form.Item
          label={t('video.movie.form.releaseDate')}
          extra={
            editing?.releaseDate
              ? t('video.movie.form.releaseDateExtraCurrent', { date: editing.releaseDate })
              : t('video.movie.form.releaseDateExtra')
          }
        >
          <DatePicker
            key={editing ? `release-${editing.id}` : 'release-new'}
            format={DATE_FORMAT}
            style={{ width: '100%' }}
            placeholder={t('video.movie.form.releaseDatePlaceholder')}
            onChange={(value) => setReleaseDateValue(value?.format(DATE_FORMAT))}
          />
        </Form.Item>

        <Typography.Text type="secondary">{t('video.movie.form.sectionLicense')}</Typography.Text>
        <Space size={16} align="start" style={{ display: 'flex' }}>
          <Form.Item
            name="licenseSource"
            label={t('video.movie.form.licenseSource')}
            style={{ flex: 1 }}
            rules={[{ required: true, message: t('video.movie.form.licenseSourceRequired') }]}
          >
            <Select
              placeholder={t('video.movie.form.licenseSourcePlaceholder')}
              options={licenseSourceOptions}
            />
          </Form.Item>
          <Form.Item
            name="licenseNo"
            label={t('video.movie.form.licenseNo')}
            style={{ flex: 2 }}
            extra={t('video.movie.form.licenseNoExtra')}
          >
            <Input maxLength={128} />
          </Form.Item>
        </Space>
        <Form.Item
          label={t('video.movie.form.licenseExpire')}
          required
          extra={
            editing?.licenseExpireAt
              ? t('video.movie.form.licenseExpireExtraCurrent', {
                  time: toDisplayDateTime(editing.licenseExpireAt),
                })
              : t('video.movie.form.licenseExpireExtra')
          }
        >
          <DatePicker
            key={editing ? `license-${editing.id}` : 'license-new'}
            showTime
            format={DATE_TIME_FORMAT}
            style={{ width: '100%' }}
            placeholder={t('video.movie.form.licenseExpirePlaceholder')}
            onChange={(value) => setLicenseExpireValue(value?.format(DATE_TIME_FORMAT))}
          />
        </Form.Item>

        <Typography.Text type="secondary">{t('video.movie.form.sectionImages')}</Typography.Text>
        <Form.Item label={t('video.movie.form.poster')}>
          <Space direction="vertical" size={6}>
            {posterFileId === '0' ? (
              <Typography.Text type="secondary">
                {t('video.movie.form.posterCleared')}
              </Typography.Text>
            ) : posterFileId ? (
              <Typography.Text type="secondary">
                {t('video.movie.form.posterUploaded', {
                  name: posterName || t('video.movie.form.fileId', { id: posterFileId }),
                })}
              </Typography.Text>
            ) : editing?.posterUrl ? (
              <Image
                src={editing.posterUrl}
                width={96}
                height={134}
                style={{ objectFit: 'cover' }}
                alt={t('video.movie.alt.currentPoster')}
              />
            ) : (
              <Typography.Text type="secondary">
                {t('video.movie.form.posterEmpty')}
              </Typography.Text>
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
                    t('video.movie.upload.poster'),
                  );
                  return false;
                }}
              >
                <Button loading={uploadingPoster}>
                  {posterFileId !== '0' && (posterFileId || editing?.posterUrl)
                    ? t('video.movie.form.posterReupload')
                    : t('video.movie.form.posterUpload')}
                </Button>
              </Upload>
              {editing?.posterUrl || (posterFileId && posterFileId !== '0') ? (
                <Button
                  danger
                  onClick={() => {
                    setPosterFileId('0');
                    setPosterName('');
                  }}
                >
                  {t('video.movie.form.posterRemove')}
                </Button>
              ) : null}
            </Space>
          </Space>
        </Form.Item>
        <Form.Item label={t('video.movie.form.backdrop')}>
          <Space direction="vertical" size={6}>
            {backdropFileId === '0' ? (
              <Typography.Text type="secondary">
                {t('video.movie.form.backdropCleared')}
              </Typography.Text>
            ) : backdropFileId ? (
              <Typography.Text type="secondary">
                {t('video.movie.form.backdropUploaded', {
                  name: backdropName || t('video.movie.form.fileId', { id: backdropFileId }),
                })}
              </Typography.Text>
            ) : editing?.backdropUrl ? (
              <Image
                src={editing.backdropUrl}
                width={240}
                height={135}
                style={{ objectFit: 'cover' }}
                alt={t('video.movie.alt.currentBackdrop')}
              />
            ) : (
              <Typography.Text type="secondary">
                {t('video.movie.form.backdropEmpty')}
              </Typography.Text>
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
                    t('video.movie.upload.backdrop'),
                  );
                  return false;
                }}
              >
                <Button loading={uploadingBackdrop}>
                  {backdropFileId !== '0' && (backdropFileId || editing?.backdropUrl)
                    ? t('video.movie.form.backdropReupload')
                    : t('video.movie.form.backdropUpload')}
                </Button>
              </Upload>
              {editing?.backdropUrl || (backdropFileId && backdropFileId !== '0') ? (
                <Button
                  danger
                  onClick={() => {
                    setBackdropFileId('0');
                    setBackdropName('');
                  }}
                >
                  {t('video.movie.form.backdropRemove')}
                </Button>
              ) : null}
            </Space>
          </Space>
        </Form.Item>
      </FormModal>

      <Modal
        open={takedownTarget !== null}
        title={
          takedownTarget
            ? t('video.movie.takedown.titleWithName', { name: takedownTarget.title })
            : t('video.movie.takedown.title')
        }
        okText={t('video.movie.takedown.ok')}
        okButtonProps={{ danger: true }}
        onOk={() => void confirmTakedown()}
        onCancel={() => {
          setTakedownTarget(null);
          setTakedownReason('');
        }}
      >
        <Space direction="vertical" style={{ width: '100%' }}>
          <span>{t('video.movie.takedown.description')}</span>
          <Input.TextArea
            rows={3}
            maxLength={500}
            showCount
            value={takedownReason}
            onChange={(event) => setTakedownReason(event.target.value)}
            placeholder={t('video.movie.takedown.reasonPlaceholder')}
          />
        </Space>
      </Modal>
    </>
  );
}
