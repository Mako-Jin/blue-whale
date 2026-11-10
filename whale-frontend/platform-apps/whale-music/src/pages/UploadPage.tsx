// 上传作品：签署《版权无争议协议》→ 上传音频 / 封面 → 填写元数据 → 选择公开或自听 → 提交

import {
  Alert,
  App,
  Button,
  Card,
  Checkbox,
  Form,
  Input,
  Modal,
  Radio,
  Result,
  Select,
  Space,
  Typography,
  Upload,
} from 'antd';
import type { UploadFile } from 'antd';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { hasPermission } from '../api/client';
import { readAudioDuration, uploadFile } from '../api/file';
import {
  acceptAgreement,
  getCurrentAgreement,
  getMeta,
  listTags,
  pageAlbums,
  pageArtists,
  uploadSong,
  type AgreementView,
  type AlbumView,
  type ArtistView,
  type MusicMeta,
  type SongVisibility,
  type TagView,
} from '../api/music';

/** 协议正文（版本由服务端下发，正文随版本在应用内维护） */
const AGREEMENT_TEXT = [
  '1. 本人保证所上传的音频、歌词、封面等素材均为本人原创或已取得完整、合法、有效的授权，不存在任何权利争议。',
  '2. 本人对作品内容独立承担全部法律责任；如发生第三方投诉或权利纠纷，本人负责处理并承担相应后果。',
  '3. 平台有权对公开作品进行事后复核，对存在权利争议或违规内容的作品立即下架并保留处理记录。',
  '4. 选择「自听」的作品仅本人可见可播；选择「公开」的作品在租户内可见可播。',
  '5. 本协议按版本管理，签署记录与版本号、签署时间、客户端信息一并留存，用于权利追溯。',
].join('\n');

const LANGUAGE_OPTIONS = [
  { value: 'ZH', label: '中文' },
  { value: 'EN', label: '英文' },
  { value: 'JA', label: '日文' },
  { value: 'OTHER', label: '其他' },
];

interface UploadFormValues {
  artistId: number;
  albumId?: number;
  title: string;
  subtitle?: string;
  language?: string;
  visibility: SongVisibility;
  tagIds?: number[];
  lyricText?: string;
}

