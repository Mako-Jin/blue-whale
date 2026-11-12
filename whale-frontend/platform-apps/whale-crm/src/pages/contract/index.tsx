// 合同台账：投影列表、按客户同步、续约标记、到期提醒
import { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Card,
  Descriptions,
  Drawer,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  Typography,
  message,
} from 'antd';
import type { TableProps } from 'antd';
import {
  expiringContracts,
  getContract,
  pageContracts,
  markContractRenewal,
  syncContracts,
  type ContractPageQuery,
  type ContractView,
} from '../../api/commerce';
import { hasPermission } from '../../utils/permission';
import {
  CONTRACT_RENEWAL_COLOR,
  CONTRACT_RENEWAL_LABEL,
  CONTRACT_RENEWAL_OPTIONS,
  formatDateTime,
} from '../../utils/dict';

/** 筛选条件 */
interface ContractFilterValues {
  keyword?: string;
  customerId?: number | null;
  status?: string;
  renewalStatus?: string;
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

/**
 * 格式化金额。
 *
 * @param value 后端金额
 * @returns 千分位金额文本
 */
function formatAmount(value?: number | string | null): string {
  if (value === null || value === undefined || value === '') {
    return '-';
  }
  const num = Number(value);
  return Number.isNaN(num)
    ? String(value)
    : num.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

interface RenewalFormValues {
  renewalStatus: string;
  renewalOwnerId?: number | null;
  renewalRemark?: string;
}

/** 合同台账页 */
export default function ContractPage() {
  const [filterForm] = Form.useForm<ContractFilterValues>();
  const [renewalForm] = Form.useForm<RenewalFormValues>();
  const [rows, setRows] = useState<ContractView[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [filters, setFilters] = useState<ContractPageQuery>({});
  const [page, setPage] = useState({ current: 1, size: 10 });
  const [detail, setDetail] = useState<ContractView | null>(null);
  const [renewing, setRenewing] = useState<ContractView | null>(null);
  const [syncOpen, setSyncOpen] = useState(false);
  const [syncCustomerId, setSyncCustomerId] = useState<number | null>(null);
  const [syncLoading, setSyncLoading] = useState(false);
  const [expiring, setExpiring] = useState<ContractView[]>([]);
  const [expiringDays, setExpiringDays] = useState(30);
  const [tabKey, setTabKey] = useState('ledger');

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const result = await pageContracts({ ...filters, current: page.current, size: page.size });
      setRows(result.records);
      setTotal(Number(result.total));
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setLoading(false);
    }
  }, [filters, page]);

  const loadExpiring = useCallback(async () => {
    try {
      const data = await expiringContracts(expiringDays);
      setExpiring(data);
    } catch (error) {
      message.error(errorText(error));
    }
  }, [expiringDays]);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  useEffect(() => {
    if (tabKey === 'expiring') {
      void loadExpiring();
    }
  }, [tabKey, loadExpiring]);

  const handleSearch = (values: ContractFilterValues) => {
    setFilters({
      keyword: values.keyword || undefined,
      customerId: values.customerId ?? null,
      status: values.status || undefined,
      renewalStatus: values.renewalStatus || undefined,
    });
    setPage((prev) => ({ ...prev, current: 1 }));
  };

  const handleReset = () => {
    filterForm.resetFields();
    setFilters({});
    setPage((prev) => ({ ...prev, current: 1 }));
  };

  const handleSync = async () => {
    if (!syncCustomerId) {
      message.error('请输入客户 ID');
      return;
    }
    setSyncLoading(true);
    try {
      const count = await syncContracts(syncCustomerId);
      message.success(`合同投影同步完成，共 ${count} 份合同`);
      setSyncOpen(false);
      setSyncCustomerId(null);
      void loadData();
      if (tabKey === 'expiring') {
        void loadExpiring();
      }
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setSyncLoading(false);
    }
  };

  const openRenewal = async (row: ContractView) => {
    try {
      const fresh = await getContract(row.id);
      setRenewing(fresh);
      renewalForm.setFieldsValue({
        renewalStatus: fresh.renewalStatus ?? 'PENDING',
        renewalOwnerId: fresh.renewalOwnerId ?? null,
        renewalRemark: fresh.renewalRemark ?? undefined,
      });
    } catch (error) {
      message.error(errorText(error));
    }
  };

  const handleRenewal = async () => {
    if (!renewing) {
      return;
    }
    try {
      const values = await renewalForm.validateFields();
      await markContractRenewal(renewing.id, values);
      message.success('续约标记已保存');
      setRenewing(null);
      void loadData();
      if (tabKey === 'expiring') {
        void loadExpiring();
      }
    } catch (error) {
      if (error instanceof Error) {
        message.error(errorText(error));
      }
    }
  };

