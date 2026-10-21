// 权限点管理页 - 分页查询 / 新增 / 编辑 / 删除
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
  Tag,
  type TableProps,
} from 'antd';
import { useRef, useState } from 'react';
import {
  AuthButton,
  FormModal,
  ProTable,
  SearchForm,
  usePermission,
  type ProTableRef,
} from '@bluewhale/whale-ui';
import {
  createPermission,
  deletePermission,
  pagePermissions,
  updatePermission,
  type PermissionPayload,
  type PermissionType,
  type PermissionVO,
} from '../../api/permission';

interface PermissionFormValues {
  parentId?: number;
  permCode: string;
  permName: string;
  permType: PermissionType | string;
  path?: string;
  method?: string;
  sortNo?: number;
  status?: number;
}

type PermissionQuery = { keyword?: string; permType?: string };

const PERM_TYPE_OPTIONS: { value: PermissionType; label: string }[] = [
  { value: 'SYSTEM', label: '系统' },
  { value: 'MENU', label: '菜单' },
  { value: 'API', label: '接口' },
  { value: 'BUTTON', label: '按钮' },
  { value: 'DATA', label: '数据' },
  { value: 'ATTR', label: '属性' },
];

const STATUS_OPTIONS = [
  { value: 1, label: '启用' },
  { value: 0, label: '停用' },
];

/** 权限类型中文名，未匹配的原样展示 */
function permTypeLabel(value?: string): string {
  return PERM_TYPE_OPTIONS.find((option) => option.value === value)?.label ?? value ?? '-';
}

export default function PermissionList() {
  const { message } = App.useApp();
  const { has } = usePermission();
  const tableRef = useRef<ProTableRef>(null);

  const [keyword, setKeyword] = useState<string | undefined>(undefined);
  const [permType, setPermType] = useState<string | undefined>(undefined);

  const [editorOpen, setEditorOpen] = useState(false);
  const [editRecord, setEditRecord] = useState<PermissionVO | null>(null);
  const [editorForm] = Form.useForm<PermissionFormValues>();

  const showError = (error: unknown, fallback: string) => {
    message.error(error instanceof Error ? error.message : fallback);
  };

  const handleDelete = async (row: PermissionVO) => {
    try {
      await deletePermission(row.id);
      message.success('权限已删除');
      tableRef.current?.reload();
    } catch (error) {
      showError(error, '删除失败');
    }
  };

  const handleSubmit = async (values: PermissionFormValues) => {
    const payload: PermissionPayload = {
      parentId: values.parentId,
      permCode: values.permCode,
      permName: values.permName,
      permType: values.permType,
      path: values.path,
      method: values.method,
      sortNo: values.sortNo,
      status: values.status,
    };
    try {
      if (editRecord) {
        await updatePermission(editRecord.id, payload);
        message.success('权限已更新');
      } else {
        await createPermission(payload);
        message.success('权限已创建');
      }
      setEditorOpen(false);
      tableRef.current?.reload();
    } catch (error) {
      showError(error, editRecord ? '更新失败' : '创建失败');
      throw error;
    }
  };

  const columns: TableProps<PermissionVO>['columns'] = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    { title: '权限编码', dataIndex: 'permCode', width: 200 },
    { title: '权限名称', dataIndex: 'permName', width: 160 },
    {
      title: '类型',
      dataIndex: 'permType',
      width: 100,
      render: (value: string | undefined) => <Tag color="blue">{permTypeLabel(value)}</Tag>,
    },
    {
      title: '路径',
      dataIndex: 'path',
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: '方法',
      dataIndex: 'method',
      width: 100,
      render: (value: string | undefined) => value ?? '-',
    },
    { title: '排序', dataIndex: 'sortNo', width: 80 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
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
            perm="sys:permission:edit"
            type="link"
            size="small"
            onClick={() => {
              setEditRecord(row);
              setEditorOpen(true);
            }}
          >
            编辑
          </AuthButton>
          {has('sys:permission:delete') && (
            <Popconfirm title="确认删除该权限？" onConfirm={() => void handleDelete(row)}>
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
      <ProTable<PermissionVO, PermissionQuery>
        ref={tableRef}
        rowKey="id"
        title="权限列表"
        columns={columns}
        request={pagePermissions}
        params={{ keyword, permType }}
        onError={(error) => showError(error, '权限列表加载失败')}
        search={
          <SearchForm
            items={[
              {
                name: 'keyword',
                label: '关键字',
                element: <Input allowClear placeholder="权限编码 / 名称" />,
              },
              {
                name: 'permType',
                label: '类型',
                element: <Select allowClear options={PERM_TYPE_OPTIONS} placeholder="全部" style={{ width: 140 }} />,
              },
            ]}
            onSearch={(values) => {
              setKeyword((values.keyword as string) || undefined);
              setPermType((values.permType as string) || undefined);
            }}
            onReset={() => {
              setKeyword(undefined);
              setPermType(undefined);
            }}
          />
        }
        toolbar={
          <AuthButton
            perm="sys:permission:add"
            type="primary"
            onClick={() => {
              setEditRecord(null);
              setEditorOpen(true);
            }}
          >
            新增权限
          </AuthButton>
        }
      />

      <FormModal<PermissionFormValues>
        open={editorOpen}
        title={editRecord ? `编辑权限：${editRecord.permName}` : '新增权限'}
        form={editorForm}
        initialValues={editRecord ?? undefined}
        onOk={handleSubmit}
        onCancel={() => setEditorOpen(false)}
      >
        <Form.Item name="parentId" label="上级权限ID">
          <InputNumber min={0} style={{ width: '100%' }} placeholder="留空表示无上级" />
        </Form.Item>
        <Form.Item
          name="permCode"
          label="权限编码"
          rules={[{ required: true, message: '请输入权限编码' }]}
        >
          <Input placeholder="如 sys:job:list" />
        </Form.Item>
        <Form.Item
          name="permName"
          label="权限名称"
          rules={[{ required: true, message: '请输入权限名称' }]}
        >
          <Input />
        </Form.Item>
        <Form.Item
          name="permType"
          label="权限类型"
          rules={[{ required: true, message: '请选择权限类型' }]}
        >
          <Select options={PERM_TYPE_OPTIONS} placeholder="请选择权限类型" />
        </Form.Item>
        <Form.Item name="path" label="路径">
          <Input placeholder="如 /job/info/page" />
        </Form.Item>
        <Form.Item name="method" label="请求方法">
          <Input placeholder="如 GET / POST / PUT / DELETE" />
        </Form.Item>
        <Form.Item name="sortNo" label="排序" initialValue={1}>
          <InputNumber min={0} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item name="status" label="状态" initialValue={1}>
          <Select options={STATUS_OPTIONS} />
        </Form.Item>
      </FormModal>
    </>
  );
}
