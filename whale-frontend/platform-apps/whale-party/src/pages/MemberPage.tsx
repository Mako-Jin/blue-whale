// 党员档案（M-P1）：列表 + 建档/编辑 + 组织关系转出 + 预备党员转正。
//
// 转出与转正走独立按钮而不是编辑框里的字段：两件事都带日期、有前置条件、会改档案状态，
// 混进通用编辑会让「谁在什么时候办的手续」无从追溯。

import {
  Button,
  Card,
  DatePicker,
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
import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  branchTree,
  createMember,
  pageMember,
  promoteMember,
  transferMember,
  updateMember,
  type BranchTreeView,
  type MemberSaveCommand,
  type MemberView,
} from '../api/party';

/** 每页条数 */
const PAGE_SIZE = 10;

/** 表单值：日期字段在表单里是 Dayjs */
type MemberFormValues = Omit<MemberSaveCommand, 'joinPartyDate' | 'birthDate'> & {
  joinPartyDate?: Dayjs;
  birthDate?: Dayjs;
};

/** 学历选项（空值计入看板「未知」桶） */
const EDUCATION_OPTIONS = [
  { value: 'DOCTOR', label: '博士' },
  { value: 'MASTER', label: '硕士' },
  { value: 'BACHELOR', label: '本科' },
  { value: 'COLLEGE', label: '大专' },
  { value: 'SECONDARY', label: '中专及以下' },
  { value: 'OTHER', label: '其他' },
];

/**
 * 把支部树拍平成下拉选项。
 *
 * @param nodes 树节点
 * @param depth 当前层级
 * @returns 选项列表
 */
function flattenBranches(nodes: BranchTreeView[], depth = 0): Array<{ value: number; label: string }> {
  return nodes.flatMap((node) => [
    { value: node.id, label: `${'　'.repeat(depth)}${node.branchCode} ${node.branchName}` },
    ...flattenBranches(node.children ?? [], depth + 1),
  ]);
}