  const columns: TableProps<ContractView>['columns'] = [
    { title: '合同编号', dataIndex: 'contractNo', width: 170, render: (value?: string | null) => value || '-' },
    { title: '标题', dataIndex: 'title', width: 200, ellipsis: true, render: (value?: string | null) => value || '-' },
    {
      title: '客户 ID',
      dataIndex: 'customerId',
      width: 100,
      render: (value?: number | null) => value ?? '-',
    },
    {
      title: '合同金额',
      dataIndex: 'amount',
      width: 140,
      align: 'right',
      render: (value?: number | string | null) => formatAmount(value),
    },
    { title: '状态', dataIndex: 'status', width: 110, render: (value?: string | null) => value || '-' },
    {
      title: '生效日期',
      dataIndex: 'effectiveDate',
      width: 120,
      render: (value?: string | null) => value || '-',
    },
    {
      title: '到期日期',
      dataIndex: 'expireDate',
      width: 120,
      render: (value?: string | null, row?: ContractView) => (
        <Space size={4}>
          {value || '-'}
          {row?.expired ? <Tag color="red">已到期</Tag> : null}
        </Space>
      ),
    },
    {
      title: '距到期',
      dataIndex: 'daysToExpire',
      width: 90,
      align: 'right',
      render: (value?: number | null) =>
        value === null || value === undefined ? '-' : `${value} 天`,
    },
    {
      title: '续约状态',
      dataIndex: 'renewalStatus',
      width: 110,
      render: (value?: string | null) => (
        <Tag color={CONTRACT_RENEWAL_COLOR[value ?? '']}>
          {CONTRACT_RENEWAL_LABEL[value ?? ''] ?? value ?? '-'}
        </Tag>
      ),
    },
    { title: '负责人', dataIndex: 'ownerId', width: 90, render: (value?: number | null) => value ?? '-' },
    {
      title: '最近同步',
      dataIndex: 'lastSyncAt',
      width: 170,
      render: (value?: string | null) => formatDateTime(value),
    },
    {
      title: '操作',
      key: 'action',
      fixed: 'right',
      width: 150,
      render: (_, row) => (
        <Space size={0}>
          <Button size="small" type="link" onClick={() => setDetail(row)}>
            详情
          </Button>
          {hasPermission('sys:crm:contract:renewal') && (
            <Button size="small" type="link" onClick={() => void openRenewal(row)}>
              续约
            </Button>
          )}
        </Space>
      ),
    },
  ];

