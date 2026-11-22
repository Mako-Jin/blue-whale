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
import { useI18n } from '../../i18n';

interface MenuFormValues extends MenuPayload {
  name: string;
}

/** 翻译函数类型 */
type TFunction = ReturnType<typeof useI18n>['t'];

interface TreeOption {
  /** 菜单 id 为后端 Long（字符串） */
  value: string;
  title: string;
  children?: TreeOption[];
}

/** 菜单类型下拉选项 */
function menuTypeOptions(t: TFunction): { value: MenuType; label: string }[] {
  return [
    { value: 'DIR', label: t('menu.type.dir') },
    { value: 'MENU', label: t('menu.type.menu') },
  ];
}

/** 是否下拉选项 */
function flagOptions(t: TFunction) {
  return [
    { value: 1, label: t('menu.flag.yes') },
    { value: 0, label: t('menu.flag.no') },
  ];
}

/** 状态下拉选项 */
function statusOptions(t: TFunction) {
  return [
    { value: 1, label: t('menu.status.enabled') },
    { value: 0, label: t('menu.status.disabled') },
  ];
}

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
  const { t } = useI18n();

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
      message.error(error instanceof Error ? error.message : t('menu.msg.loadFailed'));
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
      message.success(t('menu.msg.deleted'));
      void load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('menu.msg.deleteFailed'));
    }
  };

  const handleSubmit = async (values: MenuFormValues) => {
    try {
      const payload: MenuPayload = { ...values, parentId: values.parentId ?? '0' };
      if (editRecord) {
        await updateMenu(editRecord.id, payload);
        message.success(t('menu.msg.updated'));
      } else {
        await createMenu(payload);
        message.success(t('menu.msg.created'));
      }
      setEditorOpen(false);
      void load();
    } catch (error) {
      message.error(
        error instanceof Error
          ? error.message
          : editRecord
            ? t('menu.msg.updateFailed')
            : t('menu.msg.createFailed'),
      );
      throw error;
    }
  };

  const columns: TableProps<MenuVO>['columns'] = [
    { title: t('menu.col.name'), dataIndex: 'name', width: 180 },
    {
      title: t('menu.col.type'),
      dataIndex: 'menuType',
      width: 90,
      render: (value: string | undefined) =>
        value === 'DIR' ? (
          <Tag color="blue">{t('menu.type.dir')}</Tag>
        ) : (
          <Tag color="geekblue">{t('menu.type.menu')}</Tag>
        ),
    },
    { title: t('menu.col.path'), dataIndex: 'path', width: 160 },
    { title: t('menu.col.component'), dataIndex: 'component', width: 180 },
    { title: t('menu.col.icon'), dataIndex: 'icon', width: 100 },
    { title: t('menu.col.sort'), dataIndex: 'sort', width: 80 },
    { title: t('menu.col.permissionCode'), dataIndex: 'permissionCode', width: 160 },
    {
      title: t('menu.col.visible'),
      dataIndex: 'visible',
      width: 80,
      render: (value: number | undefined) => (value === 1 ? t('menu.flag.yes') : t('menu.flag.no')),
    },
    {
      title: t('menu.col.status'),
      dataIndex: 'status',
      width: 90,
      render: (value: number | undefined) =>
        value === 1 ? (
          <Tag color="green">{t('menu.status.enabled')}</Tag>
        ) : (
          <Tag color="red">{t('menu.status.disabled')}</Tag>
        ),
    },
    {
      title: t('menu.col.action'),
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
            {t('menu.action.edit')}
          </AuthButton>
          {has('sys:menu:delete') && (
            <Popconfirm title={t('menu.confirm.delete')} onConfirm={() => void handleDelete(row)}>
              <Button type="link" size="small" danger>
                {t('menu.action.delete')}
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
            <span>{t('menu.list.title')}</span>
            <AuthButton
              perm="sys:menu:add"
              type="primary"
              size="small"
              onClick={() => {
                setEditRecord(null);
                setEditorOpen(true);
              }}
            >
              {t('menu.action.create')}
            </AuthButton>
          </Space>
        )}
      />

      <FormModal<MenuFormValues>
        open={editorOpen}
        title={
          editRecord
            ? t('menu.form.editTitle', { name: editRecord.name })
            : t('menu.form.createTitle')
        }
        form={editorForm}
        initialValues={editRecord ?? undefined}
        onOk={handleSubmit}
        onCancel={() => setEditorOpen(false)}
      >
        <Form.Item name="parentId" label={t('menu.form.parent')}>
          <TreeSelect
            allowClear
            treeDefaultExpandAll
            placeholder={t('menu.form.parentPlaceholder')}
            treeData={[
              {
                value: '0',
                title: t('menu.form.topLevel'),
                children: toOptions(menus, editRecord?.id),
              },
            ]}
          />
        </Form.Item>
        <Form.Item
          name="name"
          label={t('menu.form.name')}
          rules={[{ required: true, message: t('menu.form.nameRequired') }]}
        >
          <Input />
        </Form.Item>
        <Form.Item name="menuType" label={t('menu.form.type')} initialValue="MENU">
          <Select options={menuTypeOptions(t)} />
        </Form.Item>
        <Form.Item name="path" label={t('menu.form.path')}>
          <Input placeholder={t('menu.form.pathPlaceholder')} />
        </Form.Item>
        <Form.Item name="component" label={t('menu.form.component')}>
          <Input placeholder={t('menu.form.componentPlaceholder')} />
        </Form.Item>
        <Form.Item name="icon" label={t('menu.form.icon')}>
          <Input />
        </Form.Item>
        <Form.Item name="sort" label={t('menu.form.sort')} initialValue={1}>
          <InputNumber min={0} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item name="permissionCode" label={t('menu.col.permissionCode')}>
          <Input placeholder={t('menu.form.permissionPlaceholder')} />
        </Form.Item>
        <Form.Item name="visible" label={t('menu.form.visible')} initialValue={1}>
          <Select options={flagOptions(t)} />
        </Form.Item>
        <Form.Item name="status" label={t('menu.form.status')} initialValue={1}>
          <Select options={statusOptions(t)} />
        </Form.Item>
      </FormModal>
    </>
  );
}
