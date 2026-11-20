// 音乐运营 - 标签管理：列表 / 新增 / 编辑 / 删除（标签总量小，一次加载全部）
// 权限码：sys:music:tag:manage（标签列表查询走 sys:music:browse）

import {
  App,
  Button,
  Card,
  Form,
  Input,
  InputNumber,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
} from 'antd';
import type { TableProps } from 'antd';
import { useCallback, useEffect, useState } from 'react';
import { AuthButton, FormModal, usePermission } from '@bluewhale/whale-ui';
import {
  createTag,
  deleteTag,
  listTags,
  updateTag,
  type TagSaveInput,
  type TagType,
  type TagView,
} from '../../api/music';
import { useI18n } from '../../i18n';

interface TagFormValues {
  name: string;
  tagType?: TagType;
  sortNo?: number;
}

export default function TagList() {
  const { message } = App.useApp();
  const { t } = useI18n();
  const { has } = usePermission();
  const [form] = Form.useForm<TagFormValues>();

  const [tags, setTags] = useState<TagView[]>([]);
  const [loading, setLoading] = useState(false);
  const [editing, setEditing] = useState<TagView | null>(null);
  const [modalOpen, setModalOpen] = useState(false);

  const tagTypeOptions = [
    { value: 'GENRE', label: t('music.tag.type.genre') },
    { value: 'LANGUAGE', label: t('music.tag.type.language') },
    { value: 'MOOD', label: t('music.tag.type.mood') },
  ];

  const load = useCallback(() => {
    setLoading(true);
    listTags()
      .then(setTags)
      .catch((error) =>
        message.error(error instanceof Error ? error.message : t('music.tag.msg.loadFailed')),
      )
      .finally(() => setLoading(false));
  }, [message, t]);

  useEffect(() => {
    load();
  }, [load]);

  const openCreate = () => {
    setEditing(null);
    setModalOpen(true);
  };

  const openEdit = (row: TagView) => {
    setEditing(row);
    setModalOpen(true);
  };

  const handleSave = async (values: TagFormValues) => {
    const payload: TagSaveInput = {
      name: values.name,
      tagType: values.tagType,
      sortNo: values.sortNo,
    };
    try {
      if (editing) {
        await updateTag(editing.id, payload);
        message.success(t('music.tag.msg.updated'));
      } else {
        await createTag(payload);
        message.success(t('music.tag.msg.created'));
      }
      setModalOpen(false);
      load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('music.common.saveFailed'));
      throw error;
    }
  };

  const handleDelete = async (row: TagView) => {
    try {
      await deleteTag(row.id);
      message.success(t('music.tag.msg.deleted'));
      load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('music.common.deleteFailed'));
    }
  };

  const columns: TableProps<TagView>['columns'] = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    { title: t('music.tag.col.name'), dataIndex: 'name' },
    {
      title: t('music.tag.col.type'),
      dataIndex: 'tagType',
      width: 120,
      render: (value: TagType | undefined) => {
        const label = tagTypeOptions.find((item) => item.value === value)?.label;
        return label ? <Tag>{label}</Tag> : '-';
      },
    },
    {
      title: t('music.tag.col.sort'),
      dataIndex: 'sortNo',
      width: 90,
      render: (value: number | undefined) => value ?? '-',
    },
    {
      title: t('music.common.action'),
      key: 'action',
      width: 150,
      render: (_, row) => (
        <Space size={4}>
          {has('sys:music:tag:manage') && (
            <Button type="link" size="small" onClick={() => openEdit(row)}>
              {t('music.common.edit')}
            </Button>
          )}
          {has('sys:music:tag:manage') && (
            <Popconfirm
              title={t('music.tag.confirm.delete')}
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
      <Card
        title={t('music.tag.title')}
        extra={
          <AuthButton perm="sys:music:tag:manage" type="primary" onClick={openCreate}>
            {t('music.tag.action.create')}
          </AuthButton>
        }
      >
        <Table<TagView>
          rowKey="id"
          size="middle"
          loading={loading}
          columns={columns}
          dataSource={tags}
          pagination={false}
        />
      </Card>

      <FormModal<TagFormValues>
        open={modalOpen}
        title={
          editing
            ? t('music.tag.form.editTitle', { name: editing.name })
            : t('music.tag.action.create')
        }
        form={form}
        initialValues={{
          name: editing?.name,
          tagType: editing?.tagType ?? 'GENRE',
          sortNo: editing?.sortNo,
        }}
        onOk={handleSave}
        onCancel={() => setModalOpen(false)}
      >
        <Form.Item
          name="name"
          label={t('music.tag.form.name')}
          rules={[{ required: true, message: t('music.tag.form.nameRequired') }]}
        >
          <Input maxLength={64} showCount />
        </Form.Item>
        <Form.Item name="tagType" label={t('music.tag.form.type')}>
          <Select options={tagTypeOptions} />
        </Form.Item>
        <Form.Item name="sortNo" label={t('music.tag.form.sort')}>
          <InputNumber min={0} style={{ width: '100%' }} />
        </Form.Item>
      </FormModal>
    </>
  );
}
