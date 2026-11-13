// 上传中心：签署《内容合法与版权无争议声明》→ 分片上传视频 / 封面 → 填写元数据 → 提交（可立即发布）

import {
  Alert,
  App,
  Button,
  Card,
  Checkbox,
  Form,
  Input,
  InputNumber,
  Modal,
  Progress,
  Radio,
  Result,
  Select,
  Space,
  Typography,
  Upload,
} from 'antd';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  acceptDeclaration,
  createShortVideo,
  createVideo,
  fetchCategories,
  fetchDeclaration,
  fetchMeta,
  fetchSeriesPage,
  fetchTags,
  type CategoryView,
  type ContentSource,
  type ContentType,
  type ContentVisibility,
  type SeriesView,
  type TagView,
  type UploadDeclaration,
  type VideoMeta,
} from '../api/video';
import { hasPermission } from '../api/client';
import { readVideoMetadata, uploadFile, uploadVideoFile } from '../api/file';

/** 内容来源选项 */
const SOURCE_OPTIONS: { value: ContentSource; label: string }[] = [
  { value: 'ORIGINAL', label: '本人原创' },
  { value: 'AUTHORIZED', label: '已取得授权' },
  { value: 'ROYALTY_FREE', label: '无版权争议素材' },
];

interface UploadFormValues {
  title: string;
  subtitle?: string;
  description?: string;
  categoryId?: number;
  seriesId?: number;
  episodeNo?: number;
  tagIds?: number[];
  visibility: ContentVisibility;
  publishNow: boolean;
}

/** 字节数格式化 */
function formatBytes(bytes?: number): string {
  if (!bytes) {
    return '-';
  }
  const gb = bytes / 1073741824;
  return gb >= 1 ? `${gb.toFixed(1)} GB` : `${Math.round(bytes / 1048576)} MB`;
}