/** 党员档案页 */
export default function MemberPage() {
  const [rows, setRows] = useState<MemberView[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<string | undefined>();
  const [loading, setLoading] = useState(false);
  const [tree, setTree] = useState<BranchTreeView[]>([]);
  const [editing, setEditing] = useState<MemberView | null>(null);
  const [open, setOpen] = useState(false);
  const [form] = Form.useForm<MemberFormValues>();

  const branchOptions = useMemo(() => flattenBranches(tree), [tree]);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageMember({ keyword: keyword || undefined, status, current, size: PAGE_SIZE });
      setRows(page.records);
      setTotal(page.total);
    } catch (err) {
      message.error(err instanceof Error ? err.message : '党员列表加载失败');
    } finally {
      setLoading(false);
    }
  }, [current, keyword, status]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    branchTree()
      .then(setTree)
      .catch(() => setTree([]));
  }, []);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ politicalStatus: 'FULL_MEMBER' } as MemberFormValues);
    setOpen(true);
  };

  const openEdit = (row: MemberView) => {
    setEditing(row);
    form.setFieldsValue({
      employeeNo: row.employeeNo,
      memberName: row.memberName,
      branchId: row.branchId,
      politicalStatus: row.politicalStatus,
      birthDate: row.birthDate ? dayjs(row.birthDate) : undefined,
      education: row.education ?? undefined,
      joinPartyDate: dayjs(row.joinPartyDate),
      duesBase: row.duesBase,
      phone: row.phone ?? undefined,
      remark: row.remark ?? undefined,
    });
    setOpen(true);
  };

  const submit = async () => {
    const values = await form.validateFields();
    const payload: MemberSaveCommand = {
      ...values,
      birthDate: values.birthDate ? values.birthDate.format('YYYY-MM-DD') : null,
      joinPartyDate: values.joinPartyDate ? values.joinPartyDate.format('YYYY-MM-DD') : '',
    };
    try {
      if (editing) {
        await updateMember(editing.id, payload);
        message.success('档案已更新');
      } else {
        await createMember(payload);
        message.success('党员已建档');
      }
      setOpen(false);
      await load();
    } catch (err) {
      message.error(err instanceof Error ? err.message : '保存失败');
    }
  };

  const transfer = async (row: MemberView) => {
    try {
      await transferMember(row.id);
      message.success('组织关系已转出，档案转只读');
      await load();
    } catch (err) {
      message.error(err instanceof Error ? err.message : '转出失败');
    }
  };

  const promote = async (row: MemberView) => {
    try {
      await promoteMember(row.id);
      message.success('已办理转正');
      await load();
    } catch (err) {
      message.error(err instanceof Error ? err.message : '转正失败');
    }
  };

  const columns: ColumnsType<MemberView> = [
    { title: '党员编号', dataIndex: 'memberNo', width: 130 },
    { title: '工号', dataIndex: 'employeeNo', width: 110 },
    { title: '姓名', dataIndex: 'memberName', width: 100 },
    { title: '所属支部', dataIndex: 'branchName', render: (value) => value ?? '—' },
    {
      title: '政治面貌',
      dataIndex: 'politicalLabel',
      width: 100,
      render: (_value, row) => (
        <Tag color={row.politicalStatus === 'PROBATIONARY' ? 'gold' : 'blue'}>{row.politicalLabel}</Tag>
      ),
    },
    { title: '入党日期', dataIndex: 'joinPartyDate', width: 110 },
    {
      title: '党龄',
      dataIndex: 'partyAgeYears',
      width: 80,
      render: (value: number, row) => (
        <Space size={4}>
          <span>{value} 年</span>
          {row.probationaryPending ? <Tag color="orange">待转正</Tag> : null}
        </Space>
      ),
    },
    {
      title: '状态',
      dataIndex: 'statusLabel',
      width: 90,
      render: (_value, row) => (
        <Tag color={row.status === 'ACTIVE' ? 'green' : row.status === 'TRANSFERRED' ? 'default' : 'red'}>
          {row.statusLabel}
        </Tag>
      ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 190,
      render: (_value, row) => (
        <Space size={4}>
          <Button
            type="link"
            size="small"
            disabled={row.status === 'TRANSFERRED'}
            onClick={() => openEdit(row)}
          >
            编辑
          </Button>
          {row.probationaryPending ? (
            <Popconfirm title="确认办理转正？" onConfirm={() => void promote(row)}>
              <Button type="link" size="small">
                转正
              </Button>
            </Popconfirm>
          ) : null}
          {row.status === 'ACTIVE' ? (
            <Popconfirm
              title="转出后档案只读且不可再改，确认？"
              onConfirm={() => void transfer(row)}
            >
              <Button type="link" size="small" danger>
                转出
              </Button>
            </Popconfirm>
          ) : null}
        </Space>
      ),
    },
  ];

  return (
    <Card
      title="党员档案"
      size="small"
      extra={
        <Space>
          <Input.Search
            allowClear
            placeholder="编号 / 工号 / 姓名"
            style={{ width: 200 }}
            onSearch={(value) => {
              setCurrent(1);
              setKeyword(value.trim());
            }}
          />
          <Select
            allowClear
            placeholder="全部状态"
            style={{ width: 120 }}
            options={[
              { value: 'ACTIVE', label: '在册' },
              { value: 'TRANSFERRED', label: '已转出' },
              { value: 'SUSPENDED', label: '停籍' },
            ]}
            onChange={(value) => {
              setCurrent(1);
              setStatus(value);
            }}
          />
          <Button type="primary" onClick={openCreate}>
            党员建档
          </Button>
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

      <Modal
        title={editing ? '编辑党员档案' : '党员建档'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={() => void submit()}
        destroyOnClose
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item
            name="employeeNo"
            label="员工编号"
            rules={[{ required: true, message: '请输入员工编号（一人一档）' }]}
          >
            <Input maxLength={64} disabled={Boolean(editing)} />
          </Form.Item>
          <Form.Item name="memberName" label="姓名" rules={[{ required: true, message: '请输入姓名' }]}>
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item name="branchId" label="所属支部" rules={[{ required: true, message: '请选择支部' }]}>
            <Select showSearch optionFilterProp="label" options={branchOptions} />
          </Form.Item>
          <Form.Item name="politicalStatus" label="政治面貌">
            <Select
              options={[
                { value: 'FULL_MEMBER', label: '正式党员' },
                { value: 'PROBATIONARY', label: '预备党员' },
              ]}
            />
          </Form.Item>
          <Form.Item name="birthDate" label="出生日期">
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="education" label="学历">
            <Select allowClear options={EDUCATION_OPTIONS} placeholder="未填写（看板计入未知）" />
          </Form.Item>
          <Form.Item
            name="joinPartyDate"
            label="入党日期"
            rules={[{ required: !editing, message: '请选择入党日期' }]}
          >
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="duesBase" label="党费基数（月收入口径）">
            <InputNumber min={0} precision={2} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="phone" label="联系电话">
            <Input maxLength={32} />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} maxLength={255} />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
}
