// ERP 质量检验页：检验单（IQC/IPQC/FQC）录入与判定、不合格品处置、质量成本
import { useCallback, useEffect, useState } from 'react';
import {
  Button,
  DatePicker,
  Descriptions,
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
  message,
} from 'antd';
import type { TableProps } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import {
  COST_TYPE_LABEL,
  DISPOSITION_LABEL,
  INSPECTION_STATUS_LABEL,
  INSPECTION_TYPE_LABEL,
  NCR_STATUS_LABEL,
  QC_RESULT_LABEL,
  QC_SOURCE_LABEL,
  closeNcr,
  createInspection,
  createNcr,
  disposeNcr,
  getInspection,
  judgeInspection,
  pageInspections,
  pageNcr,
  pageQualityCost,
  pageTools,
  qcStatusColor,
  recordInspection,
  type InspectionRecordLine,
  type InspectionView,
  type MeasuringToolView,
  type NcrView,
  type QualityCostView,
} from '../api/qc';
import { listErpItems, type ErpItem } from '../api/erp';
import { hasPermission } from '../utils/permission';
import { money, quantity } from '../utils/scm';

/** 检验单新建表单值 */
interface InspectionFormValues {
  inspectionType: string;
  sourceType?: string;
  sourceNo: string;
  itemCode: string;
  lotNo?: string;
  quantity?: number;
  supplierCode?: string;
  inspector?: string;
  inspectDate: Dayjs;
  toolNo?: string;
  remark?: string;
  items: Array<{ itemName: string; standardValue?: string }>;
}

/** 不合格品登记表单值 */
interface NcrFormValues {
  sourceType?: string;
  sourceNo: string;
  itemName: string;
  lotNo?: string;
  quantity?: number;
  defectDesc: string;
  remark?: string;
}

/** 不合格品处置表单值 */
interface DisposeFormValues {
  disposition: string;
  disposeDate: Dayjs;
  costAmount?: number;
  remark?: string;
}

/** 不合格品关闭表单值 */
interface CloseFormValues {
  closeDate: Dayjs;
  remark?: string;
}

