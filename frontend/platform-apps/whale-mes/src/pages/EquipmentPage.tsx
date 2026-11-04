// MES 设备页：设备台账 / 状态与停机 / 保养 / 维修 / 备件 / OEE
import { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Card,
  Col,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Row,
  Select,
  Space,
  Statistic,
  Table,
  Tabs,
  Tag,
  Typography,
  message,
} from 'antd';
import type { TableProps, TabsProps } from 'antd';
import dayjs from 'dayjs';
import type { Dayjs } from 'dayjs';
import {
  acceptMesMaintenanceOrder,
  assignMesRepair,
  changeMesEquipmentStatus,
  closeMesRepair,
  completeMesMaintenanceOrder,
  computeMesOee,
  createMesMaintenanceOrder,
  createMesRepair,
  fetchMesEquipmentGate,
  pageMesEquipmentDowntimes,
  pageMesEquipmentStatusEvents,
  pageMesEquipments,
  pageMesMaintenanceOrders,
  pageMesMaintenancePlans,
  pageMesOee,
  pageMesRepairs,
  pageMesSparePartMovements,
  pageMesSparePartStocks,
  pageMesSpareParts,
  saveMesEquipment,
  saveMesMaintenancePlan,
  saveMesSparePart,
  startMesMaintenanceOrder,
  startMesRepair,
  stockInMesSparePart,
  stockOutMesSparePart,
  verifyMesRepair,
  type MesEquipment,
  type MesEquipmentDowntime,
  type MesEquipmentInput,
  type MesEquipmentStatus,
  type MesEquipmentStatusEvent,
  type MesMaintenanceOrder,
  type MesMaintenancePlan,
  type MesOeeSnapshot,
  type MesRepairOrder,
  type MesSparePart,
  type MesSparePartMovement,
  type MesSparePartStock,
} from '../api/mes';
import { hasPermission } from '../utils/permission';

/** 设备页签 */
export type EquipmentTab = 'ledger' | 'status' | 'maintenance' | 'repair' | 'spare' | 'oee';

/** 设备状态文案 */
const EQUIPMENT_STATUS_LABEL: Record<string, string> = {
  RUNNING: '运行',
  IDLE: '待机',
  FAULT: '故障',
  MAINTENANCE: '保养中',
  OFFLINE: '离线',
};

/** 设备状态颜色 */
const EQUIPMENT_STATUS_COLOR: Record<string, string> = {
  RUNNING: 'green',
  IDLE: 'blue',
  FAULT: 'red',
  MAINTENANCE: 'orange',
  OFFLINE: 'default',
};

/** 设备状态筛选项 */
const EQUIPMENT_STATUS_FILTER = Object.entries(EQUIPMENT_STATUS_LABEL).map(([value, label]) => ({
  value,
  label,
}));

/** 保养工单状态文案 */
const MAINTENANCE_STATUS_LABEL: Record<string, string> = {
  PLANNED: '已计划',
  DUE: '到期',
  OVERDUE: '逾期',
  IN_PROGRESS: '执行中',
  COMPLETED: '已完工',
  ACCEPTED: '已验收',
};

/** 保养工单状态颜色 */
const MAINTENANCE_STATUS_COLOR: Record<string, string> = {
  PLANNED: 'blue',
  DUE: 'orange',
  OVERDUE: 'red',
  IN_PROGRESS: 'processing',
  COMPLETED: 'cyan',
  ACCEPTED: 'green',
};

/** 维修工单状态文案 */
const REPAIR_STATUS_LABEL: Record<string, string> = {
  REPORTED: '已报修',
  ASSIGNED: '已派工',
  REPAIRING: '维修中',
  VERIFIED: '已验证',
  CLOSED: '已关闭',
};

/** 维修工单状态颜色 */
const REPAIR_STATUS_COLOR: Record<string, string> = {
  REPORTED: 'red',
  ASSIGNED: 'orange',
  REPAIRING: 'processing',
  VERIFIED: 'cyan',
  CLOSED: 'green',
};

/** 保养类型文案 */
const MAINTENANCE_TYPE_LABEL: Record<string, string> = {
  ROUTINE: '例行保养',
  PREVENTIVE: '预防性保养',
  CALIBRATION: '校准',
};

/** 时间展示：ISO 转 `YYYY-MM-DD HH:mm` */
function formatTime(value?: string): string {
  return value ? String(value).replace('T', ' ').slice(0, 16) : '-';
}

/** 比率展示：小数转百分比 */
function formatRate(value?: number | string): string {
  const number = Number(value);
  return Number.isFinite(number) ? `${(number * 100).toFixed(2)}%` : '-';
}

/** 设备下拉选项 */
function equipmentOptions(equipment: MesEquipment[]) {
  return equipment.map((item) => ({
    value: item.id,
    label: `${item.equipmentCode} ${item.equipmentName}`.trim(),
  }));
}

