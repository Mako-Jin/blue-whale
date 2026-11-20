// 音乐运营 - 歌手管理：分页查询 / 新增 / 编辑 / 停用 / 删除
// 权限码：sys:music:artist:list / add / edit / delete

import { App, Button, Form, Input, Popconfirm, Select, Space, Tag, type TableProps } from 'antd';
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
  createArtist,
  deleteArtist,
  pageArtists,
  updateArtist,
  type ArtistQuery,
  type ArtistSaveInput,
  type ArtistView,
} from '../../api/music';
import { useI18n } from '../../i18n';

interface ArtistFormValues {
  name: string;
  region?: string;
  bio?: string;
  status?: number;
}

export default function ArtistList() {
  const { message } = App.useApp();
  const { t } = useI18n();
  const { has } = usePermission();
  const tableRef = useRef<ProTableRef>(null);
  const [form] = Form.useForm<ArtistFormValues>();

  const [keyword, setKeyword] = useState<string | undefined>(undefined);
  const [editing, setEditing] = useState<ArtistView | null>(null);
  const [modalOpen, setModalOpen] = useState(false);

  const statusOptions = [
    { value: 1, label: t('music.artist.status.enabled') },
    { value: 0, label: t('music.artist.status.disabled') },
  ];

  const openCreate = () => {
    setEditing(null);
    setModalOpen(true);
  };

  const openEdit = (row: ArtistView) => {
    setEditing(row);
    setModalOpen(true);
  };

  const handleSave = async (values: ArtistFormValues) => {
    const payload: ArtistSaveInput = {
      name: values.name,
      region: values.region || undefined,
      bio: values.bio || undefined,
      status: values.status,
    };
    try {
      if (editing) {
        await updateArtist(editing.id, payload);
        message.success(t('music.artist.msg.updated'));
      } else {
        await createArtist(payload);
        message.success(t('music.artist.msg.created'));
      }
      setModalOpen(false);
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('music.common.saveFailed'));
      throw error;
    }
  };

  const handleDelete = async (row: ArtistView) => {
    try {
      await deleteArtist(row.id);
      message.success(t('music.artist.msg.deleted'));
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('music.common.deleteFailed'));
    }
  };

  const columns: TableProps<ArtistView>['columns'] = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    { title: t('music.artist.col.name'), dataIndex: 'name' },
    {
      title: t('music.artist.col.region'),
      dataIndex: 'region',
      width: 140,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: t('music.artist.col.bio'),
      dataIndex: 'bio',
      ellipsis: true,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: t('music.artist.col.status'),
      dataIndex: 'status',
      width: 90,
      render: (value: number | undefined) =>
        value === 0 ? (
          <Tag>{t('music.artist.status.disabled')}</Tag>
        ) : (
          <Tag color="success">{t('music.artist.status.enabled')}</Tag>
        ),
    },
    {
      title: t('music.artist.col.createTime'),
      dataIndex: 'createTime',
      width: 180,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: t('music.common.action'),
      key: 'action',
      width: 150,
      render: (_, row) => (
        <Space size={4}>
          {has('sys:music:artist:edit') && (
            <Button type="link" size="small" onClick={() => openEdit(row)}>
              {t('music.common.edit')}
            </Button>
          )}
          {has('sys:music:artist:delete') && (
            <Popconfirm
              title={t('music.artist.confirm.delete')}
              onConfirm={() => void handleDelete(row)}
            >
              <Button type="link" size="small" danger>
                {t('music.common.delete')}
              </Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  return (
    <>
      <ProTable<ArtistView, ArtistQuery>
        ref={tableRef}
        rowKey="id"
        title={t('music.artist.title')}
        columns={columns}
        request={pageArtists}
        params={{ keyword }}
        onError={(error) =>
          message.error(
            error instanceof Error ? error.message : t('music.artist.msg.listLoadFailed'),
          )
        }
        search={
          <SearchForm
            items={[
              {
                name: 'keyword',
                label: t('music.artist.search.name'),
                element: (
                  <Input allowClear placeholder={t('music.artist.search.namePlaceholder')} />
                ),
              },
            ]}
            onSearch={(values) => setKeyword((values.keyword as string) || undefined)}
            onReset={() => setKeyword(undefined)}
          />
        }
        toolbar={
          <AuthButton perm="sys:music:artist:add" type="primary" onClick={openCreate}>
            {t('music.artist.action.create')}
          </AuthButton>
        }
      />

      <FormModal<ArtistFormValues>
        open={modalOpen}
        title={
          editing
            ? t('music.artist.form.editTitle', { name: editing.name })
            : t('music.artist.action.create')
        }
        form={form}
        initialValues={{
          name: editing?.name,
          region: editing?.region,
          bio: editing?.bio,
          status: editing?.status ?? 1,
        }}
        onOk={handleSave}
        onCancel={() => setModalOpen(false)}
      >
        <Form.Item
          name="name"
          label={t('music.artist.form.name')}
          rules={[{ required: true, message: t('music.artist.form.nameRequired') }]}
        >
          <Input maxLength={100} showCount />
        </Form.Item>
        <Form.Item name="region" label={t('music.artist.form.region')}>
          <Input maxLength={64} />
        </Form.Item>
        <Form.Item name="bio" label={t('music.artist.form.bio')}>
          <Input.TextArea rows={3} maxLength={1000} showCount />
        </Form.Item>
        <Form.Item name="status" label={t('music.artist.form.status')}>
          <Select options={statusOptions} />
        </Form.Item>
      </FormModal>
    </>
  );
}
