// ERP 设备与保养页：设备档案、保养计划/工单、OEE 记录
import { useCallback, useEffect, useState } from 'react';
import {
  Button,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  message,
} from 'antd';
import type { TableProps } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import {
  ENABLED_STATUS_LABEL,
  EQUIPMENT_STATUS_LABEL,
  MAINTENANCE_STATUS_LABEL,
  MAINTENANCE_TYPE_LABEL,
  completeMaintenanceOrder,
  createEquipment,
  createMaintenanceOrder,
  createMaintenancePlan,
  pageEquipment,
  pageMaintenanceOrders,
  pageMaintenancePlans,
  pageOee,
  qcStatusColor,
  recordOee,
  updateEquipment,
  updateMaintenancePlan,
  type EquipmentInput,
  type EquipmentView,
  type MaintenanceOrderView,
  type MaintenancePlanView,
  type OeeRecordView,
} from '../api/qc';
import { hasPermission } from '../utils/permission';
import { money, quantity } from '../utils/scm';

/** 设备表单值 */
interface EquipmentFormValues {
  equipmentNo: string;
  name: string;
  equipmentType?: string;
  location?: string;
  status: string;
  purchaseDate?: Dayjs;
  remark?: string;
}

/** 保养计划表单值 */
interface PlanFormValues {
  equipmentNo: string;
  cycleDays?: number;
  lastDate?: Dayjs;
  nextDate?: Dayjs;
  status: string;
  remark?: string;
}

/** 保养工单表单值 */
interface OrderFormValues {
  equipmentNo: string;
  planId?: number;
  maintenanceType: string;
  planDate: Dayjs;
  remark?: string;
}

/** 保养完成表单值 */
interface CompleteFormValues {
  completeDate: Dayjs;
  costAmount?: number;
  result?: string;
  remark?: string;
}

/** OEE 表单值 */
interface OeeFormValues {
  equipmentNo: string;
  recordDate: Dayjs;
  plannedMinutes?: number;
  runMinutes?: number;
  downMinutes?: number;
  idealQty?: number;
  outputQty?: number;
  goodQty?: number;
}

/** 设备下拉 */
function EquipmentSelect({ equipment }: { equipment: EquipmentView[] }) {
  return (
    <Select
      showSearch
      optionFilterProp="label"
      placeholder="选择设备"
      style={{ width: 210 }}
      options={equipment.map((record) => ({
        value: record.equipmentNo,
        label: `${record.equipmentNo} ${record.name}`,
      }))}
    />
  );
}

/** 设备档案弹窗 */
function EquipmentModal({
  open,
  initial,
  onCancel,
  onSaved,
}: {
  open: boolean;
  initial: EquipmentView | null;
  onCancel: () => void;
  onSaved: () => void;
}) {
  const [form] = Form.useForm<EquipmentFormValues>();

  useEffect(() => {
    if (!open) return;
    form.resetFields();
    if (initial) {
      form.setFieldsValue({
        equipmentNo: initial.equipmentNo,
        name: initial.name,
        equipmentType: initial.equipmentType,
        location: initial.location,
        status: initial.status,
        purchaseDate: initial.purchaseDate ? dayjs(initial.purchaseDate) : undefined,
        remark: initial.remark,
      });
    } else {
      form.setFieldsValue({ status: 'RUNNING' });
    }
  }, [open, initial, form]);

  const handleOk = async () => {
    const values = await form.validateFields().catch(() => null);
    if (!values) return;
    const input: EquipmentInput = {
      equipmentNo: values.equipmentNo,
      name: values.name,
      equipmentType: values.equipmentType,
      location: values.location,
      status: values.status,
      purchaseDate: values.purchaseDate?.format('YYYY-MM-DD'),
      remark: values.remark,
    };
    if (initial) {
      await updateEquipment(initial.equipmentNo, input);
      message.success('设备档案已更新');
    } else {
      await createEquipment(input);
      message.success('设备档案已创建');
    }
    onSaved();
  };

  return (
    <Modal
      open={open}
      title={initial ? '编辑设备档案' : '新增设备档案'}
      width={640}
      onOk={handleOk}
      onCancel={onCancel}
      destroyOnHidden
    >
      <Form form={form} layout="vertical">
        <Space size="middle" wrap>
          <Form.Item
            name="equipmentNo"
            label="设备编码"
            rules={[{ required: true, message: '请输入设备编码' }]}
          >
            <Input maxLength={64} disabled={initial !== null} style={{ width: 200 }} />
          </Form.Item>
          <Form.Item
            name="name"
            label="设备名称"
            rules={[{ required: true, message: '请输入设备名称' }]}
          >
            <Input maxLength={128} style={{ width: 220 }} />
          </Form.Item>
          <Form.Item name="equipmentType" label="设备类型">
            <Input maxLength={64} style={{ width: 160 }} />
          </Form.Item>
          <Form.Item name="location" label="安装位置">
            <Input maxLength={128} style={{ width: 200 }} />
          </Form.Item>
          <Form.Item name="status" label="状态" rules={[{ required: true }]}>
            <Select
              style={{ width: 140 }}
              options={Object.entries(EQUIPMENT_STATUS_LABEL).map(([value, label]) => ({
                value,
                label,
              }))}
            />
          </Form.Item>
          <Form.Item name="purchaseDate" label="购置日期">
            <DatePicker format="YYYY-MM-DD" />
          </Form.Item>
        </Space>
        <Form.Item name="remark" label="备注">
          <Input.TextArea rows={2} maxLength={255} />
        </Form.Item>
      </Form>
    </Modal>
  );
}

