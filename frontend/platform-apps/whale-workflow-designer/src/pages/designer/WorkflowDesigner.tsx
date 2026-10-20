// 自由画布流程设计器（F1）拼装页：调色板 + SVG 画布 + 属性面板，
// 顶栏提供 保存 / 校验 / 导入 BPMN / 导出 BPMN / 返回列表。
// 路由带 :id → 加载既有定义（GET → 解析 definitionJson）；无 :id → 新建（需填 code/name）。
// 校验按 errors[].refKey 高亮节点 / 连线，并以内联 Alert 列明 message / warnings。

import { Alert, App, Button, Card, Input, Modal, Space, Spin, Tag, Typography } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useCurrentUser } from '../../api/token';
import { workflowApi, type ProcessDefinitionValidate } from '../../api/workflow';
import Canvas from './Canvas';
import Palette from './Palette';
import PropertyPanel from './PropertyPanel';
import {
  createFlow,
  createNode,
  isValidKey,
  nextKey,
  parseDefinition,
  serializeDefinition,
  type CanvasFlow,
  type CanvasModel,
  type CanvasNode,
  type Selection,
} from './model';

const EMPTY_MODEL: CanvasModel = { nodes: [], flows: [] };

/** 统一把异常转为可展示文案 */
function toMessage(error: unknown): string {
  return error instanceof Error ? error.message : '操作失败';
}

/** 应用节点局部更新（切到非 USER_TASK 时清理审批人 / 表单绑定） */
function applyNodePatch(node: CanvasNode, patch: Partial<CanvasNode>): CanvasNode {
  const next: CanvasNode = { ...node, ...patch };
  if (patch.type && patch.type !== 'USER_TASK') {
    next.assignee = '';
    next.formId = null;
    next.formCode = null;
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

  const [model, setModel] = useState<CanvasModel>(EMPTY_MODEL);
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

  // 加载既有定义（:id）
  useEffect(() => {
    if (!definitionId) {
      setModel(EMPTY_MODEL);
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
        setModel(parseDefinition(definition.definitionJson));
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
    () => new Set((validation?.errors ?? []).map((issue) => issue.refKey).filter((key): key is string => !!key)),
    [validation],
  );

  const defaultAssignee = user?.username ?? '';

  const handleSelectNode = (key: string) => {
    if (linkFrom && linkFrom !== key) {
      if (model.flows.some((flow) => flow.from === linkFrom && flow.to === key)) {
        message.warning('该流转已存在');
        setLinkFrom(null);
        setSelected({ kind: 'node', key });
        return;
      }
      const flowKey = nextKey('f', model.flows.map((flow) => flow.key));
      setModel((prev) => ({ ...prev, flows: [...prev.flows, createFlow(linkFrom, key, flowKey)] }));
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
    setModel((prev) => ({
      ...prev,
      nodes: prev.nodes.map((node) => (node.key === key ? { ...node, x, y } : node)),
    }));
  };

  const handleDropNode = (type: CanvasNode['type'], x: number, y: number) => {
    const key = nextKey('n', model.nodes.map((node) => node.key));
    setModel((prev) => ({ ...prev, nodes: [...prev.nodes, createNode(type, key, x, y, defaultAssignee)] }));
    setSelected({ kind: 'node', key });
    setLinkFrom(null);
  };

  const handleChangeNode = (key: string, patch: Partial<CanvasNode>) => {
    setModel((prev) => ({
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
    setModel((prev) => ({
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
    setModel((prev) => ({
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
    setModel((prev) => ({
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
    setModel((prev) => ({ ...prev, flows: prev.flows.filter((flow) => flow.key !== key) }));
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
    const payload = { code: code.trim(), name: name.trim(), definitionJson: serializeDefinition(model) };
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
      const result = await workflowApi.definitionValidate({ definitionJson: serializeDefinition(model) });
      setValidation(result);
      if (result.valid) {
        message.success(`校验通过（节点 ${result.nodeCount} / 流转 ${result.flowCount}，模式 ${result.mode}）`);
      }
    } catch (error) {
      message.error(toMessage(error));
    } finally {
      setValidating(false);
    }
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

  return (
    <Card
      title="流程设计器"
      styles={{ body: { padding: 12 } }}
      extra={
        <Space wrap>
          <Button type="primary" loading={saving} onClick={() => void handleSave()}>
            保存
          </Button>
          <Button loading={validating} onClick={() => void handleValidate()}>
            校验
          </Button>
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
    </Card>
  );
}
