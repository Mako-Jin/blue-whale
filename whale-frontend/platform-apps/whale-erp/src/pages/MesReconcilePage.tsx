// ERP MES 对账页：下发状态、MES 进度、数量差异、重推/拉取/人工确认入库
import { useCallback, useEffect, useState } from 'react';
import {
  Button,
  DatePicker,
  Descriptions,
  Drawer,
  Form,
  Input,
  InputNumber,
  Select,
  Space,
  Table,
  Tag,
  message,
} from 'antd';
import type { TableProps } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import {
  applyMesCompletion,
  getMesReconcileDetail,
  pageMesReconcile,
  pullMesOrder,
  pushMesOrder,
  type MesCallbackView,
  type MesReconcileDetailView,
  type MesReconcileView,
  type MesTaskView,
} from '../api/mfg';
import { hasPermission } from '../utils/permission';
import { quantity } from '../utils/scm';

const { RangePicker } = DatePicker;

/** MES 同步状态标签颜色 */
const SYNC_COLORS: Record<string, string> = {
  PUSHED: 'green',
  FAILED: 'red',
  NOT_SYNCED: 'default',
};

/** 格式化时间 */
function formatTime(value?: string): string {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-';
}

/** MES 对账页 */
export default function MesReconcilePage() {
  const [data, setData] = useState<MesReconcileView[]>([]);
  const [loading, setLoading] = useState(false);
  const [current, setCurrent] = useState(1);
  const [size, setSize] = useState(20);
  const [total, setTotal] = useState(0);
  const [keyword, setKeyword] = useState('');
  const [range, setRange] = useState<[Dayjs | null, Dayjs | null] | null>(null);
  const [mesSyncStatus, setMesSyncStatus] = useState<string | undefined>();
  const [mesStatus, setMesStatus] = useState<string | undefined>();
  const [version, setVersion] = useState(0);
  const [detail, setDetail] = useState<MesReconcileDetailView | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);
  const [applying, setApplying] = useState<MesReconcileView | null>(null);
  const [applyOpen, setApplyOpen] = useState(false);
  const [form] = Form.useForm<{ quantity: number }>();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageMesReconcile({
        current,
        size,
        keyword,
        mesSyncStatus,
        mesStatus,
        fromDate: range?.[0]?.format('YYYY-MM-DD'),
        toDate: range?.[1]?.format('YYYY-MM-DD'),
      });
      setData(page.records);
      setTotal(page.total);
    } finally {
      setLoading(false);
    }
  }, [current, size, keyword, mesSyncStatus, mesStatus, range]);

  useEffect(() => {
    void load();
  }, [load, version]);

  const resetToFirst = () => {
    setCurrent(1);
    setVersion((value) => value + 1);
  };

  const handlePush = async (record: MesReconcileView) => {
    try {
      const order = await pushMesOrder(record.orderId);
      message.success(
        order.mesSyncStatus === 'PUSHED'
          ? `已下发 MES：${order.mesWorkOrderCode ?? ''}`
          : `下发失败：${order.mesLastError ?? '未知错误'}`,
      );
      setVersion((value) => value + 1);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '下发失败');
    }
  };

  const handlePull = async (record: MesReconcileView) => {
    try {
      const progress = await pullMesOrder(record.orderId);
      message.success(
        progress ? `已拉取 MES 工单：${progress.workOrderCode ?? ''}` : 'MES 未找到对应工单',
      );
      setVersion((value) => value + 1);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '拉取失败');
    }
  };

  const openApply = (record: MesReconcileView) => {
    const diff = (record.mesCompletedQuantity ?? 0) - (record.completedQuantity ?? 0);
    setApplying(record);
    form.setFieldsValue({ quantity: diff > 0 ? diff : 0 });
    setApplyOpen(true);
  };

  const handleApply = async () => {
    const values = await form.validateFields().catch(() => null);
    if (!values || !applying) return;
    try {
      await applyMesCompletion(applying.orderId, values.quantity);
      message.success('已确认完工入库');
      setApplyOpen(false);
      setVersion((value) => value + 1);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '确认入库失败');
    }
  };

  const openDetail = async (record: MesReconcileView) => {
    setDetail(await getMesReconcileDetail(record.orderId));
    setDetailOpen(true);
  };

  const columns: TableProps<MesReconcileView>['columns'] = [
    { title: 'ERP 单号', dataIndex: 'orderNo', key: 'orderNo', width: 160 },
    {
      title: '物料',
      key: 'item',
      render: (_, record) => `${record.itemCode} ${record.itemName ?? ''}`,
    },
    {
      title: '计划量',
      dataIndex: 'plannedQuantity',
      key: 'plannedQuantity',
      width: 100,
      render: quantity,
    },
    {
      title: 'ERP 已完工',
      dataIndex: 'completedQuantity',
      key: 'completedQuantity',
      width: 110,
      render: quantity,
    },
    {
      title: 'MES 状态',
      dataIndex: 'mesStatus',
      key: 'mesStatus',
      width: 110,
      render: (value?: string) => (value ? <Tag color="blue">{value}</Tag> : '-'),
    },
    {
      title: 'MES 报工',
      dataIndex: 'mesReportedQuantity',
      key: 'mesReportedQuantity',
      width: 100,
      render: quantity,
    },
    {
      title: 'MES 合格',
      dataIndex: 'mesQualifiedQuantity',
      key: 'mesQualifiedQuantity',
      width: 100,
      render: quantity,
    },
    {
      title: 'MES 完工',
      dataIndex: 'mesCompletedQuantity',
      key: 'mesCompletedQuantity',
      width: 100,
      render: quantity,
    },
    {
      title: '差异',
      key: 'mismatch',
      width: 100,
      render: (_, record) =>
        record.completedMismatch ? <Tag color="red">数量不一致</Tag> : <Tag color="green">一致</Tag>,
    },
    {
      title: '同步状态',
      dataIndex: 'mesSyncStatus',
      key: 'mesSyncStatus',
      width: 110,
      render: (value?: string) => (
        <Tag color={SYNC_COLORS[value ?? 'NOT_SYNCED'] ?? 'default'}>{value ?? 'NOT_SYNCED'}</Tag>
      ),
    },
    {
      title: '最近回调',
      key: 'callback',
      width: 120,
      render: (_, record) =>
        record.lastCallbackStatus ? (
          <Tag color={record.lastCallbackStatus === 'REJECTED' ? 'red' : 'green'}>
            {record.lastCallbackStatus}
          </Tag>
        ) : (
          '-'
        ),
    },
    {
      title: '失败原因',
      dataIndex: 'mesLastError',
      key: 'mesLastError',
      ellipsis: true,
    },
    {
      title: '操作',
      key: 'action',
      width: 300,
      render: (_, record) => (
        <Space size={4}>
          <Button size="small" onClick={() => void openDetail(record)}>
            详情
          </Button>
          {hasPermission('sys:erp:mes:push') ? (
            <Button size="small" onClick={() => void handlePush(record)}>
              重推
            </Button>
          ) : null}
          {hasPermission('sys:erp:mes:pull') ? (
            <Button size="small" onClick={() => void handlePull(record)}>
              拉取
            </Button>
          ) : null}
          {hasPermission('sys:erp:mes:complete')
          && (record.completedMismatch || record.callbackRejected) ? (
            <Button size="small" type="primary" onClick={() => openApply(record)}>
              确认入库
            </Button>
            ) : null}
        </Space>
      ),
    },
  ];

  const callbackColumns: TableProps<MesCallbackView>['columns'] = [
    {
      title: '时间',
      dataIndex: 'receivedAt',
      key: 'receivedAt',
      width: 160,
      render: formatTime,
    },
    { title: '类型', dataIndex: 'callbackType', key: 'callbackType', width: 100 },
    {
      title: '结果',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (value: string) => (
        <Tag color={value === 'APPLIED' ? 'green' : value === 'REJECTED' ? 'red' : 'default'}>
          {value}
        </Tag>
      ),
    },
    { title: '事件键', dataIndex: 'sourceEventId', key: 'sourceEventId', ellipsis: true },
    { title: '错误', dataIndex: 'error', key: 'error', ellipsis: true },
  ];

  const taskColumns: TableProps<MesTaskView>['columns'] = [
    { title: '任务号', dataIndex: 'taskNo', key: 'taskNo' },
    { title: '序号', dataIndex: 'seqNo', key: 'seqNo', width: 70 },
    { title: '工序', dataIndex: 'operationCode', key: 'operationCode', width: 110 },
    {
      title: '计划',
      dataIndex: 'plannedQuantity',
      key: 'plannedQuantity',
      width: 90,
      render: quantity,
    },
    {
      title: '报工',
      dataIndex: 'reportedQuantity',
      key: 'reportedQuantity',
      width: 90,
      render: quantity,
    },
    {
      title: '合格',
      dataIndex: 'qualifiedQuantity',
      key: 'qualifiedQuantity',
      width: 90,
      render: quantity,
    },
    {
      title: '完工',
      dataIndex: 'completedQuantity',
      key: 'completedQuantity',
      width: 90,
      render: quantity,
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 110,
      render: (value?: string) => (value ? <Tag>{value}</Tag> : '-'),
    },
  ];

  return (
    <>
      <Space wrap style={{ marginBottom: 12 }}>
        <Input
          allowClear
          placeholder="订单号 / 物料"
          style={{ width: 200 }}
          value={keyword}
          onChange={(event) => {
            setKeyword(event.target.value);
            resetToFirst();
          }}
        />
        <RangePicker
          value={range}
          onChange={(value) => {
            setRange(value as [Dayjs | null, Dayjs | null] | null);
            resetToFirst();
          }}
        />
        <Select
          allowClear
          placeholder="下发状态"
          style={{ width: 140 }}
          value={mesSyncStatus}
          onChange={(value) => {
            setMesSyncStatus(value);
            resetToFirst();
          }}
          options={[
            { value: 'NOT_SYNCED', label: '未下发' },
            { value: 'PUSHED', label: '已下发' },
            { value: 'FAILED', label: '下发失败' },
          ]}
        />
        <Select
          allowClear
          placeholder="MES 状态"
          style={{ width: 140 }}
          value={mesStatus}
          onChange={(value) => {
            setMesStatus(value);
            resetToFirst();
          }}
          options={[
            { value: 'RECEIVED', label: '已接收' },
            { value: 'DISPATCHED', label: '已派工' },
            { value: 'IN_PROGRESS', label: '生产中' },
            { value: 'COMPLETED', label: '已完工' },
            { value: 'CLOSED', label: '已关闭' },
            { value: 'CANCELLED', label: '已取消' },
          ]}
        />
        <Button onClick={() => setVersion((value) => value + 1)}>刷新</Button>
      </Space>
      <Table
        rowKey="orderId"
        loading={loading}
        columns={columns}
        dataSource={data}
        scroll={{ x: 1500 }}
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
        title={`MES 对账详情 ${detail?.order.orderNo ?? ''}`}
        width={900}
        open={detailOpen}
        onClose={() => setDetailOpen(false)}
      >
        {detail ? (
          <>
            <Descriptions column={2} size="small" bordered>
              <Descriptions.Item label="ERP 状态">{detail.order.status}</Descriptions.Item>
              <Descriptions.Item label="MES 状态">{detail.progress?.mesStatus ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="MES 工单">
                {detail.order.mesWorkOrderCode ?? detail.progress?.workOrderCode ?? '-'}
              </Descriptions.Item>
              <Descriptions.Item label="最近同步">{formatTime(detail.order.mesSyncedAt)}</Descriptions.Item>
              <Descriptions.Item label="ERP 已完工">{quantity(detail.order.completedQuantity)}</Descriptions.Item>
              <Descriptions.Item label="MES 已完工">
                {quantity(detail.progress?.completedQuantity)}
              </Descriptions.Item>
              <Descriptions.Item label="失败原因" span={2}>
                {detail.order.mesLastError ?? '-'}
              </Descriptions.Item>
            </Descriptions>
            <h4 style={{ marginTop: 16 }}>回调流水</h4>
            <Table
              rowKey="sourceEventId"
              size="small"
              columns={callbackColumns}
              dataSource={detail.callbacks}
              pagination={false}
            />
            <h4 style={{ marginTop: 16 }}>MES 任务进度</h4>
            <Table
              rowKey="taskNo"
              size="small"
              columns={taskColumns}
              dataSource={detail.progress?.tasks ?? []}
              pagination={false}
            />
          </>
        ) : null}
      </Drawer>
      <Drawer
        title="人工确认完工入库"
        width={360}
        open={applyOpen}
        onClose={() => setApplyOpen(false)}
        extra={
          <Button type="primary" onClick={() => void handleApply()}>
            确认入库
          </Button>
        }
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="quantity"
            label="本次入库数量"
            rules={[{ required: true, message: '请输入数量' }]}
          >
            <InputNumber min={0.0001} step={1} style={{ width: '100%' }} />
          </Form.Item>
          <span style={{ color: '#999' }}>
            默认按「MES 完工 − ERP 已完工」带出，可手工调整；入库会按现有完工口径生成库存与凭证。
          </span>
        </Form>
      </Drawer>
    </>
  );
}