export default function UploadPage() {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const [form] = Form.useForm<UploadFormValues>();

  const canUpload = hasPermission('sys:video:upload');
  const [meta, setMeta] = useState<VideoMeta | null>(null);
  const [declaration, setDeclaration] = useState<UploadDeclaration | null>(null);
  const [declarationOpen, setDeclarationOpen] = useState(false);
  const [agreed, setAgreed] = useState(false);
  const [accepting, setAccepting] = useState(false);
  const [contentSource, setContentSource] = useState<ContentSource>('ORIGINAL');

  const [contentType, setContentType] =
    useState<Extract<ContentType, 'SHORT_VIDEO' | 'VIDEO'>>('SHORT_VIDEO');
  const [categories, setCategories] = useState<CategoryView[]>([]);
  const [tags, setTags] = useState<TagView[]>([]);
  const [seriesList, setSeriesList] = useState<SeriesView[]>([]);

  const [videoFileId, setVideoFileId] = useState<number | undefined>(undefined);
  const [videoName, setVideoName] = useState('');
  const [durationMs, setDurationMs] = useState<number | undefined>(undefined);
  const [width, setWidth] = useState<number | undefined>(undefined);
  const [height, setHeight] = useState<number | undefined>(undefined);
  const [sizeBytes, setSizeBytes] = useState<number | undefined>(undefined);
  const [uploadingVideo, setUploadingVideo] = useState(false);
  const [uploadPercent, setUploadPercent] = useState(0);

  const [coverFileId, setCoverFileId] = useState<number | undefined>(undefined);
  const [coverName, setCoverName] = useState('');
  const [uploadingCover, setUploadingCover] = useState(false);

  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!canUpload) {
      return;
    }
    fetchMeta()
      .then(setMeta)
      .catch((error) => message.error(error instanceof Error ? error.message : '服务信息加载失败'));
    fetchDeclaration()
      .then((status) => {
        setDeclaration(status);
        setContentSource(status.contentSource ?? 'ORIGINAL');
        if (!status.accepted) {
          setDeclarationOpen(true);
        }
      })
      .catch((error) => message.error(error instanceof Error ? error.message : '声明状态加载失败'));
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

  const handleAccept = async () => {
    if (!declaration) {
      return;
    }
    if (!agreed) {
      message.warning('请先勾选确认声明');
      return;
    }
    setAccepting(true);
    try {
      const status = await acceptDeclaration(declaration.version, contentSource);
      setDeclaration(status);
      setDeclarationOpen(false);
      message.success('已签署声明');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '声明签署失败');
    } finally {
      setAccepting(false);
    }
  };

  const maxBytes = contentType === 'SHORT_VIDEO' ? meta?.shortMaxBytes : meta?.videoMaxBytes;

  const handleVideoSelect = async (file: File) => {
    if (maxBytes && file.size > maxBytes) {
      message.error(`文件超过当前类型上限 ${formatBytes(maxBytes)}`);
      return;
    }
    setUploadingVideo(true);
    setUploadPercent(0);
    try {
      const metadata = await readVideoMetadata(file);
      const uploaded = await uploadVideoFile(file, (uploadedBytes, totalBytes) => {
        setUploadPercent(
          totalBytes > 0 ? Math.min(Math.round((uploadedBytes / totalBytes) * 100), 99) : 0,
        );
      });
      setVideoFileId(uploaded.id);
      setVideoName(uploaded.originalName ?? file.name);
      setDurationMs(metadata.durationMs);
      setWidth(metadata.width);
      setHeight(metadata.height);
      setSizeBytes(uploaded.fileSize ?? file.size);
      setUploadPercent(100);
      message.success('视频已上传');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '视频上传失败');
      setUploadPercent(0);
    } finally {
      setUploadingVideo(false);
    }
  };

  const handleCoverSelect = async (file: File) => {
    setUploadingCover(true);
    try {
      const uploaded = await uploadFile(file);
      setCoverFileId(uploaded.id);
      setCoverName(uploaded.originalName ?? file.name);
      message.success('封面已上传');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '封面上传失败');
    } finally {
      setUploadingCover(false);
    }
  };

  const handleSubmit = async (values: UploadFormValues) => {
    if (!declaration?.accepted) {
      setDeclarationOpen(true);
      message.warning('请先签署上传声明');
      return;
    }
    if (!videoFileId) {
      message.warning('请先上传视频文件');
      return;
    }
    if (!durationMs) {
      message.warning('视频时长未读取，请重新上传视频');
      return;
    }
    setSubmitting(true);
    try {
      const input = {
        videoFileId,
        title: values.title,
        subtitle: values.subtitle || undefined,
        description: values.description || undefined,
        coverFileId,
        durationMs,
        width,
        height,
        sizeBytes,
        categoryId: values.categoryId,
        seriesId: contentType === 'VIDEO' ? values.seriesId : undefined,
        episodeNo: contentType === 'VIDEO' && values.seriesId ? values.episodeNo : undefined,
        tagIds: values.tagIds?.length ? values.tagIds : undefined,
        visibility: values.visibility,
        source: contentSource,
        publishNow: values.publishNow,
      };
      if (contentType === 'SHORT_VIDEO') {
        await createShortVideo(input);
      } else {
        await createVideo(input);
      }
      message.success(values.publishNow ? '作品已上传并发布' : '作品已保存为草稿');
      form.resetFields();
      setVideoFileId(undefined);
      setVideoName('');
      setDurationMs(undefined);
      setWidth(undefined);
      setHeight(undefined);
      setSizeBytes(undefined);
      setUploadPercent(0);
      setCoverFileId(undefined);
      setCoverName('');
      navigate('/mine');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '作品提交失败');
    } finally {
      setSubmitting(false);
    }
  };

  if (!canUpload) {
    return (
      <Result
        status="403"
        title="暂无上传权限"
        subTitle="上传作品需要 sys:video:upload 权限，请联系租户管理员开通"
        extra={
          <Button type="primary" onClick={() => navigate('/home')}>
            返回首页
          </Button>
        }
      />
    );
  }

  const categoryOptions = categories
    .filter(
      (category) =>
        category.contentTypeScope === 'ALL' || category.contentTypeScope === contentType,
    )
    .map((category) => ({ value: category.id, label: category.name }));

  return (
    <div style={{ maxWidth: 880, margin: '0 auto' }}>
      <Card
        title="上传作品"
        extra={
          declaration?.accepted ? (
            <Typography.Text type="secondary">已签署声明 {declaration.version}</Typography.Text>
          ) : (
            <Button size="small" danger onClick={() => setDeclarationOpen(true)}>
              待签署声明
            </Button>
          )
        }
      >
        <Alert
          style={{ marginBottom: 16 }}
          type="warning"
          showIcon
          message="上传前须签署《内容合法与版权无争议声明》"
          description="请确保你对上传内容拥有完整权利；公开作品由运营事后复核，存在权利争议将被下架。"
        />
        {meta ? (
          <Typography.Paragraph type="secondary">
            允许格式 {meta.contentTypes.join(' / ')}；短视频上限 {formatBytes(meta.shortMaxBytes)}
            、视频上限 {formatBytes(meta.videoMaxBytes)}；票据有效期 {meta.playTicketTtlSeconds}s。
          </Typography.Paragraph>
        ) : null}

        <Form
          form={form}
          layout="vertical"
          initialValues={{ visibility: 'PUBLIC', publishNow: true }}
          onFinish={(values) => void handleSubmit(values)}
        >
          <Form.Item label="内容类型" required>
            <Radio.Group
              value={contentType}
              onChange={(event) => setContentType(event.target.value as 'SHORT_VIDEO' | 'VIDEO')}
            >
              <Radio.Button value="SHORT_VIDEO">短视频</Radio.Button>
              <Radio.Button value="VIDEO">视频</Radio.Button>
            </Radio.Group>
          </Form.Item>

          <Form.Item label="视频文件（必填）">
            <Space direction="vertical" size={6} style={{ width: '100%' }}>
              <Space>
                <Upload
                  showUploadList={false}
                  accept="video/*"
                  beforeUpload={(file) => {
                    void handleVideoSelect(file);
                    return false;
                  }}
                >
                  <Button loading={uploadingVideo}>
                    {videoFileId ? '重新上传视频' : '选择视频文件'}
                  </Button>
                </Upload>
                {videoName ? (
                  <Typography.Text>
                    {videoName}
                    {durationMs ? `（${Math.round(durationMs / 1000)} 秒` : ''}
                    {width && height ? `，${width}×${height}` : ''}
                    {durationMs ? '）' : ''}
                  </Typography.Text>
                ) : null}
              </Space>
              {uploadingVideo || uploadPercent > 0 ? (
                <Progress percent={uploadPercent} size="small" />
              ) : null}
            </Space>
          </Form.Item>

          <Form.Item label="封面（可选）">
            <Space>
              <Upload
                showUploadList={false}
                accept="image/*"
                beforeUpload={(file) => {
                  void handleCoverSelect(file);
                  return false;
                }}
              >
                <Button loading={uploadingCover}>
                  {coverFileId ? '重新上传封面' : '选择封面图片'}
                </Button>
              </Upload>
              {coverName ? <Typography.Text>{coverName}</Typography.Text> : null}
            </Space>
          </Form.Item>

          <Form.Item name="title" label="标题" rules={[{ required: true, message: '请输入标题' }]}>
            <Input maxLength={200} showCount placeholder="作品标题" />
          </Form.Item>
          <Form.Item name="subtitle" label="副标题">
            <Input maxLength={200} placeholder="可选" />
          </Form.Item>
          <Form.Item name="description" label="简介">
            <Input.TextArea
              maxLength={2000}
              showCount
              autoSize={{ minRows: 3, maxRows: 6 }}
              placeholder="作品简介"
            />
          </Form.Item>
          <Form.Item name="categoryId" label="分类">
            <Select allowClear placeholder="选择分类" options={categoryOptions} />
          </Form.Item>
          {contentType === 'VIDEO' ? (
            <>
              <Form.Item
                name="seriesId"
                label="所属系列"
                extra={seriesList.length === 0 ? '暂无可选系列，系列由运营创建并上架' : undefined}
              >
                <Select
                  allowClear
                  placeholder="不加入系列"
                  options={seriesList.map((item) => ({
                    value: item.id,
                    label: `${item.title}（${item.episodeCount} 集）`,
                  }))}
                />
              </Form.Item>
              <Form.Item
                noStyle
                shouldUpdate={(previous, next) => previous.seriesId !== next.seriesId}
              >
                {({ getFieldValue }) =>
                  getFieldValue('seriesId') ? (
                    <Form.Item
                      name="episodeNo"
                      label="集数"
                      rules={[{ required: true, message: '请输入集数' }]}
                    >
                      <InputNumber
                        min={1}
                        precision={0}
                        style={{ width: 160 }}
                        placeholder="第几集"
                      />
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
            <Radio.Group>
              <Radio value="PUBLIC">公开（租户内可见）</Radio>
              <Radio value="PRIVATE">私密（仅本人可见）</Radio>
            </Radio.Group>
          </Form.Item>
          <Form.Item label="内容来源" required>
            <Select
              value={contentSource}
              onChange={(value) => setContentSource(value)}
              options={SOURCE_OPTIONS}
            />
          </Form.Item>
          <Form.Item name="publishNow" valuePropName="checked">
            <Checkbox>提交后立即发布（关闭则保存为草稿）</Checkbox>
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit" loading={submitting}>
                提交作品
              </Button>
              <Button onClick={() => navigate('/mine')}>返回我的作品</Button>
            </Space>
          </Form.Item>
        </Form>
      </Card>

      <Modal
        title={declaration?.title ?? '内容合法与版权无争议声明'}
        open={declarationOpen}
        width={680}
        confirmLoading={accepting}
        onOk={() => void handleAccept()}
        onCancel={() => setDeclarationOpen(false)}
        okText="确认签署"
        cancelText="稍后"
      >
        <Typography.Paragraph type="secondary">
          版本 {declaration?.version ?? '-'}
        </Typography.Paragraph>
        <div
          style={{
            maxHeight: 320,
            overflowY: 'auto',
            padding: 12,
            background: '#fafafa',
            whiteSpace: 'pre-wrap',
            lineHeight: 1.7,
          }}
        >
          {declaration?.text ?? '声明内容加载中…'}
        </div>
        <Select
          style={{ width: '100%', marginTop: 12 }}
          value={contentSource}
          onChange={(value) => setContentSource(value)}
          options={SOURCE_OPTIONS}
        />
        <Checkbox
          style={{ marginTop: 12 }}
          checked={agreed}
          onChange={(event) => setAgreed(event.target.checked)}
        >
          我已阅读并承诺内容合法、版权无争议，且对上传内容独立承担全部责任
        </Checkbox>
      </Modal>
    </div>
  );
}
