// 商城订单：我的订单 / 全部订单两个口径，取消、发货、确认收货
//
// 支付不在本页发起：支付单归 whale-pay，支付结果由它回调 `/mall/internal/order/paid`。
// 页面只展示状态，不伪造"支付成功"按钮——没有支付渠道的假按钮只会制造脏数据。
import { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Card,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  Tooltip,
  Typography,
  message,
} from 'antd';
import type { TableProps } from 'antd';
import { useNavigate } from 'react-router-dom';
import {
  cancelOrder,
  completeOrder,
  pageMyOrders,
  pageOrders,
  type OrderView,
} from '../../api/mall';
import { hasPermission } from '../../utils/permission';
import {
  MODE_LABEL,
  ORDER_STATUS_COLOR,
  ORDER_STATUS_LABEL,
  ORDER_STATUS_OPTIONS,
  formatDateTime,
  formatMoney,
} from '../../utils/dict';

/** 列表口径：我的 / 全部 */
type Scope = 'mine' | 'all';

/**
 * 读取错误文案。
 *
 * @param error 异常对象
 * @returns 文案
 */
function errorText(error: unknown): string {
  return error instanceof Error ? error.message : '操作失败';
}

export default function OrderPage() {
  const navigate = useNavigate();
  const [scope, setScope] = useState<Scope>('mine');
  const [rows, setRows] = useState<OrderView[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState({ current: 1, size: 10 });
  const [filter, setFilter] = useState<{ status?: string; keyword?: string }>({});
  const [cancelTarget, setCancelTarget] = useState<OrderView | null>(null);
  const [cancelReason, setCancelReason] = useState('');
  const [busy, setBusy] = useState(false);

  const canManage = hasPermission('sys:mall:order:manage');
  const canCancel = hasPermission('sys:mall:order:cancel');

  /** 分页查询 */
  const load = useCallback(async () => {
    setLoading(true);
    try {
      const query = {
        status: filter.status,
        current: page.current,
        size: page.size,
      };
      const result =
        scope === 'mine'
          ? await pageMyOrders(query)
          : await pageOrders({ ...query, keyword: filter.keyword?.trim() || undefined });
      setRows(result.records ?? []);
      setTotal(Number(result.total ?? 0));
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setLoading(false);
    }
  }, [scope, filter, page]);

  useEffect(() => {
    void load();
  }, [load]);

  /** 取消订单 */
  const submitCancel = async () => {
    if (!cancelTarget) {
      return;
    }
    setBusy(true);
    try {
      await cancelOrder(cancelTarget.id, cancelReason.trim() || undefined);
      message.success('订单已取消');
      setCancelTarget(null);
      setCancelReason('');
      await load();
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setBusy(false);
    }
  };

  /** 确认收货 */
  const handleComplete = async (row: OrderView) => {
    try {
      await completeOrder(row.id);
      message.success('订单已完成');
      await load();
    } catch (error) {
      message.error(errorText(error));
    }
  };

  const baseColumns: NonNullable<TableProps<OrderView>['columns']> = [
    {
      title: '订单号',
      dataIndex: 'orderNo',
      width: 190,
      render: (value: string, row) => (
        <Typography.Link onClick={() => navigate(`/order/${row.id}`)}>{value}</Typography.Link>
      ),
    },
    { title: '渠道', dataIndex: 'channelCode', width: 150, render: (value?: string) => value || '-' },
    {
      title: '形态',
      dataIndex: 'mode',
      width: 100,
      render: (value: string) => MODE_LABEL[value] ?? value,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (value: string) => (
        <Tag color={ORDER_STATUS_COLOR[value]}>{ORDER_STATUS_LABEL[value] ?? value}</Tag>
      ),
    },
    {
      title: '金额',
      dataIndex: 'totalAmount',
      width: 140,
      render: (value: number, row) => formatMoney(value, row.currencyType),
    },
    { title: '件数', dataIndex: 'totalQuantity', width: 80 },
    {
      title: '下单时间',
      dataIndex: 'createTime',
      width: 170,
      render: (value?: string) => formatDateTime(value),
    },
    {
      title: '操作',
      width: 200,
      render: (_, row) => (
        <Space size="small">
          <Button type="link" size="small" onClick={() => navigate(`/order/${row.id}`)}>
            详情
          </Button>
          {canCancel && row.status === 'PENDING_PAYMENT' && (
            <Button type="link" size="small" danger onClick={() => setCancelTarget(row)}>
              取消
            </Button>
          )}
          {/* 发货不是「点一下就发」：要选本次发哪几件、填承运商与运单号，一个订单可拆多个包裹。
              完整流程在订单详情页（打开发货弹窗按可发数量预填），列表页只做入口。 */}
          {canManage && row.status === 'PAID' && (
            <Button type="link" size="small" onClick={() => navigate(`/order/${row.id}`)}>
              去发货
            </Button>
          )}
          {row.status === 'SHIPPED' && (
            <Popconfirm title="确认收货？" onConfirm={() => handleComplete(row)}>
              <Button type="link" size="small">
                确认收货
              </Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  /** 买家列只在「全部订单」出现：我的订单里买家恒为当前登录人，占一列没有信息量 */
  const buyerColumn: NonNullable<TableProps<OrderView>['columns']>[number] = {
    title: '买家',
    dataIndex: 'buyerName',
    width: 130,
    render: (value?: string, row?: OrderView) => value || row?.buyerUserId || '-',
  };
  const columns: TableProps<OrderView>['columns'] =
    scope === 'all'
      ? [...baseColumns.slice(0, 6), buyerColumn, ...baseColumns.slice(6)]
      : baseColumns;

  return (
    <Card
      title="商城订单"
      extra={
        <Space wrap>
          {scope === 'all' && (
            <Input.Search
              allowClear
              placeholder="订单号"
              style={{ width: 200 }}
              onSearch={(value) => {
                setFilter((prev) => ({ ...prev, keyword: value }));
                setPage((prev) => ({ ...prev, current: 1 }));
              }}
            />
          )}
          <Select
            allowClear
            placeholder="状态"
            style={{ width: 130 }}
            options={ORDER_STATUS_OPTIONS}
            onChange={(value?: string) => {
              setFilter((prev) => ({ ...prev, status: value }));
              setPage((prev) => ({ ...prev, current: 1 }));
            }}
          />
          <Tooltip title="支付由 whale-pay 回调，页面不发起支付">
            <Typography.Text type="secondary">支付走 whale-pay</Typography.Text>
          </Tooltip>
        </Space>
      }
    >
      <Tabs
        activeKey={scope}
        onChange={(key) => {
          setScope(key as Scope);
          setPage((prev) => ({ ...prev, current: 1 }));
        }}
        items={[
          { key: 'mine', label: '我的订单' },
          ...(canManage ? [{ key: 'all', label: '全部订单' }] : []),
        ]}
      />

      <Table<OrderView>
        rowKey="id"
        size="middle"
        loading={loading}
        columns={columns}
        dataSource={rows}
        pagination={{
          current: page.current,
          pageSize: page.size,
          total,
          showSizeChanger: true,
          showTotal: (value) => `共 ${value} 条`,
          onChange: (current, size) => setPage({ current, size }),
        }}
      />

      <Modal
        title={cancelTarget ? `取消订单 · ${cancelTarget.orderNo}` : '取消订单'}
        open={cancelTarget !== null}
        confirmLoading={busy}
        onCancel={() => setCancelTarget(null)}
        onOk={submitCancel}
      >
        <Typography.Paragraph type="secondary">
          只有待支付订单可以取消；已支付订单请走退款流程（M1）。
        </Typography.Paragraph>
        <Input.TextArea
          rows={2}
          maxLength={255}
          placeholder="取消原因（可选）"
          value={cancelReason}
          onChange={(event) => setCancelReason(event.target.value)}
        />
      </Modal>
    </Card>
  );
}
