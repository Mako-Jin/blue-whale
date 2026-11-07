// 客户应收对账：按客户查看 ERP 应收快照与账龄分桶，支持手动同步当日快照
import { useCallback, useEffect, useState } from 'react';
import { Button, Card, Form, Input, InputNumber, Space, Table, Typography, message } from 'antd';
import type { TableProps } from 'antd';
import {
  pageArSnapshots,
  syncArSnapshots,
  type ArSnapshotPageQuery,
  type ArSnapshotView,
} from '../../api/commerce';
import { hasPermission } from '../../utils/permission';
import { formatDateTime } from '../../utils/dict';

/** 筛选条件 */
interface ArFilterValues {
  keyword?: string;
  customerId?: number | null;
  snapshotDate?: string;
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

/** 客户应收对账页 */
export default function ArPage() {
  const [filterForm] = Form.useForm<ArFilterValues>();
  const [rows, setRows] = useState<ArSnapshotView[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [syncing, setSyncing] = useState(false);
  const [filters, setFilters] = useState<ArSnapshotPageQuery>({});
  const [page, setPage] = useState({ current: 1, size: 10 });

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const result = await pageArSnapshots({ ...filters, current: page.current, size: page.size });
      setRows(result.records);
      setTotal(result.total);
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setLoading(false);
    }
  }, [filters, page]);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  const handleSearch = (values: ArFilterValues) => {
    setFilters({
      keyword: values.keyword || undefined,
      customerId: values.customerId ?? null,
      snapshotDate: values.snapshotDate || undefined,
    });
    setPage((prev) => ({ ...prev, current: 1 }));
  };

  const handleReset = () => {
    filterForm.resetFields();
    setFilters({});
    setPage((prev) => ({ ...prev, current: 1 }));
  };

  const handleSync = async () => {
    setSyncing(true);
    try {
      const result = await syncArSnapshots();
      if (result.message) {
        message.info(result.message);
      } else {
        message.success(`同步完成：成功 ${result.synced}，跳过 ${result.skipped}，失败 ${result.failed}`);
      }
      void loadData();
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setSyncing(false);
    }
  };

  const columns: TableProps<ArSnapshotView>['columns'] = [
    { title: '客户编码', dataIndex: 'customerCode', width: 140, fixed: 'left', render: (value?: string | null) => value || '-' },
    { title: '客户名称', dataIndex: 'customerName', width: 180, render: (value?: string | null) => value || '-' },
    { title: '快照日期', dataIndex: 'snapshotDate', width: 120, render: (value?: string | null) => value || '-' },
    { title: '币种', dataIndex: 'currency', width: 80, render: (value?: string | null) => value || '-' },
    {
      title: '未结单数',
      dataIndex: 'receivableCount',
      width: 90,
      align: 'right',
      render: (value?: number | null) => value ?? '-',
    },
    {
      title: '未结余额',
      dataIndex: 'totalRemaining',
      width: 140,
      align: 'right',
      render: (value?: number | string | null) => formatAmount(value),
    },
    {
      title: '未到期',
      dataIndex: 'notDue',
      width: 130,
      align: 'right',
      render: (value?: number | string | null) => formatAmount(value),
    },
    {
      title: '逾期 1-30',
      dataIndex: 'overdue1To30',
      width: 130,
      align: 'right',
      render: (value?: number | string | null) => formatAmount(value),
    },
    {
      title: '逾期 31-60',
      dataIndex: 'overdue31To60',
      width: 130,
      align: 'right',
      render: (value?: number | string | null) => formatAmount(value),
    },
    {
      title: '逾期 61-90',
      dataIndex: 'overdue61To90',
      width: 130,
      align: 'right',
      render: (value?: number | string | null) => formatAmount(value),
    },
    {
      title: '逾期 90+',
      dataIndex: 'overdueOver90',
      width: 130,
      align: 'right',
      render: (value?: number | string | null) => formatAmount(value),
    },
    { title: '来源', dataIndex: 'source', width: 90, render: (value?: string | null) => value || '-' },
    {
      title: '最近同步',
      dataIndex: 'lastSyncAt',
      width: 170,
      render: (value?: string | null) => formatDateTime(value),
    },
  ];

  return (
    <Card
      title="客户应收对账"
      extra={
        hasPermission('sys:crm:ar:reconcile') && (
          <Button loading={syncing} onClick={() => void handleSync()}>
            同步当日快照
          </Button>
        )
      }
    >
      <Form form={filterForm} layout="inline" onFinish={handleSearch} style={{ marginBottom: 16, rowGap: 12 }}>
        <Form.Item name="keyword">
          <Input placeholder="客户编码 / 名称" allowClear style={{ width: 200 }} />
        </Form.Item>
        <Form.Item name="customerId">
          <InputNumber min={1} precision={0} placeholder="客户 ID" style={{ width: 130 }} />
        </Form.Item>
        <Form.Item name="snapshotDate">
          <Input placeholder="快照日期 yyyy-MM-dd" allowClear style={{ width: 180 }} />
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
      <Table<ArSnapshotView>
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
      <Typography.Text type="secondary" style={{ display: 'block', marginTop: 12 }}>
        应收余额与账龄以 ERP 为准，CRM 仅保留按客户快照；同步失败时逐客户降级，不影响其余客户。
      </Typography.Text>
    </Card>
  );
}
