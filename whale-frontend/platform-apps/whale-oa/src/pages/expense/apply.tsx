// OA 报销申请页：报销单编辑（明细/发票）+ 提交审批 + 删除
import { useCallback, useEffect, useState } from 'react';
import {
  Alert,
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
  createExpense,
  deleteExpense,
  getExpense,
  listCategories,
  pageExpenses,
  submitExpense,
  updateExpense,
  type OaExpense,
  type OaExpenseCategory,
  type OaExpenseInput,
  type OaExpenseStatus,
} from '../../api/expense';
import { pageTrips, type OaTrip } from '../../api/trip';
import { getCurrentUser } from '../../api/client';
import { hasPermission } from '../../utils/permission';

/** 报销状态文案 */
const STATUS_META: Record<OaExpenseStatus, { text: string; color: string }> = {
  PENDING: { text: '待审批', color: 'processing' },
  APPROVED: { text: '已通过', color: 'success' },
  REJECTED: { text: '已驳回', color: 'error' },
  CANCELLED: { text: '已撤销', color: 'default' },
};

/** 报销单表单值 */
interface ExpenseFormValues {
  applicant: string;
  applicantId?: number;
  deptId?: number;
  categoryCode: string;
  title: string;
  currency?: string;
  tripId?: number | null;
  remark?: string;
  items: {
    categoryCode: string;
    expenseDate: Dayjs;
    amount: number;
    taxAmount?: number;
    description?: string;
  }[];
  invoices: {
    invoiceCode?: string;
    invoiceNo: string;
    invoiceDate?: Dayjs;
    amount: number;
    taxAmount?: number;
  }[];
}