/** 新建检验单弹窗 */
function InspectionCreateModal({
  open,
  onCancel,
  onSaved,
}: {
  open: boolean;
  onCancel: () => void;
  onSaved: () => void;
}) {
  const [form] = Form.useForm<InspectionFormValues>();
  const [items, setItems] = useState<ErpItem[]>([]);
  const [tools, setTools] = useState<MeasuringToolView[]>([]);

  useEffect(() => {
    if (!open) return;
    form.resetFields();
    form.setFieldsValue({
      inspectionType: 'IQC',
      sourceType: 'PURCHASE_RECEIPT',
      inspectDate: dayjs(),
      items: [{ itemName: '外观检验' }, { itemName: '尺寸检验' }],
    });
    void listErpItems().then(setItems);
    void pageTools({ size: 200, status: 'ACTIVE' }).then((page) => setTools(page.records));
  }, [open, form]);

  const handleOk = async () => {
    const values = await form.validateFields().catch(() => null);
    if (!values) return;
    await createInspection({
      inspectionType: values.inspectionType,
      sourceType: values.sourceType,
      sourceNo: values.sourceNo,
      itemCode: values.itemCode,
      lotNo: values.lotNo,
      quantity: Number(values.quantity ?? 0),
      supplierCode: values.supplierCode,
      inspector: values.inspector,
      inspectDate: values.inspectDate.format('YYYY-MM-DD'),
      toolNo: values.toolNo,
      remark: values.remark,
      items: (values.items ?? []).map((item) => ({
        itemName: item.itemName,
        standardValue: item.standardValue,
      })),
    });
    message.success('检验单已创建');
    onSaved();
  };

  return (
    <Modal
      open={open}
      title="新建质量检验单"
      width={820}
      onOk={handleOk}
      onCancel={onCancel}
      destroyOnHidden
    >
      <Form form={form} layout="vertical">
        <Space size="middle" wrap>
          <Form.Item name="inspectionType" label="检验类型" rules={[{ required: true }]}>
            <Select
              style={{ width: 160 }}
              options={Object.entries(INSPECTION_TYPE_LABEL).map(([value, label]) => ({
                value,
                label,
              }))}
            />
          </Form.Item>
          <Form.Item name="sourceType" label="来源类型">
            <Select
              style={{ width: 160 }}
              options={[
                { value: 'PURCHASE_RECEIPT', label: '采购收货' },
                { value: 'PRODUCTION_ORDER', label: '生产订单' },
                { value: 'MANUAL', label: '手工建单' },
              ]}
            />
          </Form.Item>
          <Form.Item
            name="sourceNo"
            label="来源单据号"
            rules={[{ required: true, message: '请输入来源单据号' }]}
          >
            <Input maxLength={64} style={{ width: 200 }} />
          </Form.Item>
          <Form.Item
            name="itemCode"
            label="送检物料"
            rules={[{ required: true, message: '请选择送检物料' }]}
          >
            <Select
              showSearch
              optionFilterProp="label"
              placeholder="选择物料"
              style={{ width: 220 }}
              options={items.map((item) => ({
                value: item.code,
                label: `${item.code} ${item.name}`,
              }))}
            />
          </Form.Item>
          <Form.Item name="lotNo" label="批次号">
            <Input maxLength={64} style={{ width: 160 }} />
          </Form.Item>
          <Form.Item
            name="quantity"
            label="送检数量"
            rules={[{ required: true, message: '请输入送检数量' }]}
          >
            <InputNumber min={0} precision={4} style={{ width: 140 }} />
          </Form.Item>
          <Form.Item name="supplierCode" label="供应商编码">
            <Input maxLength={64} style={{ width: 160 }} />
          </Form.Item>
          <Form.Item name="inspector" label="检验员">
            <Input maxLength={64} style={{ width: 140 }} />
          </Form.Item>
          <Form.Item
            name="inspectDate"
            label="检验日期"
            rules={[{ required: true, message: '请选择检验日期' }]}
          >
            <DatePicker format="YYYY-MM-DD" />
          </Form.Item>
          <Form.Item name="toolNo" label="量具">
            <Select
              allowClear
              placeholder="选择量具"
              style={{ width: 200 }}
              options={tools.map((tool) => ({
                value: tool.toolNo,
                label: `${tool.toolNo} ${tool.name}`,
              }))}
            />
          </Form.Item>
        </Space>
        <Form.Item name="remark" label="备注">
          <Input.TextArea rows={2} maxLength={255} />
        </Form.Item>
        <Form.List name="items">
          {(fields, { add, remove }) => (
            <Space direction="vertical" size="small" style={{ width: '100%' }}>
              {fields.map((field) => (
                <Space key={field.key} align="baseline" wrap>
                  <Form.Item
                    name={[field.name, 'itemName']}
                    label={field.name === 0 ? '检验项' : ''}
                    rules={[{ required: true, message: '请输入检验项名称' }]}
                  >
                    <Input placeholder="检验项名称" maxLength={64} style={{ width: 220 }} />
                  </Form.Item>
                  <Form.Item name={[field.name, 'standardValue']} label={field.name === 0 ? '标准值' : ''}>
                    <Input placeholder="标准值/规格" maxLength={64} style={{ width: 220 }} />
                  </Form.Item>
                  <Button type="link" size="small" onClick={() => remove(field.name)}>
                    删除
                  </Button>
                </Space>
              ))}
              <Button type="dashed" block onClick={() => add({ itemName: '' })}>
                新增检验项
              </Button>
            </Space>
          )}
        </Form.List>
      </Form>
    </Modal>
  );
}

