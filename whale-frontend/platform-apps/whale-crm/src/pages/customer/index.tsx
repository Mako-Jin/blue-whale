// 客户列表：关键字/状态/级别/负责人筛选、分页、新建编辑、状态与级别变更、查重提示、删除
import { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Card,
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
  message,
} from 'antd';
import type { TableProps } from 'antd';
import { useNavigate } from 'react-router-dom';
import {
  changeCustomerLevel,
  changeCustomerStatus,
  createCustomer,
  deleteCustomer,
  duplicateCheck,
  pageCustomers,
  releasePoolCustomer,
  updateCustomer,
  type CustomerSaveInput,
  type CustomerView,
  type DuplicateCandidateView,
} from '../../api/crm';
import { hasPermission } from '../../utils/permission';
import {
  CUSTOMER_LEVEL_LABEL,
  CUSTOMER_LEVEL_OPTIONS,
  CUSTOMER_STATUS_LABEL,
  CUSTOMER_STATUS_OPTIONS,
  POOL_STATUS_LABEL,
  formatDateTime,
} from '../../utils/dict';

/** 筛选条件 */
interface FilterValues {
  keyword?: string;
  status?: string;
  level?: string;
  ownerId?: number | null;
}

/** 新建 / 编辑表单值 */
interface CustomerFormValues {
  customerCode?: string;
  name: string;
  shortName?: string;
  level?: string;
  source?: string;
  industry?: string;
  website?: string;
  scale?: string;
  taxNo?: string;
  province?: string;
  city?: string;
  region?: string;
  address?: string;
  tags?: string;
  ownerId?: number | null;
  deptId?: number | null;
}

/** 状态颜色 */
const STATUS_COLOR: Record<string, string> = {
  ACTIVE: 'green',
  INACTIVE: 'default',
  MERGED: 'orange',
};

/** 等级颜色 */
const LEVEL_COLOR: Record<string, string> = {
  A: 'red',
  B: 'blue',
  C: 'gold',
  D: 'default',
  KEY: 'red',
  NORMAL: 'gold',
  POTENTIAL: 'default',
};

/**
 * 读取错误文案。
 *
 * @param error 异常对象
 * @returns 可展示的错误信息
 */
function errorText(error: unknown): string {
  return error instanceof Error ? error.message : '操作失败，请稍后重试';
}

/**
 * 汇总重复候选描述。
 *
 * @param candidates 重复候选
 * @returns 描述文本
 */
function describeCandidates(candidates: DuplicateCandidateView[]): string {
  return candidates
    .map(
      (item) =>
        `${item.name ?? '未命名'}（${item.customerCode ?? '无编码'}）命中：${
          item.matchedFields ?? '-'
        }`,
    )
    .join('；');
}

/**
 * 软冲突二次确认：联系人电话 / 邮箱命中时允许确认后保存。
 *
 * @param candidates 软冲突候选
 * @returns 用户是否确认继续
 */
function confirmSoftDuplicates(candidates: DuplicateCandidateView[]): Promise<boolean> {
  return new Promise((resolve) => {
    Modal.confirm({
      title: '发现疑似重复客户',
      content: (
        <Space direction="vertical" size={4}>
          <Typography.Text type="warning">
            以下客户命中联系人电话或邮箱，请确认是否继续保存。
          </Typography.Text>
          <Typography.Text>{describeCandidates(candidates)}</Typography.Text>
        </Space>
      ),
      okText: '仍然保存',
      cancelText: '返回修改',
      onOk: () => resolve(true),
      onCancel: () => resolve(false),
    });
  });
}

interface CustomerFormModalProps {
  open: boolean;
  initial: CustomerView | null;
  onCancel: () => void;
  onSaved: () => void;
}

