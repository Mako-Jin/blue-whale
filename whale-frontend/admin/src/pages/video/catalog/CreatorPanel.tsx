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
import { useI18n } from '../../../i18n';

type Translate = ReturnType<typeof useI18n>['t'];

interface CreatorFormValues {
  displayName: string;
  bio?: string;
  enabled: boolean;
}

/** 启用状态选项 */
const buildStatusOptions = (t: Translate) => [
  { value: 1, label: t('video.catalog.creator.status.enabled') },
  { value: 0, label: t('video.catalog.creator.status.disabled') },
];

export default function CreatorPanel() {
  const { message } = App.useApp();
  const { has } = usePermission();
  const { t } = useI18n();
  const statusOptions = buildStatusOptions(t);
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
        message.success(t('video.catalog.creator.msg.avatarUploaded'));
      })
      .catch((error) =>
        message.error(
          error instanceof Error
            ? error.message
            : t('video.catalog.creator.msg.avatarUploadFailed'),
        ),
      )
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
      message.success(t('video.catalog.creator.msg.updated'));
      setModalOpen(false);
      tableRef.current?.reload();
    } catch (error) {
      message.error(
        error instanceof Error ? error.message : t('video.catalog.creator.msg.saveFailed'),
      );
      throw error;
    }
  };

  const toggleStatus = async (row: CreatorAdminView, enabled: boolean) => {
    setActingId(row.id);
    try {
      await updateCreator(row.id, { status: enabled ? 1 : 0 });
      message.success(
        enabled ? t('video.catalog.creator.msg.enabled') : t('video.catalog.creator.msg.disabled'),
      );
      tableRef.current?.reload();
    } catch (error) {
      message.error(
        error instanceof Error ? error.message : t('video.catalog.creator.msg.statusFailed'),
      );
    } finally {
      setActingId(undefined);
    }
  };

  const columns: TableProps<CreatorAdminView>['columns'] = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    {
      title: t('video.catalog.creator.col.avatar'),
      dataIndex: 'avatarUrl',
      width: 80,
      render: (value?: string) =>
        value ? (
          <Image
            src={value}
            alt={t('video.catalog.creator.alt.avatar')}
            width={40}
            height={40}
            style={{ objectFit: 'cover', borderRadius: 20 }}
          />
        ) : (
          <Avatar size={40}>{t('video.catalog.creator.avatarFallback')}</Avatar>
        ),
    },
    {
      title: t('video.catalog.creator.col.creator'),
      dataIndex: 'displayName',
      render: (value: string | undefined, row) => (
        <Space direction="vertical" size={0}>
          <Typography.Text strong>
            {value || t('video.catalog.creator.cell.creator', { id: row.id })}
          </Typography.Text>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {t('video.catalog.creator.cell.userId', { id: row.userId ?? '-' })}
          </Typography.Text>
        </Space>
      ),
    },
    {
      title: t('video.catalog.creator.col.bio'),
      dataIndex: 'bio',
      render: (value?: string) => (
        <Typography.Text type="secondary" ellipsis style={{ maxWidth: 320 }}>
          {value || '-'}
        </Typography.Text>
      ),
    },
    {
      title: t('video.catalog.creator.col.status'),
      dataIndex: 'status',
      width: 100,
      render: (value?: number) =>
        value === 1 ? (
          <Tag color="success">{t('video.catalog.creator.status.enabled')}</Tag>
        ) : (
          <Tag color="default">{t('video.catalog.creator.status.disabled')}</Tag>
        ),
    },
    {
      title: t('video.catalog.creator.col.publishable'),
      dataIndex: 'active',
      width: 100,
      render: (value?: boolean) =>
        value ? (
          <Tag color="green">{t('video.catalog.creator.tag.publishable')}</Tag>
        ) : (
          <Tag color="red">{t('video.catalog.creator.tag.restricted')}</Tag>
        ),
    },
    {
      title: t('video.catalog.creator.col.action'),
      key: 'action',
      width: 180,
      render: (_, row) => (
        <Space size={8}>
          {has('sys:video:creator:manage') ? (
            <Button type="link" size="small" onClick={() => openEdit(row)}>
              {t('video.catalog.creator.action.edit')}
            </Button>
          ) : null}
          {has('sys:video:creator:manage') ? (
            <Popconfirm
              title={
                row.status === 1
                  ? t('video.catalog.creator.confirm.disable')
                  : t('video.catalog.creator.confirm.enable')
              }
              onConfirm={() => void toggleStatus(row, row.status !== 1)}
            >
              <Button
                type="link"
                size="small"
                danger={row.status === 1}
                loading={actingId === row.id}
              >
                {row.status === 1
                  ? t('video.catalog.creator.action.disable')
                  : t('video.catalog.creator.action.enable')}
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
        title={t('video.catalog.creator.list.title')}
        columns={columns}
        request={pageCreators}
        params={{ keyword, status }}
        onError={(error) =>
          message.error(
            error instanceof Error ? error.message : t('video.catalog.creator.list.loadFailed'),
          )
        }
        search={
          <SearchForm
            items={[
              {
                name: 'keyword',
                label: t('video.catalog.creator.search.keyword'),
                element: (
                  <Input
                    allowClear
                    placeholder={t('video.catalog.creator.search.keywordPlaceholder')}
                  />
                ),
              },
              {
                name: 'status',
                label: t('video.catalog.creator.search.status'),
                element: (
                  <Select
                    allowClear
                    placeholder={t('video.catalog.creator.search.all')}
                    style={{ width: 120 }}
                    options={statusOptions}
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
        title={
          editing
            ? t('video.catalog.creator.form.editTitle', {
                name: editing.displayName || `#${editing.id}`,
              })
            : t('video.catalog.creator.form.editTitlePlain')
        }
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
        <Form.Item label={t('video.catalog.creator.form.avatar')}>
          <Space direction="vertical" size={6}>
            {avatarFileId === '0' ? (
              <Typography.Text type="secondary">
                {t('video.catalog.creator.form.avatarCleared')}
              </Typography.Text>
            ) : avatarFileId ? (
              <Typography.Text type="secondary">
                {t('video.catalog.creator.form.avatarUploaded', {
                  name: avatarName || t('video.catalog.creator.form.fileId', { id: avatarFileId }),
                })}
              </Typography.Text>
            ) : editing?.avatarUrl ? (
              <Image
                src={editing.avatarUrl}
                width={64}
                height={64}
                style={{ objectFit: 'cover', borderRadius: 32 }}
                alt={t('video.catalog.creator.alt.currentAvatar')}
              />
            ) : (
              <Typography.Text type="secondary">
                {t('video.catalog.creator.form.avatarEmpty')}
              </Typography.Text>
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
                    ? t('video.catalog.creator.form.avatarReupload')
                    : t('video.catalog.creator.form.avatarUpload')}
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
                  {t('video.catalog.creator.form.avatarRemove')}
                </Button>
              ) : null}
            </Space>
          </Space>
        </Form.Item>
        <Form.Item
          name="displayName"
          label={t('video.catalog.creator.form.displayName')}
          rules={[{ required: true, message: t('video.catalog.creator.form.displayNameRequired') }]}
        >
          <Input maxLength={64} showCount />
        </Form.Item>
        <Form.Item name="bio" label={t('video.catalog.creator.form.bio')}>
          <Input.TextArea maxLength={500} showCount autoSize={{ minRows: 3, maxRows: 6 }} />
        </Form.Item>
        <Form.Item
          name="enabled"
          label={t('video.catalog.creator.form.enabled')}
          valuePropName="checked"
          extra={t('video.catalog.creator.form.enabledExtra')}
        >
          <Switch
            checkedChildren={t('video.catalog.creator.status.enabled')}
            unCheckedChildren={t('video.catalog.creator.status.disabled')}
          />
        </Form.Item>
      </FormModal>
    </>
  );
}
