// 支部台账（M-P1）：列表 + 新建/编辑 + 启用停用。
//
// 支部编码由后端编号流水生成（BR+年份+4 位），页面只读展示，不提供输入框——
// 一旦允许手填，就会出现两套编码规则，后续排序与统计都要兼容两种格式。

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
import type { Dayjs } from 'dayjs';
import { useCallback, useEffect, useState } from 'react';
import {
  branchTree,
  changeBranchStatus,
  createBranch,
  pageBranch,
  updateBranch,
  type BranchSaveCommand,
  type BranchTreeView,
  type BranchView,
} from '../api/party';

/** 每页条数 */
const PAGE_SIZE = 10;

/** 表单值：成立日期在表单里是 Dayjs，提交前转成 yyyy-MM-dd 字符串 */
type BranchFormValues = Omit<BranchSaveCommand, 'establishedDate'> & { establishedDate?: Dayjs };

/** 支部台账页 */
export default function BranchPage() {
  const [rows, setRows] = useState<BranchView[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [keyword, setKeyword] = useState('');
  const [loading, setLoading] = useState(false);
  const [tree, setTree] = useState<BranchTreeView[]>([]);
  const [editing, setEditing] = useState<BranchView | null>(null);
  const [open, setOpen] = useState(false);
  const [form] = Form.useForm<BranchFormValues>();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageBranch({ keyword: keyword || undefined, current, size: PAGE_SIZE });
      setRows(page.records);
      setTotal(page.total);
    } catch (err) {
      message.error(err instanceof Error ? err.message : '支部列表加载失败');
    } finally {
      setLoading(false);
    }
  }, [current, keyword]);

  const loadTree = useCallback(async () => {
    try {
      setTree(await branchTree());
    } catch {
      // 树仅用于上级选择，失败不阻塞列表；下拉退化为空即只能建一级支部
      setTree([]);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    void loadTree();
  }, [loadTree]);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ parentId: 0, sortNo: 0 });
    setOpen(true);
  };

  const openEdit = (row: BranchView) => {
    setEditing(row);
    form.setFieldsValue({
      branchName: row.branchName,
      parentId: row.parentId,
      orgCode: row.orgCode ?? undefined,
      secretaryName: row.secretaryName ?? undefined,
      secretaryUserId: row.secretaryUserId ?? undefined,
      sortNo: row.sortNo ?? 0,
      remark: row.remark ?? undefined,
    });
    setOpen(true);
  };

  const submit = async () => {
    const values = await form.validateFields();
    const payload: BranchSaveCommand = {
      ...values,
      establishedDate: values.establishedDate ? values.establishedDate.format('YYYY-MM-DD') : undefined,
    };
    try {
      if (editing) {
        await updateBranch(editing.id, payload);
        message.success('支部已更新');
      } else {
        await createBranch(payload);
        message.success('支部已创建');
      }
      setOpen(false);
      await Promise.all([load(), loadTree()]);
    } catch (err) {
      message.error(err instanceof Error ? err.message : '保存失败');
    }
  };

  const toggle = async (row: BranchView) => {
    try {
      await changeBranchStatus(row.id, row.status !== 'ACTIVE');
      message.success(row.status === 'ACTIVE' ? '支部已停用' : '支部已启用');
      await Promise.all([load(), loadTree()]);
    } catch (err) {
      message.error(err instanceof Error ? err.message : '状态变更失败');
    }
  };

  const columns: ColumnsType<BranchView> = [
    { title: '支部编码', dataIndex: 'branchCode', width: 140 },
    { title: '支部名称', dataIndex: 'branchName' },
    {
      title: '上级支部',
      dataIndex: 'parentName',
      width: 180,
      render: (value: string | null) => value ?? '—',
    },
    { title: '层级', dataIndex: 'branchLevel', width: 70 },
    { title: '组织编码', dataIndex: 'orgCode', width: 120, render: (value) => value ?? '—' },
    { title: '支部书记', dataIndex: 'secretaryName', width: 110, render: (value) => value ?? '—' },
    {
      title: '状态',
      dataIndex: 'statusLabel',
      width: 90,
      render: (_value, row) => (
        <Tag color={row.status === 'ACTIVE' ? 'green' : 'default'}>{row.statusLabel}</Tag>
      ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 160,
      render: (_value, row) => (
        <Space size={4}>
          <Button type="link" size="small" onClick={() => openEdit(row)}>
            编辑
          </Button>
          <Popconfirm
            title={row.status === 'ACTIVE' ? '停用后不能再接收党员，确认？' : '确认恢复该支部？'}
            onConfirm={() => void toggle(row)}
          >
            <Button type="link" size="small" danger={row.status === 'ACTIVE'}>
              {row.status === 'ACTIVE' ? '停用' : '启用'}
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <Card
      title="支部台账"
      size="small"
      extra={
        <Space>
          <Input.Search
            allowClear
            placeholder="编码 / 名称"
            style={{ width: 200 }}
            onSearch={(value) => {
              setCurrent(1);
              setKeyword(value.trim());
            }}
          />
          <Button type="primary" onClick={openCreate}>
            新建支部
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
        pagination={{
          current,
          pageSize: PAGE_SIZE,
          total,
          showSizeChanger: false,
          onChange: setCurrent,
        }}
      />

      <Modal
        title={editing ? '编辑支部' : '新建支部'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={() => void submit()}
        destroyOnClose
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item
            name="branchName"
            label="支部名称"
            rules={[{ required: true, message: '请输入支部名称' }]}
          >
            <Input maxLength={128} placeholder="如：第一党支部" />
          </Form.Item>
          <Form.Item name="parentId" label="上级支部">
            <Select
              allowClear
              placeholder="不选表示一级支部"
              options={[
                { value: 0, label: '（一级支部）' },
                ...tree.map((node) => ({ value: node.id, label: `${node.branchCode} ${node.branchName}` })),
              ]}
            />
          </Form.Item>
          <Form.Item name="orgCode" label="关联组织编码（ERP，可空）">
            <Input maxLength={64} placeholder="弱关联，组织调整不会自动跟随" />
          </Form.Item>
          <Form.Item name="secretaryName" label="支部书记">
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item name="establishedDate" label="成立日期">
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="sortNo" label="排序号">
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} maxLength={255} />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
}
