// MES 物料与追溯页：条码 / 批次 / 正反追溯 / 线边库存 / 叫料补货 / 扫码防错
import { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Button,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  Typography,
  message,
} from 'antd';
import type { TableProps, TabsProps } from 'antd';
import dayjs from 'dayjs';
import {
  closeMesBatch,
  createMesBatch,
  fetchMesTrace,
  generateMesBarcodes,
  inboundMesLineSide,
  issueMesLineSide,
  linkMesBatches,
  listMesMasterDataOptions,
  pageMesBarcodeRules,
  pageMesBarcodes,
  pageMesBatches,
  pageMesLineSideMovements,
  pageMesLineSideStock,
  pageMesReplenishments,
  pageMesScanRules,
  requestMesReplenishment,
  saveMesBarcodeRule,
  saveMesScanRule,
  validateMesScan,
  voidMesBarcode,
  type MesBarcode,
  type MesBarcodeRule,
  type MesBatch,
  type MesBatchInput,
  type MesLineSideStock,
  type MesLineSideMovement,
  type MesMasterData,
  type MesReplenishment,
  type MesScanResult,
  type MesScanRule,
  type MesTraceNode,
} from '../api/mes';
import { hasPermission } from '../utils/permission';

/** 页签键 */
export type TraceTab = 'barcode' | 'batch' | 'query' | 'stock' | 'replenish' | 'scan';

/** 批次类型文案 */
const BATCH_TYPE_LABEL: Record<string, string> = {
  RAW: '来料',
  SEMI: '半成品',
  FINISHED: '成品',
};

/** 批次状态颜色 */
const BATCH_STATUS_COLOR: Record<string, string> = {
  ACTIVE: 'green',
  CLOSED: 'default',
  VOID: 'red',
  REQUESTED: 'processing',
  CONFIRMED: 'green',
  CANCELLED: 'default',
};

/** 数量展示 */
function formatQuantity(value?: number | string): string {
  return value === undefined || value === null || value === '' ? '-' : String(value);
}

