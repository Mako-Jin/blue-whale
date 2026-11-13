// 商务订单：筛选分页、报价 / 合同转单与手工建单、提交审批、下发重试、取消与对账
import { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Card,
  Descriptions,
  Drawer,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import type { FormListFieldData, TableProps } from 'antd';
import {
  cancelOrder,
  createOrder,
  getOrder,
  pageOrders,
  reconcileOrders,
  retryOrder,
  submitOrder,
  updateOrder,
  type OrderSourceType,
  type SalesOrderCreateInput,
  type SalesOrderLineInput,
  type SalesOrderPageQuery,
  type SalesOrderUpdateInput,
  type SalesOrderView,
} from '../../api/commerce';
import { hasPermission } from '../../utils/permission';
import {
  ORDER_SOURCE_LABEL,
  ORDER_STATUS_COLOR,
  ORDER_STATUS_LABEL,
  ORDER_STATUS_OPTIONS,
  formatDateTime,
} from '../../utils/dict';

/** 筛选条件 */
interface OrderFilterValues {
  keyword?: string;
  customerId?: number | null;
  status?: string;
}

/** 订单行表单值 */
interface OrderLineFormValues {
  itemCode?: string;
  itemName?: string;
  specification?: string;
  uom?: string;
  quantity: number;
  unitPrice: number;
  remark?: string;
}

/** 订单表单值 */
interface OrderFormValues {
  sourceType: OrderSourceType;
  sourceId?: number | null;
  customerId?: number | null;
  title?: string;
  currency?: string;
  remark?: string;
  lines: OrderLineFormValues[];
}

/**
 * 读取错误文案。
 *
 * @param error 异常对象
 * @returns 可展示的错误信息
 */
function errorText(error: unknown): string {
  return error instanceof Error ? error.message : '操作失败，请稍后重试';
}

/**
 * 格式化金额。
 *
 * @param value 后端金额
 * @returns 千分位金额文本
 */
function formatAmount(value?: number | string | null): string {
  if (value === null || value === undefined || value === '') {
    return '-';
  }
  const num = Number(value);
  return Number.isNaN(num)
    ? String(value)
    : num.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

interface OrderFormModalProps {
  open: boolean;
  initial: SalesOrderView | null;
  onCancel: () => void;
  onSaved: () => void;
}

/** 订单新建 / 编辑弹窗（含手工明细行） */
function OrderFormModal({ open, initial, onCancel, onSaved }: OrderFormModalProps) {
  const [form] = Form.useForm<OrderFormValues>();
  const [saving, setSaving] = useState(false);
  const [sourceType, setSourceType] = useState<OrderSourceType>('MANUAL');

  useEffect(() => {
    if (!open) {
      return;
    }
    if (initial) {
      setSourceType('MANUAL');
      form.setFieldsValue({
        sourceType: 'MANUAL',
        customerId: initial.customerId,
        title: initial.title ?? undefined,
        currency: initial.currency ?? 'CNY',
        remark: initial.remark ?? undefined,
        lines: (initial.lines ?? []).map((line) => ({
          itemCode: line.itemCode ?? undefined,
          itemName: line.itemName ?? undefined,
          specification: line.specification ?? undefined,
          uom: line.uom ?? undefined,
          quantity:
            line.quantity === null || line.quantity === undefined ? 1 : Number(line.quantity),
          unitPrice:
            line.unitPrice === null || line.unitPrice === undefined ? 0 : Number(line.unitPrice),
          remark: line.remark ?? undefined,
        })),
      });
    } else {
      setSourceType('MANUAL');
      form.resetFields();
      form.setFieldsValue({
        sourceType: 'MANUAL',
        currency: 'CNY',
        lines: [{ quantity: 1, unitPrice: 0 }],
      });
    }
  }, [open, initial, form]);

  const handleOk = async () => {
    setSaving(true);
    try {
      const values = await form.validateFields();
      const lines: SalesOrderLineInput[] = (values.lines ?? []).map((line) => ({
        itemCode: line.itemCode || undefined,
        itemName: line.itemName || undefined,
        specification: line.specification || undefined,
        uom: line.uom || undefined,
        quantity: line.quantity,
        unitPrice: line.unitPrice,
        remark: line.remark,
      }));
      if (initial) {
        const payload: SalesOrderUpdateInput = {
          title: values.title,
          currency: values.currency,
          remark: values.remark,
          lines,
        };
        await updateOrder(initial.id, payload);
        message.success('商务订单已更新');
      } else if (values.sourceType === 'QUOTE') {
        const payload: SalesOrderCreateInput = {
          sourceType: 'QUOTE',
          sourceId: values.sourceId ?? null,
          title: values.title,
          currency: values.currency,
          remark: values.remark,
        };
        const created = await createOrder(payload);
        message.success(`商务订单已创建：${created.orderNo}，请补录物料编码后再提交`);
      } else {
        const payload: SalesOrderCreateInput = {
          sourceType: values.sourceType,
          sourceId: values.sourceType === 'CONTRACT' ? (values.sourceId ?? null) : null,
          customerId: values.sourceType === 'MANUAL' ? (values.customerId ?? null) : null,
          title: values.title,
          currency: values.currency,
          remark: values.remark,
          lines,
        };
        const created = await createOrder(payload);
        message.success(`商务订单已创建：${created.orderNo}`);
      }
      onSaved();
    } catch (error) {
      if (error instanceof Error) {
        message.error(errorText(error));
      }
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open={open}
      title={initial ? `编辑商务订单：${initial.orderNo}` : '新建商务订单'}
      width={1080}
      confirmLoading={saving}
      okText="保存"
      cancelText="取消"
      onOk={handleOk}
      onCancel={onCancel}
      destroyOnClose
    >
      <Form form={form} layout="vertical" preserve={false}>
        <Space size={12} style={{ display: 'flex' }}>
          <Form.Item name="sourceType" label="来源类型" style={{ flex: 1 }}>
            <Select
              disabled={Boolean(initial)}
              options={[
                { value: 'MANUAL', label: '手工创建' },
                { value: 'QUOTE', label: '报价转订单' },
                { value: 'CONTRACT', label: '合同转订单' },
              ]}
              onChange={(value: OrderSourceType) => setSourceType(value)}
            />
          </Form.Item>
          {!initial && sourceType !== 'MANUAL' && (
            <Form.Item
              name="sourceId"
              label={sourceType === 'QUOTE' ? '报价 ID' : '合同台账 ID'}
              rules={[{ required: true, message: '请输入来源单据 ID' }]}
              style={{ flex: 1 }}
            >
              <InputNumber min={1} precision={0} style={{ width: '100%' }} />
            </Form.Item>
          )}
          {!initial && sourceType === 'MANUAL' && (
            <Form.Item
              name="customerId"
              label="客户 ID"
              rules={[{ required: true, message: '请输入客户 ID' }]}
              style={{ flex: 1 }}
            >
              <InputNumber min={1} precision={0} style={{ width: '100%' }} />
            </Form.Item>
          )}
          <Form.Item name="currency" label="币种" style={{ flex: 1 }}>
            <Select
              options={[
                { value: 'CNY', label: 'CNY 人民币' },
                { value: 'USD', label: 'USD 美元' },
                { value: 'EUR', label: 'EUR 欧元' },
              ]}
            />
          </Form.Item>
        </Space>
        <Form.Item name="title" label="订单标题">
          <Input maxLength={100} />
        </Form.Item>
        <Form.Item name="remark" label="备注">
          <Input.TextArea rows={2} maxLength={500} showCount />
        </Form.Item>
        {sourceType === 'QUOTE' && !initial ? (
          <Typography.Text type="secondary">
            报价转订单将复制已审批报价的明细，物料编码留空；补录后再提交审批。
          </Typography.Text>
        ) : (
          <>
            <Typography.Text strong>订单明细</Typography.Text>
            <Form.List name="lines">
              {(fields, { add, remove }) => (
                <>
                  <Table<FormListFieldData>
                    style={{ marginTop: 8 }}
                    rowKey="key"
                    size="small"
                    dataSource={fields}
                    pagination={false}
                    scroll={{ x: 960 }}
                    columns={[
                      {
                        title: '物料编码',
                        width: 140,
                        render: (_, field) => (
                          <Form.Item name={[field.name, 'itemCode']} style={{ marginBottom: 0 }}>
                            <Input maxLength={50} />
                          </Form.Item>
                        ),
                      },
                      {
                        title: '物料名称',
                        width: 160,
                        render: (_, field) => (
                          <Form.Item
                            name={[field.name, 'itemName']}
                            rules={[{ required: true, message: '请输入物料名称' }]}
                            style={{ marginBottom: 0 }}
                          >
                            <Input maxLength={100} />
                          </Form.Item>
                        ),
                      },
                      {
                        title: '规格',
                        width: 110,
                        render: (_, field) => (
                          <Form.Item
                            name={[field.name, 'specification']}
                            style={{ marginBottom: 0 }}
                          >
                            <Input maxLength={50} />
                          </Form.Item>
                        ),
                      },
                      {
                        title: '单位',
                        width: 80,
                        render: (_, field) => (
                          <Form.Item name={[field.name, 'uom']} style={{ marginBottom: 0 }}>
                            <Input maxLength={20} />
                          </Form.Item>
                        ),
                      },
                      {
                        title: '数量',
                        width: 100,
                        render: (_, field) => (
                          <Form.Item
                            name={[field.name, 'quantity']}
                            rules={[{ required: true, message: '请输入数量' }]}
                            style={{ marginBottom: 0 }}
                          >
                            <InputNumber min={0} precision={2} style={{ width: '100%' }} />
                          </Form.Item>
                        ),
                      },
                      {
                        title: '单价',
                        width: 110,
                        render: (_, field) => (
                          <Form.Item
                            name={[field.name, 'unitPrice']}
                            rules={[{ required: true, message: '请输入单价' }]}
                            style={{ marginBottom: 0 }}
                          >
                            <InputNumber min={0} precision={2} style={{ width: '100%' }} />
                          </Form.Item>
                        ),
                      },
                      {
                        title: '备注',
                        width: 140,
                        render: (_, field) => (
                          <Form.Item name={[field.name, 'remark']} style={{ marginBottom: 0 }}>
                            <Input maxLength={100} />
                          </Form.Item>
                        ),
                      },
                      {
                        title: '操作',
                        width: 60,
                        fixed: 'right',
                        render: (_, field) => (
                          <Button
                            size="small"
                            type="link"
                            danger
                            onClick={() => remove(field.name)}
                          >
                            删除
                          </Button>
                        ),
                      },
                    ]}
                  />
                  <Button
                    type="dashed"
                    block
                    style={{ marginTop: 8 }}
                    onClick={() => add({ quantity: 1, unitPrice: 0 })}
                  >
                    新增明细
                  </Button>
                </>
              )}
            </Form.List>
          </>
        )}
      </Form>
    </Modal>
  );
}

interface OrderDetailDrawerProps {
  orderId: number | null;
  onClose: () => void;
}

/** 订单详情抽屉 */
function OrderDetailDrawer({ orderId, onClose }: OrderDetailDrawerProps) {
  const [detail, setDetail] = useState<SalesOrderView | null>(null);

  useEffect(() => {
    if (!orderId) {
      setDetail(null);
      return;
    }
    let active = true;
    getOrder(orderId)
      .then((data) => {
        if (active) {
          setDetail(data);
        }
      })
      .catch((error: unknown) => {
        message.error(errorText(error));
      });
    return () => {
      active = false;
    };
  }, [orderId]);

  return (
    <Drawer
      open={Boolean(orderId)}
      title={detail ? `商务订单详情：${detail.orderNo}` : '商务订单详情'}
      width={780}
      onClose={onClose}
    >
      {detail && (
        <>
          <Descriptions bordered size="small" column={2}>
            <Descriptions.Item label="订单编号">{detail.orderNo}</Descriptions.Item>
            <Descriptions.Item label="状态">
              <Tag color={ORDER_STATUS_COLOR[detail.status as string]}>
                {ORDER_STATUS_LABEL[detail.status as string] ?? detail.status}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="来源类型">
              {ORDER_SOURCE_LABEL[detail.sourceType ?? ''] ?? detail.sourceType ?? '-'}
            </Descriptions.Item>
            <Descriptions.Item label="来源单据">{detail.sourceOrderNo || '-'}</Descriptions.Item>
            <Descriptions.Item label="客户 ID">{detail.customerId}</Descriptions.Item>
            <Descriptions.Item label="客户编码">{detail.customerCode || '-'}</Descriptions.Item>
            <Descriptions.Item label="订单金额">
              {formatAmount(detail.totalAmount)}
            </Descriptions.Item>
            <Descriptions.Item label="币种">{detail.currency || '-'}</Descriptions.Item>
            <Descriptions.Item label="ERP 订单号">{detail.erpOrderNo || '-'}</Descriptions.Item>
            <Descriptions.Item label="ERP 履约状态">{detail.erpStatus || '-'}</Descriptions.Item>
            <Descriptions.Item label="下发尝试次数">{detail.pushAttempts ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="下发成功时间">
              {formatDateTime(detail.pushedAt)}
            </Descriptions.Item>
            <Descriptions.Item label="提交审批时间">
              {formatDateTime(detail.submittedAt)}
            </Descriptions.Item>
            <Descriptions.Item label="审批通过时间">
              {formatDateTime(detail.approvedAt)}
            </Descriptions.Item>
            <Descriptions.Item label="最近失败原因" span={2}>
              {detail.lastPushError || '-'}
            </Descriptions.Item>
            <Descriptions.Item label="备注" span={2}>
              {detail.remark || '-'}
            </Descriptions.Item>
          </Descriptions>
          <Typography.Text strong style={{ display: 'block', margin: '16px 0 8px' }}>
            订单明细
          </Typography.Text>
          <Table
            rowKey={(line) => String(line.id ?? line.lineNo ?? 0)}
            size="small"
            pagination={false}
            dataSource={detail.lines ?? []}
            columns={[
              { title: '行号', dataIndex: 'lineNo', width: 60 },
              {
                title: '物料编码',
                dataIndex: 'itemCode',
                width: 130,
                render: (value) => value || '-',
              },
              {
                title: '物料名称',
                dataIndex: 'itemName',
                width: 160,
                render: (value) => value || '-',
              },
              {
                title: '数量',
                dataIndex: 'quantity',
                width: 90,
                align: 'right',
                render: (value) => value ?? '-',
              },
              {
                title: '单价',
                dataIndex: 'unitPrice',
                width: 110,
                align: 'right',
                render: (value) => formatAmount(value),
              },
              {
                title: '行金额',
                dataIndex: 'amount',
                width: 120,
                align: 'right',
                render: (value) => formatAmount(value),
              },
            ]}
          />
          <Typography.Text type="secondary" style={{ display: 'block', marginTop: 12 }}>
            履约 / 发货 / 库存 / 应收由 ERP 负责，CRM 仅保留下发状态与 ERP 单号回写。
          </Typography.Text>
        </>
      )}
    </Drawer>
  );
}

/** 商务订单页 */
export default function OrderPage() {
  const [filterForm] = Form.useForm<OrderFilterValues>();
  const [rows, setRows] = useState<SalesOrderView[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [filters, setFilters] = useState<SalesOrderPageQuery>({});
  const [page, setPage] = useState({ current: 1, size: 10 });
  const [editing, setEditing] = useState<SalesOrderView | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const [detailId, setDetailId] = useState<number | null>(null);
  const [reconciling, setReconciling] = useState(false);

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const result = await pageOrders({ ...filters, current: page.current, size: page.size });
      setRows(result.records);
      setTotal(Number(result.total));
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setLoading(false);
    }
  }, [filters, page]);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  const handleSearch = (values: OrderFilterValues) => {
    setFilters({
      keyword: values.keyword || undefined,
      customerId: values.customerId ?? null,
      status: values.status || undefined,
    });
    setPage((prev) => ({ ...prev, current: 1 }));
  };

  const handleReset = () => {
    filterForm.resetFields();
    setFilters({});
    setPage((prev) => ({ ...prev, current: 1 }));
  };

  const openCreate = () => {
    setEditing(null);
    setFormOpen(true);
  };

  const openEdit = (row: SalesOrderView) => {
    setEditing(row);
    setFormOpen(true);
  };

  const runAction = async (action: () => Promise<SalesOrderView>, successText: string) => {
    try {
      await action();
      message.success(successText);
      void loadData();
    } catch (error) {
      message.error(errorText(error));
    }
  };

  const handleReconcile = async () => {
    setReconciling(true);
    try {
      const count = await reconcileOrders();
      message.success(`对账完成，刷新 ${count} 条订单`);
      void loadData();
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setReconciling(false);
    }
  };

  const columns: TableProps<SalesOrderView>['columns'] = [
    { title: '订单编号', dataIndex: 'orderNo', width: 190, fixed: 'left' },
    {
      title: '来源',
      dataIndex: 'sourceType',
      width: 110,
      render: (value?: string | null) => (
        <Tag>{ORDER_SOURCE_LABEL[value ?? ''] ?? value ?? '-'}</Tag>
      ),
    },
    {
      title: '客户编码',
      dataIndex: 'customerCode',
      width: 130,
      render: (value?: string | null) => value || '-',
    },
    {
      title: '标题',
      dataIndex: 'title',
      width: 200,
      ellipsis: true,
      render: (value?: string | null) => value || '-',
    },
    {
      title: '订单金额',
      dataIndex: 'totalAmount',
      width: 140,
      align: 'right',
      render: (value?: number | string | null) => formatAmount(value),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 110,
      render: (value?: string | null) => (
        <Tag color={ORDER_STATUS_COLOR[value ?? '']}>
          {ORDER_STATUS_LABEL[value ?? ''] ?? value ?? '-'}
        </Tag>
      ),
    },
    {
      title: 'ERP 订单号',
      dataIndex: 'erpOrderNo',
      width: 170,
      render: (value?: string | null) => value || '-',
    },
    {
      title: 'ERP 状态',
      dataIndex: 'erpStatus',
      width: 120,
      render: (value?: string | null) => value || '-',
    },
    {
      title: '尝试',
      dataIndex: 'pushAttempts',
      width: 80,
      align: 'right',
      render: (value?: number | null) => value ?? '-',
    },
    {
      title: '最近失败原因',
      dataIndex: 'lastPushError',
      width: 220,
      ellipsis: true,
      render: (value?: string | null) => value || '-',
    },
    {
      title: '操作',
      key: 'action',
      fixed: 'right',
      width: 220,
      render: (_, row) => (
        <Space size={0}>
          <Button size="small" type="link" onClick={() => setDetailId(row.id)}>
            详情
          </Button>
          {row.status === 'DRAFT' && hasPermission('sys:crm:order:update') && (
            <Button size="small" type="link" onClick={() => openEdit(row)}>
              编辑
            </Button>
          )}
          {(row.status === 'DRAFT' || row.status === 'REJECTED') &&
            hasPermission('sys:crm:order:submit') && (
              <Popconfirm
                title="提交审批"
                description="提交后将进入审批流程，草稿不可再编辑。"
                onConfirm={() => void runAction(() => submitOrder(row.id), '已提交审批')}
              >
                <Button size="small" type="link">
                  提交
                </Button>
              </Popconfirm>
            )}
          {(row.status === 'APPROVED' || row.status === 'PUSH_FAILED') &&
            hasPermission('sys:crm:order:retry') && (
              <Popconfirm
                title="重试下发"
                description="将重置 outbox 并尽快重试下发 ERP。"
                onConfirm={() => void runAction(() => retryOrder(row.id), '已重置下发任务')}
              >
                <Button size="small" type="link">
                  重试
                </Button>
              </Popconfirm>
            )}
          {row.status !== 'CANCELLED' &&
            row.status !== 'PUSHED' &&
            hasPermission('sys:crm:order:cancel') && (
              <Popconfirm
                title="取消订单"
                description="取消后不可恢复；已下发订单将请求 ERP 取消原单。"
                onConfirm={() => void runAction(() => cancelOrder(row.id), '订单已取消')}
              >
                <Button size="small" type="link" danger>
                  取消
                </Button>
              </Popconfirm>
            )}
        </Space>
      ),
    },
  ];

  return (
    <Card
      title="商务订单"
      extra={
        <Space>
          {hasPermission('sys:crm:order:reconcile') && (
            <Button loading={reconciling} onClick={() => void handleReconcile()}>
              对账履约状态
            </Button>
          )}
          {hasPermission('sys:crm:order:create') && (
            <Button type="primary" onClick={openCreate}>
              新建订单
            </Button>
          )}
        </Space>
      }
    >
      <Form
        form={filterForm}
        layout="inline"
        onFinish={handleSearch}
        style={{ marginBottom: 16, rowGap: 12 }}
      >
        <Form.Item name="keyword">
          <Input placeholder="订单编号 / 标题" allowClear style={{ width: 200 }} />
        </Form.Item>
        <Form.Item name="customerId">
          <InputNumber min={1} precision={0} placeholder="客户 ID" style={{ width: 130 }} />
        </Form.Item>
        <Form.Item name="status">
          <Select
            placeholder="订单状态"
            allowClear
            style={{ width: 140 }}
            options={ORDER_STATUS_OPTIONS}
          />
        </Form.Item>
        <Form.Item>
          <Space>
            <Button type="primary" htmlType="submit">
              查询
            </Button>
            <Button onClick={handleReset}>重置</Button>
          </Space>
        </Form.Item>
      </Form>
      <Table<SalesOrderView>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={rows}
        scroll={{ x: 1800 }}
        pagination={{
          current: page.current,
          pageSize: page.size,
          total,
          showSizeChanger: true,
          showTotal: (count) => `共 ${count} 条`,
          onChange: (current, size) => setPage({ current, size }),
        }}
      />
      <Typography.Text type="secondary" style={{ display: 'block', marginTop: 12 }}>
        审批由 workflow 负责，履约 / 发货 / 库存 / 应收归 ERP；CRM 通过 outbox 幂等下发并按 CRM
        单号对账。
      </Typography.Text>

      <OrderFormModal
        open={formOpen}
        initial={editing}
        onCancel={() => setFormOpen(false)}
        onSaved={() => {
          setFormOpen(false);
          void loadData();
        }}
      />
      <OrderDetailDrawer orderId={detailId} onClose={() => setDetailId(null)} />
    </Card>
  );
}
