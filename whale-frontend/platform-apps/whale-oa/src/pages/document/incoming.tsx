// OA 公文 - 收文管理：登记、批阅、转发、办结、归档与附件下载
import { useCallback, useEffect, useState } from 'react';
import {
  App,
  Button,
  DatePicker,
  Descriptions,
  Drawer,
  Form,
  Input,
  InputNumber,
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
  archiveIncomingDoc,
  completeIncomingDoc,
  createIncomingDoc,
  deleteIncomingDoc,
  downloadDocAttachment,
  forwardIncomingDoc,
  getIncomingDoc,
  pageIncomingDocs,
  reviewIncomingDoc,
  updateIncomingDoc,
  type OaDocAttachmentInput,
  type OaDocIncoming,
  type OaDocIncomingInput,
  type OaIncomingStatus,
} from '../../api/document';
import type { UploadedFile } from '../../api/file';
import { uploadOaFile } from '../../api/file';
import { hasPermission } from '../../utils/permission';

/** 收文状态文案 */
const STATUS_META: Record<OaIncomingStatus, { text: string; color: string }> = {
  REGISTERED: { text: '已登记', color: 'processing' },
  REVIEWED: { text: '已批阅', color: 'blue' },
  FORWARDED: { text: '已转发', color: 'cyan' },
  COMPLETED: { text: '已办结', color: 'success' },
  ARCHIVED: { text: '已归档', color: 'purple' },
};

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

/** 收文登记表单值 */
interface IncomingFormValues {
  sourceUnit: string;
  sourceNo?: string;
  title: string;
  securityLevel: number;
  urgency: number;
  receivedDate?: Dayjs;
  dueDate?: Dayjs;
  remark?: string;
}

/** 批阅表单值 */
interface ReviewFormValues {
  instruction: string;
  assigneeId?: number;
  assigneeName?: string;
}

/** 转发表单值 */
interface ForwardFormValues {
  assigneeId?: number;
  assigneeName?: string;
  remark?: string;
}

/** 时间展示 */
function timeText(value?: string): string {
  return value ? value.replace('T', ' ').slice(0, 16) : '-';
}

