// 歌单广场：公开歌单（含官方）与我的歌单，支持搜索、分页、新建 / 编辑 / 删除

import {
  App,
  Button,
  Form,
  Input,
  Popconfirm,
  Result,
  Select,
  Space,
  Tag,
  Tabs,
  Typography,
  type TableProps,
} from 'antd';
import { useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { FormModal, ProTable, SearchForm, type ProTableRef } from '@bluewhale/whale-ui';
import { getCurrentUser, hasPermission } from '../api/client';
import {
  createPlaylist,
  deletePlaylist,
  pagePlaylists,
  updatePlaylist,
  type PlaylistQuery,
  type PlaylistView,
  type PlaylistVisibility,
} from '../api/music';

interface PlaylistFormValues {
  title: string;
  description?: string;
  visibility: PlaylistVisibility;
}

const VISIBILITY_OPTIONS = [
  { value: 'PUBLIC', label: '公开（所有人可见）' },
  { value: 'PRIVATE', label: '私有（仅自己可见）' },
];

/** 歌单类型标签 */
function renderPlaylistType(playlist: PlaylistView) {
  return playlist.playlistType === 'OFFICIAL' ? (
    <Tag color="blue">官方</Tag>
  ) : (
    <Tag>用户</Tag>
  );
}

export default function PlaylistListPage() {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const tableRef = useRef<ProTableRef>(null);
  const [form] = Form.useForm<PlaylistFormValues>();

  const [mine, setMine] = useState(false);
  const [keyword, setKeyword] = useState<string | undefined>(undefined);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<PlaylistView | null>(null);

  const canManage = hasPermission('sys:music:playlist:own');
  const currentUserId = getCurrentUser()?.userId;

  const isOwner = (row: PlaylistView) =>
    row.playlistType !== 'OFFICIAL' &&
    row.ownerId !== undefined &&
    currentUserId !== undefined &&
    String(row.ownerId) === String(currentUserId);

  const openCreate = () => {
    setEditing(null);
    setModalOpen(true);
  };

  const openEdit = (row: PlaylistView) => {
    setEditing(row);
    setModalOpen(true);
  };

  const handleSave = async (values: PlaylistFormValues) => {
    const payload = {
      title: values.title,
      description: values.description || undefined,
      visibility: values.visibility,
    };
    try {
      if (editing) {
        await updatePlaylist(editing.id, payload);
        message.success('歌单已更新');
      } else {
        await createPlaylist(payload);
        message.success('歌单已创建');
      }
      setModalOpen(false);
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存失败');
      throw error;
    }
  };

  const handleDelete = async (row: PlaylistView) => {
    try {
      await deletePlaylist(row.id);
      message.success('歌单已删除');
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '删除失败');
    }
  };

  if (!canManage) {
    return (
      <Result
        status="403"
        title="暂无歌单权限"
        subTitle="歌单广场需要 sys:music:playlist:own 权限，请联系租户管理员开通"
        extra={
          <Button type="primary" onClick={() => navigate('/discover')}>
            返回发现
          </Button>
        }
      />
    );
  }

  const columns: TableProps<PlaylistView>['columns'] = [
    {
      title: '歌单',
      dataIndex: 'title',
      render: (value: string, row) => (
        <div className="music-song-title">
          <Typography.Link onClick={() => navigate(`/playlist/${row.id}`)}>{value}</Typography.Link>
          {row.description && <span className="music-song-subtitle">{row.description}</span>}
        </div>
      ),
    },
    {
      title: '类型',
      dataIndex: 'playlistType',
      width: 90,
      render: (_, row) => renderPlaylistType(row),
    },
    {
      title: '可见性',
      dataIndex: 'visibility',
      width: 100,
      render: (value: PlaylistVisibility | undefined) =>
        value === 'PRIVATE' ? <Tag color="orange">私有</Tag> : <Tag color="blue">公开</Tag>,
    },
    {
      title: '歌曲数',
      dataIndex: 'songCount',
      width: 90,
      render: (value: number | undefined) => value ?? 0,
    },
    {
      title: '播放',
      dataIndex: 'playCount',
      width: 90,
      render: (value: number | undefined) => value ?? 0,
    },
    {
      title: '更新时间',
      dataIndex: 'updateTime',
      width: 180,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: '操作',
      key: 'action',
      width: 150,
      render: (_, row) => (
        <Space size={4} wrap>
          <Button type="link" size="small" onClick={() => navigate(`/playlist/${row.id}`)}>
            查看
          </Button>
          {isOwner(row) && (
            <>
              <Button type="link" size="small" onClick={() => openEdit(row)}>
                编辑
              </Button>
              <Popconfirm title="确认删除该歌单？" onConfirm={() => void handleDelete(row)}>
                <Button type="link" size="small" danger>
                  删除
                </Button>
              </Popconfirm>
            </>
          )}
        </Space>
      ),
    },
  ];

  return (
    <>
      <Tabs
        activeKey={mine ? 'mine' : 'all'}
        onChange={(key) => setMine(key === 'mine')}
        items={[
          { key: 'all', label: '歌单广场' },
          { key: 'mine', label: '我的歌单' },
        ]}
      />

      <ProTable<PlaylistView, PlaylistQuery>
        ref={tableRef}
        rowKey="id"
        title={mine ? '我的歌单' : '歌单广场'}
        columns={columns}
        request={pagePlaylists}
        params={{ keyword, mine: mine ? true : undefined }}
        onError={(error) => message.error(error instanceof Error ? error.message : '歌单加载失败')}
        search={
          <SearchForm
            items={[
              {
                name: 'keyword',
                label: '关键词',
                element: <Input allowClear placeholder="歌单标题 / 简介" />,
              },
            ]}
            onSearch={(values) => setKeyword((values.keyword as string) || undefined)}
            onReset={() => setKeyword(undefined)}
          />
        }
        toolbar={
          <Button type="primary" onClick={openCreate}>
            新建歌单
          </Button>
        }
      />

      <FormModal<PlaylistFormValues>
        open={modalOpen}
        title={editing ? `编辑歌单：${editing.title}` : '新建歌单'}
        form={form}
        width={560}
        initialValues={{
          title: editing?.title,
          description: editing?.description,
          visibility: editing?.visibility ?? 'PUBLIC',
        }}
        onOk={handleSave}
        onCancel={() => setModalOpen(false)}
      >
        <Form.Item
          name="title"
          label="歌单标题"
          rules={[{ required: true, message: '请输入歌单标题' }]}
        >
          <Input maxLength={120} showCount />
        </Form.Item>
        <Form.Item name="description" label="歌单简介">
          <Input.TextArea rows={3} maxLength={1000} showCount placeholder="可选" />
        </Form.Item>
        <Form.Item
          name="visibility"
          label="可见性"
          rules={[{ required: true, message: '请选择可见性' }]}
        >
          <Select options={VISIBILITY_OPTIONS} />
        </Form.Item>
      </FormModal>
    </>
  );
}
