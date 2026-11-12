// 公海客户：分页筛选、认领、释放、分配、回收规则查看与保存、归属变更日志
import { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Checkbox,
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
  Typography,
  message,
} from 'antd';
import type { TableProps } from 'antd';
import {
  assignPoolCustomer,
  claimPoolCustomer,
  getPoolRule,
  listOwnerLogs,
  pagePoolCustomers,
  recyclePool,
  releasePoolCustomer,
  savePoolRule,
  type CustomerPageQuery,
  type CustomerView,
  type OwnerLogView,
  type PoolRuleView,
} from '../../api/crm';
import { hasPermission } from '../../utils/permission';
import {
  CUSTOMER_LEVEL_LABEL,
  CUSTOMER_LEVEL_OPTIONS,
  OWNER_LOG_ACTION_LABEL,
  formatDateTime,
} from '../../utils/dict';

/** 公海筛选条件 */
interface PoolFilterValues {
  keyword?: string;
  level?: string;
  status?: string;
  source?: string;
  industry?: string;
}

/** 分配表单值 */
interface AssignFormValues {
  ownerId: number;
  deptId?: number | null;
  remark?: string;
}

/** 释放表单值 */
interface ReleaseFormValues {
  customerId: number;
  remark?: string;
}

/** 规则表单值 */
interface RuleFormValues {
  ruleName?: string;
  inactiveDays?: number | null;
  noOpportunityDays?: number | null;
  levels?: string;
  deptId?: number | null;
  dailyClaimLimit?: number | null;
  enabled?: boolean;
}

/**
 * 读取错误文案。
 *
 * @param error 异常对象
 * @returns 可展示的错误信息
 */
function errorText(error: unknown): string {
  return error instanceof Error ? error.message : '操作失败，请稍后重试';
}

/** 规则弹窗 */
function RuleModal({
  open,
  onCancel,
}: {
  open: boolean;
  onCancel: () => void;
}) {
  const [form] = Form.useForm<RuleFormValues>();
  const [rule, setRule] = useState<PoolRuleView | null>(null);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!open) {
      return;
    }
    setLoading(true);
    getPoolRule()
      .then((result) => {
        setRule(result);
        form.setFieldsValue({
          ruleName: result.ruleName ?? undefined,
          inactiveDays: result.inactiveDays ?? null,
          noOpportunityDays: result.noOpportunityDays ?? null,
          levels: result.levels ?? undefined,
          deptId: result.deptId ?? null,
          dailyClaimLimit: result.dailyClaimLimit ?? null,
          enabled: result.enabled ?? true,
        });
      })
      .catch((error: unknown) => message.error(errorText(error)))
      .finally(() => setLoading(false));
  }, [open, form]);

  const handleOk = async () => {
    setSaving(true);
    try {
      const values = await form.validateFields();
      const saved = await savePoolRule({
        id: rule?.id ?? null,
        ruleName: values.ruleName,
        inactiveDays: values.inactiveDays ?? null,
        noOpportunityDays: values.noOpportunityDays ?? null,
        levels: values.levels,
        deptId: values.deptId ?? null,
        dailyClaimLimit: values.dailyClaimLimit ?? null,
        enabled: Boolean(values.enabled),
      });
      setRule(saved);
      message.success('公海规则已保存');
      onCancel();
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
      title="公海回收规则"
      width={640}
      okText="保存"
      cancelText="取消"
      confirmLoading={saving}
      onOk={handleOk}
      onCancel={onCancel}
      destroyOnClose
    >
      <Space direction="vertical" size={4} style={{ display: 'flex', marginBottom: 12 }}>
        {rule && (
          <Typography.Text type="secondary">
            当前规则：{rule.ruleName ?? '未命名'}；上次执行：{formatDateTime(rule.lastRunAt)}；上次回收：{rule.recycleCount ?? 0} 个客户
          </Typography.Text>
        )}
        <Typography.Text type="secondary">
          规则说明：超过设定天数未跟进或未产生商机的客户，将由系统自动回收至公海。
        </Typography.Text>
      </Space>
      <Form form={form} layout="vertical" preserve={false} disabled={loading}>
        <Form.Item name="ruleName" label="规则名称">
          <Input maxLength={64} />
        </Form.Item>
        <Space size={12} style={{ display: 'flex' }}>
          <Form.Item name="inactiveDays" label="未跟进天数" style={{ flex: 1 }}>
            <InputNumber min={0} style={{ width: '100%' }} placeholder="如 30" />
          </Form.Item>
          <Form.Item name="noOpportunityDays" label="无商机天数" style={{ flex: 1 }}>
            <InputNumber min={0} style={{ width: '100%' }} placeholder="如 60" />
          </Form.Item>
          <Form.Item name="dailyClaimLimit" label="每人每日领取上限" style={{ flex: 1 }}>
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
        </Space>
        <Space size={12} style={{ display: 'flex' }}>
          <Form.Item name="levels" label="适用等级" style={{ flex: 1 }} extra="多个用英文逗号分隔，如 C,D">
            <Input maxLength={32} />
          </Form.Item>
          <Form.Item name="deptId" label="适用部门 ID" style={{ flex: 1 }}>
            <InputNumber min={1} style={{ width: '100%' }} />
          </Form.Item>
        </Space>
        <Form.Item name="enabled" valuePropName="checked" style={{ marginBottom: 0 }}>
          <Checkbox>启用自动回收</Checkbox>
        </Form.Item>
      </Form>
    </Modal>
  );
}

