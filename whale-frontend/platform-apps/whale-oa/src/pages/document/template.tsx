// OA 公文 - 模板管理：红头、版记与正文模板维护
import { useCallback, useEffect, useState } from 'react';
import {
  App,
  Button,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
} from 'antd';
import type { TableProps } from 'antd';
import {
  createDocumentTemplate,
  deleteDocumentTemplate,
  pageDocumentTemplates,
  updateDocumentTemplate,
  type OaDocTemplate,
  type OaDocTemplateInput,
  type OaDocType,
} from '../../api/document';
import { hasPermission } from '../../utils/permission';

/** 模板状态文案 */
const STATUS_META: Record<string, { text: string; color: string }> = {
  DRAFT: { text: '草稿', color: 'default' },
  RELEASED: { text: '已发布', color: 'success' },
  DISABLED: { text: '已停用', color: 'error' },
};

/** 适用范围选项 */
const DOC_TYPE_OPTIONS = [
  { value: 'OUTGOING', label: '发文' },
  { value: 'INCOMING', label: '收文' },
];

/** 模板表单值 */
interface TemplateFormValues {
  code: string;
  name: string;
  docType: OaDocType;
  version: number;
  status: string;
  headerText?: string;
  footerText?: string;
  bodyTemplate?: string;
  remark?: string;
}

/** 时间展示 */
function timeText(value?: string): string {
  return value ? value.replace('T', ' ').slice(0, 16) : '-';
}

/** 适用范围展示 */
function docTypeText(value?: string): string {
  return DOC_TYPE_OPTIONS.find((item) => item.value === value)?.label ?? value ?? '-';
}

