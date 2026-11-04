// 场景编辑：场景基本信息 + 节点树 + 入口绑定 + 可见性规则（三个页签）
//
// 2D MVP 约定：坐标只维护 x / y；z 为 M3 3D 字段，界面不暴露。
// 可见性规则由前端整体编辑后一次 PUT 替换，服务端做节点归属与主体类型校验。

import {
  Alert,
  App,
  Button,
  Card,
  Descriptions,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useCallback, useEffect, useState } from 'react';
import { useParams, useSearchParams } from 'react-router-dom';
import { hasPermission } from '../api/client';
import {
  deleteSceneNode,
  fetchSceneDetail,
  offlineScene,
  publishScene,
  replaceVisibility,
  updateScene,
  type NodeBindingView,
  type SceneDetailView,
  type SceneNodeView,
  type SceneSaveCommand,
  type SceneVisibilityCommand,
  type SceneVisibilityView,
  type VisibilityEffect,
  type VisibilitySubjectType,
} from '../api/scene';
import BindingFormModal from '../components/BindingFormModal';
import NodeFormModal from '../components/NodeFormModal';
import {
  EFFECT_META,
  ENTRY_TYPE_LABEL,
  NODE_TYPE_META,
  SCENE_STATUS_META,
  SUBJECT_TYPE_LABEL,
  formatDateTime,
} from '../utils/sceneDisplay';

type EditorTab = 'nodes' | 'bindings' | 'visibility';

/** 前端本地规则行（保存前不入库，localKey 仅用于表格键） */
interface RuleRow extends SceneVisibilityCommand {
  localKey: string;
}

const SUBJECT_TYPE_OPTIONS = [
  { label: '角色', value: 'ROLE' },
  { label: '用户', value: 'USER' },
  { label: '部门（首期仅登记）', value: 'DEPT' },
  { label: '全体用户', value: 'ALL' },
];

const EFFECT_OPTIONS = [
  { label: '允许', value: 'ALLOW' },
  { label: '拒绝（优先）', value: 'DENY' },
];

/**
 * 归一化 URL 中的页签参数，非法值回落到「节点」。
 *
 * @param value 查询参数值
 * @returns 页签
 */
function normalizeTab(value: string | null): EditorTab {
  return value === 'bindings' || value === 'visibility' ? value : 'nodes';
}

/**
 * 把服务端可见性规则转换为本地编辑行。
 *
 * @param visibilities 服务端规则
 * @returns 本地规则行
 */
function toRuleRows(visibilities: SceneVisibilityView[]): RuleRow[] {
  return visibilities.map((rule) => ({
    localKey: `saved-${rule.id}`,
    nodeId: rule.nodeId,
    subjectType: rule.subjectType,
    subjectId: rule.subjectId ?? undefined,
    effect: rule.effect,
  }));
}

