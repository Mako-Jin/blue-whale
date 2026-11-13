// OA 费用类别页：类别维护 + 类别下的费用标准维护
import { useCallback, useEffect, useState } from 'react';
import {
  App,
  Button,
  Drawer,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Space,
  Switch,
  Table,
  Tag,
} from 'antd';
import type { TableProps } from 'antd';
import {
  deleteCategory,
  deleteStandard,
  listCategories,
  listStandards,
  saveCategory,
  saveStandard,
  type OaExpenseCategory,
  type OaExpenseCategoryInput,
  type OaExpenseStandard,
} from '../../api/expense';
import { hasPermission } from '../../utils/permission';

/** 类别表单值 */
interface CategoryFormValues {
  code: string;
  name: string;
  accountSubject?: string;
  requireInvoice: boolean;
  singleLimit?: number;
  sortNo?: number;
  enabled: boolean;
}

/** 标准表单值 */
interface StandardFormValues {
  levelCode: string;
  limitAmount: number;
  enabled: boolean;
  remark?: string;
}

export default function ExpenseCategoryPage() {
  const { message } = App.useApp();
  const [categories, setCategories] = useState<OaExpenseCategory[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<OaExpenseCategory | null>(null);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm<CategoryFormValues>();

  const [standards, setStandards] = useState<OaExpenseStandard[]>([]);
  const [standardOwner, setStandardOwner] = useState<OaExpenseCategory | null>(null);
  const [standardOpen, setStandardOpen] = useState(false);
  const [standardModal, setStandardModal] = useState(false);
  const [editingStandard, setEditingStandard] = useState<OaExpenseStandard | null>(null);
  const [standardForm] = Form.useForm<StandardFormValues>();

  // 后端只发放 sys:oa:expense:category:list / edit 两个权限码
  // （ExpenseCategoryController：新增/删除/编辑统一由 edit 保护）
  const canAdd = hasPermission('sys:oa:expense:category:edit');
  const canEdit = hasPermission('sys:oa:expense:category:edit');
  const canDelete = hasPermission('sys:oa:expense:category:edit');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setCategories(await listCategories());
    } catch (error) {
      message.error(error instanceof Error ? error.message : '费用类别查询失败');
    } finally {
      setLoading(false);
    }
  }, [message]);

  useEffect(() => {
    void load();
  }, [load]);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ requireInvoice: true, enabled: true, sortNo: 0 });
    setModalOpen(true);
  };

  const openEdit = (record: OaExpenseCategory) => {
    setEditing(record);
    form.resetFields();
    form.setFieldsValue({
      code: record.code,
      name: record.name,
      accountSubject: record.accountSubject,
      requireInvoice: record.requireInvoice !== false,
      singleLimit: record.singleLimit === undefined ? undefined : Number(record.singleLimit),
      sortNo: record.sortNo ?? 0,
      enabled: record.enabled !== false,
    });
    setModalOpen(true);
  };

  const handleSave = async () => {
    const values = await form.validateFields();
    const payload: OaExpenseCategoryInput = {
      id: editing?.id,
      code: values.code.trim(),
      name: values.name.trim(),
      accountSubject: values.accountSubject?.trim() || undefined,
      requireInvoice: values.requireInvoice,
      singleLimit: values.singleLimit,
      sortNo: values.sortNo ?? 0,
      enabled: values.enabled,
    };
    setSaving(true);
    try {
      await saveCategory(payload);
      message.success(editing ? '费用类别已更新' : '费用类别已创建');
      setModalOpen(false);
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '费用类别保存失败');
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (record: OaExpenseCategory) => {
    try {
      await deleteCategory(record.id);
      message.success('费用类别已删除');
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '费用类别删除失败');
    }
  };

  const openStandards = async (record: OaExpenseCategory) => {
    setStandardOwner(record);
    setStandardOpen(true);
    try {
      setStandards(await listStandards(record.id));
    } catch (error) {
      message.error(error instanceof Error ? error.message : '费用标准查询失败');
      setStandards([]);
    }
  };

  const openStandard = (record: OaExpenseStandard | null) => {
    setEditingStandard(record);
    standardForm.resetFields();
    standardForm.setFieldsValue({
      levelCode: record?.levelCode ?? 'DEFAULT',
      limitAmount: record ? Number(record.limitAmount) : 1000,
      enabled: record?.enabled !== false,
      remark: record?.remark,
    });
    setStandardModal(true);
  };

  const handleSaveStandard = async () => {
    if (!standardOwner) {
      return;
    }
    const values = await standardForm.validateFields();
    setSaving(true);
    try {
      await saveStandard({
        id: editingStandard?.id,
        categoryId: standardOwner.id,
        levelCode: values.levelCode.trim(),
        limitAmount: values.limitAmount,
        enabled: values.enabled,
        remark: values.remark?.trim() || undefined,
      });
      message.success(editingStandard ? '费用标准已更新' : '费用标准已创建');
      setStandardModal(false);
      setStandards(await listStandards(standardOwner.id));
    } catch (error) {
      message.error(error instanceof Error ? error.message : '费用标准保存失败');
    } finally {
      setSaving(false);
    }
  };

  const handleDeleteStandard = async (record: OaExpenseStandard) => {
    if (!standardOwner) {
      return;
    }
    try {
      await deleteStandard(record.id);
      message.success('费用标准已删除');
      setStandards(await listStandards(standardOwner.id));
    } catch (error) {
      message.error(error instanceof Error ? error.message : '费用标准删除失败');
    }
  };

  const columns: TableProps<OaExpenseCategory>['columns'] = [
    { title: '编码', dataIndex: 'code', key: 'code', width: 130 },
    { title: '名称', dataIndex: 'name', key: 'name', width: 150 },
    { title: '记账科目', dataIndex: 'accountSubject', key: 'accountSubject', width: 160 },
    {
      title: '需发票',
      dataIndex: 'requireInvoice',
      key: 'requireInvoice',
      width: 90,
      render: (value: boolean) => (value ? '是' : '否'),
    },
    {
      title: '单笔上限',
      dataIndex: 'singleLimit',
      key: 'singleLimit',
      width: 130,
      align: 'right',
      render: (value?: number) =>
        value === undefined || value === null ? '-' : Number(value).toFixed(2),
    },
    {
      title: '状态',
      dataIndex: 'enabled',
      key: 'enabled',
      width: 90,
      render: (value: boolean) => (
        <Tag color={value ? 'success' : 'default'}>{value ? '启用' : '停用'}</Tag>
      ),
    },
    { title: '排序', dataIndex: 'sortNo', key: 'sortNo', width: 80 },
    {
      title: '操作',
      key: 'action',
      width: 200,
      render: (_value, record) => (
        <Space size={4}>
          <Button type="link" size="small" onClick={() => void openStandards(record)}>
            标准
          </Button>
          {canEdit ? (
            <Button type="link" size="small" onClick={() => openEdit(record)}>
              编辑
            </Button>
          ) : null}
          {canDelete ? (
            <Popconfirm title="确认删除该费用类别？" onConfirm={() => void handleDelete(record)}>
              <Button type="link" size="small" danger>
                删除
              </Button>
            </Popconfirm>
          ) : null}
        </Space>
      ),
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space>
        {canAdd ? (
          <Button type="primary" onClick={openCreate}>
            新增费用类别
          </Button>
        ) : null}
        <Button loading={loading} onClick={() => void load()}>
          刷新
        </Button>
      </Space>
      <Table<OaExpenseCategory>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={categories}
        scroll={{ x: 1100 }}
        pagination={false}
      />
      <Modal
        open={modalOpen}
        title={editing ? '编辑费用类别' : '新增费用类别'}
        onOk={() => void handleSave()}
        confirmLoading={saving}
        onCancel={() => setModalOpen(false)}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item
              name="code"
              label="类别编码"
              rules={[{ required: true, message: '请输入类别编码' }]}
              style={{ flex: 1 }}
            >
              <Input maxLength={32} disabled={!!editing} />
            </Form.Item>
            <Form.Item
              name="name"
              label="类别名称"
              rules={[{ required: true, message: '请输入类别名称' }]}
              style={{ flex: 1 }}
            >
              <Input maxLength={64} />
            </Form.Item>
          </Space>
          <Form.Item name="accountSubject" label="记账科目">
            <Input maxLength={64} placeholder="选填" />
          </Form.Item>
          <Space size="large">
            <Form.Item name="requireInvoice" label="需要发票" valuePropName="checked">
              <Switch />
            </Form.Item>
            <Form.Item name="enabled" label="启用" valuePropName="checked">
              <Switch />
            </Form.Item>
            <Form.Item name="singleLimit" label="单笔上限">
              <InputNumber style={{ width: 160 }} min={0} precision={2} placeholder="留空不限制" />
            </Form.Item>
            <Form.Item name="sortNo" label="排序">
              <InputNumber style={{ width: 100 }} min={0} precision={0} />
            </Form.Item>
          </Space>
        </Form>
      </Modal>
      <Drawer
        open={standardOpen}
        width={640}
        title={`费用标准 - ${standardOwner?.name ?? ''}`}
        onClose={() => setStandardOpen(false)}
        destroyOnClose
      >
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          {canEdit ? (
            <Button type="primary" onClick={() => openStandard(null)}>
              新增标准
            </Button>
          ) : null}
          <Table<OaExpenseStandard>
            rowKey="id"
            pagination={false}
            dataSource={standards}
            columns={[
              { title: '级别编码', dataIndex: 'levelCode', key: 'levelCode', width: 140 },
              {
                title: '限额',
                dataIndex: 'limitAmount',
                key: 'limitAmount',
                width: 120,
                align: 'right',
                render: (value: number) => Number(value ?? 0).toFixed(2),
              },
              {
                title: '启用',
                dataIndex: 'enabled',
                key: 'enabled',
                width: 80,
                render: (value: boolean) => (value ? '是' : '否'),
              },
              { title: '备注', dataIndex: 'remark', key: 'remark', ellipsis: true },
              {
                title: '操作',
                key: 'action',
                width: 140,
                render: (_value, record) => (
                  <Space size={4}>
                    {canEdit ? (
                      <Button type="link" size="small" onClick={() => openStandard(record)}>
                        编辑
                      </Button>
                    ) : null}
                    {canDelete ? (
                      <Popconfirm
                        title="确认删除该标准？"
                        onConfirm={() => void handleDeleteStandard(record)}
                      >
                        <Button type="link" size="small" danger>
                          删除
                        </Button>
                      </Popconfirm>
                    ) : null}
                  </Space>
                ),
              },
            ]}
          />
        </Space>
      </Drawer>
      <Modal
        open={standardModal}
        title={editingStandard ? '编辑费用标准' : '新增费用标准'}
        onOk={() => void handleSaveStandard()}
        confirmLoading={saving}
        onCancel={() => setStandardModal(false)}
        destroyOnClose
      >
        <Form form={standardForm} layout="vertical">
          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item
              name="levelCode"
              label="级别编码"
              rules={[{ required: true, message: '请输入级别编码' }]}
              style={{ flex: 1 }}
            >
              <Input maxLength={32} placeholder="如 DEFAULT / P1" />
            </Form.Item>
            <Form.Item
              name="limitAmount"
              label="限额"
              rules={[{ required: true, message: '请输入限额' }]}
              style={{ flex: 1 }}
            >
              <InputNumber style={{ width: '100%' }} min={0} precision={2} />
            </Form.Item>
            <Form.Item name="enabled" label="启用" valuePropName="checked">
              <Switch />
            </Form.Item>
          </Space>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} maxLength={255} showCount />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}
