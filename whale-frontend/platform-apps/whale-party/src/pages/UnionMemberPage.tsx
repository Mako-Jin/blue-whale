// 工会会员台账（M-P3）：入会 / 编辑 / 退会 / 会费基数与缴至月份。
//
// 退会走独立按钮而不是编辑框：退会是终态、会写退会日期，混进通用编辑会让
// 「谁在什么时候退的会」无所追溯；退会后档案保留，再入会须新建一份会籍。
// 会费只记基数与缴至月份，逐笔流水顺延 M-P4。

import {
  Button,
  Card,
  DatePicker,
  Drawer,
  Form,
  Input,
  InputNumber,
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
  createUnionMember,
  pageUnionMembers,
  updateUnionMember,
  withdrawUnionMember,
  type UnionMemberSaveCommand,
  type UnionMemberView,
} from '../api/party';
import { hasPermission } from '../api/client';

/** 每页条数 */
const PAGE_SIZE = 10;

/** 会员状态选项 */
const STATUS_OPTIONS = [
  { value: 'ACTIVE', label: '在册' },
  { value: 'WITHDRAWN', label: '已退会' },
];

/** 表单值：日期字段在表单里是 Dayjs */
type UnionMemberFormValues = Omit<UnionMemberSaveCommand, 'joinDate' | 'duesPaidTo'> & {
  joinDate?: Dayjs;
  duesPaidTo?: Dayjs;
};

/**
 * 格式化日期。
 *
 * @param value 日期文本
 * @returns 展示用日期
 */
function formatDate(value?: string | null): string {
  return value ? dayjs(value).format('YYYY-MM-DD') : '—';
}