/** 检验项录入弹窗 */
function RecordModal({
  open,
  inspection,
  onCancel,
  onSaved,
}: {
  open: boolean;
  inspection: InspectionView | null;
  onCancel: () => void;
  onSaved: () => void;
}) {
  const [lines, setLines] = useState<InspectionRecordLine[]>([]);

  useEffect(() => {
    if (!open || !inspection) return;
    setLines(
      (inspection.items ?? []).map((item) => ({
        lineNo: item.lineNo,
        actualValue: item.actualValue,
        result: item.result ?? '',
        remark: item.remark,
      })),
    );
  }, [open, inspection]);

  const update = (lineNo: number, patch: Partial<InspectionRecordLine>) => {
    setLines((current) =>
      current.map((line) => (line.lineNo === lineNo ? { ...line, ...patch } : line)),
    );
  };

  const handleOk = async () => {
    if (!inspection) return;
    const filled = lines.filter((line) => line.result);
    if (filled.length !== lines.length) {
      message.warning('请为每个检验项录入结论');
      return;
    }
    await recordInspection(inspection.id, { lines: filled });
    message.success('检验项已录入');
    onSaved();
  };

  const columns: TableProps<InspectionRecordLine>['columns'] = [
    { title: '行号', dataIndex: 'lineNo', key: 'lineNo', width: 70 },
    {
      title: '检验项',
      key: 'itemName',
      render: (_, record) =>
        (inspection?.items ?? []).find((item) => item.lineNo === record.lineNo)?.itemName ?? '-',
    },
    {
      title: '标准值',
      key: 'standardValue',
      render: (_, record) =>
        (inspection?.items ?? []).find((item) => item.lineNo === record.lineNo)?.standardValue ?? '-',
    },
    {
      title: '实测值',
      key: 'actualValue',
      width: 200,
      render: (_, record) => (
        <Input
          value={record.actualValue}
          maxLength={64}
          onChange={(event) => update(record.lineNo, { actualValue: event.target.value })}
        />
      ),
    },
    {
      title: '结论',
      key: 'result',
      width: 150,
      render: (_, record) => (
        <Select
          value={record.result || undefined}
          placeholder="选择结论"
          style={{ width: 120 }}
          options={Object.entries(QC_RESULT_LABEL).map(([value, label]) => ({ value, label }))}
          onChange={(value) => update(record.lineNo, { result: value })}
        />
      ),
    },
    {
      title: '备注',
      key: 'remark',
      width: 180,
      render: (_, record) => (
        <Input
          value={record.remark}
          maxLength={255}
          onChange={(event) => update(record.lineNo, { remark: event.target.value })}
        />
      ),
    },
  ];

  return (
    <Modal
      open={open}
      title={inspection ? `录入检验项 ${inspection.orderNo}` : '录入检验项'}
      width={900}
      onOk={handleOk}
      onCancel={onCancel}
      destroyOnHidden
    >
      <Table<InspectionRecordLine>
        rowKey="lineNo"
        size="small"
        pagination={false}
        columns={columns}
        dataSource={lines}
      />
    </Modal>
  );
}

/** 不合格品登记弹窗 */
function NcrCreateModal({
  open,
  onCancel,
  onSaved,
}: {
  open: boolean;
  onCancel: () => void;
  onSaved: () => void;
}) {
  const [form] = Form.useForm<NcrFormValues>();

  useEffect(() => {
    if (!open) return;
    form.resetFields();
    form.setFieldsValue({ sourceType: 'MANUAL' });
  }, [open, form]);

  const handleOk = async () => {
    const values = await form.validateFields().catch(() => null);
    if (!values) return;
    await createNcr({
      sourceType: values.sourceType,
      sourceNo: values.sourceNo,
      itemName: values.itemName,
      lotNo: values.lotNo,
      quantity: Number(values.quantity ?? 0),
      defectDesc: values.defectDesc,
      remark: values.remark,
    });
    message.success('不合格品单已登记');
    onSaved();
  };

  return (
    <Modal open={open} title="登记不合格品单" width={640} onOk={handleOk} onCancel={onCancel} destroyOnHidden>
      <Form form={form} layout="vertical">
        <Space size="middle" wrap>
          <Form.Item name="sourceType" label="来源类型">
            <Select
              style={{ width: 150 }}
              options={[
                { value: 'MANUAL', label: '手工建单' },
                { value: 'PURCHASE_RECEIPT', label: '采购收货' },
                { value: 'PRODUCTION_ORDER', label: '生产订单' },
              ]}
            />
          </Form.Item>
          <Form.Item
            name="sourceNo"
            label="来源单据号"
            rules={[{ required: true, message: '请输入来源单据号' }]}
          >
            <Input maxLength={64} style={{ width: 200 }} />
          </Form.Item>
          <Form.Item
            name="itemName"
            label="物料名称"
            rules={[{ required: true, message: '请输入物料名称' }]}
          >
            <Input maxLength={128} style={{ width: 220 }} />
          </Form.Item>
          <Form.Item name="lotNo" label="批次号">
            <Input maxLength={64} style={{ width: 160 }} />
          </Form.Item>
          <Form.Item name="quantity" label="不合格数量">
            <InputNumber min={0} precision={4} style={{ width: 140 }} />
          </Form.Item>
        </Space>
        <Form.Item
          name="defectDesc"
          label="缺陷描述"
          rules={[{ required: true, message: '请输入缺陷描述' }]}
        >
          <Input.TextArea rows={2} maxLength={255} />
        </Form.Item>
        <Form.Item name="remark" label="备注">
          <Input.TextArea rows={2} maxLength={255} />
        </Form.Item>
      </Form>
    </Modal>
  );
}

