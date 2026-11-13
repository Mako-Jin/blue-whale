// OA 采购申请页：申请编辑（明细）、撤销、删除与 ERP 采购订单重试推送
import { useCallback, useEffect, useState } from 'react';
import {
  App,
  Button,
  DatePicker,
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
import dayjs from 'dayjs';
import type { Dayjs } from 'dayjs';
import {
  createPurchase,
  deletePurchase,
  pagePurchases,
  retryPurchasePush,
  updatePurchase,
  updatePurchaseStatus,
  type OaAdministrativeStatus,
  type OaPurchaseInput,
  type OaPurchasePushStatus,
  type OaPurchaseRequest,
} from '../../api/administrative';
import { getCurrentUser } from '../../api/client';
import { hasPermission } from '../../utils/permission';

/** 审批状态文案 */
const STATUS_META: Record<OaAdministrativeStatus, { text: string; color: string }> = {
  PENDING: { text: '待审批', color: 'processing' },
  APPROVED: { text: '已通过', color: 'success' },
  REJECTED: { text: '已驳回', color: 'error' },
  CANCELLED: { text: '已撤销', color: 'default' },
};

/** ERP 推送状态文案 */
const PUSH_META: Record<OaPurchasePushStatus, { text: string; color: string }> = {
  NONE: { text: '未推送', color: 'default' },
  PENDING: { text: '待推送', color: 'processing' },
  SENT: { text: '已推送', color: 'success' },
  FAILED: { text: '推送失败', color: 'error' },
};

/** 采购单表单值 */
interface PurchaseFormValues {
  applicant: string;
  applicantId?: number;
  deptId?: number;
  reason: string;
  expectedDate?: Dayjs;
  supplierId?: number;
  warehouseId?: number;
  currency?: string;
  remark?: string;
  items: {
    itemCode: string;
    itemName?: string;
    quantity: number;
    unitPrice: number;
    supplierAdvice?: string;
  }[];
}

export default function PurchaseAdminPage() {
  const { message } = App.useApp();
  const user = getCurrentUser();
  const [data, setData] = useState<OaPurchaseRequest[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<OaAdministrativeStatus | ''>('');
  const [pushStatus, setPushStatus] = useState<OaPurchasePushStatus | ''>('');
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<OaPurchaseRequest | null>(null);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm<PurchaseFormValues>();

  const canAdd = hasPermission('sys:oa:purchase:add');
  const canEdit = hasPermission('sys:oa:purchase:edit');
  const canDelete = hasPermission('sys:oa:purchase:delete');
  const canPush = hasPermission('sys:oa:purchase:push');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pagePurchases({
        current,
        size: 10,
        keyword: keyword.trim() || undefined,
        status,
        erpPushStatus: pushStatus,
      });
      setData(page.records);
      setTotal(page.total);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '采购申请查询失败');
      setData([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, [current, keyword, status, pushStatus, message]);

  useEffect(() => {
    void load();
  }, [load]);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({
      applicant: user?.nickname ?? user?.username ?? '',
      applicantId: typeof user?.userId === 'number' ? user.userId : undefined,
      currency: 'CNY',
      items: [{ itemCode: '', quantity: 1, unitPrice: 0 }],
    });
    setModalOpen(true);
  };

  const openEdit = (record: OaPurchaseRequest) => {
    setEditing(record);
    form.resetFields();
    form.setFieldsValue({
      applicant: record.applicant,
      applicantId: record.applicantId,
      deptId: record.deptId,
      reason: record.reason,
      expectedDate: record.expectedDate ? dayjs(record.expectedDate) : undefined,
      supplierId: record.supplierId,
      warehouseId: record.warehouseId,
      currency: record.currency,
      remark: record.remark,
      items: (record.items ?? []).map((item) => ({
        itemCode: item.itemCode,
        itemName: item.itemName,
        quantity: Number(item.quantity),
        unitPrice: Number(item.unitPrice),
        supplierAdvice: item.supplierAdvice,
      })),
    });
    setModalOpen(true);
  };

  const handleSave = async () => {
    const values = await form.validateFields();
    const payload: OaPurchaseInput = {
      applicant: values.applicant.trim(),
      applicantId: values.applicantId,
      deptId: values.deptId,
      reason: values.reason.trim(),
      expectedDate: values.expectedDate?.format('YYYY-MM-DD'),
      supplierId: values.supplierId,
      warehouseId: values.warehouseId,
      currency: values.currency?.trim() || 'CNY',
      remark: values.remark?.trim() || undefined,
      items: values.items.map((item) => ({
        itemCode: item.itemCode.trim(),
        itemName: item.itemName?.trim() || undefined,
        quantity: item.quantity,
        unitPrice: item.unitPrice,
        supplierAdvice: item.supplierAdvice?.trim() || undefined,
      })),
    };
    setSaving(true);
    try {
      if (editing) {
        await updatePurchase(editing.id, payload);
        message.success('采购申请已更新');
      } else {
        await createPurchase(payload);
        message.success('采购申请已创建并提交审批');
      }
      setModalOpen(false);
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '采购申请保存失败');
    } finally {
      setSaving(false);
    }
  };

  const handleCancel = async (record: OaPurchaseRequest) => {
    try {
      await updatePurchaseStatus(record.id, 'CANCELLED');
      message.success('采购申请已撤销');
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '采购申请撤销失败');
    }
  };

  const handleDelete = async (record: OaPurchaseRequest) => {
    try {
      await deletePurchase(record.id);
      message.success('采购申请已删除');
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '采购申请删除失败');
    }
  };

  const handlePush = async (record: OaPurchaseRequest) => {
    try {
      await retryPurchasePush(record.id);
      message.success('ERP 采购订单推送成功');
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : 'ERP 采购订单推送失败');
    }
  };

  const columns: TableProps<OaPurchaseRequest>['columns'] = [
    { title: '采购单号', dataIndex: 'code', key: 'code', width: 170 },
    { title: '申请人', dataIndex: 'applicant', key: 'applicant', width: 110 },
    { title: '采购事由', dataIndex: 'reason', key: 'reason', ellipsis: true },
    {
      title: '预计到货',
      dataIndex: 'expectedDate',
      key: 'expectedDate',
      width: 120,
      render: (value?: string) => value ?? '-',
    },
    {
      title: '合计',
      dataIndex: 'totalAmount',
      key: 'totalAmount',
      width: 120,
      align: 'right',
      render: (value?: number) => Number(value ?? 0).toFixed(2),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (value: OaAdministrativeStatus) => {
        const meta = STATUS_META[value] ?? { text: value, color: 'default' };
        return <Tag color={meta.color}>{meta.text}</Tag>;
      },
    },
    {
      title: 'ERP 推送',
      dataIndex: 'erpPushStatus',
      key: 'erpPushStatus',
      width: 110,
      render: (value: OaPurchasePushStatus) => {
        const meta = PUSH_META[value] ?? { text: value, color: 'default' };
        return <Tag color={meta.color}>{meta.text}</Tag>;
      },
    },
    { title: 'ERP 订单号', dataIndex: 'erpPoNo', key: 'erpPoNo', width: 170 },
    {
      title: '操作',
      key: 'action',
      width: 230,
      render: (_value, record) => (
        <Space size={4}>
          {record.status === 'PENDING' && canEdit ? (
            <Button type="link" size="small" onClick={() => openEdit(record)}>
              编辑
            </Button>
          ) : null}
          {record.status === 'PENDING' && canEdit ? (
            <Popconfirm title="确认撤销该采购申请？" onConfirm={() => void handleCancel(record)}>
              <Button type="link" size="small">
                撤销
              </Button>
            </Popconfirm>
          ) : null}
          {record.status === 'PENDING' && canDelete ? (
            <Popconfirm title="确认删除该采购申请？" onConfirm={() => void handleDelete(record)}>
              <Button type="link" size="small" danger>
                删除
              </Button>
            </Popconfirm>
          ) : null}
          {record.status === 'APPROVED' && record.erpPushStatus !== 'SENT' && canPush ? (
            <Button type="link" size="small" onClick={() => void handlePush(record)}>
              重试推送
            </Button>
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
          placeholder="单号/申请人"
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
            setStatus((value as OaAdministrativeStatus) ?? '');
            setCurrent(1);
          }}
        />
        <Select
          allowClear
          placeholder="ERP 推送"
          style={{ width: 130 }}
          value={pushStatus || undefined}
          options={Object.entries(PUSH_META).map(([value, meta]) => ({ label: meta.text, value }))}
          onChange={(value) => {
            setPushStatus((value as OaPurchasePushStatus) ?? '');
            setCurrent(1);
          }}
        />
        {canAdd ? (
          <Button type="primary" onClick={openCreate}>
            新建采购申请
          </Button>
        ) : null}
        <Button onClick={() => void load()}>刷新</Button>
      </Space>
      <Table<OaPurchaseRequest>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={data}
        scroll={{ x: 1400 }}
        pagination={{
          current,
          pageSize: 10,
          total,
          onChange: (page) => setCurrent(page),
        }}
      />
      <Modal
        open={modalOpen}
        title={editing ? '编辑采购申请' : '新建采购申请'}
        width={900}
        onOk={() => void handleSave()}
        confirmLoading={saving}
        onCancel={() => setModalOpen(false)}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item
              name="applicant"
              label="申请人"
              rules={[{ required: true, message: '请输入申请人' }]}
              style={{ flex: 1 }}
            >
              <Input maxLength={64} />
            </Form.Item>
            <Form.Item name="applicantId" label="申请人ID" style={{ flex: 1 }}>
              <InputNumber style={{ width: '100%' }} min={1} precision={0} />
            </Form.Item>
            <Form.Item name="deptId" label="部门ID" style={{ flex: 1 }}>
              <InputNumber style={{ width: '100%' }} min={1} precision={0} />
            </Form.Item>
          </Space>
          <Form.Item
            name="reason"
            label="采购事由"
            rules={[{ required: true, message: '请输入采购事由' }]}
          >
            <Input.TextArea rows={2} maxLength={512} showCount />
          </Form.Item>
          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item name="expectedDate" label="预计到货日期" style={{ flex: 1 }}>
              <DatePicker style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item
              name="supplierId"
              label="供应商ID"
              style={{ flex: 1 }}
              tooltip="推送 ERP 时必填，缺失将标记推送失败"
            >
              <InputNumber style={{ width: '100%' }} min={1} precision={0} />
            </Form.Item>
            <Form.Item
              name="warehouseId"
              label="收货仓库ID"
              style={{ flex: 1 }}
              tooltip="推送 ERP 时必填，缺失将标记推送失败"
            >
              <InputNumber style={{ width: '100%' }} min={1} precision={0} />
            </Form.Item>
            <Form.Item name="currency" label="币种" style={{ flex: 1 }}>
              <Input maxLength={8} />
            </Form.Item>
          </Space>
          <Form.List name="items">
            {(fields, { add, remove }) => (
              <Space direction="vertical" size="small" style={{ width: '100%' }}>
                <Space>
                  <span>采购明细（合计由服务端重算）</span>
                  <Button
                    size="small"
                    onClick={() => add({ itemCode: '', quantity: 1, unitPrice: 0 })}
                  >
                    添加明细
                  </Button>
                </Space>
                {fields.map((field) => (
                  <Space key={field.key} size="small" align="baseline" wrap>
                    <Form.Item
                      name={[field.name, 'itemCode']}
                      rules={[{ required: true, message: '物料编码必填' }]}
                    >
                      <Input style={{ width: 160 }} placeholder="物料编码" maxLength={64} />
                    </Form.Item>
                    <Form.Item name={[field.name, 'itemName']}>
                      <Input style={{ width: 200 }} placeholder="物料名称" maxLength={128} />
                    </Form.Item>
                    <Form.Item
                      name={[field.name, 'quantity']}
                      rules={[{ required: true, message: '数量必填' }]}
                    >
                      <InputNumber
                        style={{ width: 110 }}
                        min={0.001}
                        precision={3}
                        placeholder="数量"
                      />
                    </Form.Item>
                    <Form.Item
                      name={[field.name, 'unitPrice']}
                      rules={[{ required: true, message: '单价必填' }]}
                    >
                      <InputNumber
                        style={{ width: 120 }}
                        min={0}
                        precision={2}
                        placeholder="单价"
                      />
                    </Form.Item>
                    <Form.Item name={[field.name, 'supplierAdvice']}>
                      <Input style={{ width: 180 }} placeholder="供应商建议" maxLength={128} />
                    </Form.Item>
                    <Button type="link" size="small" danger onClick={() => remove(field.name)}>
                      移除
                    </Button>
                  </Space>
                ))}
              </Space>
            )}
          </Form.List>
          <Form.Item name="remark" label="备注" style={{ marginTop: 12 }}>
            <Input.TextArea rows={2} maxLength={255} showCount />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}
