// 学分页：规则维护（口径）+ 台账（记账与查询）+ 年度汇总。
//
// 权限码与 migration-5.87-lms-m4a/b.sql 对齐（sys:lms:credit:manage）。
// 口径：同一业务事件只记一次，重复记账返回已有记录；没有匹配规则时后端直接报错，
// 不会静默落 0 分记录——前端因此把「先配规则再记账」写在提示里。

import {
  Button,
  Card,
  Col,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Row,
  Select,
  Space,
  Statistic,
  Table,
  Tabs,
  Tag,
  message,
} from 'antd';
import type { Dayjs } from 'dayjs';
import { useCallback, useEffect, useState } from 'react';
import { hasPermission } from '../api/client';
import {
  accrueCredit,
  changeCreditRuleStatus,
  createCreditRule,
  creditSummary,
  deleteCreditRule,
  pageCreditRules,
  pageCredits,
  updateCreditRule,
  type CreditLedgerItem,
  type CreditRuleItem,
  type CreditSource,
  type CreditSummary,
} from '../api/credit';

/** 来源展示 */
const SOURCE_LABEL: Record<CreditSource, string> = {
  COURSE: '课程完成',
  EXAM: '考试通过',
  TRAINING: '培训完成',
};

/** 状态展示 */
const STATUS_TAG: Record<string, { color: string; label: string }> = {
  ENABLED: { color: 'green', label: '启用' },
  DISABLED: { color: 'default', label: '停用' },
};

/** 规则表单值 */
interface RuleValues {
  ruleCode: string;
  name: string;
  sourceType: CreditSource;
  matchId?: number;
  credit: number;
  remark?: string;
}

/** 记账表单值 */
interface AccrueValues {
  userId: number;
  username?: string;
  sourceType: CreditSource;
  sourceId: number;
  occurredAt?: Dayjs;
  remark?: string;
}

/** 规则行 */
interface RuleRow extends CreditRuleItem {
  key: string;
}

/** 台账行 */
interface LedgerRow extends CreditLedgerItem {
  key: string;
}

