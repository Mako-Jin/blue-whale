// 慰问帮扶（M-P3）：申请 → 提交审批 → 同步 → 登记发放。
//
// 提交时必须把「审批不可达」如实告诉用户：后端返回 approvalStarted=false 表示
// 数据已保存但仍是草稿，页面不能提示「已提交」；这里直接 message.warning 展示后端提示。
// 登记发放只对 APPROVED 开放，避免前端先发钱后补审批。

import {
  Button,
  Card,
  Drawer,
  Form,
  Input,
  InputNumber,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  createUnionRelief,
  pageUnionMembers,
  pageUnionReliefs,
  payUnionRelief,
  submitUnionRelief,
  syncUnionRelief,
  updateUnionRelief,
  type UnionMemberView,
  type UnionReliefSaveCommand,
  type UnionReliefView,
} from '../api/party';
import { hasPermission } from '../api/client';

/** 每页条数 */
const PAGE_SIZE = 10;

/** 状态选项与颜色 */
const STATUS_OPTIONS = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'SUBMITTED', label: '审批中' },
  { value: 'APPROVED', label: '已通过' },
  { value: 'REJECTED', label: '已退回' },
  { value: 'PAID', label: '已发放' },
];

/** 状态颜色 */
const STATUS_COLOR: Record<string, string> = {
  DRAFT: 'default',
  SUBMITTED: 'orange',
  APPROVED: 'green',
  REJECTED: 'red',
  PAID: 'blue',
};

/** 状态标签 */
const STATUS_LABEL: Record<string, string> = {
  DRAFT: '草稿',
  SUBMITTED: '审批中',
  APPROVED: '已通过',
  REJECTED: '已退回',
  PAID: '已发放',
};

/** 慰问类型选项 */
const RELIEF_TYPE_OPTIONS = [
  { value: 'DIFFICULTY', label: '困难帮扶' },
  { value: 'CONDOLENCE', label: '慰问' },
  { value: 'MEDICAL', label: '医疗' },
  { value: 'EDUCATION', label: '助学' },
  { value: 'OTHER', label: '其他' },
];

/** 慰问类型标签 */
const RELIEF_TYPE_LABEL: Record<string, string> = {
  DIFFICULTY: '困难帮扶',
  CONDOLENCE: '慰问',
  MEDICAL: '医疗',
  EDUCATION: '助学',
  OTHER: '其他',
};

/**
 * 格式化时间。
 *
 * @param value 时间文本
 * @returns 展示用时间
 */
function formatTime(value?: string | null): string {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '—';
}