/** 客户新建 / 编辑弹窗（含查重提示） */
function CustomerFormModal({ open, initial, onCancel, onSaved }: CustomerFormModalProps) {
  const [form] = Form.useForm<CustomerFormValues>();
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!open) {
      return;
    }
    if (initial) {
      form.setFieldsValue({
        customerCode: initial.customerCode ?? undefined,
        name: initial.name,
        shortName: initial.shortName ?? undefined,
        level: initial.level ?? undefined,
        source: initial.source ?? undefined,
        industry: initial.industry ?? undefined,
        website: initial.website ?? undefined,
        scale: initial.scale ?? undefined,
        taxNo: initial.taxNo ?? undefined,
        province: initial.province ?? undefined,
        city: initial.city ?? undefined,
        region: initial.region ?? undefined,
        address: initial.address ?? undefined,
        tags: initial.tags ?? undefined,
        ownerId: initial.ownerId ?? null,
        deptId: initial.deptId ?? null,
      });
    } else {
      form.resetFields();
      form.setFieldsValue({ level: 'C' });
    }
  }, [open, initial, form]);

  const handleOk = async () => {
    setSaving(true);
    try {
      const values = await form.validateFields();
      let confirmed = false;
      if (hasPermission('sys:crm:customer:duplicate')) {
        const check = await duplicateCheck({
          name: values.name,
          website: values.website,
          taxNo: values.taxNo,
          excludeId: initial?.id,
        });
        const hard = check.records.filter((item) => item.severity === 'HARD');
        if (hard.length > 0) {
          Modal.warning({
            title: '存在重名或同税号客户',
            content: `无法保存。${describeCandidates(hard)}`,
          });
          return;
        }
        const soft = check.records.filter((item) => item.severity === 'SOFT');
        if (soft.length > 0) {
          confirmed = await confirmSoftDuplicates(soft);
          if (!confirmed) {
            return;
          }
        }
      }
      const payload: CustomerSaveInput = {
        customerCode: values.customerCode,
        name: values.name,
        shortName: values.shortName,
        level: values.level,
        source: values.source,
        industry: values.industry,
        website: values.website,
        scale: values.scale,
        taxNo: values.taxNo,
        province: values.province,
        city: values.city,
        region: values.region,
        address: values.address,
        tags: values.tags,
        ownerId: values.ownerId ?? null,
        deptId: values.deptId ?? null,
        confirmedDuplicate: confirmed,
      };
      if (initial) {
        await updateCustomer(initial.id, payload);
        message.success('客户已更新');
      } else {
        await createCustomer(payload);
        message.success('客户已创建');
      }
      onSaved();
    } catch (error) {
      if (error instanceof Error) {
        message.error(errorText(error));
      }
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open={open}
      title={initial ? `编辑客户：${initial.name}` : '新建客户'}
      width={760}
      confirmLoading={saving}
      okText="保存"
      cancelText="取消"
      onOk={handleOk}
      onCancel={onCancel}
      destroyOnClose
    >
      <Form form={form} layout="vertical" preserve={false}>
        <Space size={12} style={{ display: 'flex' }}>
          <Form.Item
            name="name"
            label="客户名称"
            rules={[{ required: true, message: '请输入客户名称' }]}
            style={{ flex: 1 }}
          >
            <Input placeholder="必填" maxLength={128} />
          </Form.Item>
          <Form.Item name="customerCode" label="客户编码" style={{ flex: 1 }}>
            <Input placeholder="留空由系统生成" maxLength={64} />
          </Form.Item>
          <Form.Item name="shortName" label="客户简称" style={{ flex: 1 }}>
            <Input maxLength={64} />
          </Form.Item>
        </Space>
        <Space size={12} style={{ display: 'flex' }}>
          <Form.Item name="level" label="客户等级" style={{ flex: 1 }}>
            <Select options={CUSTOMER_LEVEL_OPTIONS} allowClear />
          </Form.Item>
          <Form.Item name="source" label="来源" style={{ flex: 1 }}>
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item name="industry" label="行业" style={{ flex: 1 }}>
            <Input maxLength={64} />
          </Form.Item>
        </Space>
        <Space size={12} style={{ display: 'flex' }}>
          <Form.Item name="website" label="官网" style={{ flex: 1 }}>
            <Input placeholder="用于域名查重" maxLength={255} />
          </Form.Item>
          <Form.Item name="scale" label="企业规模" style={{ flex: 1 }}>
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item name="taxNo" label="税号" style={{ flex: 1 }}>
            <Input maxLength={64} />
          </Form.Item>
        </Space>
        <Space size={12} style={{ display: 'flex' }}>
          <Form.Item name="province" label="省" style={{ flex: 1 }}>
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item name="city" label="市" style={{ flex: 1 }}>
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item name="region" label="区县" style={{ flex: 1 }}>
            <Input maxLength={64} />
          </Form.Item>
        </Space>
        <Form.Item name="address" label="联系地址">
          <Input maxLength={255} />
        </Form.Item>
        <Space size={12} style={{ display: 'flex' }}>
          <Form.Item name="ownerId" label="负责人 ID" style={{ flex: 1 }}>
            <InputNumber style={{ width: '100%' }} min={1} placeholder="仅全量数据范围可指定" />
          </Form.Item>
          <Form.Item name="deptId" label="归属部门 ID" style={{ flex: 1 }}>
            <InputNumber style={{ width: '100%' }} min={1} />
          </Form.Item>
          <Form.Item name="tags" label="标签" style={{ flex: 1 }}>
            <Input maxLength={255} />
          </Form.Item>
        </Space>
      </Form>
    </Modal>
  );
}