/** 保养计划弹窗 */
function PlanModal({
  open,
  initial,
  equipment,
  onCancel,
  onSaved,
}: {
  open: boolean;
  initial: MaintenancePlanView | null;
  equipment: EquipmentView[];
  onCancel: () => void;
  onSaved: () => void;
}) {
  const [form] = Form.useForm<PlanFormValues>();

  useEffect(() => {
    if (!open) return;
    form.resetFields();
    if (initial) {
      form.setFieldsValue({
        equipmentNo: initial.equipmentNo,
        cycleDays: initial.cycleDays,
        lastDate: initial.lastDate ? dayjs(initial.lastDate) : undefined,
        nextDate: initial.nextDate ? dayjs(initial.nextDate) : undefined,
        status: initial.status,
        remark: initial.remark,
      });
    } else {
      form.setFieldsValue({ cycleDays: 30, status: 'ACTIVE', lastDate: dayjs() });
    }
  }, [open, initial, form]);

  const handleOk = async () => {
    const values = await form.validateFields().catch(() => null);
    if (!values) return;
    const input = {
      equipmentNo: values.equipmentNo,
      cycleDays: Number(values.cycleDays ?? 0),
      lastDate: values.lastDate?.format('YYYY-MM-DD'),
      nextDate: values.nextDate?.format('YYYY-MM-DD'),
      status: values.status,
      remark: values.remark,
    };
    if (initial) {
      await updateMaintenancePlan(initial.id, input);
      message.success('保养计划已更新');
    } else {
      await createMaintenancePlan(input);
      message.success('保养计划已创建');
    }
    onSaved();
  };

  return (
    <Modal
      open={open}
      title={initial ? '编辑保养计划' : '新增保养计划'}
      width={640}
      onOk={handleOk}
      onCancel={onCancel}
      destroyOnHidden
    >
      <Form form={form} layout="vertical">
        <Space size="middle" wrap>
          <Form.Item
            name="equipmentNo"
            label="设备"
            rules={[{ required: true, message: '请选择设备' }]}
          >
            <EquipmentSelect equipment={equipment} />
          </Form.Item>
          <Form.Item
            name="cycleDays"
            label="保养周期（天）"
            rules={[{ required: true, message: '请输入保养周期' }]}
          >
            <InputNumber min={1} precision={0} style={{ width: 140 }} />
          </Form.Item>
          <Form.Item name="status" label="状态">
            <Select
              style={{ width: 140 }}
              options={Object.entries(ENABLED_STATUS_LABEL).map(([value, label]) => ({
                value,
                label,
              }))}
            />
          </Form.Item>
          <Form.Item name="lastDate" label="上次保养日期">
            <DatePicker format="YYYY-MM-DD" />
          </Form.Item>
          <Form.Item name="nextDate" label="下次保养日期">
            <DatePicker format="YYYY-MM-DD" />
          </Form.Item>
        </Space>
        <Form.Item name="remark" label="备注">
          <Input.TextArea rows={2} maxLength={255} />
        </Form.Item>
      </Form>
    </Modal>
  );
}