  return (
    <Card
      title="合同台账"
      extra={
        hasPermission('sys:crm:contract:sync') ? (
          <Button type="primary" onClick={() => setSyncOpen(true)}>
            按客户同步
          </Button>
        ) : null
      }
    >
      <Tabs
        activeKey={tabKey}
        onChange={setTabKey}
        items={[
          { key: 'ledger', label: '合同台账' },
          { key: 'expiring', label: '到期提醒' },
        ]}
      />
      {tabKey === 'ledger' ? (
        <>
          <Form form={filterForm} layout="inline" onFinish={handleSearch} style={{ marginBottom: 16, rowGap: 12 }}>
            <Form.Item name="keyword">
              <Input placeholder="合同编号 / 标题" allowClear style={{ width: 190 }} />
            </Form.Item>
            <Form.Item name="customerId">
              <InputNumber min={1} precision={0} placeholder="客户 ID" style={{ width: 130 }} />
            </Form.Item>
            <Form.Item name="status">
              <Input placeholder="合同状态" allowClear style={{ width: 130 }} />
            </Form.Item>
            <Form.Item name="renewalStatus">
              <Select
                placeholder="续约状态"
                allowClear
                style={{ width: 130 }}
                options={CONTRACT_RENEWAL_OPTIONS}
              />
            </Form.Item>
            <Form.Item>
              <Space>
                <Button type="primary" htmlType="submit">
                  查询
                </Button>
                <Button onClick={handleReset}>重置</Button>
              </Space>
            </Form.Item>
          </Form>
          <Table<ContractView>
            rowKey="id"
            loading={loading}
            columns={columns}
            dataSource={rows}
            scroll={{ x: 1600 }}
            pagination={{
              current: page.current,
              pageSize: page.size,
              total,
              showSizeChanger: true,
              showTotal: (count) => `共 ${count} 条`,
              onChange: (current, size) => setPage({ current, size }),
            }}
          />
        </>
      ) : (
        <>
          <Space style={{ marginBottom: 16 }}>
            <span>到期窗口</span>
            <Select
              value={expiringDays}
              style={{ width: 120 }}
              options={[
                { value: 7, label: '7 天' },
                { value: 30, label: '30 天' },
                { value: 60, label: '60 天' },
                { value: 90, label: '90 天' },
              ]}
              onChange={setExpiringDays}
            />
            <Button onClick={() => void loadExpiring()}>刷新</Button>
          </Space>
          <Table<ContractView>
            rowKey="id"
            columns={columns}
            dataSource={expiring}
            scroll={{ x: 1600 }}
            pagination={{ pageSize: 10, showTotal: (count) => `共 ${count} 条` }}
          />
        </>
      )}

      <Typography.Text type="secondary" style={{ display: 'block', marginTop: 12 }}>
        合同正文与签署归合同服务；CRM 只保存台账投影并按到期日生成续约提醒。
      </Typography.Text>

      <Modal
        open={syncOpen}
        title="按客户同步合同投影"
        okText="同步"
        cancelText="取消"
        confirmLoading={syncLoading}
        onOk={() => void handleSync()}
        onCancel={() => setSyncOpen(false)}
      >
        <Space direction="vertical" style={{ width: '100%' }}>
          <InputNumber
            min={1}
            precision={0}
            value={syncCustomerId}
            onChange={(value) => setSyncCustomerId(value ?? null)}
            placeholder="客户 ID"
            style={{ width: '100%' }}
          />
          <Typography.Text type="secondary">
            从合同服务拉取该客户的合同台账投影；重复同步按合同 ID 覆盖，不复制合同正文。
          </Typography.Text>
        </Space>
      </Modal>

      <Modal
        open={Boolean(renewing)}
        title={renewing ? `续约标记：${renewing.contractNo ?? renewing.title ?? ''}` : '续约标记'}
        okText="保存"
        cancelText="取消"
        onOk={() => void handleRenewal()}
        onCancel={() => setRenewing(null)}
      >
        <Form form={renewalForm} layout="vertical">
          <Form.Item
            name="renewalStatus"
            label="续约状态"
            rules={[{ required: true, message: '请选择续约状态' }]}
          >
            <Select options={CONTRACT_RENEWAL_OPTIONS} />
          </Form.Item>
          <Form.Item name="renewalOwnerId" label="续约负责人">
            <InputNumber min={1} precision={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="renewalRemark" label="续约备注">
            <Input.TextArea rows={3} maxLength={500} showCount />
          </Form.Item>
        </Form>
      </Modal>

      <Drawer
        open={Boolean(detail)}
        title={detail ? `合同详情：${detail.contractNo ?? detail.title ?? ''}` : '合同详情'}
        width={720}
        onClose={() => setDetail(null)}
      >
        {detail && (
          <Descriptions bordered size="small" column={2}>
            <Descriptions.Item label="合同编号">{detail.contractNo || '-'}</Descriptions.Item>
            <Descriptions.Item label="合同服务 ID">{detail.contractId}</Descriptions.Item>
            <Descriptions.Item label="标题" span={2}>
              {detail.title || '-'}
            </Descriptions.Item>
            <Descriptions.Item label="客户 ID">{detail.customerId}</Descriptions.Item>
            <Descriptions.Item label="合同类别">{detail.category || '-'}</Descriptions.Item>
            <Descriptions.Item label="合同金额">{formatAmount(detail.amount)}</Descriptions.Item>
            <Descriptions.Item label="币种">{detail.currency || '-'}</Descriptions.Item>
            <Descriptions.Item label="合同状态">{detail.status || '-'}</Descriptions.Item>
            <Descriptions.Item label="续约状态">
              <Tag color={CONTRACT_RENEWAL_COLOR[detail.renewalStatus ?? '']}>
                {CONTRACT_RENEWAL_LABEL[detail.renewalStatus ?? ''] ?? detail.renewalStatus ?? '-'}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="生效日期">{detail.effectiveDate || '-'}</Descriptions.Item>
            <Descriptions.Item label="到期日期">{detail.expireDate || '-'}</Descriptions.Item>
            <Descriptions.Item label="距到期">
              {detail.daysToExpire === null || detail.daysToExpire === undefined
                ? '-'
                : `${detail.daysToExpire} 天`}
            </Descriptions.Item>
            <Descriptions.Item label="最近同步">{formatDateTime(detail.lastSyncAt)}</Descriptions.Item>
            <Descriptions.Item label="续约负责人">{detail.renewalOwnerId ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="续约时间">{formatDateTime(detail.renewalAt)}</Descriptions.Item>
            <Descriptions.Item label="续约备注" span={2}>
              {detail.renewalRemark || '-'}
            </Descriptions.Item>
            <Descriptions.Item label="备注" span={2}>
              {detail.remark || '-'}
            </Descriptions.Item>
          </Descriptions>
        )}
      </Drawer>
    </Card>
  );
}
