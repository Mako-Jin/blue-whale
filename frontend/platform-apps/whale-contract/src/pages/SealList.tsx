// 印章管理：印章台账、新建/编辑、停用、用章授权与授权记录

import { useCallback, useEffect, useState } from 'react';
import {
  App,
  Button,
  DatePicker,
  Descriptions,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { sealApi, type Seal, type SealGrant, type SealGrantInput, type SealSaveInput } from '../api/esign';
import {
  CATEGORY_OPTIONS,
  SEAL_STATUS_OPTIONS,
  SEAL_TYPE_OPTIONS,
  formatDate,
  formatTime,
  hasPermission,
  sealTypeLabel,
} from '../utils';

/** 印章表单值（日期为 dayjs 对象） */
interface SealFormValues {
  name: string;
  sealType?: string;
  imageFileId?: number;
  keeperUserId?: number;
  validFrom?: dayjs.Dayjs;
  validTo?: dayjs.Dayjs;
  status?: string;
}

/** 授权表单值 */
interface GrantFormValues {
  granteeUserId: number;
  contractCategory?: string;
  maxAmount?: number;
  validFrom?: dayjs.Dayjs;
  validTo?: dayjs.Dayjs;
}

/** 把可选日期转为 YYYY-MM-DD */
function toDate(value?: dayjs.Dayjs): string | undefined {
  return value ? value.format('YYYY-MM-DD') : undefined;
}

export default function SealList() {
  const { message } = App.useApp();
  const [queryForm] = Form.useForm();
  const [form] = Form.useForm<SealFormValues>();
  const [grantForm] = Form.useForm<GrantFormValues>();
  const [records, setRecords] = useState<Seal[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [size, setSize] = useState(10);
  const [query, setQuery] = useState<Record<string, unknown>>({});
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<Seal | null>(null);
  const [grantTarget, setGrantTarget] = useState<Seal | null>(null);
  const [grantOpen, setGrantOpen] = useState(false);
  const [grantListOpen, setGrantListOpen] = useState(false);
  const [grants, setGrants] = useState<SealGrant[]>([]);
  const [previewTarget, setPreviewTarget] = useState<Seal | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const result = await sealApi.page({ current, size, ...query });
      setRecords(result.records);
      setTotal(result.total);
    } catch (error) {
      message.error((error as Error).message);
    } finally {
      setLoading(false);
    }
  }, [current, size, query, message]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => () => {
    if (previewUrl) {
      URL.revokeObjectURL(previewUrl);
    }
  }, [previewUrl]);

  const openPreview = async (record: Seal) => {
    try {
      const blob = await sealApi.preview(record.id);
      setPreviewTarget(record);
      setPreviewUrl(URL.createObjectURL(blob));
    } catch (error) {
      message.error((error as Error).message);
    }
  };

  const closePreview = () => {
    setPreviewTarget(null);
    setPreviewUrl(null);
  };

  const downloadOriginal = async (record: Seal) => {
    try {
      const blob = await sealApi.download(record.id);
      const extension = blob.type === 'image/jpeg' ? 'jpg'
        : blob.type === 'image/gif' ? 'gif' : 'png';
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `seal-${record.id}.${extension}`;
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(url);
      message.success('印章原图已开始下载，本次操作已写入审计日志');
    } catch (error) {
      message.error((error as Error).message);
    }
  };

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ sealType: 'CONTRACT', status: 'ENABLED' });
    setModalOpen(true);
  };

  const openEdit = (record: Seal) => {
    setEditing(record);
    form.setFieldsValue({
      name: record.name,
      sealType: record.sealType ?? undefined,
      imageFileId: record.imageFileId ? Number(record.imageFileId) : undefined,
      keeperUserId: record.keeperUserId ? Number(record.keeperUserId) : undefined,
      validFrom: record.validFrom ? dayjs(record.validFrom) : undefined,
      validTo: record.validTo ? dayjs(record.validTo) : undefined,
      status: record.status,
    });
    setModalOpen(true);
  };

  const handleSave = async () => {
    const values = await form.validateFields();
    const payload: SealSaveInput = {
      name: values.name,
      sealType: values.sealType,
      imageFileId: values.imageFileId,
      keeperUserId: values.keeperUserId,
      validFrom: toDate(values.validFrom),
      validTo: toDate(values.validTo),
      status: values.status,
    };
    try {
      if (editing) {
        await sealApi.update(editing.id, payload);
      } else {
        await sealApi.create(payload);
      }
      message.success(editing ? '印章已更新' : '印章已创建');
      setModalOpen(false);
      setEditing(null);
      await load();
    } catch (error) {
      message.error((error as Error).message);
    }
  };

  const handleDelete = async (record: Seal) => {
    try {
      await sealApi.remove(record.id);
      message.success('印章已删除');
      await load();
    } catch (error) {
      message.error((error as Error).message);
    }
  };

  const openGrant = (record: Seal) => {
    setGrantTarget(record);
    grantForm.resetFields();
    grantForm.setFieldsValue({ validFrom: dayjs(), validTo: dayjs().add(1, 'year') });
    setGrantOpen(true);
  };

  const handleGrant = async () => {
    if (!grantTarget) {
      return;
    }
    const values = await grantForm.validateFields();
    const payload: SealGrantInput = {
      granteeUserId: values.granteeUserId,
      contractCategory: values.contractCategory,
      maxAmount: values.maxAmount,
      validFrom: toDate(values.validFrom),
      validTo: toDate(values.validTo),
    };
    try {
      await sealApi.grant(grantTarget.id, payload);
      message.success('用章授权已新增');
      setGrantOpen(false);
      setGrantTarget(null);
    } catch (error) {
      message.error((error as Error).message);
    }
  };

  const openGrantList = async (record: Seal) => {
    setGrantTarget(record);
    setGrantListOpen(true);
    try {
      setGrants(await sealApi.grants(record.id));
    } catch (error) {
      message.error((error as Error).message);
      setGrants([]);
    }
  };

  const columns: ColumnsType<Seal> = [
    { title: '印章名称', dataIndex: 'name', ellipsis: true },
    {
      title: '类型',
      dataIndex: 'sealType',
      width: 130,
      render: (value: string | null) => sealTypeLabel(value),
    },
    { title: '保管人ID', dataIndex: 'keeperUserId', width: 110, render: (value) => value || '-' },
    {
      title: '有效期',
      key: 'validity',
      width: 210,
      render: (_, record) => `${formatDate(record.validFrom)} ~ ${formatDate(record.validTo)}`,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (value: string) => (
        <Tag color={value === 'ENABLED' ? 'green' : 'default'}>
          {value === 'ENABLED' ? '启用' : '停用'}
        </Tag>
      ),
    },
    { title: '创建时间', dataIndex: 'createTime', width: 160, render: (value) => formatTime(value) },
    {
      title: '操作',
      key: 'actions',
      width: 280,
      render: (_, record) => (
        <Space size={0} wrap>
          {hasPermission('sys:contract:seal:list') ? (
            <Button type="link" size="small" onClick={() => void openPreview(record)}>
              预览
            </Button>
          ) : null}
          {hasPermission('sys:contract:seal:download') ? (
            <Button type="link" size="small" onClick={() => void downloadOriginal(record)}>
              下载原图
            </Button>
          ) : null}
          {hasPermission('sys:contract:seal:edit') ? (
            <Button type="link" size="small" onClick={() => openEdit(record)}>
              编辑
            </Button>
          ) : null}
          {hasPermission('sys:contract:seal:grant') ? (
            <Button type="link" size="small" onClick={() => openGrant(record)}>
              授权
            </Button>
          ) : null}
          {hasPermission('sys:contract:seal:list') ? (
            <Button type="link" size="small" onClick={() => void openGrantList(record)}>
              授权记录
            </Button>
          ) : null}
          {hasPermission('sys:contract:seal:delete') ? (
            <Popconfirm title="删除该印章？" onConfirm={() => void handleDelete(record)}>
              <Button type="link" size="small" danger>
                删除
              </Button>
            </Popconfirm>
          ) : null}
        </Space>
      ),
    },
  ];

  const grantColumns: ColumnsType<SealGrant> = [
    { title: '被授权用户ID', dataIndex: 'granteeUserId', width: 130 },
    {
      title: '适用类别',
      dataIndex: 'contractCategory',
      width: 120,
      render: (value: string | null) =>
        value ? CATEGORY_OPTIONS.find((option) => option.value === value)?.label ?? value : '全部',
    },
    {
      title: '金额上限',
      dataIndex: 'maxAmount',
      width: 130,
      align: 'right',
      render: (value: number | null) => (value == null ? '不限' : value.toLocaleString()),
    },
    {
      title: '授权有效期',
      key: 'validity',
      width: 210,
      render: (_, record) => `${formatDate(record.validFrom)} ~ ${formatDate(record.validTo)}`,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (value: string) => (
        <Tag color={value === 'ENABLED' ? 'green' : 'default'}>
          {value === 'ENABLED' ? '有效' : '失效'}
        </Tag>
      ),
    },
    { title: '授权人ID', dataIndex: 'grantedBy', width: 110, render: (value) => value || '-' },
    { title: '授权时间', dataIndex: 'createTime', width: 160, render: (value) => formatTime(value) },
  ];

  return (
    <div style={{ padding: 16 }}>
      <Space direction="vertical" size={12} style={{ width: '100%' }}>
        <Form
          form={queryForm}
          layout="inline"
          onFinish={(values) => {
            setCurrent(1);
            setQuery(values as Record<string, unknown>);
          }}
        >
          <Form.Item name="keyword" label="名称">
            <Input allowClear placeholder="印章名称关键字" style={{ width: 180 }} />
          </Form.Item>
          <Form.Item name="status" label="状态">
            <Select allowClear options={SEAL_STATUS_OPTIONS} style={{ width: 120 }} />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit">
                查询
              </Button>
              <Button
                onClick={() => {
                  queryForm.resetFields();
                  setCurrent(1);
                  setQuery({});
                }}
              >
                重置
              </Button>
            </Space>
          </Form.Item>
          {hasPermission('sys:contract:seal:add') ? (
            <Form.Item>
              <Button type="primary" onClick={openCreate}>
                新建印章
              </Button>
            </Form.Item>
          ) : null}
        </Form>

        <Table
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={records}
          pagination={{
            current,
            pageSize: size,
            total,
            showSizeChanger: true,
            showTotal: (count) => `共 ${count} 条`,
            onChange: (page, pageSize) => {
              setCurrent(page);
              setSize(pageSize);
            },
          }}
        />
      </Space>

      <Modal
        title={editing ? `编辑印章 ${editing.name}` : '新建印章'}
        open={modalOpen}
        width={640}
        onCancel={() => setModalOpen(false)}
        onOk={() => void handleSave()}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="印章名称" rules={[{ required: true, message: '请输入印章名称' }]}>
            <Input maxLength={128} />
          </Form.Item>
          <Space size={12} style={{ width: '100%' }}>
            <Form.Item name="sealType" label="印章类型">
              <Select options={SEAL_TYPE_OPTIONS} style={{ width: 160 }} />
            </Form.Item>
            <Form.Item name="status" label="状态">
              <Select options={SEAL_STATUS_OPTIONS} style={{ width: 120 }} />
            </Form.Item>
          </Space>
          <Space size={12} style={{ width: '100%' }}>
            <Form.Item name="imageFileId" label="印章图片文件ID">
              <InputNumber min={1} style={{ width: 200 }} placeholder="whale-file 文件ID" />
            </Form.Item>
            <Form.Item name="keeperUserId" label="保管人用户ID">
              <InputNumber min={1} style={{ width: 160 }} />
            </Form.Item>
          </Space>
          <Space size={12} style={{ width: '100%' }}>
            <Form.Item name="validFrom" label="有效期起">
              <DatePicker style={{ width: 160 }} />
            </Form.Item>
            <Form.Item
              name="validTo"
              label="有效期止"
              dependencies={['validFrom']}
              rules={[
                ({ getFieldValue }) => ({
                  validator(_, value?: dayjs.Dayjs) {
                    const start = getFieldValue('validFrom') as dayjs.Dayjs | undefined;
                    if (value && start && value.isBefore(start, 'day')) {
                      return Promise.reject(new Error('有效期止不得早于有效期起'));
                    }
                    return Promise.resolve();
                  },
                }),
              ]}
            >
              <DatePicker style={{ width: 160 }} />
            </Form.Item>
          </Space>
        </Form>
      </Modal>

      <Modal
        title={`用章授权${grantTarget ? ` · ${grantTarget.name}` : ''}`}
        open={grantOpen}
        onCancel={() => setGrantOpen(false)}
        onOk={() => void handleGrant()}
        destroyOnClose
      >
        <Form form={grantForm} layout="vertical">
          <Form.Item
            name="granteeUserId"
            label="被授权用户ID"
            rules={[{ required: true, message: '请输入被授权用户ID' }]}
          >
            <InputNumber min={1} style={{ width: 200 }} />
          </Form.Item>
          <Form.Item name="contractCategory" label="适用合同类别（空表示全类别）">
            <Select allowClear options={CATEGORY_OPTIONS} style={{ width: 200 }} />
          </Form.Item>
          <Form.Item name="maxAmount" label="单份合同金额上限（空表示不限）">
            <InputNumber min={0} precision={2} style={{ width: 200 }} />
          </Form.Item>
          <Space size={12} style={{ width: '100%' }}>
            <Form.Item name="validFrom" label="生效日期">
              <DatePicker style={{ width: 160 }} />
            </Form.Item>
            <Form.Item name="validTo" label="失效日期">
              <DatePicker style={{ width: 160 }} />
            </Form.Item>
          </Space>
        </Form>
      </Modal>

      <Modal
        title={`授权记录${grantTarget ? ` · ${grantTarget.name}` : ''}`}
        open={grantListOpen}
        width={900}
        footer={null}
        onCancel={() => setGrantListOpen(false)}
      >
        <Space direction="vertical" size={12} style={{ width: '100%' }}>
          {grantTarget ? (
            <Descriptions size="small" column={3} bordered>
              <Descriptions.Item label="印章">{grantTarget.name}</Descriptions.Item>
              <Descriptions.Item label="类型">{sealTypeLabel(grantTarget.sealType)}</Descriptions.Item>
              <Descriptions.Item label="状态">
                <Tag color={grantTarget.status === 'ENABLED' ? 'green' : 'default'}>
                  {grantTarget.status === 'ENABLED' ? '启用' : '停用'}
                </Tag>
              </Descriptions.Item>
            </Descriptions>
          ) : null}
          <Typography.Text type="secondary">
            仅授权范围内的签署人可在金额/类别限制内使用该印章；未授权用章返回 403 并写入审计日志。
          </Typography.Text>
          <Table rowKey="id" size="small" columns={grantColumns} dataSource={grants} pagination={false} />
        </Space>
      </Modal>

      <Modal
        title={`印章预览${previewTarget ? ` · ${previewTarget.name}` : ''}`}
        open={previewTarget !== null}
        width={360}
        footer={null}
        onCancel={closePreview}
        destroyOnClose
      >
        <Space direction="vertical" size={8} style={{ width: '100%', alignItems: 'center' }}>
          {previewUrl ? (
            <img
              src={previewUrl}
              alt="印章预览"
              style={{ maxWidth: '100%', maxHeight: 240, objectFit: 'contain' }}
            />
          ) : null}
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            预览图为低分辨率并带半透明水印，仅用于识别；原图导出需单独授权并留审计。
          </Typography.Text>
        </Space>
      </Modal>
    </div>
  );
}
