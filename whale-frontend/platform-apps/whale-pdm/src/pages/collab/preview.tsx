// 在线预览：原生 PDF/图片/STL/glTF 直接查看，需转换的格式给出可诊断原因并可重试
import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Col,
  Empty,
  Radio,
  Row,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import type { TableProps } from 'antd';
import {
  createPreview,
  downloadPreviewContent,
  pagePreviewJobs,
  retryPreview,
  type PreviewJob,
  type PreviewStatus,
  type PreviewType,
} from '../../api/collab';
import {
  listVersions,
  pageDocuments,
  type PdmDocument,
  type PdmDocumentVersion,
} from '../../api/document';
import { hasPermission } from '../../utils/permission';

/** 预览状态文案与颜色 */
const STATUS_META: Record<PreviewStatus, { text: string; color: string }> = {
  PENDING: { text: '等待中', color: 'default' },
  RUNNING: { text: '转换中', color: 'processing' },
  SUCCESS: { text: '可预览', color: 'green' },
  FAILED: { text: '失败', color: 'red' },
  UNSUPPORTED: { text: '暂不支持', color: 'orange' },
};

export default function PreviewCenter() {
  const [documents, setDocuments] = useState<PdmDocument[]>([]);
  const [documentId, setDocumentId] = useState<number | undefined>();
  const [versions, setVersions] = useState<PdmDocumentVersion[]>([]);
  const [versionId, setVersionId] = useState<number | undefined>();
  const [previewType, setPreviewType] = useState<PreviewType>('2D');
  const [job, setJob] = useState<PreviewJob | null>(null);
  const [contentUrl, setContentUrl] = useState<string | null>(null);
  const [contentType, setContentType] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const [jobs, setJobs] = useState<PreviewJob[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [size, setSize] = useState(10);
  const [jobsLoading, setJobsLoading] = useState(false);

  const canCreate = hasPermission('sys:pdm:collab:preview:create');
  const canRetry = hasPermission('sys:pdm:collab:preview:retry');

  const loadDocuments = useCallback(async () => {
    try {
      const result = await pageDocuments({ current: 1, size: 100 });
      setDocuments(result.records);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '文档加载失败');
    }
  }, []);

  const loadJobs = useCallback(async () => {
    setJobsLoading(true);
    try {
      const result = await pagePreviewJobs({ current, size, documentId, versionId });
      setJobs(result.records);
      setTotal(Number(result.total));
    } catch (error) {
      message.error(error instanceof Error ? error.message : '预览任务加载失败');
    } finally {
      setJobsLoading(false);
    }
  }, [current, size, documentId, versionId]);

  useEffect(() => {
    void loadDocuments();
  }, [loadDocuments]);

  useEffect(() => {
    void loadJobs();
  }, [loadJobs]);

  useEffect(() => {
    if (!documentId) {
      setVersions([]);
      setVersionId(undefined);
      return;
    }
    void listVersions(documentId)
      .then((list) => {
        setVersions(list);
        const released = list.find((version) => version.status === 'RELEASED') ?? list[0];
        setVersionId(released?.id);
      })
      .catch((error: unknown) => {
        message.error(error instanceof Error ? error.message : '版本加载失败');
      });
  }, [documentId]);

  useEffect(() => {
    if (!job || job.status !== 'SUCCESS') {
      setContentUrl(null);
      setContentType(job?.sourceContentType ?? null);
      return;
    }
    if (!job.viewerHint || job.viewerHint === 'STL' || job.viewerHint === 'GLTF') {
      setContentUrl(null);
      setContentType(job.sourceContentType ?? null);
      return;
    }
    let revoked = false;
    let url: string | null = null;
    void downloadPreviewContent(job.id)
      .then((result) => {
        if (revoked) {
          return;
        }
        url = URL.createObjectURL(result.blob);
        setContentUrl(url);
        setContentType(result.blob.type || job.sourceContentType || null);
      })
      .catch((error: unknown) => {
        message.error(error instanceof Error ? error.message : '预览内容加载失败');
      });
    return () => {
      revoked = true;
      if (url) {
        URL.revokeObjectURL(url);
      }
    };
  }, [job]);

  const documentOptions = useMemo(
    () =>
      documents.map((document) => ({
        value: document.id,
        label: `${document.docNo} ${document.title}`,
      })),
    [documents],
  );

  const versionOptions = useMemo(
    () =>
      versions.map((version) => ({
        value: version.id,
        label: `${version.versionNo} · ${version.fileName} (${version.status})`,
      })),
    [versions],
  );

  const onCreate = async () => {
    if (!documentId || !versionId) {
      message.warning('请先选择文档与版本');
      return;
    }
    setLoading(true);
    try {
      const created = await createPreview(documentId, versionId, previewType);
      setJob(created);
      message.success('预览任务已创建');
      await loadJobs();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '创建预览失败');
    } finally {
      setLoading(false);
    }
  };

  const onRetry = async (jobId: number) => {
    try {
      const retried = await retryPreview(jobId);
      setJob(retried);
      message.info(retried.lastError ?? '已重试');
      await loadJobs();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '重试失败');
    }
  };

  const columns: TableProps<PreviewJob>['columns'] = [
    { title: '文档 ID', dataIndex: 'documentId', width: 100 },
    { title: '版本', dataIndex: 'versionNo', width: 100 },
    { title: '类型', dataIndex: 'previewType', width: 80 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 110,
      render: (value: PreviewStatus) => (
        <Tag color={STATUS_META[value].color}>{STATUS_META[value].text}</Tag>
      ),
    },
    { title: '说明', dataIndex: 'lastError', ellipsis: true, render: (v: string) => v || '-' },
    { title: '重试', dataIndex: 'retryCount', width: 70 },
    {
      title: '操作',
      width: 140,
      render: (_, row) => (
        <Space size={4}>
          {canRetry && row.status !== 'SUCCESS' ? (
            <Button type="link" size="small" onClick={() => void onRetry(row.id)}>
              重试
            </Button>
          ) : null}
          <Button type="link" size="small" onClick={() => setJob(row)}>
            查看
          </Button>
        </Space>
      ),
    },
  ];

  const renderViewer = () => {
    if (!job) {
      return <Empty description="选择文档与版本后发起预览" />;
    }
    if (job.status === 'UNSUPPORTED' || job.status === 'FAILED') {
      return (
        <Alert
          type="warning"
          showIcon
          message="无法在线预览"
          description={job.lastError ?? '未知原因'}
        />
      );
    }
    if (job.status !== 'SUCCESS') {
      return (
        <Alert
          type="info"
          showIcon
          message="预览处理中"
          description={job.lastError ?? '请稍后重试'}
        />
      );
    }
    if (job.viewerHint === 'STL' || job.viewerHint === 'GLTF') {
      return (
        <Alert
          type="info"
          showIcon
          message={`${job.viewerHint} 为原生格式`}
          description="3D 查看器尚未接入渲染引擎，请下载后在桌面端查看。"
        />
      );
    }
    if (!contentUrl) {
      return <Empty description="预览内容加载中…" />;
    }
    if (job.viewerHint === 'IMAGE') {
      return <img src={contentUrl} alt={job.sourceFileName} style={{ maxWidth: '100%' }} />;
    }
    return (
      <iframe
        title={job.sourceFileName ?? 'PDF 预览'}
        src={contentUrl}
        style={{ width: '100%', height: 520, border: 'none' }}
      />
    );
  };

  return (
    <Row gutter={16}>
      <Col span={9}>
        <Card title="发起预览">
          <Space direction="vertical" style={{ width: '100%' }} size="middle">
            <Select
              showSearch
              allowClear
              optionFilterProp="label"
              placeholder="选择文档"
              style={{ width: '100%' }}
              value={documentId}
              options={documentOptions}
              onChange={(value) => {
                setDocumentId(value);
                setJob(null);
              }}
            />
            <Select
              allowClear
              placeholder="选择版本（默认最新已发布）"
              style={{ width: '100%' }}
              value={versionId}
              options={versionOptions}
              onChange={setVersionId}
            />
            <Radio.Group
              value={previewType}
              onChange={(event) => setPreviewType(event.target.value)}
            >
              <Radio.Button value="2D">2D 图纸/文档</Radio.Button>
              <Radio.Button value="3D">3D 模型</Radio.Button>
            </Radio.Group>
            <Button
              type="primary"
              disabled={!canCreate}
              loading={loading}
              onClick={() => void onCreate()}
            >
              发起预览
            </Button>
            {job ? (
              <Card size="small" title="任务状态">
                <Space direction="vertical" size={4}>
                  <Space>
                    <Tag color={STATUS_META[job.status].color}>{STATUS_META[job.status].text}</Tag>
                    <Typography.Text type="secondary">重试 {job.retryCount} 次</Typography.Text>
                  </Space>
                  {job.viewerHint ? (
                    <Typography.Text>查看器：{job.viewerHint}</Typography.Text>
                  ) : null}
                  {job.lastError ? (
                    <Typography.Text type="warning">{job.lastError}</Typography.Text>
                  ) : null}
                  {job.status !== 'SUCCESS' && canRetry ? (
                    <Button size="small" onClick={() => void onRetry(job.id)}>
                      重试
                    </Button>
                  ) : null}
                </Space>
              </Card>
            ) : null}
          </Space>
        </Card>
      </Col>
      <Col span={15}>
        <Card title="预览视图" style={{ marginBottom: 16 }}>
          {renderViewer()}
          {contentType ? (
            <Typography.Text type="secondary" style={{ display: 'block', marginTop: 8 }}>
              内容类型：{contentType}
            </Typography.Text>
          ) : null}
        </Card>
        <Card title="最近预览任务">
          <Table<PreviewJob>
            rowKey="id"
            size="small"
            loading={jobsLoading}
            columns={columns}
            dataSource={jobs}
            pagination={{
              current,
              pageSize: size,
              total,
              onChange: (page, pageSize) => {
                setCurrent(page);
                setSize(pageSize);
              },
            }}
          />
        </Card>
      </Col>
    </Row>
  );
}
