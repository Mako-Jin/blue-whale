// 权限策略管理页 - 列表（分页 / 新增 / 编辑 / 删除 / 设默认）
// 权限码：sys:perm:strategy:list(2400) / add(2401) / edit(2402) / delete(2403) / set-default(2404)

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
  createPermStrategy,
  deletePermStrategy,
  pagePermStrategies,
  setDefaultPermStrategy,
  updatePermStrategy,
  type PermStrategyVO,
} from '../../../api/permStrategy';

type StrategyQuery = { keyword?: string };

interface StrategyFormValues {
  strategyCode: string;
  strategyName: string;
  strategyType: string;
  roleIds?: string[];
  resourceIds?: string[];
  conditionJson?: string;
  isDefault?: boolean;
  priority?: number;
  status?: number;
  description?: string;
}

export default function PermStrategyList() {
  const { message } = App.useApp();
  const { t } = useI18n();
  const { has } = usePermission();
  const tableRef = useRef<ProTableRef>(null);

  const typeOptions = [
    { value: 'GRANT', label: t('systemOps.permStrategy.type.grant') },
    { value: 'DENY', label: t('systemOps.permStrategy.type.deny') },
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

  const [editing, setEditing] = useState<PermStrategyVO | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const [formInitial, setFormInitial] = useState<Partial<StrategyFormValues> | undefined>(
    undefined,
  );
  const [form] = Form.useForm<StrategyFormValues>();

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
    setFormInitial({ strategyType: 'GRANT', status: 1, priority: 0, roleIds: [], resourceIds: [] });
    setFormOpen(true);
  };

  const openEdit = (record: PermStrategyVO) => {
    setEditing(record);
    setFormInitial({
      strategyCode: record.strategyCode,
      strategyName: record.strategyName,
      strategyType: record.strategyType,
      roleIds: record.roleIds ?? [],
      resourceIds: record.resourceIds ?? [],
      conditionJson: record.conditionJson,
      isDefault: record.defaultStrategy,
      priority: record.priority ?? 0,
      status: record.status ?? 1,
      description: record.description,
    });
    setFormOpen(true);
  };

  const handleSave = async (values: StrategyFormValues) => {
    const payload = {
      ...values,
      resourceIds: values.resourceIds ?? [],
    };
    try {
      if (editing) {
        await updatePermStrategy(editing.id, payload);
        message.success(t('systemOps.permStrategy.updateSuccess'));
      } else {
        await createPermStrategy(payload);
        message.success(t('systemOps.permStrategy.createSuccess'));
      }
      setFormOpen(false);
      tableRef.current?.reload();
    } catch (error) {
      showError(error, t('systemOps.common.saveFailed'));
      throw error;
    }
  };

  const handleDelete = async (row: PermStrategyVO) => {
    try {
      await deletePermStrategy(row.id);
      message.success(t('systemOps.permStrategy.deleteSuccess'));
      tableRef.current?.reload();
    } catch (error) {
      showError(error, t('systemOps.common.deleteFailed'));
    }
  };

  const handleSetDefault = async (row: PermStrategyVO) => {
    try {
      await setDefaultPermStrategy(row.id);
      message.success(t('systemOps.permStrategy.setDefaultSuccess'));
      tableRef.current?.reload();
    } catch (error) {
      showError(error, t('systemOps.permStrategy.setDefaultFailed'));
    }
  };

  const columns: TableProps<PermStrategyVO>['columns'] = [
    {
      title: t('systemOps.permStrategy.col.code'),
      dataIndex: 'strategyCode',
      width: 160,
      ellipsis: true,
    },
    {
      title: t('systemOps.permStrategy.col.name'),
      dataIndex: 'strategyName',
      width: 160,
      ellipsis: true,
    },
    {
      title: t('systemOps.common.type'),
      dataIndex: 'strategyType',
      width: 100,
      render: (value: string | undefined) =>
        value === 'DENY' ? (
          <Tag color="red">{t('systemOps.permStrategy.typeTag.deny')}</Tag>
        ) : (
          <Tag color="green">{t('systemOps.permStrategy.typeTag.grant')}</Tag>
        ),
    },
    {
      title: t('systemOps.permStrategy.col.default'),
      dataIndex: 'defaultStrategy',
      width: 70,
      render: (value: boolean | undefined) =>
        value ? <Tag color="gold">{t('systemOps.permStrategy.defaultTag')}</Tag> : '-',
    },
    {
      title: t('systemOps.permStrategy.col.roles'),
      dataIndex: 'roleIds',
      width: 120,
      render: (value: string[] | undefined) =>
        value?.length ? t('systemOps.common.rolesCount', { count: value.length }) : '-',
    },
    {
      title: t('systemOps.permStrategy.col.priority'),
      dataIndex: 'priority',
      width: 80,
      align: 'center',
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
      width: 200,
      render: (_, row) => (
        <Space size={4}>
          {has('sys:perm:strategy:set-default') && !row.defaultStrategy && (
            <Button type="link" size="small" onClick={() => void handleSetDefault(row)}>
              {t('systemOps.permStrategy.action.setDefault')}
            </Button>
          )}
          {has('sys:perm:strategy:edit') && (
            <Button type="link" size="small" onClick={() => openEdit(row)}>
              {t('systemOps.common.edit')}
            </Button>
          )}
          {has('sys:perm:strategy:delete') && (
            <Popconfirm
              title={t('systemOps.permStrategy.confirm.delete')}
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
      <ProTable<PermStrategyVO, StrategyQuery>
        ref={tableRef}
        rowKey="id"
        title={t('systemOps.permStrategy.title')}
        columns={columns}
        request={pagePermStrategies}
        params={{ keyword }}
        onError={(error) => showError(error, t('systemOps.permStrategy.loadFailed'))}
        search={
          <SearchForm
            items={[
              {
                name: 'keyword',
                label: t('systemOps.common.keyword'),
                element: (
                  <Input allowClear placeholder={t('systemOps.permStrategy.search.placeholder')} />
                ),
              },
            ]}
            onSearch={(values) => setKeyword((values.keyword as string) || undefined)}
            onReset={() => setKeyword(undefined)}
          />
        }
        toolbar={
          <AuthButton perm="sys:perm:strategy:add" type="primary" onClick={openCreate}>
            {t('systemOps.permStrategy.add')}
          </AuthButton>
        }
      />

      <FormModal<StrategyFormValues>
        open={formOpen}
        title={
          editing
            ? t('systemOps.permStrategy.editTitle', { name: editing.strategyName })
            : t('systemOps.permStrategy.createTitle')
        }
        form={form}
        initialValues={formInitial}
        width={640}
        onOk={handleSave}
        onCancel={() => setFormOpen(false)}
      >
        <Space style={{ display: 'flex' }}>
          <Form.Item
            name="strategyCode"
            label={t('systemOps.permStrategy.field.code')}
            rules={[{ required: true, message: t('systemOps.permStrategy.field.codeRequired') }]}
          >
            <Input maxLength={64} disabled={Boolean(editing)} style={{ width: 200 }} />
          </Form.Item>
          <Form.Item
            name="strategyName"
            label={t('systemOps.permStrategy.field.name')}
            rules={[{ required: true, message: t('systemOps.permStrategy.field.nameRequired') }]}
          >
            <Input maxLength={64} style={{ width: 200 }} />
          </Form.Item>
        </Space>
        <Space style={{ display: 'flex' }}>
          <Form.Item
            name="strategyType"
            label={t('systemOps.common.type')}
            initialValue="GRANT"
            rules={[{ required: true, message: t('systemOps.permStrategy.field.typeRequired') }]}
          >
            <Select options={typeOptions} style={{ width: 180 }} />
          </Form.Item>
          <Form.Item name="status" label={t('systemOps.common.status')} initialValue={1}>
            <Select options={statusOptions} style={{ width: 120 }} />
          </Form.Item>
          <Form.Item
            name="priority"
            label={t('systemOps.permStrategy.col.priority')}
            initialValue={0}
          >
            <InputNumber min={0} style={{ width: 100 }} />
          </Form.Item>
        </Space>
        <Form.Item
          name="roleIds"
          label={t('systemOps.permStrategy.field.roles')}
          extra={t('systemOps.permStrategy.field.rolesExtra')}
        >
          <Select
            mode="multiple"
            allowClear
            options={roleOptions}
            placeholder={t('systemOps.common.selectRole')}
            style={{ minHeight: 80 }}
          />
        </Form.Item>
        <Form.Item
          name="resourceIds"
          label={t('systemOps.permStrategy.field.resourceIds')}
          extra={t('systemOps.permStrategy.field.resourceIdsExtra')}
        >
          <Select
            mode="tags"
            allowClear
            open={false}
            placeholder={t('systemOps.permStrategy.field.resourceIdsPlaceholder')}
            style={{ minHeight: 60 }}
          />
        </Form.Item>
        <Form.Item
          name="conditionJson"
          label={t('systemOps.permStrategy.field.conditionJson')}
          extra={t('systemOps.permStrategy.field.conditionJsonExtra')}
        >
          <Input.TextArea rows={3} placeholder='{"deptScope":"SELF"}' />
        </Form.Item>
        <Form.Item
          name="isDefault"
          label={t('systemOps.permStrategy.field.isDefault')}
          valuePropName="checked"
          extra={t('systemOps.permStrategy.field.isDefaultExtra')}
        >
          <Select
            options={[
              { value: true, label: t('systemOps.common.yes') },
              { value: false, label: t('systemOps.common.no') },
            ]}
            style={{ width: 120 }}
          />
        </Form.Item>
        <Form.Item name="description" label={t('systemOps.common.description')}>
          <Input.TextArea rows={2} maxLength={255} />
        </Form.Item>
      </FormModal>
    </>
  );
}
