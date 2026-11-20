// 用户组管理页 - 列表（分页 / 新增 / 编辑 / 删除）+ 成员维护抽屉
// 权限码：sys:user:group:list(2395) / add(2396) / edit(2397) / delete(2398) / member(2399)
// DYNAMIC（动态规则）组不可手工维护成员，抽屉内禁用保存。

import {
  Alert,
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
import { pageUsers, type UserVO } from '../../../api/user';
import { useI18n } from '../../../i18n';
import {
  createUserGroup,
  deleteUserGroup,
  listUserGroupMembers,
  pageUserGroups,
  replaceUserGroupMembers,
  updateUserGroup,
  type UserGroupVO,
} from '../../../api/userGroup';

type UserGroupQuery = { keyword?: string; category?: string };

interface UserGroupFormValues {
  groupCode: string;
  groupName: string;
  category?: string;
  filtersJson?: string;
  description?: string;
  sortNo?: number;
  status?: number;
}

export default function UserGroupList() {
  const { message } = App.useApp();
  const { t } = useI18n();
  const { has } = usePermission();
  const tableRef = useRef<ProTableRef>(null);

  const categoryOptions = [
    { value: 'STATIC', label: t('systemOps.userGroup.category.static') },
    { value: 'DYNAMIC', label: t('systemOps.userGroup.category.dynamic') },
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

  const renderCategory = (category: string | undefined) => {
    if (!category) return '-';
    return category === 'DYNAMIC' ? (
      <Tag color="purple">{t('systemOps.userGroup.categoryTag.dynamic')}</Tag>
    ) : (
      <Tag color="blue">{t('systemOps.userGroup.categoryTag.static')}</Tag>
    );
  };

  const [keyword, setKeyword] = useState<string | undefined>(undefined);
  const [category, setCategory] = useState<string | undefined>(undefined);

  const [editing, setEditing] = useState<UserGroupVO | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const [formInitial, setFormInitial] = useState<Partial<UserGroupFormValues> | undefined>(
    undefined,
  );
  const [form] = Form.useForm<UserGroupFormValues>();
  const [memberForm] = Form.useForm<{ memberUserIds: string[] }>();

  // 成员维护抽屉
  const [membersOpen, setMembersOpen] = useState(false);
  const [membersRecord, setMembersRecord] = useState<UserGroupVO | null>(null);
  const [membersLoading, setMembersLoading] = useState(false);
  const [userOptions, setUserOptions] = useState<{ value: string; label: string }[]>([]);
  const [userSearching, setUserSearching] = useState(false);

  const showError = (error: unknown, fallback: string) => {
    message.error(error instanceof Error ? error.message : fallback);
  };

  const openCreate = () => {
    setEditing(null);
    setFormInitial({ category: 'STATIC', status: 1, sortNo: 0 });
    setFormOpen(true);
  };

  const openEdit = (record: UserGroupVO) => {
    setEditing(record);
    setFormInitial({
      groupCode: record.groupCode,
      groupName: record.groupName,
      category: record.category ?? 'STATIC',
      filtersJson: record.filtersJson,
      description: record.description,
      sortNo: record.sortNo ?? 0,
      status: record.status ?? 1,
    });
    setFormOpen(true);
  };

  const handleSave = async (values: UserGroupFormValues) => {
    const payload = {
      ...values,
      filtersJson: values.category === 'DYNAMIC' ? values.filtersJson || '[]' : undefined,
    };
    try {
      if (editing) {
        await updateUserGroup(editing.id, payload);
        message.success(t('systemOps.userGroup.updateSuccess'));
      } else {
        await createUserGroup(payload);
        message.success(t('systemOps.userGroup.createSuccess'));
      }
      setFormOpen(false);
      tableRef.current?.reload();
    } catch (error) {
      showError(error, t('systemOps.common.saveFailed'));
      throw error;
    }
  };

  const handleDelete = async (row: UserGroupVO) => {
    try {
      await deleteUserGroup(row.id);
      message.success(t('systemOps.userGroup.deleteSuccess'));
      tableRef.current?.reload();
    } catch (error) {
      showError(error, t('systemOps.common.deleteFailed'));
    }
  };

  const searchUsers = async (keyword: string) => {
    setUserSearching(true);
    try {
      const page = await pageUsers({ keyword: keyword || undefined, current: 1, size: 50 });
      setUserOptions(
        page.records.map((user: UserVO) => ({
          value: user.id,
          label: `${user.nickname || user.username}（${user.username}）`,
        })),
      );
    } catch (error) {
      showError(error, t('systemOps.common.searchUserFailed'));
    } finally {
      setUserSearching(false);
    }
  };

  const openMembers = (record: UserGroupVO) => {
    setMembersRecord(record);
    setMembersOpen(true);
    setMembersLoading(true);
    listUserGroupMembers(record.id)
      .then((ids) => {
        memberForm.setFieldsValue({ memberUserIds: ids });
        // 已有成员回填为选项，避免远程搜索覆盖后显示成裸 ID
        setUserOptions((prev) => {
          const known = new Map(prev.map((opt) => [opt.value, opt]));
          ids.forEach((id) => {
            if (!known.has(id)) {
              known.set(id, {
                value: id,
                label: t('systemOps.userGroup.memberOptionFallback', { id }),
              });
            }
          });
          return [...known.values()];
        });
        void searchUsers('');
      })
      .catch((error) => showError(error, t('systemOps.userGroup.memberLoadFailed')))
      .finally(() => setMembersLoading(false));
  };

  const handleSaveMembers = async () => {
    if (!membersRecord) {
      return;
    }
    try {
      const { memberUserIds } = await memberForm.validateFields();
      await replaceUserGroupMembers(membersRecord.id, memberUserIds);
      message.success(t('systemOps.userGroup.memberSaved'));
      setMembersOpen(false);
    } catch (error) {
      if (error instanceof Error) {
        showError(error, t('systemOps.userGroup.memberSaveFailed'));
      }
      // 表单校验中断（validateFields reject）时静默返回
    }
  };

  useEffect(() => {
    void searchUsers('');
    // eslint-disable-next-line react-hooks/exhaustive-deps -- 仅首挂载预加载一批用户选项
  }, []);

  const columns: TableProps<UserGroupVO>['columns'] = [
    {
      title: t('systemOps.userGroup.col.code'),
      dataIndex: 'groupCode',
      width: 160,
      ellipsis: true,
    },
    {
      title: t('systemOps.userGroup.col.name'),
      dataIndex: 'groupName',
      width: 180,
      ellipsis: true,
    },
    {
      title: t('systemOps.userGroup.col.category'),
      dataIndex: 'category',
      width: 110,
      render: (value: string | undefined) => renderCategory(value),
    },
    {
      title: t('systemOps.common.description'),
      dataIndex: 'description',
      ellipsis: true,
      render: (value: string | undefined) => value ?? '-',
    },
    { title: t('systemOps.common.sortNo'), dataIndex: 'sortNo', width: 70, align: 'center' },
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
          {has('sys:user:group:member') && row.category !== 'DYNAMIC' && (
            <Button type="link" size="small" onClick={() => openMembers(row)}>
              {t('systemOps.userGroup.action.members')}
            </Button>
          )}
          {has('sys:user:group:edit') && (
            <Button type="link" size="small" onClick={() => openEdit(row)}>
              {t('systemOps.common.edit')}
            </Button>
          )}
          {has('sys:user:group:delete') && (
            <Popconfirm
              title={t('systemOps.userGroup.confirm.delete')}
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
      <ProTable<UserGroupVO, UserGroupQuery>
        ref={tableRef}
        rowKey="id"
        title={t('systemOps.userGroup.title')}
        columns={columns}
        request={pageUserGroups}
        params={{ keyword, category }}
        onError={(error) => showError(error, t('systemOps.userGroup.loadFailed'))}
        search={
          <SearchForm
            items={[
              {
                name: 'keyword',
                label: t('systemOps.common.keyword'),
                element: (
                  <Input allowClear placeholder={t('systemOps.userGroup.search.placeholder')} />
                ),
              },
              {
                name: 'category',
                label: t('systemOps.userGroup.col.category'),
                element: (
                  <Select
                    allowClear
                    options={categoryOptions}
                    placeholder={t('systemOps.common.all')}
                    style={{ width: 160 }}
                  />
                ),
              },
            ]}
            onSearch={(values) => {
              setKeyword((values.keyword as string) || undefined);
              setCategory((values.category as string) || undefined);
            }}
            onReset={() => {
              setKeyword(undefined);
              setCategory(undefined);
            }}
          />
        }
        toolbar={
          <AuthButton perm="sys:user:group:add" type="primary" onClick={openCreate}>
            {t('systemOps.userGroup.add')}
          </AuthButton>
        }
      />

      <FormModal<UserGroupFormValues>
        open={formOpen}
        title={
          editing
            ? t('systemOps.userGroup.editTitle', { name: editing.groupName })
            : t('systemOps.userGroup.createTitle')
        }
        form={form}
        initialValues={formInitial}
        onOk={handleSave}
        onCancel={() => setFormOpen(false)}
      >
        <Form.Item
          name="groupCode"
          label={t('systemOps.userGroup.field.code')}
          rules={[{ required: true, message: t('systemOps.userGroup.field.codeRequired') }]}
        >
          <Input
            maxLength={64}
            disabled={Boolean(editing)}
            placeholder={t('systemOps.userGroup.field.codePlaceholder')}
          />
        </Form.Item>
        <Form.Item
          name="groupName"
          label={t('systemOps.userGroup.field.name')}
          rules={[{ required: true, message: t('systemOps.userGroup.field.nameRequired') }]}
        >
          <Input maxLength={64} />
        </Form.Item>
        <Form.Item
          name="category"
          label={t('systemOps.userGroup.col.category')}
          initialValue="STATIC"
          rules={[{ required: true, message: t('systemOps.userGroup.field.categoryRequired') }]}
        >
          <Select options={categoryOptions} />
        </Form.Item>
        <Form.Item noStyle shouldUpdate={(prev, next) => prev.category !== next.category}>
          {({ getFieldValue }) =>
            getFieldValue('category') === 'DYNAMIC' ? (
              <Form.Item
                name="filtersJson"
                label={t('systemOps.userGroup.field.filters')}
                extra={t('systemOps.userGroup.field.filtersExtra')}
              >
                <Input.TextArea
                  rows={3}
                  placeholder={t('systemOps.userGroup.field.filtersPlaceholder')}
                />
              </Form.Item>
            ) : null
          }
        </Form.Item>
        <Form.Item name="description" label={t('systemOps.common.description')}>
          <Input.TextArea rows={2} maxLength={255} />
        </Form.Item>
        <Space style={{ display: 'flex' }}>
          <Form.Item name="sortNo" label={t('systemOps.common.sortNo')} initialValue={0}>
            <InputNumber min={0} style={{ width: 120 }} />
          </Form.Item>
          <Form.Item name="status" label={t('systemOps.common.status')} initialValue={1}>
            <Select options={statusOptions} style={{ width: 120 }} />
          </Form.Item>
        </Space>
      </FormModal>

      <FormModal<{ memberUserIds: string[] }>
        open={membersOpen}
        form={memberForm}
        width={640}
        title={
          membersRecord
            ? t('systemOps.userGroup.membersTitle', { name: membersRecord.groupName })
            : t('systemOps.userGroup.membersCreateTitle')
        }
        onOk={handleSaveMembers}
        onCancel={() => setMembersOpen(false)}
      >
        {membersRecord?.category === 'DYNAMIC' ? (
          <Alert type="info" showIcon message={t('systemOps.userGroup.alert.dynamic')} />
        ) : (
          <Alert
            style={{ marginBottom: 12 }}
            type="warning"
            showIcon
            message={t('systemOps.userGroup.alert.replace')}
          />
        )}
        <Form.Item name="memberUserIds" label={t('systemOps.userGroup.field.members')}>
          <Select
            mode="multiple"
            showSearch
            allowClear
            filterOption={false}
            loading={userSearching || membersLoading}
            options={userOptions}
            onSearch={(keyword) => void searchUsers(keyword)}
            placeholder={t('systemOps.common.userSearchPlaceholder')}
            style={{ minHeight: 120 }}
          />
        </Form.Item>
      </FormModal>
    </>
  );
}