/** 保养工单弹窗 */
function OrderModal({
  open,
  equipment,
  plans,
  onCancel,
  onSaved,
}: {
  open: boolean;
  equipment: EquipmentView[];
  plans: MaintenancePlanView[];
  onCancel: () => void;
  onSaved: () => void;
}) {
  const [form] = Form.useForm<OrderFormValues>();
  const equipmentNo = Form.useWatch('equipmentNo', form);

  useEffect(() => {
    if (!open) return;
    form.resetFields();
    form.setFieldsValue({ maintenanceType: 'PREVENTIVE', planDate: dayjs() });
  }, [open, form]);

  const handleOk = async () => {
    const values = await form.validateFields().catch(() => null);
    if (!values) return;
    await createMaintenanceOrder({
      equipmentNo: values.equipmentNo,
      planId: values.planId,
      maintenanceType: values.maintenanceType,
      planDate: values.planDate.format('YYYY-MM-DD'),
      remark: values.remark,
    });
    message.success('保养工单已创建');
    onSaved();
  };

  const planOptions = plans
    .filter((plan) => (equipmentNo ? plan.equipmentNo === equipmentNo : true))
    .map((plan) => ({
      value: plan.id,
      label: `${plan.planNo}（下次 ${plan.nextDate ?? '-'}）`,
    }));

  return (
    <Modal
      open={open}
      title="新增保养工单"
      width={640}
      onOk={handleOk}
      onCancel={onCancel}
      destroyOnHidden
    >
      <Form form={form} layout="vertical">
        <Space size="middle" wrap>
          <Form.Item
            name="equipmentNo"
            label="设备"
            rules={[{ required: true, message: '请选择设备' }]}
          >
            <EquipmentSelect equipment={equipment} />
          </Form.Item>
          <Form.Item name="planId" label="保养计划">
            <Select
              allowClear
              placeholder="选择计划"
              style={{ width: 220 }}
              options={planOptions}
            />
          </Form.Item>
          <Form.Item name="maintenanceType" label="保养类型" rules={[{ required: true }]}>
            <Select
              style={{ width: 150 }}
              options={Object.entries(MAINTENANCE_TYPE_LABEL).map(([value, label]) => ({
                value,
                label,
              }))}
            />
          </Form.Item>
          <Form.Item
            name="planDate"
            label="计划日期"
            rules={[{ required: true, message: '请选择计划日期' }]}
          >
            <DatePicker format="YYYY-MM-DD" />
          </Form.Item>
        </Space>
        <Form.Item name="remark" label="备注">
          <Input.TextArea rows={2} maxLength={255} />
        </Form.Item>
      </Form>
    </Modal>
  );
}

/** 保养完成弹窗 */
function CompleteModal({
  open,
  order,
  onCancel,
  onSaved,
}: {
  open: boolean;
  order: MaintenanceOrderView | null;
  onCancel: () => void;
  onSaved: () => void;
}) {
  const [form] = Form.useForm<CompleteFormValues>();

  useEffect(() => {
    if (!open) return;
    form.resetFields();
    form.setFieldsValue({ completeDate: dayjs(), costAmount: 0 });
  }, [open, form]);

  const handleOk = async () => {
    if (!order) return;
    const values = await form.validateFields().catch(() => null);
    if (!values) return;
    await completeMaintenanceOrder(order.id, {
      completeDate: values.completeDate.format('YYYY-MM-DD'),
      costAmount: Number(values.costAmount ?? 0),
      result: values.result,
      remark: values.remark,
    });
    message.success('保养工单已完成');
    onSaved();
  };

  return (
    <Modal
      open={open}
      title={order ? `完成保养工单 ${order.orderNo}` : '完成保养工单'}
      onOk={handleOk}
      onCancel={onCancel}
      destroyOnHidden
    >
      <Form form={form} layout="vertical">
        <Form.Item
          name="completeDate"
          label="实际完成日期"
          rules={[{ required: true, message: '请选择完成日期' }]}
        >
          <DatePicker format="YYYY-MM-DD" />
        </Form.Item>
        <Form.Item name="costAmount" label="保养费用">
          <InputNumber min={0} precision={2} style={{ width: 180 }} />
        </Form.Item>
        <Form.Item name="result" label="保养结果">
          <Input.TextArea rows={2} maxLength={255} />
        </Form.Item>
        <Form.Item name="remark" label="备注">
          <Input.TextArea rows={2} maxLength={255} />
        </Form.Item>
      </Form>
    </Modal>
  );
}