/** 不合格品处置弹窗 */
function DisposeModal({
  open,
  ncr,
  onCancel,
  onSaved,
}: {
  open: boolean;
  ncr: NcrView | null;
  onCancel: () => void;
  onSaved: () => void;
}) {
  const [form] = Form.useForm<DisposeFormValues>();

  useEffect(() => {
    if (!open) return;
    form.resetFields();
    form.setFieldsValue({ disposition: 'REWORK', disposeDate: dayjs(), costAmount: 0 });
  }, [open, form]);

  const handleOk = async () => {
    if (!ncr) return;
    const values = await form.validateFields().catch(() => null);
    if (!values) return;
    await disposeNcr(ncr.id, {
      disposition: values.disposition,
      disposeDate: values.disposeDate.format('YYYY-MM-DD'),
      costAmount: Number(values.costAmount ?? 0),
      remark: values.remark,
    });
    message.success('不合格品已处置');
    onSaved();
  };

  return (
    <Modal
      open={open}
      title={ncr ? `处置不合格品 ${ncr.ncrNo}` : '处置不合格品'}
      onOk={handleOk}
      onCancel={onCancel}
      destroyOnHidden
    >
      <Form form={form} layout="vertical">
        <Form.Item
          name="disposition"
          label="处置方式"
          rules={[{ required: true, message: '请选择处置方式' }]}
        >
          <Select
            options={Object.entries(DISPOSITION_LABEL).map(([value, label]) => ({ value, label }))}
          />
        </Form.Item>
        <Form.Item
          name="disposeDate"
          label="处置日期"
          rules={[{ required: true, message: '请选择处置日期' }]}
        >
          <DatePicker format="YYYY-MM-DD" />
        </Form.Item>
        <Form.Item name="costAmount" label="质量成本金额">
          <InputNumber min={0} precision={2} style={{ width: 180 }} />
        </Form.Item>
        <Form.Item name="remark" label="备注">
          <Input.TextArea rows={2} maxLength={255} />
        </Form.Item>
      </Form>
    </Modal>
  );
}

/** 不合格品关闭弹窗 */
function CloseModal({
  open,
  ncr,
  onCancel,
  onSaved,
}: {
  open: boolean;
  ncr: NcrView | null;
  onCancel: () => void;
  onSaved: () => void;
}) {
  const [form] = Form.useForm<CloseFormValues>();

  useEffect(() => {
    if (!open) return;
    form.resetFields();
    form.setFieldsValue({ closeDate: dayjs() });
  }, [open, form]);

  const handleOk = async () => {
    if (!ncr) return;
    const values = await form.validateFields().catch(() => null);
    if (!values) return;
    await closeNcr(ncr.id, {
      closeDate: values.closeDate.format('YYYY-MM-DD'),
      remark: values.remark,
    });
    message.success('不合格品单已关闭');
    onSaved();
  };

  return (
    <Modal
      open={open}
      title={ncr ? `关闭不合格品单 ${ncr.ncrNo}` : '关闭不合格品单'}
      onOk={handleOk}
      onCancel={onCancel}
      destroyOnHidden
    >
      <Form form={form} layout="vertical">
        <Form.Item
          name="closeDate"
          label="关闭日期"
          rules={[{ required: true, message: '请选择关闭日期' }]}
        >
          <DatePicker format="YYYY-MM-DD" />
        </Form.Item>
        <Form.Item name="remark" label="备注">
          <Input.TextArea rows={2} maxLength={255} />
        </Form.Item>
      </Form>
    </Modal>
  );
}

