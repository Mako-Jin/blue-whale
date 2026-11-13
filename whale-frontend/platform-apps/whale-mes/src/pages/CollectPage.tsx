// MES 采集页：采集设备 / 采集点 / 样本 / 看板 / 归档
import { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Col,
  Descriptions,
  Form,
  Input,
  InputNumber,
  Modal,
  Row,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  Typography,
  message,
} from 'antd';
import type { TableProps, TabsProps } from 'antd';
import {
  fetchMesCollectBoardData,
  heartbeatMesCollectDevice,
  pageMesArchivePolicies,
  pageMesCollectBoards,
  pageMesCollectDevices,
  pageMesCollectPoints,
  pageMesMetricSamples,
  pageMesEquipments,
  runMesArchivePolicy,
  saveMesArchivePolicy,
  saveMesCollectBoard,
  saveMesCollectDevice,
  saveMesCollectPoint,
  type MesArchivePolicy,
  type MesCollectBoard,
  type MesCollectBoardData,
  type MesCollectDevice,
  type MesCollectPoint,
  type MesEquipment,
  type MesMetricSample,
} from '../api/mes';
import { hasPermission } from '../utils/permission';

/** 采集页签 */
export type CollectTab = 'device' | 'point' | 'sample' | 'board' | 'archive';

/** 时间展示：ISO 转 `YYYY-MM-DD HH:mm` */
function formatTime(value?: string): string {
  return value ? String(value).replace('T', ' ').slice(0, 16) : '-';
}