export default function UploadPage() {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const [form] = Form.useForm<UploadFormValues>();

  const [meta, setMeta] = useState<MusicMeta | null>(null);
  const [agreement, setAgreement] = useState<AgreementView | null>(null);
  const [agreementOpen, setAgreementOpen] = useState(false);
  const [agreed, setAgreed] = useState(false);
  const [accepting, setAccepting] = useState(false);

  const [tags, setTags] = useState<TagView[]>([]);
  const [artists, setArtists] = useState<ArtistView[]>([]);
  const [albums, setAlbums] = useState<AlbumView[]>([]);
  const [selectedArtistId, setSelectedArtistId] = useState<number | undefined>(undefined);

  const [audioFileId, setAudioFileId] = useState<number | undefined>(undefined);
  const [audioName, setAudioName] = useState<string>('');
  const [durationMs, setDurationMs] = useState<number | undefined>(undefined);
  const [uploadingAudio, setUploadingAudio] = useState(false);

  const [coverFileId, setCoverFileId] = useState<number | undefined>(undefined);
  const [coverName, setCoverName] = useState<string>('');
  const [uploadingCover, setUploadingCover] = useState(false);

  const [submitting, setSubmitting] = useState(false);
  const canUpload = hasPermission('sys:music:upload');

  useEffect(() => {
    if (!canUpload) {
      return;
    }
    getMeta()
      .then(setMeta)
      .catch((error) => message.error(error instanceof Error ? error.message : '服务信息加载失败'));
    getCurrentAgreement()
      .then((status) => {
        setAgreement(status);
        if (!status.accepted) {
          setAgreementOpen(true);
        }
      })
      .catch((error) => message.error(error instanceof Error ? error.message : '协议状态加载失败'));
    listTags()
      .then(setTags)
      .catch((error) => message.error(error instanceof Error ? error.message : '标签加载失败'));
    pageArtists({ current: 1, size: 200 })
      .then((result) => setArtists(result.records))
      .catch((error) => message.error(error instanceof Error ? error.message : '歌手加载失败'));
    pageAlbums({ current: 1, size: 200 })
      .then((result) => setAlbums(result.records))
      .catch((error) => message.error(error instanceof Error ? error.message : '专辑加载失败'));
  }, [canUpload, message]);

  const handleAccept = async () => {
    if (!agreement) {
      return;
    }
    if (!agreed) {
      message.warning('请先勾选确认声明');
      return;
    }
    setAccepting(true);
    try {
      const status = await acceptAgreement(agreement.agreementVersion);
      setAgreement(status);
      setAgreementOpen(false);
      message.success('已签署《版权无争议协议》');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '协议签署失败');
    } finally {
      setAccepting(false);
    }
  };

  const handleAudioSelect = async (file: UploadFile | File) => {
    const raw = file instanceof File ? file : (file.originFileObj as File | undefined);
    if (!raw) {
      message.error('无法读取所选音频文件');
      return;
    }
    setUploadingAudio(true);
    try {
      const uploaded = await uploadFile(raw);
      const duration = await readAudioDuration(raw);
      setAudioFileId(uploaded.id);
      setAudioName(uploaded.originalName ?? raw.name);
      setDurationMs(duration);
      message.success('音频已上传');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '音频上传失败');
    } finally {
      setUploadingAudio(false);
    }
  };

  const handleCoverSelect = (file: File) => {
    setUploadingCover(true);
    uploadFile(file)
      .then((uploaded) => {
        setCoverFileId(uploaded.id);
        setCoverName(uploaded.originalName ?? file.name);
        message.success('封面已上传');
      })
      .catch((error) => message.error(error instanceof Error ? error.message : '封面上传失败'))
      .finally(() => setUploadingCover(false));
  };

  const handleSubmit = async (values: UploadFormValues) => {
    if (!agreement?.accepted) {
      setAgreementOpen(true);
      message.warning('请先签署《版权无争议协议》');
      return;
    }
    if (!audioFileId) {
      message.warning('请先上传音频文件');
      return;
    }
    setSubmitting(true);
    try {
      await uploadSong({
        artistId: values.artistId,
        albumId: values.albumId,
        title: values.title,
        subtitle: values.subtitle || undefined,
        language: values.language || undefined,
        visibility: values.visibility || 'PUBLIC',
        tagIds: values.tagIds?.length ? values.tagIds : undefined,
        lyricText: values.lyricText || undefined,
        audioFileId,
        coverFileId,
        durationMs,
      });
      message.success('作品已上传并发布');
      form.resetFields();
      setAudioFileId(undefined);
      setAudioName('');
      setDurationMs(undefined);
      setCoverFileId(undefined);
      setCoverName('');
      setSelectedArtistId(undefined);
      navigate('/mine');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '作品上传失败');
    } finally {
      setSubmitting(false);
    }
  };

  if (!canUpload) {
    return (
      <Result
        status="403"
        title="暂无上传权限"
        subTitle="上传作品需要 sys:music:upload 权限，请联系租户管理员开通"
        extra={
          <Button type="primary" onClick={() => navigate('/library')}>
            返回曲库
          </Button>
        }
      />
    );
  }

  const albumOptions = albums
    .filter((album) => (selectedArtistId ? album.artistId === selectedArtistId : true))
    .map((album) => ({ value: album.id, label: album.title }));

  return (
    <div className="music-upload-card">
      <Card
        title="上传作品"
        extra={
          agreement?.accepted ? (
            <Typography.Text type="secondary">
              已签署协议 {agreement.agreementVersion}
            </Typography.Text>
          ) : (
            <Button size="small" danger onClick={() => setAgreementOpen(true)}>
              待签署协议
            </Button>
          )
        }
      >
        <Alert
          className="music-upload-tip"
          type="warning"
          showIcon
          message="上传前须签署《版权无争议协议》"
          description="请确保你对上传内容拥有完整权利；公开作品由运营事后复核，存在权利争议将被下架。"
        />
        {meta && (
          <Typography.Paragraph type="secondary">
            当前协议版本 {meta.agreementVersion}；允许格式 {meta.audioFormats?.join(' / ') ?? '-'}；
            单文件上限{' '}
            {meta.uploadMaxBytes ? `${Math.round(meta.uploadMaxBytes / 1024 / 1024)} MB` : '-'}
          </Typography.Paragraph>
        )}

        <Form form={form} layout="vertical" onFinish={(values) => void handleSubmit(values)}>
          <Form.Item label="音频文件（必填）">
            <Space>
              <Upload
                showUploadList={false}
                accept="audio/*"
                beforeUpload={(file) => {
                  void handleAudioSelect(file);
                  return false;
                }}
              >
                <Button loading={uploadingAudio}>
                  {audioFileId ? '重新上传音频' : '选择音频文件'}
                </Button>
              </Upload>
              {audioName && (
                <Typography.Text>
                  {audioName}
                  {durationMs ? `（${Math.round(durationMs / 1000)} 秒）` : ''}
                </Typography.Text>
              )}
            </Space>
          </Form.Item>

          <Form.Item label="封面（可选）">
            <Space>
              <Upload
                showUploadList={false}
                accept="image/*"
                beforeUpload={(file) => {
                  handleCoverSelect(file);
                  return false;
                }}
              >
                <Button loading={uploadingCover}>
                  {coverFileId ? '重新上传封面' : '选择封面图片'}
                </Button>
              </Upload>
              {coverName && <Typography.Text>{coverName}</Typography.Text>}
            </Space>
          </Form.Item>

          <Form.Item
            name="title"
            label="歌曲标题"
            rules={[{ required: true, message: '请输入歌曲标题' }]}
          >
            <Input maxLength={120} showCount placeholder="歌曲标题" />
          </Form.Item>

          <Form.Item name="subtitle" label="副标题">
            <Input maxLength={200} placeholder="可选，如版本 / 备注" />
          </Form.Item>

          <Form.Item
            name="artistId"
            label="歌手"
            rules={[{ required: true, message: '请选择歌手' }]}
            extra="若列表中没有对应歌手，请联系租户管理员在运营后台新增"
          >
            <Select
              showSearch
              optionFilterProp="label"
              placeholder="选择歌手"
              options={artists.map((artist) => ({ value: artist.id, label: artist.name }))}
              onChange={(value: number) => setSelectedArtistId(value)}
            />
          </Form.Item>

          <Form.Item name="albumId" label="专辑（可选）">
            <Select
              allowClear
              placeholder={selectedArtistId ? '选择该歌手的专辑' : '先选择歌手'}
              disabled={!selectedArtistId}
              options={albumOptions}
            />
          </Form.Item>

          <Form.Item name="language" label="语言">
            <Select allowClear placeholder="可选" options={LANGUAGE_OPTIONS} />
          </Form.Item>

          <Form.Item
            name="visibility"
            label="可见性"
            initialValue="PUBLIC"
            rules={[{ required: true, message: '请选择可见性' }]}
          >
            <Radio.Group>
              <Radio value="PUBLIC">公开（租户内可见可播，运营可复核下架）</Radio>
              <Radio value="PRIVATE">仅自听（仅本人可见可播）</Radio>
            </Radio.Group>
          </Form.Item>

          <Form.Item name="tagIds" label="标签">
            <Select
              mode="multiple"
              allowClear
              placeholder="可选，最多选择 5 个"
              maxCount={5}
              options={tags.map((tag) => ({ value: tag.id, label: tag.name }))}
            />
          </Form.Item>

          <Form.Item name="lyricText" label="歌词（纯文本）">
            <Input.TextArea rows={4} maxLength={5000} showCount placeholder="可选" />
          </Form.Item>

          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit" loading={submitting}>
                上传并发布
              </Button>
              <Button onClick={() => navigate('/library')}>取消</Button>
            </Space>
          </Form.Item>
        </Form>
      </Card>

      <Modal
        open={agreementOpen}
        title={
          agreement
            ? `${agreement.agreementTitle}（${agreement.agreementVersion}）`
            : '版权无争议协议'
        }
        okText="同意并签署"
        cancelText="暂不签署"
        confirmLoading={accepting}
        onOk={() => void handleAccept()}
        onCancel={() => setAgreementOpen(false)}
      >
        <Typography.Paragraph type="secondary">
          未签署当前版本协议前不能上传作品；同一版本重复签署幂等。
        </Typography.Paragraph>
        <Typography.Paragraph style={{ whiteSpace: 'pre-line', maxHeight: 260, overflow: 'auto' }}>
          {AGREEMENT_TEXT}
        </Typography.Paragraph>
        <Checkbox checked={agreed} onChange={(event) => setAgreed(event.target.checked)}>
          我已阅读并同意上述协议，承诺上传内容无版权争议
        </Checkbox>
      </Modal>
    </div>
  );
}