/** 条码管理面板 */
function BarcodePanel() {
  const [rules, setRules] = useState<MesBarcodeRule[]>([]);
  const [barcodes, setBarcodes] = useState<MesBarcode[]>([]);
  const [batches, setBatches] = useState<MesBatch[]>([]);
  const [ruleCode, setRuleCode] = useState<string>();
  const [barcodeValue, setBarcodeValue] = useState<string>();
  const [loading, setLoading] = useState(false);
  const [generateOpen, setGenerateOpen] = useState(false);
  const [ruleOpen, setRuleOpen] = useState(false);
  const [editingRule, setEditingRule] = useState<MesBarcodeRule | null>(null);
  const [generateForm] = Form.useForm<{
    ruleId: string;
    batchId: string;
    quantity: number;
    remark?: string;
  }>();
  const [ruleForm] = Form.useForm<{
    ruleCode: string;
    ruleName: string;
    prefix: string;
    codeType: 'CODE128' | 'QR';
    serialLength: number;
    status: 'ENABLED' | 'DISABLED';
    remark?: string;
  }>();

  const loadRules = useCallback(async () => {
    const page = await pageMesBarcodeRules({ ruleCode });
    setRules(page.records);
  }, [ruleCode]);

  const loadBarcodes = useCallback(async () => {
    const page = await pageMesBarcodes({ barcodeValue });
    setBarcodes(page.records);
  }, [barcodeValue]);

  const loadBatches = useCallback(async () => {
    const page = await pageMesBatches({ status: 'ACTIVE' });
    setBatches(page.records);
  }, []);

  const reload = useCallback(async () => {
    setLoading(true);
    try {
      await Promise.all([loadRules(), loadBarcodes(), loadBatches()]);
    } finally {
      setLoading(false);
    }
  }, [loadRules, loadBarcodes, loadBatches]);

  useEffect(() => {
    void reload();
  }, [reload]);

  const openGenerate = () => {
    generateForm.resetFields();
    generateForm.setFieldsValue({ quantity: 10 });
    setGenerateOpen(true);
  };

  const submitGenerate = async () => {
    const values = await generateForm.validateFields().catch(() => null);
    if (!values) return;
    try {
      const created = await generateMesBarcodes(values);
      message.success(`已生成 ${created.length} 个条码`);
      setGenerateOpen(false);
      await reload();
    } catch {
      message.error('条码生成失败：请确认规则启用且批次未封批');
    }
  };

  const openRule = (rule: MesBarcodeRule | null) => {
    setEditingRule(rule);
    ruleForm.resetFields();
    ruleForm.setFieldsValue(
      rule
        ? {
            ruleCode: rule.ruleCode,
            ruleName: rule.ruleName,
            prefix: rule.prefix,
            codeType: rule.codeType,
            serialLength: rule.serialLength,
            status: rule.status,
            remark: rule.remark,
          }
        : {
            prefix: 'BW',
            codeType: 'CODE128',
            serialLength: 6,
            status: 'ENABLED',
          },
    );
    setRuleOpen(true);
  };

  const submitRule = async () => {
    const values = await ruleForm.validateFields().catch(() => null);
    if (!values) return;
    try {
      await saveMesBarcodeRule({ ...values, id: editingRule?.id });
      message.success('条码规则已保存');
      setRuleOpen(false);
      await reload();
    } catch {
      message.error('保存失败：规则编码必须唯一');
    }
  };

  const ruleColumns: TableProps<MesBarcodeRule>['columns'] = [
    { title: '规则编码', dataIndex: 'ruleCode', key: 'ruleCode', width: 140 },
    { title: '规则名称', dataIndex: 'ruleName', key: 'ruleName', width: 160 },
    { title: '前缀', dataIndex: 'prefix', key: 'prefix', width: 100 },
    { title: '码制', dataIndex: 'codeType', key: 'codeType', width: 100 },
    { title: '流水号长度', dataIndex: 'serialLength', key: 'serialLength', width: 110 },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 90,
      render: (value: string) => (
        <Tag color={value === 'ENABLED' ? 'green' : 'default'}>
          {value === 'ENABLED' ? '启用' : '停用'}
        </Tag>
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 100,
      render: (_, record) =>
        hasPermission('sys:mes:trace:barcode') ? (
          <Button type="link" size="small" onClick={() => openRule(record)}>
            编辑
          </Button>
        ) : null,
    },
  ];

  const barcodeColumns: TableProps<MesBarcode>['columns'] = [
    { title: '条码', dataIndex: 'barcodeValue', key: 'barcodeValue', width: 220 },
    { title: '批次号', dataIndex: 'batchNo', key: 'batchNo', width: 170 },
    { title: '物料编码', dataIndex: 'itemCode', key: 'itemCode', width: 130 },
    { title: '码制', dataIndex: 'codeType', key: 'codeType', width: 90 },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 90,
      render: (value: string) => (
        <Tag color={value === 'ACTIVE' ? 'green' : 'red'}>
          {value === 'ACTIVE' ? '有效' : '已作废'}
        </Tag>
      ),
    },
    { title: '扫码次数', dataIndex: 'scanCount', key: 'scanCount', width: 90 },
    {
      title: '操作',
      key: 'action',
      width: 90,
      render: (_, record) =>
        record.status === 'ACTIVE' && hasPermission('sys:mes:trace:barcode') ? (
          <Popconfirm
            title="确认作废该条码？"
            okText="作废"
            cancelText="取消"
            onConfirm={async () => {
              await voidMesBarcode(record.id);
              message.success('条码已作废');
              await reload();
            }}
          >
            <Button type="link" size="small" danger>
              作废
            </Button>
          </Popconfirm>
        ) : null,
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <Typography.Title level={5} style={{ margin: 0 }}>
          条码规则
        </Typography.Title>
        <Input
          allowClear
          placeholder="规则编码"
          style={{ width: 160 }}
          onChange={(event) => setRuleCode(event.target.value || undefined)}
        />
        {hasPermission('sys:mes:trace:barcode') ? (
          <Button onClick={() => openRule(null)}>新增规则</Button>
        ) : null}
        <Button
          type="primary"
          onClick={openGenerate}
          disabled={!hasPermission('sys:mes:trace:barcode')}
        >
          生成条码
        </Button>
      </Space>
      <Table<MesBarcodeRule>
        rowKey="id"
        size="small"
        loading={loading}
        columns={ruleColumns}
        dataSource={rules}
        pagination={false}
      />
      <Space wrap>
        <Typography.Title level={5} style={{ margin: 0 }}>
          条码列表
        </Typography.Title>
        <Input.Search
          allowClear
          placeholder="条码值"
          style={{ width: 240 }}
          onSearch={(value) => setBarcodeValue(value || undefined)}
        />
      </Space>
      <Table<MesBarcode>
        rowKey="id"
        loading={loading}
        columns={barcodeColumns}
        dataSource={barcodes}
        scroll={{ x: 900 }}
      />
      <Modal
        open={generateOpen}
        title="生成条码"
        onOk={submitGenerate}
        onCancel={() => setGenerateOpen(false)}
        okText="生成"
        cancelText="取消"
        destroyOnClose
      >
        <Form form={generateForm} layout="vertical">
          <Form.Item
            name="ruleId"
            label="条码规则"
            rules={[{ required: true, message: '请选择规则' }]}
          >
            <Select
              options={rules
                .filter((rule) => rule.status === 'ENABLED')
                .map((rule) => ({ value: rule.id, label: `${rule.ruleCode} ${rule.ruleName}` }))}
            />
          </Form.Item>
          <Form.Item
            name="batchId"
            label="绑定批次"
            rules={[{ required: true, message: '请选择批次' }]}
          >
            <Select
              showSearch
              optionFilterProp="label"
              options={batches.map((batch) => ({
                value: batch.id,
                label: `${batch.batchNo} ${batch.itemCode}`,
              }))}
            />
          </Form.Item>
          <Form.Item
            name="quantity"
            label="生成数量"
            rules={[{ required: true, message: '请填写数量' }]}
          >
            <InputNumber min={1} max={1000} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input maxLength={255} />
          </Form.Item>
        </Form>
      </Modal>
      <Modal
        open={ruleOpen}
        title={editingRule ? '编辑条码规则' : '新增条码规则'}
        onOk={submitRule}
        onCancel={() => setRuleOpen(false)}
        okText="保存"
        cancelText="取消"
        destroyOnClose
      >
        <Form form={ruleForm} layout="vertical">
          <Form.Item
            name="ruleCode"
            label="规则编码"
            rules={[{ required: true, message: '请填写规则编码' }]}
          >
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item
            name="ruleName"
            label="规则名称"
            rules={[{ required: true, message: '请填写规则名称' }]}
          >
            <Input maxLength={128} />
          </Form.Item>
          <Form.Item
            name="prefix"
            label="条码前缀"
            rules={[{ required: true, message: '请填写前缀' }]}
          >
            <Input maxLength={32} />
          </Form.Item>
          <Form.Item name="codeType" label="码制">
            <Select
              options={[
                { value: 'CODE128', label: 'Code128' },
                { value: 'QR', label: 'QR' },
              ]}
            />
          </Form.Item>
          <Form.Item name="serialLength" label="流水号长度">
            <InputNumber min={4} max={12} style={{ width: '100%' }} />
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
            <Input.TextArea rows={2} maxLength={255} />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}

/** 批次与谱系面板 */
function BatchPanel() {
  const [batches, setBatches] = useState<MesBatch[]>([]);
  const [batchNo, setBatchNo] = useState<string>();
  const [loading, setLoading] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [linkOpen, setLinkOpen] = useState(false);
  const [createForm] = Form.useForm();
  const [linkForm] = Form.useForm<{
    parentBatchNo: string;
    childBatchNo: string;
    quantity: number;
    uom?: string;
    remark?: string;
  }>();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageMesBatches({ batchNo });
      setBatches(page.records);
    } finally {
      setLoading(false);
    }
  }, [batchNo]);

  useEffect(() => {
    void load();
  }, [load]);

  const submitCreate = async () => {
    const values = await createForm.validateFields().catch(() => null);
    if (!values) return;
    try {
      const input: MesBatchInput = {
        batchNo: values.batchNo || undefined,
        itemCode: values.itemCode,
        itemName: values.itemName || undefined,
        batchType: values.batchType,
        sourceType: values.sourceType,
        quantity: values.quantity,
        uom: values.uom || undefined,
        productionDate: values.productionDate
          ? dayjs(values.productionDate).format('YYYY-MM-DD')
          : undefined,
        remark: values.remark || undefined,
      };
      await createMesBatch(input);
      message.success('批次已建档');
      setCreateOpen(false);
      await load();
    } catch {
      message.error('建档失败：批次号必须租户内唯一');
    }
  };

  const submitLink = async () => {
    const values = await linkForm.validateFields().catch(() => null);
    if (!values) return;
    try {
      await linkMesBatches(values);
      message.success('谱系链接已建立');
      setLinkOpen(false);
      await load();
    } catch {
      message.error('建链失败：请确认消耗量不超过投入量且不成环');
    }
  };

  const columns: TableProps<MesBatch>['columns'] = [
    { title: '批次号', dataIndex: 'batchNo', key: 'batchNo', width: 190 },
    { title: '物料编码', dataIndex: 'itemCode', key: 'itemCode', width: 130 },
    { title: '物料名称', dataIndex: 'itemName', key: 'itemName', width: 150 },
    {
      title: '类型',
      dataIndex: 'batchType',
      key: 'batchType',
      width: 100,
      render: (value: string) => BATCH_TYPE_LABEL[value] ?? value,
    },
    {
      title: '数量 / 已消耗 / 剩余',
      key: 'quantity',
      width: 200,
      render: (_, record) =>
        `${formatQuantity(record.quantity)} / ${formatQuantity(record.consumedQuantity)} / ${formatQuantity(record.remainingQuantity)}`,
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 90,
      render: (value: string) => (
        <Tag color={BATCH_STATUS_COLOR[value]}>{value === 'ACTIVE' ? '有效' : '已封批'}</Tag>
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 100,
      render: (_, record) =>
        record.status === 'ACTIVE' && hasPermission('sys:mes:trace:batch') ? (
          <Popconfirm
            title="确认封批？封批后不可再作为谱系父批次消耗"
            okText="封批"
            cancelText="取消"
            onConfirm={async () => {
              await closeMesBatch(record.id);
              message.success('批次已封批');
              await load();
            }}
          >
            <Button type="link" size="small">
              封批
            </Button>
          </Popconfirm>
        ) : null,
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <Typography.Title level={5} style={{ margin: 0 }}>
          批次管理
        </Typography.Title>
        <Input.Search
          allowClear
          placeholder="批次号"
          style={{ width: 200 }}
          onSearch={(value) => setBatchNo(value || undefined)}
        />
        <Button
          onClick={() => {
            createForm.resetFields();
            createForm.setFieldsValue({ batchType: 'RAW', sourceType: 'PRODUCTION' });
            setCreateOpen(true);
          }}
        >
          批次建档
        </Button>
        <Button
          onClick={() => {
            linkForm.resetFields();
            setLinkOpen(true);
          }}
        >
          谱系建链
        </Button>
      </Space>
      <Table<MesBatch>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={batches}
        scroll={{ x: 1000 }}
      />
      <Modal
        open={createOpen}
        title="批次建档"
        onOk={submitCreate}
        onCancel={() => setCreateOpen(false)}
        okText="保存"
        cancelText="取消"
        destroyOnClose
      >
        <Form form={createForm} layout="vertical">
          <Form.Item name="batchNo" label="批次号（空则自动生成）">
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item
            name="itemCode"
            label="物料编码"
            rules={[{ required: true, message: '请填写物料编码' }]}
          >
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item name="itemName" label="物料名称">
            <Input maxLength={128} />
          </Form.Item>
          <Form.Item name="batchType" label="批次类型">
            <Select
              options={[
                { value: 'RAW', label: '来料' },
                { value: 'SEMI', label: '半成品' },
                { value: 'FINISHED', label: '成品' },
              ]}
            />
          </Form.Item>
          <Form.Item
            name="quantity"
            label="批次数量"
            rules={[{ required: true, message: '请填写数量' }]}
          >
            <InputNumber min={0.0001} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="uom" label="计量单位">
            <Input maxLength={16} />
          </Form.Item>
          <Form.Item name="productionDate" label="生产/到货日期">
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} maxLength={255} />
          </Form.Item>
        </Form>
      </Modal>
      <Modal
        open={linkOpen}
        title="批次谱系建链"
        onOk={submitLink}
        onCancel={() => setLinkOpen(false)}
        okText="建链"
        cancelText="取消"
        destroyOnClose
      >
        <Form form={linkForm} layout="vertical">
          <Form.Item
            name="parentBatchNo"
            label="上级批次号（被消耗）"
            rules={[{ required: true, message: '请填写上级批次号' }]}
          >
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item
            name="childBatchNo"
            label="下级批次号（产出）"
            rules={[{ required: true, message: '请填写下级批次号' }]}
          >
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item
            name="quantity"
            label="消耗数量"
            rules={[{ required: true, message: '请填写消耗数量' }]}
          >
            <InputNumber min={0.0001} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="uom" label="计量单位">
            <Input maxLength={16} />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} maxLength={255} />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}

