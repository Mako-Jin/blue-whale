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

const CATEGORY_OPTIONS = [
  { value: 'STATIC', label: '静态 STATIC' },
  { value: 'DYNAMIC', label: '动态 DYNAMIC' },
];

const STATUS_OPTIONS = [
  { value: 1, label: '启用' },
  { value: 0, label: '停用' },
];

function renderStatus(status: number | undefined) {
  return status === 0 ? <Tag color="default">停用</Tag> : <Tag color="success">启用</Tag>;
}

function renderCategory(category: string | undefined) {
  if (!category) return '-';
  return category === 'DYNAMIC' ? <Tag color="purple">动态规则</Tag> : <Tag color="blue">静态</Tag>;
}

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
  const { has } = usePermission();
  const tableRef = useRef<ProTableRef>(null);

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
        message.success('用户组已更新');
      } else {
        await createUserGroup(payload);
        message.success('用户组已创建');
      }
      setFormOpen(false);
      tableRef.current?.reload();
    } catch (error) {
      showError(error, '保存失败');
      throw error;
    }
  };

  const handleDelete = async (row: UserGroupVO) => {
    try {
      await deleteUserGroup(row.id);
      message.success('用户组已删除');
      tableRef.current?.reload();
    } catch (error) {
      showError(error, '删除失败');
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
      showError(error, '用户搜索失败');
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
              known.set(id, { value: id, label: `用户 ${id}` });
            }
          });
          return [...known.values()];
        });
        void searchUsers('');
      })
      .catch((error) => showError(error, '成员加载失败'))
      .finally(() => setMembersLoading(false));
  };

  const handleSaveMembers = async () => {
    if (!membersRecord) {
      return;
    }
    try {
      const { memberUserIds } = await memberForm.validateFields();
      await replaceUserGroupMembers(membersRecord.id, memberUserIds);
      message.success('成员已保存');
      setMembersOpen(false);
    } catch (error) {
      if (error instanceof Error) {
        showError(error, '成员保存失败');
      }
      // 表单校验中断（validateFields reject）时静默返回
    }
  };

  useEffect(() => {
    void searchUsers('');
    // eslint-disable-next-line react-hooks/exhaustive-deps -- 仅首挂载预加载一批用户选项
  }, []);

  const columns: TableProps<UserGroupVO>['columns'] = [
    { title: '组编码', dataIndex: 'groupCode', width: 160, ellipsis: true },
    { title: '组名称', dataIndex: 'groupName', width: 180, ellipsis: true },
    {
      title: '类别',
      dataIndex: 'category',
      width: 110,
      render: (value: string | undefined) => renderCategory(value),
    },
    {
      title: '说明',
      dataIndex: 'description',
      ellipsis: true,
      render: (value: string | undefined) => value ?? '-',
    },
    { title: '排序', dataIndex: 'sortNo', width: 70, align: 'center' },
    {
      title: '状态',
      dataIndex: 'status',
      width: 80,
      render: (value: number | undefined) => renderStatus(value),
    },
    {
      title: '更新时间',
      dataIndex: 'updateTime',
      width: 170,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: '操作',
      key: 'action',
      width: 200,
      render: (_, row) => (
        <Space size={4}>
          {has('sys:user:group:member') && row.category !== 'DYNAMIC' && (
            <Button type="link" size="small" onClick={() => openMembers(row)}>
              成员
            </Button>
          )}
          {has('sys:user:group:edit') && (
            <Button type="link" size="small" onClick={() => openEdit(row)}>
              编辑
            </Button>
          )}
          {has('sys:user:group:delete') && (
            <Popconfirm title="确认删除该用户组？" onConfirm={() => void handleDelete(row)}>
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
      <ProTable<UserGroupVO, UserGroupQuery>
        ref={tableRef}
        rowKey="id"
        title="用户组"
        columns={columns}
        request={pageUserGroups}
        params={{ keyword, category }}
        onError={(error) => showError(error, '用户组加载失败')}
        search={
          <SearchForm
            items={[
              {
                name: 'keyword',
                label: '关键字',
                element: <Input allowClear placeholder="组编码 / 组名称 / 说明" />,
              },
              {
                name: 'category',
                label: '类别',
                element: (
                  <Select
                    allowClear
                    options={CATEGORY_OPTIONS}
                    placeholder="全部"
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
            新增用户组
          </AuthButton>
        }
      />

      <FormModal<UserGroupFormValues>
        open={formOpen}
        title={editing ? `编辑用户组：${editing.groupName}` : '新增用户组'}
        form={form}
        initialValues={formInitial}
        onOk={handleSave}
        onCancel={() => setFormOpen(false)}
      >
        <Form.Item
          name="groupCode"
          label="组编码"
          rules={[{ required: true, message: '请输入组编码' }]}
        >
          <Input maxLength={64} disabled={Boolean(editing)} placeholder="租户内唯一" />
        </Form.Item>
        <Form.Item
          name="groupName"
          label="组名称"
          rules={[{ required: true, message: '请输入组名称' }]}
        >
          <Input maxLength={64} />
        </Form.Item>
        <Form.Item
          name="category"
          label="类别"
          initialValue="STATIC"
          rules={[{ required: true, message: '请选择类别' }]}
        >
          <Select options={CATEGORY_OPTIONS} />
        </Form.Item>
        <Form.Item noStyle shouldUpdate={(prev, next) => prev.category !== next.category}>
          {({ getFieldValue }) =>
            getFieldValue('category') === 'DYNAMIC' ? (
              <Form.Item
                name="filtersJson"
                label="动态规则"
                extra='JSON 规则，如 [{"field":"deptId","op":"eq","value":5}]'
              >
                <Input.TextArea rows={3} placeholder='[{"field":"deptId","op":"eq","value":5}]' />
              </Form.Item>
            ) : null
          }
        </Form.Item>
        <Form.Item name="description" label="说明">
          <Input.TextArea rows={2} maxLength={255} />
        </Form.Item>
        <Space style={{ display: 'flex' }}>
          <Form.Item name="sortNo" label="排序" initialValue={0}>
            <InputNumber min={0} style={{ width: 120 }} />
          </Form.Item>
          <Form.Item name="status" label="状态" initialValue={1}>
            <Select options={STATUS_OPTIONS} style={{ width: 120 }} />
          </Form.Item>
        </Space>
      </FormModal>

      <FormModal<{ memberUserIds: string[] }>
        open={membersOpen}
        form={memberForm}
        width={640}
        title={membersRecord ? `维护成员：${membersRecord.groupName}` : '维护成员'}
        onOk={handleSaveMembers}
        onCancel={() => setMembersOpen(false)}
      >
        {membersRecord?.category === 'DYNAMIC' ? (
          <Alert
            type="info"
            showIcon
            message="动态规则组按 filters 规则自动圈人，不可手工维护成员"
          />
        ) : (
          <Alert
            style={{ marginBottom: 12 }}
            type="warning"
            showIcon
            message="成员为全量替换：保存后以当前选中列表为准"
          />
        )}
        <Form.Item name="memberUserIds" label="成员">
          <Select
            mode="multiple"
            showSearch
            allowClear
            filterOption={false}
            loading={userSearching || membersLoading}
            options={userOptions}
            onSearch={(keyword) => void searchUsers(keyword)}
            placeholder="输入关键字搜索用户（昵称 / 用户名）"
            style={{ minHeight: 120 }}
          />
        </Form.Item>
      </FormModal>
    </>
  );
}
