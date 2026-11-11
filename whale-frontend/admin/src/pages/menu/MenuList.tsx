// 菜单管理页 - 树形表格 + 新增 / 编辑 / 删除
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
  Tag,
  TreeSelect,
  type TableProps,
} from 'antd';
import { useEffect, useState } from 'react';
import { AuthButton, FormModal, usePermission } from '@bluewhale/whale-ui';
import {
  createMenu,
  deleteMenu,
  treeMenus,
  updateMenu,
  type MenuPayload,
  type MenuType,
  type MenuVO,
} from '../../api/menu';

interface MenuFormValues extends MenuPayload {
  name: string;
}

interface TreeOption {
  /** 菜单 id 为后端 Long（字符串） */
  value: string;
  title: string;
  children?: TreeOption[];
}

const MENU_TYPE_OPTIONS: { value: MenuType; label: string }[] = [
  { value: 'DIR', label: '目录' },
  { value: 'MENU', label: '菜单' },
];

const FLAG_OPTIONS = [
  { value: 1, label: '是' },
  { value: 0, label: '否' },
];

const STATUS_OPTIONS = [
  { value: 1, label: '启用' },
  { value: 0, label: '停用' },
];

function toOptions(nodes: MenuVO[], excludeId?: string): TreeOption[] {
  return nodes
    .filter((node) => node.id !== excludeId)
    .map((node) => ({
      value: node.id,
      title: node.name,
      children: node.children?.length ? toOptions(node.children, excludeId) : undefined,
    }));
}

export default function MenuList() {
  const { message } = App.useApp();
  const { has } = usePermission();

  const [menus, setMenus] = useState<MenuVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [editorOpen, setEditorOpen] = useState(false);
  const [editRecord, setEditRecord] = useState<MenuVO | null>(null);
  const [editorForm] = Form.useForm<MenuFormValues>();

  const load = async () => {
    setLoading(true);
    try {
      setMenus(await treeMenus());
    } catch (error) {
      message.error(error instanceof Error ? error.message : '菜单加载失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleDelete = async (row: MenuVO) => {
    try {
      await deleteMenu(row.id);
      message.success('菜单已删除');
      void load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '删除失败');
    }
  };

  const handleSubmit = async (values: MenuFormValues) => {
    try {
      const payload: MenuPayload = { ...values, parentId: values.parentId ?? '0' };
      if (editRecord) {
        await updateMenu(editRecord.id, payload);
        message.success('菜单已更新');
      } else {
        await createMenu(payload);
        message.success('菜单已创建');
      }
      setEditorOpen(false);
      void load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : editRecord ? '更新失败' : '创建失败');
      throw error;
    }
  };

  const columns: TableProps<MenuVO>['columns'] = [
    { title: '菜单名称', dataIndex: 'name', width: 180 },
    {
      title: '类型',
      dataIndex: 'menuType',
      width: 90,
      render: (value: string | undefined) =>
        value === 'DIR' ? <Tag color="blue">目录</Tag> : <Tag color="geekblue">菜单</Tag>,
    },
    { title: '路由路径', dataIndex: 'path', width: 160 },
    { title: '组件', dataIndex: 'component', width: 180 },
    { title: '图标', dataIndex: 'icon', width: 100 },
    { title: '排序', dataIndex: 'sort', width: 80 },
    { title: '权限码', dataIndex: 'permissionCode', width: 160 },
    {
      title: '可见',
      dataIndex: 'visible',
      width: 80,
      render: (value: number | undefined) => (value === 1 ? '是' : '否'),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (value: number | undefined) =>
        value === 1 ? <Tag color="green">启用</Tag> : <Tag color="red">停用</Tag>,
    },
    {
      title: '操作',
      key: 'action',
      width: 160,
      render: (_, row) => (
        <Space size={4}>
          <AuthButton
            perm="sys:menu:edit"
            type="link"
            size="small"
            onClick={() => {
              setEditRecord(row);
              setEditorOpen(true);
            }}
          >
            编辑
          </AuthButton>
          {has('sys:menu:delete') && (
            <Popconfirm title="确认删除该菜单？" onConfirm={() => void handleDelete(row)}>
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
      <Table<MenuVO>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={menus}
        pagination={false}
        expandable={{ defaultExpandAllRows: true }}
        title={() => (
          <Space>
            <span>菜单列表</span>
            <AuthButton
              perm="sys:menu:add"
              type="primary"
              size="small"
              onClick={() => {
                setEditRecord(null);
                setEditorOpen(true);
              }}
            >
              新增菜单
            </AuthButton>
          </Space>
        )}
      />

      <FormModal<MenuFormValues>
        open={editorOpen}
        title={editRecord ? `编辑菜单：${editRecord.name}` : '新增菜单'}
        form={editorForm}
        initialValues={editRecord ?? undefined}
        onOk={handleSubmit}
        onCancel={() => setEditorOpen(false)}
      >
        <Form.Item name="parentId" label="上级菜单">
          <TreeSelect
            allowClear
            treeDefaultExpandAll
            placeholder="不选则为顶级菜单"
            treeData={[{ value: '0', title: '顶级菜单', children: toOptions(menus, editRecord?.id) }]}
          />
        </Form.Item>
        <Form.Item
          name="name"
          label="菜单名称"
          rules={[{ required: true, message: '请输入菜单名称' }]}
        >
          <Input />
        </Form.Item>
        <Form.Item name="menuType" label="菜单类型" initialValue="MENU">
          <Select options={MENU_TYPE_OPTIONS} />
        </Form.Item>
        <Form.Item name="path" label="路由路径">
          <Input placeholder="如 /system/user" />
        </Form.Item>
        <Form.Item name="component" label="组件路径">
          <Input placeholder="如 system/user/index" />
        </Form.Item>
        <Form.Item name="icon" label="图标">
          <Input />
        </Form.Item>
        <Form.Item name="sort" label="排序" initialValue={1}>
          <InputNumber min={0} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item name="permissionCode" label="权限码">
          <Input placeholder="如 sys:user:list" />
        </Form.Item>
        <Form.Item name="visible" label="是否可见" initialValue={1}>
          <Select options={FLAG_OPTIONS} />
        </Form.Item>
        <Form.Item name="status" label="状态" initialValue={1}>
          <Select options={STATUS_OPTIONS} />
        </Form.Item>
      </FormModal>
    </>
  );
}