/** OEE 回传弹窗 */
function OeeModal({
  open,
  equipment,
  onCancel,
  onSaved,
}: {
  open: boolean;
  equipment: EquipmentView[];
  onCancel: () => void;
  onSaved: () => void;
}) {
  const [form] = Form.useForm<OeeFormValues>();

  useEffect(() => {
    if (!open) return;
    form.resetFields();
    form.setFieldsValue({
      recordDate: dayjs(),
      plannedMinutes: 480,
      runMinutes: 450,
      downMinutes: 30,
    });
  }, [open, form]);

  const handleOk = async () => {
    const values = await form.validateFields().catch(() => null);
    if (!values) return;
    await recordOee({
      equipmentNo: values.equipmentNo,
      recordDate: values.recordDate.format('YYYY-MM-DD'),
      plannedMinutes: Number(values.plannedMinutes ?? 0),
      runMinutes: Number(values.runMinutes ?? 0),
      downMinutes: Number(values.downMinutes ?? 0),
      idealQty: Number(values.idealQty ?? 0),
      outputQty: Number(values.outputQty ?? 0),
      goodQty: Number(values.goodQty ?? 0),
    });
    message.success('OEE 数据已回传（同设备同日期覆盖）');
    onSaved();
  };

  return (
    <Modal
      open={open}
      title="回传 OEE 数据"
      width={640}
      onOk={handleOk}
      onCancel={onCancel}
      destroyOnHidden
    >
      <Form form={form} layout="vertical">
        <Space size="middle" wrap>
          <Form.Item
            name="equipmentNo"
            label="设备"
            rules={[{ required: true, message: '请选择设备' }]}
          >
            <EquipmentSelect equipment={equipment} />
          </Form.Item>
          <Form.Item
            name="recordDate"
            label="统计日期"
            rules={[{ required: true, message: '请选择日期' }]}
          >
            <DatePicker format="YYYY-MM-DD" />
          </Form.Item>
          <Form.Item
            name="plannedMinutes"
            label="计划运行分钟"
            rules={[{ required: true, message: '请输入计划运行分钟' }]}
          >
            <InputNumber min={0} precision={2} style={{ width: 140 }} />
          </Form.Item>
          <Form.Item name="runMinutes" label="实际运行分钟">
            <InputNumber min={0} precision={2} style={{ width: 140 }} />
          </Form.Item>
          <Form.Item name="downMinutes" label="停机分钟">
            <InputNumber min={0} precision={2} style={{ width: 140 }} />
          </Form.Item>
          <Form.Item
            name="idealQty"
            label="理论产量"
            rules={[{ required: true, message: '请输入理论产量' }]}
          >
            <InputNumber min={0} precision={4} style={{ width: 140 }} />
          </Form.Item>
          <Form.Item name="outputQty" label="实际产量">
            <InputNumber min={0} precision={4} style={{ width: 140 }} />
          </Form.Item>
          <Form.Item name="goodQty" label="合格产量">
            <InputNumber min={0} precision={4} style={{ width: 140 }} />
          </Form.Item>
        </Space>
      </Form>
    </Modal>
  );
}