export default function SceneEditorPage() {
  const { message, modal } = App.useApp();
  const params = useParams<{ id: string }>();
  const sceneId = Number(params.id);
  const [searchParams, setSearchParams] = useSearchParams();
  const tab = normalizeTab(searchParams.get('tab'));

  const [detail, setDetail] = useState<SceneDetailView | null>(null);
  const [loading, setLoading] = useState(true);
  const [sceneModalOpen, setSceneModalOpen] = useState(false);
  const [nodeModalOpen, setNodeModalOpen] = useState(false);
  const [editingNode, setEditingNode] = useState<SceneNodeView | null>(null);
  const [bindingModalOpen, setBindingModalOpen] = useState(false);
  const [bindingNode, setBindingNode] = useState<SceneNodeView | null>(null);
  const [rules, setRules] = useState<RuleRow[]>([]);
  const [savingRules, setSavingRules] = useState(false);

  const [sceneForm] = Form.useForm<SceneSaveCommand>();
  const [addRuleForm] = Form.useForm<SceneVisibilityCommand>();
  const subjectType = Form.useWatch('subjectType', addRuleForm);

  const canEditScene = hasPermission('sys:scene:edit');
  const canPublish = hasPermission('sys:scene:publish');
  const canOffline = hasPermission('sys:scene:offline');
  const canNode = hasPermission('sys:scene:node:manage');
  const canBinding = hasPermission('sys:scene:binding:manage');
  const canVisibility = hasPermission('sys:scene:visibility:manage');

  const load = useCallback(() => {
    setLoading(true);
    fetchSceneDetail(sceneId)
      .then((data) => {
        setDetail(data);
        setRules(toRuleRows(data.visibilities ?? []));
      })
      .catch((reason: unknown) => {
        message.error(reason instanceof Error ? reason.message : '场景详情加载失败');
      })
      .finally(() => setLoading(false));
  }, [sceneId, message]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    if (!sceneModalOpen || !detail) {
      return;
    }
    sceneForm.setFieldsValue({
      sceneKey: detail.scene.sceneKey,
      name: detail.scene.name,
      description: detail.scene.description ?? undefined,
    });
  }, [sceneModalOpen, detail, sceneForm]);

  const scene = detail?.scene;
  const nodes = detail?.nodes ?? [];
  const bindings = detail?.bindings ?? [];
  const bindingByNodeId = new Map<number, NodeBindingView>(
    bindings.map((binding) => [binding.nodeId, binding]),
  );
  const nodeNameById = new Map<number, string>(
    nodes.map((node) => [node.id, `${node.name}（${node.nodeKey}）`]),
  );

  const handleSceneSave = async () => {
    const values = await sceneForm.validateFields();
    try {
      await updateScene(sceneId, values);
      message.success('场景已更新');
      setSceneModalOpen(false);
      load();
    } catch (reason) {
      message.error(reason instanceof Error ? reason.message : '场景更新失败');
    }
  };

  const handlePublish = () => {
    if (!scene) {
      return;
    }
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

  const handleOffline = () => {
    if (!scene) {
      return;
    }
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

  const handleDeleteNode = async (node: SceneNodeView) => {
    try {
      await deleteSceneNode(node.id);
      message.success('节点已删除');
      load();
    } catch (reason) {
      message.error(reason instanceof Error ? reason.message : '节点删除失败');
    }
  };

  const handleAddRule = async () => {
    const values = await addRuleForm.validateFields();
    setRules((prev) => [
      ...prev,
      {
        ...values,
        nodeId: values.nodeId ?? 0,
        localKey: `new-${Date.now()}-${prev.length}`,
      },
    ]);
    addRuleForm.resetFields(['subjectId']);
  };

  const handleSaveRules = async () => {
    setSavingRules(true);
    try {
      await replaceVisibility(
        sceneId,
        rules.map((rule) => ({
          nodeId: rule.nodeId ?? 0,
          subjectType: rule.subjectType,
          subjectId: rule.subjectId,
          effect: rule.effect,
        })),
      );
      message.success('可见性规则已保存');
      load();
    } catch (reason) {
      message.error(reason instanceof Error ? reason.message : '可见性保存失败');
    } finally {
      setSavingRules(false);
    }
  };

  const nodeColumns: ColumnsType<SceneNodeView> = [
    {
      title: '节点',
      key: 'node',
      render: (_value, record) => (
        <Space direction="vertical" size={0}>
          <Typography.Text strong>{record.name}</Typography.Text>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {record.nodeKey}
          </Typography.Text>
        </Space>
      ),
    },
    {
      title: '类型',
      dataIndex: 'nodeType',
      key: 'nodeType',
      width: 90,
      render: (value: SceneNodeView['nodeType']) => {
        const meta = NODE_TYPE_META[value];
        return <Tag color={meta?.color}>{meta?.text ?? value}</Tag>;
      },
    },
    {
      title: '父节点',
      dataIndex: 'parentKey',
      key: 'parentKey',
      width: 160,
      render: (value?: string | null) => value || '根节点',
    },
    {
      title: '2D 坐标',
      key: 'coordinate',
      width: 120,
      render: (_value, record) => `x=${record.x ?? '—'}, y=${record.y ?? '—'}`,
    },
    {
      title: '排序',
      dataIndex: 'sortNo',
      key: 'sortNo',
      width: 80,
      render: (value?: number | null) => value ?? 0,
    },
    {
      title: '操作',
      key: 'actions',
      width: 180,
      render: (_value, record) => (
        <Space size={0}>
          {canNode ? (
            <Button
              type="link"
              onClick={() => {
                setEditingNode(record);
                setNodeModalOpen(true);
              }}
            >
              编辑
            </Button>
          ) : null}
          {canNode ? (
            <Popconfirm
              title="删除该节点？"
              description="存在子节点时服务端会拒绝删除。"
              okText="删除"
              okButtonProps={{ danger: true }}
              onConfirm={() => handleDeleteNode(record)}
            >
              <Button type="link" danger>
                删除
              </Button>
            </Popconfirm>
          ) : null}
        </Space>
      ),
    },
  ];

  const bindingColumns: ColumnsType<SceneNodeView> = [
    {
      title: '节点',
      key: 'node',
      render: (_value, record) => (
        <Space direction="vertical" size={0}>
          <Typography.Text strong>{record.name}</Typography.Text>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {record.nodeKey}
          </Typography.Text>
        </Space>
      ),
    },
    {
      title: '入口类型',
      key: 'entryType',
      width: 120,
      render: (_value, record) => {
        const binding = bindingByNodeId.get(record.id);
        return binding ? ENTRY_TYPE_LABEL[binding.entryType] : '—';
      },
    },
    {
      title: '入口地址',
      key: 'targetPath',
      render: (_value, record) => {
        const binding = bindingByNodeId.get(record.id);
        return binding?.targetPath ?? '未绑定';
      },
    },
    {
      title: '权限码',
      key: 'permissionCode',
      width: 200,
      render: (_value, record) => bindingByNodeId.get(record.id)?.permissionCode || '—',
    },
    {
      title: '状态',
      key: 'enabled',
      width: 90,
      render: (_value, record) => {
        const binding = bindingByNodeId.get(record.id);
        if (!binding) {
          return <Tag>未绑定</Tag>;
        }
        return <Tag color={binding.enabled ? 'green' : 'default'}>{binding.enabled ? '启用' : '停用'}</Tag>;
      },
    },
    {
      title: '操作',
      key: 'actions',
      width: 120,
      render: (_value, record) =>
        canBinding ? (
          <Button
            type="link"
            onClick={() => {
              setBindingNode(record);
              setBindingModalOpen(true);
            }}
          >
            {bindingByNodeId.has(record.id) ? '编辑入口' : '绑定入口'}
          </Button>
        ) : null,
    },
  ];

  const ruleColumns: ColumnsType<RuleRow> = [
    {
      title: '作用节点',
      key: 'node',
      width: 220,
      render: (_value, record) =>
        record.nodeId && record.nodeId > 0
          ? nodeNameById.get(record.nodeId) ?? `节点 ${record.nodeId}`
          : '场景级',
    },
    {
      title: '主体',
      key: 'subject',
      width: 200,
      render: (_value, record) => {
        const label = SUBJECT_TYPE_LABEL[record.subjectType] ?? record.subjectType;
        return record.subjectType === 'ALL' || !record.subjectId
          ? label
          : `${label}：${record.subjectId}`;
      },
    },
    {
      title: '效果',
      dataIndex: 'effect',
      key: 'effect',
      width: 140,
      render: (value: VisibilityEffect) => {
        const meta = EFFECT_META[value];
        return <Tag color={meta?.color}>{meta?.text ?? value}</Tag>;
      },
    },
    {
      title: '操作',
      key: 'actions',
      width: 100,
      render: (_value, record) => (
        <Button
          type="link"
          danger
          onClick={() => setRules((prev) => prev.filter((rule) => rule.localKey !== record.localKey))}
        >
          移除
        </Button>
      ),
    },
  ];

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Card
        size="small"
        loading={loading}
        title={
          <Space wrap>
            <span>{scene?.name ?? '场景'}</span>
            {scene ? (
              <Tag color={SCENE_STATUS_META[scene.status]?.color}>
                {SCENE_STATUS_META[scene.status]?.text ?? scene.status}
              </Tag>
            ) : null}
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              {scene?.sceneKey}
            </Typography.Text>
          </Space>
        }
        extra={
          <Space wrap>
            {canEditScene ? <Button onClick={() => setSceneModalOpen(true)}>编辑场景</Button> : null}
            {canPublish && scene?.status !== 'PUBLISHED' ? (
              <Button type="primary" onClick={handlePublish}>
                发布
              </Button>
            ) : null}
            {canOffline && scene?.status === 'PUBLISHED' ? (
              <Button danger onClick={handleOffline}>
                下线
              </Button>
            ) : null}
          </Space>
        }
      >
        <Descriptions size="small" column={2}>
          <Descriptions.Item label="场景说明" span={2}>
            {scene?.description || '—'}
          </Descriptions.Item>
          <Descriptions.Item label="布局版本">{scene?.layoutVersion ?? '尚未发布'}</Descriptions.Item>
          <Descriptions.Item label="最近发布">{formatDateTime(scene?.publishedAt)}</Descriptions.Item>
          <Descriptions.Item label="快照校验和" span={2}>
            {detail?.latestLayout?.checksum ? (
              <Typography.Text copyable style={{ fontSize: 12 }}>
                {detail.latestLayout.checksum}
              </Typography.Text>
            ) : (
              '—'
            )}
          </Descriptions.Item>
        </Descriptions>
      </Card>

      <Tabs
        activeKey={tab}
        onChange={(key) => setSearchParams({ tab: key })}
        items={[
          { key: 'nodes', label: `节点（${nodes.length}）` },
          { key: 'bindings', label: `入口绑定（${bindings.length}）` },
          { key: 'visibility', label: `可见性（${rules.length}）` },
        ]}
      />

      {tab === 'nodes' ? (
        <Card
          size="small"
          title="节点树"
          extra={
            canNode ? (
              <Button
                type="primary"
                onClick={() => {
                  setEditingNode(null);
                  setNodeModalOpen(true);
                }}
              >
                新增节点
              </Button>
            ) : null
          }
        >
          <Alert
            type="info"
            showIcon
            style={{ marginBottom: 12 }}
            message="2D 优先：节点坐标 x / y 取 0–1000；3D 高度 z 将在 M3 启用，本波不录入。"
          />
          <Table<SceneNodeView>
            rowKey="id"
            size="small"
            loading={loading}
            columns={nodeColumns}
            dataSource={nodes}
            pagination={false}
            locale={{ emptyText: '暂无节点，先新增区域或入口' }}
          />
        </Card>
      ) : null}

      {tab === 'bindings' ? (
        <Card size="small" title="节点入口">
          <Alert
            type="info"
            showIcon
            style={{ marginBottom: 12 }}
            message="一个节点仅一个入口，重复保存即覆盖；发布时要求至少 1 个启用入口。"
          />
          <Table<SceneNodeView>
            rowKey="id"
            size="small"
            loading={loading}
            columns={bindingColumns}
            dataSource={nodes}
            pagination={false}
            locale={{ emptyText: '暂无节点，请先在「节点」页签新增入口节点' }}
          />
        </Card>
      ) : null}

      {tab === 'visibility' ? (
        <Card
          size="small"
          title="可见性规则"
          extra={
            canVisibility ? (
              <Button type="primary" loading={savingRules} onClick={handleSaveRules}>
                保存规则
              </Button>
            ) : null
          }
        >
          <Alert
            type="warning"
            showIcon
            style={{ marginBottom: 12 }}
            message="拒绝（DENY）优先；存在允许（ALLOW）规则时按白名单解析；节点被拒绝时整棵子树隐藏。保存将整体替换现有规则。"
          />
          <Table<RuleRow>
            rowKey={(record) => record.localKey}
            size="small"
            loading={loading}
            columns={ruleColumns}
            dataSource={rules}
            pagination={false}
            locale={{ emptyText: '暂无规则，默认对全体登录用户可见' }}
            style={{ marginBottom: 16 }}
          />
          {canVisibility ? (
            <Form<SceneVisibilityCommand>
              form={addRuleForm}
              layout="inline"
              initialValues={{ nodeId: 0, subjectType: 'ROLE' as VisibilitySubjectType, effect: 'DENY' as VisibilityEffect }}
            >
              <Form.Item name="nodeId" label="作用节点">
                <Select
                  style={{ width: 200 }}
                  options={[
                    { label: '场景级', value: 0 },
                    ...nodes.map((node) => ({
                      label: `${node.name}（${node.nodeKey}）`,
                      value: node.id,
                    })),
                  ]}
                />
              </Form.Item>
              <Form.Item name="subjectType" label="主体类型" rules={[{ required: true }]}>
                <Select
                  style={{ width: 170 }}
                  options={SUBJECT_TYPE_OPTIONS}
                  onChange={(value: VisibilitySubjectType) => {
                    if (value === 'ALL') {
                      addRuleForm.setFieldsValue({ subjectId: undefined });
                    }
                  }}
                />
              </Form.Item>
              <Form.Item
                name="subjectId"
                label="主体标识"
                rules={[
                  {
                    validator: (_rule, value) =>
                      subjectType === 'ALL' || value
                        ? Promise.resolve()
                        : Promise.reject(new Error('非“全体用户”时请输入主体标识')),
                  },
                ]}
              >
                <Input
                  style={{ width: 180 }}
                  disabled={subjectType === 'ALL'}
                  placeholder="角色码 / 用户 ID / 部门 ID"
                />
              </Form.Item>
              <Form.Item name="effect" label="效果" rules={[{ required: true }]}>
                <Select style={{ width: 140 }} options={EFFECT_OPTIONS} />
              </Form.Item>
              <Form.Item>
                <Button onClick={handleAddRule}>添加规则</Button>
              </Form.Item>
            </Form>
          ) : null}
        </Card>
      ) : null}

      <Modal
        title="编辑场景"
        open={sceneModalOpen}
        onOk={handleSceneSave}
        onCancel={() => setSceneModalOpen(false)}
        destroyOnHidden
      >
        <Form<SceneSaveCommand> form={sceneForm} layout="vertical">
          <Form.Item
            name="sceneKey"
            label="场景标识"
            extra="2–64 位小写字母 / 数字 / 连字符，且以字母开头；已发布场景不可修改。"
            rules={[
              { required: true, message: '请输入场景标识' },
              { pattern: /^[a-z][a-z0-9-]{1,63}$/, message: '场景标识格式不合法' },
            ]}
          >
            <Input disabled={scene?.status === 'PUBLISHED'} />
          </Form.Item>
          <Form.Item
            name="name"
            label="场景名称"
            rules={[
              { required: true, message: '请输入场景名称' },
              { max: 128, message: '场景名称不能超过 128 个字符' },
            ]}
          >
            <Input />
          </Form.Item>
          <Form.Item
            name="description"
            label="场景说明"
            rules={[{ max: 500, message: '场景说明不能超过 500 个字符' }]}
          >
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>

      {scene ? (
        <NodeFormModal
          open={nodeModalOpen}
          scene={scene}
          nodes={nodes}
          node={editingNode}
          onCancel={() => setNodeModalOpen(false)}
          onSaved={load}
        />
      ) : null}

      <BindingFormModal
        open={bindingModalOpen}
        node={bindingNode}
        binding={bindingNode ? bindingByNodeId.get(bindingNode.id) ?? null : null}
        onCancel={() => setBindingModalOpen(false)}
        onSaved={load}
      />
    </Space>
  );
}
