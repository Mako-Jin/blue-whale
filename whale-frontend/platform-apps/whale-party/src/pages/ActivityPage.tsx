// 组织生活（M-P1）：计划 → 完成 → 归档 三态流转 + 整场签到录入。
//
// 归档是终态，页面在归档后隐藏一切写操作按钮：归档语义是「材料定稿」，
// 允许事后改签到会让「这次会议到会几人」在考核时被悄悄改写。

import {
  Button,
  Card,
  Checkbox,
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
import { useCallback, useEffect, useState } from 'react';
import {
  archiveActivity,
  branchTree,
  completeActivity,
  createActivity,
  getActivity,
  pageActivity,
  pageMember,
  signinActivity,
  updateActivity,
  type ActivitySaveCommand,
  type ActivityView,
  type BranchTreeView,
} from '../api/party';

/** 每页条数 */
const PAGE_SIZE = 10;

/** 组织生活类型选项 */
const TYPE_OPTIONS = [
  { value: 'BRANCH_COMMITTEE', label: '支委会' },
  { value: 'MEMBER_ASSEMBLY', label: '党员大会' },
  { value: 'PARTY_GROUP', label: '党小组会' },
  { value: 'PARTY_LECTURE', label: '党课' },
  { value: 'THEME_DAY', label: '主题党日' },
];

/** 表单值：计划日期在表单里是 Dayjs */
type ActivityFormValues = Omit<ActivitySaveCommand, 'plannedDate'> & { plannedDate?: Dayjs };

/**
 * 把支部树拍平成下拉选项。
 *
 * @param nodes 树节点
 * @param depth 当前层级
 * @returns 选项列表
 */
function flattenBranches(
  nodes: BranchTreeView[],
  depth = 0,
): Array<{ value: number; label: string }> {
  return nodes.flatMap((node) => [
    { value: node.id, label: `${'　'.repeat(depth)}${node.branchCode} ${node.branchName}` },
    ...flattenBranches(node.children ?? [], depth + 1),
  ]);
}

/** 组织生活页 */
export default function ActivityPage() {
  const [rows, setRows] = useState<ActivityView[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<string | undefined>();
  const [loading, setLoading] = useState(false);
  const [tree, setTree] = useState<BranchTreeView[]>([]);
  const [editing, setEditing] = useState<ActivityView | null>(null);
  const [open, setOpen] = useState(false);
  const [form] = Form.useForm<ActivityFormValues>();

  const [signinOpen, setSigninOpen] = useState(false);
  const [signinTarget, setSigninTarget] = useState<ActivityView | null>(null);
  const [signinChoices, setSigninChoices] = useState<
    Array<{ id: number; name: string; present: boolean }>
  >([]);
  const [signinBusy, setSigninBusy] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageActivity({
        keyword: keyword || undefined,
        status,
        current,
        size: PAGE_SIZE,
      });
      setRows(page.records);
      setTotal(Number(page.total));
    } catch (err) {
      message.error(err instanceof Error ? err.message : '组织生活列表加载失败');
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
    form.setFieldsValue({
      activityType: 'MEMBER_ASSEMBLY',
      plannedDate: dayjs(),
      expectedCount: 0,
    } as ActivityFormValues);
    setOpen(true);
  };

  const openEdit = (row: ActivityView) => {
    setEditing(row);
    form.setFieldsValue({
      branchId: row.branchId,
      activityType: row.activityType,
      title: row.title,
      plannedDate: dayjs(row.plannedDate),
      location: row.location ?? undefined,
      hostName: row.hostName ?? undefined,
      expectedCount: row.expectedCount,
      summary: row.summary ?? undefined,
      remark: row.remark ?? undefined,
    });
    setOpen(true);
  };

  const submit = async () => {
    const values = await form.validateFields();
    const payload: ActivitySaveCommand = {
      ...values,
      plannedDate: values.plannedDate ? values.plannedDate.format('YYYY-MM-DD') : '',
    };
    try {
      if (editing) {
        await updateActivity(editing.id, payload);
        message.success('已更新');
      } else {
        await createActivity(payload);
        message.success('计划已创建');
      }
      setOpen(false);
      await load();
    } catch (err) {
      message.error(err instanceof Error ? err.message : '保存失败');
    }
  };

  const complete = async (row: ActivityView) => {
    try {
      await completeActivity(row.id, dayjs().format('YYYY-MM-DD'));
      message.success('已标记完成');
      await load();
    } catch (err) {
      message.error(err instanceof Error ? err.message : '操作失败');
    }
  };

  const archive = async (row: ActivityView) => {
    try {
      await archiveActivity(row.id);
      message.success('已归档');
      await load();
    } catch (err) {
      message.error(err instanceof Error ? err.message : '归档失败（需先完成并填写纪要）');
    }
  };

  const openSignin = async (row: ActivityView) => {
    setSigninBusy(true);
    try {
      const detail = await getActivity(row.id);
      const members = await pageMember({ branchId: row.branchId, status: 'ACTIVE', size: 200 });
      const existing = new Set(
        (detail.signins ?? []).filter((item) => item.present).map((item) => item.memberId),
      );
      setSigninTarget(detail);
      setSigninChoices(
        members.records.map((member) => ({
          id: member.id,
          name: member.memberName,
          present: existing.has(member.id),
        })),
      );
      setSigninOpen(true);
    } catch (err) {
      message.error(err instanceof Error ? err.message : '签到名单加载失败');
    } finally {
      setSigninBusy(false);
    }
  };

  const saveSignin = async () => {
    if (!signinTarget) {
      return;
    }
    try {
      await signinActivity(
        signinTarget.id,
        signinChoices.map((item) => ({ memberId: item.id, present: item.present })),
      );
      message.success('签到已保存');
      setSigninOpen(false);
      await load();
    } catch (err) {
      message.error(err instanceof Error ? err.message : '签到保存失败');
    }
  };

  const columns: ColumnsType<ActivityView> = [
    { title: '主题', dataIndex: 'title' },
    {
      title: '类型',
      dataIndex: 'typeLabel',
      width: 100,
      render: (value: string) => <Tag>{value}</Tag>,
    },
    { title: '支部', dataIndex: 'branchName', width: 160, render: (value) => value ?? '—' },
    { title: '计划日期', dataIndex: 'plannedDate', width: 110 },
    { title: '召开日期', dataIndex: 'heldDate', width: 110, render: (value) => value ?? '—' },
    {
      title: '应到 / 实到',
      key: 'counts',
      width: 110,
      render: (_value, row) => `${row.expectedCount} / ${row.presentCount ?? 0}`,
    },
    {
      title: '状态',
      dataIndex: 'statusLabel',
      width: 90,
      render: (_value, row) => (
        <Tag
          color={
            row.status === 'ARCHIVED' ? 'default' : row.status === 'COMPLETED' ? 'green' : 'blue'
          }
        >
          {row.statusLabel}
        </Tag>
      ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 230,
      render: (_value, row) => (
        <Space size={4}>
          <Button
            type="link"
            size="small"
            disabled={row.status === 'ARCHIVED'}
            onClick={() => openEdit(row)}
          >
            编辑
          </Button>
          {row.status !== 'ARCHIVED' ? (
            <Button
              type="link"
              size="small"
              loading={signinBusy}
              onClick={() => void openSignin(row)}
            >
              签到
            </Button>
          ) : null}
          {row.status === 'PLANNED' ? (
            <Popconfirm title="标记为已召开？" onConfirm={() => void complete(row)}>
              <Button type="link" size="small">
                完成
              </Button>
            </Popconfirm>
          ) : null}
          {row.status === 'COMPLETED' ? (
            <Popconfirm title="归档后不可再修改，确认？" onConfirm={() => void archive(row)}>
              <Button type="link" size="small" danger>
                归档
              </Button>
            </Popconfirm>
          ) : null}
        </Space>
      ),
    },
  ];

  return (
    <Card
      title="组织生活"
      size="small"
      extra={
        <Space>
          <Input.Search
            allowClear
            placeholder="主题关键字"
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
              { value: 'PLANNED', label: '计划中' },
              { value: 'COMPLETED', label: '已完成' },
              { value: 'ARCHIVED', label: '已归档' },
            ]}
            onChange={(value) => {
              setCurrent(1);
              setStatus(value);
            }}
          />
          <Button type="primary" onClick={openCreate}>
            新建计划
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
        scroll={{ x: 1000 }}
        pagination={{
          current,
          pageSize: PAGE_SIZE,
          total,
          showSizeChanger: false,
          onChange: setCurrent,
        }}
      />

      <Modal
        title={editing ? '编辑组织生活' : '新建组织生活'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={() => void submit()}
        destroyOnClose
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item
            name="branchId"
            label="所属支部"
            rules={[{ required: true, message: '请选择支部' }]}
          >
            <Select showSearch optionFilterProp="label" options={flattenBranches(tree)} />
          </Form.Item>
          <Form.Item name="activityType" label="类型">
            <Select options={TYPE_OPTIONS} />
          </Form.Item>
          <Form.Item
            name="title"
            label="活动主题"
            rules={[{ required: true, message: '请输入主题' }]}
          >
            <Input maxLength={200} />
          </Form.Item>
          <Form.Item
            name="plannedDate"
            label="计划日期"
            rules={[{ required: true, message: '请选择日期' }]}
          >
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="location" label="地点">
            <Input maxLength={128} />
          </Form.Item>
          <Form.Item name="hostName" label="主持人">
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item name="expectedCount" label="应到人数">
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="summary" label="会议纪要（归档前必填）">
            <Input.TextArea rows={3} maxLength={2000} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={`签到 · ${signinTarget?.title ?? ''}`}
        open={signinOpen}
        onCancel={() => setSigninOpen(false)}
        onOk={() => void saveSignin()}
        okText="保存签到"
        width={520}
      >
        <Space direction="vertical" style={{ width: '100%' }}>
          <span style={{ color: '#888' }}>
            仅本支部在册党员可签到；上级列席请记入会议纪要，不计入签到明细。
          </span>
          <Checkbox.Group
            style={{ display: 'flex', flexDirection: 'column', gap: 8 }}
            value={signinChoices.filter((item) => item.present).map((item) => item.id)}
            onChange={(checked) => {
              const present = new Set(checked as number[]);
              setSigninChoices((prev) =>
                prev.map((item) => ({ ...item, present: present.has(item.id) })),
              );
            }}
            options={signinChoices.map((item) => ({ label: item.name, value: item.id }))}
          />
        </Space>
      </Modal>
    </Card>
  );
}