/** 设备档案页签 */
function EquipmentTab() {
  const [data, setData] = useState<EquipmentView[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<string | undefined>();
  const [version, setVersion] = useState(0);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<EquipmentView | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageEquipment({ size: 50, keyword, status });
      setData(page.records);
    } finally {
      setLoading(false);
    }
  }, [keyword, status]);

  useEffect(() => {
    void load();
  }, [load, version]);

  const columns: TableProps<EquipmentView>['columns'] = [
    { title: '设备编码', dataIndex: 'equipmentNo', key: 'equipmentNo', width: 150 },
    { title: '设备名称', dataIndex: 'name', key: 'name' },
    { title: '设备类型', dataIndex: 'equipmentType', key: 'equipmentType', width: 130 },
    { title: '安装位置', dataIndex: 'location', key: 'location', width: 160 },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 110,
      render: (value) => (
        <Tag color={qcStatusColor(String(value))}>
          {EQUIPMENT_STATUS_LABEL[String(value)] ?? value}
        </Tag>
      ),
    },
    { title: '购置日期', dataIndex: 'purchaseDate', key: 'purchaseDate', width: 120 },
    { title: '备注', dataIndex: 'remark', key: 'remark' },
    {
      title: '操作',
      key: 'action',
      width: 100,
      render: (_, record) =>
        hasPermission('sys:erp:qc:equipment:update') ? (
          <Button
            type="link"
            size="small"
            onClick={() => {
              setEditing(record);
              setModalOpen(true);
            }}
          >
            编辑
          </Button>
        ) : null,
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <Input.Search
          allowClear
          placeholder="设备编码 / 名称"
          style={{ width: 240 }}
          onSearch={(value) => setKeyword(value)}
        />
        <Button onClick={() => setStatus(undefined)} type={status ? 'default' : 'primary'}>
          全部
        </Button>
        {['RUNNING', 'IDLE', 'DOWN', 'RETIRED'].map((value) => (
          <Button
            key={value}
            onClick={() => setStatus(value)}
            type={status === value ? 'primary' : 'default'}
          >
            {EQUIPMENT_STATUS_LABEL[value] ?? value}
          </Button>
        ))}
        {hasPermission('sys:erp:qc:equipment:add') ? (
          <Button
            type="primary"
            onClick={() => {
              setEditing(null);
              setModalOpen(true);
            }}
          >
            新增设备
          </Button>
        ) : null}
      </Space>
      <Table<EquipmentView>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={data}
        pagination={false}
      />
      <EquipmentModal
        open={modalOpen}
        initial={editing}
        onCancel={() => setModalOpen(false)}
        onSaved={() => {
          setModalOpen(false);
          setVersion((value) => value + 1);
        }}
      />
    </Space>
  );
}

/** 保养计划页签 */
function PlanTab({ equipment, notify }: { equipment: EquipmentView[]; notify: () => void }) {
  const [data, setData] = useState<MaintenancePlanView[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<string | undefined>();
  const [version, setVersion] = useState(0);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<MaintenancePlanView | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageMaintenancePlans({ size: 50, keyword, status });
      setData(page.records);
    } finally {
      setLoading(false);
    }
  }, [keyword, status]);

  useEffect(() => {
    void load();
  }, [load, version]);

  const columns: TableProps<MaintenancePlanView>['columns'] = [
    { title: '保养计划号', dataIndex: 'planNo', key: 'planNo', width: 170 },
    { title: '设备编码', dataIndex: 'equipmentNo', key: 'equipmentNo', width: 150 },
    {
      title: '保养周期（天）',
      dataIndex: 'cycleDays',
      key: 'cycleDays',
      width: 130,
      render: quantity,
    },
    { title: '上次保养', dataIndex: 'lastDate', key: 'lastDate', width: 120 },
    { title: '下次保养', dataIndex: 'nextDate', key: 'nextDate', width: 120 },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (value) => (
        <Tag color={qcStatusColor(String(value))}>
          {ENABLED_STATUS_LABEL[String(value)] ?? value}
        </Tag>
      ),
    },
    { title: '备注', dataIndex: 'remark', key: 'remark' },
    {
      title: '操作',
      key: 'action',
      width: 100,
      render: (_, record) =>
        hasPermission('sys:erp:qc:maintenance:save') ? (
          <Button
            type="link"
            size="small"
            onClick={() => {
              setEditing(record);
              setModalOpen(true);
            }}
          >
            编辑
          </Button>
        ) : null,
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <Input.Search
          allowClear
          placeholder="保养计划号 / 设备编码"
          style={{ width: 260 }}
          onSearch={(value) => setKeyword(value)}
        />
        <Button onClick={() => setStatus(undefined)} type={status ? 'default' : 'primary'}>
          全部
        </Button>
        {['ACTIVE', 'DISABLED'].map((value) => (
          <Button
            key={value}
            onClick={() => setStatus(value)}
            type={status === value ? 'primary' : 'default'}
          >
            {ENABLED_STATUS_LABEL[value] ?? value}
          </Button>
        ))}
        {hasPermission('sys:erp:qc:maintenance:save') ? (
          <Button
            type="primary"
            onClick={() => {
              setEditing(null);
              setModalOpen(true);
            }}
          >
            新增保养计划
          </Button>
        ) : null}
      </Space>
      <Table<MaintenancePlanView>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={data}
        pagination={false}
      />
      <PlanModal
        open={modalOpen}
        initial={editing}
        equipment={equipment}
        onCancel={() => setModalOpen(false)}
        onSaved={() => {
          setModalOpen(false);
          setVersion((value) => value + 1);
          notify();
        }}
      />
    </Space>
  );
}

