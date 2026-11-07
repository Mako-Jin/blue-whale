// OA 数据报表页：报表目录、条件查询、月度快照与 CSV 导出
import { useCallback, useEffect, useState } from 'react';
import { App, Button, Card, DatePicker, Modal, Select, Space, Table, Typography } from 'antd';
import type { TableProps } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import {
  createReportSnapshot,
  downloadReportCsv,
  getReportCatalog,
  getReportSnapshot,
  pageReportSnapshots,
  runReport,
  type OaReportCatalogItem,
  type OaReportResult,
  type OaReportSnapshot,
} from '../../api/report';
import { hasPermission } from '../../utils/permission';

/** 报表默认编码 */
const DEFAULT_REPORT = 'ATTENDANCE_MONTHLY';

/** 快照每页条数 */
const SNAPSHOT_PAGE_SIZE = 10;

/** 结果行唯一键：各报表至少有一个稳定标识列 */
function rowKeyOf(record: Record<string, unknown>): string {
  const keys = [
    'userId',
    'deptId',
    'leaveTypeCode',
    'vehicleId',
    'sealId',
    'roomId',
    'noticeId',
    'businessType',
    'uploaderId',
  ];
  const value = keys.map((key) => record[key]).find((item) => item !== undefined && item !== null);
  return String(value ?? '');
}

/** 通用报表表格列 */
function buildColumns(columns: OaReportResult['columns']): TableProps<Record<string, unknown>>['columns'] {
  return columns.map((column) => ({
    title: column.label,
    dataIndex: column.key,
    key: column.key,
    ellipsis: true,
  }));
}

/**
 * 数据报表页。
 *
 * @returns 页面
 */
