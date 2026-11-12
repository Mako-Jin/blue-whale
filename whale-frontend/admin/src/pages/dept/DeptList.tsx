// 部门管理页 - 树形表格 + 新增 / 编辑 / 删除
// 操作按钮均按权限码渲染（AuthButton / usePermission），无权限自动隐藏

import {
  App,
  Button,
  Form,
  Input,
  InputNumber,
  Popconfirm,
  Select,
  Space,
  Table,
  type TableProps,
} from 'antd';
import { useEffect, useState } from 'react';
import { AuthButton, FormModal, usePermission } from '@bluewhale/whale-ui';
import {
  createDept,
  deleteDept,
  treeDepts,
  updateDept,
  type DeptPayload,
  type DeptVO,
} from '../../api/dept';

interface DeptFormValues {
  /** 后端 Long 序列化为字符串 */
  parentId?: string;
  deptName: string;
  sort?: number;
  leader?: string;
}

/** 把部门树拍平，供上级部门下拉选择 */
function flattenDepts(nodes: DeptVO[], acc: DeptVO[] = []): DeptVO[] {
  nodes.forEach((node) => {
    acc.push(node);
    if (node.children?.length) {
      flattenDepts(node.children, acc);
    }
  });
  return acc;
}

export default function DeptList() {
  const { message } = App.useApp();
  const { has } = usePermission();

  const [depts, setDepts] = useState<DeptVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [editorOpen, setEditorOpen] = useState(false);
  const [editRecord, setEditRecord] = useState<DeptVO | null>(null);
  const [editorForm] = Form.useForm<DeptFormValues>();

  // 每次成功操作后重新拉取整棵树
  const load = async () => {
    setLoading(true);
    try {
      setDepts(await treeDepts());
    } catch (error) {
      message.error(error instanceof Error ? error.message : '部门加载失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleDelete = async (row: DeptVO) => {
    try {
      await deleteDept(row.id);
      message.success('部门已删除');
      void load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '删除失败');
    }
  };

  const handleSubmit = async (values: DeptFormValues) => {
    try {
      const payload: DeptPayload = { ...values, parentId: values.parentId ?? '0' };
      if (editRecord) {
        await updateDept(editRecord.id, payload);
        message.success('部门已更新');
      } else {
        await createDept(payload);
        message.success('部门已创建');
      }
      setEditorOpen(false);
      void load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : editRecord ? '更新失败' : '创建失败');
      throw error;
    }
  };

  const parentOptions = flattenDepts(depts)
    .filter((dept) => dept.id !== editRecord?.id)
    .map((dept) => ({ value: dept.id, label: dept.deptName }));

  const columns: TableProps<DeptVO>['columns'] = [
    { title: '部门名称', dataIndex: 'deptName', width: 240 },
    {
      title: '负责人',
      dataIndex: 'leader',
      width: 140,
      render: (value: string | undefined) => value ?? '-',
    },
    { title: '排序', dataIndex: 'sort', width: 80 },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      width: 180,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: '操作',
      key: 'action',
      width: 160,
      render: (_, row) => (
        <Space size={4}>
          <AuthButton
            perm="sys:dept:edit"
            type="link"
            size="small"
            onClick={() => {
              setEditRecord(row);
              setEditorOpen(true);
            }}
          >
            编辑
          </AuthButton>
          {has('sys:dept:delete') && (
            <Popconfirm title="确认删除该部门？" onConfirm={() => void handleDelete(row)}>
              <Button type="link" size="small" danger>
                删除
              </Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  return (
    <>
      <Table<DeptVO>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={depts}
        pagination={false}
        expandable={{ defaultExpandAllRows: true }}
        title={() => (
          <Space>
            <span>部门列表</span>
            <AuthButton
              perm="sys:dept:add"
              type="primary"
              size="small"
              onClick={() => {
                setEditRecord(null);
                setEditorOpen(true);
              }}
            >
              新增部门
            </AuthButton>
          </Space>
        )}
      />

      <FormModal<DeptFormValues>
        open={editorOpen}
        title={editRecord ? `编辑部门：${editRecord.deptName}` : '新增部门'}
        form={editorForm}
        initialValues={editRecord ?? undefined}
        onOk={handleSubmit}
        onCancel={() => setEditorOpen(false)}
      >
        <Form.Item name="parentId" label="上级部门">
          <Select
            allowClear
            options={parentOptions}
            placeholder="不选则为顶级部门"
            showSearch
            optionFilterProp="label"
          />
        </Form.Item>
        <Form.Item
          name="deptName"
          label="部门名称"
          rules={[{ required: true, message: '请输入部门名称' }]}
        >
          <Input />
        </Form.Item>
        <Form.Item name="sort" label="排序" initialValue={1}>
          <InputNumber min={0} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item name="leader" label="负责人">
          <Input />
        </Form.Item>
      </FormModal>
    </>
  );
}
