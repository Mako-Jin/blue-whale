// 角色约束管理页 - 列表（分页 / 新增 / 编辑 / 删除）
// 权限码：sys:role:constraint:list(2405) / add(2406) / edit(2407) / delete(2408)
// MUTEX = 互斥（约束内角色不可同时持有）；CARDINALITY = 基数（最多持有 maxAssign 个）

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
import { useEffect, useRef, useState } from 'react';
import {
  AuthButton,
  FormModal,
  ProTable,
  SearchForm,
  usePermission,
  type ProTableRef,
} from '@bluewhale/whale-ui';
import { pageRoles, type RoleVO } from '../../../api/role';
import { useI18n } from '../../../i18n';
import {
  createRoleConstraint,
  deleteRoleConstraint,
  pageRoleConstraints,
  updateRoleConstraint,
  type RoleConstraintVO,
} from '../../../api/roleConstraint';

type ConstraintQuery = { keyword?: string; constraintType?: string };

interface ConstraintFormValues {
  constraintCode: string;
  constraintName: string;
  constraintType: string;
  roleIds?: string[];
  maxAssign?: number;
  status?: number;
  description?: string;
}

export default function RoleConstraintList() {
  const { message } = App.useApp();
  const { t } = useI18n();
  const { has } = usePermission();
  const tableRef = useRef<ProTableRef>(null);

  const typeOptions = [
    { value: 'MUTEX', label: t('systemOps.roleConstraint.type.mutex') },
    { value: 'CARDINALITY', label: t('systemOps.roleConstraint.type.cardinality') },
  ];

  const statusOptions = [
    { value: 1, label: t('systemOps.common.enabled') },
    { value: 0, label: t('systemOps.common.disabled') },
  ];

  const renderStatus = (status: number | undefined) =>
    status === 0 ? (
      <Tag color="default">{t('systemOps.common.disabled')}</Tag>
    ) : (
      <Tag color="success">{t('systemOps.common.enabled')}</Tag>
    );

  const [keyword, setKeyword] = useState<string | undefined>(undefined);
  const [constraintType, setConstraintType] = useState<string | undefined>(undefined);

  const [editing, setEditing] = useState<RoleConstraintVO | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const [formInitial, setFormInitial] = useState<Partial<ConstraintFormValues> | undefined>(
    undefined,
  );
  const [form] = Form.useForm<ConstraintFormValues>();

  const [roleOptions, setRoleOptions] = useState<{ value: string; label: string }[]>([]);

  const showError = (error: unknown, fallback: string) => {
    message.error(error instanceof Error ? error.message : fallback);
  };

  const loadRoles = () => {
    pageRoles({ current: 1, size: 100 })
      .then((page) => {
        setRoleOptions(
          page.records.map((role: RoleVO) => ({
            value: role.id,
            label: `${role.roleName}（${role.roleCode}）`,
          })),
        );
      })
      .catch((error) => showError(error, t('systemOps.common.loadRolesFailed')));
  };

  useEffect(() => {
    loadRoles();
    // eslint-disable-next-line react-hooks/exhaustive-deps -- 仅首挂载加载角色选项
  }, []);

  const openCreate = () => {
    setEditing(null);
    setFormInitial({ constraintType: 'MUTEX', status: 1, roleIds: [] });
    setFormOpen(true);
  };

  const openEdit = (record: RoleConstraintVO) => {
    setEditing(record);
    setFormInitial({
      constraintCode: record.constraintCode,
      constraintName: record.constraintName,
      constraintType: record.constraintType,
      roleIds: record.roleIds ?? [],
      maxAssign: record.maxAssign,
      status: record.status ?? 1,
      description: record.description,
    });
    setFormOpen(true);
  };

  const handleSave = async (values: ConstraintFormValues) => {
    try {
      if (editing) {
        await updateRoleConstraint(editing.id, values);
        message.success(t('systemOps.roleConstraint.updateSuccess'));
      } else {
        await createRoleConstraint(values);
        message.success(t('systemOps.roleConstraint.createSuccess'));
      }
      setFormOpen(false);
      tableRef.current?.reload();
    } catch (error) {
      showError(error, t('systemOps.common.saveFailed'));
      throw error;
    }
  };

  const handleDelete = async (row: RoleConstraintVO) => {
    try {
      await deleteRoleConstraint(row.id);
      message.success(t('systemOps.roleConstraint.deleteSuccess'));
      tableRef.current?.reload();
    } catch (error) {
      showError(error, t('systemOps.common.deleteFailed'));
    }
  };

  const columns: TableProps<RoleConstraintVO>['columns'] = [
    {
      title: t('systemOps.roleConstraint.col.code'),
      dataIndex: 'constraintCode',
      width: 160,
      ellipsis: true,
    },
    {
      title: t('systemOps.roleConstraint.col.name'),
      dataIndex: 'constraintName',
      width: 160,
      ellipsis: true,
    },
    {
      title: t('systemOps.common.type'),
      dataIndex: 'constraintType',
      width: 130,
      render: (value: string | undefined) =>
        value === 'CARDINALITY' ? (
          <Tag color="purple">{t('systemOps.roleConstraint.typeTag.cardinality')}</Tag>
        ) : (
          <Tag color="volcano">{t('systemOps.roleConstraint.typeTag.mutex')}</Tag>
        ),
    },
    {
      title: t('systemOps.roleConstraint.col.roles'),
      dataIndex: 'roleIds',
      width: 100,
      render: (value: string[] | undefined) =>
        value?.length ? t('systemOps.common.rolesCount', { count: value.length }) : '-',
    },
    {
      title: t('systemOps.roleConstraint.col.maxAssign'),
      dataIndex: 'maxAssign',
      width: 90,
      align: 'center',
      render: (value: number | undefined, row) =>
        row.constraintType === 'CARDINALITY' && value ? value : '-',
    },
    {
      title: t('systemOps.common.status'),
      dataIndex: 'status',
      width: 80,
      render: (value: number | undefined) => renderStatus(value),
    },
    {
      title: t('systemOps.common.updateTime'),
      dataIndex: 'updateTime',
      width: 170,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: t('systemOps.common.action'),
      key: 'action',
      width: 140,
      render: (_, row) => (
        <Space size={4}>
          {has('sys:role:constraint:edit') && (
            <Button type="link" size="small" onClick={() => openEdit(row)}>
              {t('systemOps.common.edit')}
            </Button>
          )}
          {has('sys:role:constraint:delete') && (
            <Popconfirm
              title={t('systemOps.roleConstraint.confirm.delete')}
              onConfirm={() => void handleDelete(row)}
            >
              <Button type="link" size="small" danger>
                {t('systemOps.common.delete')}
              </Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  return (
    <>
      <ProTable<RoleConstraintVO, ConstraintQuery>
        ref={tableRef}
        rowKey="id"
        title={t('systemOps.roleConstraint.title')}
        columns={columns}
        request={pageRoleConstraints}
        params={{ keyword, constraintType }}
        onError={(error) => showError(error, t('systemOps.roleConstraint.loadFailed'))}
        search={
          <SearchForm
            items={[
              {
                name: 'keyword',
                label: t('systemOps.common.keyword'),
                element: (
                  <Input
                    allowClear
                    placeholder={t('systemOps.roleConstraint.search.placeholder')}
                  />
                ),
              },
              {
                name: 'constraintType',
                label: t('systemOps.common.type'),
                element: (
                  <Select
                    allowClear
                    options={typeOptions}
                    placeholder={t('systemOps.common.all')}
                    style={{ width: 180 }}
                  />
                ),
              },
            ]}
            onSearch={(values) => {
              setKeyword((values.keyword as string) || undefined);
              setConstraintType((values.constraintType as string) || undefined);
            }}
            onReset={() => {
              setKeyword(undefined);
              setConstraintType(undefined);
            }}
          />
        }
        toolbar={
          <AuthButton perm="sys:role:constraint:add" type="primary" onClick={openCreate}>
            {t('systemOps.roleConstraint.add')}
          </AuthButton>
        }
      />

      <FormModal<ConstraintFormValues>
        open={formOpen}
        title={
          editing
            ? t('systemOps.roleConstraint.editTitle', { name: editing.constraintName })
            : t('systemOps.roleConstraint.createTitle')
        }
        form={form}
        initialValues={formInitial}
        width={640}
        onOk={handleSave}
        onCancel={() => setFormOpen(false)}
      >
        <Space style={{ display: 'flex' }}>
          <Form.Item
            name="constraintCode"
            label={t('systemOps.roleConstraint.field.code')}
            rules={[
              { required: true, message: t('systemOps.roleConstraint.field.codeRequired') },
            ]}
          >
            <Input maxLength={64} disabled={Boolean(editing)} style={{ width: 200 }} />
          </Form.Item>
          <Form.Item
            name="constraintName"
            label={t('systemOps.roleConstraint.field.name')}
            rules={[
              { required: true, message: t('systemOps.roleConstraint.field.nameRequired') },
            ]}
          >
            <Input maxLength={64} style={{ width: 200 }} />
          </Form.Item>
        </Space>
        <Space style={{ display: 'flex' }}>
          <Form.Item
            name="constraintType"
            label={t('systemOps.common.type')}
            initialValue="MUTEX"
            rules={[{ required: true, message: t('systemOps.roleConstraint.field.typeRequired') }]}
          >
            <Select options={typeOptions} style={{ width: 180 }} />
          </Form.Item>
          <Form.Item name="status" label={t('systemOps.common.status')} initialValue={1}>
            <Select options={statusOptions} style={{ width: 120 }} />
          </Form.Item>
          <Form.Item
            noStyle
            shouldUpdate={(prev, next) => prev.constraintType !== next.constraintType}
          >
            {({ getFieldValue }) =>
              getFieldValue('constraintType') === 'CARDINALITY' ? (
                <Form.Item
                  name="maxAssign"
                  label={t('systemOps.roleConstraint.field.maxAssign')}
                  extra={t('systemOps.roleConstraint.field.maxAssignExtra')}
                >
                  <InputNumber min={1} style={{ width: 120 }} />
                </Form.Item>
              ) : null
            }
          </Form.Item>
        </Space>
        <Form.Item
          name="roleIds"
          label={t('systemOps.roleConstraint.field.roles')}
          rules={[{ required: true, message: t('systemOps.roleConstraint.field.rolesRequired') }]}
          extra={t('systemOps.roleConstraint.field.rolesExtra')}
        >
          <Select
            mode="multiple"
            allowClear
            options={roleOptions}
            placeholder={t('systemOps.common.selectRole')}
            style={{ minHeight: 80 }}
          />
        </Form.Item>
        <Form.Item name="description" label={t('systemOps.common.description')}>
          <Input.TextArea rows={2} maxLength={255} />
        </Form.Item>
      </FormModal>
    </>
  );
}