export default function DocumentTemplatePage() {
  const { message } = App.useApp();
  const [data, setData] = useState<OaDocTemplate[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [keyword, setKeyword] = useState('');
  const [docType, setDocType] = useState<OaDocType | ''>('');
  const [status, setStatus] = useState('');
  const [loading, setLoading] = useState(false);

  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<OaDocTemplate | null>(null);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm<TemplateFormValues>();

  const canManage = hasPermission('sys:oa:document:template');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageDocumentTemplates({
        current,
        size: 10,
        keyword: keyword.trim() || undefined,
        docType: docType || undefined,
        status: status || undefined,
      });
      setData(page.records);
      setTotal(page.total);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '模板查询失败');
      setData([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, [current, keyword, docType, status, message]);

  useEffect(() => {
    void load();
  }, [load]);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ docType: 'OUTGOING', version: 1, status: 'DRAFT' });
    setModalOpen(true);
  };

  const openEdit = (record: OaDocTemplate) => {
    setEditing(record);
    form.resetFields();
    form.setFieldsValue({
      code: record.code,
      name: record.name,
      docType: record.docType,
      version: record.version,
      status: record.status,
      headerText: record.headerText,
      footerText: record.footerText,
      bodyTemplate: record.bodyTemplate,
      remark: record.remark,
    });
    setModalOpen(true);
  };

  const handleSave = async () => {
    const values = await form.validateFields();
    const payload: OaDocTemplateInput = {
      code: values.code.trim(),
      name: values.name.trim(),
      docType: values.docType,
      version: values.version,
      status: values.status,
      headerText: values.headerText?.trim() || undefined,
      footerText: values.footerText?.trim() || undefined,
      bodyTemplate: values.bodyTemplate,
      remark: values.remark?.trim() || undefined,
    };
    setSaving(true);
    try {
      if (editing) {
        await updateDocumentTemplate(editing.id, payload);
        message.success('模板已更新');
      } else {
        await createDocumentTemplate(payload);
        message.success('模板已创建');
      }
      setModalOpen(false);
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '模板保存失败');
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (record: OaDocTemplate) => {
    try {
      await deleteDocumentTemplate(record.id);
      message.success('模板已删除');
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '模板删除失败');
    }
  };

  const columns: TableProps<OaDocTemplate>['columns'] = [
    { title: '模板编码', dataIndex: 'code', key: 'code', width: 150 },
    { title: '模板名称', dataIndex: 'name', key: 'name', ellipsis: true },
    {
      title: '适用范围',
      dataIndex: 'docType',
      key: 'docType',
      width: 100,
      render: (value?: string) => docTypeText(value),
    },
    { title: '版本', dataIndex: 'version', key: 'version', width: 80 },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (value: string) => {
        const meta = STATUS_META[value] ?? { text: value, color: 'default' };
        return <Tag color={meta.color}>{meta.text}</Tag>;
      },
    },
    {
      title: '红头',
      dataIndex: 'headerText',
      key: 'headerText',
      width: 180,
      ellipsis: true,
      render: (value?: string) => value || '-',
    },
    {
      title: '更新时间',
      dataIndex: 'updateTime',
      key: 'updateTime',
      width: 160,
      render: (value?: string) => timeText(value),
    },
    {
      title: '操作',
      key: 'action',
      width: 140,
      render: (_value, record) =>
        canManage ? (
          <Space size={4}>
            <Button type="link" size="small" onClick={() => openEdit(record)}>
              编辑
            </Button>
            <Popconfirm title="确认删除该模板？" onConfirm={() => void handleDelete(record)}>
              <Button type="link" size="small" danger>
                删除
              </Button>
            </Popconfirm>
          </Space>
        ) : null,
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <Input
          allowClear
          placeholder="编码/名称"
          style={{ width: 200 }}
          value={keyword}
          onChange={(event) => {
            setKeyword(event.target.value);
            setCurrent(1);
          }}
        />
        <Select
          allowClear
          placeholder="适用范围"
          style={{ width: 120 }}
          value={docType || undefined}
          options={DOC_TYPE_OPTIONS}
          onChange={(value) => {
            setDocType((value as OaDocType) ?? '');
            setCurrent(1);
          }}
        />
        <Select
          allowClear
          placeholder="状态"
          style={{ width: 120 }}
          value={status || undefined}
          options={Object.entries(STATUS_META).map(([value, meta]) => ({
            label: meta.text,
            value,
          }))}
          onChange={(value) => {
            setStatus(value ?? '');
            setCurrent(1);
          }}
        />
        {canManage ? (
          <Button type="primary" onClick={openCreate}>
            新建模板
          </Button>
        ) : null}
        <Button onClick={() => void load()}>刷新</Button>
      </Space>
      <Table<OaDocTemplate>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={data}
        scroll={{ x: 1100 }}
        pagination={{
          current,
          pageSize: 10,
          total,
          onChange: (page) => setCurrent(page),
        }}
      />
      <Modal
        open={modalOpen}
        title={editing ? '编辑公文模板' : '新建公文模板'}
        width={720}
        onOk={() => void handleSave()}
        confirmLoading={saving}
        onCancel={() => setModalOpen(false)}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item
              name="code"
              label="模板编码"
              style={{ flex: 1 }}
              rules={[{ required: true, message: '请输入模板编码' }]}
            >
              <Input maxLength={64} />
            </Form.Item>
            <Form.Item
              name="name"
              label="模板名称"
              style={{ flex: 1 }}
              rules={[{ required: true, message: '请输入模板名称' }]}
            >
              <Input maxLength={128} />
            </Form.Item>
          </Space>
          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item
              name="docType"
              label="适用范围"
              style={{ flex: 1 }}
              rules={[{ required: true, message: '请选择适用范围' }]}
            >
              <Select options={DOC_TYPE_OPTIONS} />
            </Form.Item>
            <Form.Item name="version" label="版本号" style={{ flex: 1 }}>
              <InputNumber style={{ width: '100%' }} min={1} precision={0} />
            </Form.Item>
            <Form.Item name="status" label="状态" style={{ flex: 1 }}>
              <Select
                options={Object.entries(STATUS_META).map(([value, meta]) => ({
                  label: meta.text,
                  value,
                }))}
              />
            </Form.Item>
          </Space>
          <Form.Item name="headerText" label="红头文字">
            <Input maxLength={512} placeholder="选填" />
          </Form.Item>
          <Form.Item name="footerText" label="版记文字">
            <Input maxLength={512} placeholder="选填" />
          </Form.Item>
          <Form.Item name="bodyTemplate" label="正文模板">
            <Input.TextArea rows={6} placeholder="选填，支持占位符" />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} maxLength={255} showCount placeholder="选填" />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}
