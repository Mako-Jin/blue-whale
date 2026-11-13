// OA 公文 - 发文管理：拟稿、提交审核、审核、签发、下发、归档与附件下载
import { useCallback, useEffect, useState } from 'react';
import {
  App,
  Button,
  DatePicker,
  Descriptions,
  Drawer,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Upload,
} from 'antd';
import type { TableProps, UploadFile, UploadProps } from 'antd';
import dayjs from 'dayjs';
import type { Dayjs } from 'dayjs';
import {
  auditOutgoingDoc,
  archiveOutgoingDoc,
  createOutgoingDoc,
  deleteOutgoingDoc,
  downloadDocAttachment,
  getOutgoingDoc,
  issueOutgoingDoc,
  listDocumentTemplates,
  pageOutgoingDocs,
  signOutgoingDoc,
  submitOutgoingDoc,
  updateOutgoingDoc,
  type OaDocAttachmentInput,
  type OaDocOutgoing,
  type OaDocOutgoingInput,
  type OaDocTemplate,
  type OaOutgoingStatus,
} from '../../api/document';
import type { UploadedFile } from '../../api/file';
import { uploadOaFile } from '../../api/file';
import { hasPermission } from '../../utils/permission';

/** 发文状态文案 */
const STATUS_META: Record<OaOutgoingStatus, { text: string; color: string }> = {
  DRAFT: { text: '草稿', color: 'default' },
  PENDING: { text: '审核中', color: 'processing' },
  APPROVED: { text: '已通过', color: 'success' },
  REJECTED: { text: '已驳回', color: 'error' },
  SIGNED: { text: '已签发', color: 'blue' },
  ISSUED: { text: '已下发', color: 'cyan' },
  ARCHIVED: { text: '已归档', color: 'purple' },
};

/** 文种选项 */
const DOC_TYPE_OPTIONS = [
  { value: 'NOTICE', label: '通知' },
  { value: 'REQUEST', label: '请示' },
  { value: 'REPLY', label: '批复' },
  { value: 'LETTER', label: '函' },
  { value: 'MINUTES', label: '纪要' },
  { value: 'ANNOUNCEMENT', label: '公告' },
];

/** 密级文案 */
const SECURITY_META: Record<number, { text: string; color: string }> = {
  1: { text: '公开', color: 'default' },
  2: { text: '内部', color: 'blue' },
  3: { text: '秘密', color: 'red' },
};

/** 紧急度文案 */
const URGENCY_META: Record<number, { text: string; color: string }> = {
  1: { text: '平件', color: 'default' },
  2: { text: '急件', color: 'orange' },
  3: { text: '特急', color: 'red' },
};

/** 单笔附件上限 */
const MAX_ATTACHMENTS = 5;

/** 单个附件大小上限：20MB */
const MAX_ATTACHMENT_SIZE = 20 * 1024 * 1024;

/** 发文表单值 */
interface OutgoingFormValues {
  title: string;
  docType: string;
  securityLevel: number;
  urgency: number;
  templateId?: number;
  mainRecipients?: string;
  copyRecipients?: string;
  content?: string;
  remark?: string;
}

/** 签发表单值 */
interface SignFormValues {
  signer?: string;
  signDate?: Dayjs;
}

/** 时间展示 */
function timeText(value?: string): string {
  return value ? value.replace('T', ' ').slice(0, 16) : '-';
}

/** 文种展示 */
function docTypeText(value?: string): string {
  return DOC_TYPE_OPTIONS.find((item) => item.value === value)?.label ?? value ?? '-';
}