/** 台账号筛选与编辑 */
function LedgerPanel({ onChanged }: { onChanged: () => Promise<void> }) {
  const [rows, setRows] = useState<MesEquipment[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<MesEquipmentStatus | undefined>(undefined);
  const [editing, setEditing] = useState<MesEquipment | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const [statusTarget, setStatusTarget] = useState<MesEquipment | null>(null);
  const [form] = Form.useForm<MesEquipmentInput>();
  const [statusForm] = Form.useForm<{ toStatus: MesEquipmentStatus; operator?: string; reasonName?: string; remark?: string }>();
  const canManage = hasPermission('sys:mes:equipment:manage');
  const canChangeStatus = hasPermission('sys:mes:equipment:status');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageMesEquipments({ equipmentCode: keyword || undefined, status });
      setRows(page.records);
    } finally {
      setLoading(false);
    }
  }, [keyword, status]);

  useEffect(() => {
    void load();
  }, [load]);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ enabled: 1 });
    setFormOpen(true);
  };

  const openEdit = (record: MesEquipment) => {
    setEditing(record);
    form.setFieldsValue(record);
    setFormOpen(true);
  };

  const submitForm = async () => {
    const values = await form.validateFields();
    await saveMesEquipment({ ...values, id: editing?.id });
    message.success('设备已保存');
    setFormOpen(false);
    await Promise.all([load(), onChanged()]);
  };

  const submitStatus = async () => {
    if (!statusTarget) {
      return;
    }
    const values = await statusForm.validateFields();
    await changeMesEquipmentStatus(statusTarget.id, values);
    message.success(`设备已切换为${EQUIPMENT_STATUS_LABEL[values.toStatus] ?? values.toStatus}`);
    setStatusTarget(null);
    statusForm.resetFields();
    await Promise.all([load(), onChanged()]);
  };

  const checkGate = async (record: MesEquipment) => {
    const gate = await fetchMesEquipmentGate(record.equipmentCode);
    if (gate.passed) {
      message.success(`门禁通过：${record.equipmentCode} 可开工`);
    } else {
      message.warning(`门禁阻断：${gate.message ?? record.equipmentCode}`);
    }
  };

  const columns: TableProps<MesEquipment>['columns'] = [
    { title: '设备编码', dataIndex: 'equipmentCode', key: 'equipmentCode', width: 140 },
    { title: '设备名称', dataIndex: 'equipmentName', key: 'equipmentName', width: 160 },
    { title: '类型', dataIndex: 'equipmentType', key: 'equipmentType', width: 110 },
    { title: '产线', dataIndex: 'lineCode', key: 'lineCode', width: 110 },
    { title: '型号', dataIndex: 'modelNo', key: 'modelNo', width: 120 },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (value: string) => (
        <Tag color={EQUIPMENT_STATUS_COLOR[value]}>{EQUIPMENT_STATUS_LABEL[value] ?? value}</Tag>
      ),
    },
    {
      title: '启用',
      dataIndex: 'enabled',
      key: 'enabled',
      width: 80,
      render: (value: number) => (value === 1 ? <Tag color="green">启用</Tag> : <Tag>停用</Tag>),
    },
    { title: '保养周期', dataIndex: 'maintenanceCycleDays', key: 'maintenanceCycleDays', width: 100 },
    {
      title: '操作',
      key: 'action',
      width: 220,
      render: (_, record) => (
        <Space size="small">
          {canManage && (
            <Button type="link" size="small" onClick={() => openEdit(record)}>
              编辑
            </Button>
          )}
          {canChangeStatus && (
            <Button
              type="link"
              size="small"
              onClick={() => {
                setStatusTarget(record);
                statusForm.setFieldsValue({ toStatus: record.status });
              }}
            >
              状态
            </Button>
          )}
          <Button type="link" size="small" onClick={() => void checkGate(record)}>
            门禁
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <Input
          allowClear
          placeholder="设备编码"
          style={{ width: 180 }}
          value={keyword}
          onChange={(event) => setKeyword(event.target.value)}
        />
        <Select
          allowClear
          placeholder="状态"
          style={{ width: 150 }}
          value={status}
          options={EQUIPMENT_STATUS_FILTER}
          onChange={(value) => setStatus(value as MesEquipmentStatus | undefined)}
        />
        <Button onClick={() => void load()}>查询</Button>
        {canManage && (
          <Button type="primary" onClick={openCreate}>
            新增设备
          </Button>
        )}
      </Space>
      <Table<MesEquipment>
        rowKey="id"
        size="small"
        loading={loading}
        columns={columns}
        dataSource={rows}
        pagination={{ pageSize: 10, showSizeChanger: true }}
      />

      <Modal
        title={editing ? '编辑设备' : '新增设备'}
        open={formOpen}
        onCancel={() => setFormOpen(false)}
        onOk={() => void submitForm()}
        destroyOnHidden
        width={720}
      >
        <Form form={form} layout="vertical">
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="equipmentCode" label="设备编码" rules={[{ required: true, message: '请输入设备编码' }]}>
                <Input />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="equipmentName" label="设备名称" rules={[{ required: true, message: '请输入设备名称' }]}>
                <Input />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="equipmentType" label="设备类型">
                <Input placeholder="如 CNC / PRESS" />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="lineCode" label="产线编码">
                <Input />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="stationCode" label="工位编码">
                <Input />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="workCenterCode" label="工作中心">
                <Input />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="modelNo" label="型号">
                <Input />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="manufacturer" label="制造商">
                <Input />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="maintenanceCycleDays" label="保养周期（天）">
                <InputNumber min={1} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="enabled" label="是否启用" initialValue={1}>
                <Select
                  options={[
                    { value: 1, label: '启用' },
                    { value: 0, label: '停用' },
                  ]}
                />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="statusBaseline" label="状态基线">
                <Input />
              </Form.Item>
            </Col>
            <Col span={24}>
              <Form.Item name="remark" label="备注">
                <Input.TextArea rows={2} />
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Modal>

      <Modal
        title={`设备状态变更：${statusTarget?.equipmentCode ?? ''}`}
        open={statusTarget !== null}
        onCancel={() => {
          setStatusTarget(null);
          statusForm.resetFields();
        }}
        onOk={() => void submitStatus()}
        destroyOnHidden
      >
        <Form form={statusForm} layout="vertical">
          <Form.Item name="toStatus" label="目标状态" rules={[{ required: true, message: '请选择目标状态' }]}>
            <Select options={EQUIPMENT_STATUS_FILTER} />
          </Form.Item>
          <Form.Item name="operator" label="操作人">
            <Input />
          </Form.Item>
          <Form.Item name="reasonName" label="原因">
            <Input />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}

/** 状态事件与停机记录 */
function StatusPanel({ equipment }: { equipment: MesEquipment[] }) {
  const [events, setEvents] = useState<MesEquipmentStatusEvent[]>([]);
  const [downtimes, setDowntimes] = useState<MesEquipmentDowntime[]>([]);
  const [equipmentId, setEquipmentId] = useState<string | undefined>(undefined);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [eventPage, downtimePage] = await Promise.all([
        pageMesEquipmentStatusEvents({ equipmentId }),
        pageMesEquipmentDowntimes({ equipmentId }),
      ]);
      setEvents(eventPage.records);
      setDowntimes(downtimePage.records);
    } finally {
      setLoading(false);
    }
  }, [equipmentId]);

  useEffect(() => {
    void load();
  }, [load]);

  const eventColumns: TableProps<MesEquipmentStatusEvent>['columns'] = [
    { title: '设备编码', dataIndex: 'equipmentCode', key: 'equipmentCode', width: 140 },
    { title: '变更前', dataIndex: 'fromStatus', key: 'fromStatus', width: 100 },
    {
      title: '变更后',
      dataIndex: 'toStatus',
      key: 'toStatus',
      width: 100,
      render: (value: string) => (
        <Tag color={EQUIPMENT_STATUS_COLOR[value]}>{EQUIPMENT_STATUS_LABEL[value] ?? value}</Tag>
      ),
    },
    { title: '事件类型', dataIndex: 'eventType', key: 'eventType', width: 130 },
    {
      title: '发生时间',
      dataIndex: 'occurredAt',
      key: 'occurredAt',
      width: 160,
      render: (value) => formatTime(value as string | undefined),
    },
    { title: '操作人', dataIndex: 'operator', key: 'operator', width: 110 },
    { title: '备注', dataIndex: 'remark', key: 'remark' },
  ];

  const downtimeColumns: TableProps<MesEquipmentDowntime>['columns'] = [
    { title: '停机单号', dataIndex: 'downtimeNo', key: 'downtimeNo', width: 170 },
    { title: '设备编码', dataIndex: 'equipmentCode', key: 'equipmentCode', width: 140 },
    { title: '原因', dataIndex: 'reasonName', key: 'reasonName', width: 140 },
    {
      title: '计划停机',
      dataIndex: 'plannedFlag',
      key: 'plannedFlag',
      width: 100,
      render: (value: number) => (value === 1 ? <Tag color="blue">计划</Tag> : <Tag color="red">非计划</Tag>),
    },
    {
      title: '开始时间',
      dataIndex: 'startAt',
      key: 'startAt',
      width: 160,
      render: (value) => formatTime(value as string | undefined),
    },
    {
      title: '结束时间',
      dataIndex: 'endAt',
      key: 'endAt',
      width: 160,
      render: (value) => formatTime(value as string | undefined),
    },
    { title: '时长(分)', dataIndex: 'durationMinutes', key: 'durationMinutes', width: 100 },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <Select
          allowClear
          showSearch
          optionFilterProp="label"
          placeholder="设备"
          style={{ width: 240 }}
          value={equipmentId}
          options={equipmentOptions(equipment)}
          onChange={(value) => setEquipmentId(value)}
        />
        <Button onClick={() => void load()}>刷新</Button>
      </Space>
      <Card title="状态事件（只追加）" size="small" loading={loading}>
        <Table<MesEquipmentStatusEvent>
          rowKey="id"
          size="small"
          columns={eventColumns}
          dataSource={events}
          pagination={{ pageSize: 5, showSizeChanger: true }}
        />
      </Card>
      <Card title="停机记录" size="small" loading={loading}>
        <Table<MesEquipmentDowntime>
          rowKey="downtimeNo"
          size="small"
          columns={downtimeColumns}
          dataSource={downtimes}
          pagination={{ pageSize: 5, showSizeChanger: true }}
        />
      </Card>
    </Space>
  );
}

