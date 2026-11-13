// 视频运营 - 创作者管理（M4）：分页 / 资料编辑 / 启停 / 头像维护
// 权限码：sys:video:creator:manage；停用后创作者不得发布新内容

import {
  App,
  Avatar,
  Button,
  Form,
  Image,
  Input,
  Popconfirm,
  Select,
  Space,
  Switch,
  Tag,
  Typography,
  Upload,
  type TableProps,
} from 'antd';
import { useRef, useState } from 'react';
import {
  FormModal,
  ProTable,
  SearchForm,
  usePermission,
  type ProTableRef,
} from '@bluewhale/whale-ui';
import {
  pageCreators,
  updateCreator,
  type CreatorAdminView,
  type CreatorQuery,
  type CreatorUpdateInput,
} from '../../../api/video';
import { uploadFile } from '../../../api/file';

interface CreatorFormValues {
  displayName: string;
  bio?: string;
  enabled: boolean;
}

const STATUS_OPTIONS = [
  { value: 1, label: '启用' },
  { value: 0, label: '停用' },
];

export default function CreatorPanel() {
  const { message } = App.useApp();
  const { has } = usePermission();
  const tableRef = useRef<ProTableRef>(null);
  const [form] = Form.useForm<CreatorFormValues>();

  const [keyword, setKeyword] = useState<string | undefined>(undefined);
  const [status, setStatus] = useState<number | undefined>(undefined);

  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<CreatorAdminView | null>(null);
  const [avatarFileId, setAvatarFileId] = useState<string | undefined>(undefined);
  const [avatarName, setAvatarName] = useState('');
  const [uploadingAvatar, setUploadingAvatar] = useState(false);
  const [actingId, setActingId] = useState<string | undefined>(undefined);

  const openEdit = (row: CreatorAdminView) => {
    setEditing(row);
    setAvatarFileId(undefined);
    setAvatarName('');
    setModalOpen(true);
  };

  const handleAvatarSelect = (file: File) => {
    setUploadingAvatar(true);
    uploadFile(file)
      .then((uploaded) => {
        setAvatarFileId(uploaded.id);
        setAvatarName(uploaded.originalName ?? file.name);
        message.success('头像已上传');
      })
      .catch((error) => message.error(error instanceof Error ? error.message : '头像上传失败'))
      .finally(() => setUploadingAvatar(false));
  };

  const handleSave = async (values: CreatorFormValues) => {
    if (!editing) {
      return;
    }
    const payload: CreatorUpdateInput = {
      displayName: values.displayName,
      bio: values.bio || undefined,
      status: values.enabled ? 1 : 0,
    };
    if (avatarFileId !== undefined) {
      payload.avatarFileId = avatarFileId;
    }
    try {
      await updateCreator(editing.id, payload);
      message.success('创作者资料已更新');
      setModalOpen(false);
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存失败');
      throw error;
    }
  };

  const toggleStatus = async (row: CreatorAdminView, enabled: boolean) => {
    setActingId(row.id);
    try {
      await updateCreator(row.id, { status: enabled ? 1 : 0 });
      message.success(enabled ? '创作者已启用' : '创作者已停用（不可发布新内容）');
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '状态更新失败');
    } finally {
      setActingId(undefined);
    }
  };

  const columns: TableProps<CreatorAdminView>['columns'] = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    {
      title: '头像',
      dataIndex: 'avatarUrl',
      width: 80,
      render: (value?: string) =>
        value ? (
          <Image
            src={value}
            alt="头像"
            width={40}
            height={40}
            style={{ objectFit: 'cover', borderRadius: 20 }}
          />
        ) : (
          <Avatar size={40}>创</Avatar>
        ),
    },
    {
      title: '创作者',
      dataIndex: 'displayName',
      render: (value: string | undefined, row) => (
        <Space direction="vertical" size={0}>
          <Typography.Text strong>{value || `创作者 #${row.id}`}</Typography.Text>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            用户 ID：{row.userId ?? '-'}
          </Typography.Text>
        </Space>
      ),
    },
    {
      title: '简介',
      dataIndex: 'bio',
      render: (value?: string) => (
        <Typography.Text type="secondary" ellipsis style={{ maxWidth: 320 }}>
          {value || '-'}
        </Typography.Text>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (value?: number) =>
        value === 1 ? <Tag color="success">启用</Tag> : <Tag color="default">停用</Tag>,
    },
    {
      title: '可发布',
      dataIndex: 'active',
      width: 100,
      render: (value?: boolean) =>
        value ? <Tag color="green">可发布</Tag> : <Tag color="red">受限</Tag>,
    },
    {
      title: '操作',
      key: 'action',
      width: 180,
      render: (_, row) => (
        <Space size={8}>
          {has('sys:video:creator:manage') ? (
            <Button type="link" size="small" onClick={() => openEdit(row)}>
              编辑
            </Button>
          ) : null}
          {has('sys:video:creator:manage') ? (
            <Popconfirm
              title={
                row.status === 1 ? '停用后该创作者不可发布新内容，确认停用？' : '确认启用该创作者？'
              }
              onConfirm={() => void toggleStatus(row, row.status !== 1)}
            >
              <Button
                type="link"
                size="small"
                danger={row.status === 1}
                loading={actingId === row.id}
              >
                {row.status === 1 ? '停用' : '启用'}
              </Button>
            </Popconfirm>
          ) : null}
        </Space>
      ),
    },
  ];

  return (
    <>
      <ProTable<CreatorAdminView, CreatorQuery>
        ref={tableRef}
        rowKey="id"
        title="创作者管理"
        columns={columns}
        request={pageCreators}
        params={{ keyword, status }}
        onError={(error) =>
          message.error(error instanceof Error ? error.message : '创作者列表加载失败')
        }
        search={
          <SearchForm
            items={[
              {
                name: 'keyword',
                label: '关键词',
                element: <Input allowClear placeholder="创作者昵称" />,
              },
              {
                name: 'status',
                label: '状态',
                element: (
                  <Select
                    allowClear
                    placeholder="全部"
                    style={{ width: 120 }}
                    options={STATUS_OPTIONS}
                  />
                ),
              },
            ]}
            onSearch={(values) => {
              setKeyword((values.keyword as string) || undefined);
              setStatus((values.status as number) ?? undefined);
            }}
            onReset={() => {
              setKeyword(undefined);
              setStatus(undefined);
            }}
          />
        }
      />

      <FormModal<CreatorFormValues>
        open={modalOpen}
        title={editing ? `编辑创作者：${editing.displayName || `#${editing.id}`}` : '编辑创作者'}
        form={form}
        width={600}
        initialValues={{
          displayName: editing?.displayName,
          bio: editing?.bio,
          enabled: editing ? editing.status === 1 : true,
        }}
        onOk={handleSave}
        onCancel={() => setModalOpen(false)}
      >
        <Form.Item label="头像">
          <Space direction="vertical" size={6}>
            {avatarFileId === '0' ? (
              <Typography.Text type="secondary">已清除头像（保存后生效）</Typography.Text>
            ) : avatarFileId ? (
              <Typography.Text type="secondary">
                已上传头像：{avatarName || `文件 ID ${avatarFileId}`}
              </Typography.Text>
            ) : editing?.avatarUrl ? (
              <Image
                src={editing.avatarUrl}
                width={64}
                height={64}
                style={{ objectFit: 'cover', borderRadius: 32 }}
                alt="当前头像"
              />
            ) : (
              <Typography.Text type="secondary">暂无头像</Typography.Text>
            )}
            <Space>
              <Upload
                showUploadList={false}
                accept="image/*"
                beforeUpload={(file) => {
                  handleAvatarSelect(file);
                  return false;
                }}
              >
                <Button loading={uploadingAvatar}>
                  {avatarFileId !== '0' && (avatarFileId || editing?.avatarUrl)
                    ? '重新上传头像'
                    : '上传头像'}
                </Button>
              </Upload>
              {editing?.avatarUrl || (avatarFileId && avatarFileId !== '0') ? (
                <Button
                  danger
                  onClick={() => {
                    setAvatarFileId('0');
                    setAvatarName('');
                  }}
                >
                  移除头像
                </Button>
              ) : null}
            </Space>
          </Space>
        </Form.Item>
        <Form.Item
          name="displayName"
          label="创作者昵称"
          rules={[{ required: true, message: '请输入创作者昵称' }]}
        >
          <Input maxLength={64} showCount />
        </Form.Item>
        <Form.Item name="bio" label="简介">
          <Input.TextArea maxLength={500} showCount autoSize={{ minRows: 3, maxRows: 6 }} />
        </Form.Item>
        <Form.Item
          name="enabled"
          label="启用状态"
          valuePropName="checked"
          extra="停用后该创作者不可发布新内容"
        >
          <Switch checkedChildren="启用" unCheckedChildren="停用" />
        </Form.Item>
      </FormModal>
    </>
  );
}