/** 正反向追溯面板 */
function TraceQueryPanel() {
  const [nodes, setNodes] = useState<MesTraceNode[]>([]);
  const [batchNo, setBatchNo] = useState('');
  const [depth, setDepth] = useState(3);
  const [direction, setDirection] = useState<'forward' | 'backward'>('forward');
  const [loading, setLoading] = useState(false);

  const runTrace = async (nextDirection: 'forward' | 'backward') => {
    if (!batchNo.trim()) {
      message.warning('请输入批次号');
      return;
    }
    setLoading(true);
    setDirection(nextDirection);
    try {
      setNodes(await fetchMesTrace(nextDirection, batchNo.trim(), depth));
    } catch {
      message.error('追溯查询失败');
      setNodes([]);
    } finally {
      setLoading(false);
    }
  };

  const columns: TableProps<MesTraceNode>['columns'] = [
    {
      title: '层级',
      dataIndex: 'level',
      key: 'level',
      width: 80,
      render: (value: number) => `L${value}`,
    },
    { title: '批次号', dataIndex: 'batchNo', key: 'batchNo', width: 200 },
    { title: '物料编码', dataIndex: 'itemCode', key: 'itemCode', width: 140 },
    { title: '物料名称', dataIndex: 'itemName', key: 'itemName', width: 160 },
    {
      title: '类型',
      dataIndex: 'batchType',
      key: 'batchType',
      width: 100,
      render: (value: string) => BATCH_TYPE_LABEL[value] ?? value,
    },
    {
      title: '关系',
      dataIndex: 'relation',
      key: 'relation',
      width: 90,
      render: (value: string) => (
        <Tag color={value === 'CHILD' ? 'blue' : 'purple'}>
          {value === 'CHILD' ? '下游' : '上游'}
        </Tag>
      ),
    },
    {
      title: '数量',
      key: 'quantity',
      width: 130,
      render: (_, record) => `${formatQuantity(record.quantity)} ${record.uom ?? ''}`.trim(),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 90,
      render: (value: string) => (value === 'ACTIVE' ? '有效' : '已封批'),
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Typography.Title level={5} style={{ margin: 0 }}>
        正反向追溯
      </Typography.Title>
      <Space wrap>
        <Input
          placeholder="批次号"
          style={{ width: 240 }}
          value={batchNo}
          onChange={(event) => setBatchNo(event.target.value)}
        />
        <InputNumber
          min={1}
          max={10}
          value={depth}
          onChange={(value) => setDepth(value ?? 3)}
          addonBefore="层级"
        />
        <Button type="primary" loading={loading} onClick={() => runTrace('forward')}>
          正向追溯（下游）
        </Button>
        <Button loading={loading} onClick={() => runTrace('backward')}>
          反向追溯（上游）
        </Button>
        <Tag color="blue">当前方向：{direction === 'forward' ? '正向' : '反向'}</Tag>
      </Space>
      <Table<MesTraceNode>
        rowKey={(record) => `${record.level}-${record.batchId}`}
        loading={loading}
        columns={columns}
        dataSource={nodes}
        scroll={{ x: 1000 }}
      />
    </Space>
  );
}

/** 线边库存面板 */
function StockPanel() {
  const [stocks, setStocks] = useState<MesLineSideStock[]>([]);
  const [movements, setMovements] = useState<MesLineSideMovement[]>([]);
  const [lines, setLines] = useState<MesMasterData[]>([]);
  const [lineId, setLineId] = useState<string>();
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [modalMode, setModalMode] = useState<'inbound' | 'issue'>('inbound');
  const [form] = Form.useForm();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [stockPage, movementPage] = await Promise.all([
        pageMesLineSideStock({ lineId }),
        pageMesLineSideMovements({ lineId }),
      ]);
      setStocks(stockPage.records);
      setMovements(movementPage.records);
    } finally {
      setLoading(false);
    }
  }, [lineId]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    void (async () => {
      setLines(await listMesMasterDataOptions('line'));
    })();
  }, []);

  const openModal = (mode: 'inbound' | 'issue') => {
    setModalMode(mode);
    form.resetFields();
    form.setFieldsValue({ lineId, movementType: 'INBOUND' });
    setModalOpen(true);
  };

  const submit = async () => {
    const values = await form.validateFields().catch(() => null);
    if (!values) return;
    const line = lines.find((item) => item.id === values.lineId);
    const input = { ...values, lineCode: line?.code ?? '' };
    try {
      if (modalMode === 'inbound') {
        await inboundMesLineSide(input);
        message.success('线边入库成功');
      } else {
        await issueMesLineSide(input);
        message.success('线边领用成功');
      }
      setModalOpen(false);
      await load();
    } catch {
      message.error(modalMode === 'inbound' ? '入库失败' : '领用失败：请确认线边库存充足');
    }
  };

  const stockColumns: TableProps<MesLineSideStock>['columns'] = [
    { title: '产线', dataIndex: 'lineCode', key: 'lineCode', width: 120 },
    { title: '线边仓', dataIndex: 'warehouseCode', key: 'warehouseCode', width: 120 },
    { title: '物料编码', dataIndex: 'itemCode', key: 'itemCode', width: 140 },
    { title: '物料名称', dataIndex: 'itemName', key: 'itemName', width: 150 },
    { title: '批次号', dataIndex: 'batchNo', key: 'batchNo', width: 170 },
    {
      title: '库存 / 安全库存',
      key: 'quantity',
      width: 150,
      render: (_, record) =>
        `${formatQuantity(record.quantity)} / ${formatQuantity(record.safetyQuantity)}`,
    },
    {
      title: '状态',
      key: 'belowSafety',
      width: 100,
      render: (_, record) => (
        <Tag color={record.belowSafety ? 'red' : 'green'}>
          {record.belowSafety ? '低于安全库存' : '正常'}
        </Tag>
      ),
    },
  ];

  const movementColumns: TableProps<MesLineSideMovement>['columns'] = [
    { title: '流水号', dataIndex: 'movementNo', key: 'movementNo', width: 190 },
    { title: '物料编码', dataIndex: 'itemCode', key: 'itemCode', width: 140 },
    { title: '批次号', dataIndex: 'batchNo', key: 'batchNo', width: 170 },
    { title: '类型', dataIndex: 'movementType', key: 'movementType', width: 100 },
    {
      title: '变动',
      key: 'change',
      width: 160,
      render: (_, record) =>
        `${record.direction > 0 ? '+' : '-'}${formatQuantity(record.quantity)}（${formatQuantity(record.beforeQuantity)} → ${formatQuantity(record.afterQuantity)}）`,
    },
    { title: '操作人', dataIndex: 'operator', key: 'operator', width: 100 },
    { title: '时间', dataIndex: 'occurredAt', key: 'occurredAt', width: 170 },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <Typography.Title level={5} style={{ margin: 0 }}>
          线边库存
        </Typography.Title>
        <Select
          allowClear
          placeholder="产线"
          style={{ width: 180 }}
          value={lineId}
          onChange={(value) => setLineId(value)}
          options={lines.map((item) => ({ value: item.id, label: `${item.code} ${item.name}` }))}
        />
        <Button
          disabled={!hasPermission('sys:mes:trace:line-side')}
          onClick={() => openModal('inbound')}
        >
          入库
        </Button>
        <Button
          disabled={!hasPermission('sys:mes:trace:line-side')}
          onClick={() => openModal('issue')}
        >
          领用
        </Button>
      </Space>
      <Table<MesLineSideStock>
        rowKey="id"
        loading={loading}
        columns={stockColumns}
        dataSource={stocks}
        scroll={{ x: 1100 }}
      />
      <Typography.Title level={5} style={{ margin: 0 }}>
        线边流水
      </Typography.Title>
      <Table<MesLineSideMovement>
        rowKey="id"
        size="small"
        loading={loading}
        columns={movementColumns}
        dataSource={movements}
        scroll={{ x: 1100 }}
      />
      <Modal
        open={modalOpen}
        title={modalMode === 'inbound' ? '线边入库' : '线边领用'}
        onOk={submit}
        onCancel={() => setModalOpen(false)}
        okText="提交"
        cancelText="取消"
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item name="lineId" label="产线" rules={[{ required: true, message: '请选择产线' }]}>
            <Select
              options={lines.map((item) => ({
                value: item.id,
                label: `${item.code} ${item.name}`,
              }))}
            />
          </Form.Item>
          <Form.Item
            name="warehouseCode"
            label="线边仓编码"
            rules={[{ required: true, message: '请填写线边仓编码' }]}
          >
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item
            name="itemCode"
            label="物料编码"
            rules={[{ required: true, message: '请填写物料编码' }]}
          >
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item name="itemName" label="物料名称">
            <Input maxLength={128} />
          </Form.Item>
          <Form.Item name="batchNo" label="批次号">
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item
            name="quantity"
            label="数量"
            rules={[{ required: true, message: '请填写数量' }]}
          >
            <InputNumber min={0.0001} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="uom" label="计量单位">
            <Input maxLength={16} />
          </Form.Item>
          <Form.Item name="operator" label="操作人">
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} maxLength={255} />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}