/** 学分规则面板 */
function RulePanel() {
  const [form] = Form.useForm<RuleValues>();
  const [loading, setLoading] = useState(false);
  const [rows, setRows] = useState<RuleRow[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [keyword, setKeyword] = useState<string | undefined>();
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [editing, setEditing] = useState<CreditRuleItem | null>(null);
  const canManage = hasPermission('sys:lms:credit:manage');

  const load = useCallback(async (page = 1, nextKeyword = keyword) => {
    setLoading(true);
    try {
      const data = await pageCreditRules({ keyword: nextKeyword, current: page, size: 10 });
      setRows((data.records ?? []).map((item) => ({ ...item, key: String(item.id) })));
      setTotal(data.total ?? 0);
      setCurrent(page);
    } finally {
      setLoading(false);
    }
  }, [keyword]);

  useEffect(() => {
    void load(1, undefined);
    // 首次加载
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ sourceType: 'EXAM', matchId: 0, credit: 2 });
    setOpen(true);
  };

  const openEdit = (row: CreditRuleItem) => {
    setEditing(row);
    form.setFieldsValue({
      ruleCode: row.ruleCode,
      name: row.name,
      sourceType: row.sourceType,
      matchId: row.matchId,
      credit: row.credit,
      remark: row.remark,
    });
    setOpen(true);
  };

  const onSubmit = async (values: RuleValues) => {
    setSaving(true);
    try {
      if (editing) {
        await updateCreditRule(editing.id, values);
        message.success('规则已更新');
      } else {
        await createCreditRule(values);
        message.success('规则已创建');
      }
      setOpen(false);
      await load(editing ? current : 1, keyword);
    } finally {
      setSaving(false);
    }
  };

  return (
    <>
      <Space style={{ marginBottom: 12 }} wrap>
        <Input.Search
          placeholder="规则编码 / 名称"
          allowClear
          style={{ width: 240 }}
          onSearch={(value) => {
            setKeyword(value || undefined);
            void load(1, value || undefined);
          }}
        />
        {canManage && (
          <Button type="primary" onClick={openCreate}>
            新建规则
          </Button>
        )}
      </Space>
      <Table<RuleRow>
        rowKey="key"
        loading={loading}
        dataSource={rows}
        pagination={{ current, total, pageSize: 10, onChange: (page) => void load(page, keyword) }}
        columns={[
          { title: '规则编码', dataIndex: 'ruleCode', width: 150 },
          { title: '规则名称', dataIndex: 'name' },
          {
            title: '来源',
            dataIndex: 'sourceType',
            width: 110,
            render: (value: CreditSource) => SOURCE_LABEL[value] ?? value,
          },
          {
            title: '适用范围',
            dataIndex: 'matchId',
            width: 140,
            render: (value: number, row) => (row.matchAll ? '该来源全部' : `对象 #${value}`),
          },
          { title: '学分', dataIndex: 'credit', width: 90 },
          {
            title: '状态',
            dataIndex: 'status',
            width: 90,
            render: (value: string) => {
              const tag = STATUS_TAG[value] ?? { color: 'default', label: value };
              return <Tag color={tag.color}>{tag.label}</Tag>;
            },
          },
          {
            title: '操作',
            width: 180,
            render: (_, row) => (
              <Space size="small">
                {canManage && (
                  <Button size="small" type="link" onClick={() => openEdit(row)}>
                    编辑
                  </Button>
                )}
                {canManage && (
                  <Popconfirm
                    title={row.status === 'ENABLED' ? '停用后不再参与新的记账，确认？' : '确认启用？'}
                    onConfirm={async () => {
                      await changeCreditRuleStatus(row.id, row.status !== 'ENABLED');
                      message.success('状态已更新');
                      await load(current, keyword);
                    }}
                  >
                    <Button size="small" type="link">
                      {row.status === 'ENABLED' ? '停用' : '启用'}
                    </Button>
                  </Popconfirm>
                )}
                {canManage && (
                  <Popconfirm
                    title="确认删除该规则？已记的台账不受影响"
                    onConfirm={async () => {
                      await deleteCreditRule(row.id);
                      message.success('规则已删除');
                      await load(current, keyword);
                    }}
                  >
                    <Button size="small" type="link" danger>
                      删除
                    </Button>
                  </Popconfirm>
                )}
              </Space>
            ),
          },
        ]}
      />

      <Modal
        title={editing ? '编辑学分规则' : '新建学分规则'}
        open={open}
        forceRender
        confirmLoading={saving}
        onCancel={() => setOpen(false)}
        onOk={() => form.submit()}
        width={560}
      >
        <Form form={form} layout="vertical" onFinish={onSubmit}>
          <Form.Item name="ruleCode" label="规则编码" rules={[{ required: true, message: '请输入规则编码' }]}>
            <Input placeholder="如 EXAM-SAFETY（自动大写归一）" />
          </Form.Item>
          <Form.Item name="name" label="规则名称" rules={[{ required: true, message: '请输入规则名称' }]}>
            <Input placeholder="如 安全生产考试通过计 5 分" />
          </Form.Item>
          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item name="sourceType" label="学分来源" style={{ flex: 1 }} rules={[{ required: true }]}>
              <Select options={Object.entries(SOURCE_LABEL).map(([value, label]) => ({ value, label }))} />
            </Form.Item>
            <Form.Item name="credit" label="单次学分" style={{ flex: 1 }} rules={[{ required: true, message: '请输入学分' }]}>
              <InputNumber min={0.1} max={999.9} step={0.5} style={{ width: '100%' }} />
            </Form.Item>
          </Space>
          <Form.Item
            name="matchId"
            label="匹配对象 ID"
            extra="0 表示该来源下的全部对象；填具体 ID 则只对该课程/考试/培训班生效（同对象只能有一条规则）"
          >
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} maxLength={500} />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}

