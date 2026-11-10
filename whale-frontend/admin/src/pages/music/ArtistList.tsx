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

interface ArtistFormValues {
  name: string;
  region?: string;
  bio?: string;
  status?: number;
}

const STATUS_OPTIONS = [
  { value: 1, label: '启用' },
  { value: 0, label: '停用' },
];

export default function ArtistList() {
  const { message } = App.useApp();
  const { has } = usePermission();
  const tableRef = useRef<ProTableRef>(null);
  const [form] = Form.useForm<ArtistFormValues>();

  const [keyword, setKeyword] = useState<string | undefined>(undefined);
  const [editing, setEditing] = useState<ArtistView | null>(null);
  const [modalOpen, setModalOpen] = useState(false);

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
        message.success('歌手已更新');
      } else {
        await createArtist(payload);
        message.success('歌手已新增');
      }
      setModalOpen(false);
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存失败');
      throw error;
    }
  };

  const handleDelete = async (row: ArtistView) => {
    try {
      await deleteArtist(row.id);
      message.success('歌手已删除');
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '删除失败');
    }
  };

  const columns: TableProps<ArtistView>['columns'] = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    { title: '艺名', dataIndex: 'name' },
    {
      title: '地区',
      dataIndex: 'region',
      width: 140,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: '简介',
      dataIndex: 'bio',
      ellipsis: true,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (value: number | undefined) =>
        value === 0 ? <Tag>停用</Tag> : <Tag color="success">启用</Tag>,
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      width: 180,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: '操作',
      key: 'action',
      width: 150,
      render: (_, row) => (
        <Space size={4}>
          {has('sys:music:artist:edit') && (
            <Button type="link" size="small" onClick={() => openEdit(row)}>
              编辑
            </Button>
          )}
          {has('sys:music:artist:delete') && (
            <Popconfirm title="确认删除该歌手？" onConfirm={() => void handleDelete(row)}>
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
      <ProTable<ArtistView, ArtistQuery>
        ref={tableRef}
        rowKey="id"
        title="歌手"
        columns={columns}
        request={pageArtists}
        params={{ keyword }}
        onError={(error) =>
          message.error(error instanceof Error ? error.message : '歌手列表加载失败')
        }
        search={
          <SearchForm
            items={[
              {
                name: 'keyword',
                label: '艺名',
                element: <Input allowClear placeholder="艺名关键词" />,
              },
            ]}
            onSearch={(values) => setKeyword((values.keyword as string) || undefined)}
            onReset={() => setKeyword(undefined)}
          />
        }
        toolbar={
          <AuthButton perm="sys:music:artist:add" type="primary" onClick={openCreate}>
            新增歌手
          </AuthButton>
        }
      />

      <FormModal<ArtistFormValues>
        open={modalOpen}
        title={editing ? `编辑歌手：${editing.name}` : '新增歌手'}
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
        <Form.Item name="name" label="艺名" rules={[{ required: true, message: '请输入艺名' }]}>
          <Input maxLength={100} showCount />
        </Form.Item>
        <Form.Item name="region" label="地区">
          <Input maxLength={64} />
        </Form.Item>
        <Form.Item name="bio" label="简介">
          <Input.TextArea rows={3} maxLength={1000} showCount />
        </Form.Item>
        <Form.Item name="status" label="状态">
          <Select options={STATUS_OPTIONS} />
        </Form.Item>
      </FormModal>
    </>
  );
}