export default function ReportPage() {
  const { message } = App.useApp();
  const [catalog, setCatalog] = useState<OaReportCatalogItem[]>([]);
  const [code, setCode] = useState<string>(DEFAULT_REPORT);
  const [range, setRange] = useState<[Dayjs, Dayjs]>([dayjs().startOf('month'), dayjs()]);
  const [result, setResult] = useState<OaReportResult>();
  const [loading, setLoading] = useState(false);

  const [snapshots, setSnapshots] = useState<OaReportSnapshot[]>([]);
  const [snapshotTotal, setSnapshotTotal] = useState(0);
  const [snapshotPage, setSnapshotPage] = useState(1);
  const [snapshotLoading, setSnapshotLoading] = useState(false);

  const [snapshotOpen, setSnapshotOpen] = useState(false);
  const [snapshotMonth, setSnapshotMonth] = useState<Dayjs>(dayjs());
  const [snapshotSaving, setSnapshotSaving] = useState(false);

  const [viewOpen, setViewOpen] = useState(false);
  const [viewResult, setViewResult] = useState<OaReportResult>();

  const canManage = hasPermission('sys:oa:report:manage');
  const canExport = hasPermission('sys:oa:report:export');

  const loadReport = useCallback(
    async (reportCode: string, nextRange: [Dayjs, Dayjs]) => {
      setLoading(true);
      try {
        const data = await runReport(reportCode, {
          from: nextRange[0].format('YYYY-MM-DD'),
          to: nextRange[1].format('YYYY-MM-DD'),
        });
        setResult(data);
      } catch (error) {
        message.error((error as Error).message);
      } finally {
        setLoading(false);
      }
    },
    [message],
  );

  const loadSnapshots = useCallback(
    async (reportCode: string, current: number) => {
      setSnapshotLoading(true);
      try {
        const data = await pageReportSnapshots({
          code: reportCode,
          current,
          size: SNAPSHOT_PAGE_SIZE,
        });
        setSnapshots(data.records);
        setSnapshotTotal(data.total);
      } catch (error) {
        message.error((error as Error).message);
      } finally {
        setSnapshotLoading(false);
      }
    },
    [message],
  );

  useEffect(() => {
    let active = true;
    getReportCatalog()
      .then((items) => {
        if (!active) {
          return;
        }
        setCatalog(items);
        const first = items[0]?.code ?? DEFAULT_REPORT;
        setCode(first);
        return loadReport(first, [dayjs().startOf('month'), dayjs()]);
      })
      .catch((error: Error) => message.error(error.message));
    return () => {
      active = false;
    };
  }, [loadReport, message]);

  useEffect(() => {
    if (code) {
      loadSnapshots(code, snapshotPage);
    }
  }, [code, snapshotPage, loadSnapshots]);

  const currentItem = catalog.find((item) => item.code === code);

  const handleQuery = () => {
    loadReport(code, range);
  };

  const handleExport = async () => {
    try {
      const file = await downloadReportCsv(code, {
        from: range[0].format('YYYY-MM-DD'),
        to: range[1].format('YYYY-MM-DD'),
      });
      const url = URL.createObjectURL(file.blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = file.filename || `${code}.csv`;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      URL.revokeObjectURL(url);
      message.success('导出成功');
    } catch (error) {
      message.error((error as Error).message);
    }
  };

  const handleGenerateSnapshot = async () => {
    setSnapshotSaving(true);
    try {
      await createReportSnapshot(code, snapshotMonth.format('YYYY-MM'));
      message.success('快照已生成');
      setSnapshotOpen(false);
      if (snapshotPage === 1) {
        await loadSnapshots(code, 1);
      } else {
        setSnapshotPage(1);
      }
    } catch (error) {
      message.error((error as Error).message);
    } finally {
      setSnapshotSaving(false);
    }
  };

  const handleViewSnapshot = async (snapshot: OaReportSnapshot) => {
    try {
      setViewResult(await getReportSnapshot(snapshot.id));
      setViewOpen(true);
    } catch (error) {
      message.error((error as Error).message);
    }
  };

  const snapshotColumns: TableProps<OaReportSnapshot>['columns'] = [
    { title: '统计期间', dataIndex: 'period', key: 'period', width: 110 },
    { title: '数据行数', dataIndex: 'rowCount', key: 'rowCount', width: 100 },
    { title: '生成人', dataIndex: 'generatedByName', key: 'generatedByName', width: 120 },
    {
      title: '生成时间',
      dataIndex: 'generatedAt',
      key: 'generatedAt',
      width: 170,
      render: (value: string) => dayjs(value).format('YYYY-MM-DD HH:mm:ss'),
    },
    {
      title: '操作',
      key: 'action',
      width: 90,
      render: (_, record) => (
        <Button type="link" size="small" onClick={() => handleViewSnapshot(record)}>
          查看
        </Button>
      ),
    },
  ];

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Card
        title="数据报表"
        extra={
          <Space>
            {canManage ? (
              <Button onClick={() => setSnapshotOpen(true)}>生成快照</Button>
            ) : null}
            {canExport ? <Button onClick={handleExport}>导出 CSV</Button> : null}
          </Space>
        }
      >
        <Space wrap style={{ marginBottom: 16 }}>
          <Select
            style={{ width: 220 }}
            value={code}
            options={catalog.map((item) => ({ label: item.title, value: item.code }))}
            onChange={(value) => {
              setCode(value);
              setSnapshotPage(1);
              loadReport(value, range);
            }}
          />
          <DatePicker.RangePicker
            value={range}
            allowClear={false}
            onChange={(values) => {
              if (values?.[0] && values?.[1]) {
                setRange([values[0], values[1]]);
              }
            }}
          />
          <Button type="primary" loading={loading} onClick={handleQuery}>
            查询
          </Button>
        </Space>
        <Typography.Paragraph type="secondary" style={{ marginTop: 0 }}>
          {currentItem?.description ?? '选择报表后按区间实时聚合'}
        </Typography.Paragraph>
        <Table<Record<string, unknown>>
          size="small"
          rowKey={rowKeyOf}
          loading={loading}
          dataSource={result?.rows ?? []}
          columns={buildColumns(result?.columns ?? [])}
          pagination={false}
          scroll={{ x: 'max-content' }}
        />
      </Card>

      <Card title="快照记录">
        <Table<OaReportSnapshot>
          size="small"
          rowKey="id"
          loading={snapshotLoading}
          dataSource={snapshots}
          columns={snapshotColumns}
          pagination={{
            current: snapshotPage,
            pageSize: SNAPSHOT_PAGE_SIZE,
            total: snapshotTotal,
            showSizeChanger: false,
            onChange: (page) => setSnapshotPage(page),
          }}
        />
      </Card>

      <Modal
        title="生成月度快照"
        open={snapshotOpen}
        confirmLoading={snapshotSaving}
        onCancel={() => setSnapshotOpen(false)}
        onOk={handleGenerateSnapshot}
        okText="生成"
      >
        <Space direction="vertical" style={{ width: '100%' }}>
          <Typography.Text>统计期间（同期间重复生成将覆盖旧快照）</Typography.Text>
          <DatePicker
            picker="month"
            value={snapshotMonth}
            allowClear={false}
            onChange={(value) => {
              if (value) {
                setSnapshotMonth(value);
              }
            }}
          />
        </Space>
      </Modal>

      <Modal
        title={viewResult ? `${viewResult.title}（快照）` : '快照内容'}
        open={viewOpen}
        width={960}
        footer={null}
        onCancel={() => setViewOpen(false)}
      >
        <Table<Record<string, unknown>>
          size="small"
          rowKey={rowKeyOf}
          dataSource={viewResult?.rows ?? []}
          columns={buildColumns(viewResult?.columns ?? [])}
          pagination={false}
          scroll={{ x: 'max-content', y: 420 }}
        />
      </Modal>
    </Space>
  );
}