export default function CustomerList() {
  const navigate = useNavigate();
  const [filterForm] = Form.useForm<FilterValues>();
  const [query, setQuery] = useState<FilterValues>({});
  const [page, setPage] = useState({ current: 1, size: 20 });
  const [refreshKey, setRefreshKey] = useState(0);
  const [rows, setRows] = useState<CustomerView[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);

  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<CustomerView | null>(null);
  const [statusTarget, setStatusTarget] = useState<CustomerView | null>(null);
  const [statusValue, setStatusValue] = useState('ACTIVE');
  const [levelTarget, setLevelTarget] = useState<CustomerView | null>(null);
  const [levelValue, setLevelValue] = useState('C');
  const [saving, setSaving] = useState(false);

  const reload = useCallback(() => setRefreshKey((key) => key + 1), []);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    pageCustomers({ ...query, current: page.current, size: page.size })
      .then((result) => {
        if (!cancelled) {
          setRows(result.records);
          setTotal(Number(result.total));
        }
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          message.error(errorText(error));
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [query, page, refreshKey]);

  const handleSearch = (values: FilterValues) => {
    setQuery(values);
    setPage((previous) => ({ ...previous, current: 1 }));
  };

  const handleReset = () => {
    filterForm.resetFields();
    setQuery({});
    setPage((previous) => ({ ...previous, current: 1 }));
  };

  const openCreate = () => {
    setEditing(null);
    setFormOpen(true);
  };

  const openEdit = (row: CustomerView) => {
    setEditing(row);
    setFormOpen(true);
  };

  const openStatus = (row: CustomerView) => {
    setStatusTarget(row);
    setStatusValue(row.status);
  };

  const openLevel = (row: CustomerView) => {
    setLevelTarget(row);
    setLevelValue(row.level ?? 'C');
  };

  const submitStatus = async () => {
    if (!statusTarget) {
      return;
    }
    setSaving(true);
    try {
      await changeCustomerStatus(statusTarget.id, statusValue);
      message.success('客户状态已更新');
      setStatusTarget(null);
      reload();
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setSaving(false);
    }
  };

  const submitLevel = async () => {
    if (!levelTarget) {
      return;
    }
    setSaving(true);
    try {
      await changeCustomerLevel(levelTarget.id, levelValue);
      message.success('客户等级已更新');
      setLevelTarget(null);
      reload();
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (row: CustomerView) => {
    try {
      await deleteCustomer(row.id);
      message.success('客户已删除');
      reload();
    } catch (error) {
      message.error(errorText(error));
    }
  };

  const handleRelease = (row: CustomerView) => {
    Modal.confirm({
      title: `释放客户「${row.name}」到公海？`,
      content: '释放后客户将失去负责人并进入公海，其他销售可领取。',
      okText: '释放',
      cancelText: '取消',
      onOk: async () => {
        try {
          await releasePoolCustomer(row.id);
          message.success('客户已释放到公海');
          reload();
        } catch (error) {
          message.error(errorText(error));
        }
      },
    });
  };

  const columns: TableProps<CustomerView>['columns'] = [
    {
      title: '客户编码',
      dataIndex: 'customerCode',
      width: 140,
      render: (value: string | null | undefined) => value ?? '-',
    },
    {
      title: '客户名称',
      dataIndex: 'name',
      width: 200,
      render: (name: string, row) => (
        <Typography.Link onClick={() => navigate(`/customer/${row.id}`)}>{name}</Typography.Link>
      ),
    },
    {
      title: '等级',
      dataIndex: 'level',
      width: 100,
      render: (value: string | null | undefined) =>
        value ? (
          <Tag color={LEVEL_COLOR[value] ?? 'default'}>{CUSTOMER_LEVEL_LABEL[value] ?? value}</Tag>
        ) : (
          '-'
        ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (value: string | null | undefined) =>
        value ? (
          <Tag color={STATUS_COLOR[value] ?? 'default'}>
            {CUSTOMER_STATUS_LABEL[value] ?? value}
          </Tag>
        ) : (
          '-'
        ),
    },
    {
      title: '归属',
      dataIndex: 'poolStatus',
      width: 90,
      render: (value: string | null | undefined) =>
        value ? (
          <Tag color={value === 'IN_POOL' ? 'orange' : 'green'}>
            {POOL_STATUS_LABEL[value] ?? value}
          </Tag>
        ) : (
          '-'
        ),
    },
    {
      title: '负责人',
      dataIndex: 'ownerId',
      width: 90,
      render: (value: number | null | undefined) => value ?? '公海',
    },
    {
      title: '行业',
      dataIndex: 'industry',
      width: 120,
      render: (value: string | null | undefined) => value ?? '-',
    },
    {
      title: '来源',
      dataIndex: 'source',
      width: 110,
      render: (value: string | null | undefined) => value ?? '-',
    },
    {
      title: '最近跟进',
      dataIndex: 'lastFollowUpAt',
      width: 160,
      render: (value: string | null | undefined) => formatDateTime(value),
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      width: 160,
      render: (value: string | null | undefined) => formatDateTime(value),
    },
    {
      title: '操作',
      key: 'actions',
      width: 300,
      fixed: 'right',
      render: (_: unknown, row: CustomerView) => (
        <Space size={4} wrap>
          <Button type="link" size="small" onClick={() => navigate(`/customer/${row.id}`)}>
            详情
          </Button>
          {hasPermission('sys:crm:customer:update') && (
            <Button type="link" size="small" onClick={() => openEdit(row)}>
              编辑
            </Button>
          )}
          {hasPermission('sys:crm:customer:status') && (
            <Button type="link" size="small" onClick={() => openStatus(row)}>
              状态
            </Button>
          )}
          {hasPermission('sys:crm:customer:level') && (
            <Button type="link" size="small" onClick={() => openLevel(row)}>
              级别
            </Button>
          )}
          {hasPermission('sys:crm:pool:release') && row.poolStatus !== 'IN_POOL' && (
            <Button type="link" size="small" onClick={() => handleRelease(row)}>
              释放
            </Button>
          )}
          {hasPermission('sys:crm:customer:delete') && (
            <Popconfirm
              title="删除客户"
              description="删除后不可恢复，确认继续？"
              okText="删除"
              cancelText="取消"
              onConfirm={() => handleDelete(row)}
            >
              <Button type="link" size="small" danger>
                删除
              </Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  return (
    <>
      <Card size="small" style={{ marginBottom: 16 }}>
        <Form form={filterForm} layout="inline" onFinish={handleSearch}>
          <Form.Item name="keyword" label="关键字">
            <Input allowClear placeholder="编码 / 名称 / 简称" style={{ width: 200 }} />
          </Form.Item>
          <Form.Item name="status" label="状态">
            <Select
              allowClear
              placeholder="全部"
              style={{ width: 120 }}
              options={CUSTOMER_STATUS_OPTIONS.filter((item) => item.value !== 'MERGED')}
            />
          </Form.Item>
          <Form.Item name="level" label="级别">
            <Select
              allowClear
              placeholder="全部"
              style={{ width: 130 }}
              options={CUSTOMER_LEVEL_OPTIONS}
            />
          </Form.Item>
          <Form.Item name="ownerId" label="负责人 ID">
            <InputNumber min={1} placeholder="负责人用户 ID" style={{ width: 150 }} />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button htmlType="submit" type="primary">
                查询
              </Button>
              <Button onClick={handleReset}>重置</Button>
            </Space>
          </Form.Item>
        </Form>
      </Card>

      <Card>
        <Space style={{ marginBottom: 16, width: '100%', justifyContent: 'space-between' }}>
          <Space>
            <Typography.Title level={5} style={{ margin: 0 }}>
              客户列表
            </Typography.Title>
            <Typography.Text type="secondary">共 {total} 个客户</Typography.Text>
          </Space>
          <Space>
            <Button onClick={reload}>刷新</Button>
            {hasPermission('sys:crm:customer:create') && (
              <Button type="primary" onClick={openCreate}>
                新建客户
              </Button>
            )}
          </Space>
        </Space>
        <Table<CustomerView>
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={rows}
          scroll={{ x: 1500 }}
          pagination={{
            current: page.current,
            pageSize: page.size,
            total,
            showSizeChanger: true,
            showTotal: (totalCount) => `共 ${totalCount} 条`,
            onChange: (current, size) => setPage({ current, size }),
          }}
        />
      </Card>

      <CustomerFormModal
        open={formOpen}
        initial={editing}
        onCancel={() => {
          setFormOpen(false);
          setEditing(null);
        }}
        onSaved={() => {
          setFormOpen(false);
          setEditing(null);
          reload();
        }}
      />

      <Modal
        open={Boolean(statusTarget)}
        title={`变更客户状态：${statusTarget?.name ?? ''}`}
        okText="保存"
        cancelText="取消"
        confirmLoading={saving}
        onOk={submitStatus}
        onCancel={() => setStatusTarget(null)}
      >
        <Select
          style={{ width: '100%' }}
          value={statusValue}
          options={CUSTOMER_STATUS_OPTIONS.filter((item) => item.value !== 'MERGED')}
          onChange={setStatusValue}
        />
      </Modal>

      <Modal
        open={Boolean(levelTarget)}
        title={`变更客户等级：${levelTarget?.name ?? ''}`}
        okText="保存"
        cancelText="取消"
        confirmLoading={saving}
        onOk={submitLevel}
        onCancel={() => setLevelTarget(null)}
      >
        <Select
          style={{ width: '100%' }}
          value={levelValue}
          options={CUSTOMER_LEVEL_OPTIONS}
          onChange={setLevelValue}
        />
      </Modal>
    </>
  );
}
