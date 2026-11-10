// 退换货申请：按商务订单选行申请、提交审批、下发失败重试与对账 ERP 退货单
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
import { getOrder, type SalesOrderLineView, type SalesOrderView } from '../../api/commerce';
import {
  cancelReturn,
  createReturn,
  getReturn,
  pageReturns,
  reconcileReturn,
  retryReturn,
  submitReturn,
  updateReturn,
  type ReturnLineInput,
  type ReturnPageQuery,
  type ReturnRequestInput,
  type ReturnRequestView,
} from '../../api/service';
import { hasPermission } from '../../utils/permission';
import {
  RETURN_STATUS_COLOR,
  RETURN_STATUS_LABEL,
  RETURN_STATUS_OPTIONS,
  RETURN_TYPE_LABEL,
} from '../../utils/dict';

/** 是否具备退换货管理权限 */
const CAN_MANAGE = hasPermission('sys:crm:return:manage');

/** 筛选条件 */
interface ReturnFilterValues {
  keyword?: string;
  orderId?: number | null;
  customerId?: number | null;
  status?: string;
}

/** 申请行表单值 */
interface ReturnLineFormValues {
  orderLineNo: number;
  quantity: number;
}

/** 申请表单值 */
interface ReturnFormValues {
  requestType: string;
  orderId: number;
  reason?: string;
  lines: ReturnLineFormValues[];
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

/**
 * 格式化数量。
 *
 * @param value 后端数量
 * @returns 数量文本
 */
function formatQuantity(value?: number | string | null): string {
  if (value === null || value === undefined || value === '') {
    return '-';
  }
  const num = Number(value);
  return Number.isNaN(num) ? String(value) : String(num);
}

interface ReturnFormModalProps {
  open: boolean;
  initial: ReturnRequestView | null;
  onCancel: () => void;
  onSaved: () => void;
}

/** 退换货申请新建 / 编辑弹窗（按商务订单行填写退货数量） */
function ReturnFormModal({ open, initial, onCancel, onSaved }: ReturnFormModalProps) {
  const [form] = Form.useForm<ReturnFormValues>();
  const [saving, setSaving] = useState(false);
  const [loadingOrder, setLoadingOrder] = useState(false);
  const [order, setOrder] = useState<SalesOrderView | null>(null);
  const [orderLines, setOrderLines] = useState<SalesOrderLineView[]>([]);

  /**
   * 按订单 ID 载入订单及其行。
   *
   * @param orderId 商务订单 ID
   */
  const loadOrder = useCallback(async (orderId: number) => {
    setLoadingOrder(true);
    try {
      const data = await getOrder(orderId);
      setOrder(data);
      setOrderLines(data.lines ?? []);
      return data;
    } catch (error) {
      setOrder(null);
      setOrderLines([]);
      message.error(errorText(error));
      return null;
    } finally {
      setLoadingOrder(false);
    }
  }, []);

  useEffect(() => {
    if (!open) {
      setOrder(null);
      setOrderLines([]);
      return;
    }
    if (initial) {
      form.setFieldsValue({
        requestType: initial.requestType ?? 'RETURN',
        orderId: initial.orderId ?? 0,
        reason: initial.reason ?? undefined,
        lines: (initial.items ?? []).map((item) => ({
          orderLineNo: item.orderLineNo ?? 0,
          quantity: item.quantity === null || item.quantity === undefined ? 1 : Number(item.quantity),
        })),
      });
      if (initial.orderId) {
        void loadOrder(initial.orderId);
      }
    } else {
      form.resetFields();
      form.setFieldsValue({ requestType: 'RETURN', lines: [] });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, initial, form]);

  /**
   * 订单 ID 变更后载入订单行。
   */
  const handleOrderChange = async () => {
    const orderId = form.getFieldValue('orderId');
    if (!orderId) {
      setOrder(null);
      setOrderLines([]);
      return;
    }
    const data = await loadOrder(orderId as number);
    if (data) {
      form.setFieldValue('lines', []);
    }
  };

  const handleOk = async () => {
    setSaving(true);
    try {
      const values = await form.validateFields();
      const lines: ReturnLineInput[] = (values.lines ?? []).map((line) => ({
        orderLineNo: line.orderLineNo,
        quantity: line.quantity,
      }));
      const payload: ReturnRequestInput = {
        requestType: values.requestType,
        orderId: values.orderId,
        reason: values.reason,
        lines,
      };
      if (initial) {
        await updateReturn(initial.id, payload);
        message.success('退换货申请已更新');
      } else {
        const created = await createReturn(payload);
        message.success(`退换货申请已创建：${created.returnNo}`);
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
      title={initial ? `编辑退换货申请：${initial.returnNo}` : '新建退换货申请'}
      width={860}
      confirmLoading={saving}
      okText="保存"
      cancelText="取消"
      onOk={handleOk}
      onCancel={onCancel}
      destroyOnClose
    >
      <Form form={form} layout="vertical" preserve={false}>
        <Space size={12} style={{ display: 'flex' }}>
          <Form.Item name="requestType" label="申请类型" style={{ width: 160 }}>
            <Select
              options={[
                { value: 'RETURN', label: RETURN_TYPE_LABEL.RETURN },
                { value: 'EXCHANGE', label: `${RETURN_TYPE_LABEL.EXCHANGE}（暂未开放）`, disabled: true },
              ]}
            />
          </Form.Item>
          <Form.Item
            name="orderId"
            label="商务订单 ID"
            rules={[{ required: true, message: '请输入商务订单 ID' }]}
            style={{ width: 220 }}
          >
            <InputNumber
              min={1}
              precision={0}
              style={{ width: '100%' }}
              onChange={() => {
                setOrder(null);
                setOrderLines([]);
                form.setFieldValue('lines', []);
              }}
            />
          </Form.Item>
          <Form.Item label=" ">
            <Button loading={loadingOrder} onClick={() => void handleOrderChange()}>
              载入订单行
            </Button>
          </Form.Item>
        </Space>
        {order && (
          <Typography.Text type="secondary" style={{ display: 'block', marginBottom: 12 }}>
            订单 {order.orderNo} · ERP 单号 {order.erpOrderNo || '未下发'} · 客户 {order.customerId}
            {!order.erpOrderNo && '（订单未下发 ERP，暂不能申请退货）'}
          </Typography.Text>
        )}
        <Form.Item name="reason" label="退货原因">
          <Input.TextArea rows={2} maxLength={500} showCount />
        </Form.Item>
        <Typography.Text strong>退货明细</Typography.Text>
        <Form.List name="lines">
          {(fields, { add, remove }) => (
            <>
              <Table<FormListFieldData>
                style={{ marginTop: 8 }}
                rowKey="key"
                size="small"
                dataSource={fields}
                pagination={false}
                locale={{ emptyText: '请先载入订单行后新增退货明细' }}
                columns={[
                  {
                    title: '订单行',
                    width: 320,
                    render: (_, field) => (
                      <Form.Item
                        name={[field.name, 'orderLineNo']}
                        rules={[{ required: true, message: '请选择订单行' }]}
                        style={{ marginBottom: 0 }}
                      >
                        <Select
                          placeholder="选择订单行"
                          options={orderLines.map((line) => ({
                            value: line.lineNo ?? 0,
                            label: `行 ${line.lineNo ?? '-'} · ${line.itemName || line.itemCode || '-'} · 数量 ${formatQuantity(line.quantity)}`,
                          }))}
                        />
                      </Form.Item>
                    ),
                  },
                  {
                    title: '退货数量',
                    width: 160,
                    render: (_, field) => (
                      <Form.Item
                        name={[field.name, 'quantity']}
                        rules={[{ required: true, message: '请输入退货数量' }]}
                        style={{ marginBottom: 0 }}
                      >
                        <InputNumber min={0.0001} precision={4} style={{ width: '100%' }} />
                      </Form.Item>
                    ),
                  },
                  {
                    title: '操作',
                    width: 70,
                    render: (_, field) => (
                      <Button size="small" type="link" danger onClick={() => remove(field.name)}>
                        删除
                      </Button>
                    ),
                  },
                ]}
              />
              <Button
                type="dashed"
                block
                disabled={orderLines.length === 0}
                style={{ marginTop: 8 }}
                onClick={() => add({ quantity: 1 })}
              >
                新增退货明细
              </Button>
            </>
          )}
        </Form.List>
      </Form>
    </Modal>
  );
}

interface ReturnDetailDrawerProps {
  returnId: number | null;
  onClose: () => void;
  onRefresh: () => void;
}

/** 退换货申请详情抽屉 */
function ReturnDetailDrawer({ returnId, onClose, onRefresh }: ReturnDetailDrawerProps) {
  const [detail, setDetail] = useState<ReturnRequestView | null>(null);

  useEffect(() => {
    if (!returnId) {
      setDetail(null);
      return;
    }
    let active = true;
    getReturn(returnId)
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
  }, [returnId]);

  const runReconcile = async () => {
    if (!returnId) {
      return;
    }
    try {
      const data = await reconcileReturn(returnId);
      setDetail(data);
      message.success('已对账 ERP 退货单状态');
      onRefresh();
    } catch (error) {
      message.error(errorText(error));
    }
  };

  return (
    <Drawer
      open={Boolean(returnId)}
      title={detail ? `退换货详情：${detail.returnNo}` : '退换货详情'}
      width={780}
      onClose={onClose}
      extra={
        CAN_MANAGE && detail?.status !== 'CANCELLED' ? (
          <Button onClick={() => void runReconcile()}>对账 ERP 状态</Button>
        ) : null
      }
    >
      {detail && (
        <>
          <Descriptions bordered size="small" column={2}>
            <Descriptions.Item label="申请单号">{detail.returnNo}</Descriptions.Item>
            <Descriptions.Item label="状态">
              <Tag color={RETURN_STATUS_COLOR[detail.status as string]}>
                {RETURN_STATUS_LABEL[detail.status as string] ?? detail.status}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="申请类型">
              {RETURN_TYPE_LABEL[detail.requestType ?? ''] ?? detail.requestType ?? '-'}
            </Descriptions.Item>
            <Descriptions.Item label="商务订单 ID">{detail.orderId ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="ERP 订单号">{detail.erpOrderNo || '-'}</Descriptions.Item>
            <Descriptions.Item label="ERP 退货单号">{detail.erpReturnNo || '-'}</Descriptions.Item>
            <Descriptions.Item label="客户编码">{detail.customerCode || '-'}</Descriptions.Item>
            <Descriptions.Item label="客户名称">{detail.customerName || '-'}</Descriptions.Item>
            <Descriptions.Item label="退货总额">{formatAmount(detail.totalAmount)}</Descriptions.Item>
            <Descriptions.Item label="下发尝试次数">{detail.pushAttempts ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="ERP 退货单状态">{detail.erpStatus || '-'}</Descriptions.Item>
            <Descriptions.Item label="最近失败原因">{detail.lastError || '-'}</Descriptions.Item>
            <Descriptions.Item label="退货原因" span={2}>
              {detail.reason || '-'}
            </Descriptions.Item>
          </Descriptions>
          <Typography.Text strong style={{ display: 'block', margin: '16px 0 8px' }}>
            退货明细
          </Typography.Text>
          <Table
            rowKey={(line) => String(line.lineNo ?? 0)}
            size="small"
            pagination={false}
            dataSource={detail.items ?? []}
            columns={[
              { title: '行号', dataIndex: 'lineNo', width: 60 },
              { title: '订单行号', dataIndex: 'orderLineNo', width: 90 },
              { title: '物料编码', dataIndex: 'itemCode', width: 130, render: (value) => value || '-' },
              { title: '物料名称', dataIndex: 'itemName', width: 160, render: (value) => value || '-' },
              {
                title: '退货数量',
                dataIndex: 'quantity',
                width: 100,
                align: 'right',
                render: (value) => formatQuantity(value),
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
            退货过账（库存与应收）由 ERP 负责，CRM 只保留申请、审批与 ERP 单号回写。
          </Typography.Text>
        </>
      )}
    </Drawer>
  );
}

/** 退换货申请页 */
export default function ReturnPage() {
  const [filterForm] = Form.useForm<ReturnFilterValues>();
  const [rows, setRows] = useState<ReturnRequestView[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [filters, setFilters] = useState<ReturnPageQuery>({});
  const [page, setPage] = useState({ current: 1, size: 10 });
  const [editing, setEditing] = useState<ReturnRequestView | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const [detailId, setDetailId] = useState<number | null>(null);

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const result = await pageReturns({ ...filters, current: page.current, size: page.size });
      setRows(result.records);
      setTotal(result.total);
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setLoading(false);
    }
  }, [filters, page]);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  const handleSearch = (values: ReturnFilterValues) => {
    setFilters({
      keyword: values.keyword || undefined,
      orderId: values.orderId ?? null,
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

  const openEdit = async (row: ReturnRequestView) => {
    try {
      const detail = await getReturn(row.id);
      setEditing(detail);
      setFormOpen(true);
    } catch (error) {
      message.error(errorText(error));
    }
  };

  const runAction = async (action: () => Promise<ReturnRequestView>, successText: string) => {
    try {
      await action();
      message.success(successText);
      void loadData();
    } catch (error) {
      message.error(errorText(error));
    }
  };

  const columns: TableProps<ReturnRequestView>['columns'] = [
    { title: '申请单号', dataIndex: 'returnNo', width: 200, fixed: 'left' },
    {
      title: '类型',
      dataIndex: 'requestType',
      width: 90,
      render: (value?: string | null) => (
        <Tag>{RETURN_TYPE_LABEL[value ?? ''] ?? value ?? '-'}</Tag>
      ),
    },
    { title: '客户编码', dataIndex: 'customerCode', width: 130, render: (value?: string | null) => value || '-' },
    {
      title: '商务订单 ID',
      dataIndex: 'orderId',
      width: 110,
      align: 'right',
      render: (value?: number | null) => value ?? '-',
    },
    {
      title: 'ERP 订单号',
      dataIndex: 'erpOrderNo',
      width: 170,
      render: (value?: string | null) => value || '-',
    },
    {
      title: '退货金额',
      dataIndex: 'totalAmount',
      width: 130,
      align: 'right',
      render: (value?: number | string | null) => formatAmount(value),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 110,
      render: (value?: string | null) => (
        <Tag color={RETURN_STATUS_COLOR[value ?? '']}>
          {RETURN_STATUS_LABEL[value ?? ''] ?? value ?? '-'}
        </Tag>
      ),
    },
    {
      title: 'ERP 退货单号',
      dataIndex: 'erpReturnNo',
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
      dataIndex: 'lastError',
      width: 220,
      ellipsis: true,
      render: (value?: string | null) => value || '-',
    },
    {
      title: '操作',
      key: 'action',
      fixed: 'right',
      width: 200,
      render: (_, row) => (
        <Space size={0}>
          <Button size="small" type="link" onClick={() => setDetailId(row.id)}>
            详情
          </Button>
          {row.status === 'DRAFT' && CAN_MANAGE && (
            <Button size="small" type="link" onClick={() => void openEdit(row)}>
              编辑
            </Button>
          )}
          {(row.status === 'DRAFT' || row.status === 'REJECTED') && CAN_MANAGE && (
            <Popconfirm
              title="提交审批"
              description="提交后进入审批流程，审批通过将自动下发 ERP 退货过账。"
              onConfirm={() => void runAction(() => submitReturn(row.id), '已提交审批')}
            >
              <Button size="small" type="link">
                提交
              </Button>
            </Popconfirm>
          )}
          {(row.status === 'APPROVED' || row.status === 'FAILED') && CAN_MANAGE && (
            <Popconfirm
              title="重试下发"
              description="将重置 outbox 并尽快重试下发 ERP 退货。"
              onConfirm={() => void runAction(() => retryReturn(row.id), '已重置下发任务')}
            >
              <Button size="small" type="link">
                重试
              </Button>
            </Popconfirm>
          )}
          {(row.status === 'DRAFT' || row.status === 'REJECTED') && CAN_MANAGE && (
            <Popconfirm
              title="取消申请"
              description="取消后不可恢复。"
              onConfirm={() => void runAction(() => cancelReturn(row.id), '申请已取消')}
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
      title="退换货申请"
      extra={
        CAN_MANAGE && (
          <Button type="primary" onClick={openCreate}>
            新建申请
          </Button>
        )
      }
    >
      <Form form={filterForm} layout="inline" onFinish={handleSearch} style={{ marginBottom: 16, rowGap: 12 }}>
        <Form.Item name="keyword">
          <Input placeholder="申请单号 / 原因" allowClear style={{ width: 200 }} />
        </Form.Item>
        <Form.Item name="orderId">
          <InputNumber min={1} precision={0} placeholder="商务订单 ID" style={{ width: 130 }} />
        </Form.Item>
        <Form.Item name="customerId">
          <InputNumber min={1} precision={0} placeholder="客户 ID" style={{ width: 130 }} />
        </Form.Item>
        <Form.Item name="status">
          <Select placeholder="申请状态" allowClear style={{ width: 140 }} options={RETURN_STATUS_OPTIONS} />
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
      <Table<ReturnRequestView>
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
        审批由 workflow 负责；退货过账（库存 / 应收 / 凭证）归 ERP，CRM 通过 outbox 幂等下发并按申请单号对账。
      </Typography.Text>

      <ReturnFormModal
        open={formOpen}
        initial={editing}
        onCancel={() => setFormOpen(false)}
        onSaved={() => {
          setFormOpen(false);
          void loadData();
        }}
      />
      <ReturnDetailDrawer
        returnId={detailId}
        onClose={() => setDetailId(null)}
        onRefresh={() => void loadData()}
      />
    </Card>
  );
}