/** 学分台账面板 */
function LedgerPanel() {
  const [form] = Form.useForm<AccrueValues>();
  const [loading, setLoading] = useState(false);
  const [rows, setRows] = useState<LedgerRow[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [userId, setUserId] = useState<number | undefined>();
  const [periodYear, setPeriodYear] = useState<number | undefined>(new Date().getFullYear());
  const [summary, setSummary] = useState<CreditSummary | null>(null);
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const canManage = hasPermission('sys:lms:credit:manage');

  const load = useCallback(async (page = 1, nextUserId = userId, nextYear = periodYear) => {
    setLoading(true);
    try {
      const data = await pageCredits({ userId: nextUserId, periodYear: nextYear, current: page, size: 10 });
      setRows((data.records ?? []).map((item) => ({ ...item, key: String(item.id) })));
      setTotal(data.total ?? 0);
      setCurrent(page);
      const summaryData = await creditSummary({ userId: nextUserId, periodYear: nextYear });
      setSummary(summaryData);
    } finally {
      setLoading(false);
    }
  }, [userId, periodYear]);

  useEffect(() => {
    void load(1, undefined, new Date().getFullYear());
    // 首次加载
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const onAccrue = async (values: AccrueValues) => {
    setSaving(true);
    try {
      await accrueCredit({
        userId: values.userId,
        username: values.username,
        sourceType: values.sourceType,
        sourceId: values.sourceId,
        occurredAt: values.occurredAt ? values.occurredAt.format('YYYY-MM-DDTHH:mm:ss') : undefined,
        remark: values.remark,
      });
      message.success('已记账（同一事件重复记账不会重复计分）');
      setOpen(false);
      await load(1, userId, periodYear);
    } finally {
      setSaving(false);
    }
  };

  return (
    <>
      <Row gutter={12} style={{ marginBottom: 12 }}>
        <Col span={6}>
          <Card size="small">
            <Statistic
              title={userId ? `学员 #${userId} 年度学分（${periodYear ?? '全部'}）` : `我的年度学分（${periodYear ?? '全部'}）`}
              value={summary?.totalCredit ?? 0}
              precision={1}
            />
          </Card>
        </Col>
        {(summary?.items ?? []).map((item) => (
          <Col span={6} key={item.sourceType}>
            <Card size="small">
              <Statistic
                title={`${SOURCE_LABEL[item.sourceType] ?? item.sourceType}（${item.count} 笔）`}
                value={item.credit}
                precision={1}
              />
            </Card>
          </Col>
        ))}
      </Row>

      <Space style={{ marginBottom: 12 }} wrap>
        <InputNumber
          placeholder="学员用户 ID"
          min={1}
          style={{ width: 160 }}
          onChange={(value) => setUserId(value ?? undefined)}
        />
        <Select
          placeholder="年度"
          allowClear
          style={{ width: 120 }}
          value={periodYear}
          options={[0, 1, 2].map((offset) => {
            const year = new Date().getFullYear() - offset;
            return { value: year, label: `${year} 年` };
          })}
          onChange={(value) => setPeriodYear(value ?? undefined)}
        />
        <Button onClick={() => void load(1, userId, periodYear)}>查询</Button>
        {canManage && (
          <Button
            type="primary"
            onClick={() => {
              form.resetFields();
              form.setFieldsValue({ sourceType: 'EXAM' });
              setOpen(true);
            }}
          >
            记账
          </Button>
        )}
      </Space>

      <Table<LedgerRow>
        rowKey="key"
        loading={loading}
        dataSource={rows}
        pagination={{ current, total, pageSize: 10, onChange: (page) => void load(page, userId, periodYear) }}
        columns={[
          {
            title: '学员',
            dataIndex: 'username',
            width: 130,
            render: (value, row) => value ?? `#${row.userId}`,
          },
          {
            title: '来源',
            dataIndex: 'sourceType',
            width: 110,
            render: (value: CreditSource) => SOURCE_LABEL[value] ?? value,
          },
          { title: '来源对象', dataIndex: 'sourceId', width: 110, render: (value) => `#${value}` },
          { title: '命中规则', dataIndex: 'ruleCode', width: 150 },
          { title: '学分', dataIndex: 'credit', width: 90 },
          {
            title: '发生时间',
            dataIndex: 'occurredAt',
            width: 170,
            render: (value: string) => (value ? value.replace('T', ' ').slice(0, 19) : '-'),
          },
          { title: '年度', dataIndex: 'periodYear', width: 90 },
          { title: '备注', dataIndex: 'remark' },
        ]}
      />

      <Modal
        title="学分记账"
        open={open}
        forceRender
        confirmLoading={saving}
        onCancel={() => setOpen(false)}
        onOk={() => form.submit()}
        width={560}
      >
        <Form form={form} layout="vertical" onFinish={onAccrue}>
          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item name="userId" label="学员用户 ID" style={{ flex: 1 }} rules={[{ required: true, message: '请输入学员 ID' }]}>
              <InputNumber min={1} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="username" label="学员用户名" style={{ flex: 1 }}>
              <Input placeholder="用于列表展示" />
            </Form.Item>
          </Space>
          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item name="sourceType" label="学分来源" style={{ flex: 1 }} rules={[{ required: true }]}>
              <Select options={Object.entries(SOURCE_LABEL).map(([value, label]) => ({ value, label }))} />
            </Form.Item>
            <Form.Item
              name="sourceId"
              label="来源业务 ID"
              style={{ flex: 1 }}
              rules={[{ required: true, message: '请输入来源业务 ID' }]}
              extra="考试请填考试 ID（不是作答记录 ID），否则补考会重复记分"
            >
              <InputNumber min={1} style={{ width: '100%' }} />
            </Form.Item>
          </Space>
          <Form.Item name="occurredAt" label="业务发生时间" extra="跨年补记请填业务实际发生时间，年度汇总按此归集">
            <DatePicker showTime style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} maxLength={500} />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}

export default function CreditPage() {
  return (
    <div style={{ background: '#fff', padding: 16, borderRadius: 8 }}>
      <Tabs
        items={[
          { key: 'rule', label: '学分规则', children: <RulePanel /> },
          { key: 'ledger', label: '学分台账', children: <LedgerPanel /> },
        ]}
      />
    </div>
  );
}
