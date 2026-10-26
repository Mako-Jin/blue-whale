// 合同列表：分页检索、新建/编辑、提交审批与状态流转

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
  Switch,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { useNavigate } from 'react-router-dom';
import { contractApi, type Contract, type ContractParty, type ContractSaveInput } from '../api/contract';
import {
  CATEGORY_OPTIONS,
  STATUS_OPTIONS,
  categoryLabel,
  formatDate,
  hasPermission,
  statusColor,
  statusLabel,
} from '../utils';

/** 表单值（日期为 dayjs 对象，提交时转换） */
interface ContractFormValues {
  title: string;
  category: string;
  amount?: number;
  currency?: string;
  effectiveDate?: dayjs.Dayjs;
  expireDate?: dayjs.Dayjs;
  remark?: string;
  parties?: ContractParty[];
}

/** 可提交审批的状态 */
const SUBMITTABLE = ['DRAFT', 'REJECTED', 'WITHDRAWN'];

export default function ContractList() {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const [queryForm] = Form.useForm();
  const [form] = Form.useForm<ContractFormValues>();
  const [records, setRecords] = useState<Contract[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [size, setSize] = useState(10);
  const [query, setQuery] = useState<Record<string, unknown>>({});
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<Contract | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const result = await contractApi.page({ current, size, ...query });
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

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ category: 'PURCHASE', currency: 'CNY', parties: [] });
    setModalOpen(true);
  };

  const openEdit = (record: Contract) => {
    setEditing(record);
    form.setFieldsValue({
      title: record.title,
      category: record.category,
      amount: record.amount ?? undefined,
      currency: record.currency ?? 'CNY',
      effectiveDate: record.effectiveDate ? dayjs(record.effectiveDate) : undefined,
      expireDate: record.expireDate ? dayjs(record.expireDate) : undefined,
      remark: record.remark ?? undefined,
      parties: record.parties ?? [],
    });
    setModalOpen(true);
  };

  const handleSave = async () => {
    const values = await form.validateFields();
    const payload: ContractSaveInput = {
      title: values.title,
      category: values.category,
      amount: values.amount,
      currency: values.currency,
      effectiveDate: values.effectiveDate?.format('YYYY-MM-DD'),
      expireDate: values.expireDate?.format('YYYY-MM-DD'),
      remark: values.remark,
      parties: (values.parties ?? []).filter((party) => party?.name),
    };
    try {
      if (editing) {
        await contractApi.update(editing.id, payload);
      } else {
        await contractApi.create(payload);
      }
      message.success(editing ? '合同已更新' : '合同已创建');
      setModalOpen(false);
      setEditing(null);
      await load();
    } catch (error) {
      message.error((error as Error).message);
    }
  };

  const runAction = async (record: Contract, action: 'submit' | 'withdraw' | 'terminate' | 'archive') => {
    try {
      await contractApi[action](record.id);
      message.success('操作成功');
      await load();
    } catch (error) {
      message.error((error as Error).message);
    }
  };

  const handleDelete = async (record: Contract) => {
    try {
      await contractApi.remove(record.id);
      message.success('合同已删除');
      await load();
    } catch (error) {
      message.error((error as Error).message);
    }
  };

  const columns: ColumnsType<Contract> = [
    {
      title: '合同编号',
      dataIndex: 'code',
      width: 190,
      render: (value: string, record) => (
        <Typography.Link onClick={() => navigate(`/${record.id}`)}>{value}</Typography.Link>
      ),
    },
    { title: '标题', dataIndex: 'title', ellipsis: true },
    {
      title: '类别',
      dataIndex: 'category',
      width: 110,
      render: (value: string) => categoryLabel(value),
    },
    {
      title: '金额',
      dataIndex: 'amount',
      width: 130,
      align: 'right',
      render: (value: number | null, record) =>
        value == null ? '-' : `${record.currency ?? 'CNY'} ${value.toLocaleString()}`,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (value: string) => <Tag color={statusColor(value)}>{statusLabel(value)}</Tag>,
    },
    {
      title: '版本',
      dataIndex: 'currentVersion',
      width: 70,
      align: 'center',
      render: (value: number) => `v${value}`,
    },
    {
      title: '到期日',
      dataIndex: 'expireDate',
      width: 130,
      render: (value: string | null, record) => {
        const days = record.daysToExpire;
        const warning = days != null && days >= 0 && days <= 30;
        return (
          <Space size={4}>
            <span>{formatDate(value)}</span>
            {warning ? <Tag color="orange">{days} 天</Tag> : null}
          </Space>
        );
      },
    },
    {
      title: '操作',
      key: 'actions',
      width: 260,
      render: (_, record) => (
        <Space size={0} wrap>
          <Button type="link" size="small" onClick={() => navigate(`/${record.id}`)}>
            详情
          </Button>
          {record.status === 'DRAFT' && hasPermission('sys:contract:edit') ? (
            <Button type="link" size="small" onClick={() => openEdit(record)}>
              编辑
            </Button>
          ) : null}
          {SUBMITTABLE.includes(record.status) && hasPermission('sys:contract:submit') ? (
            <Button type="link" size="small" onClick={() => void runAction(record, 'submit')}>
              提交审批
            </Button>
          ) : null}
          {record.status === 'APPROVING' && hasPermission('sys:contract:withdraw') ? (
            <Popconfirm title="撤回该合同的审批？" onConfirm={() => void runAction(record, 'withdraw')}>
              <Button type="link" size="small">
                撤回
              </Button>
            </Popconfirm>
          ) : null}
          {['APPROVED', 'SIGNING', 'SIGNED', 'EFFECTIVE'].includes(record.status)
            && hasPermission('sys:contract:terminate') ? (
            <Popconfirm title="终止该合同？" onConfirm={() => void runAction(record, 'terminate')}>
              <Button type="link" size="small" danger>
                终止
              </Button>
            </Popconfirm>
          ) : null}
          {record.status === 'EFFECTIVE' && hasPermission('sys:contract:archive') ? (
            <Popconfirm title="归档该合同？" onConfirm={() => void runAction(record, 'archive')}>
              <Button type="link" size="small">
                归档
              </Button>
            </Popconfirm>
          ) : null}
          {record.status === 'DRAFT' && hasPermission('sys:contract:delete') ? (
            <Popconfirm title="删除该合同？" onConfirm={() => void handleDelete(record)}>
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
          <Form.Item name="code" label="编号">
            <Input allowClear placeholder="HT-CG-2026" style={{ width: 160 }} />
          </Form.Item>
          <Form.Item name="title" label="标题">
            <Input allowClear placeholder="关键字" style={{ width: 160 }} />
          </Form.Item>
          <Form.Item name="category" label="类别">
            <Select allowClear options={CATEGORY_OPTIONS} style={{ width: 130 }} />
          </Form.Item>
          <Form.Item name="status" label="状态">
            <Select allowClear options={STATUS_OPTIONS} style={{ width: 130 }} />
          </Form.Item>
          <Form.Item name="expiringSoon" label="30 天内到期" valuePropName="checked">
            <Switch />
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
          {hasPermission('sys:contract:add') ? (
            <Form.Item>
              <Button type="primary" onClick={openCreate}>
                新建合同
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
        title={editing ? `编辑合同 ${editing.code}` : '新建合同'}
        open={modalOpen}
        width={720}
        onCancel={() => setModalOpen(false)}
        onOk={() => void handleSave()}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item name="title" label="合同标题" rules={[{ required: true, message: '请输入合同标题' }]}>
            <Input maxLength={255} />
          </Form.Item>
          <Space size={12} style={{ width: '100%' }}>
            <Form.Item name="category" label="合同类别" rules={[{ required: true, message: '请选择类别' }]}>
              <Select options={CATEGORY_OPTIONS} style={{ width: 160 }} />
            </Form.Item>
            <Form.Item name="amount" label="合同金额">
              <InputNumber min={0} precision={2} style={{ width: 180 }} />
            </Form.Item>
            <Form.Item name="currency" label="币种">
              <Select
                options={[
                  { value: 'CNY', label: 'CNY' },
                  { value: 'USD', label: 'USD' },
                  { value: 'EUR', label: 'EUR' },
                ]}
                style={{ width: 100 }}
              />
            </Form.Item>
          </Space>
          <Space size={12} style={{ width: '100%' }}>
            <Form.Item name="effectiveDate" label="生效日期">
              <DatePicker style={{ width: 160 }} />
            </Form.Item>
            <Form.Item
              name="expireDate"
              label="到期日期"
              dependencies={['effectiveDate']}
              rules={[
                ({ getFieldValue }) => ({
                  validator(_, value?: dayjs.Dayjs) {
                    const start = getFieldValue('effectiveDate') as dayjs.Dayjs | undefined;
                    if (value && start && value.isBefore(start, 'day')) {
                      return Promise.reject(new Error('到期日期不得早于生效日期'));
                    }
                    return Promise.resolve();
                  },
                }),
              ]}
            >
              <DatePicker style={{ width: 160 }} />
            </Form.Item>
          </Space>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} maxLength={255} />
          </Form.Item>

          <Form.List name="parties">
            {(fields, { add, remove }) => (
              <Space direction="vertical" size={8} style={{ width: '100%' }}>
                <Space>
                  <Typography.Text strong>相对方</Typography.Text>
                  <Button size="small" onClick={() => add({ partyType: 'B' })}>
                    添加相对方
                  </Button>
                </Space>
                {fields.map((field) => (
                  <Space key={field.key} align="baseline" wrap>
                    <Form.Item
                      name={[field.name, 'partyType']}
                      rules={[{ required: true, message: '类型必填' }]}
                    >
                      <Select options={[{ value: 'A', label: '甲方' }, { value: 'B', label: '乙方' }, { value: 'OTHER', label: '其他' }]} placeholder="类型" style={{ width: 90 }} />
                    </Form.Item>
                    <Form.Item
                      name={[field.name, 'name']}
                      rules={[{ required: true, message: '名称必填' }]}
                    >
                      <Input placeholder="相对方名称" style={{ width: 220 }} />
                    </Form.Item>
                    <Form.Item name={[field.name, 'creditCode']}>
                      <Input placeholder="统一社会信用代码" style={{ width: 200 }} />
                    </Form.Item>
                    <Form.Item name={[field.name, 'contact']}>
                      <Input placeholder="联系人" style={{ width: 100 }} />
                    </Form.Item>
                    <Form.Item name={[field.name, 'phone']}>
                      <Input placeholder="联系电话" style={{ width: 130 }} />
                    </Form.Item>
                    <Button type="link" danger size="small" onClick={() => remove(field.name)}>
                      移除
                    </Button>
                  </Space>
                ))}
              </Space>
            )}
          </Form.List>
        </Form>
      </Modal>
    </div>
  );
}
