// OA 报销台账页：全量报销单查询、详情查看、撤销与 ERP 推送重试
import { useCallback, useEffect, useState } from 'react';
import {
  App,
  Button,
  DatePicker,
  Descriptions,
  Drawer,
  Input,
  InputNumber,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
} from 'antd';
import type { TableProps } from 'antd';
import type { Dayjs } from 'dayjs';
import {
  getExpense,
  listCategories,
  pageExpenses,
  retryExpensePush,
  updateExpenseStatus,
  type OaExpense,
  type OaExpenseCategory,
  type OaExpensePushStatus,
  type OaExpenseStatus,
} from '../../api/expense';
import { hasPermission } from '../../utils/permission';

/** 报销状态文案 */
const STATUS_META: Record<OaExpenseStatus, { text: string; color: string }> = {
  PENDING: { text: '待审批', color: 'processing' },
  APPROVED: { text: '已通过', color: 'success' },
  REJECTED: { text: '已驳回', color: 'error' },
  CANCELLED: { text: '已撤销', color: 'default' },
};

/** ERP 推送状态文案 */
const PUSH_META: Record<OaExpensePushStatus, { text: string; color: string }> = {
  NONE: { text: '未推送', color: 'default' },
  PENDING: { text: '待推送', color: 'processing' },
  SENT: { text: '已推送', color: 'success' },
  FAILED: { text: '推送失败', color: 'error' },
};

