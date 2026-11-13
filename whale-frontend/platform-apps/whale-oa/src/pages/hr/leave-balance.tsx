// OA 额度台账页：额度筛选查询、初始化/调整弹窗与额度流水抽屉
import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  App,
  Button,
  Drawer,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Table,
  Tag,
} from 'antd';
import type { TableProps } from 'antd';
import dayjs from 'dayjs';
import {
  adjustLeaveBalance,
  initLeaveBalance,
  listEnabledLeaveTypes,
  pageLeaveBalanceLogs,
  pageLeaveBalances,
  type LeaveBalanceAction,
  type LeaveBalanceLogView,
  type LeaveBalanceView,
  type LeaveTypeView,
} from '../../api/hr';
import { hasPermission } from '../../utils/permission';

/** 流水动作文案与颜色 */
const ACTION_META: Record<LeaveBalanceAction, { text: string; color: string }> = {
  INIT: { text: '初始化', color: 'blue' },
  ADJUST: { text: '调整', color: 'purple' },
  FREEZE: { text: '冻结', color: 'processing' },
  RELEASE: { text: '释放', color: 'warning' },
  DEDUCT: { text: '扣减', color: 'success' },
  GRANT: { text: '增补', color: 'cyan' },
};

/** 初始化表单字段 */
interface InitFormValues {
  userId: number;
  year: number;
}

/** 调整表单字段 */
interface AdjustFormValues {
  totalDays: number;
  remark?: string;
}

