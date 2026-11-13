// 党费台账（M-P1）：标准档位 + 月度账单 + 生成 + 缴费登记 + 免缴。
//
// 「生成账单」与「登记缴费」分开：生成是批量、可重跑的月度动作，缴费是逐笔、留缴款日期的动作。
// 合成一个入口会让「补缴上月」和「生成本月」抢同一个按钮，出错时也分不清是规则问题还是操作问题。

import {
  Button,
  Card,
  DatePicker,
  Descriptions,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs, { type Dayjs } from 'dayjs';
import { useCallback, useEffect, useState } from 'react';
import {
  generateFeeBills,
  initFeeRules,
  listFeeRules,
  pageFeeBill,
  payFeeBill,
  waiveFeeBill,
  type FeeBillView,
  type FeeGenerateResult,
  type FeeRuleView,
} from '../api/party';

/** 每页条数 */
const PAGE_SIZE = 10;

/** 缴纳表单 */
interface PayFormValues {
  amount: number;
  paidDate?: Dayjs;
  remark?: string;
}

/** 党费台账页 */
export default function FeePage() {
  const [rules, setRules] = useState<FeeRuleView[]>([]);
  const [rows, setRows] = useState<FeeBillView[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [period, setPeriod] = useState<string>(dayjs().format('YYYYMM'));
  const [outstanding, setOutstanding] = useState<boolean>(false);
  const [loading, setLoading] = useState(false);
  const [lastResult, setLastResult] = useState<FeeGenerateResult | null>(null);

  const [payTarget, setPayTarget] = useState<FeeBillView | null>(null);
  const [payOpen, setPayOpen] = useState(false);
  const [payForm] = Form.useForm<PayFormValues>();

  const loadRules = useCallback(async () => {
    try {
      setRules(await listFeeRules());
    } catch (err) {
      message.error(err instanceof Error ? err.message : '党费标准加载失败');
    }
  }, []);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageFeeBill({
        periodMonth: period || undefined,
        outstanding: outstanding || undefined,
        current,
        size: PAGE_SIZE,
      });
      setRows(page.records);
      setTotal(Number(page.total));
    } catch (err) {
      message.error(err instanceof Error ? err.message : '账单加载失败');
    } finally {
      setLoading(false);
    }
  }, [current, outstanding, period]);

  useEffect(() => {
    void loadRules();
  }, [loadRules]);

  useEffect(() => {
    void load();
  }, [load]);

  const initRules = async () => {
    try {
      setRules(await initFeeRules());
      message.success('标准档位已就绪');
    } catch (err) {
      message.error(err instanceof Error ? err.message : '初始化失败');
    }
  };

  const generate = async () => {
    try {
      const result = await generateFeeBills(period);
      setLastResult(result);
      message.success(`已生成 ${result.created} 条账单`);
      await load();
    } catch (err) {
      message.error(err instanceof Error ? err.message : '生成失败，请先配置党费标准');
    }
  };

  const openPay = (row: FeeBillView) => {
    setPayTarget(row);
    payForm.setFieldsValue({ amount: row.outstandingAmount, paidDate: dayjs() });
    setPayOpen(true);
  };

  const submitPay = async () => {
    if (!payTarget) {
      return;
    }
    const values = await payForm.validateFields();
    try {
      await payFeeBill(
        payTarget.id,
        values.amount,
        values.paidDate ? values.paidDate.format('YYYY-MM-DD') : undefined,
        values.remark,
      );
      message.success('缴费已登记');
      setPayOpen(false);
      await load();
    } catch (err) {
      message.error(err instanceof Error ? err.message : '登记失败');
    }
  };

  const waive = async (row: FeeBillView) => {
    try {
      await waiveFeeBill(row.id, '困难党员，经支部研究同意免缴');
      message.success('已标记免缴');
      await load();
    } catch (err) {
      message.error(err instanceof Error ? err.message : '免缴失败');
    }
  };

  const billColumns: ColumnsType<FeeBillView> = [
    { title: '姓名', dataIndex: 'memberName', width: 100 },
    { title: '月份', dataIndex: 'periodMonth', width: 90 },
    { title: '基数', dataIndex: 'duesBase', width: 100 },
    {
      title: '应缴',
      dataIndex: 'dueAmount',
      width: 90,
      render: (value: number) => `¥${Number(value).toFixed(2)}`,
    },
    {
      title: '实缴',
      dataIndex: 'paidAmount',
      width: 90,
      render: (value: number) => `¥${Number(value).toFixed(2)}`,
    },
    {
      title: '欠缴',
      dataIndex: 'outstandingAmount',
      width: 90,
      render: (value: number) =>
        Number(value) > 0 ? (
          <span style={{ color: '#cf1322' }}>¥{Number(value).toFixed(2)}</span>
        ) : (
          '—'
        ),
    },
    {
      title: '状态',
      dataIndex: 'statusLabel',
      width: 100,
      render: (_value, row) => (
        <Tag
          color={row.status === 'PAID' ? 'green' : row.status === 'WAIVED' ? 'default' : 'orange'}
        >
          {row.statusLabel}
        </Tag>
      ),
    },
    { title: '缴费日期', dataIndex: 'paidDate', width: 110, render: (value) => value ?? '—' },
    {
      title: '操作',
      key: 'actions',
      width: 150,
      render: (_value, row) =>
        row.status === 'UNPAID' || row.status === 'PARTIAL' ? (
          <Space size={4}>
            <Button type="link" size="small" onClick={() => openPay(row)}>
              登记缴费
            </Button>
            <Popconfirm title="标记为免缴？" onConfirm={() => void waive(row)}>
              <Button type="link" size="small" danger>
                免缴
              </Button>
            </Popconfirm>
          </Space>
        ) : (
          '—'
        ),
    },
  ];

  const ruleColumns: ColumnsType<FeeRuleView> = [
    { title: '档位', dataIndex: 'ruleName' },
    {
      title: '基数区间',
      key: 'range',
      render: (_value, row) =>
        `${Number(row.minBase).toFixed(2)} ~ ${
          row.maxBase === null || row.maxBase === undefined
            ? '不限'
            : Number(row.maxBase).toFixed(2)
        }`,
    },
    { title: '比例', dataIndex: 'ratePercent', width: 90 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (value: string) => (
        <Tag color={value === 'ACTIVE' ? 'green' : 'default'}>{value}</Tag>
      ),
    },
  ];

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Card
        title="党费标准"
        size="small"
        extra={
          rules.length === 0 ? (
            <Button type="primary" size="small" onClick={() => void initRules()}>
              初始化标准档位
            </Button>
          ) : null
        }
      >
        <Table
          rowKey="id"
          size="small"
          pagination={false}
          columns={ruleColumns}
          dataSource={rules}
          locale={{ emptyText: '尚未配置档位：生成账单前需要先初始化或新建档位' }}
        />
      </Card>

      <Card
        title="月度账单"
        size="small"
        extra={
          <Space>
            <DatePicker
              picker="month"
              allowClear={false}
              value={dayjs(period, 'YYYYMM')}
              onChange={(value) => {
                setCurrent(1);
                setPeriod(value.format('YYYYMM'));
              }}
            />
            <Select
              value={outstanding ? 'outstanding' : 'all'}
              style={{ width: 120 }}
              options={[
                { value: 'all', label: '全部账单' },
                { value: 'outstanding', label: '仅欠缴' },
              ]}
              onChange={(value) => {
                setCurrent(1);
                setOutstanding(value === 'outstanding');
              }}
            />
            <Button type="primary" onClick={() => void generate()}>
              生成本月账单
            </Button>
          </Space>
        }
      >
        {lastResult ? (
          <Descriptions size="small" column={4} style={{ marginBottom: 12 }}>
            <Descriptions.Item label="月份">{lastResult.periodMonth}</Descriptions.Item>
            <Descriptions.Item label="新建">{lastResult.created}</Descriptions.Item>
            <Descriptions.Item label="跳过">{lastResult.skipped}</Descriptions.Item>
            <Descriptions.Item label="未匹配档位">
              {lastResult.noRule > 0
                ? `${lastResult.noRule} 人（${lastResult.noRuleMembers.join('、')}）`
                : '0'}
            </Descriptions.Item>
          </Descriptions>
        ) : null}
        <Table
          rowKey="id"
          size="small"
          loading={loading}
          columns={billColumns}
          dataSource={rows}
          scroll={{ x: 900 }}
          pagination={{
            current,
            pageSize: PAGE_SIZE,
            total,
            showSizeChanger: false,
            onChange: setCurrent,
          }}
        />
      </Card>

      <Modal
        title={`登记缴费 · ${payTarget?.memberName ?? ''}`}
        open={payOpen}
        onCancel={() => setPayOpen(false)}
        onOk={() => void submitPay()}
        destroyOnClose
      >
        <Form form={payForm} layout="vertical" preserve={false}>
          <Form.Item
            name="amount"
            label="本次缴纳金额"
            rules={[{ required: true, message: '请输入金额' }]}
          >
            <InputNumber min={0.01} precision={2} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="paidDate" label="缴费日期">
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="remark" label="备注（补缴说明）">
            <Input maxLength={255} />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}