export default function OutgoingDocPage() {
  const { message } = App.useApp();
  const [data, setData] = useState<OaDocOutgoing[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<OaOutgoingStatus | ''>('');
  const [docType, setDocType] = useState('');
  const [securityLevel, setSecurityLevel] = useState<number | undefined>(undefined);
  const [loading, setLoading] = useState(false);
  const [templates, setTemplates] = useState<OaDocTemplate[]>([]);

  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<OaDocOutgoing | null>(null);
  const [saving, setSaving] = useState(false);
  const [fileList, setFileList] = useState<UploadFile[]>([]);
  const [form] = Form.useForm<OutgoingFormValues>();

  const [signTarget, setSignTarget] = useState<OaDocOutgoing | null>(null);
  const [signing, setSigning] = useState(false);
  const [signForm] = Form.useForm<SignFormValues>();

  const [detail, setDetail] = useState<OaDocOutgoing | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);

  const canAdd = hasPermission('sys:oa:document:add');
  const canEdit = hasPermission('sys:oa:document:edit');
  const canDelete = hasPermission('sys:oa:document:delete');
  const canSubmit = hasPermission('sys:oa:document:submit');
  const canAudit = hasPermission('sys:oa:document:audit');
  const canSign = hasPermission('sys:oa:document:sign');
  const canArchive = hasPermission('sys:oa:document:archive');
  const canDownload = hasPermission('sys:oa:document:download');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageOutgoingDocs({
        current,
        size: 10,
        keyword: keyword.trim() || undefined,
        status: status || undefined,
        docType: docType || undefined,
        securityLevel,
      });
      setData(page.records);
      setTotal(page.total);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '发文查询失败');
      setData([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, [current, keyword, status, docType, securityLevel, message]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    listDocumentTemplates('OUTGOING')
      .then((list) => setTemplates(list.filter((item) => item.status === 'RELEASED')))
      .catch(() => setTemplates([]));
  }, []);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ docType: 'NOTICE', securityLevel: 2, urgency: 2 });
    setFileList([]);
    setModalOpen(true);
  };

  const openEdit = (record: OaDocOutgoing) => {
    setEditing(record);
    form.resetFields();
    form.setFieldsValue({
      title: record.title,
      docType: record.docType,
      securityLevel: record.securityLevel,
      urgency: record.urgency,
      templateId: record.templateId,
      mainRecipients: record.mainRecipients,
      copyRecipients: record.copyRecipients,
      content: record.content,
      remark: record.remark,
    });
    setFileList(
      (record.attachments ?? []).map((attachment) => ({
        uid: `ref-${attachment.id}`,
        name: attachment.fileName ?? String(attachment.fileId),
        status: 'done',
        response: {
          id: attachment.fileId,
          fileName: attachment.fileName ?? String(attachment.fileId),
          fileSize: attachment.fileSize,
          contentType: attachment.contentType,
        } satisfies UploadedFile,
      })),
    );
    setModalOpen(true);
  };

  const beforeUpload: UploadProps['beforeUpload'] = (file) => {
    if (file.size > MAX_ATTACHMENT_SIZE) {
      message.error('单个附件不能超过 20MB');
      return Upload.LIST_IGNORE;
    }
    return true;
  };

  const customUpload: UploadProps['customRequest'] = (options) => {
    const file = options.file as unknown as File & { uid: string };
    void (async () => {
      try {
        const uploaded = await uploadOaFile(file);
        setFileList((prev) =>
          prev.map((item) =>
            item.uid === file.uid ? { ...item, status: 'done', response: uploaded } : item,
          ),
        );
        options.onSuccess?.(uploaded);
      } catch (error) {
        setFileList((prev) => prev.filter((item) => item.uid !== file.uid));
        options.onError?.(error instanceof Error ? error : new Error('上传失败'));
        message.error(error instanceof Error ? error.message : '附件上传失败');
      }
    })();
  };

  const handleFileListChange: UploadProps['onChange'] = ({ fileList: next }) => {
    setFileList((prev) =>
      next.map((item) => {
        const previous = prev.find((candidate) => candidate.uid === item.uid);
        return previous?.response ? { ...item, response: previous.response } : item;
      }),
    );
  };

  const collectAttachments = (): OaDocAttachmentInput[] => {
    const done = fileList.filter((item) => item.status === 'done');
    return done.flatMap((item) => {
      const response = item.response as UploadedFile | undefined;
      if (typeof response?.id !== 'number') {
        return [];
      }
      return [
        {
          fileId: response.id,
          fileName: response.fileName ?? item.name,
          fileSize: response.fileSize,
          contentType: response.contentType,
        },
      ];
    });
  };

  const handleSave = async () => {
    const values = await form.validateFields();
    if (fileList.some((item) => item.status === 'uploading')) {
      message.warning('附件上传中，请稍候');
      return;
    }
    const payload: OaDocOutgoingInput = {
      title: values.title.trim(),
      docType: values.docType,
      securityLevel: values.securityLevel,
      urgency: values.urgency,
      templateId: values.templateId,
      mainRecipients: values.mainRecipients?.trim() || undefined,
      copyRecipients: values.copyRecipients?.trim() || undefined,
      content: values.content,
      remark: values.remark?.trim() || undefined,
      attachments: collectAttachments(),
    };
    setSaving(true);
    try {
      if (editing) {
        await updateOutgoingDoc(editing.id, payload);
        message.success('发文已更新');
      } else {
        await createOutgoingDoc(payload);
        message.success('发文已拟稿并分配文号');
      }
      setModalOpen(false);
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '发文保存失败');
    } finally {
      setSaving(false);
    }
  };

  const runAction = async (action: () => Promise<OaDocOutgoing>, success: string) => {
    try {
      await action();
      message.success(success);
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '操作失败');
    }
  };

  const handleDelete = async (record: OaDocOutgoing) => {
    try {
      await deleteOutgoingDoc(record.id);
      message.success('发文已删除');
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '发文删除失败');
    }
  };

  const openSign = (record: OaDocOutgoing) => {
    setSignTarget(record);
    signForm.resetFields();
    signForm.setFieldsValue({ signDate: dayjs() });
  };

  const handleSign = async () => {
    if (!signTarget) {
      return;
    }
    const values = await signForm.validateFields();
    setSigning(true);
    try {
      await signOutgoingDoc(signTarget.id, {
        signer: values.signer?.trim() || undefined,
        signDate: values.signDate?.format('YYYY-MM-DD'),
      });
      message.success('发文已签发');
      setSignTarget(null);
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '签发失败');
    } finally {
      setSigning(false);
    }
  };

  const openDetail = async (record: OaDocOutgoing) => {
    try {
      const data = await getOutgoingDoc(record.id);
      setDetail(data);
      setDetailOpen(true);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '详情加载失败');
    }
  };

  const downloadAttachment = async (
    record: OaDocOutgoing,
    attachmentId: number,
    fileName: string,
  ) => {
    try {
      await downloadDocAttachment('OUTGOING', record.id, attachmentId, fileName);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '附件下载失败');
    }
  };

  const columns: TableProps<OaDocOutgoing>['columns'] = [
    { title: '文号', dataIndex: 'docNo', key: 'docNo', width: 170 },
    { title: '标题', dataIndex: 'title', key: 'title', ellipsis: true },
    {
      title: '文种',
      dataIndex: 'docType',
      key: 'docType',
      width: 90,
      render: (value?: string) => docTypeText(value),
    },
    {
      title: '密级',
      dataIndex: 'securityLevel',
      key: 'securityLevel',
      width: 80,
      render: (value: number) => {
        const meta = SECURITY_META[value] ?? { text: String(value), color: 'default' };
        return <Tag color={meta.color}>{meta.text}</Tag>;
      },
    },
    {
      title: '紧急度',
      dataIndex: 'urgency',
      key: 'urgency',
      width: 90,
      render: (value: number) => {
        const meta = URGENCY_META[value] ?? { text: String(value), color: 'default' };
        return <Tag color={meta.color}>{meta.text}</Tag>;
      },
    },
    {
      title: '拟稿人',
      dataIndex: 'drafterName',
      key: 'drafterName',
      width: 110,
      render: (value: string | undefined, record) => value ?? record.drafterId,
    },
    {
      title: '主送',
      dataIndex: 'mainRecipients',
      key: 'mainRecipients',
      width: 160,
      ellipsis: true,
      render: (value?: string) => value || '-',
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (value: OaOutgoingStatus) => {
        const meta = STATUS_META[value] ?? { text: value, color: 'default' };
        return <Tag color={meta.color}>{meta.text}</Tag>;
      },
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      key: 'createTime',
      width: 160,
      render: (value?: string) => timeText(value),
    },
    {
      title: '操作',
      key: 'action',
      width: 260,
      render: (_value, record) => {
        const editable = record.status === 'DRAFT' || record.status === 'REJECTED';
        return (
          <Space size={4} wrap>
            <Button type="link" size="small" onClick={() => void openDetail(record)}>
              详情
            </Button>
            {editable && canEdit ? (
              <Button type="link" size="small" onClick={() => openEdit(record)}>
                编辑
              </Button>
            ) : null}
            {editable && canSubmit ? (
              <Popconfirm
                title="确认提交审核？"
                onConfirm={() => void runAction(() => submitOutgoingDoc(record.id), '已提交审核')}
              >
                <Button type="link" size="small">
                  提交
                </Button>
              </Popconfirm>
            ) : null}
            {record.status === 'PENDING' && canAudit ? (
              <>
                <Popconfirm
                  title="确认审核通过？"
                  onConfirm={() =>
                    void runAction(() => auditOutgoingDoc(record.id, true), '审核已通过')
                  }
                >
                  <Button type="link" size="small">
                    通过
                  </Button>
                </Popconfirm>
                <Popconfirm
                  title="确认驳回该发文？"
                  onConfirm={() =>
                    void runAction(() => auditOutgoingDoc(record.id, false), '已驳回')
                  }
                >
                  <Button type="link" size="small" danger>
                    驳回
                  </Button>
                </Popconfirm>
              </>
            ) : null}
            {record.status === 'APPROVED' && canSign ? (
              <Button type="link" size="small" onClick={() => openSign(record)}>
                签发
              </Button>
            ) : null}
            {record.status === 'SIGNED' && canSign ? (
              <Popconfirm
                title="确认下发该发文？"
                onConfirm={() => void runAction(() => issueOutgoingDoc(record.id), '发文已下发')}
              >
                <Button type="link" size="small">
                  下发
                </Button>
              </Popconfirm>
            ) : null}
            {record.status === 'ISSUED' && canArchive ? (
              <Popconfirm
                title="确认推送归档？"
                onConfirm={() => void runAction(() => archiveOutgoingDoc(record.id), '发文已归档')}
              >
                <Button type="link" size="small">
                  归档
                </Button>
              </Popconfirm>
            ) : null}
            {editable && canDelete ? (
              <Popconfirm title="确认删除该发文？" onConfirm={() => void handleDelete(record)}>
                <Button type="link" size="small" danger>
                  删除
                </Button>
              </Popconfirm>
            ) : null}
          </Space>
        );
      },
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <Input
          allowClear
          placeholder="文号/标题"
          style={{ width: 200 }}
          value={keyword}
          onChange={(event) => {
            setKeyword(event.target.value);
            setCurrent(1);
          }}
        />
        <Select
          allowClear
          placeholder="状态"
          style={{ width: 130 }}
          value={status || undefined}
          options={Object.entries(STATUS_META).map(([value, meta]) => ({
            label: meta.text,
            value,
          }))}
          onChange={(value) => {
            setStatus((value as OaOutgoingStatus) ?? '');
            setCurrent(1);
          }}
        />
        <Select
          allowClear
          placeholder="文种"
          style={{ width: 120 }}
          value={docType || undefined}
          options={DOC_TYPE_OPTIONS}
          onChange={(value) => {
            setDocType(value ?? '');
            setCurrent(1);
          }}
        />
        <Select
          allowClear
          placeholder="密级"
          style={{ width: 110 }}
          value={securityLevel}
          options={Object.entries(SECURITY_META).map(([value, meta]) => ({
            label: meta.text,
            value: Number(value),
          }))}
          onChange={(value) => {
            setSecurityLevel(value);
            setCurrent(1);
          }}
        />
        {canAdd ? (
          <Button type="primary" onClick={openCreate}>
            新建发文
          </Button>
        ) : null}
        <Button onClick={() => void load()}>刷新</Button>
      </Space>
      <Table<OaDocOutgoing>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={data}
        scroll={{ x: 1500 }}
        pagination={{
          current,
          pageSize: 10,
          total,
          onChange: (page) => setCurrent(page),
        }}
      />
      <Modal
        open={modalOpen}
        title={editing ? '编辑发文' : '新建发文'}
        width={760}
        onOk={() => void handleSave()}
        confirmLoading={saving}
        onCancel={() => setModalOpen(false)}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="title"
            label="公文标题"
            rules={[{ required: true, message: '请输入公文标题' }]}
          >
            <Input maxLength={255} />
          </Form.Item>
          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item name="docType" label="文种" style={{ flex: 1 }}>
              <Select options={DOC_TYPE_OPTIONS} />
            </Form.Item>
            <Form.Item name="securityLevel" label="密级" style={{ flex: 1 }}>
              <Select
                options={Object.entries(SECURITY_META).map(([value, meta]) => ({
                  label: meta.text,
                  value: Number(value),
                }))}
              />
            </Form.Item>
            <Form.Item name="urgency" label="紧急度" style={{ flex: 1 }}>
              <Select
                options={Object.entries(URGENCY_META).map(([value, meta]) => ({
                  label: meta.text,
                  value: Number(value),
                }))}
              />
            </Form.Item>
          </Space>
          <Form.Item name="templateId" label="公文模板">
            <Select
              allowClear
              placeholder="可选"
              options={templates.map((item) => ({
                label: `${item.name}（v${item.version}）`,
                value: item.id,
              }))}
            />
          </Form.Item>
          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item name="mainRecipients" label="主送" style={{ flex: 1 }}>
              <Input maxLength={512} placeholder="选填" />
            </Form.Item>
            <Form.Item name="copyRecipients" label="抄送" style={{ flex: 1 }}>
              <Input maxLength={512} placeholder="选填" />
            </Form.Item>
          </Space>
          <Form.Item name="content" label="正文">
            <Input.TextArea rows={5} placeholder="选填" />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} maxLength={255} showCount placeholder="选填" />
          </Form.Item>
          <Form.Item label="附件" extra="最多 5 个，单个不超过 20MB">
            <Upload
              multiple
              maxCount={MAX_ATTACHMENTS}
              fileList={fileList}
              beforeUpload={beforeUpload}
              customRequest={customUpload}
              onChange={handleFileListChange}
              onRemove={(file) => {
                setFileList((prev) => prev.filter((item) => item.uid !== file.uid));
                return true;
              }}
            >
              <Button>选择文件</Button>
            </Upload>
          </Form.Item>
        </Form>
      </Modal>
      <Modal
        open={signTarget !== null}
        title="签发文"
        onOk={() => void handleSign()}
        confirmLoading={signing}
        onCancel={() => setSignTarget(null)}
        destroyOnClose
      >
        <Form form={signForm} layout="vertical">
          <Form.Item name="signer" label="签发人" extra="留空则使用当前登录用户">
            <Input maxLength={64} placeholder="选填" />
          </Form.Item>
          <Form.Item name="signDate" label="签发日期">
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>
      <Drawer
        open={detailOpen}
        title={detail ? `发文详情：${detail.docNo}` : '发文详情'}
        width={720}
        onClose={() => setDetailOpen(false)}
      >
        {detail ? (
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <Descriptions
              column={2}
              size="small"
              bordered
              items={[
                { key: 'docNo', label: '文号', children: detail.docNo },
                {
                  key: 'status',
                  label: '状态',
                  children: STATUS_META[detail.status]?.text ?? detail.status,
                },
                { key: 'title', label: '标题', children: detail.title, span: 2 },
                {
                  key: 'docType',
                  label: '文种',
                  children: docTypeText(detail.docType),
                },
                {
                  key: 'securityLevel',
                  label: '密级',
                  children: SECURITY_META[detail.securityLevel]?.text ?? detail.securityLevel,
                },
                {
                  key: 'urgency',
                  label: '紧急度',
                  children: URGENCY_META[detail.urgency]?.text ?? detail.urgency,
                },
                {
                  key: 'drafter',
                  label: '拟稿人',
                  children: detail.drafterName ?? detail.drafterId,
                },
                { key: 'template', label: '模板', children: detail.templateName || '-' },
                {
                  key: 'mainRecipients',
                  label: '主送',
                  children: detail.mainRecipients || '-',
                  span: 2,
                },
                {
                  key: 'copyRecipients',
                  label: '抄送',
                  children: detail.copyRecipients || '-',
                  span: 2,
                },
                { key: 'signer', label: '签发人', children: detail.signer || '-' },
                { key: 'signDate', label: '签发日期', children: detail.signDate || '-' },
                { key: 'issuedAt', label: '下发时间', children: timeText(detail.issuedAt) },
                {
                  key: 'approval',
                  label: '审批实例',
                  children: detail.approvalInstanceId ?? '-',
                },
                {
                  key: 'archive',
                  label: '档案条目',
                  children: detail.archiveItemId || '-',
                  span: 2,
                },
                { key: 'remark', label: '备注', children: detail.remark || '-', span: 2 },
                { key: 'content', label: '正文', children: detail.content || '-', span: 2 },
              ]}
            />
            <div>
              <div style={{ fontWeight: 600, marginBottom: 8 }}>附件</div>
              {detail.attachments && detail.attachments.length > 0 ? (
                <Space direction="vertical" size={6}>
                  {detail.attachments.map((attachment) => (
                    <Space key={attachment.id} size={8}>
                      <span>{attachment.fileName ?? attachment.fileId}</span>
                      {canDownload ? (
                        <Button
                          type="link"
                          size="small"
                          onClick={() =>
                            void downloadAttachment(
                              detail,
                              attachment.id,
                              attachment.fileName ?? String(attachment.fileId),
                            )
                          }
                        >
                          下载
                        </Button>
                      ) : null}
                    </Space>
                  ))}
                </Space>
              ) : (
                <span style={{ color: '#999' }}>暂无附件</span>
              )}
            </div>
            <div>
              <div style={{ fontWeight: 600, marginBottom: 8 }}>归档</div>
              {detail.archiveRef ? (
                <Descriptions
                  column={2}
                  size="small"
                  bordered
                  items={[
                    {
                      key: 'status',
                      label: '归档状态',
                      children: detail.archiveRef.status,
                    },
                    {
                      key: 'item',
                      label: '档案条目',
                      children: detail.archiveRef.archiveItemId || '-',
                    },
                    {
                      key: 'source',
                      label: '来源ID',
                      children: detail.archiveRef.sourceId || '-',
                    },
                    {
                      key: 'pushedAt',
                      label: '推送时间',
                      children: timeText(detail.archiveRef.pushedAt),
                    },
                    {
                      key: 'error',
                      label: '失败原因',
                      children: detail.archiveRef.errorMessage || '-',
                      span: 2,
                    },
                  ]}
                />
              ) : (
                <span style={{ color: '#999' }}>尚未归档</span>
              )}
            </div>
          </Space>
        ) : null}
      </Drawer>
    </Space>
  );
}