export default function LeaveBalanceCenter() {
  const { message } = App.useApp();
  const [data, setData] = useState<LeaveBalanceView[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [size, setSize] = useState(10);
  const [loading, setLoading] = useState(false);
  const [userId, setUserId] = useState<number | undefined>();
  const [leaveTypeId, setLeaveTypeId] = useState<number | undefined>();
  const [year, setYear] = useState<number | undefined>(dayjs().year());
  const [types, setTypes] = useState<LeaveTypeView[]>([]);

  const [initOpen, setInitOpen] = useState(false);
  const [adjusting, setAdjusting] = useState<LeaveBalanceView | null>(null);
  const [logBalance, setLogBalance] = useState<LeaveBalanceView | null>(null);
  const [logs, setLogs] = useState<LeaveBalanceLogView[]>([]);
  const [logTotal, setLogTotal] = useState(0);
  const [logCurrent, setLogCurrent] = useState(1);
  const [logSize, setLogSize] = useState(10);
  const [logLoading, setLogLoading] = useState(false);
  const [initForm] = Form.useForm<InitFormValues>();
  const [adjustForm] = Form.useForm<AdjustFormValues>();

  const canEdit = hasPermission('sys:oa:hr:balance:edit');

  const typeMap = useMemo(() => new Map(types.map((item) => [item.id, item.name])), [types]);

  useEffect(() => {
    listEnabledLeaveTypes()
      .then((items) => setTypes(items))
      .catch(() => setTypes([]));
  }, []);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageLeaveBalances({
        current,
        size,
        userId,
        leaveTypeId,
        balanceYear: year,
      });
      setData(page.records);
      setTotal(page.total);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '额度查询失败');
      setData([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, [current, size, userId, leaveTypeId, year, message]);

  useEffect(() => {
    void load();
  }, [load]);

  const loadLogs = useCallback(async () => {
    if (!logBalance) {
      return;
    }
    setLogLoading(true);
    try {
      const page = await pageLeaveBalanceLogs(logBalance.id, {
        current: logCurrent,
        size: logSize,
      });
      setLogs(page.records);
      setLogTotal(page.total);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '额度流水加载失败');
      setLogs([]);
      setLogTotal(0);
    } finally {
      setLogLoading(false);
    }
  }, [logBalance, logCurrent, logSize, message]);

  useEffect(() => {
    if (logBalance) {
      void loadLogs();
    } else {
      setLogs([]);
      setLogTotal(0);
    }
  }, [logBalance, loadLogs]);

  const openInit = () => {
    initForm.resetFields();
    initForm.setFieldsValue({ year: dayjs().year() });
    setInitOpen(true);
  };

  const handleInit = async () => {
    const values = await initForm.validateFields();
    try {
      await initLeaveBalance({ userId: values.userId, year: values.year });
      message.success('额度初始化完成');
      setInitOpen(false);
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '额度初始化失败');
    }
  };

  const openAdjust = (record: LeaveBalanceView) => {
    adjustForm.resetFields();
    adjustForm.setFieldsValue({ totalDays: record.totalDays, remark: record.remark });
    setAdjusting(record);
  };

  const handleAdjust = async () => {
    if (!adjusting) {
      return;
    }
    const values = await adjustForm.validateFields();
    try {
      await adjustLeaveBalance(adjusting.id, {
        totalDays: values.totalDays,
        remark: values.remark?.trim() || undefined,
      });
      message.success('额度调整成功');
      setAdjusting(null);
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '额度调整失败');
    }
  };

  const columns: TableProps<LeaveBalanceView>['columns'] = [
    { title: '用户ID', dataIndex: 'userId', key: 'userId', width: 140 },
    {
      title: '假期类型',
      dataIndex: 'leaveTypeId',
      key: 'leaveTypeId',
      width: 160,
      render: (value: number) => typeMap.get(value) ?? `#${value}`,
    },
    { title: '年份', dataIndex: 'balanceYear', key: 'balanceYear', width: 90 },
    { title: '总额', dataIndex: 'totalDays', key: 'totalDays', width: 100 },
    { title: '已用', dataIndex: 'usedDays', key: 'usedDays', width: 100 },
    { title: '冻结', dataIndex: 'frozenDays', key: 'frozenDays', width: 100 },
    { title: '可用', dataIndex: 'availableDays', key: 'availableDays', width: 100 },
    {
      title: '操作',
      key: 'action',
      width: 140,
      render: (_value, record) => (
        <Space size={4}>
          {canEdit ? (
            <Button type="link" size="small" onClick={() => openAdjust(record)}>
              调整额度
            </Button>
          ) : null}
          <Button
            type="link"
            size="small"
            onClick={() => {
              setLogCurrent(1);
              setLogBalance(record);
            }}
          >
            流水
          </Button>
        </Space>
      ),
    },
  ];

  const logColumns: TableProps<LeaveBalanceLogView>['columns'] = [
    {
      title: '动作',
      dataIndex: 'action',
      key: 'action',
      width: 90,
      render: (value: LeaveBalanceAction) => {
        const meta = ACTION_META[value] ?? { text: value, color: 'default' };
        return <Tag color={meta.color}>{meta.text}</Tag>;
      },
    },
    { title: '变动天数', dataIndex: 'days', key: 'days', width: 90 },
    {
      title: '总额度',
      key: 'totalRange',
      width: 130,
      render: (_value, record) => `${record.beforeTotal} → ${record.afterTotal}`,
    },
    {
      title: '已使用',
      key: 'usedRange',
      width: 130,
      render: (_value, record) => `${record.beforeUsed} → ${record.afterUsed}`,
    },
    {
      title: '冻结',
      key: 'frozenRange',
      width: 130,
      render: (_value, record) => `${record.beforeFrozen} → ${record.afterFrozen}`,
    },
    {
      title: '时间',
      dataIndex: 'createTime',
      key: 'createTime',
      width: 170,
      render: (value?: string) => value || '-',
    },
    { title: '备注', dataIndex: 'remark', key: 'remark', ellipsis: true },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <InputNumber
          placeholder="用户ID"
          style={{ width: 160 }}
          min={1}
          precision={0}
          value={userId}
          onChange={(value) => {
            setUserId(value ?? undefined);
            setCurrent(1);
          }}
        />
        <Select
          allowClear
          placeholder="假期类型"
          style={{ width: 160 }}
          value={leaveTypeId}
          options={types.map((item) => ({ label: item.name, value: item.id }))}
          onChange={(value) => {
            setLeaveTypeId(value ?? undefined);
            setCurrent(1);
          }}
        />
        <InputNumber
          placeholder="年份"
          style={{ width: 120 }}
          min={2000}
          max={2100}
          precision={0}
          value={year}
          onChange={(value) => {
            setYear(value ?? undefined);
            setCurrent(1);
          }}
        />
        {canEdit ? (
          <Button type="primary" onClick={openInit}>
            初始化额度
          </Button>
        ) : null}
        <Button onClick={() => void load()}>刷新</Button>
      </Space>
      <Table<LeaveBalanceView>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={data}
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
        open={initOpen}
        title="初始化额度"
        onOk={() => void handleInit()}
        onCancel={() => setInitOpen(false)}
        destroyOnClose
      >
        <Form form={initForm} layout="vertical">
          <Form.Item
            name="userId"
            label="用户ID"
            rules={[{ required: true, message: '请输入用户ID' }]}
          >
            <InputNumber style={{ width: '100%' }} min={1} precision={0} />
          </Form.Item>
          <Form.Item
            name="year"
            label="额度年份"
            rules={[{ required: true, message: '请输入额度年份' }]}
          >
            <InputNumber style={{ width: '100%' }} min={2000} max={2100} precision={0} />
          </Form.Item>
        </Form>
      </Modal>
      <Modal
        open={Boolean(adjusting)}
        title="调整额度"
        onOk={() => void handleAdjust()}
        onCancel={() => setAdjusting(null)}
        destroyOnClose
      >
        <Form form={adjustForm} layout="vertical">
          <Form.Item
            name="totalDays"
            label="新的总额度"
            rules={[{ required: true, message: '请输入总额度' }]}
          >
            <InputNumber style={{ width: '100%' }} min={0} precision={2} />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} maxLength={255} showCount placeholder="选填" />
          </Form.Item>
        </Form>
      </Modal>
      <Drawer
        open={Boolean(logBalance)}
        width={900}
        title={logBalance ? `额度流水 - 用户 ${logBalance.userId}` : '额度流水'}
        onClose={() => setLogBalance(null)}
      >
        <Table<LeaveBalanceLogView>
          rowKey="id"
          size="small"
          loading={logLoading}
          columns={logColumns}
          dataSource={logs}
          scroll={{ x: 900 }}
          pagination={{
            current: logCurrent,
            pageSize: logSize,
            total: logTotal,
            onChange: (page, pageSize) => {
              setLogCurrent(page);
              setLogSize(pageSize);
            },
          }}
        />
      </Drawer>
    </Space>
  );
}
