// ERP 生产订单页：列表、新建（产出物料 + 领料/完工仓库 + 用料）、下达、领料、完工入库、结案与取消
import { useCallback, useEffect, useState } from 'react';
import {
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
  Tag,
  message,
} from 'antd';
import type { TableProps } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import {
  cancelProductionOrder,
  closeProductionOrder,
  completeProduction,
  createProductionOrder,
  getProductionOrder,
  issueProduction,
  pageProductionOrders,
  releaseProductionOrder,
  type ProductionMaterialView,
  type ProductionOrderView,
} from '../api/mfg';
import { listErpItems, type ErpItem } from '../api/erp';
import { pageWarehouses, type ErpWarehouse } from '../api/masterdata';
import { hasPermission } from '../utils/permission';
import { SCM_STATUS_LABEL, money, quantity, scmStatusColor } from '../utils/scm';

/** 用料表单值 */
interface MaterialFormValues {
  itemCode: string;
  requiredQuantity?: number;
  uom?: string;
}

/** 生产订单表单值 */
interface ProductionFormValues {
  itemCode: string;
  plannedQuantity?: number;
  issueWarehouseId: number;
  targetWarehouseId: number;
  planStartDate: Dayjs;
  planEndDate?: Dayjs;
  remark?: string;
  materials: MaterialFormValues[];
}

/** 领料行编辑状态 */
interface IssueLineState {
  lineNo: number;
  itemCode: string;
  itemName?: string;
  requiredQuantity: number;
  issuedQuantity: number;
  quantity: number;
}

/** 完工表单值 */
interface CompleteFormValues {
  quantity?: number;
  completeDate: Dayjs;
}

/** 剩余未领数量 */
function remainingOf(material: ProductionMaterialView): number {
  return Math.max(
    Number(material.requiredQuantity ?? 0) - Number(material.issuedQuantity ?? 0),
    0,
  );
}