export default function PoolList() {
  const [filterForm] = Form.useForm<PoolFilterValues>();
  const [query, setQuery] = useState<PoolFilterValues>({});
  const [page, setPage] = useState({ current: 1, size: 20 });
  const [refreshKey, setRefreshKey] = useState(0);
  const [rows, setRows] = useState<CustomerView[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);

  const [assignTarget, setAssignTarget] = useState<CustomerView | null>(null);
  const [assignForm] = Form.useForm<AssignFormValues>();
  const [releaseOpen, setReleaseOpen] = useState(false);
  const [releaseForm] = Form.useForm<ReleaseFormValues>();
  const [ruleOpen, setRuleOpen] = useState(false);

  const [logsTarget, setLogsTarget] = useState<CustomerView | null>(null);
  const [logs, setLogs] = useState<OwnerLogView[]>([]);
  const [logsLoading, setLogsLoading] = useState(false);

  const reload = useCallback(() => setRefreshKey((key) => key + 1), []);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    const poolQuery: CustomerPageQuery = { ...query, current: page.current, size: page.size };
    pagePoolCustomers(poolQuery)
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

  useEffect(() => {
    if (!logsTarget) {
      return;
    }
    let cancelled = false;
    setLogsLoading(true);
    listOwnerLogs(logsTarget.id)
      .then((result) => {
        if (!cancelled) {
          setLogs(result);
        }
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          message.error(errorText(error));
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLogsLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [logsTarget]);

  const handleSearch = (values: PoolFilterValues) => {
    setQuery(values);
    setPage((previous) => ({ ...previous, current: 1 }));
  };

  const handleReset = () => {
    filterForm.resetFields();
    setQuery({});
    setPage((previous) => ({ ...previous, current: 1 }));
  };

  const handleClaim = async (row: CustomerView) => {
    try {
      await claimPoolCustomer(row.id);
      message.success(`已领取客户「${row.name}」`);
      reload();
    } catch (error) {
      message.error(errorText(error));
    }
  };

  const handleRecycle = async () => {
    try {
      const result = await recyclePool();
      message.success(`回收完成，共回收 ${result.recycledCount} 个客户`);
      reload();
    } catch (error) {
      message.error(errorText(error));
    }
  };

  const handleAssign = async () => {
    if (!assignTarget) {
      return;
    }
    setSaving(true);
    try {
      const values = await assignForm.validateFields();
      await assignPoolCustomer(assignTarget.id, {
        ownerId: values.ownerId,
        deptId: values.deptId ?? null,
        remark: values.remark,
      });
      message.success('负责人已分配');
      setAssignTarget(null);
      assignForm.resetFields();
      reload();
    } catch (error) {
      if (error instanceof Error) {
        message.error(errorText(error));
      }
    } finally {
      setSaving(false);
    }
  };

  const handleRelease = async () => {
    setSaving(true);
    try {
      const values = await releaseForm.validateFields();
      await releasePoolCustomer(values.customerId, values.remark);
      message.success('客户已释放到公海');
      setReleaseOpen(false);
      releaseForm.resetFields();
      reload();
    } catch (error) {
      if (error instanceof Error) {
        message.error(errorText(error));
      }
    } finally {
      setSaving(false);
    }
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
      width: 220,
    },
    {
      title: '等级',
      dataIndex: 'level',
      width: 100,
      render: (value: string | null | undefined) =>
        value ? (
          <Tag color="blue">{CUSTOMER_LEVEL_LABEL[value] ?? value}</Tag>
        ) : (
          '-'
        ),
    },
    {
      title: '行业',
      dataIndex: 'industry',
      width: 140,
      render: (value: string | null | undefined) => value ?? '-',
    },
    {
      title: '来源',
      dataIndex: 'source',
      width: 120,
      render: (value: string | null | undefined) => value ?? '-',
    },
    {
      title: '入公海时间',
      dataIndex: 'poolAt',
      width: 160,
      render: (value: string | null | undefined) => formatDateTime(value),
    },
    {
      title: '最近跟进',
      dataIndex: 'lastFollowUpAt',
      width: 160,
      render: (value: string | null | undefined) => formatDateTime(value),
    },
    {
      title: '操作',
      key: 'actions',
      width: 260,
      fixed: 'right',
      render: (_: unknown, row: CustomerView) => (
        <Space size={4} wrap>
          {hasPermission('sys:crm:pool:claim') && (
            <Popconfirm
              title={`领取客户「${row.name}」？`}
              description="领取后客户将归属到您名下。"
              okText="领取"
              cancelText="取消"
              onConfirm={() => handleClaim(row)}
            >
              <Button type="link" size="small">
                认领
              </Button>
            </Popconfirm>
          )}
          {hasPermission('sys:crm:pool:assign') && (
            <Button
              type="link"
              size="small"
              onClick={() => {
                setAssignTarget(row);
                assignForm.resetFields();
              }}
            >
              分配
            </Button>
          )}
          {hasPermission('sys:crm:pool:view') && (
            <Button type="link" size="small" onClick={() => setLogsTarget(row)}>
              归属日志
            </Button>
          )}
        </Space>
      ),
    },
  ];

  const logColumns: TableProps<OwnerLogView>['columns'] = [
    {
      title: '时间',
      dataIndex: 'createTime',
      width: 160,
      render: (value: string | null | undefined) => formatDateTime(value),
    },
    {
      title: '动作',
      dataIndex: 'action',
      width: 90,
      render: (value: string | null | undefined) =>
        OWNER_LOG_ACTION_LABEL[value ?? ''] ?? value ?? '-',
    },
    {
      title: '原负责人',
      dataIndex: 'fromOwnerId',
      width: 100,
      render: (value: number | null | undefined) => value ?? '-',
    },
    {
      title: '新负责人',
      dataIndex: 'toOwnerId',
      width: 100,
      render: (value: number | null | undefined) => value ?? '-',
    },
    {
      title: '操作人',
      dataIndex: 'operatorId',
      width: 100,
      render: (value: number | null | undefined) => value ?? '-',
    },
    {
      title: '备注',
      dataIndex: 'remark',
      render: (value: string | null | undefined) => value ?? '-',
    },
  ];

  return (
    <>
      <Card size="small" style={{ marginBottom: 16 }}>
        <Form form={filterForm} layout="inline" onFinish={handleSearch}>
          <Form.Item name="keyword" label="关键字">
            <Input allowClear placeholder="编码 / 名称 / 简称" style={{ width: 200 }} />
          </Form.Item>
          <Form.Item name="level" label="级别">
            <Select
              allowClear
              placeholder="全部"
              style={{ width: 130 }}
              options={CUSTOMER_LEVEL_OPTIONS}
            />
          </Form.Item>
          <Form.Item name="source" label="来源">
            <Input allowClear placeholder="来源" style={{ width: 140 }} />
          </Form.Item>
          <Form.Item name="industry" label="行业">
            <Input allowClear placeholder="行业" style={{ width: 140 }} />
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
              公海客户
            </Typography.Title>
            <Typography.Text type="secondary">共 {total} 个客户</Typography.Text>
          </Space>
          <Space>
            <Button onClick={reload}>刷新</Button>
            {hasPermission('sys:crm:pool:rule') && (
              <Button onClick={() => setRuleOpen(true)}>回收规则</Button>
            )}
            {hasPermission('sys:crm:pool:release') && (
              <Button onClick={() => setReleaseOpen(true)}>释放客户</Button>
            )}
            {hasPermission('sys:crm:pool:recycle') && (
              <Popconfirm
                title="按规则立即回收客户？"
                description="将按当前公海规则把满足条件的客户回收至公海。"
                okText="回收"
                cancelText="取消"
                onConfirm={handleRecycle}
              >
                <Button danger>立即回收</Button>
              </Popconfirm>
            )}
          </Space>
        </Space>

        {!hasPermission('sys:crm:pool:view') && (
          <Alert
            type="warning"
            showIcon
            style={{ marginBottom: 12 }}
            message="当前账号无公海查看权限，以下数据可能无法加载。"
          />
        )}

        <Table<CustomerView>
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={rows}
          scroll={{ x: 1200 }}
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

      <Modal
        open={Boolean(assignTarget)}
        title={`分配负责人：${assignTarget?.name ?? ''}`}
        okText="分配"
        cancelText="取消"
        confirmLoading={saving}
        onOk={handleAssign}
        onCancel={() => {
          setAssignTarget(null);
          assignForm.resetFields();
        }}
      >
        <Form form={assignForm} layout="vertical">
          <Form.Item
            name="ownerId"
            label="负责人用户 ID"
            rules={[{ required: true, message: '请输入负责人用户 ID' }]}
          >
            <InputNumber min={1} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="deptId" label="归属部门 ID">
            <InputNumber min={1} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="remark" label="分配备注">
            <Input.TextArea rows={2} maxLength={255} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        open={releaseOpen}
        title="释放客户到公海"
        okText="释放"
        cancelText="取消"
        confirmLoading={saving}
        onOk={handleRelease}
        onCancel={() => {
          setReleaseOpen(false);
          releaseForm.resetFields();
        }}
      >
        <Form form={releaseForm} layout="vertical">
          <Form.Item
            name="customerId"
            label="客户 ID"
            rules={[{ required: true, message: '请输入要释放的客户 ID' }]}
            extra="仅可释放当前登录人负责的私有客户"
          >
            <InputNumber min={1} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="remark" label="释放原因">
            <Input.TextArea rows={2} maxLength={255} />
          </Form.Item>
        </Form>
      </Modal>

      <RuleModal open={ruleOpen} onCancel={() => setRuleOpen(false)} />

      <Drawer
        open={Boolean(logsTarget)}
        title={`归属变更日志：${logsTarget?.name ?? ''}`}
        width={720}
        onClose={() => {
          setLogsTarget(null);
          setLogs([]);
        }}
      >
        <Table<OwnerLogView>
          rowKey="id"
          size="small"
          loading={logsLoading}
          columns={logColumns}
          dataSource={logs}
          pagination={false}
          scroll={{ x: 700 }}
        />
      </Drawer>
    </>
  );
}