export default function ExpenseLedgerPage() {
  const { message } = App.useApp();
  const [data, setData] = useState<OaExpense[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [size, setSize] = useState(10);
  const [loading, setLoading] = useState(false);
  const [code, setCode] = useState('');
  const [applicantId, setApplicantId] = useState<number | undefined>();
  const [status, setStatus] = useState<OaExpenseStatus | ''>('');
  const [pushStatus, setPushStatus] = useState<OaExpensePushStatus | ''>('');
  const [dateRange, setDateRange] = useState<[Dayjs, Dayjs] | null>(null);
  const [categories, setCategories] = useState<OaExpenseCategory[]>([]);
  const [detail, setDetail] = useState<OaExpense | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);

  const canPush = hasPermission('sys:oa:expense:push');
  const canEdit = hasPermission('sys:oa:expense:edit');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageExpenses({
        current,
        size,
        code: code.trim() || undefined,
        applicantId,
        status,
        erpPushStatus: pushStatus,
        dateFrom: dateRange?.[0].format('YYYY-MM-DD'),
        dateTo: dateRange?.[1].format('YYYY-MM-DD'),
      });
      setData(page.records);
      setTotal(page.total);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '报销台账查询失败');
      setData([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, [current, size, code, applicantId, status, pushStatus, dateRange, message]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    listCategories()
      .then(setCategories)
      .catch(() => setCategories([]));
  }, []);

  const openDetail = async (record: OaExpense) => {
    try {
      const entity = await getExpense(record.id);
      setDetail(entity);
      setDetailOpen(true);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '报销单详情加载失败');
    }
  };

  const handleCancel = async (record: OaExpense) => {
    try {
      await updateExpenseStatus(record.id, 'CANCELLED');
      message.success('报销单已撤销');
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '报销单撤销失败');
    }
  };

  const handleRetryPush = async (record: OaExpense) => {
    try {
      await retryExpensePush(record.id);
      message.success('ERP 应付单推送成功');
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : 'ERP 应付单推送失败');
    }
  };

  const categoryName = (value: string) =>
    categories.find((item) => item.code === value)?.name ?? value;

  const columns: TableProps<OaExpense>['columns'] = [
    { title: '单号', dataIndex: 'code', key: 'code', width: 170 },
    { title: '申请人', dataIndex: 'applicant', key: 'applicant', width: 110 },
    { title: '事由', dataIndex: 'title', key: 'title', ellipsis: true },
    {
      title: '类别',
      dataIndex: 'categoryCode',
      key: 'categoryCode',
      width: 130,
      render: (value: string) => categoryName(value),
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
    {
      title: 'ERP 推送',
      dataIndex: 'erpPushStatus',
      key: 'erpPushStatus',
      width: 110,
      render: (value: OaExpensePushStatus) => {
        const meta = PUSH_META[value] ?? { text: value, color: 'default' };
        return <Tag color={meta.color}>{meta.text}</Tag>;
      },
    },
    { title: '应付单号', dataIndex: 'erpPayableNo', key: 'erpPayableNo', width: 170 },
    {
      title: '操作',
      key: 'action',
      width: 200,
      render: (_value, record) => (
        <Space size={4}>
          <Button type="link" size="small" onClick={() => void openDetail(record)}>
            详情
          </Button>
          {record.status === 'PENDING' && canEdit ? (
            <Popconfirm title="确认撤销该报销单？" onConfirm={() => void handleCancel(record)}>
              <Button type="link" size="small" danger>
                撤销
              </Button>
            </Popconfirm>
          ) : null}
          {record.status === 'APPROVED' && record.erpPushStatus !== 'SENT' && canPush ? (
            <Button type="link" size="small" onClick={() => void handleRetryPush(record)}>
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
          placeholder="单号"
          style={{ width: 180 }}
          value={code}
          onChange={(event) => {
            setCode(event.target.value);
            setCurrent(1);
          }}
        />
        <InputNumber
          min={1}
          precision={0}
          placeholder="申请人ID"
          style={{ width: 130 }}
          value={applicantId}
          onChange={(value) => {
            setApplicantId(value ?? undefined);
            setCurrent(1);
          }}
        />
        <Select
          allowClear
          placeholder="状态"
          style={{ width: 120 }}
          value={status || undefined}
          options={Object.entries(STATUS_META).map(([value, meta]) => ({ label: meta.text, value }))}
          onChange={(value) => {
            setStatus((value as OaExpenseStatus) ?? '');
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
            setPushStatus((value as OaExpensePushStatus) ?? '');
            setCurrent(1);
          }}
        />
        <DatePicker.RangePicker
          value={dateRange}
          onChange={(value) => {
            setDateRange(value as [Dayjs, Dayjs] | null);
            setCurrent(1);
          }}
        />
        <Button onClick={() => void load()}>刷新</Button>
      </Space>
      <Table<OaExpense>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={data}
        scroll={{ x: 1400 }}
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
      <Drawer
        open={detailOpen}
        width={720}
        title="报销单详情"
        onClose={() => setDetailOpen(false)}
        destroyOnClose
      >
        {detail ? (
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <Descriptions column={2} bordered size="small">
              <Descriptions.Item label="单号">{detail.code}</Descriptions.Item>
              <Descriptions.Item label="申请人">{detail.applicant}</Descriptions.Item>
              <Descriptions.Item label="事由" span={2}>
                {detail.title}
              </Descriptions.Item>
              <Descriptions.Item label="类别">{categoryName(detail.categoryCode)}</Descriptions.Item>
              <Descriptions.Item label="合计">{Number(detail.totalAmount ?? 0).toFixed(2)}</Descriptions.Item>
              <Descriptions.Item label="状态">{STATUS_META[detail.status]?.text ?? detail.status}</Descriptions.Item>
              <Descriptions.Item label="ERP 推送">
                {PUSH_META[detail.erpPushStatus]?.text ?? detail.erpPushStatus}
              </Descriptions.Item>
              <Descriptions.Item label="应付单号" span={2}>
                {detail.erpPayableNo ?? '-'}
              </Descriptions.Item>
            </Descriptions>
            <Table
              size="small"
              rowKey={(record) => record.id ?? record.lineNo ?? record.categoryCode}
              pagination={false}
              dataSource={detail.items ?? []}
              columns={[
                { title: '行号', dataIndex: 'lineNo', width: 70 },
                { title: '类别', dataIndex: 'categoryCode', render: (value: string) => categoryName(value) },
                { title: '日期', dataIndex: 'expenseDate', width: 110 },
                { title: '金额', dataIndex: 'amount', width: 100, align: 'right' },
                { title: '说明', dataIndex: 'description', ellipsis: true },
              ]}
            />
            <Table
              size="small"
              rowKey={(record) => record.id ?? record.invoiceNo}
              pagination={false}
              dataSource={detail.invoices ?? []}
              columns={[
                { title: '发票代码', dataIndex: 'invoiceCode', width: 130 },
                { title: '发票号', dataIndex: 'invoiceNo', width: 160 },
                { title: '开票日期', dataIndex: 'invoiceDate', width: 110 },
                { title: '金额', dataIndex: 'amount', width: 100, align: 'right' },
              ]}
            />
          </Space>
        ) : null}
      </Drawer>
    </Space>
  );
}
