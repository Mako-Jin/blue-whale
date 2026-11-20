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
import { useI18n } from '../../i18n';

interface PermissionFormValues {
  /** 后端 Long 序列化为字符串 */
  parentId?: string;
  permCode: string;
  permName: string;
  permType: PermissionType | string;
  path?: string;
  method?: string;
  sortNo?: number;
  status?: number;
}

type PermissionQuery = { keyword?: string; permType?: string };

/** 翻译函数类型 */
type TFunction = ReturnType<typeof useI18n>['t'];

/** 权限类型下拉选项 */
function permTypeOptions(t: TFunction): { value: PermissionType; label: string }[] {
  return [
    { value: 'SYSTEM', label: t('permission.type.system') },
    { value: 'MENU', label: t('permission.type.menu') },
    { value: 'API', label: t('permission.type.api') },
    { value: 'BUTTON', label: t('permission.type.button') },
    { value: 'DATA', label: t('permission.type.data') },
    { value: 'ATTR', label: t('permission.type.attr') },
  ];
}

/** 状态下拉选项 */
function statusOptions(t: TFunction) {
  return [
    { value: 1, label: t('permission.status.enabled') },
    { value: 0, label: t('permission.status.disabled') },
  ];
}

/** 权限类型中文名，未匹配的原样展示 */
function permTypeLabel(value: string | undefined, t: TFunction): string {
  return permTypeOptions(t).find((option) => option.value === value)?.label ?? value ?? '-';
}

export default function PermissionList() {
  const { message } = App.useApp();
  const { has } = usePermission();
  const { t } = useI18n();
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
      message.success(t('permission.msg.deleted'));
      tableRef.current?.reload();
    } catch (error) {
      showError(error, t('permission.msg.deleteFailed'));
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
        message.success(t('permission.msg.updated'));
      } else {
        await createPermission(payload);
        message.success(t('permission.msg.created'));
      }
      setEditorOpen(false);
      tableRef.current?.reload();
    } catch (error) {
      showError(
        error,
        editRecord ? t('permission.msg.updateFailed') : t('permission.msg.createFailed'),
      );
      throw error;
    }
  };

  const columns: TableProps<PermissionVO>['columns'] = [
    { title: t('permission.col.id'), dataIndex: 'id', width: 80 },
    { title: t('permission.col.code'), dataIndex: 'permCode', width: 200 },
    { title: t('permission.col.name'), dataIndex: 'permName', width: 160 },
    {
      title: t('permission.col.type'),
      dataIndex: 'permType',
      width: 100,
      render: (value: string | undefined) => <Tag color="blue">{permTypeLabel(value, t)}</Tag>,
    },
    {
      title: t('permission.col.path'),
      dataIndex: 'path',
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: t('permission.col.method'),
      dataIndex: 'method',
      width: 100,
      render: (value: string | undefined) => value ?? '-',
    },
    { title: t('permission.col.sort'), dataIndex: 'sortNo', width: 80 },
    {
      title: t('permission.col.status'),
      dataIndex: 'status',
      width: 100,
      render: (value: number | undefined) =>
        value === 1 ? (
          <Tag color="green">{t('permission.status.enabled')}</Tag>
        ) : (
          <Tag color="red">{t('permission.status.disabled')}</Tag>
        ),
    },
    {
      title: t('permission.col.action'),
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
            {t('permission.action.edit')}
          </AuthButton>
          {has('sys:permission:delete') && (
            <Popconfirm
              title={t('permission.confirm.delete')}
              onConfirm={() => void handleDelete(row)}
            >
              <Button type="link" size="small" danger>
                {t('permission.action.delete')}
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
        title={t('permission.list.title')}
        columns={columns}
        request={pagePermissions}
        params={{ keyword, permType }}
        onError={(error) => showError(error, t('permission.msg.loadFailed'))}
        search={
          <SearchForm
            items={[
              {
                name: 'keyword',
                label: t('permission.search.keyword'),
                element: (
                  <Input allowClear placeholder={t('permission.search.keywordPlaceholder')} />
                ),
              },
              {
                name: 'permType',
                label: t('permission.search.type'),
                element: (
                  <Select
                    allowClear
                    options={permTypeOptions(t)}
                    placeholder={t('permission.search.all')}
                    style={{ width: 140 }}
                  />
                ),
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
            {t('permission.action.create')}
          </AuthButton>
        }
      />

      <FormModal<PermissionFormValues>
        open={editorOpen}
        title={
          editRecord
            ? t('permission.form.editTitle', { name: editRecord.permName })
            : t('permission.form.createTitle')
        }
        form={editorForm}
        initialValues={editRecord ?? undefined}
        onOk={handleSubmit}
        onCancel={() => setEditorOpen(false)}
      >
        <Form.Item name="parentId" label={t('permission.form.parentId')}>
          <Input placeholder={t('permission.form.parentIdPlaceholder')} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item
          name="permCode"
          label={t('permission.form.code')}
          rules={[{ required: true, message: t('permission.form.codeRequired') }]}
        >
          <Input placeholder={t('permission.form.codePlaceholder')} />
        </Form.Item>
        <Form.Item
          name="permName"
          label={t('permission.form.name')}
          rules={[{ required: true, message: t('permission.form.nameRequired') }]}
        >
          <Input />
        </Form.Item>
        <Form.Item
          name="permType"
          label={t('permission.form.type')}
          rules={[{ required: true, message: t('permission.form.typeRequired') }]}
        >
          <Select
            options={permTypeOptions(t)}
            placeholder={t('permission.form.typePlaceholder')}
          />
        </Form.Item>
        <Form.Item name="path" label={t('permission.form.path')}>
          <Input placeholder={t('permission.form.pathPlaceholder')} />
        </Form.Item>
        <Form.Item name="method" label={t('permission.form.method')}>
          <Input placeholder={t('permission.form.methodPlaceholder')} />
        </Form.Item>
        <Form.Item name="sortNo" label={t('permission.form.sort')} initialValue={1}>
          <InputNumber min={0} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item name="status" label={t('permission.form.status')} initialValue={1}>
          <Select options={statusOptions(t)} />
        </Form.Item>
      </FormModal>
    </>
  );
}