/** 叫料补货面板 */
function ReplenishPanel() {
  const [records, setRecords] = useState<MesReplenishment[]>([]);
  const [lines, setLines] = useState<MesMasterData[]>([]);
  const [lineId, setLineId] = useState<string>();
  const [status, setStatus] = useState<string>();
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [form] = Form.useForm();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageMesReplenishments({ lineId, status });
      setRecords(page.records);
    } finally {
      setLoading(false);
    }
  }, [lineId, status]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    void (async () => {
      setLines(await listMesMasterDataOptions('line'));
    })();
  }, []);

  const submit = async () => {
    const values = await form.validateFields().catch(() => null);
    if (!values) return;
    const line = lines.find((item) => item.id === values.lineId);
    try {
      const created = await requestMesReplenishment({ ...values, lineCode: line?.code ?? '' });
      message.success(`叫料请求已提交：${created.requestNo}`);
      setModalOpen(false);
      await load();
    } catch {
      message.error('叫料失败：请确认数量与产线');
    }
  };

  const columns: TableProps<MesReplenishment>['columns'] = [
    { title: '请求号', dataIndex: 'requestNo', key: 'requestNo', width: 210 },
    { title: '产线', dataIndex: 'lineCode', key: 'lineCode', width: 110 },
    { title: '线边仓', dataIndex: 'warehouseCode', key: 'warehouseCode', width: 120 },
    { title: '物料编码', dataIndex: 'itemCode', key: 'itemCode', width: 140 },
    { title: '数量', dataIndex: 'quantity', key: 'quantity', width: 100 },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (value: string) => (
        <Tag color={BATCH_STATUS_COLOR[value]}>
          {value === 'REQUESTED' ? '待处理' : value === 'CONFIRMED' ? '已确认' : '已取消'}
        </Tag>
      ),
    },
    { title: '原因', dataIndex: 'reason', key: 'reason', width: 180 },
    { title: '叫料人', dataIndex: 'requestedBy', key: 'requestedBy', width: 100 },
    { title: '时间', dataIndex: 'requestedAt', key: 'requestedAt', width: 170 },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <Typography.Title level={5} style={{ margin: 0 }}>
          叫料补货
        </Typography.Title>
        <Select
          allowClear
          placeholder="产线"
          style={{ width: 180 }}
          value={lineId}
          onChange={(value) => setLineId(value)}
          options={lines.map((item) => ({ value: item.id, label: `${item.code} ${item.name}` }))}
        />
        <Select
          allowClear
          placeholder="状态"
          style={{ width: 140 }}
          value={status}
          onChange={(value) => setStatus(value)}
          options={[
            { value: 'REQUESTED', label: '待处理' },
            { value: 'CONFIRMED', label: '已确认' },
            { value: 'CANCELLED', label: '已取消' },
          ]}
        />
        <Button
          type="primary"
          disabled={!hasPermission('sys:mes:trace:replenish')}
          onClick={() => {
            form.resetFields();
            form.setFieldsValue({ lineId });
            setModalOpen(true);
          }}
        >
          发起叫料
        </Button>
      </Space>
      <Table<MesReplenishment>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={records}
        scroll={{ x: 1200 }}
      />
      <Modal
        open={modalOpen}
        title="发起叫料补货"
        onOk={submit}
        onCancel={() => setModalOpen(false)}
        okText="提交"
        cancelText="取消"
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item name="lineId" label="产线" rules={[{ required: true, message: '请选择产线' }]}>
            <Select
              options={lines.map((item) => ({
                value: item.id,
                label: `${item.code} ${item.name}`,
              }))}
            />
          </Form.Item>
          <Form.Item
            name="warehouseCode"
            label="线边仓编码"
            rules={[{ required: true, message: '请填写线边仓编码' }]}
          >
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item
            name="itemCode"
            label="物料编码"
            rules={[{ required: true, message: '请填写物料编码' }]}
          >
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item name="itemName" label="物料名称">
            <Input maxLength={128} />
          </Form.Item>
          <Form.Item
            name="quantity"
            label="叫料数量"
            rules={[{ required: true, message: '请填写数量' }]}
          >
            <InputNumber min={0.0001} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="uom" label="计量单位">
            <Input maxLength={16} />
          </Form.Item>
          <Form.Item name="reason" label="叫料原因">
            <Input.TextArea rows={2} maxLength={255} />
          </Form.Item>
          <Form.Item name="requestedBy" label="叫料人">
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} maxLength={255} />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}