/** 保养计划与工单 */
function MaintenancePanel({ equipment }: { equipment: MesEquipment[] }) {
  const [plans, setPlans] = useState<MesMaintenancePlan[]>([]);
  const [orders, setOrders] = useState<MesMaintenanceOrder[]>([]);
  const [loading, setLoading] = useState(false);
  const [planOpen, setPlanOpen] = useState(false);
  const [orderOpen, setOrderOpen] = useState(false);
  const [completing, setCompleting] = useState<MesMaintenanceOrder | null>(null);
  const [accepting, setAccepting] = useState<MesMaintenanceOrder | null>(null);
  const [planForm] = Form.useForm();
  const [orderForm] = Form.useForm();
  const [completeForm] = Form.useForm<{ completedBy?: string; checklistText: string; remark?: string }>();
  const [acceptForm] = Form.useForm<{ acceptedBy?: string; conclusion: string }>();
  const canManage = hasPermission('sys:mes:equipment:maintenance');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [planPage, orderPage] = await Promise.all([
        pageMesMaintenancePlans(),
        pageMesMaintenanceOrders(),
      ]);
      setPlans(planPage.records);
      setOrders(orderPage.records);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const submitPlan = async () => {
    const values = await planForm.validateFields();
    await saveMesMaintenancePlan(values);
    message.success('保养计划已保存');
    setPlanOpen(false);
    planForm.resetFields();
    await load();
  };

  const submitOrder = async () => {
    const values = await orderForm.validateFields();
    await createMesMaintenanceOrder({
      planId: values.planId,
      dueAt: values.dueAt ? (values.dueAt as Dayjs).format('YYYY-MM-DDTHH:mm:ss') : undefined,
      assignee: values.assignee,
      remark: values.remark,
    });
    message.success('保养工单已生成');
    setOrderOpen(false);
    orderForm.resetFields();
    await load();
  };

  const startOrder = async (record: MesMaintenanceOrder) => {
    await startMesMaintenanceOrder(record.id, record.assignee);
    message.success('保养已开始');
    await load();
  };

  const openComplete = (record: MesMaintenanceOrder) => {
    setCompleting(record);
    const items = (record.checklist ?? '')
      .split(';')
      .map((item) => item.trim())
      .filter((item) => item !== '')
      .map((item) => `${item}=正常`);
    completeForm.setFieldsValue({ completedBy: record.assignee, checklistText: items.join('\n') });
  };

  const submitComplete = async () => {
    if (!completing) {
      return;
    }
    const values = await completeForm.validateFields();
    await completeMesMaintenanceOrder(completing.id, {
      completedBy: values.completedBy,
      checklistResults: values.checklistText
        .split('\n')
        .map((line) => line.trim())
        .filter((line) => line !== ''),
      remark: values.remark,
    });
    message.success('保养已完工');
    setCompleting(null);
    await load();
  };

  const submitAccept = async () => {
    if (!accepting) {
      return;
    }
    const values = await acceptForm.validateFields();
    await acceptMesMaintenanceOrder(accepting.id, values);
    message.success('保养已验收');
    setAccepting(null);
    await load();
  };

  const planColumns: TableProps<MesMaintenancePlan>['columns'] = [
    { title: '计划编号', dataIndex: 'planNo', key: 'planNo', width: 170 },
    { title: '设备编码', dataIndex: 'equipmentCode', key: 'equipmentCode', width: 140 },
    {
      title: '类型',
      dataIndex: 'maintenanceType',
      key: 'maintenanceType',
      width: 120,
      render: (value: string) => MAINTENANCE_TYPE_LABEL[value] ?? value,
    },
    { title: '周期(天)', dataIndex: 'cycleDays', key: 'cycleDays', width: 90 },
    { title: '保养项', dataIndex: 'checklist', key: 'checklist' },
    {
      title: '下次到期',
      dataIndex: 'nextDueAt',
      key: 'nextDueAt',
      width: 160,
      render: (value) => formatTime(value as string | undefined),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (value: string) => <Tag color={value === 'ENABLED' ? 'green' : 'default'}>{value === 'ENABLED' ? '启用' : value}</Tag>,
    },
  ];

  const orderColumns: TableProps<MesMaintenanceOrder>['columns'] = [
    { title: '工单编号', dataIndex: 'orderNo', key: 'orderNo', width: 170 },
    { title: '设备编码', dataIndex: 'equipmentCode', key: 'equipmentCode', width: 140 },
    {
      title: '到期时间',
      dataIndex: 'dueAt',
      key: 'dueAt',
      width: 160,
      render: (value) => formatTime(value as string | undefined),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (value: string) => (
        <Tag color={MAINTENANCE_STATUS_COLOR[value]}>{MAINTENANCE_STATUS_LABEL[value] ?? value}</Tag>
      ),
    },
    { title: '执行人', dataIndex: 'assignee', key: 'assignee', width: 100 },
    { title: '保养结论', dataIndex: 'checklistResult', key: 'checklistResult' },
    {
      title: '操作',
      key: 'action',
      width: 180,
      render: (_, record) => {
        if (!canManage) {
          return '-';
        }
        if (['PLANNED', 'DUE', 'OVERDUE'].includes(record.status)) {
          return (
            <Button type="link" size="small" onClick={() => void startOrder(record)}>
              开始
            </Button>
          );
        }
        if (record.status === 'IN_PROGRESS') {
          return (
            <Button type="link" size="small" onClick={() => openComplete(record)}>
              完工
            </Button>
          );
        }
        if (record.status === 'COMPLETED') {
          return (
            <Button
              type="link"
              size="small"
              onClick={() => {
                setAccepting(record);
                acceptForm.setFieldsValue({ acceptedBy: record.completedBy, conclusion: '保养合格' });
              }}
            >
              验收
            </Button>
          );
        }
        return '-';
      },
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <Button onClick={() => void load()}>刷新</Button>
        {canManage && (
          <>
            <Button
              type="primary"
              onClick={() => {
                planForm.resetFields();
                setPlanOpen(true);
              }}
            >
              新增保养计划
            </Button>
            <Button
              onClick={() => {
                orderForm.resetFields();
                setOrderOpen(true);
              }}
            >
              生成保养工单
            </Button>
          </>
        )}
      </Space>
      <Card title="保养计划" size="small" loading={loading}>
        <Table<MesMaintenancePlan>
          rowKey="id"
          size="small"
          columns={planColumns}
          dataSource={plans}
          pagination={{ pageSize: 5, showSizeChanger: true }}
        />
      </Card>
      <Card title="保养工单" size="small" loading={loading}>
        <Table<MesMaintenanceOrder>
          rowKey="id"
          size="small"
          columns={orderColumns}
          dataSource={orders}
          pagination={{ pageSize: 5, showSizeChanger: true }}
        />
      </Card>

      <Modal
        title="新增保养计划"
        open={planOpen}
        onCancel={() => setPlanOpen(false)}
        onOk={() => void submitPlan()}
        destroyOnHidden
      >
        <Form form={planForm} layout="vertical" initialValues={{ maintenanceType: 'ROUTINE', status: 'ENABLED', cycleDays: 30 }}>
          <Form.Item name="equipmentId" label="设备" rules={[{ required: true, message: '请选择设备' }]}>
            <Select showSearch optionFilterProp="label" options={equipmentOptions(equipment)} />
          </Form.Item>
          <Form.Item name="maintenanceType" label="保养类型">
            <Select
              options={Object.entries(MAINTENANCE_TYPE_LABEL).map(([value, label]) => ({ value, label }))}
            />
          </Form.Item>
          <Form.Item name="cycleDays" label="周期（天）" rules={[{ required: true, message: '请输入周期' }]}>
            <InputNumber min={1} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="checklist" label="保养项（分号分隔）" rules={[{ required: true, message: '请输入保养项' }]}>
            <Input.TextArea rows={2} placeholder="润滑;紧固;清洁" />
          </Form.Item>
          <Form.Item name="status" label="状态">
            <Select
              options={[
                { value: 'ENABLED', label: '启用' },
                { value: 'DISABLED', label: '停用' },
              ]}
            />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="生成保养工单"
        open={orderOpen}
        onCancel={() => setOrderOpen(false)}
        onOk={() => void submitOrder()}
        destroyOnHidden
      >
        <Form form={orderForm} layout="vertical">
          <Form.Item name="planId" label="保养计划" rules={[{ required: true, message: '请选择保养计划' }]}>
            <Select
              showSearch
              optionFilterProp="label"
              options={plans.map((item) => ({
                value: item.id,
                label: `${item.planNo} ${item.equipmentCode}`.trim(),
              }))}
            />
          </Form.Item>
          <Form.Item name="dueAt" label="到期时间">
            <DatePicker showTime style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="assignee" label="执行人">
            <Input />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={`保养完工：${completing?.orderNo ?? ''}`}
        open={completing !== null}
        onCancel={() => setCompleting(null)}
        onOk={() => void submitComplete()}
        destroyOnHidden
      >
        <Form form={completeForm} layout="vertical">
          <Form.Item name="completedBy" label="完工人员" rules={[{ required: true, message: '请输入完工人员' }]}>
            <Input />
          </Form.Item>
          <Form.Item
            name="checklistText"
            label="保养项结论（每行 项名=结论）"
            rules={[{ required: true, message: '请填写保养项结论' }]}
          >
            <Input.TextArea rows={6} />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={`保养验收：${accepting?.orderNo ?? ''}`}
        open={accepting !== null}
        onCancel={() => setAccepting(null)}
        onOk={() => void submitAccept()}
        destroyOnHidden
      >
        <Form form={acceptForm} layout="vertical">
          <Form.Item name="acceptedBy" label="验收人" rules={[{ required: true, message: '请输入验收人' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="conclusion" label="验收结论" rules={[{ required: true, message: '请输入验收结论' }]}>
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}

/** 维修工单 */
function RepairPanel({ equipment }: { equipment: MesEquipment[] }) {
  const [rows, setRows] = useState<MesRepairOrder[]>([]);
  const [loading, setLoading] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [action, setAction] = useState<{ type: 'assign' | 'start' | 'verify' | 'close'; order: MesRepairOrder } | null>(null);
  const [createForm] = Form.useForm();
  const [actionForm] = Form.useForm();
  const canRepair = hasPermission('sys:mes:equipment:repair');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setRows((await pageMesRepairs()).records);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const submitCreate = async () => {
    const values = await createForm.validateFields();
    await createMesRepair(values);
    message.success('维修工单已创建');
    setCreateOpen(false);
    createForm.resetFields();
    await load();
  };

  const submitAction = async () => {
    if (!action) {
      return;
    }
    const values = await actionForm.validateFields();
    if (action.type === 'assign') {
      await assignMesRepair(action.order.id, values);
    } else if (action.type === 'start') {
      await startMesRepair(action.order.id, values);
    } else if (action.type === 'verify') {
      await verifyMesRepair(action.order.id, values);
    } else {
      await closeMesRepair(action.order.id, values);
    }
    message.success('维修工单已更新');
    setAction(null);
    actionForm.resetFields();
    await load();
  };

  const actionLabel: Record<string, string> = {
    assign: '维修派工',
    start: '开始维修',
    verify: '维修验证',
    close: '关闭维修',
  };

  const columns: TableProps<MesRepairOrder>['columns'] = [
    { title: '维修单号', dataIndex: 'repairNo', key: 'repairNo', width: 160 },
    { title: '设备编码', dataIndex: 'equipmentCode', key: 'equipmentCode', width: 140 },
    { title: '故障描述', dataIndex: 'faultDesc', key: 'faultDesc' },
    { title: '严重度', dataIndex: 'severity', key: 'severity', width: 90 },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (value: string) => (
        <Tag color={REPAIR_STATUS_COLOR[value]}>{REPAIR_STATUS_LABEL[value] ?? value}</Tag>
      ),
    },
    { title: '维修人', dataIndex: 'assignee', key: 'assignee', width: 100 },
    {
      title: '报修时间',
      dataIndex: 'reportedAt',
      key: 'reportedAt',
      width: 160,
      render: (value) => formatTime(value as string | undefined),
    },
    {
      title: '操作',
      key: 'action',
      width: 180,
      render: (_, record) => {
        if (!canRepair) {
          return '-';
        }
        if (record.status === 'REPORTED') {
          return (
            <Button
              type="link"
              size="small"
              onClick={() => {
                setAction({ type: 'assign', order: record });
                actionForm.setFieldsValue({ assignee: record.assignee });
              }}
            >
              派工
            </Button>
          );
        }
        if (record.status === 'ASSIGNED') {
          return (
            <Button
              type="link"
              size="small"
              onClick={() => {
                setAction({ type: 'start', order: record });
                actionForm.resetFields();
              }}
            >
              开始
            </Button>
          );
        }
        if (record.status === 'REPAIRING') {
          return (
            <Button
              type="link"
              size="small"
              onClick={() => {
                setAction({ type: 'verify', order: record });
                actionForm.setFieldsValue({ verifiedBy: record.assignee });
              }}
            >
              验证
            </Button>
          );
        }
        if (record.status === 'VERIFIED') {
          return (
            <Popconfirm
              title="确认关闭该维修工单？"
              okText="关闭"
              cancelText="取消"
              onConfirm={() => void closeMesRepair(record.id, {}).then(() => load())}
            >
              <Button type="link" size="small">
                关闭
              </Button>
            </Popconfirm>
          );
        }
        return '-';
      },
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <Button onClick={() => void load()}>刷新</Button>
        {canRepair && (
          <Button
            type="primary"
            onClick={() => {
              createForm.resetFields();
              setCreateOpen(true);
            }}
          >
            新增报修
          </Button>
        )}
      </Space>
      <Table<MesRepairOrder>
        rowKey="id"
        size="small"
        loading={loading}
        columns={columns}
        dataSource={rows}
        pagination={{ pageSize: 10, showSizeChanger: true }}
      />

      <Modal
        title="新增报修"
        open={createOpen}
        onCancel={() => setCreateOpen(false)}
        onOk={() => void submitCreate()}
        destroyOnHidden
      >
        <Form form={createForm} layout="vertical" initialValues={{ severity: 'MAJOR' }}>
          <Form.Item name="equipmentId" label="设备" rules={[{ required: true, message: '请选择设备' }]}>
            <Select showSearch optionFilterProp="label" options={equipmentOptions(equipment)} />
          </Form.Item>
          <Form.Item name="faultDesc" label="故障描述" rules={[{ required: true, message: '请输入故障描述' }]}>
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item name="severity" label="严重度">
            <Select
              options={[
                { value: 'MINOR', label: '轻微' },
                { value: 'MAJOR', label: '一般' },
                { value: 'CRITICAL', label: '严重' },
              ]}
            />
          </Form.Item>
          <Form.Item name="reportedBy" label="报修人">
            <Input />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={action ? `${actionLabel[action.type]}：${action.order.repairNo}` : ''}
        open={action !== null}
        onCancel={() => setAction(null)}
        onOk={() => void submitAction()}
        destroyOnHidden
      >
        <Form form={actionForm} layout="vertical">
          {action?.type === 'assign' && (
            <Form.Item name="assignee" label="维修人" rules={[{ required: true, message: '请输入维修人' }]}>
              <Input />
            </Form.Item>
          )}
          {action?.type === 'verify' && (
            <>
              <Form.Item name="repairDesc" label="维修说明">
                <Input.TextArea rows={2} />
              </Form.Item>
              <Form.Item name="verifiedBy" label="验证人" rules={[{ required: true, message: '请输入验证人' }]}>
                <Input />
              </Form.Item>
              <Form.Item name="verifyConclusion" label="验证结论" rules={[{ required: true, message: '请输入验证结论' }]}>
                <Input.TextArea rows={2} />
              </Form.Item>
            </>
          )}
          {action?.type === 'close' && (
            <Form.Item name="remark" label="关闭备注">
              <Input.TextArea rows={2} />
            </Form.Item>
          )}
        </Form>
      </Modal>
    </Space>
  );
}

/** 备件主档、库存、流水 */
function SparePanel() {
  const [parts, setParts] = useState<MesSparePart[]>([]);
  const [stocks, setStocks] = useState<MesSparePartStock[]>([]);
  const [movements, setMovements] = useState<MesSparePartMovement[]>([]);
  const [loading, setLoading] = useState(false);
  const [partOpen, setPartOpen] = useState(false);
  const [stockOpen, setStockOpen] = useState<'in' | 'out' | null>(null);
  const [partForm] = Form.useForm();
  const [stockForm] = Form.useForm();
  const canManage = hasPermission('sys:mes:equipment:spare-part');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [partPage, stockPage, movementPage] = await Promise.all([
        pageMesSpareParts(),
        pageMesSparePartStocks(),
        pageMesSparePartMovements(),
      ]);
      setParts(partPage.records);
      setStocks(stockPage.records);
      setMovements(movementPage.records);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const submitPart = async () => {
    const values = await partForm.validateFields();
    await saveMesSparePart(values);
    message.success('备件已保存');
    setPartOpen(false);
    partForm.resetFields();
    await load();
  };

  const submitStock = async () => {
    if (!stockOpen) {
      return;
    }
    const values = await stockForm.validateFields();
    if (stockOpen === 'in') {
      await stockInMesSparePart(values);
      message.success('备件已入库');
    } else {
      await stockOutMesSparePart(values);
      message.success('备件已领用');
    }
    setStockOpen(null);
    stockForm.resetFields();
    await load();
  };

  const partColumns: TableProps<MesSparePart>['columns'] = [
    { title: '备件编码', dataIndex: 'partCode', key: 'partCode', width: 140 },
    { title: '名称', dataIndex: 'partName', key: 'partName', width: 160 },
    { title: '规格', dataIndex: 'spec', key: 'spec' },
    { title: '单位', dataIndex: 'uom', key: 'uom', width: 80 },
    { title: '安全库存', dataIndex: 'safetyStock', key: 'safetyStock', width: 110 },
  ];

  const stockColumns: TableProps<MesSparePartStock>['columns'] = [
    { title: '备件编码', dataIndex: 'partCode', key: 'partCode', width: 140 },
    { title: '仓库', dataIndex: 'warehouseCode', key: 'warehouseCode', width: 120 },
    { title: '库存数量', dataIndex: 'quantity', key: 'quantity', width: 120 },
    {
      title: '更新时间',
      dataIndex: 'updateTime',
      key: 'updateTime',
      width: 160,
      render: (value) => formatTime(value as string | undefined),
    },
  ];

  const movementColumns: TableProps<MesSparePartMovement>['columns'] = [
    { title: '流水号', dataIndex: 'movementNo', key: 'movementNo', width: 170 },
    { title: '备件编码', dataIndex: 'partCode', key: 'partCode', width: 140 },
    { title: '类型', dataIndex: 'movementType', key: 'movementType', width: 100 },
    { title: '数量', dataIndex: 'quantity', key: 'quantity', width: 100 },
    { title: '变动前', dataIndex: 'beforeQuantity', key: 'beforeQuantity', width: 100 },
    { title: '变动后', dataIndex: 'afterQuantity', key: 'afterQuantity', width: 100 },
    { title: '操作人', dataIndex: 'operator', key: 'operator', width: 100 },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <Button onClick={() => void load()}>刷新</Button>
        {canManage && (
          <>
            <Button
              type="primary"
              onClick={() => {
                partForm.resetFields();
                setPartOpen(true);
              }}
            >
              新增备件
            </Button>
            <Button
              onClick={() => {
                stockForm.resetFields();
                stockForm.setFieldsValue({ warehouseCode: 'DEFAULT' });
                setStockOpen('in');
              }}
            >
              入库
            </Button>
            <Button
              onClick={() => {
                stockForm.resetFields();
                stockForm.setFieldsValue({ warehouseCode: 'DEFAULT' });
                setStockOpen('out');
              }}
            >
              领用
            </Button>
          </>
        )}
      </Space>
      <Card title="备件主档" size="small" loading={loading}>
        <Table<MesSparePart>
          rowKey="id"
          size="small"
          columns={partColumns}
          dataSource={parts}
          pagination={{ pageSize: 5, showSizeChanger: true }}
        />
      </Card>
      <Card title="备件库存" size="small" loading={loading}>
        <Table<MesSparePartStock>
          rowKey="id"
          size="small"
          columns={stockColumns}
          dataSource={stocks}
          pagination={{ pageSize: 5, showSizeChanger: true }}
        />
      </Card>
      <Card title="备件流水" size="small" loading={loading}>
        <Table<MesSparePartMovement>
          rowKey="movementNo"
          size="small"
          columns={movementColumns}
          dataSource={movements}
          pagination={{ pageSize: 5, showSizeChanger: true }}
        />
      </Card>

      <Modal
        title="新增备件"
        open={partOpen}
        onCancel={() => setPartOpen(false)}
        onOk={() => void submitPart()}
        destroyOnHidden
      >
        <Form form={partForm} layout="vertical" initialValues={{ status: 'ENABLED', uom: '件' }}>
          <Form.Item name="partCode" label="备件编码" rules={[{ required: true, message: '请输入备件编码' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="partName" label="名称" rules={[{ required: true, message: '请输入名称' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="spec" label="规格">
            <Input />
          </Form.Item>
          <Form.Item name="uom" label="单位">
            <Input />
          </Form.Item>
          <Form.Item name="safetyStock" label="安全库存">
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="status" label="状态">
            <Select
              options={[
                { value: 'ENABLED', label: '启用' },
                { value: 'DISABLED', label: '停用' },
              ]}
            />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={stockOpen === 'in' ? '备件入库' : '备件领用'}
        open={stockOpen !== null}
        onCancel={() => setStockOpen(null)}
        onOk={() => void submitStock()}
        destroyOnHidden
      >
        <Form form={stockForm} layout="vertical" initialValues={{ warehouseCode: 'DEFAULT' }}>
          <Form.Item name="partCode" label="备件编码" rules={[{ required: true, message: '请输入备件编码' }]}>
            <Select
              showSearch
              optionFilterProp="label"
              options={parts.map((item) => ({ value: item.partCode, label: `${item.partCode} ${item.partName}` }))}
            />
          </Form.Item>
          <Form.Item name="warehouseCode" label="仓库">
            <Input />
          </Form.Item>
          <Form.Item name="quantity" label="数量" rules={[{ required: true, message: '请输入数量' }]}>
            <InputNumber min={0.001} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="refNo" label="关联单号">
            <Input />
          </Form.Item>
          <Form.Item name="operator" label="操作人">
            <Input />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}

/** OEE 计算与快照 */
function OeePanel({ equipment }: { equipment: MesEquipment[] }) {
  const [rows, setRows] = useState<MesOeeSnapshot[]>([]);
  const [loading, setLoading] = useState(false);
  const [form] = Form.useForm<{
    equipmentId: string;
    shiftDate: Dayjs;
    shiftCode: string;
    plannedMinutes: number;
    plannedDowntimeMinutes?: number;
    runMinutes: number;
    downtimeMinutes?: number;
    totalCount: number;
    goodCount: number;
    idealCycleSeconds: number;
  }>();
  const canCompute = hasPermission('sys:mes:equipment:oee');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setRows((await pageMesOee()).records);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const submit = async () => {
    const values = await form.validateFields();
    await computeMesOee({
      ...values,
      shiftDate: values.shiftDate.format('YYYY-MM-DD'),
    });
    message.success('OEE 快照已计算');
    await load();
  };

  const columns: TableProps<MesOeeSnapshot>['columns'] = [
    { title: '快照号', dataIndex: 'snapshotNo', key: 'snapshotNo', width: 170 },
    { title: '设备编码', dataIndex: 'equipmentCode', key: 'equipmentCode', width: 140 },
    { title: '班次日期', dataIndex: 'shiftDate', key: 'shiftDate', width: 110 },
    { title: '班次', dataIndex: 'shiftCode', key: 'shiftCode', width: 80 },
    {
      title: '可用率',
      dataIndex: 'availability',
      key: 'availability',
      width: 100,
      render: (value) => formatRate(value as number | string | undefined),
    },
    {
      title: '性能',
      dataIndex: 'performance',
      key: 'performance',
      width: 100,
      render: (value) => formatRate(value as number | string | undefined),
    },
    {
      title: '质量',
      dataIndex: 'quality',
      key: 'quality',
      width: 100,
      render: (value) => formatRate(value as number | string | undefined),
    },
    {
      title: 'OEE',
      dataIndex: 'oee',
      key: 'oee',
      width: 100,
      render: (value) => <Tag color="green">{formatRate(value as number | string | undefined)}</Tag>,
    },
    {
      title: '计算时间',
      dataIndex: 'computedAt',
      key: 'computedAt',
      width: 160,
      render: (value) => formatTime(value as string | undefined),
    },
  ];

  const latest = rows[0];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Card title="OEE 计算" size="small">
        <Form
          form={form}
          layout="inline"
          initialValues={{
            shiftDate: dayjs(),
            shiftCode: 'DAY',
            plannedMinutes: 480,
            plannedDowntimeMinutes: 0,
            runMinutes: 420,
            totalCount: 400,
            goodCount: 380,
            idealCycleSeconds: 60,
          }}
          onFinish={() => void submit()}
        >
          <Form.Item name="equipmentId" label="设备" rules={[{ required: true, message: '请选择设备' }]}>
            <Select showSearch optionFilterProp="label" style={{ width: 200 }} options={equipmentOptions(equipment)} />
          </Form.Item>
          <Form.Item name="shiftDate" label="日期" rules={[{ required: true, message: '请选择日期' }]}>
            <DatePicker />
          </Form.Item>
          <Form.Item name="shiftCode" label="班次" rules={[{ required: true, message: '请输入班次' }]}>
            <Input style={{ width: 90 }} />
          </Form.Item>
          <Form.Item name="plannedMinutes" label="计划分钟" rules={[{ required: true, message: '请输入计划分钟' }]}>
            <InputNumber min={1} style={{ width: 110 }} />
          </Form.Item>
          <Form.Item name="plannedDowntimeMinutes" label="计划停机">
            <InputNumber min={0} style={{ width: 110 }} />
          </Form.Item>
          <Form.Item name="runMinutes" label="运行分钟" rules={[{ required: true, message: '请输入运行分钟' }]}>
            <InputNumber min={0} style={{ width: 110 }} />
          </Form.Item>
          <Form.Item name="downtimeMinutes" label="停机分钟">
            <InputNumber min={0} style={{ width: 110 }} />
          </Form.Item>
          <Form.Item name="totalCount" label="总产量" rules={[{ required: true, message: '请输入总产量' }]}>
            <InputNumber min={0} style={{ width: 110 }} />
          </Form.Item>
          <Form.Item name="goodCount" label="合格产量" rules={[{ required: true, message: '请输入合格产量' }]}>
            <InputNumber min={0} style={{ width: 110 }} />
          </Form.Item>
          <Form.Item name="idealCycleSeconds" label="理想节拍(秒)" rules={[{ required: true, message: '请输入理想节拍' }]}>
            <InputNumber min={0.001} style={{ width: 120 }} />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" disabled={!canCompute}>
              计算
            </Button>
          </Form.Item>
        </Form>
      </Card>
      {latest && (
        <Card size="small">
          <Row gutter={16}>
            <Col span={6}>
              <Statistic title={`${latest.equipmentCode} 可用率`} value={formatRate(latest.availability)} />
            </Col>
            <Col span={6}>
              <Statistic title="性能" value={formatRate(latest.performance)} />
            </Col>
            <Col span={6}>
              <Statistic title="质量" value={formatRate(latest.quality)} />
            </Col>
            <Col span={6}>
              <Statistic title="OEE" value={formatRate(latest.oee)} />
            </Col>
          </Row>
        </Card>
      )}
      <Table<MesOeeSnapshot>
        rowKey="id"
        size="small"
        loading={loading}
        columns={columns}
        dataSource={rows}
        pagination={{ pageSize: 10, showSizeChanger: true }}
      />
    </Space>
  );
}

/** 设备页 */
export default function EquipmentPage({ initialTab = 'ledger' }: { initialTab?: EquipmentTab }) {
  const [tab, setTab] = useState<EquipmentTab>(initialTab);
  const [equipment, setEquipment] = useState<MesEquipment[]>([]);

  const loadEquipment = useCallback(async () => {
    const page = await pageMesEquipments();
    setEquipment(page.records);
  }, []);

  useEffect(() => {
    void loadEquipment();
  }, [loadEquipment]);

  useEffect(() => {
    setTab(initialTab);
  }, [initialTab]);

  const items: TabsProps['items'] = [
    { key: 'ledger', label: '设备台账', children: <LedgerPanel onChanged={loadEquipment} /> },
    { key: 'status', label: '状态与停机', children: <StatusPanel equipment={equipment} /> },
    { key: 'maintenance', label: '保养管理', children: <MaintenancePanel equipment={equipment} /> },
    { key: 'repair', label: '维修管理', children: <RepairPanel equipment={equipment} /> },
    { key: 'spare', label: '备件管理', children: <SparePanel /> },
    { key: 'oee', label: 'OEE 分析', children: <OeePanel equipment={equipment} /> },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Typography.Title level={5} style={{ margin: 0 }}>
        设备与 OEE
      </Typography.Title>
      <Tabs activeKey={tab} onChange={(key) => setTab(key as EquipmentTab)} items={items} destroyInactiveTabPane />
    </Space>
  );
}
