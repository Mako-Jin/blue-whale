// 商城库存：按 SKU 维度的可售 / 已卖未发 / 总量，增量调整
//
// 库存是「增量调整」而不是「直接覆写」：覆写式改库存会在并发抢购时把已卖未发的量放回去，
// 页面因此只让用户填 delta（正数入库、负数出库），提交后回显后端给出的最新可售量。
import { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Card,
  Form,
  Input,
  InputNumber,
  Modal,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import type { TableProps } from 'antd';
import { adjustStock, pageStocks, type StockView } from '../../api/mall';
import { hasPermission } from '../../utils/permission';
import { SPU_STATUS_COLOR, SPU_STATUS_LABEL, formatDateTime, formatSpec } from '../../utils/dict';

/** 调整表单值 */
interface AdjustFormValues {
  delta: number;
  remark?: string;
}

/**
 * 读取错误文案。
 *
 * @param error 异常对象
 * @returns 文案
 */
function errorText(error: unknown): string {
  return error instanceof Error ? error.message : '操作失败';
}

export default function StockPage() {
  const [rows, setRows] = useState<StockView[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [page, setPage] = useState({ current: 1, size: 10 });
  const [target, setTarget] = useState<StockView | null>(null);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm<AdjustFormValues>();

  const canManage = hasPermission('sys:mall:stock:manage');

  /** 分页查询 */
  const load = useCallback(async () => {
    setLoading(true);
    try {
      const result = await pageStocks({
        keyword: keyword.trim() || undefined,
        current: page.current,
        size: page.size,
      });
      setRows(result.records ?? []);
      setTotal(result.total ?? 0);
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setLoading(false);
    }
  }, [keyword, page]);

  useEffect(() => {
    void load();
  }, [load]);

  /** 打开调整弹窗 */
  const openAdjust = (row: StockView) => {
    setTarget(row);
    form.setFieldsValue({ delta: 0, remark: '' });
  };

  /** 提交调整 */
  const submit = async () => {
    if (!target) {
      return;
    }
    const values = await form.validateFields();
    setSaving(true);
    try {
      const available = await adjustStock(target.skuId, values.delta, values.remark);
      message.success(`调整成功，当前可售 ${available}`);
      setTarget(null);
      await load();
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setSaving(false);
    }
  };

  const columns: TableProps<StockView>['columns'] = [
    { title: 'SKU 编码', dataIndex: 'skuCode', width: 180 },
    { title: '商品', dataIndex: 'spuName' },
    {
      title: '规格',
      dataIndex: 'specJson',
      width: 180,
      render: (value?: string) => formatSpec(value),
    },
    {
      title: '商品状态',
      dataIndex: 'spuStatus',
      width: 110,
      render: (value?: string) =>
        value ? (
          <Tag color={SPU_STATUS_COLOR[value]}>{SPU_STATUS_LABEL[value] ?? value}</Tag>
        ) : (
          '-'
        ),
    },
    {
      title: '可售',
      dataIndex: 'availableQty',
      width: 100,
      render: (value: number) => (
        <Typography.Text type={value > 0 ? undefined : 'danger'}>{value}</Typography.Text>
      ),
    },
    { title: '已卖未发', dataIndex: 'reservedQty', width: 110 },
    { title: '总库存', dataIndex: 'totalQty', width: 100 },
    {
      title: '更新时间',
      dataIndex: 'updateTime',
      width: 170,
      render: (value?: string) => formatDateTime(value),
    },
    {
      title: '操作',
      width: 100,
      render: (_, row) =>
        canManage ? (
          <Button type="link" size="small" onClick={() => openAdjust(row)}>
            调整
          </Button>
        ) : null,
    },
  ];

  return (
    <Card
      title="商城库存"
      extra={
        <Space>
          <Input.Search
            allowClear
            placeholder="SKU 编码 / 商品名称"
            style={{ width: 240 }}
            onSearch={(value) => {
              setKeyword(value);
              setPage((prev) => ({ ...prev, current: 1 }));
            }}
          />
          <Button onClick={() => void load()}>刷新</Button>
        </Space>
      }
    >
      <Table<StockView>
        rowKey="skuId"
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
        title={target ? `调整库存 · ${target.skuCode}` : '调整库存'}
        open={target !== null}
        confirmLoading={saving}
        onCancel={() => setTarget(null)}
        onOk={submit}
        destroyOnClose
      >
        <Typography.Paragraph type="secondary">
          当前可售 {target?.availableQty ?? 0}，已卖未发 {target?.reservedQty ?? 0}。
          填写增量：正数入库、负数出库；出库不能超过可售量。
        </Typography.Paragraph>
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item
            label="增量"
            name="delta"
            rules={[{ required: true, message: '请填写增量' }]}
          >
            <InputNumber style={{ width: '100%' }} precision={0} />
          </Form.Item>
          <Form.Item label="备注" name="remark">
            <Input.TextArea rows={2} maxLength={255} placeholder="如：补货入库 / 盘亏" />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
}