/** 工会会员页 */
export default function UnionMemberPage() {
  const [rows, setRows] = useState<UnionMemberView[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<string | undefined>();
  const [loading, setLoading] = useState(false);

  const [editing, setEditing] = useState<UnionMemberView | null>(null);
  const [open, setOpen] = useState(false);
  const [form] = Form.useForm<UnionMemberFormValues>();

  const canManage = hasPermission('sys:union:member:manage');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageUnionMembers({
        keyword: keyword || undefined,
        status,
        current,
        size: PAGE_SIZE,
      });
      setRows(page.records);
      setTotal(Number(page.total));
    } catch (err) {
      message.error(err instanceof Error ? err.message : '工会会员列表加载失败');
    } finally {
      setLoading(false);
    }
  }, [current, keyword, status]);

  useEffect(() => {
    void load();
  }, [load]);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ joinDate: dayjs(), duesBase: 0 });
    setOpen(true);
  };

  const openEdit = (row: UnionMemberView) => {
    setEditing(row);
    form.setFieldsValue({
      employeeNo: row.employeeNo,
      userId: row.userId ?? undefined,
      memberName: row.memberName,
      deptName: row.deptName ?? undefined,
      joinDate: dayjs(row.joinDate),
      duesBase: row.duesBase,
      duesPaidTo: row.duesPaidTo ? dayjs(row.duesPaidTo, 'YYYY-MM') : undefined,
      remark: row.remark ?? undefined,
    });
    setOpen(true);
  };

  const submit = async () => {
    const values = await form.validateFields();
    const payload: UnionMemberSaveCommand = {
      employeeNo: values.employeeNo,
      userId: values.userId ?? null,
      memberName: values.memberName,
      deptName: values.deptName ?? null,
      joinDate: values.joinDate ? values.joinDate.format('YYYY-MM-DD') : '',
      duesBase: values.duesBase ?? null,
      duesPaidTo: values.duesPaidTo ? values.duesPaidTo.format('YYYY-MM') : null,
      remark: values.remark ?? null,
    };
    try {
      if (editing) {
        await updateUnionMember(editing.id, payload);
        message.success('会员档案已更新');
      } else {
        await createUnionMember(payload);
        message.success('入会已登记');
      }
      setOpen(false);
      await load();
    } catch (err) {
      message.error(err instanceof Error ? err.message : '保存失败');
    }
  };

  const withdraw = async (row: UnionMemberView) => {
    try {
      await withdrawUnionMember(row.id);
      message.success('退会已办理，历史会籍保留');
      await load();
    } catch (err) {
      message.error(err instanceof Error ? err.message : '退会失败');
    }
  };

  const columns: ColumnsType<UnionMemberView> = [
    { title: '会员编号', dataIndex: 'memberNo', width: 130 },
    { title: '工号', dataIndex: 'employeeNo', width: 110 },
    { title: '姓名', dataIndex: 'memberName', width: 100 },
    { title: '所属部门', dataIndex: 'deptName', render: (value) => value ?? '—' },
    { title: '入会日期', dataIndex: 'joinDate', width: 110, render: (value: string) => formatDate(value) },
    {
      title: '会费基数',
      dataIndex: 'duesBase',
      width: 110,
      render: (value: number) => `¥${Number(value ?? 0).toFixed(2)}`,
    },
    {
      title: '缴至月份',
      dataIndex: 'duesPaidTo',
      width: 100,
      render: (value) => value ?? '—',
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (value: string) => (
        <Tag color={value === 'ACTIVE' ? 'green' : 'default'}>
          {value === 'ACTIVE' ? '在册' : value === 'WITHDRAWN' ? '已退会' : value}
        </Tag>
      ),
    },
    {
      title: '退会日期',
      dataIndex: 'withdrawDate',
      width: 110,
      render: (value?: string | null) => formatDate(value),
    },
    {
      title: '操作',
      key: 'actions',
      width: 150,
      render: (_value, row) =>
        canManage ? (
          <Space size={4}>
            <Button type="link" size="small" onClick={() => openEdit(row)}>
              编辑
            </Button>
            {row.status === 'ACTIVE' ? (
              <Popconfirm
                title="退会为终态，历史会籍保留；再入会需新建档案，确认？"
                onConfirm={() => void withdraw(row)}
              >
                <Button type="link" size="small" danger>
                  退会
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
      title="工会会员"
      size="small"
      extra={
        <Space>
          <Input.Search
            allowClear
            placeholder="会员编号 / 工号 / 姓名"
            style={{ width: 210 }}
            onSearch={(value) => {
              setCurrent(1);
              setKeyword(value.trim());
            }}
          />
          <Select
            allowClear
            placeholder="全部状态"
            style={{ width: 120 }}
            options={STATUS_OPTIONS}
            value={status}
            onChange={(value) => {
              setCurrent(1);
              setStatus(value);
            }}
          />
          {canManage ? (
            <Button type="primary" onClick={openCreate}>
              入会登记
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
        scroll={{ x: 1100 }}
        pagination={{
          current,
          pageSize: PAGE_SIZE,
          total,
          showSizeChanger: false,
          onChange: setCurrent,
        }}
      />

      <Drawer
        title={editing ? '编辑会员档案' : '入会登记'}
        width={560}
        open={open}
        onClose={() => setOpen(false)}
        extra={
          <Space>
            <Button onClick={() => setOpen(false)}>取消</Button>
            <Button type="primary" onClick={() => void submit()}>
              保存
            </Button>
          </Space>
        }
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item
            name="employeeNo"
            label="员工编号"
            rules={[{ required: true, message: '请输入员工编号' }]}
          >
            <Input maxLength={64} disabled={Boolean(editing)} />
          </Form.Item>
          <Form.Item name="memberName" label="姓名" rules={[{ required: true, message: '请输入姓名' }]}>
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item name="userId" label="平台用户 ID（可选，关联后可按人查我的福利）">
            <InputNumber min={1} precision={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="deptName" label="所属部门（ERP 快照）">
            <Input maxLength={128} />
          </Form.Item>
          <Form.Item
            name="joinDate"
            label="入会日期"
            rules={[{ required: true, message: '请选择入会日期' }]}
          >
            <DatePicker style={{ width: '100%' }} disabledDate={(value) => value.isAfter(dayjs())} />
          </Form.Item>
          <Form.Item name="duesBase" label="会费基数">
            <InputNumber min={0} precision={2} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="duesPaidTo" label="会费缴至月份">
            <DatePicker picker="month" style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={3} maxLength={255} showCount />
          </Form.Item>
        </Form>
      </Drawer>
    </Card>
  );
}