/** 采集设备 */
function DevicePanel() {
  const [rows, setRows] = useState<MesCollectDevice[]>([]);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<MesCollectDevice | null>(null);
  const [form] = Form.useForm();
  const canManage = hasPermission('sys:mes:collect:manage');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setRows((await pageMesCollectDevices()).records);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const submit = async () => {
    const values = await form.validateFields();
    await saveMesCollectDevice({ ...values, id: editing?.id });
    message.success('采集设备已保存');
    setOpen(false);
    await load();
  };

  const heartbeat = async (record: MesCollectDevice) => {
    await heartbeatMesCollectDevice(record.deviceCode);
    message.success(`心跳已上报：${record.deviceCode}`);
    await load();
  };

  const columns: TableProps<MesCollectDevice>['columns'] = [
    { title: '设备编码', dataIndex: 'deviceCode', key: 'deviceCode', width: 150 },
    { title: '名称', dataIndex: 'deviceName', key: 'deviceName', width: 160 },
    { title: '类型', dataIndex: 'deviceType', key: 'deviceType', width: 110 },
    { title: '协议', dataIndex: 'protocol', key: 'protocol', width: 90 },
    { title: 'IP', dataIndex: 'ipAddress', key: 'ipAddress', width: 130 },
    { title: '产线', dataIndex: 'lineCode', key: 'lineCode', width: 110 },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (value: string) => (
        <Tag color={value === 'ONLINE' ? 'green' : value === 'DISABLED' ? 'default' : 'orange'}>
          {value === 'ONLINE' ? '在线' : value === 'DISABLED' ? '停用' : '离线'}
        </Tag>
      ),
    },
    {
      title: '最后心跳',
      dataIndex: 'lastHeartbeatAt',
      key: 'lastHeartbeatAt',
      width: 160,
      render: (value) => formatTime(value as string | undefined),
    },
    {
      title: '操作',
      key: 'action',
      width: 140,
      render: (_, record) => (
        <Space size="small">
          {canManage && (
            <>
              <Button
                type="link"
                size="small"
                onClick={() => {
                  setEditing(record);
                  form.setFieldsValue(record);
                  setOpen(true);
                }}
              >
                编辑
              </Button>
              <Button type="link" size="small" onClick={() => void heartbeat(record)}>
                心跳
              </Button>
            </>
          )}
        </Space>
      ),
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <Button onClick={() => void load()}>刷新</Button>
        {canManage && (
          <Button
            type="primary"
            onClick={() => {
              setEditing(null);
              form.resetFields();
              setOpen(true);
            }}
          >
            新增采集设备
          </Button>
        )}
      </Space>
      <Table<MesCollectDevice>
        rowKey="id"
        size="small"
        loading={loading}
        columns={columns}
        dataSource={rows}
        pagination={{ pageSize: 10, showSizeChanger: true }}
      />

      <Modal
        title={editing ? '编辑采集设备' : '新增采集设备'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={() => void submit()}
        destroyOnHidden
      >
        <Form
          form={form}
          layout="vertical"
          initialValues={{ deviceType: 'GATEWAY', protocol: 'REST', status: 'OFFLINE' }}
        >
          <Form.Item
            name="deviceCode"
            label="设备编码"
            rules={[{ required: true, message: '请输入设备编码' }]}
          >
            <Input />
          </Form.Item>
          <Form.Item
            name="deviceName"
            label="名称"
            rules={[{ required: true, message: '请输入名称' }]}
          >
            <Input />
          </Form.Item>
          <Form.Item name="deviceType" label="类型">
            <Select
              options={[
                { value: 'GATEWAY', label: '网关' },
                { value: 'PLC', label: 'PLC' },
                { value: 'SCADA', label: 'SCADA' },
              ]}
            />
          </Form.Item>
          <Form.Item name="protocol" label="协议">
            <Select
              options={[
                { value: 'REST', label: 'REST' },
                { value: 'OPC_UA', label: 'OPC UA' },
                { value: 'MQTT', label: 'MQTT' },
                { value: 'MODBUS', label: 'Modbus' },
              ]}
            />
          </Form.Item>
          <Form.Item name="ipAddress" label="IP 地址">
            <Input />
          </Form.Item>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="lineId" label="产线 ID">
                <Input />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="lineCode" label="产线编码">
                <Input />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="status" label="状态">
            <Select
              options={[
                { value: 'ONLINE', label: '在线' },
                { value: 'OFFLINE', label: '离线' },
                { value: 'DISABLED', label: '停用' },
              ]}
            />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}

/** 采集点 */
function PointPanel({
  devices,
  equipment,
}: {
  devices: MesCollectDevice[];
  equipment: MesEquipment[];
}) {
  const [rows, setRows] = useState<MesCollectPoint[]>([]);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<MesCollectPoint | null>(null);
  const [form] = Form.useForm();
  const canManage = hasPermission('sys:mes:collect:manage');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setRows((await pageMesCollectPoints()).records);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const submit = async () => {
    const values = await form.validateFields();
    await saveMesCollectPoint({ ...values, id: editing?.id });
    message.success('采集点已保存');
    setOpen(false);
    await load();
  };

  const columns: TableProps<MesCollectPoint>['columns'] = [
    { title: '采集点编码', dataIndex: 'pointCode', key: 'pointCode', width: 150 },
    { title: '名称', dataIndex: 'pointName', key: 'pointName', width: 160 },
    { title: '采集设备', dataIndex: 'deviceCode', key: 'deviceCode', width: 140 },
    { title: '设备编码', dataIndex: 'equipmentCode', key: 'equipmentCode', width: 140 },
    { title: '指标类型', dataIndex: 'metricType', key: 'metricType', width: 110 },
    { title: '单位', dataIndex: 'unit', key: 'unit', width: 80 },
    {
      title: '采集间隔(秒)',
      dataIndex: 'collectIntervalSeconds',
      key: 'collectIntervalSeconds',
      width: 120,
    },
    {
      title: '启用',
      dataIndex: 'enabled',
      key: 'enabled',
      width: 80,
      render: (value: number) => (value === 1 ? <Tag color="green">启用</Tag> : <Tag>停用</Tag>),
    },
    {
      title: '操作',
      key: 'action',
      width: 90,
      render: (_, record) =>
        canManage ? (
          <Button
            type="link"
            size="small"
            onClick={() => {
              setEditing(record);
              form.setFieldsValue(record);
              setOpen(true);
            }}
          >
            编辑
          </Button>
        ) : (
          '-'
        ),
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <Button onClick={() => void load()}>刷新</Button>
        {canManage && (
          <Button
            type="primary"
            onClick={() => {
              setEditing(null);
              form.resetFields();
              setOpen(true);
            }}
          >
            新增采集点
          </Button>
        )}
      </Space>
      <Table<MesCollectPoint>
        rowKey="id"
        size="small"
        loading={loading}
        columns={columns}
        dataSource={rows}
        pagination={{ pageSize: 10, showSizeChanger: true }}
      />

      <Modal
        title={editing ? '编辑采集点' : '新增采集点'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={() => void submit()}
        destroyOnHidden
      >
        <Form
          form={form}
          layout="vertical"
          initialValues={{ metricType: 'METRIC', collectIntervalSeconds: 60, enabled: 1 }}
        >
          <Form.Item
            name="pointCode"
            label="采集点编码"
            rules={[{ required: true, message: '请输入采集点编码' }]}
          >
            <Input />
          </Form.Item>
          <Form.Item
            name="pointName"
            label="名称"
            rules={[{ required: true, message: '请输入名称' }]}
          >
            <Input />
          </Form.Item>
          <Form.Item name="deviceId" label="采集设备">
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              options={devices.map((item) => ({
                value: item.id,
                label: `${item.deviceCode} ${item.deviceName}`,
              }))}
            />
          </Form.Item>
          <Form.Item name="deviceCode" label="采集设备编码">
            <Input />
          </Form.Item>
          <Form.Item name="equipmentId" label="关联设备">
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              options={equipment.map((item) => ({
                value: item.id,
                label: `${item.equipmentCode} ${item.equipmentName}`,
              }))}
            />
          </Form.Item>
          <Form.Item name="operationCode" label="工序编码">
            <Input />
          </Form.Item>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="metricType" label="指标类型">
                <Select
                  options={[
                    { value: 'METRIC', label: '计量值' },
                    { value: 'COUNTER', label: '计数' },
                    { value: 'STATUS', label: '状态' },
                  ]}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="unit" label="单位">
                <Input />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="collectIntervalSeconds" label="采集间隔（秒）">
            <InputNumber min={1} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="enabled" label="是否启用">
            <Select
              options={[
                { value: 1, label: '启用' },
                { value: 0, label: '停用' },
              ]}
            />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}

/** 采集样本 */
function SamplePanel({ points }: { points: MesCollectPoint[] }) {
  const [rows, setRows] = useState<MesMetricSample[]>([]);
  const [loading, setLoading] = useState(false);
  const [pointCode, setPointCode] = useState<string | undefined>(undefined);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setRows((await pageMesMetricSamples({ pointCode })).records);
    } finally {
      setLoading(false);
    }
  }, [pointCode]);

  useEffect(() => {
    void load();
  }, [load]);

  const columns: TableProps<MesMetricSample>['columns'] = [
    { title: '采集点', dataIndex: 'pointCode', key: 'pointCode', width: 150 },
    { title: '设备编码', dataIndex: 'equipmentCode', key: 'equipmentCode', width: 140 },
    { title: '数值', dataIndex: 'metricValue', key: 'metricValue', width: 120 },
    { title: '单位', dataIndex: 'unit', key: 'unit', width: 80 },
    {
      title: '采样时间',
      dataIndex: 'sampledAt',
      key: 'sampledAt',
      width: 170,
      render: (value) => formatTime(value as string | undefined),
    },
    { title: '序号', dataIndex: 'seq', key: 'seq', width: 80 },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <Select
          allowClear
          showSearch
          optionFilterProp="label"
          placeholder="采集点"
          style={{ width: 220 }}
          value={pointCode}
          options={points.map((item) => ({
            value: item.pointCode,
            label: `${item.pointCode} ${item.pointName}`,
          }))}
          onChange={(value) => setPointCode(value)}
        />
        <Button onClick={() => void load()}>查询</Button>
      </Space>
      <Table<MesMetricSample>
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

/** 看板配置与实时数据 */
function BoardPanel() {
  const [rows, setRows] = useState<MesCollectBoard[]>([]);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<MesCollectBoard | null>(null);
  const [boardData, setBoardData] = useState<MesCollectBoardData | null>(null);
  const [form] = Form.useForm();
  const canManage = hasPermission('sys:mes:collect:manage');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setRows((await pageMesCollectBoards()).records);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const submit = async () => {
    const values = await form.validateFields();
    await saveMesCollectBoard({ ...values, id: editing?.id });
    message.success('看板已保存');
    setOpen(false);
    await load();
  };

  const renderData = async (record: MesCollectBoard) => {
    setBoardData(await fetchMesCollectBoardData(record.boardCode));
  };

  const columns: TableProps<MesCollectBoard>['columns'] = [
    { title: '看板编码', dataIndex: 'boardCode', key: 'boardCode', width: 150 },
    { title: '名称', dataIndex: 'boardName', key: 'boardName', width: 160 },
    { title: '产线', dataIndex: 'lineCode', key: 'lineCode', width: 110 },
    { title: '指标', dataIndex: 'metricCodes', key: 'metricCodes' },
    { title: '刷新(秒)', dataIndex: 'refreshSeconds', key: 'refreshSeconds', width: 100 },
    {
      title: '操作',
      key: 'action',
      width: 170,
      render: (_, record) => (
        <Space size="small">
          {canManage && (
            <Button
              type="link"
              size="small"
              onClick={() => {
                setEditing(record);
                form.setFieldsValue(record);
                setOpen(true);
              }}
            >
              编辑
            </Button>
          )}
          <Button type="link" size="small" onClick={() => void renderData(record)}>
            实时数据
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <Button onClick={() => void load()}>刷新</Button>
        {canManage && (
          <Button
            type="primary"
            onClick={() => {
              setEditing(null);
              form.resetFields();
              setOpen(true);
            }}
          >
            新增看板
          </Button>
        )}
      </Space>
      <Table<MesCollectBoard>
        rowKey="id"
        size="small"
        loading={loading}
        columns={columns}
        dataSource={rows}
        pagination={{ pageSize: 10, showSizeChanger: true }}
      />

      <Modal
        title={editing ? '编辑看板' : '新增看板'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={() => void submit()}
        destroyOnHidden
      >
        <Form
          form={form}
          layout="vertical"
          initialValues={{ refreshSeconds: 30, status: 'ENABLED' }}
        >
          <Form.Item
            name="boardCode"
            label="看板编码"
            rules={[{ required: true, message: '请输入看板编码' }]}
          >
            <Input />
          </Form.Item>
          <Form.Item
            name="boardName"
            label="名称"
            rules={[{ required: true, message: '请输入名称' }]}
          >
            <Input />
          </Form.Item>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="lineId" label="产线 ID">
                <Input />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="lineCode" label="产线编码">
                <Input />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="refreshSeconds" label="刷新间隔（秒）">
            <InputNumber min={1} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="metricCodes" label="指标编码（逗号分隔）">
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item name="layout" label="布局">
            <Input placeholder="GRID / LIST" />
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
        title={`实时数据：${boardData?.board.boardCode ?? ''}`}
        open={boardData !== null}
        onCancel={() => setBoardData(null)}
        footer={null}
        destroyOnHidden
        width={720}
      >
        {boardData && (
          <>
            <Descriptions size="small" column={2} bordered style={{ marginBottom: 12 }}>
              <Descriptions.Item label="看板">{boardData.board.boardName}</Descriptions.Item>
              <Descriptions.Item label="刷新间隔">
                {boardData.board.refreshSeconds} 秒
              </Descriptions.Item>
              <Descriptions.Item label="生成时间">
                {formatTime(boardData.generatedAt)}
              </Descriptions.Item>
            </Descriptions>
            <Table<MesMetricSample>
              rowKey="id"
              size="small"
              columns={[
                { title: '采集点', dataIndex: 'pointCode', key: 'pointCode' },
                { title: '数值', dataIndex: 'metricValue', key: 'metricValue' },
                { title: '单位', dataIndex: 'unit', key: 'unit' },
                {
                  title: '采样时间',
                  dataIndex: 'sampledAt',
                  key: 'sampledAt',
                  render: (value) => formatTime(value as string | undefined),
                },
              ]}
              dataSource={boardData.latestSamples}
              pagination={false}
            />
          </>
        )}
      </Modal>
    </Space>
  );
}

/** 归档策略 */
function ArchivePanel() {
  const [rows, setRows] = useState<MesArchivePolicy[]>([]);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<MesArchivePolicy | null>(null);
  const [form] = Form.useForm();
  const canManage = hasPermission('sys:mes:collect:manage');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setRows((await pageMesArchivePolicies()).records);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const submit = async () => {
    const values = await form.validateFields();
    await saveMesArchivePolicy({ ...values, id: editing?.id });
    message.success('归档策略已保存');
    setOpen(false);
    await load();
  };

  const run = async (record: MesArchivePolicy) => {
    const result = await runMesArchivePolicy(record.id);
    message.success(`归档已执行，候选 ${result.lastCandidateCount ?? 0} 条`);
    await load();
  };

  const columns: TableProps<MesArchivePolicy>['columns'] = [
    { title: '策略编码', dataIndex: 'policyCode', key: 'policyCode', width: 150 },
    { title: '目标表', dataIndex: 'tableName', key: 'tableName', width: 200 },
    { title: '保留天数', dataIndex: 'retentionDays', key: 'retentionDays', width: 110 },
    { title: '批次大小', dataIndex: 'archiveBatchSize', key: 'archiveBatchSize', width: 110 },
    { title: 'Cron', dataIndex: 'cronExpr', key: 'cronExpr', width: 150 },
    {
      title: '上次执行',
      dataIndex: 'lastRunAt',
      key: 'lastRunAt',
      width: 160,
      render: (value) => formatTime(value as string | undefined),
    },
    { title: '候选数', dataIndex: 'lastCandidateCount', key: 'lastCandidateCount', width: 90 },
    {
      title: '操作',
      key: 'action',
      width: 150,
      render: (_, record) => (
        <Space size="small">
          {canManage && (
            <Button
              type="link"
              size="small"
              onClick={() => {
                setEditing(record);
                form.setFieldsValue(record);
                setOpen(true);
              }}
            >
              编辑
            </Button>
          )}
          <Button type="link" size="small" onClick={() => void run(record)}>
            执行
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <Button onClick={() => void load()}>刷新</Button>
        {canManage && (
          <Button
            type="primary"
            onClick={() => {
              setEditing(null);
              form.resetFields();
              setOpen(true);
            }}
          >
            新增归档策略
          </Button>
        )}
      </Space>
      <Table<MesArchivePolicy>
        rowKey="id"
        size="small"
        loading={loading}
        columns={columns}
        dataSource={rows}
        pagination={{ pageSize: 10, showSizeChanger: true }}
      />

      <Modal
        title={editing ? '编辑归档策略' : '新增归档策略'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={() => void submit()}
        destroyOnHidden
      >
        <Form
          form={form}
          layout="vertical"
          initialValues={{
            tableName: 'mes_cl_metric_sample',
            retentionDays: 365,
            archiveBatchSize: 1000,
            cronExpr: '0 0 2 * * ?',
            status: 'ENABLED',
          }}
        >
          <Form.Item
            name="policyCode"
            label="策略编码"
            rules={[{ required: true, message: '请输入策略编码' }]}
          >
            <Input />
          </Form.Item>
          <Form.Item
            name="tableName"
            label="目标表"
            rules={[{ required: true, message: '请输入目标表' }]}
          >
            <Select options={[{ value: 'mes_cl_metric_sample', label: 'mes_cl_metric_sample' }]} />
          </Form.Item>
          <Form.Item
            name="retentionDays"
            label="保留天数"
            rules={[{ required: true, message: '请输入保留天数' }]}
          >
            <InputNumber min={1} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="archiveBatchSize" label="批次大小">
            <InputNumber min={1} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="cronExpr" label="Cron 表达式">
            <Input />
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
    </Space>
  );
}

/** 采集页 */
export default function CollectPage({ initialTab = 'device' }: { initialTab?: CollectTab }) {
  const [tab, setTab] = useState<CollectTab>(initialTab);
  const [devices, setDevices] = useState<MesCollectDevice[]>([]);
  const [points, setPoints] = useState<MesCollectPoint[]>([]);
  const [equipment, setEquipment] = useState<MesEquipment[]>([]);

  const loadOptions = useCallback(async () => {
    const [devicePage, pointPage, equipmentPage] = await Promise.all([
      pageMesCollectDevices(),
      pageMesCollectPoints(),
      pageMesEquipments(),
    ]);
    setDevices(devicePage.records);
    setPoints(pointPage.records);
    setEquipment(equipmentPage.records);
  }, []);

  useEffect(() => {
    void loadOptions();
  }, [loadOptions]);

  useEffect(() => {
    setTab(initialTab);
  }, [initialTab]);

  const items: TabsProps['items'] = [
    { key: 'device', label: '采集设备', children: <DevicePanel /> },
    {
      key: 'point',
      label: '采集点',
      children: <PointPanel devices={devices} equipment={equipment} />,
    },
    { key: 'sample', label: '样本明细', children: <SamplePanel points={points} /> },
    { key: 'board', label: '采集看板', children: <BoardPanel /> },
    { key: 'archive', label: '归档策略', children: <ArchivePanel /> },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Typography.Title level={5} style={{ margin: 0 }}>
        数据采集
      </Typography.Title>
      <Tabs
        activeKey={tab}
        onChange={(key) => setTab(key as CollectTab)}
        items={items}
        destroyInactiveTabPane
      />
    </Space>
  );
}