/** 生产订单页 */
export default function ProductionPage() {
  const [data, setData] = useState<ProductionOrderView[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<string | undefined>();
  const [version, setVersion] = useState(0);
  const [createOpen, setCreateOpen] = useState(false);
  const [detail, setDetail] = useState<ProductionOrderView | null>(null);
  const [issueTarget, setIssueTarget] = useState<ProductionOrderView | null>(null);
  const [issueLines, setIssueLines] = useState<IssueLineState[]>([]);
  const [completeTarget, setCompleteTarget] = useState<ProductionOrderView | null>(null);
  const [warehouses, setWarehouses] = useState<ErpWarehouse[]>([]);
  const [items, setItems] = useState<ErpItem[]>([]);
  const [form] = Form.useForm<ProductionFormValues>();
  const [completeForm] = Form.useForm<CompleteFormValues>();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageProductionOrders({ size: 50, keyword, status });
      setData(page.records);
    } finally {
      setLoading(false);
    }
  }, [keyword, status]);

  useEffect(() => {
    void load();
  }, [load, version]);

  const openCreate = async () => {
    const [warehousePage, itemList] = await Promise.all([
      pageWarehouses({ size: 100 }),
      listErpItems(),
    ]);
    setWarehouses(warehousePage.records);
    setItems(itemList);
    form.resetFields();
    form.setFieldsValue({
      planStartDate: dayjs(),
      materials: [{ itemCode: '', requiredQuantity: 1 }],
    });
    setCreateOpen(true);
  };

  const handleCreate = async () => {
    const values = await form.validateFields().catch(() => null);
    if (!values) return;
    await createProductionOrder({
      itemCode: values.itemCode,
      plannedQuantity: Number(values.plannedQuantity ?? 0),
      issueWarehouseId: values.issueWarehouseId,
      targetWarehouseId: values.targetWarehouseId,
      planStartDate: values.planStartDate.format('YYYY-MM-DD'),
      planEndDate: values.planEndDate?.format('YYYY-MM-DD'),
      remark: values.remark,
      materials: values.materials.map((material) => ({
        itemCode: material.itemCode,
        requiredQuantity: Number(material.requiredQuantity ?? 0),
        uom: material.uom,
      })),
    });
    message.success('生产订单已创建');
    setCreateOpen(false);
    setVersion((value) => value + 1);
  };

  const handleRelease = async (id: number) => {
    await releaseProductionOrder(id);
    message.success('生产订单已下达，可开始领料');
    setVersion((value) => value + 1);
  };

  const handleDetail = async (id: number) => {
    setDetail(await getProductionOrder(id));
  };

  const openIssue = async (id: number) => {
    const order = await getProductionOrder(id);
    setIssueTarget(order);
    setIssueLines(
      (order.materials ?? []).map((material) => ({
        lineNo: material.lineNo,
        itemCode: material.itemCode,
        itemName: material.itemName,
        requiredQuantity: Number(material.requiredQuantity ?? 0),
        issuedQuantity: Number(material.issuedQuantity ?? 0),
        quantity: remainingOf(material),
      })),
    );
  };

  const handleIssue = async () => {
    if (!issueTarget) return;
    const lines = issueLines
      .filter((line) => Number(line.quantity) > 0)
      .map((line) => ({ lineNo: line.lineNo, quantity: Number(line.quantity) }));
    if (lines.length === 0) {
      message.warning('请填写本次领料数量');
      return;
    }
    await issueProduction(issueTarget.id, { lines });
    message.success('领料已出库并计入在建成本');
    setIssueTarget(null);
    setVersion((value) => value + 1);
  };

  const openComplete = (order: ProductionOrderView) => {
    setCompleteTarget(order);
    completeForm.resetFields();
    completeForm.setFieldsValue({
      quantity: Math.max(Number(order.plannedQuantity ?? 0) - Number(order.completedQuantity ?? 0), 0),
      completeDate: dayjs(),
    });
  };

  const handleComplete = async () => {
    if (!completeTarget) return;
    const values = await completeForm.validateFields().catch(() => null);
    if (!values) return;
    await completeProduction(completeTarget.id, {
      quantity: Number(values.quantity ?? 0),
      completeDate: values.completeDate.format('YYYY-MM-DD'),
    });
    message.success('完工入库已过账，材料成本转入存货');
    setCompleteTarget(null);
    setVersion((value) => value + 1);
  };

  const handleClose = async (id: number) => {
    await closeProductionOrder(id);
    message.success('生产订单已结案');
    setVersion((value) => value + 1);
  };

  const handleCancel = async (id: number) => {
    await cancelProductionOrder(id);
    message.success('生产订单已取消');
    setVersion((value) => value + 1);
  };

  const columns: TableProps<ProductionOrderView>['columns'] = [
    { title: '生产订单号', dataIndex: 'orderNo', key: 'orderNo', width: 180 },
    {
      title: '产出物料',
      key: 'item',
      render: (_, record) => `${record.itemCode}${record.itemName ? ` ${record.itemName}` : ''}`,
    },
    { title: '计划数量', dataIndex: 'plannedQuantity', key: 'plannedQuantity', width: 110, render: quantity },
    {
      title: '已完工',
      dataIndex: 'completedQuantity',
      key: 'completedQuantity',
      width: 110,
      render: quantity,
    },
    {
      title: '材料成本',
      dataIndex: 'totalMaterialCost',
      key: 'totalMaterialCost',
      width: 120,
      render: money,
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (value) => (
        <Tag color={scmStatusColor(String(value))}>{SCM_STATUS_LABEL[String(value)] ?? value}</Tag>
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 320,
      render: (_, record) => (
        <Space size="small" wrap>
          <Button type="link" size="small" onClick={() => handleDetail(record.id)}>
            查看
          </Button>
          {record.status === 'DRAFT' && hasPermission('sys:erp:production:release') ? (
            <Popconfirm title="下达后可开始领料，确认继续？" onConfirm={() => handleRelease(record.id)}>
              <Button type="link" size="small">
                下达
              </Button>
            </Popconfirm>
          ) : null}
          {(record.status === 'RELEASED' || record.status === 'IN_PROGRESS')
          && hasPermission('sys:erp:production:issue') ? (
            <Button type="link" size="small" onClick={() => openIssue(record.id)}>
              领料
            </Button>
          ) : null}
          {record.status === 'IN_PROGRESS' && hasPermission('sys:erp:production:complete') ? (
            <Button type="link" size="small" onClick={() => openComplete(record)}>
              完工
            </Button>
          ) : null}
          {record.status === 'COMPLETED' && hasPermission('sys:erp:production:close') ? (
            <Popconfirm title="结案后不可再操作，确认继续？" onConfirm={() => handleClose(record.id)}>
              <Button type="link" size="small">
                结案
              </Button>
            </Popconfirm>
          ) : null}
          {(record.status === 'DRAFT' || record.status === 'RELEASED')
          && hasPermission('sys:erp:production:cancel') ? (
            <Popconfirm title="确认取消该生产订单？" onConfirm={() => handleCancel(record.id)}>
              <Button type="link" size="small" danger>
                取消
              </Button>
            </Popconfirm>
          ) : null}
        </Space>
      ),
    },
  ];

  const materialColumns: TableProps<ProductionMaterialView>['columns'] = [
    { title: '行', dataIndex: 'lineNo', key: 'lineNo', width: 60 },
    { title: '物料编码', dataIndex: 'itemCode', key: 'itemCode', width: 140 },
    { title: '物料名称', dataIndex: 'itemName', key: 'itemName' },
    { title: '单位', dataIndex: 'uom', key: 'uom', width: 90 },
    {
      title: '需求数量',
      dataIndex: 'requiredQuantity',
      key: 'requiredQuantity',
      width: 120,
      render: quantity,
    },
    {
      title: '已领数量',
      dataIndex: 'issuedQuantity',
      key: 'issuedQuantity',
      width: 120,
      render: quantity,
    },
  ];

  const issueColumns: TableProps<IssueLineState>['columns'] = [
    { title: '行', dataIndex: 'lineNo', key: 'lineNo', width: 60 },
    { title: '物料编码', dataIndex: 'itemCode', key: 'itemCode', width: 140 },
    { title: '物料名称', dataIndex: 'itemName', key: 'itemName' },
    {
      title: '需求数量',
      dataIndex: 'requiredQuantity',
      key: 'requiredQuantity',
      width: 110,
      render: quantity,
    },
    {
      title: '已领数量',
      dataIndex: 'issuedQuantity',
      key: 'issuedQuantity',
      width: 110,
      render: quantity,
    },
    {
      title: '本次领料',
      key: 'quantity',
      width: 140,
      render: (_, record, index) => (
        <InputNumber
          min={0}
          precision={4}
          style={{ width: 120 }}
          value={record.quantity}
          onChange={(value) => {
            const next = Number(value ?? 0);
            setIssueLines((current) =>
              current.map((line, lineIndex) =>
                lineIndex === index ? { ...line, quantity: next } : line,
              ),
            );
          }}
        />
      ),
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <Input.Search
          allowClear
          placeholder="订单号 / 物料"
          style={{ width: 240 }}
          onSearch={(value) => setKeyword(value)}
        />
        <Button onClick={() => setStatus(undefined)} type={status ? 'default' : 'primary'}>
          全部
        </Button>
        <Button onClick={() => setStatus('DRAFT')} type={status === 'DRAFT' ? 'primary' : 'default'}>
          草稿
        </Button>
        <Button onClick={() => setStatus('RELEASED')} type={status === 'RELEASED' ? 'primary' : 'default'}>
          已下达
        </Button>
        <Button onClick={() => setStatus('IN_PROGRESS')} type={status === 'IN_PROGRESS' ? 'primary' : 'default'}>
          生产中
        </Button>
        <Button onClick={() => setStatus('COMPLETED')} type={status === 'COMPLETED' ? 'primary' : 'default'}>
          已完工
        </Button>
        {hasPermission('sys:erp:production:add') ? (
          <Button type="primary" onClick={openCreate}>
            新增生产订单
          </Button>
        ) : null}
      </Space>
      <Table<ProductionOrderView> rowKey="id" loading={loading} columns={columns} dataSource={data} />
      <Modal
        open={createOpen}
        title="新增生产订单"
        width={880}
        onOk={handleCreate}
        onCancel={() => setCreateOpen(false)}
        destroyOnHidden
      >
        <Form form={form} layout="vertical">
          <Space size="large" wrap>
            <Form.Item name="itemCode" label="产出物料" rules={[{ required: true, message: '请选择产出物料' }]}>
              <Select
                showSearch
                optionFilterProp="label"
                style={{ width: 240 }}
                options={items.map((item) => ({
                  value: item.code,
                  label: `${item.code} ${item.name}`,
                }))}
              />
            </Form.Item>
            <Form.Item
              name="plannedQuantity"
              label="计划生产数量"
              rules={[{ required: true, message: '请输入计划生产数量' }]}
            >
              <InputNumber min={0.0001} precision={4} style={{ width: 150 }} />
            </Form.Item>
          </Space>
          <Space size="large" wrap>
            <Form.Item
              name="issueWarehouseId"
              label="领料仓库"
              rules={[{ required: true, message: '请选择领料仓库' }]}
            >
              <Select
                showSearch
                optionFilterProp="label"
                style={{ width: 220 }}
                options={warehouses.map((item) => ({
                  value: item.id,
                  label: `${item.code} ${item.name}`,
                }))}
              />
            </Form.Item>
            <Form.Item
              name="targetWarehouseId"
              label="完工入库仓库"
              rules={[{ required: true, message: '请选择完工入库仓库' }]}
            >
              <Select
                showSearch
                optionFilterProp="label"
                style={{ width: 220 }}
                options={warehouses.map((item) => ({
                  value: item.id,
                  label: `${item.code} ${item.name}`,
                }))}
              />
            </Form.Item>
          </Space>
          <Space size="large" wrap>
            <Form.Item name="planStartDate" label="计划开工日期" rules={[{ required: true }]}>
              <DatePicker />
            </Form.Item>
            <Form.Item name="planEndDate" label="计划完工日期">
              <DatePicker />
            </Form.Item>
          </Space>
          <Form.List name="materials">
            {(fields, { add, remove }) => (
              <Space direction="vertical" size="small" style={{ width: '100%' }}>
                {fields.map((field) => (
                  <Space key={field.key} align="baseline" wrap>
                    <Form.Item
                      name={[field.name, 'itemCode']}
                      rules={[{ required: true, message: '请选择用料' }]}
                    >
                      <Select
                        showSearch
                        optionFilterProp="label"
                        placeholder="用料物料"
                        style={{ width: 280 }}
                        options={items.map((item) => ({
                          value: item.code,
                          label: `${item.code} ${item.name}`,
                        }))}
                      />
                    </Form.Item>
                    <Form.Item
                      name={[field.name, 'requiredQuantity']}
                      rules={[{ required: true, message: '需求数量' }]}
                    >
                      <InputNumber placeholder="需求数量" min={0.0001} precision={4} style={{ width: 120 }} />
                    </Form.Item>
                    <Form.Item name={[field.name, 'uom']}>
                      <Input placeholder="单位" style={{ width: 90 }} maxLength={16} />
                    </Form.Item>
                    <Button type="link" danger onClick={() => remove(field.name)}>
                      删除
                    </Button>
                  </Space>
                ))}
                <Button type="dashed" onClick={() => add({ requiredQuantity: 1 })} block>
                  增加用料
                </Button>
              </Space>
            )}
          </Form.List>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} maxLength={255} />
          </Form.Item>
        </Form>
      </Modal>
      <Modal
        open={detail !== null}
        title={detail ? `生产订单 ${detail.orderNo}` : '生产订单详情'}
        footer={null}
        width={880}
        onCancel={() => setDetail(null)}
        destroyOnHidden
      >
        {detail ? (
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <Space size="large" wrap>
              <span>产出：{detail.itemCode} {detail.itemName}</span>
              <span>状态：{SCM_STATUS_LABEL[detail.status] ?? detail.status}</span>
              <span>计划：{quantity(detail.plannedQuantity)}</span>
              <span>已完工：{quantity(detail.completedQuantity)}</span>
              <span>材料成本：{money(detail.totalMaterialCost)}</span>
              <span>入库成本：{money(detail.completedCost)}</span>
            </Space>
            <Space size="large" wrap>
              <span>领料仓：{detail.issueWarehouseName}</span>
              <span>入库仓：{detail.targetWarehouseName}</span>
              <span>计划开工：{detail.planStartDate}</span>
              <span>计划完工：{detail.planEndDate ?? '-'}</span>
              <span>实际完工：{detail.completeDate ?? '-'}</span>
            </Space>
            <Table<ProductionMaterialView>
              rowKey="lineNo"
              size="small"
              columns={materialColumns}
              dataSource={detail.materials ?? []}
              pagination={false}
            />
          </Space>
        ) : null}
      </Modal>
      <Modal
        open={issueTarget !== null}
        title={issueTarget ? `生产领料 ${issueTarget.orderNo}` : '生产领料'}
        width={880}
        onOk={handleIssue}
        onCancel={() => setIssueTarget(null)}
        destroyOnHidden
      >
        {issueTarget ? (
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <span>领料仓：{issueTarget.issueWarehouseName}</span>
            <Table<IssueLineState>
              rowKey="lineNo"
              size="small"
              columns={issueColumns}
              dataSource={issueLines}
              pagination={false}
            />
          </Space>
        ) : null}
      </Modal>
      <Modal
        open={completeTarget !== null}
        title={completeTarget ? `完工入库 ${completeTarget.orderNo}` : '完工入库'}
        onOk={handleComplete}
        onCancel={() => setCompleteTarget(null)}
        destroyOnHidden
      >
        {completeTarget ? (
          <Form form={completeForm} layout="vertical">
            <Space size="large" wrap>
              <Form.Item
                name="quantity"
                label="本次完工数量"
                rules={[{ required: true, message: '请输入完工数量' }]}
              >
                <InputNumber min={0.0001} precision={4} style={{ width: 150 }} />
              </Form.Item>
              <Form.Item name="completeDate" label="完工日期" rules={[{ required: true }]}>
                <DatePicker />
              </Form.Item>
            </Space>
            <span>
              入库仓：{completeTarget.targetWarehouseName}；单位成本按累计领料成本 / 计划数量结转
            </span>
          </Form>
        ) : null}
      </Modal>
    </Space>
  );
}
