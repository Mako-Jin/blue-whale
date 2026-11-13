// ERP 会计凭证页：查询、录入、记账与红冲
import { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Space,
  Table,
  Tag,
  message,
} from 'antd';
import type { TableProps } from 'antd';
import {
  createVoucher,
  getVoucher,
  pageVouchers,
  postVoucher,
  reverseVoucher,
  type Voucher,
  type VoucherInput,
  type VoucherLine,
} from '../api/finance';
import { hasPermission } from '../utils/permission';

/** 凭证状态文案 */
const STATUS_LABEL: Record<string, string> = {
  DRAFT: '草稿',
  POSTED: '已记账',
  REVERSED: '已红冲',
};

/** 状态颜色 */
function statusColor(status: string): string {
  if (status === 'POSTED') return 'green';
  if (status === 'REVERSED') return 'orange';
  return 'default';
}

/** 默认凭证日期 */
function today(): string {
  return new Date().toISOString().slice(0, 10);
}

/** 凭证页 */
export default function VoucherPage() {
  const [data, setData] = useState<Voucher[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<string | undefined>();
  const [periodCode, setPeriodCode] = useState('');
  const [version, setVersion] = useState(0);
  const [createOpen, setCreateOpen] = useState(false);
  const [detail, setDetail] = useState<Voucher | null>(null);
  const [form] = Form.useForm<VoucherInput>();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageVouchers({ size: 50, keyword, status, periodCode });
      setData(page.records);
    } finally {
      setLoading(false);
    }
  }, [keyword, status, periodCode]);

  useEffect(() => {
    void load();
  }, [load, version]);

  useEffect(() => {
    if (createOpen) {
      form.resetFields();
      form.setFieldsValue({
        voucherDate: today(),
        currency: 'CNY',
        lines: [
          { accountCode: '', summary: '', debitAmount: undefined, creditAmount: undefined },
          { accountCode: '', summary: '', debitAmount: undefined, creditAmount: undefined },
        ],
      });
    }
  }, [createOpen, form]);

  const handleCreate = async () => {
    const values = await form.validateFields().catch(() => null);
    if (!values) return;
    await createVoucher({
      ...values,
      lines: values.lines.map((line) => ({
        accountCode: line.accountCode,
        summary: line.summary,
        debitAmount: line.debitAmount || undefined,
        creditAmount: line.creditAmount || undefined,
      })),
    });
    message.success('凭证已保存为草稿');
    setCreateOpen(false);
    setVersion((value) => value + 1);
  };

  const handlePost = async (id: number) => {
    await postVoucher(id);
    message.success('记账成功');
    setVersion((value) => value + 1);
  };

  const handleReverse = async (id: number) => {
    await reverseVoucher(id);
    message.success('红冲成功');
    setVersion((value) => value + 1);
  };

  const handleDetail = async (id: number) => {
    setDetail(await getVoucher(id));
  };

  const columns: TableProps<Voucher>['columns'] = [
    { title: '凭证号', dataIndex: 'voucherNo', key: 'voucherNo', width: 150 },
    { title: '凭证日期', dataIndex: 'voucherDate', key: 'voucherDate', width: 110 },
    { title: '期间', dataIndex: 'periodCode', key: 'periodCode', width: 90 },
    {
      title: '金额',
      dataIndex: 'totalDebit',
      key: 'totalDebit',
      width: 130,
      render: (value) => Number(value ?? 0).toFixed(2),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (value) => (
        <Tag color={statusColor(String(value))}>{STATUS_LABEL[String(value)] ?? value}</Tag>
      ),
    },
    { title: '备注', dataIndex: 'remark', key: 'remark' },
    {
      title: '操作',
      key: 'action',
      width: 220,
      render: (_, record) => (
        <Space size="small">
          <Button type="link" size="small" onClick={() => handleDetail(record.id)}>
            查看
          </Button>
          {record.status === 'DRAFT' && hasPermission('sys:erp:voucher:post') ? (
            <Button type="link" size="small" onClick={() => handlePost(record.id)}>
              记账
            </Button>
          ) : null}
          {record.status === 'POSTED' && hasPermission('sys:erp:voucher:reverse') ? (
            <Popconfirm
              title="红冲后将生成反向凭证，确认继续？"
              onConfirm={() => handleReverse(record.id)}
            >
              <Button type="link" size="small" danger>
                红冲
              </Button>
            </Popconfirm>
          ) : null}
        </Space>
      ),
    },
  ];

  const lineColumns: TableProps<VoucherLine>['columns'] = [
    { title: '行', dataIndex: 'lineNo', key: 'lineNo', width: 60 },
    { title: '科目', dataIndex: 'accountCode', key: 'accountCode', width: 110 },
    { title: '科目名称', dataIndex: 'accountName', key: 'accountName' },
    { title: '摘要', dataIndex: 'summary', key: 'summary' },
    {
      title: '借方',
      dataIndex: 'debitAmount',
      key: 'debitAmount',
      width: 120,
      render: (value) => (value ? Number(value).toFixed(2) : ''),
    },
    {
      title: '贷方',
      dataIndex: 'creditAmount',
      key: 'creditAmount',
      width: 120,
      render: (value) => (value ? Number(value).toFixed(2) : ''),
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <Input.Search
          allowClear
          placeholder="凭证号 / 备注"
          style={{ width: 220 }}
          onSearch={(value) => setKeyword(value)}
        />
        <Input
          allowClear
          placeholder="期间 yyyyMM"
          style={{ width: 140 }}
          onChange={(event) => setPeriodCode(event.target.value)}
        />
        <Button onClick={() => setStatus(undefined)} type={status ? 'default' : 'primary'}>
          全部
        </Button>
        <Button
          onClick={() => setStatus('DRAFT')}
          type={status === 'DRAFT' ? 'primary' : 'default'}
        >
          草稿
        </Button>
        <Button
          onClick={() => setStatus('POSTED')}
          type={status === 'POSTED' ? 'primary' : 'default'}
        >
          已记账
        </Button>
        {hasPermission('sys:erp:voucher:add') ? (
          <Button type="primary" onClick={() => setCreateOpen(true)}>
            录入凭证
          </Button>
        ) : null}
      </Space>
      <Table<Voucher> rowKey="id" loading={loading} columns={columns} dataSource={data} />
      <Modal
        open={createOpen}
        title="录入会计凭证"
        width={860}
        onOk={handleCreate}
        onCancel={() => setCreateOpen(false)}
        destroyOnHidden
      >
        <Form form={form} layout="vertical">
          <Space size="large">
            <Form.Item
              name="voucherDate"
              label="凭证日期"
              rules={[{ required: true, message: '请选择凭证日期' }]}
            >
              <Input type="date" style={{ width: 180 }} />
            </Form.Item>
            <Form.Item name="currency" label="币种">
              <Input style={{ width: 120 }} maxLength={8} />
            </Form.Item>
            <Form.Item name="remark" label="备注">
              <Input style={{ width: 320 }} maxLength={128} />
            </Form.Item>
          </Space>
          <Form.List name="lines">
            {(fields, { add, remove }) => (
              <Space direction="vertical" size="small" style={{ width: '100%' }}>
                {fields.map((field) => (
                  <Space key={field.key} align="baseline" wrap>
                    <Form.Item
                      name={[field.name, 'accountCode']}
                      rules={[{ required: true, message: '科目编码' }]}
                    >
                      <Input placeholder="科目编码" style={{ width: 120 }} />
                    </Form.Item>
                    <Form.Item name={[field.name, 'summary']}>
                      <Input placeholder="摘要" style={{ width: 240 }} />
                    </Form.Item>
                    <Form.Item name={[field.name, 'debitAmount']}>
                      <InputNumber
                        placeholder="借方"
                        min={0}
                        precision={2}
                        style={{ width: 130 }}
                      />
                    </Form.Item>
                    <Form.Item name={[field.name, 'creditAmount']}>
                      <InputNumber
                        placeholder="贷方"
                        min={0}
                        precision={2}
                        style={{ width: 130 }}
                      />
                    </Form.Item>
                    <Button type="link" danger onClick={() => remove(field.name)}>
                      删除
                    </Button>
                  </Space>
                ))}
                <Button type="dashed" onClick={() => add()} block>
                  增加分录
                </Button>
              </Space>
            )}
          </Form.List>
        </Form>
      </Modal>
      <Modal
        open={detail !== null}
        title={detail ? `凭证 ${detail.voucherNo}` : '凭证详情'}
        footer={null}
        width={860}
        onCancel={() => setDetail(null)}
        destroyOnHidden
      >
        {detail ? (
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <Space size="large">
              <span>日期：{detail.voucherDate}</span>
              <span>期间：{detail.periodCode}</span>
              <span>状态：{STATUS_LABEL[detail.status] ?? detail.status}</span>
              <span>借方合计：{Number(detail.totalDebit ?? 0).toFixed(2)}</span>
              <span>贷方合计：{Number(detail.totalCredit ?? 0).toFixed(2)}</span>
            </Space>
            <Table<VoucherLine>
              rowKey="lineNo"
              size="small"
              columns={lineColumns}
              dataSource={detail.lines ?? []}
              pagination={false}
            />
          </Space>
        ) : null}
      </Modal>
    </Space>
  );
}
