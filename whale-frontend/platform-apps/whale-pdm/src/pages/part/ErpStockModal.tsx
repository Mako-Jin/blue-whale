// ERP 物料与库存回显：ERP 不可用时展示降级提示，不阻断零件页
import { useEffect, useState } from 'react';
import { Alert, Descriptions, Modal, Table, Tag } from 'antd';
import type { TableProps } from 'antd';
import {
  getPartErpStock,
  type PartErpStock,
  type PartErpWarehouse,
  type PdmPart,
} from '../../api/part';

const WAREHOUSE_COLUMNS: TableProps<PartErpWarehouse>['columns'] = [
  {
    title: '仓库ID',
    dataIndex: 'warehouseId',
    width: 100,
    render: (value?: string) => value || '—',
  },
  {
    title: '仓库编码',
    dataIndex: 'warehouseCode',
    width: 140,
    render: (value?: string) => value || '—',
  },
  { title: '仓库名称', dataIndex: 'warehouseName', render: (value?: string) => value || '—' },
  { title: '数量', dataIndex: 'quantity', width: 120 },
];

interface ErpStockModalProps {
  part: PdmPart | null;
  onClose: () => void;
}

export default function ErpStockModal({ part, onClose }: ErpStockModalProps) {
  const [data, setData] = useState<PartErpStock | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!part) {
      setData(null);
      return;
    }
    setLoading(true);
    getPartErpStock(part.id)
      .then(setData)
      .catch(() => setData(null))
      .finally(() => setLoading(false));
  }, [part]);

  return (
    <Modal
      title={part ? `ERP 库存 - ${part.code}` : 'ERP 库存'}
      open={part !== null}
      onCancel={onClose}
      footer={null}
      width={720}
      destroyOnHidden
    >
      {data?.degraded ? (
        <Alert type="warning" showIcon message={data.message || 'ERP 暂不可用，库存信息未获取'} />
      ) : null}
      <Descriptions column={2} size="small" style={{ marginTop: 12 }}>
        <Descriptions.Item label="ERP 物料编码">{data?.erpItemCode || '—'}</Descriptions.Item>
        <Descriptions.Item label="物料状态">
          {data?.itemExists ? (
            <Tag color={data.itemStatus === 'ENABLED' ? 'green' : 'default'}>
              {data.itemStatus || '存在'}
            </Tag>
          ) : (
            <Tag>未同步</Tag>
          )}
        </Descriptions.Item>
        <Descriptions.Item label="物料名称">{data?.itemName || '—'}</Descriptions.Item>
        <Descriptions.Item label="库存合计">{data ? data.totalQuantity : '—'}</Descriptions.Item>
      </Descriptions>
      <Table<PartErpWarehouse>
        rowKey={(row) => `${row.warehouseId ?? 'unknown'}-${row.warehouseCode ?? ''}`}
        size="small"
        loading={loading}
        columns={WAREHOUSE_COLUMNS}
        dataSource={data?.warehouses ?? []}
        pagination={false}
        locale={{ emptyText: '暂无库存分布' }}
        style={{ marginTop: 12 }}
      />
    </Modal>
  );
}
