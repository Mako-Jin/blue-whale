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
import { useI18n } from '../../i18n';
import {
  SECURITY_COLORS,
  enabledOptions,
  flattenCategories,
  securityLabel,
  securityOptions,
} from './helpers';

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
  const { t } = useI18n();
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
      message.error(error instanceof Error ? error.message : t('archive.category.msg.loadFailed'));
    } finally {
      setLoading(false);
    }
  }, [message, t]);

  useEffect(() => {
    void load();
  }, [load]);

  const parentOptions = useMemo(
    () => [
      { label: t('archive.category.root'), value: ROOT_PARENT_ID },
      ...flattenCategories(tree).filter((item) => item.value !== editing?.id),
    ],
    [tree, editing, t],
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
        message.success(t('archive.category.msg.updated'));
      } else {
        await createCategory(payload);
        message.success(t('archive.category.msg.created'));
      }
      setModalOpen(false);
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('archive.common.saveFailed'));
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (row: ArchiveCategoryVO) => {
    try {
      await deleteCategory(row.id);
      message.success(t('archive.category.msg.deleted'));
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('archive.common.deleteFailed'));
    }
  };

  const columns: TableProps<ArchiveCategoryVO>['columns'] = [
    { title: t('archive.category.code'), dataIndex: 'code', width: 140 },
    { title: t('archive.category.name'), dataIndex: 'name' },
    { title: t('archive.category.level'), dataIndex: 'level', width: 80 },
    {
      title: t('archive.category.retention'),
      dataIndex: 'retentionYears',
      width: 130,
      render: (value?: number) =>
        value ? t('archive.common.years', { years: value }) : t('archive.common.longTerm'),
    },
    {
      title: t('archive.common.securityLevel'),
      dataIndex: 'securityLevel',
      width: 100,
      render: (value?: number) => (
        <Tag color={SECURITY_COLORS[value ?? 2]}>{securityLabel(value, t)}</Tag>
      ),
    },
    {
      title: t('archive.common.status'),
      dataIndex: 'status',
      width: 90,
      render: (value?: number) => (
        <Tag color={value === 1 ? 'green' : 'default'}>
          {value === 1 ? t('archive.common.enabled') : t('archive.common.disabled')}
        </Tag>
      ),
    },
    {
      title: t('archive.common.action'),
      key: 'action',
      width: 230,
      render: (_, row) => (
        <Space size={4}>
          {has('sys:archive:category:manage') && (
            <>
              <Button type="link" size="small" onClick={() => openCreate(row.id)}>
                {t('archive.category.createChild')}
              </Button>
              <Button type="link" size="small" onClick={() => openEdit(row, true)}>
                {t('archive.common.edit')}
              </Button>
              <Popconfirm
                title={t('archive.category.confirm.delete')}
                onConfirm={() => void handleDelete(row)}
              >
                <Button type="link" size="small" danger>
                  {t('archive.common.delete')}
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
            <span>{t('archive.category.title')}</span>
            <AuthButton
              perm="sys:archive:category:manage"
              type="primary"
              size="small"
              onClick={() => openCreate()}
            >
              {t('archive.category.create')}
            </AuthButton>
            <Button size="small" onClick={() => void load()}>
              {t('archive.common.refresh')}
            </Button>
          </Space>
        )}
        loading={loading}
        columns={columns}
        dataSource={tree}
        pagination={false}
      />
      <Modal
        title={editing ? t('archive.category.edit') : t('archive.category.create')}
        open={modalOpen}
        confirmLoading={saving}
        onOk={() => void handleSubmit()}
        onCancel={() => setModalOpen(false)}
        destroyOnClose
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item
            name="code"
            label={t('archive.category.code')}
            rules={[{ required: true, message: t('archive.category.codeRequired') }]}
          >
            <Input
              placeholder={t('archive.category.codePlaceholder')}
              disabled={Boolean(editing)}
            />
          </Form.Item>
          <Form.Item
            name="name"
            label={t('archive.category.name')}
            rules={[{ required: true, message: t('archive.category.nameRequired') }]}
          >
            <Input placeholder={t('archive.category.namePlaceholder')} />
          </Form.Item>
          <Form.Item name="parentId" label={t('archive.category.parent')}>
            <Select options={parentOptions} disabled={parentLocked} />
          </Form.Item>
          <Form.Item name="retentionYears" label={t('archive.category.retentionLabel')}>
            <InputNumber min={1} max={100} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="securityLevel" label={t('archive.category.securityLevel')}>
            <Select options={securityOptions(t)} />
          </Form.Item>
          <Form.Item name="sort" label={t('archive.category.sort')}>
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="status" label={t('archive.common.status')}>
            <Select options={enabledOptions(t)} />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
