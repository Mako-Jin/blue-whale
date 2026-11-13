// 发货管理：发货单列表、详情、手工签收与作废
//
// 发货是履约的入口：一个订单可以拆成多个包裹（多张发货单），每张单对应一个运单号。
// 签收是「完成订单」的触发条件，但只有全部发满且全部签收才算完单——
// 只签一个包裹就完结，剩下的货永远发不出去。
import { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Drawer,
  Empty,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import type { TableProps } from 'antd';
import { useNavigate } from 'react-router-dom';
import {
  cancelShipment,
  deliverShipment,
  getShipment,
  pageShipments,
  type ShipmentView,
} from '../../api/mall';
import { hasPermission } from '../../utils/permission';
import {
  CARRIER_LABEL,
  SHIPMENT_STATUS_COLOR,
  SHIPMENT_STATUS_LABEL,
  SHIPMENT_STATUS_OPTIONS,
  formatDateTime,
  formatSpec,
} from '../../utils/dict';

/** 每页条数 */
const PAGE_SIZE = 10;

/**
 * 读取错误文案。
 *
 * @param error 异常对象
 * @returns 文案
 */
function errorText(error: unknown): string {
  return error instanceof Error ? error.message : '操作失败';
}

export default function ShipmentPage() {
  const navigate = useNavigate();
  const [rows, setRows] = useState<ShipmentView[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<string | undefined>(undefined);
  const [detail, setDetail] = useState<ShipmentView | null>(null);
  const [cancelTarget, setCancelTarget] = useState<ShipmentView | null>(null);
  const [cancelReason, setCancelReason] = useState('');

  const canManage = hasPermission('sys:mall:shipment:manage');

  /** 加载列表 */
  const load = useCallback(
    async (page: number) => {
      setLoading(true);
      try {
        const result = await pageShipments({
          keyword: keyword.trim() || undefined,
          status,
          current: page,
          size: PAGE_SIZE,
        });
        setRows(result.records ?? []);
        setTotal(Number(result.total ?? 0));
        setCurrent(page);
      } catch (error) {
        message.error(errorText(error));
      } finally {
        setLoading(false);
      }
    },
    [keyword, status],
  );

  useEffect(() => {
    void load(1);
  }, [load]);

  /** 打开详情（列表行不带明细，详情要重新拉一次） */
  const openDetail = async (row: ShipmentView) => {
    try {
      setDetail(await getShipment(row.id));
    } catch (error) {
      message.error(errorText(error));
    }
  };

  /** 手工签收 */
  const runDeliver = async (row: ShipmentView) => {
    setBusy(true);
    try {
      await deliverShipment(row.id);
      message.success('已标记签收；若订单全部发满，订单将自动完成');
      await load(current);
      if (detail?.id === row.id) {
        setDetail(await getShipment(row.id));
      }
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setBusy(false);
    }
  };

  /** 作废 */
  const runCancel = async () => {
    if (!cancelTarget) {
      return;
    }
    setBusy(true);
    try {
      await cancelShipment(cancelTarget.id, cancelReason.trim() || undefined);
      message.success('已作废，已发数量与预占库存已释放');
      setCancelTarget(null);
      setCancelReason('');
      setDetail(null);
      await load(current);
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setBusy(false);
    }
  };

  const columns: TableProps<ShipmentView>['columns'] = [
    {
      title: '发货单号',
      dataIndex: 'shipmentNo',
      width: 190,
      render: (value: string, row) => (
        <Button
          type="link"
          size="small"
          style={{ padding: 0 }}
          onClick={() => void openDetail(row)}
        >
          {value}
        </Button>
      ),
    },
    {
      title: '订单号',
      dataIndex: 'orderNo',
      width: 180,
      render: (value: string, row) => (
        <Button
          type="link"
          size="small"
          style={{ padding: 0 }}
          onClick={() => navigate(`/order/${row.orderId}`)}
        >
          {value}
        </Button>
      ),
    },
    {
      title: '承运商',
      dataIndex: 'carrierName',
      width: 130,
      render: (value: string, row) => value || CARRIER_LABEL[row.carrierCode] || row.carrierCode,
    },
    { title: '运单号', dataIndex: 'trackingNo', width: 180 },
    {
      title: '件数',
      dataIndex: 'totalQuantity',
      width: 90,
      render: (value: number, row) => `${value} 件 / ${row.itemCount} 行`,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (value: string) => (
        <Tag color={SHIPMENT_STATUS_COLOR[value]}>{SHIPMENT_STATUS_LABEL[value] ?? value}</Tag>
      ),
    },
    {
      title: '发货时间',
      dataIndex: 'shippedTime',
      width: 170,
      render: (value?: string) => formatDateTime(value),
    },
    {
      title: '操作',
      width: 160,
      render: (_, row) => (
        <Space size="small">
          <Button type="link" size="small" onClick={() => void openDetail(row)}>
            详情
          </Button>
          {canManage && row.status === 'SHIPPED' ? (
            <Popconfirm title="确认已签收？" onConfirm={() => void runDeliver(row)}>
              <Button type="link" size="small">
                签收
              </Button>
            </Popconfirm>
          ) : null}
          {canManage && row.status === 'SHIPPED' ? (
            <Button
              type="link"
              size="small"
              danger
              onClick={() => {
                setCancelTarget(row);
                setCancelReason('');
              }}
            >
              作废
            </Button>
          ) : null}
        </Space>
      ),
    },
  ];

  return (
    <Card
      title="发货管理"
      extra={
        <Space wrap>
          <Input.Search
            allowClear
            style={{ width: 260 }}
            placeholder="发货单号 / 订单号 / 运单号"
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            onSearch={() => void load(1)}
          />
          <Select
            allowClear
            style={{ width: 140 }}
            placeholder="状态"
            value={status}
            options={SHIPMENT_STATUS_OPTIONS}
            onChange={(value?: string) => setStatus(value)}
          />
          <Button onClick={() => void load(current)}>刷新</Button>
        </Space>
      }
    >
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 12 }}
        message="发货由订单详情页发起（一个订单可拆成多个包裹）；这里负责跟踪、手工签收与作废。"
      />
      <Table<ShipmentView>
        rowKey="id"
        size="middle"
        loading={loading}
        columns={columns}
        dataSource={rows}
        locale={{ emptyText: <Empty description="还没有发货单" /> }}
        pagination={{
          current,
          pageSize: PAGE_SIZE,
          total,
          showSizeChanger: false,
          onChange: (page) => void load(page),
        }}
      />

      <Drawer
        title={detail ? `发货单 ${detail.shipmentNo}` : '发货单详情'}
        width={720}
        open={detail !== null}
        onClose={() => setDetail(null)}
      >
        {detail ? (
          <>
            <Descriptions column={2} size="small" bordered>
              <Descriptions.Item label="状态">
                <Tag color={SHIPMENT_STATUS_COLOR[detail.status]}>
                  {SHIPMENT_STATUS_LABEL[detail.status] ?? detail.status}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="订单号">{detail.orderNo}</Descriptions.Item>
              <Descriptions.Item label="承运商">{detail.carrierName}</Descriptions.Item>
              <Descriptions.Item label="运单号">{detail.trackingNo}</Descriptions.Item>
              <Descriptions.Item label="发货时间">
                {formatDateTime(detail.shippedTime)}
              </Descriptions.Item>
              <Descriptions.Item label="签收时间">
                {formatDateTime(detail.deliveredTime)}
              </Descriptions.Item>
              {detail.cancelTime ? (
                <Descriptions.Item label="作废时间">
                  {formatDateTime(detail.cancelTime)}
                </Descriptions.Item>
              ) : null}
              {detail.cancelReason ? (
                <Descriptions.Item label="作废原因">{detail.cancelReason}</Descriptions.Item>
              ) : null}
              <Descriptions.Item label="备注" span={2}>
                {detail.remark || '-'}
              </Descriptions.Item>
            </Descriptions>

            <Typography.Title level={5} style={{ marginTop: 20 }}>
              发货明细
            </Typography.Title>
            <Table
              rowKey={(row) => String(row.orderItemId)}
              size="small"
              pagination={false}
              dataSource={detail.items ?? []}
              columns={[
                { title: '商品', dataIndex: 'spuName' },
                { title: 'SKU', dataIndex: 'skuCode', width: 160 },
                {
                  title: '规格',
                  dataIndex: 'specJson',
                  width: 140,
                  render: (value?: string) => formatSpec(value),
                },
                { title: '本单发货', dataIndex: 'quantity', width: 100 },
              ]}
            />

            {canManage && detail.status === 'SHIPPED' ? (
              <Space style={{ marginTop: 20 }}>
                <Popconfirm title="确认已签收？" onConfirm={() => void runDeliver(detail)}>
                  <Button type="primary" loading={busy}>
                    标记签收
                  </Button>
                </Popconfirm>
                <Button
                  danger
                  onClick={() => {
                    setCancelTarget(detail);
                    setCancelReason('');
                  }}
                >
                  作废发货单
                </Button>
              </Space>
            ) : null}
          </>
        ) : null}
      </Drawer>

      <Modal
        title="作废发货单"
        open={cancelTarget !== null}
        confirmLoading={busy}
        onCancel={() => setCancelTarget(null)}
        onOk={runCancel}
      >
        <Typography.Paragraph type="secondary">
          作废会释放这一单占用的已发数量与预占库存，货可以重新发。已签收的发货单不能作废——
          货已经在买家手里，该走售后。
        </Typography.Paragraph>
        <Input.TextArea
          rows={2}
          maxLength={255}
          placeholder="作废原因（可选）"
          value={cancelReason}
          onChange={(event) => setCancelReason(event.target.value)}
        />
      </Modal>
    </Card>
  );
}
