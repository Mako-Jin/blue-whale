// 自由画布流程设计器（F1）拼装页：调色板 + SVG 画布 + 属性面板，
// 顶栏提供 保存 / 校验 / 模拟 / 导入 BPMN / 导出 BPMN / 返回列表。
// 路由带 :id → 加载既有定义（GET → 解析 definitionJson）；无 :id → 新建（需填 code/name）。
// 校验按 errors[].refKey 高亮节点 / 连线，并以内联 Alert 列明 message / warnings。
// 模拟按流程变量真实求值选路，回放节点路径（不创建实例）。

import {
  Alert,
  App,
  Button,
  Card,
  Drawer,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Space,
  Spin,
  Steps,
  Table,
  Tag,
  Tooltip,
  Typography,
  type TableProps,
} from 'antd';
import { useEffect, useMemo, useReducer, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useCurrentUser } from '../../api/token';
import {
  workflowApi,
  type DefinitionMigrationResult,
  type ProcessDefinitionSimulate,
  type ProcessDefinitionValidate,
  type ProcessDefinitionVersion,
} from '../../api/workflow';
import Canvas from './Canvas';
import Palette from './Palette';
import PropertyPanel from './PropertyPanel';
import {
  createFlow,
  createGroup,
  createNode,
  isValidKey,
  nextKey,
  parseDefinition,
  serializeDefinition,
  type CanvasFlow,
  type CanvasGroup,
  type CanvasModel,
  type CanvasNode,
  type Selection,
} from './model';

const EMPTY_MODEL: CanvasModel = { nodes: [], flows: [], groups: [] };

/** 撤销栈上限（避免长会话内存膨胀） */
const MAX_HISTORY = 50;

/** 画布历史状态：当前模型 + 撤销栈 + 重做栈 */
interface HistoryState {
  model: CanvasModel;
  past: CanvasModel[];
  future: CanvasModel[];
}

/** 历史动作：commit 记录一次变更，reset 丢弃历史（加载 / 新建） */
type HistoryAction =
  | { type: 'commit'; model: CanvasModel }
  | { type: 'reset'; model: CanvasModel }
  | { type: 'undo' }
  | { type: 'redo' };

/** 历史栈 reducer：commit 清空重做栈，undo / redo 在两栈间搬运当前模型 */
function historyReducer(state: HistoryState, action: HistoryAction): HistoryState {
  switch (action.type) {
    case 'commit':
      if (action.model === state.model) {
        return state;
      }
      return {
        model: action.model,
        past: [...state.past, state.model].slice(-MAX_HISTORY),
        future: [],
      };
    case 'reset':
      return { model: action.model, past: [], future: [] };
    case 'undo': {
      const previous = state.past[state.past.length - 1];
      if (!previous) {
        return state;
      }
      return {
        model: previous,
        past: state.past.slice(0, -1),
        future: [state.model, ...state.future].slice(0, MAX_HISTORY),
      };
    }
    case 'redo': {
      const next = state.future[0];
      if (!next) {
        return state;
      }
      return {
        model: next,
        past: [...state.past, state.model].slice(-MAX_HISTORY),
        future: state.future.slice(1),
      };
    }
    default:
      return state;
  }
}

/** 统一把异常转为可展示文案 */
function toMessage(error: unknown): string {
  return error instanceof Error ? error.message : '操作失败';
}

/** 应用节点局部更新（切换类型时清理该类型无意义的字段） */
function applyNodePatch(node: CanvasNode, patch: Partial<CanvasNode>): CanvasNode {
  const next: CanvasNode = { ...node, ...patch };
  if (patch.type) {
    if (patch.type !== 'USER_TASK') {
      next.assignee = '';
      next.formId = null;
      next.formCode = null;
    }
    if (patch.type !== 'COUNTER_SIGN') {
      next.assignees = [];
      next.counterSignPolicy = 'ALL';
    } else if (!next.counterSignPolicy) {
      next.counterSignPolicy = 'ALL';
    }
  }
  return next;
}