export default function IncomingDocPage() {
  const { message } = App.useApp();
  const [data, setData] = useState<OaDocIncoming[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<OaIncomingStatus | ''>('');
  const [securityLevel, setSecurityLevel] = useState<number | undefined>(undefined);
  const [loading, setLoading] = useState(false);

  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<OaDocIncoming | null>(null);
  const [saving, setSaving] = useState(false);
  const [fileList, setFileList] = useState<UploadFile[]>([]);
  const [form] = Form.useForm<IncomingFormValues>();

  const [reviewTarget, setReviewTarget] = useState<OaDocIncoming | null>(null);
  const [reviewing, setReviewing] = useState(false);
  const [reviewForm] = Form.useForm<ReviewFormValues>();

  const [forwardTarget, setForwardTarget] = useState<OaDocIncoming | null>(null);
  const [forwarding, setForwarding] = useState(false);
  const [forwardForm] = Form.useForm<ForwardFormValues>();

  const [detail, setDetail] = useState<OaDocIncoming | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);

  const canAdd = hasPermission('sys:oa:document:add');
  const canEdit = hasPermission('sys:oa:document:edit');
  const canDelete = hasPermission('sys:oa:document:delete');
  const canHandle = hasPermission('sys:oa:document:incoming');
  const canArchive = hasPermission('sys:oa:document:archive');
  const canDownload = hasPermission('sys:oa:document:download');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageIncomingDocs({
        current,
        size: 10,
        keyword: keyword.trim() || undefined,
        status: status || undefined,
        securityLevel,
      });
      setData(page.records);
      setTotal(page.total);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '收文查询失败');
      setData([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, [current, keyword, status, securityLevel, message]);

  useEffect(() => {
    void load();
  }, [load]);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({
      securityLevel: 2,
      urgency: 2,
      receivedDate: dayjs(),
    });
    setFileList([]);
    setModalOpen(true);
  };

  const openEdit = (record: OaDocIncoming) => {
    setEditing(record);
    form.resetFields();
    form.setFieldsValue({
      sourceUnit: record.sourceUnit,
      sourceNo: record.sourceNo,
      title: record.title,
      securityLevel: record.securityLevel,
      urgency: record.urgency,
      receivedDate: record.receivedDate ? dayjs(record.receivedDate) : undefined,
      dueDate: record.dueDate ? dayjs(record.dueDate) : undefined,
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
    const payload: OaDocIncomingInput = {
      sourceUnit: values.sourceUnit.trim(),
      sourceNo: values.sourceNo?.trim() || undefined,
      title: values.title.trim(),
      securityLevel: values.securityLevel,
      urgency: values.urgency,
      receivedDate: values.receivedDate?.format('YYYY-MM-DD'),
      dueDate: values.dueDate?.format('YYYY-MM-DD'),
      remark: values.remark?.trim() || undefined,
      attachments: collectAttachments(),
    };
    setSaving(true);
    try {
      if (editing) {
        await updateIncomingDoc(editing.id, payload);
        message.success('收文已更新');
      } else {
        await createIncomingDoc(payload);
        message.success('收文已登记并分配收文号');
      }
      setModalOpen(false);
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '收文保存失败');
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (record: OaDocIncoming) => {
    try {
      await deleteIncomingDoc(record.id);
      message.success('收文已删除');
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '收文删除失败');
    }
  };

  const runAction = async (action: () => Promise<OaDocIncoming>, success: string) => {
    try {
      await action();
      message.success(success);
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '操作失败');
    }
  };

  const openReview = (record: OaDocIncoming) => {
    setReviewTarget(record);
    reviewForm.resetFields();
    reviewForm.setFieldsValue({
      instruction: record.instruction,
      assigneeId: record.assigneeId,
      assigneeName: record.assigneeName,
    });
  };

  const handleReview = async () => {
    if (!reviewTarget) {
      return;
    }
    const values = await reviewForm.validateFields();
    setReviewing(true);
    try {
      await reviewIncomingDoc(reviewTarget.id, {
        instruction: values.instruction.trim(),
        assigneeId: values.assigneeId,
        assigneeName: values.assigneeName?.trim() || undefined,
      });
      message.success('收文已批阅');
      setReviewTarget(null);
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '批阅失败');
    } finally {
      setReviewing(false);
    }
  };

  const openForward = (record: OaDocIncoming) => {
    setForwardTarget(record);
    forwardForm.resetFields();
    forwardForm.setFieldsValue({
      assigneeId: record.assigneeId,
      assigneeName: record.assigneeName,
    });
  };

  const handleForward = async () => {
    if (!forwardTarget) {
      return;
    }
    const values = await forwardForm.validateFields();
    setForwarding(true);
    try {
      await forwardIncomingDoc(forwardTarget.id, {
        assigneeId: values.assigneeId,
        assigneeName: values.assigneeName?.trim() || undefined,
        remark: values.remark?.trim() || undefined,
      });
      message.success('收文已转发');
      setForwardTarget(null);
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '转发失败');
    } finally {
      setForwarding(false);
    }
  };

  const openDetail = async (record: OaDocIncoming) => {
    try {
      const detailData = await getIncomingDoc(record.id);
      setDetail(detailData);
      setDetailOpen(true);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '详情加载失败');
    }
  };

  const downloadAttachment = async (
    record: OaDocIncoming,
    attachmentId: number,
    fileName: string,
  ) => {
    try {
      await downloadDocAttachment('INCOMING', record.id, attachmentId, fileName);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '附件下载失败');
    }
  };

  const columns: TableProps<OaDocIncoming>['columns'] = [
    { title: '收文号', dataIndex: 'incomingNo', key: 'incomingNo', width: 170 },
    { title: '来文单位', dataIndex: 'sourceUnit', key: 'sourceUnit', width: 140, ellipsis: true },
    {
      title: '来文文号',
      dataIndex: 'sourceNo',
      key: 'sourceNo',
      width: 140,
      render: (value?: string) => value || '-',
    },
    { title: '标题', dataIndex: 'title', key: 'title', ellipsis: true },
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
      title: '收文日期',
      dataIndex: 'receivedDate',
      key: 'receivedDate',
      width: 110,
      render: (value?: string) => value || '-',
    },
    {
      title: '承办人',
      dataIndex: 'assigneeName',
      key: 'assigneeName',
      width: 110,
      render: (value: string | undefined, record) => value ?? record.assigneeId ?? '-',
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (value: OaIncomingStatus) => {
        const meta = STATUS_META[value] ?? { text: value, color: 'default' };
        return <Tag color={meta.color}>{meta.text}</Tag>;
      },
    },
    {
      title: '操作',
      key: 'action',
      width: 240,
      render: (_value, record) => (
        <Space size={4} wrap>
          <Button type="link" size="small" onClick={() => void openDetail(record)}>
            详情
          </Button>
          {record.status === 'REGISTERED' && canEdit ? (
            <Button type="link" size="small" onClick={() => openEdit(record)}>
              编辑
            </Button>
          ) : null}
          {record.status === 'REGISTERED' && canHandle ? (
            <Button type="link" size="small" onClick={() => openReview(record)}>
              批阅
            </Button>
          ) : null}
          {record.status === 'REVIEWED' && canHandle ? (
            <Button type="link" size="small" onClick={() => openForward(record)}>
              转发
            </Button>
          ) : null}
          {record.status === 'FORWARDED' && canHandle ? (
            <Popconfirm
              title="确认办结该收文？"
              onConfirm={() => void runAction(() => completeIncomingDoc(record.id), '收文已办结')}
            >
              <Button type="link" size="small">
                办结
              </Button>
            </Popconfirm>
          ) : null}
          {record.status === 'COMPLETED' && canArchive ? (
            <Popconfirm
              title="确认推送归档？"
              onConfirm={() => void runAction(() => archiveIncomingDoc(record.id), '收文已归档')}
            >
              <Button type="link" size="small">
                归档
              </Button>
            </Popconfirm>
          ) : null}
          {record.status === 'REGISTERED' && canDelete ? (
            <Popconfirm title="确认删除该收文？" onConfirm={() => void handleDelete(record)}>
              <Button type="link" size="small" danger>
                删除
              </Button>
            </Popconfirm>
          ) : null}
        </Space>
      ),
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <Input
          allowClear
          placeholder="收文号/标题/来文单位"
          style={{ width: 220 }}
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
            setStatus((value as OaIncomingStatus) ?? '');
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
            收文登记
          </Button>
        ) : null}
        <Button onClick={() => void load()}>刷新</Button>
      </Space>
      <Table<OaDocIncoming>
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
        title={editing ? '编辑收文' : '收文登记'}
        width={760}
        onOk={() => void handleSave()}
        confirmLoading={saving}
        onCancel={() => setModalOpen(false)}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item
              name="sourceUnit"
              label="来文单位"
              style={{ flex: 1 }}
              rules={[{ required: true, message: '请输入来文单位' }]}
            >
              <Input maxLength={128} />
            </Form.Item>
            <Form.Item name="sourceNo" label="来文文号" style={{ flex: 1 }}>
              <Input maxLength={128} placeholder="选填" />
            </Form.Item>
          </Space>
          <Form.Item
            name="title"
            label="公文标题"
            rules={[{ required: true, message: '请输入公文标题' }]}
          >
            <Input maxLength={255} />
          </Form.Item>
          <Space size="middle" style={{ display: 'flex' }}>
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
            <Form.Item name="receivedDate" label="收文日期" style={{ flex: 1 }}>
              <DatePicker style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="dueDate" label="办理期限" style={{ flex: 1 }}>
              <DatePicker style={{ width: '100%' }} />
            </Form.Item>
          </Space>
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
        open={reviewTarget !== null}
        title="批阅收文"
        onOk={() => void handleReview()}
        confirmLoading={reviewing}
        onCancel={() => setReviewTarget(null)}
        destroyOnClose
      >
        <Form form={reviewForm} layout="vertical">
          <Form.Item
            name="instruction"
            label="批阅意见"
            rules={[{ required: true, message: '请输入批阅意见' }]}
          >
            <Input.TextArea rows={4} maxLength={1024} showCount />
          </Form.Item>
          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item name="assigneeId" label="承办人用户ID" style={{ flex: 1 }}>
              <InputNumber style={{ width: '100%' }} min={1} precision={0} placeholder="选填" />
            </Form.Item>
            <Form.Item name="assigneeName" label="承办人姓名" style={{ flex: 1 }}>
              <Input maxLength={64} placeholder="选填" />
            </Form.Item>
          </Space>
        </Form>
      </Modal>
      <Modal
        open={forwardTarget !== null}
        title="转发收文"
        onOk={() => void handleForward()}
        confirmLoading={forwarding}
        onCancel={() => setForwardTarget(null)}
        destroyOnClose
      >
        <Form form={forwardForm} layout="vertical">
          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item name="assigneeId" label="承办人用户ID" style={{ flex: 1 }}>
              <InputNumber style={{ width: '100%' }} min={1} precision={0} placeholder="选填" />
            </Form.Item>
            <Form.Item name="assigneeName" label="承办人姓名" style={{ flex: 1 }}>
              <Input maxLength={64} placeholder="选填" />
            </Form.Item>
          </Space>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} maxLength={255} showCount placeholder="选填" />
          </Form.Item>
        </Form>
      </Modal>
      <Drawer
        open={detailOpen}
        title={detail ? `收文详情：${detail.incomingNo}` : '收文详情'}
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
                { key: 'incomingNo', label: '收文号', children: detail.incomingNo },
                {
                  key: 'status',
                  label: '状态',
                  children: STATUS_META[detail.status]?.text ?? detail.status,
                },
                { key: 'title', label: '标题', children: detail.title, span: 2 },
                { key: 'sourceUnit', label: '来文单位', children: detail.sourceUnit },
                { key: 'sourceNo', label: '来文文号', children: detail.sourceNo || '-' },
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
                { key: 'receivedDate', label: '收文日期', children: detail.receivedDate || '-' },
                { key: 'dueDate', label: '办理期限', children: detail.dueDate || '-' },
                {
                  key: 'assignee',
                  label: '承办人',
                  children: detail.assigneeName ?? detail.assigneeId ?? '-',
                },
                { key: 'handledAt', label: '办结时间', children: timeText(detail.handledAt) },
                {
                  key: 'archive',
                  label: '档案条目',
                  children: detail.archiveItemId || '-',
                  span: 2,
                },
                {
                  key: 'instruction',
                  label: '批阅意见',
                  children: detail.instruction || '-',
                  span: 2,
                },
                { key: 'remark', label: '备注', children: detail.remark || '-', span: 2 },
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
                    { key: 'status', label: '归档状态', children: detail.archiveRef.status },
                    {
                      key: 'item',
                      label: '档案条目',
                      children: detail.archiveRef.archiveItemId || '-',
                    },
                    { key: 'source', label: '来源ID', children: detail.archiveRef.sourceId || '-' },
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
