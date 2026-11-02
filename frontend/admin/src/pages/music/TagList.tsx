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

interface TagFormValues {
  name: string;
  tagType?: TagType;
  sortNo?: number;
}

const TAG_TYPE_OPTIONS = [
  { value: 'GENRE', label: '曲风' },
  { value: 'LANGUAGE', label: '语言' },
  { value: 'MOOD', label: '情绪' },
];

export default function TagList() {
  const { message } = App.useApp();
  const { has } = usePermission();
  const [form] = Form.useForm<TagFormValues>();

  const [tags, setTags] = useState<TagView[]>([]);
  const [loading, setLoading] = useState(false);
  const [editing, setEditing] = useState<TagView | null>(null);
  const [modalOpen, setModalOpen] = useState(false);

  const load = useCallback(() => {
    setLoading(true);
    listTags()
      .then(setTags)
      .catch((error) => message.error(error instanceof Error ? error.message : '标签加载失败'))
      .finally(() => setLoading(false));
  }, [message]);

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
        message.success('标签已更新');
      } else {
        await createTag(payload);
        message.success('标签已新增');
      }
      setModalOpen(false);
      load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存失败');
      throw error;
    }
  };

  const handleDelete = async (row: TagView) => {
    try {
      await deleteTag(row.id);
      message.success('标签已删除');
      load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '删除失败');
    }
  };

  const columns: TableProps<TagView>['columns'] = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    { title: '名称', dataIndex: 'name' },
    {
      title: '类型',
      dataIndex: 'tagType',
      width: 120,
      render: (value: TagType | undefined) => {
        const label = TAG_TYPE_OPTIONS.find((item) => item.value === value)?.label;
        return label ? <Tag>{label}</Tag> : '-';
      },
    },
    {
      title: '排序',
      dataIndex: 'sortNo',
      width: 90,
      render: (value: number | undefined) => value ?? '-',
    },
    {
      title: '操作',
      key: 'action',
      width: 150,
      render: (_, row) => (
        <Space size={4}>
          {has('sys:music:tag:manage') && (
            <Button type="link" size="small" onClick={() => openEdit(row)}>
              编辑
            </Button>
          )}
          {has('sys:music:tag:manage') && (
            <Popconfirm title="确认删除该标签？" onConfirm={() => void handleDelete(row)}>
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
      <Card
        title="标签"
        extra={
          <AuthButton perm="sys:music:tag:manage" type="primary" onClick={openCreate}>
            新增标签
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
        title={editing ? `编辑标签：${editing.name}` : '新增标签'}
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
          label="标签名称"
          rules={[{ required: true, message: '请输入标签名称' }]}
        >
          <Input maxLength={64} showCount />
        </Form.Item>
        <Form.Item name="tagType" label="类型">
          <Select options={TAG_TYPE_OPTIONS} />
        </Form.Item>
        <Form.Item name="sortNo" label="排序">
          <InputNumber min={0} style={{ width: '100%' }} />
        </Form.Item>
      </FormModal>
    </>
  );
}