export default function WorkflowDesigner() {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const { id } = useParams<{ id: string }>();
  const user = useCurrentUser();
  // 路由参数即雪花 ID 字符串，禁止 Number() 转换（>2^53 丢精度）
  const definitionId = id ?? null;

  // 画布模型走历史栈：每次结构变更 commit 一次，撤销 / 重做在两栈间搬运
  const [history, dispatch] = useReducer(historyReducer, {
    model: EMPTY_MODEL,
    past: [],
    future: [],
  });
  const model = history.model;
  const [code, setCode] = useState('');
  const [name, setName] = useState('');
  const [selected, setSelected] = useState<Selection | null>(null);
  const [linkFrom, setLinkFrom] = useState<string | null>(null);
  const [validation, setValidation] = useState<ProcessDefinitionValidate | null>(null);
  const [loading, setLoading] = useState(definitionId !== null);
  const [saving, setSaving] = useState(false);
  const [validating, setValidating] = useState(false);

  // BPMN 导入弹窗
  const [bpmnOpen, setBpmnOpen] = useState(false);
  const [bpmnXml, setBpmnXml] = useState('');
  const [bpmnCode, setBpmnCode] = useState('');
  const [bpmnName, setBpmnName] = useState('');
  const [bpmnSubmitting, setBpmnSubmitting] = useState(false);

  // 选路模拟弹窗
  const [simulateOpen, setSimulateOpen] = useState(false);
  const [simulateVariables, setSimulateVariables] = useState('{}');
  const [simulating, setSimulating] = useState(false);
  const [simulation, setSimulation] = useState<ProcessDefinitionSimulate | null>(null);

  // 分组 / 泳道弹窗
  const [groupOpen, setGroupOpen] = useState(false);
  const [groupName, setGroupName] = useState('');

  // 版本管理抽屉与迁移弹窗
  const [versionOpen, setVersionOpen] = useState(false);
  const [versionLoading, setVersionLoading] = useState(false);
  const [versions, setVersions] = useState<ProcessDefinitionVersion[]>([]);
  const [publishingVersion, setPublishingVersion] = useState<number | null>(null);
  const [migrateOpen, setMigrateOpen] = useState(false);
  const [migrateVersion, setMigrateVersion] = useState<number | null>(null);
  const [migrateIds, setMigrateIds] = useState('');
  const [migrating, setMigrating] = useState(false);
  const [migration, setMigration] = useState<DefinitionMigrationResult | null>(null);

  // 加载既有定义（:id）
  useEffect(() => {
    if (!definitionId) {
      dispatch({ type: 'reset', model: EMPTY_MODEL });
      setCode('');
      setName('');
      setSelected(null);
      setLinkFrom(null);
      setValidation(null);
      setLoading(false);
      return;
    }
    let alive = true;
    setLoading(true);
    workflowApi
      .definitionDetail(definitionId)
      .then((definition) => {
        if (!alive) {
          return;
        }
        setCode(definition.code);
        setName(definition.name);
        dispatch({ type: 'reset', model: parseDefinition(definition.definitionJson) });
        setSelected(null);
        setLinkFrom(null);
        setValidation(null);
      })
      .catch((error) => {
        if (alive) {
          message.error(toMessage(error));
        }
      })
      .finally(() => {
        if (alive) {
          setLoading(false);
        }
      });
    return () => {
      alive = false;
    };
  }, [definitionId, message]);

  const errorKeys = useMemo(
    () =>
      new Set(
        (validation?.errors ?? [])
          .map((issue) => issue.refKey)
          .filter((key): key is string => !!key),
      ),
    [validation],
  );

  const defaultAssignee = user?.username ?? '';

  /** 提交一次画布变更（进入撤销栈并清空重做栈） */
  const commit = (updater: (prev: CanvasModel) => CanvasModel) => {
    dispatch({ type: 'commit', model: updater(model) });
  };

  // Ctrl/Cmd+Z 撤销、Ctrl/Cmd+Y 或 Ctrl/Cmd+Shift+Z 重做（输入框内不劫持）
  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      const target = event.target as HTMLElement | null;
      const tag = target?.tagName;
      if (tag === 'INPUT' || tag === 'TEXTAREA' || target?.isContentEditable) {
        return;
      }
      if (!event.ctrlKey && !event.metaKey) {
        return;
      }
      const key = event.key.toLowerCase();
      if (key === 'z') {
        event.preventDefault();
        dispatch({ type: event.shiftKey ? 'redo' : 'undo' });
      } else if (key === 'y') {
        event.preventDefault();
        dispatch({ type: 'redo' });
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, []);

  const handleSelectNode = (key: string) => {
    if (linkFrom && linkFrom !== key) {
      if (model.flows.some((flow) => flow.from === linkFrom && flow.to === key)) {
        message.warning('该流转已存在');
        setLinkFrom(null);
        setSelected({ kind: 'node', key });
        return;
      }
      const flowKey = nextKey(
        'f',
        model.flows.map((flow) => flow.key),
      );
      commit((prev) => ({ ...prev, flows: [...prev.flows, createFlow(linkFrom, key, flowKey)] }));
      setSelected({ kind: 'flow', key: flowKey });
      setLinkFrom(null);
      return;
    }
    setSelected({ kind: 'node', key });
  };

  const handleClearSelection = () => {
    setSelected(null);
    setLinkFrom(null);
  };

  const handleMoveNode = (key: string, x: number, y: number) => {
    commit((prev) => ({
      ...prev,
      nodes: prev.nodes.map((node) => (node.key === key ? { ...node, x, y } : node)),
    }));
  };

  const handleDropNode = (type: CanvasNode['type'], x: number, y: number) => {
    const key = nextKey(
      'n',
      model.nodes.map((node) => node.key),
    );
    commit((prev) => ({
      ...prev,
      nodes: [...prev.nodes, createNode(type, key, x, y, defaultAssignee)],
    }));
    setSelected({ kind: 'node', key });
    setLinkFrom(null);
  };

  const handleChangeNode = (key: string, patch: Partial<CanvasNode>) => {
    commit((prev) => ({
      ...prev,
      nodes: prev.nodes.map((node) => (node.key === key ? applyNodePatch(node, patch) : node)),
    }));
  };

  const handleRenameNode = (oldKey: string, newKey: string): boolean => {
    const trimmed = newKey.trim();
    if (!isValidKey(trimmed)) {
      message.error('key 仅限字母 / 数字 / _ / -，1~64 位');
      return false;
    }
    if (trimmed !== oldKey && model.nodes.some((node) => node.key === trimmed)) {
      message.error(`key「${trimmed}」已存在`);
      return false;
    }
    commit((prev) => ({
      ...prev,
      nodes: prev.nodes.map((node) => (node.key === oldKey ? { ...node, key: trimmed } : node)),
      flows: prev.flows.map((flow) => ({
        ...flow,
        from: flow.from === oldKey ? trimmed : flow.from,
        to: flow.to === oldKey ? trimmed : flow.to,
      })),
    }));
    setSelected({ kind: 'node', key: trimmed });
    setLinkFrom((current) => (current === oldKey ? trimmed : current));
    return true;
  };

  const handleDeleteNode = (key: string) => {
    commit((prev) => ({
      ...prev,
      nodes: prev.nodes.filter((node) => node.key !== key),
      flows: prev.flows.filter((flow) => flow.from !== key && flow.to !== key),
    }));
    if (selected?.key === key) {
      setSelected(null);
    }
    setLinkFrom((current) => (current === key ? null : current));
  };

  const handleChangeFlow = (key: string, patch: Partial<CanvasFlow>) => {
    const target = model.flows.find((flow) => flow.key === key);
    if (!target) {
      return;
    }
    commit((prev) => ({
      ...prev,
      flows: prev.flows.map((flow) => {
        if (flow.key === key) {
          return { ...flow, ...patch };
        }
        // 同源默认流互斥
        if (patch.isDefault === true && flow.from === target.from) {
          return { ...flow, isDefault: false };
        }
        return flow;
      }),
    }));
  };

  const handleDeleteFlow = (key: string) => {
    commit((prev) => ({ ...prev, flows: prev.flows.filter((flow) => flow.key !== key) }));
    if (selected?.kind === 'flow' && selected.key === key) {
      setSelected(null);
    }
  };

  const handleSave = async () => {
    if (!code.trim()) {
      message.warning('请填写流程编码 code');
      return;
    }
    if (!name.trim()) {
      message.warning('请填写流程名称 name');
      return;
    }
    setSaving(true);
    const payload = {
      code: code.trim(),
      name: name.trim(),
      definitionJson: serializeDefinition(model),
    };
    try {
      if (definitionId) {
        await workflowApi.definitionUpdate(definitionId, payload);
        message.success('保存成功');
      } else {
        const created = await workflowApi.definitionCreate(payload);
        message.success('新建成功');
        if (created?.id) {
          navigate(`/design/${created.id}`, { replace: true });
        }
      }
    } catch (error) {
      message.error(toMessage(error));
    } finally {
      setSaving(false);
    }
  };

  const handleValidate = async () => {
    setValidating(true);
    try {
      const result = await workflowApi.definitionValidate({
        definitionJson: serializeDefinition(model),
      });
      setValidation(result);
      if (result.valid) {
        message.success(
          `校验通过（节点 ${result.nodeCount} / 流转 ${result.flowCount}，模式 ${result.mode}）`,
        );
      }
    } catch (error) {
      message.error(toMessage(error));
    } finally {
      setValidating(false);
    }
  };

  /** 打开模拟弹窗并重置上次结果 */
  const openSimulate = () => {
    setSimulation(null);
    setSimulateVariables('{}');
    setSimulateOpen(true);
  };

  /** 解析变量 JSON 文本；非法时提示并返回 null */
  const parseVariables = (): Record<string, unknown> | null => {
    const raw = simulateVariables.trim();
    if (!raw) {
      return {};
    }
    try {
      const parsed: unknown = JSON.parse(raw);
      if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed)) {
        message.warning('流程变量必须是 JSON 对象，如 {"amount": 200}');
        return null;
      }
      return parsed as Record<string, unknown>;
    } catch {
      message.error('流程变量不是合法 JSON');
      return null;
    }
  };

  const handleSimulate = async () => {
    const variables = parseVariables();
    if (variables === null) {
      return;
    }
    setSimulating(true);
    try {
      const result = await workflowApi.definitionSimulate({
        definitionJson: serializeDefinition(model),
        variables,
      });
      setSimulation(result);
      if (result.valid && !result.errorCode && result.reachedEnd) {
        message.success('模拟完成：已抵达结束事件');
      }
    } catch (error) {
      message.error(toMessage(error));
    } finally {
      setSimulating(false);
    }
  };

  /** 定位到模拟路径中的节点并关闭弹窗 */
  const locateStep = (key: string) => {
    setSelected({ kind: 'node', key });
    setSimulateOpen(false);
  };

  const handleExportBpmn = async () => {
    if (!definitionId) {
      message.warning('请先保存流程定义后再导出 BPMN');
      return;
    }
    try {
      const { blob, filename } = await workflowApi.definitionBpmnExport(definitionId);
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = filename;
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      URL.revokeObjectURL(url);
      message.success('BPMN 已导出');
    } catch (error) {
      message.error(toMessage(error));
    }
  };

  const openBpmnImport = () => {
    setBpmnCode(code);
    setBpmnName(name);
    setBpmnXml('');
    setBpmnOpen(true);
  };

  const handleBpmnImport = async () => {
    if (!bpmnCode.trim() || !bpmnName.trim()) {
      message.warning('请填写流程编码与名称');
      return;
    }
    if (!bpmnXml.trim()) {
      message.warning('请粘贴 BPMN XML');
      return;
    }
    setBpmnSubmitting(true);
    try {
      const created = await workflowApi.definitionBpmnImport({
        code: bpmnCode.trim(),
        name: bpmnName.trim(),
        bpmnXml,
      });
      message.success('BPMN 导入成功');
      setBpmnOpen(false);
      if (created?.id) {
        navigate(`/design/${created.id}`, { replace: true });
      }
    } catch (error) {
      message.error(toMessage(error));
    } finally {
      setBpmnSubmitting(false);
    }
  };

  /** 新建分组：名称必填，key 自动生成 */
  const handleCreateGroup = () => {
    const trimmed = groupName.trim();
    if (!trimmed) {
      message.warning('请填写分组名称');
      return;
    }
    const key = nextKey(
      'g',
      model.groups.map((group) => group.key),
    );
    commit((prev) => ({ ...prev, groups: [...prev.groups, createGroup(key, trimmed)] }));
    setGroupName('');
    message.success(`分组「${trimmed}」已创建`);
  };

  const handleRenameGroup = (key: string, name: string) => {
    commit((prev) => ({
      ...prev,
      groups: prev.groups.map((group) => (group.key === key ? { ...group, name } : group)),
    }));
  };

  const handleDeleteGroup = (key: string) => {
    commit((prev) => ({
      ...prev,
      groups: prev.groups.filter((group) => group.key !== key),
      nodes: prev.nodes.map((node) => (node.groupKey === key ? { ...node, groupKey: null } : node)),
    }));
  };

  /** 拉取版本快照列表 */
  const loadVersions = async () => {
    if (!definitionId) {
      return;
    }
    setVersionLoading(true);
    try {
      const result = await workflowApi.definitionVersions(definitionId);
      setVersions(result);
    } catch (error) {
      message.error(toMessage(error));
    } finally {
      setVersionLoading(false);
    }
  };

  /** 打开版本抽屉并重置迁移状态 */
  const openVersions = () => {
    setMigration(null);
    setMigrateOpen(false);
    setVersionOpen(true);
    void loadVersions();
  };

  /** 发布指定版本，并同步定义表内容到画布 */
  const handlePublish = async (version: number) => {
    if (!definitionId) {
      return;
    }
    setPublishingVersion(version);
    try {
      await workflowApi.definitionPublish(definitionId, { version });
      message.success(`已发布版本 v${version}`);
      await loadVersions();
      const definition = await workflowApi.definitionDetail(definitionId);
      setCode(definition.code);
      setName(definition.name);
      dispatch({ type: 'reset', model: parseDefinition(definition.definitionJson) });
      setSelected(null);
      setLinkFrom(null);
      setValidation(null);
    } catch (error) {
      message.error(toMessage(error));
    } finally {
      setPublishingVersion(null);
    }
  };

  /** 打开迁移弹窗并预填目标版本 */
  const openMigrate = (version: number) => {
    setMigration(null);
    setMigrateVersion(version);
    setMigrateIds('');
    setMigrateOpen(true);
  };

  /** 迁移运行中实例到目标版本（实例 ID 保持字符串，避免雪花 ID 丢精度） */
  const handleMigrate = async () => {
    if (!definitionId) {
      return;
    }
    if (migrateVersion === null) {
      message.warning('请填写目标版本号');
      return;
    }
    const ids = migrateIds
      .split(/[\s,，]+/)
      .map((item) => item.trim())
      .filter(Boolean);
    setMigrating(true);
    try {
      const result = await workflowApi.definitionMigrate(definitionId, {
        targetVersion: migrateVersion,
        ...(ids.length > 0 ? { instanceIds: ids } : {}),
      });
      setMigration(result);
      message.success(
        `迁移完成：成功 ${result.migrated} / 跳过 ${result.skipped} / 失败 ${result.failed}`,
      );
    } catch (error) {
      message.error(toMessage(error));
    } finally {
      setMigrating(false);
    }
  };

  const versionColumns: TableProps<ProcessDefinitionVersion>['columns'] = [
    {
      title: '版本',
      dataIndex: 'version',
      width: 90,
      render: (version: number) => <Tag color="blue">v{version}</Tag>,
    },
    {
      title: '状态',
      dataIndex: 'publishStatus',
      width: 100,
      render: (status?: number | null) =>
        status === 1 ? <Tag color="green">已发布</Tag> : <Tag>草稿</Tag>,
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      render: (createTime?: string) => createTime ?? '-',
    },
    {
      title: '操作',
      key: 'action',
      width: 140,
      render: (_, record) => (
        <Space size={4}>
          <Button
            type="link"
            size="small"
            loading={publishingVersion === record.version}
            onClick={() => void handlePublish(record.version)}
          >
            发布
          </Button>
          <Button type="link" size="small" onClick={() => openMigrate(record.version)}>
            迁移
          </Button>
        </Space>
      ),
    },
  ];

  const groupColumns: TableProps<CanvasGroup>['columns'] = [
    { title: 'key', dataIndex: 'key', width: 110 },
    {
      title: '名称',
      dataIndex: 'name',
      render: (_, record) => (
        <Input
          size="small"
          value={record.name}
          onChange={(event) => handleRenameGroup(record.key, event.target.value)}
        />
      ),
    },
    {
      title: '节点数',
      key: 'count',
      width: 90,
      render: (_, record) => model.nodes.filter((node) => node.groupKey === record.key).length,
    },
    {
      title: '操作',
      key: 'action',
      width: 80,
      render: (_, record) => (
        <Popconfirm title="删除后成员将变为未分组" onConfirm={() => handleDeleteGroup(record.key)}>
          <Button type="link" size="small" danger>
            删除
          </Button>
        </Popconfirm>
      ),
    },
  ];

  return (
    <Card
      title="流程设计器"
      styles={{ body: { padding: 12 } }}
      extra={
        <Space wrap>
          <Tooltip title="撤销（Ctrl+Z）">
            <Button disabled={history.past.length === 0} onClick={() => dispatch({ type: 'undo' })}>
              撤销
            </Button>
          </Tooltip>
          <Tooltip title="重做（Ctrl+Y / Ctrl+Shift+Z）">
            <Button
              disabled={history.future.length === 0}
              onClick={() => dispatch({ type: 'redo' })}
            >
              重做
            </Button>
          </Tooltip>
          <Button type="primary" loading={saving} onClick={() => void handleSave()}>
            保存
          </Button>
          <Button loading={validating} onClick={() => void handleValidate()}>
            校验
          </Button>
          <Button onClick={() => setGroupOpen(true)}>分组</Button>
          <Button disabled={!definitionId} onClick={openVersions}>
            版本
          </Button>
          <Button onClick={openSimulate}>模拟</Button>
          <Button onClick={openBpmnImport}>导入 BPMN</Button>
          <Button disabled={!definitionId} onClick={() => void handleExportBpmn()}>
            导出 BPMN
          </Button>
          <Button onClick={() => navigate('/definitions')}>返回列表</Button>
        </Space>
      }
    >
      <Spin spinning={loading}>
        <Space style={{ marginBottom: 8 }} wrap>
          <Input
            addonBefore="code"
            style={{ width: 240 }}
            placeholder="流程编码"
            value={code}
            onChange={(event) => setCode(event.target.value)}
          />
          <Input
            addonBefore="name"
            style={{ width: 280 }}
            placeholder="流程名称"
            value={name}
            onChange={(event) => setName(event.target.value)}
          />
          {definitionId ? (
            <Typography.Text type="secondary">ID：{definitionId}</Typography.Text>
          ) : (
            <Tag color="orange">未保存</Tag>
          )}
        </Space>

        {validation && validation.errors.length > 0 ? (
          <Alert
            type="error"
            showIcon
            style={{ marginBottom: 8 }}
            message={`校验未通过（${validation.errors.length} 个错误）`}
            description={
              <ul style={{ margin: 0, paddingLeft: 18 }}>
                {validation.errors.map((issue, index) => (
                  <li key={`${issue.code}-${index}`}>
                    {issue.code}：{issue.message}
                    {issue.refKey ? `（${issue.refKey}）` : ''}
                  </li>
                ))}
              </ul>
            }
          />
        ) : null}

        {validation && validation.warnings.length > 0 ? (
          <Alert
            type="warning"
            showIcon
            style={{ marginBottom: 8 }}
            message={`警告（${validation.warnings.length} 条）`}
            description={
              <ul style={{ margin: 0, paddingLeft: 18 }}>
                {validation.warnings.map((issue, index) => (
                  <li key={`${issue.code}-${index}`}>
                    {issue.code}：{issue.message}
                    {issue.refKey ? `（${issue.refKey}）` : ''}
                  </li>
                ))}
              </ul>
            }
          />
        ) : null}

        <div style={{ display: 'flex', gap: 12, height: 560 }}>
          <div style={{ flex: 1, minWidth: 0 }}>
            <Canvas
              palette={<Palette />}
              model={model}
              selected={selected}
              errorKeys={errorKeys}
              linkFrom={linkFrom}
              onSelectNode={handleSelectNode}
              onSelectFlow={(key) => setSelected({ kind: 'flow', key })}
              onClearSelection={handleClearSelection}
              onMoveNode={handleMoveNode}
              onDropNode={handleDropNode}
            />
          </div>
          <div
            style={{
              flex: '0 0 300px',
              width: 300,
              overflow: 'auto',
              paddingLeft: 8,
              borderLeft: '1px solid #f0f0f0',
            }}
          >
            <PropertyPanel
              model={model}
              selection={selected}
              linkFrom={linkFrom}
              onChangeNode={handleChangeNode}
              onRenameNode={handleRenameNode}
              onDeleteNode={handleDeleteNode}
              onChangeFlow={handleChangeFlow}
              onDeleteFlow={handleDeleteFlow}
              onStartLink={setLinkFrom}
              onCancelLink={() => setLinkFrom(null)}
            />
          </div>
        </div>
      </Spin>

      <Modal
        title="选路模拟"
        open={simulateOpen}
        width={760}
        okText={simulation ? '关闭' : '开始模拟'}
        confirmLoading={simulating}
        onOk={() => {
          if (simulation) {
            setSimulateOpen(false);
            return;
          }
          void handleSimulate();
        }}
        onCancel={() => setSimulateOpen(false)}
      >
        <Space direction="vertical" style={{ width: '100%' }} size="middle">
          <Typography.Text type="secondary">
            按当前画布定义与流程变量真实求值选路（不创建实例）。变量键可直接写在条件里，如 amount
            &gt; 100。
          </Typography.Text>
          <Input.TextArea
            rows={5}
            placeholder='{"amount": 200}'
            value={simulateVariables}
            onChange={(event) => setSimulateVariables(event.target.value)}
          />
          {simulation && !simulation.valid ? (
            <Alert
              type="error"
              showIcon
              message={`定义结构非法（${simulation.errors.length} 个错误）`}
              description={
                <ul style={{ margin: 0, paddingLeft: 18 }}>
                  {simulation.errors.map((issue, index) => (
                    <li key={`${issue.code}-${index}`}>
                      {issue.code}：{issue.message}
                      {issue.refKey ? `（${issue.refKey}）` : ''}
                    </li>
                  ))}
                </ul>
              }
            />
          ) : null}
          {simulation && simulation.valid && simulation.errorCode ? (
            <Alert
              type="error"
              showIcon
              message={`选路失败：${simulation.errorCode}`}
              description={simulation.errorMessage ?? '流程变量不足以选出分支'}
            />
          ) : null}
          {simulation && simulation.valid && !simulation.errorCode ? (
            <Alert
              type={simulation.reachedEnd ? 'success' : 'warning'}
              showIcon
              message={
                simulation.reachedEnd
                  ? `已抵达结束事件（mode=${simulation.mode}）`
                  : `停在节点 ${simulation.terminalNodeKey ?? '-'}`
              }
              description={`模拟路径 ${simulation.steps.length} 步`}
            />
          ) : null}
          {simulation && simulation.steps.length > 0 ? (
            <Steps
              direction="vertical"
              size="small"
              items={simulation.steps.map((step) => ({
                title: `#${step.order} ${step.nodeName || step.nodeKey}`,
                description: (
                  <Space size={4} wrap>
                    <Tag>{step.nodeType ?? '-'}</Tag>
                    <Typography.Text type="secondary">{step.nodeKey}</Typography.Text>
                    {step.flowKey ? (
                      <Typography.Text type="secondary">入边 {step.flowKey}</Typography.Text>
                    ) : null}
                    {step.condition ? <Tag color="blue">{step.condition}</Tag> : null}
                    {step.isDefault ? <Tag color="orange">默认流</Tag> : null}
                    <Button size="small" type="link" onClick={() => locateStep(step.nodeKey)}>
                      定位
                    </Button>
                  </Space>
                ),
              }))}
            />
          ) : null}
        </Space>
      </Modal>

      <Modal
        title="导入 BPMN"
        open={bpmnOpen}
        width={720}
        okText="导入"
        confirmLoading={bpmnSubmitting}
        onOk={() => void handleBpmnImport()}
        onCancel={() => setBpmnOpen(false)}
      >
        <Space direction="vertical" style={{ width: '100%' }} size="middle">
          <Input
            addonBefore="code"
            placeholder="流程编码"
            value={bpmnCode}
            onChange={(event) => setBpmnCode(event.target.value)}
          />
          <Input
            addonBefore="name"
            placeholder="流程名称"
            value={bpmnName}
            onChange={(event) => setBpmnName(event.target.value)}
          />
          <Input.TextArea
            rows={12}
            placeholder="粘贴 BPMN 2.0 XML"
            value={bpmnXml}
            onChange={(event) => setBpmnXml(event.target.value)}
          />
        </Space>
      </Modal>

      <Modal
        title="分组 / 泳道"
        open={groupOpen}
        footer={null}
        width={520}
        onCancel={() => setGroupOpen(false)}
      >
        <Space direction="vertical" style={{ width: '100%' }} size="middle">
          <Space.Compact style={{ width: '100%' }}>
            <Input
              placeholder="新分组名称"
              value={groupName}
              onChange={(event) => setGroupName(event.target.value)}
              onPressEnter={handleCreateGroup}
            />
            <Button type="primary" onClick={handleCreateGroup}>
              新建
            </Button>
          </Space.Compact>
          <Table
            size="small"
            rowKey="key"
            pagination={false}
            dataSource={model.groups}
            columns={groupColumns}
            locale={{ emptyText: '暂无分组' }}
          />
          <Typography.Text type="secondary">
            容器矩形按成员节点包围盒自动派生；在右侧属性面板为节点选择所属分组。
          </Typography.Text>
        </Space>
      </Modal>

      <Drawer
        title="版本管理"
        width={640}
        open={versionOpen}
        onClose={() => setVersionOpen(false)}
        extra={
          <Button size="small" onClick={() => void loadVersions()}>
            刷新
          </Button>
        }
      >
        <Spin spinning={versionLoading}>
          <Space direction="vertical" style={{ width: '100%' }} size="middle">
            <Typography.Text type="secondary">
              发布后新实例按该版本发起；迁移把运行中实例切换到目标版本的结构。
            </Typography.Text>
            <Table
              size="small"
              rowKey="id"
              dataSource={versions}
              columns={versionColumns}
              pagination={false}
              locale={{ emptyText: '暂无版本快照' }}
            />
          </Space>
        </Spin>
      </Drawer>

      <Modal
        title="迁移运行中实例"
        open={migrateOpen}
        okText="开始迁移"
        confirmLoading={migrating}
        onOk={() => void handleMigrate()}
        onCancel={() => setMigrateOpen(false)}
      >
        <Space direction="vertical" style={{ width: '100%' }} size="middle">
          <InputNumber
            min={1}
            precision={0}
            value={migrateVersion}
            onChange={(value) => setMigrateVersion(typeof value === 'number' ? value : null)}
          />
          <Input.TextArea
            rows={4}
            placeholder="实例 ID（可选，逗号/换行分隔；留空=全部运行中实例）"
            value={migrateIds}
            onChange={(event) => setMigrateIds(event.target.value)}
          />
          <Typography.Text type="secondary">
            实例 ID 按字符串提交，避免雪花 ID 精度丢失。
          </Typography.Text>
          {migration ? (
            <Alert
              type={migration.failed > 0 ? 'warning' : 'success'}
              showIcon
              message={`迁移完成：成功 ${migration.migrated} / 跳过 ${migration.skipped} / 失败 ${migration.failed}`}
              description={
                migration.failures.length > 0 ? (
                  <ul style={{ margin: 0, paddingLeft: 18 }}>
                    {migration.failures.map((failure, index) => (
                      <li key={`${failure}-${index}`}>{failure}</li>
                    ))}
                  </ul>
                ) : undefined
              }
            />
          ) : null}
        </Space>
      </Modal>
    </Card>
  );
}