/** 检验单页签 */
function InspectionTab({ notify }: { notify: () => void }) {
  const [data, setData] = useState<InspectionView[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<string | undefined>();
  const [version, setVersion] = useState(0);
  const [createOpen, setCreateOpen] = useState(false);
  const [detail, setDetail] = useState<InspectionView | null>(null);
  const [recording, setRecording] = useState<InspectionView | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageInspections({ size: 50, keyword, status });
      setData(page.records);
    } finally {
      setLoading(false);
    }
  }, [keyword, status]);

  useEffect(() => {
    void load();
  }, [load, version]);

  const refreshDetail = async (id: number) => {
    setDetail(await getInspection(id));
  };

  const handleJudge = async (record: InspectionView) => {
    const judged = await judgeInspection(record.id);
    message.success(
      judged.status === 'PASSED' ? '检验判定：合格' : '检验判定：不合格，已自动生成不合格品单',
    );
    setVersion((value) => value + 1);
    if (detail?.id === record.id) {
      await refreshDetail(record.id);
    }
    notify();
  };

  const columns: TableProps<InspectionView>['columns'] = [
    { title: '检验单号', dataIndex: 'orderNo', key: 'orderNo', width: 170 },
    {
      title: '检验类型',
      dataIndex: 'inspectionType',
      key: 'inspectionType',
      width: 110,
      render: (value) => <Tag color="blue">{INSPECTION_TYPE_LABEL[String(value)] ?? value}</Tag>,
    },
    { title: '来源单据', dataIndex: 'sourceNo', key: 'sourceNo' },
    { title: '物料', dataIndex: 'itemCode', key: 'itemCode', width: 160 },
    { title: '批次号', dataIndex: 'lotNo', key: 'lotNo', width: 130 },
    {
      title: '送检数量',
      dataIndex: 'quantity',
      key: 'quantity',
      width: 110,
      render: quantity,
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (value) => (
        <Tag color={qcStatusColor(String(value))}>
          {INSPECTION_STATUS_LABEL[String(value)] ?? value}
        </Tag>
      ),
    },
    { title: '检验员', dataIndex: 'inspector', key: 'inspector', width: 100 },
    { title: '检验日期', dataIndex: 'inspectDate', key: 'inspectDate', width: 120 },
    {
      title: '操作',
      key: 'action',
      width: 190,
      render: (_, record) => (
        <Space size="small">
          <Button type="link" size="small" onClick={() => void refreshDetail(record.id)}>
            查看
          </Button>
          {['DRAFT', 'INSPECTING'].includes(record.status)
            && hasPermission('sys:erp:qc:inspection:record') ? (
              <Button type="link" size="small" onClick={() => setRecording(record)}>
                录入
              </Button>
            ) : null}
          {['DRAFT', 'INSPECTING'].includes(record.status)
            && hasPermission('sys:erp:qc:inspection:judge') ? (
              <Popconfirm
                title="判定后不可修改，检验不合格将自动生成不合格品单，确认？"
                onConfirm={() => void handleJudge(record)}
              >
                <Button type="link" size="small">
                  判定
                </Button>
              </Popconfirm>
            ) : null}
        </Space>
      ),
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <Input.Search
          allowClear
          placeholder="检验单号 / 来源单据 / 物料"
          style={{ width: 260 }}
          onSearch={(value) => setKeyword(value)}
        />
        <Button onClick={() => setStatus(undefined)} type={status ? 'default' : 'primary'}>
          全部
        </Button>
        {['DRAFT', 'INSPECTING', 'PASSED', 'FAILED'].map((value) => (
          <Button
            key={value}
            onClick={() => setStatus(value)}
            type={status === value ? 'primary' : 'default'}
          >
            {INSPECTION_STATUS_LABEL[value] ?? value}
          </Button>
        ))}
        {hasPermission('sys:erp:qc:inspection:add') ? (
          <Button type="primary" onClick={() => setCreateOpen(true)}>
            新建检验单
          </Button>
        ) : null}
      </Space>
      <Table<InspectionView>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={data}
        pagination={false}
      />
      <InspectionCreateModal
        open={createOpen}
        onCancel={() => setCreateOpen(false)}
        onSaved={() => {
          setCreateOpen(false);
          setVersion((value) => value + 1);
          notify();
        }}
      />
      <RecordModal
        open={recording !== null}
        inspection={recording}
        onCancel={() => setRecording(null)}
        onSaved={() => {
          const id = recording?.id;
          setRecording(null);
          setVersion((value) => value + 1);
          if (id) {
            void refreshDetail(id);
          }
        }}
      />
      <Modal
        open={detail !== null}
        title={detail ? `检验单 ${detail.orderNo}` : '检验单详情'}
        footer={null}
        width={1000}
        onCancel={() => setDetail(null)}
        destroyOnHidden
      >
        {detail ? (
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <Descriptions size="small" column={3} bordered>
              <Descriptions.Item label="检验类型">
                {INSPECTION_TYPE_LABEL[detail.inspectionType] ?? detail.inspectionType}
              </Descriptions.Item>
              <Descriptions.Item label="来源">
                {QC_SOURCE_LABEL[detail.sourceType ?? ''] ?? detail.sourceType ?? '-'}
                {` / ${detail.sourceNo}`}
              </Descriptions.Item>
              <Descriptions.Item label="状态">
                <Tag color={qcStatusColor(detail.status)}>
                  {INSPECTION_STATUS_LABEL[detail.status] ?? detail.status}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="物料">
                {detail.itemCode}
                {detail.itemName ? ` ${detail.itemName}` : ''}
              </Descriptions.Item>
              <Descriptions.Item label="批次号">{detail.lotNo ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="送检/已检数量">
                {quantity(detail.quantity)} / {quantity(detail.inspectedQuantity)}
              </Descriptions.Item>
              <Descriptions.Item label="量具">{detail.toolNo ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="检验员">{detail.inspector ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="检验日期">{detail.inspectDate}</Descriptions.Item>
              <Descriptions.Item label="关联不合格品单">
                {detail.ncrId ?? '-'}
              </Descriptions.Item>
              <Descriptions.Item label="备注" span={2}>
                {detail.remark ?? '-'}
              </Descriptions.Item>
            </Descriptions>
            <Table
              rowKey="lineNo"
              size="small"
              title={() => '检验项'}
              pagination={false}
              dataSource={detail.items ?? []}
              columns={[
                { title: '行号', dataIndex: 'lineNo', width: 70 },
                { title: '检验项', dataIndex: 'itemName' },
                { title: '标准值', dataIndex: 'standardValue' },
                { title: '实测值', dataIndex: 'actualValue' },
                {
                  title: '结论',
                  dataIndex: 'result',
                  width: 100,
                  render: (value: string | undefined) =>
                    value ? (
                      <Tag color={value === 'PASS' ? 'green' : 'red'}>
                        {QC_RESULT_LABEL[value] ?? value}
                      </Tag>
                    ) : (
                      '待录入'
                    ),
                },
                { title: '备注', dataIndex: 'remark' },
              ]}
            />
          </Space>
        ) : null}
      </Modal>
    </Space>
  );
}

/** 不合格品页签 */
function NcrTab({ ticket }: { ticket: number }) {
  const [data, setData] = useState<NcrView[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<string | undefined>();
  const [version, setVersion] = useState(0);
  const [createOpen, setCreateOpen] = useState(false);
  const [disposing, setDisposing] = useState<NcrView | null>(null);
  const [closing, setClosing] = useState<NcrView | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageNcr({ size: 50, keyword, status });
      setData(page.records);
    } finally {
      setLoading(false);
    }
  }, [keyword, status]);

  useEffect(() => {
    void load();
  }, [load, version, ticket]);

  const columns: TableProps<NcrView>['columns'] = [
    { title: '不合格品单号', dataIndex: 'ncrNo', key: 'ncrNo', width: 170 },
    {
      title: '来源',
      dataIndex: 'sourceType',
      key: 'sourceType',
      width: 120,
      render: (value) => QC_SOURCE_LABEL[String(value)] ?? value,
    },
    { title: '来源单据', dataIndex: 'sourceNo', key: 'sourceNo' },
    { title: '物料', dataIndex: 'itemName', key: 'itemName' },
    { title: '批次号', dataIndex: 'lotNo', key: 'lotNo', width: 130 },
    { title: '数量', dataIndex: 'quantity', key: 'quantity', width: 100, render: quantity },
    { title: '缺陷描述', dataIndex: 'defectDesc', key: 'defectDesc' },
    {
      title: '处置方式',
      dataIndex: 'disposition',
      key: 'disposition',
      width: 110,
      render: (value) => (value ? DISPOSITION_LABEL[String(value)] ?? value : '-'),
    },
    {
      title: '质量成本',
      dataIndex: 'costAmount',
      key: 'costAmount',
      width: 110,
      render: money,
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (value) => (
        <Tag color={qcStatusColor(String(value))}>
          {NCR_STATUS_LABEL[String(value)] ?? value}
        </Tag>
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 150,
      render: (_, record) => (
        <Space size="small">
          {record.status === 'OPEN' && hasPermission('sys:erp:qc:ncr:dispose') ? (
            <Button type="link" size="small" onClick={() => setDisposing(record)}>
              处置
            </Button>
          ) : null}
          {record.status === 'DISPOSED' && hasPermission('sys:erp:qc:ncr:close') ? (
            <Button type="link" size="small" onClick={() => setClosing(record)}>
              关闭
            </Button>
          ) : null}
        </Space>
      ),
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <Input.Search
          allowClear
          placeholder="不合格品单号 / 来源单据 / 物料"
          style={{ width: 280 }}
          onSearch={(value) => setKeyword(value)}
        />
        <Button onClick={() => setStatus(undefined)} type={status ? 'default' : 'primary'}>
          全部
        </Button>
        {['OPEN', 'DISPOSED', 'CLOSED'].map((value) => (
          <Button
            key={value}
            onClick={() => setStatus(value)}
            type={status === value ? 'primary' : 'default'}
          >
            {NCR_STATUS_LABEL[value] ?? value}
          </Button>
        ))}
        {hasPermission('sys:erp:qc:ncr:dispose') ? (
          <Button type="primary" onClick={() => setCreateOpen(true)}>
            登记不合格品
          </Button>
        ) : null}
      </Space>
      <Table<NcrView>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={data}
        pagination={false}
      />
      <NcrCreateModal
        open={createOpen}
        onCancel={() => setCreateOpen(false)}
        onSaved={() => {
          setCreateOpen(false);
          setVersion((value) => value + 1);
        }}
      />
      <DisposeModal
        open={disposing !== null}
        ncr={disposing}
        onCancel={() => setDisposing(null)}
        onSaved={() => {
          setDisposing(null);
          setVersion((value) => value + 1);
        }}
      />
      <CloseModal
        open={closing !== null}
        ncr={closing}
        onCancel={() => setClosing(null)}
        onSaved={() => {
          setClosing(null);
          setVersion((value) => value + 1);
        }}
      />
    </Space>
  );
}

/** 质量成本页签 */
function CostTab({ ticket }: { ticket: number }) {
  const [data, setData] = useState<QualityCostView[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [costType, setCostType] = useState<string | undefined>();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageQualityCost({ size: 50, keyword, costType });
      setData(page.records);
    } finally {
      setLoading(false);
    }
  }, [keyword, costType]);

  useEffect(() => {
    void load();
  }, [load, ticket]);

  const columns: TableProps<QualityCostView>['columns'] = [
    { title: '成本单号', dataIndex: 'costNo', key: 'costNo', width: 170 },
    {
      title: '来源类型',
      dataIndex: 'sourceType',
      key: 'sourceType',
      width: 130,
      render: (value) => (value ? QC_SOURCE_LABEL[String(value)] ?? value : '-'),
    },
    { title: '来源单据', dataIndex: 'sourceNo', key: 'sourceNo' },
    {
      title: '成本类型',
      dataIndex: 'costType',
      key: 'costType',
      width: 120,
      render: (value) => <Tag color="orange">{COST_TYPE_LABEL[String(value)] ?? value}</Tag>,
    },
    {
      title: '金额',
      dataIndex: 'amount',
      key: 'amount',
      width: 130,
      render: money,
    },
    { title: '成本日期', dataIndex: 'costDate', key: 'costDate', width: 120 },
    { title: '备注', dataIndex: 'remark', key: 'remark' },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <Input.Search
          allowClear
          placeholder="成本单号 / 来源单据"
          style={{ width: 260 }}
          onSearch={(value) => setKeyword(value)}
        />
        <Select
          allowClear
          placeholder="成本类型"
          style={{ width: 160 }}
          value={costType}
          onChange={(value) => setCostType(value)}
          options={Object.entries(COST_TYPE_LABEL).map(([value, label]) => ({ value, label }))}
        />
      </Space>
      <Table<QualityCostView>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={data}
        pagination={false}
      />
    </Space>
  );
}

/** 质量检验页 */
export default function QcInspectionPage() {
  const [tab, setTab] = useState('inspection');
  const [ticket, setTicket] = useState(0);

  return (
    <Tabs
      activeKey={tab}
      onChange={setTab}
      items={[
        {
          key: 'inspection',
          label: '检验单',
          children: (
            <InspectionTab
              notify={() => {
                setTicket((value) => value + 1);
              }}
            />
          ),
        },
        { key: 'ncr', label: '不合格品', children: <NcrTab ticket={ticket} /> },
        { key: 'cost', label: '质量成本', children: <CostTab ticket={ticket} /> },
      ]}
    />
  );
}
