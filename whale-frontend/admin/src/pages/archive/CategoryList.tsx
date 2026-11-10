// 档案门类管理页：门类树查询 + 新增/修改/删除
// 写操作按钮按权限码 sys:archive:category:manage 渲染

import {
  App,
  Button,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  type TableProps,
} from 'antd';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { AuthButton, usePermission } from '@bluewhale/whale-ui';
import {
  categoryTree,
  createCategory,
  deleteCategory,
  updateCategory,
  type ArchiveCategorySaveRequest,
  type ArchiveCategoryVO,
} from '../../api/archive';
import { SECURITY_COLORS, SECURITY_OPTIONS, flattenCategories, securityLabel } from './helpers';

/** 门类表单值 */
interface CategoryFormValues {
  code: string;
  name: string;
  parentId: string;
  retentionYears?: number;
  securityLevel: number;
  sort: number;
  status: number;
}

/** 根节点父ID */
const ROOT_PARENT_ID = '0';

/** 默认表单值 */
const DEFAULT_VALUES: CategoryFormValues = {
  code: '',
  name: '',
  parentId: ROOT_PARENT_ID,
  securityLevel: 2,
  sort: 0,
  status: 1,
};

export default function ArchiveCategoryList() {
  const { message } = App.useApp();
  const { has } = usePermission();
  const [form] = Form.useForm<CategoryFormValues>();
  const [tree, setTree] = useState<ArchiveCategoryVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<ArchiveCategoryVO | null>(null);
  const [parentLocked, setParentLocked] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setTree(await categoryTree());
    } catch (error) {
      message.error(error instanceof Error ? error.message : '门类树加载失败');
    } finally {
      setLoading(false);
    }
  }, [message]);

  useEffect(() => {
    void load();
  }, [load]);

  const parentOptions = useMemo(
    () => [
      { label: '根节点', value: ROOT_PARENT_ID },
      ...flattenCategories(tree).filter((item) => item.value !== editing?.id),
    ],
    [tree, editing],
  );

  const openCreate = (parentId?: string) => {
    setEditing(null);
    setParentLocked(false);
    form.setFieldsValue({ ...DEFAULT_VALUES, parentId: parentId ?? ROOT_PARENT_ID });
    setModalOpen(true);
  };

  const openEdit = (row: ArchiveCategoryVO, lockParent: boolean) => {
    setEditing(row);
    setParentLocked(lockParent);
    form.setFieldsValue({
      code: row.code,
      name: row.name,
      parentId: row.parentId ?? ROOT_PARENT_ID,
      retentionYears: row.retentionYears,
      securityLevel: row.securityLevel ?? 2,
      sort: row.sort ?? 0,
      status: row.status ?? 1,
    });
    setModalOpen(true);
  };

  const handleSubmit = async () => {
    const values = await form.validateFields();
    const payload: ArchiveCategorySaveRequest = { ...values };
    setSaving(true);
    try {
      if (editing) {
        await updateCategory(editing.id, payload);
        message.success('门类已更新');
      } else {
        await createCategory(payload);
        message.success('门类已创建');
      }
      setModalOpen(false);
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存失败');
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (row: ArchiveCategoryVO) => {
    try {
      await deleteCategory(row.id);
      message.success('门类已删除');
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '删除失败');
    }
  };

  const columns: TableProps<ArchiveCategoryVO>['columns'] = [
    { title: '门类编码', dataIndex: 'code', width: 140 },
    { title: '门类名称', dataIndex: 'name' },
    { title: '层级', dataIndex: 'level', width: 80 },
    {
      title: '默认保管期限',
      dataIndex: 'retentionYears',
      width: 130,
      render: (value?: number) => (value ? `${value} 年` : '长期'),
    },
    {
      title: '密级',
      dataIndex: 'securityLevel',
      width: 100,
      render: (value?: number) => (
        <Tag color={SECURITY_COLORS[value ?? 2]}>{securityLabel(value)}</Tag>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (value?: number) => (
        <Tag color={value === 1 ? 'green' : 'default'}>{value === 1 ? '启用' : '停用'}</Tag>
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 230,
      render: (_, row) => (
        <Space size={4}>
          {has('sys:archive:category:manage') && (
            <>
              <Button type="link" size="small" onClick={() => openCreate(row.id)}>
                新增子类
              </Button>
              <Button type="link" size="small" onClick={() => openEdit(row, true)}>
                修改
              </Button>
              <Popconfirm title="确认删除该门类？" onConfirm={() => void handleDelete(row)}>
                <Button type="link" size="small" danger>
                  删除
                </Button>
              </Popconfirm>
            </>
          )}
        </Space>
      ),
    },
  ];

  return (
    <>
      <Table<ArchiveCategoryVO>
        rowKey="id"
        title={() => (
          <Space>
            <span>档案门类</span>
            <AuthButton
              perm="sys:archive:category:manage"
              type="primary"
              size="small"
              onClick={() => openCreate()}
            >
              新增门类
            </AuthButton>
            <Button size="small" onClick={() => void load()}>
              刷新
            </Button>
          </Space>
        )}
        loading={loading}
        columns={columns}
        dataSource={tree}
        pagination={false}
      />
      <Modal
        title={editing ? '修改门类' : '新增门类'}
        open={modalOpen}
        confirmLoading={saving}
        onOk={() => void handleSubmit()}
        onCancel={() => setModalOpen(false)}
        destroyOnClose
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item
            name="code"
            label="门类编码"
            rules={[{ required: true, message: '请输入门类编码' }]}
          >
            <Input placeholder="如 GL，仅字母/数字/下划线" disabled={Boolean(editing)} />
          </Form.Item>
          <Form.Item
            name="name"
            label="门类名称"
            rules={[{ required: true, message: '请输入门类名称' }]}
          >
            <Input placeholder="如 管理类" />
          </Form.Item>
          <Form.Item name="parentId" label="上级门类">
            <Select options={parentOptions} disabled={parentLocked} />
          </Form.Item>
          <Form.Item name="retentionYears" label="默认保管期限（年，留空表示长期）">
            <InputNumber min={1} max={100} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="securityLevel" label="默认密级">
            <Select options={SECURITY_OPTIONS} />
          </Form.Item>
          <Form.Item name="sort" label="排序号">
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="status" label="状态">
            <Select
              options={[
                { label: '启用', value: 1 },
                { label: '停用', value: 0 },
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