/** 保养工单页签 */
function OrderTab({
  equipment,
  plans,
  ticket,
  notify,
}: {
  equipment: EquipmentView[];
  plans: MaintenancePlanView[];
  ticket: number;
  notify: () => void;
}) {
  const [data, setData] = useState<MaintenanceOrderView[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<string | undefined>();
  const [version, setVersion] = useState(0);
  const [createOpen, setCreateOpen] = useState(false);
  const [completing, setCompleting] = useState<MaintenanceOrderView | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageMaintenanceOrders({ size: 50, keyword, status });
      setData(page.records);
    } finally {
      setLoading(false);
    }
  }, [keyword, status]);

  useEffect(() => {
    void load();
  }, [load, version, ticket]);

  const columns: TableProps<MaintenanceOrderView>['columns'] = [
    { title: '工单号', dataIndex: 'orderNo', key: 'orderNo', width: 170 },
    { title: '设备编码', dataIndex: 'equipmentNo', key: 'equipmentNo', width: 150 },
    {
      title: '保养类型',
      dataIndex: 'maintenanceType',
      key: 'maintenanceType',
      width: 130,
      render: (value) => MAINTENANCE_TYPE_LABEL[String(value)] ?? value,
    },
    { title: '计划日期', dataIndex: 'planDate', key: 'planDate', width: 120 },
    { title: '完成日期', dataIndex: 'completeDate', key: 'completeDate', width: 120 },
    { title: '保养费用', dataIndex: 'costAmount', key: 'costAmount', width: 120, render: money },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (value) => (
        <Tag color={qcStatusColor(String(value))}>
          {MAINTENANCE_STATUS_LABEL[String(value)] ?? value}
        </Tag>
      ),
    },
    { title: '保养结果', dataIndex: 'result', key: 'result' },
    {
      title: '操作',
      key: 'action',
      width: 120,
      render: (_, record) =>
        ['OPEN', 'IN_PROGRESS'].includes(record.status) &&
        hasPermission('sys:erp:qc:maintenance:complete') ? (
          <Button type="link" size="small" onClick={() => setCompleting(record)}>
            完成
          </Button>
        ) : null,
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <Input.Search
          allowClear
          placeholder="工单号 / 设备编码"
          style={{ width: 240 }}
          onSearch={(value) => setKeyword(value)}
        />
        <Button onClick={() => setStatus(undefined)} type={status ? 'default' : 'primary'}>
          全部
        </Button>
        {['OPEN', 'IN_PROGRESS', 'COMPLETED'].map((value) => (
          <Button
            key={value}
            onClick={() => setStatus(value)}
            type={status === value ? 'primary' : 'default'}
          >
            {MAINTENANCE_STATUS_LABEL[value] ?? value}
          </Button>
        ))}
        {hasPermission('sys:erp:qc:maintenance:save') ? (
          <Button type="primary" onClick={() => setCreateOpen(true)}>
            新增工单
          </Button>
        ) : null}
      </Space>
      <Table<MaintenanceOrderView>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={data}
        pagination={false}
      />
      <OrderModal
        open={createOpen}
        equipment={equipment}
        plans={plans}
        onCancel={() => setCreateOpen(false)}
        onSaved={() => {
          setCreateOpen(false);
          setVersion((value) => value + 1);
          notify();
        }}
      />
      <CompleteModal
        open={completing !== null}
        order={completing}
        onCancel={() => setCompleting(null)}
        onSaved={() => {
          setCompleting(null);
          setVersion((value) => value + 1);
          notify();
        }}
      />
    </Space>
  );
}