/** 慰问帮扶页 */
export default function UnionReliefPage() {
  const [rows, setRows] = useState<UnionReliefView[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<string | undefined>();
  const [reliefType, setReliefType] = useState<string | undefined>();
  const [year, setYear] = useState<number | undefined>();
  const [loading, setLoading] = useState(false);

  const [members, setMembers] = useState<UnionMemberView[]>([]);
  const [editing, setEditing] = useState<UnionReliefView | null>(null);
  const [open, setOpen] = useState(false);
  const [form] = Form.useForm<UnionReliefSaveCommand>();

  const canManage = hasPermission('sys:union:relief:manage');

  const memberLabel = useMemo(() => {
    const map = new Map<number, string>();
    members.forEach((item) => map.set(item.id, `${item.memberName}（${item.memberNo}）`));
    return (id: number) => map.get(id) ?? `#${id}`;
  }, [members]);

  const memberOptions = useMemo(
    () =>
      members.map((item) => ({ value: item.id, label: `${item.memberName}（${item.memberNo}）` })),
    [members],
  );

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageUnionReliefs({
        keyword: keyword || undefined,
        status,
        reliefType,
        year,
        current,
        size: PAGE_SIZE,
      });
      setRows(page.records);
      setTotal(Number(page.total));
    } catch (err) {
      message.error(err instanceof Error ? err.message : '慰问申请加载失败');
    } finally {
      setLoading(false);
    }
  }, [current, keyword, reliefType, status, year]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    pageUnionMembers({ status: 'ACTIVE', current: 1, size: 200 })
      .then((page) => setMembers(page.records))
      .catch(() => setMembers([]));
  }, []);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ reliefType: 'DIFFICULTY' });
    setOpen(true);
  };

  const openEdit = (row: UnionReliefView) => {
    setEditing(row);
    form.setFieldsValue({
      unionMemberId: row.unionMemberId,
      reliefType: row.reliefType,
      amount: row.amount,
      reason: row.reason,
      remark: row.remark ?? undefined,
    });
    setOpen(true);
  };

  const submitEditor = async () => {
    const values = await form.validateFields();
    const payload: UnionReliefSaveCommand = {
      unionMemberId: values.unionMemberId,
      reliefType: values.reliefType,
      amount: values.amount,
      reason: values.reason,
      remark: values.remark ?? null,
    };
    try {
      if (editing) {
        await updateUnionRelief(editing.id, payload);
        message.success('申请已更新');
      } else {
        await createUnionRelief(payload);
        message.success('申请草稿已创建');
      }
      setOpen(false);
      await load();
    } catch (err) {
      message.error(err instanceof Error ? err.message : '保存失败');
    }
  };

  const submitApproval = async (row: UnionReliefView) => {
    try {
      const result = await submitUnionRelief(row.id);
      if (result.approvalStarted) {
        message.success('已提交审批');
      } else {
        message.warning(result.approvalHint || '审批服务不可用，已保存为草稿');
      }
      await load();
    } catch (err) {
      message.error(err instanceof Error ? err.message : '提交失败');
    }
  };

  const sync = async (row: UnionReliefView) => {
    try {
      const view = await syncUnionRelief(row.id);
      message.success(`审批状态已同步：${STATUS_LABEL[view.status] ?? view.status}`);
      await load();
    } catch (err) {
      message.error(err instanceof Error ? err.message : '同步失败');
    }
  };

  const pay = async (row: UnionReliefView) => {
    try {
      await payUnionRelief(row.id);
      message.success('已登记发放');
      await load();
    } catch (err) {
      message.error(err instanceof Error ? err.message : '登记发放失败');
    }
  };

  const columns: ColumnsType<UnionReliefView> = [
    { title: '申请编号', dataIndex: 'reliefNo', width: 130 },
    {
      title: '会员',
      dataIndex: 'unionMemberId',
      width: 150,
      render: (value: number) => memberLabel(value),
    },
    {
      title: '类型',
      dataIndex: 'reliefType',
      width: 100,
      render: (value: string) => RELIEF_TYPE_LABEL[value] ?? value,
    },
    { title: '年度', dataIndex: 'applyYear', width: 80 },
    {
      title: '金额',
      dataIndex: 'amount',
      width: 110,
      render: (value: number) => `¥${Number(value ?? 0).toFixed(2)}`,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (value: string) => (
        <Tag color={STATUS_COLOR[value] ?? 'default'}>{STATUS_LABEL[value] ?? value}</Tag>
      ),
    },
    { title: '事由', dataIndex: 'reason', ellipsis: true },
    {
      title: '提交时间',
      dataIndex: 'submitTime',
      width: 160,
      render: (value?: string | null) => formatTime(value),
    },
    {
      title: '审批提示',
      dataIndex: 'approvalHint',
      width: 160,
      ellipsis: true,
      render: (value: string | null | undefined) => value ?? '—',
    },
    {
      title: '操作',
      key: 'actions',
      width: 170,
      render: (_value, row) =>
        canManage ? (
          <Space size={4} wrap>
            {row.status === 'DRAFT' ? (
              <>
                <Button type="link" size="small" onClick={() => openEdit(row)}>
                  编辑
                </Button>
                <Button type="link" size="small" onClick={() => void submitApproval(row)}>
                  提交
                </Button>
              </>
            ) : null}
            {row.status === 'SUBMITTED' ? (
              <Button type="link" size="small" onClick={() => void sync(row)}>
                同步审批
              </Button>
            ) : null}
            {row.status === 'APPROVED' ? (
              <Popconfirm title="确认已向会员发放该笔慰问金？" onConfirm={() => void pay(row)}>
                <Button type="link" size="small">
                  登记发放
                </Button>
              </Popconfirm>
            ) : null}
          </Space>
        ) : (
          '—'
        ),
    },
  ];

  return (
    <Card
      title="慰问帮扶"
      size="small"
      extra={
        <Space wrap>
          <Input.Search
            allowClear
            placeholder="申请编号 / 事由关键字"
            style={{ width: 200 }}
            onSearch={(value) => {
              setCurrent(1);
              setKeyword(value.trim());
            }}
          />
          <Select
            allowClear
            placeholder="全部状态"
            style={{ width: 110 }}
            options={STATUS_OPTIONS}
            value={status}
            onChange={(value) => {
              setCurrent(1);
              setStatus(value);
            }}
          />
          <Select
            allowClear
            placeholder="全部类型"
            style={{ width: 120 }}
            options={RELIEF_TYPE_OPTIONS}
            value={reliefType}
            onChange={(value) => {
              setCurrent(1);
              setReliefType(value);
            }}
          />
          <InputNumber
            placeholder="年度"
            min={2000}
            max={2100}
            style={{ width: 100 }}
            value={year ?? null}
            onChange={(value) => {
              setCurrent(1);
              setYear(typeof value === 'number' ? value : undefined);
            }}
          />
          {canManage ? (
            <Button type="primary" onClick={openCreate}>
              新建申请
            </Button>
          ) : null}
        </Space>
      }
    >
      <Table
        rowKey="id"
        size="small"
        loading={loading}
        columns={columns}
        dataSource={rows}
        scroll={{ x: 1400 }}
        pagination={{
          current,
          pageSize: PAGE_SIZE,
          total,
          showSizeChanger: false,
          onChange: setCurrent,
        }}
      />

      <Drawer
        title={editing ? '编辑慰问申请' : '新建慰问申请'}
        width={560}
        open={open}
        onClose={() => setOpen(false)}
        extra={
          <Space>
            <Button onClick={() => setOpen(false)}>取消</Button>
            <Button type="primary" onClick={() => void submitEditor()}>
              保存草稿
            </Button>
          </Space>
        }
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item
            name="unionMemberId"
            label="会员"
            rules={[{ required: true, message: '请选择会员' }]}
          >
            <Select
              showSearch
              optionFilterProp="label"
              options={memberOptions}
              placeholder="按姓名搜索在册会员"
              notFoundContent="暂无在册会员"
            />
          </Form.Item>
          <Form.Item
            name="reliefType"
            label="慰问类型"
            rules={[{ required: true, message: '请选择类型' }]}
          >
            <Select options={RELIEF_TYPE_OPTIONS} />
          </Form.Item>
          <Form.Item name="amount" label="金额" rules={[{ required: true, message: '请输入金额' }]}>
            <InputNumber min={0.01} precision={2} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item
            name="reason"
            label="事由"
            rules={[{ required: true, message: '请填写申请事由' }]}
          >
            <Input.TextArea rows={4} maxLength={500} showCount />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input maxLength={255} />
          </Form.Item>
          <Tooltip title="草稿保存后需在列表中点「提交」才会发起审批">
            <span>保存后状态为草稿</span>
          </Tooltip>
        </Form>
      </Drawer>
    </Card>
  );
}