export default function ExpenseApplyPage() {
  const { message } = App.useApp();
  const user = getCurrentUser();
  const [data, setData] = useState<OaExpense[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [size, setSize] = useState(10);
  const [loading, setLoading] = useState(false);
  const [code, setCode] = useState('');
  const [status, setStatus] = useState<OaExpenseStatus | ''>('');
  const [categories, setCategories] = useState<OaExpenseCategory[]>([]);
  const [trips, setTrips] = useState<OaTrip[]>([]);
  const [editTrip, setEditTrip] = useState<{ id: number; budget?: number | null } | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<OaExpense | null>(null);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm<ExpenseFormValues>();

  const canAdd = hasPermission('sys:oa:expense:add');
  const canEdit = hasPermission('sys:oa:expense:edit');
  const canDelete = hasPermission('sys:oa:expense:delete');
  const canSubmit = hasPermission('sys:oa:expense:submit');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageExpenses({ current, size, code: code.trim() || undefined, status });
      setData(page.records);
      setTotal(page.total);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '报销单查询失败');
      setData([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, [current, size, code, status, message]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    listCategories()
      .then(setCategories)
      .catch(() => setCategories([]));
  }, []);

  useEffect(() => {
    pageTrips({ current: 1, size: 200, status: 'APPROVED' })
      .then((page) => setTrips(page.records))
      .catch(() => setTrips([]));
  }, []);

  const categoryOptions = categories
    .filter((item) => item.enabled !== false)
    .map((item) => ({ label: `${item.name}（${item.code}）`, value: item.code }));

  const tripIdValue = Form.useWatch('tripId', form);
  const itemsValue = Form.useWatch('items', form);

  const tripOptions = trips.map((item) => ({
    label: `${item.code} ${item.destination}（${item.startDate} ~ ${item.endDate}）`,
    value: item.id,
  }));
  if (tripIdValue != null && !trips.some((item) => item.id === tripIdValue)) {
    tripOptions.push({ label: `出差单 #${tripIdValue}`, value: tripIdValue });
  }

  const selectedTrip = trips.find((item) => item.id === tripIdValue);
  const tripBudget =
    selectedTrip?.budgetAmount ??
    (editTrip && editTrip.id === tripIdValue ? editTrip.budget : undefined);
  const totalAmount = (itemsValue ?? []).reduce(
    (sum, item) => sum + (Number(item?.amount) || 0),
    0,
  );
  const overBudget =
    tripIdValue != null && tripBudget != null && totalAmount > Number(tripBudget);

  const openCreate = () => {
    setEditing(null);
    setEditTrip(null);
    form.resetFields();
    form.setFieldsValue({
      applicant: user?.nickname ?? user?.username ?? '',
      applicantId: typeof user?.userId === 'number' ? user.userId : undefined,
      currency: 'CNY',
      tripId: null,
      items: [
        {
          categoryCode: undefined as unknown as string,
          expenseDate: dayjs(),
          amount: 0,
          taxAmount: 0,
        },
      ],
      invoices: [],
    });
    setModalOpen(true);
  };

  const openEdit = async (record: OaExpense) => {
    setEditing(record);
    form.resetFields();
    try {
      const detail = await getExpense(record.id);
      form.setFieldsValue({
        applicant: detail.applicant,
        applicantId: detail.applicantId,
        deptId: detail.deptId,
        categoryCode: detail.categoryCode,
        title: detail.title,
        currency: detail.currency,
        tripId: detail.tripId ?? null,
        remark: detail.remark,
        items: (detail.items ?? []).map((item) => ({
          categoryCode: item.categoryCode,
          expenseDate: dayjs(item.expenseDate),
          amount: Number(item.amount),
          taxAmount: Number(item.taxAmount ?? 0),
          description: item.description,
        })),
        invoices: (detail.invoices ?? []).map((invoice) => ({
          invoiceCode: invoice.invoiceCode,
          invoiceNo: invoice.invoiceNo,
          invoiceDate: invoice.invoiceDate ? dayjs(invoice.invoiceDate) : undefined,
          amount: Number(invoice.amount),
          taxAmount: Number(invoice.taxAmount ?? 0),
        })),
      });
      setEditTrip(
        detail.tripId != null
          ? { id: detail.tripId, budget: detail.tripBudgetAmount ?? undefined }
          : null,
      );
      setModalOpen(true);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '报销单详情加载失败');
    }
  };

  const handleSave = async () => {
    const values = await form.validateFields();
    const payload: OaExpenseInput = {
      applicant: values.applicant.trim(),
      applicantId: values.applicantId,
      deptId: values.deptId,
      categoryCode: values.categoryCode,
      title: values.title.trim(),
      currency: values.currency?.trim() || 'CNY',
      tripId: values.tripId ?? null,
      remark: values.remark?.trim() || undefined,
      items: values.items.map((item) => ({
        categoryCode: item.categoryCode,
        expenseDate: item.expenseDate.format('YYYY-MM-DD'),
        amount: item.amount,
        taxAmount: item.taxAmount ?? 0,
        description: item.description?.trim() || undefined,
      })),
      invoices: (values.invoices ?? []).map((invoice) => ({
        invoiceCode: invoice.invoiceCode?.trim() || undefined,
        invoiceNo: invoice.invoiceNo.trim(),
        invoiceDate: invoice.invoiceDate?.format('YYYY-MM-DD'),
        amount: invoice.amount,
        taxAmount: invoice.taxAmount ?? 0,
      })),
    };
    setSaving(true);
    try {
      if (editing) {
        await updateExpense(editing.id, payload);
        message.success('报销单已更新');
      } else {
        await createExpense(payload);
        message.success('报销单已创建并提交审批');
      }
      setModalOpen(false);
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '报销单保存失败');
    } finally {
      setSaving(false);
    }
  };

  const handleSubmit = async (record: OaExpense) => {
    try {
      await submitExpense(record.id);
      message.success('报销单已提交审批');
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '报销单提交失败');
    }
  };

  const handleDelete = async (record: OaExpense) => {
    try {
      await deleteExpense(record.id);
      message.success('报销单已删除');
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '报销单删除失败');
    }
  };

  const columns: TableProps<OaExpense>['columns'] = [
    { title: '单号', dataIndex: 'code', key: 'code', width: 170 },
    { title: '申请人', dataIndex: 'applicant', key: 'applicant', width: 110 },
    { title: '事由', dataIndex: 'title', key: 'title', ellipsis: true },
    {
      title: '类别',
      dataIndex: 'categoryCode',
      key: 'categoryCode',
      width: 130,
      render: (value: string) => categories.find((item) => item.code === value)?.name ?? value,
    },
    {
      title: '合计',
      dataIndex: 'totalAmount',
      key: 'totalAmount',
      width: 120,
      align: 'right',
      render: (value: number) => Number(value ?? 0).toFixed(2),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (value: OaExpenseStatus) => {
        const meta = STATUS_META[value] ?? { text: value, color: 'default' };
        return <Tag color={meta.color}>{meta.text}</Tag>;
      },
    },
    { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 180 },
    {
      title: '操作',
      key: 'action',
      width: 210,
      render: (_value, record) => (
        <Space size={4}>
          {record.status === 'PENDING' && canEdit ? (
            <Button type="link" size="small" onClick={() => void openEdit(record)}>
              编辑
            </Button>
          ) : null}
          {record.status === 'PENDING' && canSubmit ? (
            <Button type="link" size="small" onClick={() => void handleSubmit(record)}>
              提交审批
            </Button>
          ) : null}
          {record.status === 'PENDING' && canDelete ? (
            <Popconfirm title="确认删除该报销单？" onConfirm={() => void handleDelete(record)}>
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
          placeholder="单号"
          style={{ width: 200 }}
          value={code}
          onChange={(event) => {
            setCode(event.target.value);
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
            setStatus((value as OaExpenseStatus) ?? '');
            setCurrent(1);
          }}
        />
        {canAdd ? (
          <Button type="primary" onClick={openCreate}>
            新建报销单
          </Button>
        ) : null}
        <Button onClick={() => void load()}>刷新</Button>
      </Space>
      <Table<OaExpense>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={data}
        scroll={{ x: 1200 }}
        pagination={{
          current,
          pageSize: size,
          total,
          showSizeChanger: true,
          onChange: (page, pageSize) => {
            setCurrent(page);
            setSize(pageSize);
          },
        }}
      />
      <Modal
        open={modalOpen}
        title={editing ? '编辑报销单' : '新建报销单'}
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
          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item
              name="categoryCode"
              label="费用类别"
              rules={[{ required: true, message: '请选择费用类别' }]}
              style={{ flex: 1 }}
            >
              <Select options={categoryOptions} placeholder="请选择" />
            </Form.Item>
            <Form.Item
              name="title"
              label="报销事由"
              rules={[{ required: true, message: '请输入报销事由' }]}
              style={{ flex: 2 }}
            >
              <Input maxLength={128} />
            </Form.Item>
            <Form.Item name="currency" label="币种" style={{ flex: 1 }}>
              <Input maxLength={8} />
            </Form.Item>
          </Space>
          <Space size="middle" align="baseline" style={{ display: 'flex' }}>
            <Form.Item name="tripId" label="关联出差单" style={{ flex: 1 }}>
              <Select
                allowClear
                showSearch
                optionFilterProp="label"
                placeholder="可不选，仅展示已通过的出差单"
                options={tripOptions}
              />
            </Form.Item>
            {tripIdValue != null && tripBudget != null ? (
              <span>出差预算：{Number(tripBudget).toFixed(2)} 元</span>
            ) : null}
          </Space>
          {overBudget ? (
            <Alert
              type="warning"
              showIcon
              message={`已超出出差预算（当前合计 ${totalAmount.toFixed(2)} 元）`}
              style={{ marginBottom: 16 }}
            />
          ) : null}
          <Form.List name="items">
            {(fields, { add, remove }) => (
              <Space direction="vertical" size="small" style={{ width: '100%' }}>
                <Space>
                  <span>费用明细（合计由服务端重算）</span>
                  <Button
                    size="small"
                    onClick={() => add({ expenseDate: dayjs(), amount: 0, taxAmount: 0 })}
                  >
                    添加明细
                  </Button>
                </Space>
                {fields.map((field) => (
                  <Space key={field.key} size="small" align="baseline" wrap>
                    <Form.Item
                      name={[field.name, 'categoryCode']}
                      rules={[{ required: true, message: '类别必填' }]}
                    >
                      <Select style={{ width: 200 }} options={categoryOptions} placeholder="类别" />
                    </Form.Item>
                    <Form.Item
                      name={[field.name, 'expenseDate']}
                      rules={[{ required: true, message: '日期必填' }]}
                    >
                      <DatePicker placeholder="日期" />
                    </Form.Item>
                    <Form.Item
                      name={[field.name, 'amount']}
                      rules={[{ required: true, message: '金额必填' }]}
                    >
                      <InputNumber
                        style={{ width: 130 }}
                        min={0}
                        precision={2}
                        placeholder="金额"
                      />
                    </Form.Item>
                    <Form.Item name={[field.name, 'taxAmount']}>
                      <InputNumber
                        style={{ width: 120 }}
                        min={0}
                        precision={2}
                        placeholder="税额"
                      />
                    </Form.Item>
                    <Form.Item name={[field.name, 'description']}>
                      <Input style={{ width: 220 }} placeholder="说明" maxLength={255} />
                    </Form.Item>
                    <Button type="link" size="small" danger onClick={() => remove(field.name)}>
                      移除
                    </Button>
                  </Space>
                ))}
              </Space>
            )}
          </Form.List>
          <Form.List name="invoices">
            {(fields, { add, remove }) => (
              <Space direction="vertical" size="small" style={{ width: '100%', marginTop: 16 }}>
                <Space>
                  <span>发票（同单内发票号不可重复）</span>
                  <Button size="small" onClick={() => add({ amount: 0, taxAmount: 0 })}>
                    添加发票
                  </Button>
                </Space>
                {fields.map((field) => (
                  <Space key={field.key} size="small" align="baseline" wrap>
                    <Form.Item
                      name={[field.name, 'invoiceNo']}
                      rules={[{ required: true, message: '发票号必填' }]}
                    >
                      <Input style={{ width: 170 }} placeholder="发票号" maxLength={64} />
                    </Form.Item>
                    <Form.Item name={[field.name, 'invoiceCode']}>
                      <Input style={{ width: 130 }} placeholder="发票代码" maxLength={64} />
                    </Form.Item>
                    <Form.Item name={[field.name, 'invoiceDate']}>
                      <DatePicker placeholder="开票日期" />
                    </Form.Item>
                    <Form.Item name={[field.name, 'amount']}>
                      <InputNumber
                        style={{ width: 120 }}
                        min={0}
                        precision={2}
                        placeholder="金额"
                      />
                    </Form.Item>
                    <Form.Item name={[field.name, 'taxAmount']}>
                      <InputNumber
                        style={{ width: 110 }}
                        min={0}
                        precision={2}
                        placeholder="税额"
                      />
                    </Form.Item>
                    <Button type="link" size="small" danger onClick={() => remove(field.name)}>
                      移除
                    </Button>
                  </Space>
                ))}
              </Space>
            )}
          </Form.List>
          <Form.Item name="remark" label="备注" style={{ marginTop: 16 }}>
            <Input.TextArea rows={2} maxLength={255} showCount />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}
