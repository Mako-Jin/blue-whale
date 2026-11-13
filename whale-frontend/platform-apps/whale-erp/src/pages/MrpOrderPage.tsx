// ERP MRP 计划订单页：筛选、计划订单列表与需求追溯
import { useCallback, useEffect, useState } from 'react';
import { Button, Descriptions, Drawer, Input, Space, Table, Tag, message } from 'antd';
import type { TableProps } from 'antd';
import {
  getMrpPlannedOrder,
  pageMrpPlannedOrders,
  type MrpPlannedOrderView,
  type MrpTraceView,
} from '../api/mfg';
import { SCM_STATUS_LABEL, quantity, scmStatusColor } from '../utils/scm';

/** 计划订单建议类型文案 */
const ORDER_TYPE_LABEL: Record<string, string> = {
  PURCHASE: '采购建议',
  PRODUCTION: '生产建议',
};

/** MRP 计划订单页 */
export default function MrpOrderPage() {
  const [data, setData] = useState<MrpPlannedOrderView[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [orderType, setOrderType] = useState<string | undefined>();
  const [detail, setDetail] = useState<MrpPlannedOrderView | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageMrpPlannedOrders({ size: 50, keyword, orderType });
      setData(page.records);
    } finally {
      setLoading(false);
    }
  }, [keyword, orderType]);

  useEffect(() => {
    void load();
  }, [load]);

  const openTrace = async (id: number) => {
    setDetail(await getMrpPlannedOrder(id));
    setDetailOpen(true);
  };

  const columns: TableProps<MrpPlannedOrderView>['columns'] = [
    { title: '计划订单号', dataIndex: 'orderNo', key: 'orderNo', width: 180 },
    {
      title: '建议类型',
      dataIndex: 'orderType',
      key: 'orderType',
      width: 110,
      render: (value) => (
        <Tag color={value === 'PRODUCTION' ? 'blue' : 'orange'}>
          {ORDER_TYPE_LABEL[String(value)] ?? value}
        </Tag>
      ),
    },
    {
      title: '物料',
      key: 'item',
      render: (_, record) => `${record.itemCode}${record.itemName ? ` ${record.itemName}` : ''}`,
    },
    { title: '单位', dataIndex: 'uom', key: 'uom', width: 80 },
    { title: '层级', dataIndex: 'itemLevel', key: 'itemLevel', width: 80 },
    {
      title: '毛需求',
      dataIndex: 'grossQuantity',
      key: 'grossQuantity',
      width: 110,
      render: quantity,
    },
    {
      title: '可用量',
      dataIndex: 'availableQuantity',
      key: 'availableQuantity',
      width: 110,
      render: quantity,
    },
    { title: '净需求', dataIndex: 'netQuantity', key: 'netQuantity', width: 110, render: quantity },
    { title: '需求日期', dataIndex: 'needDate', key: 'needDate', width: 110 },
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
      width: 120,
      render: (_, record) => (
        <Button type="link" size="small" onClick={() => openTrace(record.id)}>
          查看追溯
        </Button>
      ),
    },
  ];

  const traceColumns: TableProps<MrpTraceView>['columns'] = [
    { title: '来源需求行', dataIndex: 'demandLineNo', key: 'demandLineNo', width: 110 },
    { title: 'BOM 路径', dataIndex: 'bomPath', key: 'bomPath', width: 130 },
    { title: '层级', dataIndex: 'depth', key: 'depth', width: 80 },
    { title: '单位累计用量', dataIndex: 'unitQty', key: 'unitQty', width: 130, render: quantity },
    {
      title: '损耗率(%)',
      dataIndex: 'lossRate',
      key: 'lossRate',
      width: 110,
      render: quantity,
    },
    {
      title: '贡献毛需求',
      dataIndex: 'grossQuantity',
      key: 'grossQuantity',
      width: 130,
      render: quantity,
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <Input.Search
          allowClear
          placeholder="计划订单号 / 物料"
          style={{ width: 240 }}
          onSearch={(value) => setKeyword(value)}
        />
        <Button onClick={() => setOrderType(undefined)} type={orderType ? 'default' : 'primary'}>
          全部
        </Button>
        <Button
          onClick={() => setOrderType('PURCHASE')}
          type={orderType === 'PURCHASE' ? 'primary' : 'default'}
        >
          采购建议
        </Button>
        <Button
          onClick={() => setOrderType('PRODUCTION')}
          type={orderType === 'PRODUCTION' ? 'primary' : 'default'}
        >
          生产建议
        </Button>
        <Button onClick={() => message.info('计划订单由 MRP 运算生成，请到「MRP 运算」发起')}>
          生成说明
        </Button>
      </Space>
      <Table<MrpPlannedOrderView>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={data}
      />
      <Drawer
        open={detailOpen}
        title={detail ? `计划订单 ${detail.orderNo}` : '计划订单'}
        width={1000}
        onClose={() => setDetailOpen(false)}
        destroyOnHidden
      >
        {detail ? (
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <Descriptions column={2} size="small" bordered>
              <Descriptions.Item label="建议类型">
                <Tag color={detail.orderType === 'PRODUCTION' ? 'blue' : 'orange'}>
                  {ORDER_TYPE_LABEL[detail.orderType] ?? detail.orderType}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="状态">
                <Tag color={scmStatusColor(detail.status)}>
                  {SCM_STATUS_LABEL[detail.status] ?? detail.status}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="物料">
                {detail.itemCode} {detail.itemName ?? ''}
              </Descriptions.Item>
              <Descriptions.Item label="单位">{detail.uom ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="BOM">
                {detail.bomNo ? `${detail.bomNo} / ${detail.bomVersion}` : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="BOM 最小层级">{detail.itemLevel ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="毛需求">{quantity(detail.grossQuantity)}</Descriptions.Item>
              <Descriptions.Item label="可用量">
                {quantity(detail.availableQuantity)}
              </Descriptions.Item>
              <Descriptions.Item label="净需求">{quantity(detail.netQuantity)}</Descriptions.Item>
              <Descriptions.Item label="需求日期">{detail.needDate}</Descriptions.Item>
            </Descriptions>
            <Table<MrpTraceView>
              rowKey={(record) => `${record.demandLineNo}-${record.bomPath}`}
              size="small"
              title={() => '需求追溯'}
              columns={traceColumns}
              dataSource={detail.traces ?? []}
              pagination={false}
            />
          </Space>
        ) : null}
      </Drawer>
    </Space>
  );
}