/** 扫码防错面板 */
function ScanPanel() {
  const [rules, setRules] = useState<MesScanRule[]>([]);
  const [result, setResult] = useState<MesScanResult | null>(null);
  const [loading, setLoading] = useState(false);
  const [ruleOpen, setRuleOpen] = useState(false);
  const [editing, setEditing] = useState<MesScanRule | null>(null);
  const [scanForm] = Form.useForm<{
    barcodeValue: string;
    stationCode?: string;
    operationCode?: string;
    scanNo?: string;
  }>();
  const [ruleForm] = Form.useForm();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageMesScanRules();
      setRules(page.records);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const submitScan = async () => {
    const values = await scanForm.validateFields().catch(() => null);
    if (!values) return;
    try {
      setResult(await validateMesScan(values));
    } catch {
      message.error('扫码校验失败');
    }
  };

  const openRule = (rule: MesScanRule | null) => {
    setEditing(rule);
    ruleForm.resetFields();
    ruleForm.setFieldsValue(
      rule
        ? {
            ruleCode: rule.ruleCode,
            ruleName: rule.ruleName,
            stationCode: rule.stationCode,
            operationCode: rule.operationCode,
            expectedItemCode: rule.expectedItemCode,
            action: rule.action,
            status: rule.status,
            remark: rule.remark,
          }
        : { action: 'BLOCK', status: 'ENABLED' },
    );
    setRuleOpen(true);
  };

  const submitRule = async () => {
    const values = await ruleForm.validateFields().catch(() => null);
    if (!values) return;
    try {
      await saveMesScanRule({ ...values, id: editing?.id });
      message.success('扫码规则已保存');
      setRuleOpen(false);
      await load();
    } catch {
      message.error('保存失败：规则编码必须唯一');
    }
  };

  const columns: TableProps<MesScanRule>['columns'] = [
    { title: '规则编码', dataIndex: 'ruleCode', key: 'ruleCode', width: 140 },
    { title: '规则名称', dataIndex: 'ruleName', key: 'ruleName', width: 160 },
    { title: '工位', dataIndex: 'stationCode', key: 'stationCode', width: 110 },
    { title: '工序', dataIndex: 'operationCode', key: 'operationCode', width: 110 },
    { title: '期望物料', dataIndex: 'expectedItemCode', key: 'expectedItemCode', width: 140 },
    {
      title: '处理',
      dataIndex: 'action',
      key: 'action',
      width: 90,
      render: (value: string) => (
        <Tag color={value === 'BLOCK' ? 'red' : 'orange'}>
          {value === 'BLOCK' ? '阻断' : '告警'}
        </Tag>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 90,
      render: (value: string) => (
        <Tag color={value === 'ENABLED' ? 'green' : 'default'}>
          {value === 'ENABLED' ? '启用' : '停用'}
        </Tag>
      ),
    },
    {
      title: '操作',
      key: 'op',
      width: 90,
      render: (_, record) =>
        hasPermission('sys:mes:trace:scan') ? (
          <Button type="link" size="small" onClick={() => openRule(record)}>
            编辑
          </Button>
        ) : null,
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Typography.Title level={5} style={{ margin: 0 }}>
        扫码校验
      </Typography.Title>
      <Form form={scanForm} layout="inline">
        <Form.Item name="barcodeValue" rules={[{ required: true, message: '请输入条码' }]}>
          <Input placeholder="扫描条码" style={{ width: 240 }} />
        </Form.Item>
        <Form.Item name="stationCode">
          <Input placeholder="工位编码" style={{ width: 140 }} />
        </Form.Item>
        <Form.Item name="operationCode">
          <Input placeholder="工序编码" style={{ width: 140 }} />
        </Form.Item>
        <Form.Item name="scanNo">
          <Input placeholder="扫码幂等键（可选）" style={{ width: 180 }} />
        </Form.Item>
        <Form.Item>
          <Button
            type="primary"
            disabled={!hasPermission('sys:mes:trace:scan')}
            onClick={submitScan}
          >
            校验
          </Button>
        </Form.Item>
      </Form>
      {result ? (
        <Alert
          type={result.blocked ? 'error' : result.duplicate ? 'info' : 'success'}
          showIcon
          message={result.message}
          description={`条码 ${result.barcodeValue}｜批次 ${result.batchNo ?? '-'}｜物料 ${result.itemCode ?? '-'}`}
          closable
          onClose={() => setResult(null)}
        />
      ) : null}
      <Space wrap>
        <Typography.Title level={5} style={{ margin: 0 }}>
          扫码规则
        </Typography.Title>
        <Button onClick={() => openRule(null)} disabled={!hasPermission('sys:mes:trace:scan')}>
          新增规则
        </Button>
      </Space>
      <Table<MesScanRule>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={rules}
        scroll={{ x: 1000 }}
      />
      <Modal
        open={ruleOpen}
        title={editing ? '编辑扫码规则' : '新增扫码规则'}
        onOk={submitRule}
        onCancel={() => setRuleOpen(false)}
        okText="保存"
        cancelText="取消"
        destroyOnClose
      >
        <Form form={ruleForm} layout="vertical">
          <Form.Item
            name="ruleCode"
            label="规则编码"
            rules={[{ required: true, message: '请填写规则编码' }]}
          >
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item
            name="ruleName"
            label="规则名称"
            rules={[{ required: true, message: '请填写规则名称' }]}
          >
            <Input maxLength={128} />
          </Form.Item>
          <Form.Item name="stationCode" label="适用工位（空表示不限）">
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item name="operationCode" label="适用工序（空表示不限）">
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item name="expectedItemCode" label="期望物料编码（空表示仅校验有效性）">
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item name="action" label="命中处理">
            <Select
              options={[
                { value: 'BLOCK', label: '阻断' },
                { value: 'WARN', label: '告警' },
              ]}
            />
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
            <Input.TextArea rows={2} maxLength={255} />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}

/** 物料与追溯页 */
export default function TracePage({ initialTab = 'barcode' }: { initialTab?: TraceTab }) {
  const [activeKey, setActiveKey] = useState<TraceTab>(initialTab);

  useEffect(() => {
    setActiveKey(initialTab);
  }, [initialTab]);

  const items: TabsProps['items'] = [
    { key: 'barcode', label: '条码管理', children: <BarcodePanel /> },
    { key: 'batch', label: '批次管理', children: <BatchPanel /> },
    { key: 'query', label: '追溯查询', children: <TraceQueryPanel /> },
    { key: 'stock', label: '线边库存', children: <StockPanel /> },
    { key: 'replenish', label: '叫料补货', children: <ReplenishPanel /> },
    { key: 'scan', label: '扫码防错', children: <ScanPanel /> },
  ];

  return (
    <Tabs activeKey={activeKey} onChange={(key) => setActiveKey(key as TraceTab)} items={items} />
  );
}
