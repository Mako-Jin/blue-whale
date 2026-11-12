// 售后单：买家看自己的，运营看全量并可审核
import { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Card,
  Descriptions,
  Image,
  Input,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import type { TableProps } from 'antd';
import {
  approveAfterSale,
  cancelAfterSale,
  getAfterSale,
  pageAfterSaleAdmin,
  pageMyAfterSale,
  rejectAfterSale,
  type AfterSaleLineView,
  type AfterSaleView,
} from '../../api/mall';
import { hasPermission } from '../../utils/permission';
import {
  AFTER_SALE_STATUS_COLOR,
  AFTER_SALE_STATUS_LABEL,
  AFTER_SALE_STATUS_OPTIONS,
  AFTER_SALE_TYPE_LABEL,
  formatDateTime,
  formatMoney,
} from '../../utils/dict';

/**
 * 读取错误文案。
 *
 * @param error 异常对象
 * @returns 文案
 */
function errorText(error: unknown): string {
  return error instanceof Error ? error.message : '操作失败';
}

export default function AfterSalePage() {
  const [rows, setRows] = useState<AfterSaleView[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(10);
  const [status, setStatus] = useState<string | undefined>();
  const [keyword, setKeyword] = useState('');
  const [loading, setLoading] = useState(false);
  const [detail, setDetail] = useState<AfterSaleView | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [auditRemark, setAuditRemark] = useState('');

  const canList = hasPermission('sys:mall:aftersale:list');
  const canAudit = hasPermission('sys:mall:aftersale:audit');

  /** 加载列表：有运营权限走全量，否则只看自己 */
  const load = useCallback(async () => {
    setLoading(true);
    try {
      const query = { status, keyword: keyword.trim() || undefined, current: page, size };
      const result = canList ? await pageAfterSaleAdmin(query) : await pageMyAfterSale(query);
      setRows(result.records ?? []);
      setTotal(Number(result.total ?? 0));
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setLoading(false);
    }
  }, [canList, keyword, page, size, status]);

  useEffect(() => {
    void load();
  }, [load]);

  /** 打开详情 */
  const openDetail = async (id: number) => {
    try {
      setDetail(await getAfterSale(id));
      setAuditRemark('');
      setDetailOpen(true);
    } catch (error) {
      message.error(errorText(error));
    }
  };

  /** 审核：通过会立即发起退款 */
  const audit = async (action: 'approve' | 'reject') => {
    if (!detail) {
      return;
    }
    setBusy(true);
    try {
      if (action === 'approve') {
        await approveAfterSale(detail.id, auditRemark.trim() || undefined);
        message.success('已审核通过并发起退款，等待支付服务回调确认');
      } else {
        await rejectAfterSale(detail.id, auditRemark.trim() || undefined);
        message.success('已驳回，占用的可退额度已释放');
      }
      setDetailOpen(false);
      await load();
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setBusy(false);
    }
  };

  /** 撤销售后（仅待审核） */
  const cancel = async (id: number) => {
    setBusy(true);
    try {
      await cancelAfterSale(id);
      message.success('已撤销，占用的可退额度已释放');
      setDetailOpen(false);
      await load();
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setBusy(false);
    }
  };

  const columns: TableProps<AfterSaleView>['columns'] = [
    {
      title: '售后单号',
      dataIndex: 'afterSaleNo',
      width: 200,
      render: (value: string, row) => <a onClick={() => void openDetail(row.id)}>{value}</a>,
    },
    { title: '订单号', dataIndex: 'orderNo', width: 200 },
    {
      title: '类型',
      dataIndex: 'type',
      width: 110,
      render: (value: string) => AFTER_SALE_TYPE_LABEL[value] ?? value,
    },
    {
      title: '退款金额',
      dataIndex: 'refundAmount',
      width: 120,
      render: (value: number) => formatMoney(value),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 110,
      render: (value: string) => (
        <Tag color={AFTER_SALE_STATUS_COLOR[value]}>{AFTER_SALE_STATUS_LABEL[value] ?? value}</Tag>
      ),
    },
    { title: '原因', dataIndex: 'reason', width: 140 },
    {
      title: '申请时间',
      dataIndex: 'createTime',
      width: 170,
      render: (value?: string) => formatDateTime(value),
    },
    {
      title: '审核人',
      dataIndex: 'auditUserName',
      width: 120,
      render: (value?: string) => value || '-',
    },
    {
      title: '操作',
      width: 150,
      render: (_: unknown, row) => (
        <Space>
          <a onClick={() => void openDetail(row.id)}>详情</a>
          {canAudit && row.status === 'CREATED' && (
            <a onClick={() => void openDetail(row.id)}>审核</a>
          )}
        </Space>
      ),
    },
  ];

  const lineColumns: TableProps<AfterSaleLineView>['columns'] = [
    { title: '商品', dataIndex: 'spuName' },
    { title: 'SKU', dataIndex: 'skuCode', width: 160 },
    { title: '单价', dataIndex: 'unitPrice', width: 110, render: (value: number) => formatMoney(value) },
    { title: '本次退数量', dataIndex: 'quantity', width: 110 },
    { title: '退金额', dataIndex: 'amount', width: 110, render: (value: number) => formatMoney(value) },
  ];

  return (
    <Card
      title="售后单"
      extra={
        <Space>
          <Select
            allowClear
            placeholder="状态"
            style={{ width: 140 }}
            value={status}
            options={AFTER_SALE_STATUS_OPTIONS}
            onChange={(value) => {
              setStatus(value);
              setPage(1);
            }}
          />
          <Input.Search
            allowClear
            placeholder="售后单号 / 订单号"
            style={{ width: 220 }}
            onSearch={(value) => {
              setKeyword(value);
              setPage(1);
            }}
          />
          <Button onClick={() => void load()}>刷新</Button>
        </Space>
      }
    >
      <Table<AfterSaleView>
        rowKey="id"
        size="small"
        loading={loading}
        columns={columns}
        dataSource={rows}
        scroll={{ x: 1400 }}
        pagination={{
          current: page,
          pageSize: size,
          total,
          showSizeChanger: true,
          onChange: (nextPage, nextSize) => {
            setPage(nextPage);
            setSize(nextSize);
          },
        }}
      />

      <Modal
        title={`售后详情${detail ? ` · ${detail.afterSaleNo}` : ''}`}
        open={detailOpen}
        width={720}
        confirmLoading={busy}
        onCancel={() => setDetailOpen(false)}
        footer={
          detail ? (
            <Space>
              <Button onClick={() => setDetailOpen(false)}>关闭</Button>
              {detail.status === 'CREATED' && (
                <Button danger onClick={() => void cancel(detail.id)}>
                  撤销
                </Button>
              )}
              {canAudit && detail.status === 'CREATED' && (
                <>
                  <Button danger onClick={() => void audit('reject')}>
                    驳回
                  </Button>
                  <Button type="primary" onClick={() => void audit('approve')}>
                    通过并发起退款
                  </Button>
                </>
              )}
            </Space>
          ) : null
        }
      >
        {detail ? (
          <>
            <Descriptions column={2} size="small" bordered>
              <Descriptions.Item label="售后单号">{detail.afterSaleNo}</Descriptions.Item>
              <Descriptions.Item label="订单号">{detail.orderNo}</Descriptions.Item>
              <Descriptions.Item label="类型">
                {AFTER_SALE_TYPE_LABEL[detail.type] ?? detail.type}
              </Descriptions.Item>
              <Descriptions.Item label="状态">
                <Tag color={AFTER_SALE_STATUS_COLOR[detail.status]}>
                  {AFTER_SALE_STATUS_LABEL[detail.status] ?? detail.status}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="退款金额">
                <Typography.Text strong>{formatMoney(detail.refundAmount)}</Typography.Text>
              </Descriptions.Item>
              <Descriptions.Item label="申请时间">{formatDateTime(detail.createTime)}</Descriptions.Item>
              <Descriptions.Item label="原因">{detail.reason || '-'}</Descriptions.Item>
              <Descriptions.Item label="审核人">{detail.auditUserName || '-'}</Descriptions.Item>
              <Descriptions.Item label="审核时间">{formatDateTime(detail.auditTime)}</Descriptions.Item>
              <Descriptions.Item label="退款完成">{formatDateTime(detail.refundTime)}</Descriptions.Item>
              <Descriptions.Item label="问题描述" span={2}>
                {detail.description || '-'}
              </Descriptions.Item>
              {(detail.images ?? '').trim() !== '' && (
                <Descriptions.Item label="凭证图片" span={2}>
                  <Image.PreviewGroup>
                    <Space wrap>
                      {(detail.images ?? '')
                        .split(',')
                        .map((url) => url.trim())
                        .filter(Boolean)
                        .map((url) => (
                          <Image key={url} src={url} width={64} height={64} style={{ objectFit: 'cover' }} />
                        ))}
                    </Space>
                  </Image.PreviewGroup>
                </Descriptions.Item>
              )}
              <Descriptions.Item label="审核意见" span={2}>
                {detail.auditRemark || '-'}
              </Descriptions.Item>
            </Descriptions>

            <Typography.Title level={5} style={{ marginTop: 16 }}>
              退换明细
            </Typography.Title>
            <Table<AfterSaleLineView>
              rowKey="id"
              size="small"
              pagination={false}
              columns={lineColumns}
              dataSource={detail.items ?? []}
            />

            {canAudit && detail.status === 'CREATED' && (
              <Input.TextArea
                style={{ marginTop: 16 }}
                rows={2}
                maxLength={255}
                placeholder="审核意见（驳回时建议填写）"
                value={auditRemark}
                onChange={(event) => setAuditRemark(event.target.value)}
              />
            )}
          </>
        ) : (
          <Typography.Text type="secondary">暂无数据</Typography.Text>
        )}
      </Modal>
    </Card>
  );
}
