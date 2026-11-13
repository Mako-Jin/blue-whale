// 场景列表：场景管理 / 入口绑定 / 可见性三个入口共用的场景选择页
//
// - manage：新建、编辑、发布、下线场景
// - bindings：跳到场景编辑页的「入口绑定」页签
// - visibility：跳到场景编辑页的「可见性」页签

import { App, Button, Card, Form, Input, Modal, Select, Space, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { hasPermission } from '../api/client';
import {
  createScene,
  fetchScenePage,
  offlineScene,
  publishScene,
  type SceneSaveCommand,
  type SceneView,
} from '../api/scene';
import { SCENE_STATUS_META, formatDateTime } from '../utils/sceneDisplay';

type SceneListMode = 'manage' | 'bindings' | 'visibility';

const PAGE_TITLE: Record<SceneListMode, string> = {
  manage: '场景管理',
  bindings: '入口绑定',
  visibility: '可见性',
};

const PAGE_DESCRIPTION: Record<SceneListMode, string> = {
  manage: '维护城池场景、节点树与发布状态；发布前服务端会校验节点数、层级、环与入口合法性。',
  bindings: '选择场景后维护入口节点的跳转地址与所需权限码；入口仅保存跳转信息，不直连业务库。',
  visibility:
    '选择场景后维护角色 / 用户 / 全体可见性规则；拒绝（DENY）优先，节点被拒绝时整棵子树隐藏。',
};

export default function SceneListPage({ mode = 'manage' }: { mode?: SceneListMode }) {
  const { message, modal } = App.useApp();
  const navigate = useNavigate();

  const [records, setRecords] = useState<SceneView[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [size, setSize] = useState(20);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<string>();
  const [loading, setLoading] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm<SceneSaveCommand>();

  const canAdd = hasPermission('sys:scene:add');
  const canPublish = hasPermission('sys:scene:publish');
  const canOffline = hasPermission('sys:scene:offline');

  const load = useCallback(() => {
    setLoading(true);
    fetchScenePage({ keyword: keyword.trim(), status, current, size })
      .then((data) => {
        setRecords(data.records);
        setTotal(data.total);
      })
      .catch((reason: unknown) => {
        message.error(reason instanceof Error ? reason.message : '场景列表加载失败');
      })
      .finally(() => setLoading(false));
  }, [keyword, status, current, size, message]);

  useEffect(() => {
    load();
  }, [load]);

  const openEditor = (scene: SceneView, tab: 'nodes' | 'bindings' | 'visibility') => {
    navigate(`/manage/${scene.id}?tab=${tab}`);
  };

  const handlePublish = (scene: SceneView) => {
    modal.confirm({
      title: `发布场景「${scene.name}」？`,
      content: '发布将校验节点树与入口并生成布局快照，发布后导航才可见。',
      okText: '发布',
      onOk: async () => {
        await publishScene(scene.id);
        message.success('场景已发布');
        load();
      },
    });
  };

  const handleOffline = (scene: SceneView) => {
    modal.confirm({
      title: `下线场景「${scene.name}」？`,
      content: '下线后该场景不再出现在城池导航，可稍后重新发布。',
      okText: '下线',
      okButtonProps: { danger: true },
      onOk: async () => {
        await offlineScene(scene.id);
        message.success('场景已下线');
        load();
      },
    });
  };

  const handleCreate = async () => {
    const values = await form.validateFields();
    setSubmitting(true);
    try {
      await createScene(values);
      message.success('场景已创建（草稿）');
      setCreateOpen(false);
      form.resetFields();
      setCurrent(1);
      load();
    } catch (reason) {
      message.error(reason instanceof Error ? reason.message : '场景创建失败');
    } finally {
      setSubmitting(false);
    }
  };

  const columns: ColumnsType<SceneView> = [
    {
      title: '场景标识',
      dataIndex: 'sceneKey',
      key: 'sceneKey',
      render: (value: string, record) => (
        <Space direction="vertical" size={0}>
          <Typography.Text strong>{record.name}</Typography.Text>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {value}
          </Typography.Text>
        </Space>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 110,
      render: (value: SceneView['status']) => {
        const meta = SCENE_STATUS_META[value];
        return <Tag color={meta?.color}>{meta?.text ?? value}</Tag>;
      },
    },
    {
      title: '布局版本',
      dataIndex: 'layoutVersion',
      key: 'layoutVersion',
      width: 100,
      render: (value?: number | null) => value ?? '—',
    },
    {
      title: '最近发布',
      dataIndex: 'publishedAt',
      key: 'publishedAt',
      width: 180,
      render: (value?: string | null) => formatDateTime(value),
    },
    {
      title: '更新时间',
      dataIndex: 'updateTime',
      key: 'updateTime',
      width: 180,
      render: (value?: string) => formatDateTime(value),
    },
    {
      title: '操作',
      key: 'actions',
      width: mode === 'manage' ? 280 : 140,
      render: (_value, record) => {
        if (mode === 'bindings') {
          return (
            <Button type="link" onClick={() => openEditor(record, 'bindings')}>
              管理入口
            </Button>
          );
        }
        if (mode === 'visibility') {
          return (
            <Button type="link" onClick={() => openEditor(record, 'visibility')}>
              管理可见性
            </Button>
          );
        }
        return (
          <Space size={0} wrap>
            <Button type="link" onClick={() => openEditor(record, 'nodes')}>
              编辑
            </Button>
            {canPublish ? (
              <Button type="link" onClick={() => handlePublish(record)}>
                发布
              </Button>
            ) : null}
            {canOffline ? (
              <Button type="link" danger onClick={() => handleOffline(record)}>
                下线
              </Button>
            ) : null}
          </Space>
        );
      },
    },
  ];

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Typography.Title level={3} style={{ margin: 0 }}>
        {PAGE_TITLE[mode]}
      </Typography.Title>
      <Typography.Text type="secondary">{PAGE_DESCRIPTION[mode]}</Typography.Text>

      <Card
        size="small"
        title={
          <Space wrap>
            <Input.Search
              allowClear
              placeholder="搜索场景标识 / 名称"
              style={{ width: 240 }}
              onSearch={(value) => {
                setKeyword(value);
                setCurrent(1);
              }}
            />
            <Select
              allowClear
              placeholder="全部状态"
              style={{ width: 140 }}
              value={status}
              onChange={(value) => {
                setStatus(value);
                setCurrent(1);
              }}
              options={[
                { label: '草稿', value: 'DRAFT' },
                { label: '已发布', value: 'PUBLISHED' },
                { label: '已下线', value: 'OFFLINE' },
              ]}
            />
          </Space>
        }
        extra={
          mode === 'manage' && canAdd ? (
            <Button type="primary" onClick={() => setCreateOpen(true)}>
              新建场景
            </Button>
          ) : null
        }
      >
        <Table<SceneView>
          rowKey="id"
          size="small"
          loading={loading}
          columns={columns}
          dataSource={records}
          pagination={{
            current,
            pageSize: size,
            total,
            showSizeChanger: true,
            onChange: (page, pageSize) => {
              setCurrent(page);
              setSize(pageSize);
            },
          }}
        />
      </Card>

      <Modal
        title="新建场景"
        open={createOpen}
        confirmLoading={submitting}
        onOk={handleCreate}
        onCancel={() => {
          setCreateOpen(false);
          form.resetFields();
        }}
        destroyOnHidden
      >
        <Form<SceneSaveCommand> form={form} layout="vertical" initialValues={{}}>
          <Form.Item
            name="sceneKey"
            label="场景标识"
            rules={[
              { required: true, message: '请输入场景标识' },
              {
                pattern: /^[a-z][a-z0-9-]{1,63}$/,
                message: '2–64 位小写字母 / 数字 / 连字符，且以字母开头',
              },
            ]}
          >
            <Input placeholder="例如 bluewhale-city" />
          </Form.Item>
          <Form.Item
            name="name"
            label="场景名称"
            rules={[
              { required: true, message: '请输入场景名称' },
              { max: 128, message: '场景名称不能超过 128 个字符' },
            ]}
          >
            <Input placeholder="例如 蓝鲸城" />
          </Form.Item>
          <Form.Item
            name="description"
            label="场景说明"
            rules={[{ max: 500, message: '场景说明不能超过 500 个字符' }]}
          >
            <Input.TextArea rows={3} placeholder="场景用途、覆盖的系统入口等" />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}
