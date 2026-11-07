// 商品详情：主信息 + 规格（价目与库存）一览
import { useCallback, useEffect, useState } from 'react';
import { Button, Card, Descriptions, Space, Spin, Table, Tag, Typography, message } from 'antd';
import type { TableProps } from 'antd';
import { useNavigate, useParams } from 'react-router-dom';
import { getProduct, type SkuView, type SpuDetailView } from '../../api/mall';
import {
  ENABLE_STATUS_COLOR,
  ENABLE_STATUS_LABEL,
  PRICE_TYPE_LABEL,
  SPU_STATUS_COLOR,
  SPU_STATUS_LABEL,
  formatDateTime,
  formatMoney,
  formatSpec,
} from '../../utils/dict';

/**
 * 读取错误文案。
 *
 * @param error 异常对象
 * @returns 文案
 */
function errorText(error: unknown): string {
  return error instanceof Error ? error.message : '加载失败';
}

export default function ProductDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [detail, setDetail] = useState<SpuDetailView | null>(null);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    if (!id) {
      return;
    }
    setLoading(true);
    try {
      setDetail(await getProduct(Number(id)));
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    void load();
  }, [load]);

  const columns: TableProps<SkuView>['columns'] = [
    { title: 'SKU 编码', dataIndex: 'skuCode', width: 180 },
    {
      title: '规格',
      dataIndex: 'specJson',
      render: (value?: string) => formatSpec(value),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (value: string) => (
        <Tag color={ENABLE_STATUS_COLOR[value]}>{ENABLE_STATUS_LABEL[value] ?? value}</Tag>
      ),
    },
    {
      title: '价目',
      dataIndex: 'prices',
      width: 240,
      render: (_, row) =>
        row.prices && row.prices.length > 0 ? (
          <Space direction="vertical" size={0}>
            {row.prices.map((price) => (
              <Typography.Text key={price.priceType} type="secondary">
                {PRICE_TYPE_LABEL[price.priceType] ?? price.priceType}：
                {formatMoney(price.price, price.priceType === 'POINTS' ? 'POINTS' : 'CASH')}
              </Typography.Text>
            ))}
          </Space>
        ) : (
          <Typography.Text type="danger">未配置价目</Typography.Text>
        ),
    },
    {
      title: '可售',
      dataIndex: 'availableQty',
      width: 90,
      render: (value?: number) => (value === undefined ? '-' : value),
    },
    {
      title: '已卖未发',
      dataIndex: 'reservedQty',
      width: 100,
      render: (value?: number) => (value === undefined ? '-' : value),
    },
  ];

  return (
    <Card
      title="商品详情"
      extra={
        <Space>
          <Button onClick={() => navigate('/product')}>返回列表</Button>
          <Button type="primary" onClick={() => void load()}>
            刷新
          </Button>
        </Space>
      }
    >
      <Spin spinning={loading}>
        {detail ? (
          <>
            <Descriptions column={3} size="small" bordered>
              <Descriptions.Item label="商品编码">{detail.spuCode}</Descriptions.Item>
              <Descriptions.Item label="商品名称">{detail.spuName}</Descriptions.Item>
              <Descriptions.Item label="状态">
                <Tag color={SPU_STATUS_COLOR[detail.status]}>
                  {SPU_STATUS_LABEL[detail.status] ?? detail.status}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="类目">{detail.categoryName || detail.categoryId}</Descriptions.Item>
              <Descriptions.Item label="品牌">{detail.brand || '-'}</Descriptions.Item>
              <Descriptions.Item label="更新时间">{formatDateTime(detail.updateTime)}</Descriptions.Item>
              <Descriptions.Item label="主图" span={3}>
                {detail.mainImage ? (
                  <Typography.Link href={detail.mainImage} target="_blank">
                    {detail.mainImage}
                  </Typography.Link>
                ) : (
                  '-'
                )}
              </Descriptions.Item>
              <Descriptions.Item label="详情" span={3}>
                <Typography.Paragraph style={{ marginBottom: 0, whiteSpace: 'pre-wrap' }}>
                  {detail.detail || '-'}
                </Typography.Paragraph>
              </Descriptions.Item>
            </Descriptions>

            <Typography.Title level={5} style={{ marginTop: 20 }}>
              规格与库存
            </Typography.Title>
            <Table<SkuView>
              rowKey="id"
              size="small"
              columns={columns}
              dataSource={detail.skus ?? []}
              pagination={false}
            />
          </>
        ) : (
          <Typography.Text type="secondary">暂无数据</Typography.Text>
        )}
      </Spin>
    </Card>
  );
}