/** OEE 页签 */
function OeeTab({ equipment, ticket }: { equipment: EquipmentView[]; ticket: number }) {
  const [data, setData] = useState<OeeRecordView[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [version, setVersion] = useState(0);
  const [modalOpen, setModalOpen] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageOee({ size: 50, keyword });
      setData(page.records);
    } finally {
      setLoading(false);
    }
  }, [keyword]);

  useEffect(() => {
    void load();
  }, [load, version, ticket]);

  const rate = (value: number | undefined) => `${(Number(value ?? 0) * 100).toFixed(2)}%`;

  const columns: TableProps<OeeRecordView>['columns'] = [
    { title: '设备编码', dataIndex: 'equipmentNo', key: 'equipmentNo', width: 150 },
    { title: '统计日期', dataIndex: 'recordDate', key: 'recordDate', width: 120 },
    {
      title: '计划分钟',
      dataIndex: 'plannedMinutes',
      key: 'plannedMinutes',
      width: 110,
      render: quantity,
    },
    { title: '运行分钟', dataIndex: 'runMinutes', key: 'runMinutes', width: 110, render: quantity },
    { title: '实际产量', dataIndex: 'outputQty', key: 'outputQty', width: 110, render: quantity },
    { title: '合格产量', dataIndex: 'goodQty', key: 'goodQty', width: 110, render: quantity },
    {
      title: '时间开动率',
      dataIndex: 'availabilityRate',
      key: 'availabilityRate',
      width: 110,
      render: rate,
    },
    {
      title: '性能开动率',
      dataIndex: 'performanceRate',
      key: 'performanceRate',
      width: 110,
      render: rate,
    },
    { title: '合格率', dataIndex: 'qualityRate', key: 'qualityRate', width: 100, render: rate },
    {
      title: 'OEE',
      dataIndex: 'oeeRate',
      key: 'oeeRate',
      width: 100,
      render: (value) => <Tag color="blue">{rate(value)}</Tag>,
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <Input.Search
          allowClear
          placeholder="设备编码"
          style={{ width: 240 }}
          onSearch={(value) => setKeyword(value)}
        />
        {hasPermission('sys:erp:qc:oee:record') ? (
          <Button type="primary" onClick={() => setModalOpen(true)}>
            回传 OEE
          </Button>
        ) : null}
      </Space>
      <Table<OeeRecordView>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={data}
        pagination={false}
      />
      <OeeModal
        open={modalOpen}
        equipment={equipment}
        onCancel={() => setModalOpen(false)}
        onSaved={() => {
          setModalOpen(false);
          setVersion((value) => value + 1);
        }}
      />
    </Space>
  );
}

/** 设备与保养页 */
export default function QcEquipmentPage() {
  const [tab, setTab] = useState('equipment');
  const [equipment, setEquipment] = useState<EquipmentView[]>([]);
  const [plans, setPlans] = useState<MaintenancePlanView[]>([]);
  const [ticket, setTicket] = useState(0);

  const loadOptions = useCallback(async () => {
    const [equipmentPage, planPage] = await Promise.all([
      pageEquipment({ size: 200 }),
      pageMaintenancePlans({ size: 200 }),
    ]);
    setEquipment(equipmentPage.records);
    setPlans(planPage.records);
  }, []);

  useEffect(() => {
    void loadOptions();
  }, [loadOptions, ticket]);

  return (
    <Tabs
      activeKey={tab}
      onChange={setTab}
      items={[
        { key: 'equipment', label: '设备档案', children: <EquipmentTab /> },
        {
          key: 'plan',
          label: '保养计划',
          children: (
            <PlanTab
              equipment={equipment}
              notify={() => {
                setTicket((value) => value + 1);
              }}
            />
          ),
        },
        {
          key: 'order',
          label: '保养工单',
          children: (
            <OrderTab
              equipment={equipment}
              plans={plans}
              ticket={ticket}
              notify={() => {
                setTicket((value) => value + 1);
              }}
            />
          ),
        },
        {
          key: 'oee',
          label: 'OEE 记录',
          children: <OeeTab equipment={equipment} ticket={ticket} />,
        },
      ]}
    />
  );
}
