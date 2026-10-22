// 画布编辑状态与节点 / 连线 / 分组变更：
// 选中元素、连线起点，以及所有会进入历史栈的结构变更（坐标、类型、key、删除、分组）。
// 页面只负责编排：这里返回的 handler 直接交给 Canvas / PropertyPanel / 分组弹窗。

import { App } from 'antd';
import { useState } from 'react';
import {
  createFlow,
  createGroup,
  createNode,
  isValidKey,
  nextKey,
  type CanvasFlow,
  type CanvasModel,
  type CanvasNode,
  type Selection,
} from '../model';

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

export interface CanvasEditor {
  selected: Selection | null;
  linkFrom: string | null;
  setLinkFrom: (key: string | null) => void;
  /** 点击节点：若处于连线态则建流，否则选中该节点 */
  onSelectNode: (key: string) => void;
  onSelectFlow: (key: string) => void;
  /** 直接选中节点（不触发连线逻辑，供模拟定位等外部跳转使用） */
  locateNode: (key: string) => void;
  onClearSelection: () => void;
  onMoveNode: (key: string, x: number, y: number) => void;
  onDropNode: (type: CanvasNode['type'], x: number, y: number) => void;
  onChangeNode: (key: string, patch: Partial<CanvasNode>) => void;
  /** 重命名节点 key（同步改流转 from/to）；非法或重复时返回 false */
  onRenameNode: (oldKey: string, newKey: string) => boolean;
  onDeleteNode: (key: string) => void;
  onChangeFlow: (key: string, patch: Partial<CanvasFlow>) => void;
  onDeleteFlow: (key: string) => void;
  /** 新建分组；名称为空时提示并返回 false */
  onCreateGroup: (name: string) => boolean;
  onRenameGroup: (key: string, name: string) => void;
  onDeleteGroup: (key: string) => void;
}

export function useCanvasEditor(
  model: CanvasModel,
  commit: (updater: (prev: CanvasModel) => CanvasModel) => void,
  defaultAssignee: string,
): CanvasEditor {
  const { message } = App.useApp();
  const [selected, setSelected] = useState<Selection | null>(null);
  const [linkFrom, setLinkFrom] = useState<string | null>(null);

  const onSelectNode = (key: string) => {
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

  const onClearSelection = () => {
    setSelected(null);
    setLinkFrom(null);
  };

  const onMoveNode = (key: string, x: number, y: number) => {
    commit((prev) => ({
      ...prev,
      nodes: prev.nodes.map((node) => (node.key === key ? { ...node, x, y } : node)),
    }));
  };

  const onDropNode = (type: CanvasNode['type'], x: number, y: number) => {
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

  const onChangeNode = (key: string, patch: Partial<CanvasNode>) => {
    commit((prev) => ({
      ...prev,
      nodes: prev.nodes.map((node) => (node.key === key ? applyNodePatch(node, patch) : node)),
    }));
  };

  const onRenameNode = (oldKey: string, newKey: string): boolean => {
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

  const onDeleteNode = (key: string) => {
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

  const onChangeFlow = (key: string, patch: Partial<CanvasFlow>) => {
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

  const onDeleteFlow = (key: string) => {
    commit((prev) => ({ ...prev, flows: prev.flows.filter((flow) => flow.key !== key) }));
    if (selected?.kind === 'flow' && selected.key === key) {
      setSelected(null);
    }
  };

  const onCreateGroup = (name: string): boolean => {
    const trimmed = name.trim();
    if (!trimmed) {
      message.warning('请填写分组名称');
      return false;
    }
    const key = nextKey(
      'g',
      model.groups.map((group) => group.key),
    );
    commit((prev) => ({ ...prev, groups: [...prev.groups, createGroup(key, trimmed)] }));
    message.success(`分组「${trimmed}」已创建`);
    return true;
  };

  const onRenameGroup = (key: string, name: string) => {
    commit((prev) => ({
      ...prev,
      groups: prev.groups.map((group) => (group.key === key ? { ...group, name } : group)),
    }));
  };

  const onDeleteGroup = (key: string) => {
    commit((prev) => ({
      ...prev,
      groups: prev.groups.filter((group) => group.key !== key),
      nodes: prev.nodes.map((node) => (node.groupKey === key ? { ...node, groupKey: null } : node)),
    }));
  };

  return {
    selected,
    linkFrom,
    setLinkFrom,
    onSelectNode,
    onSelectFlow: (key) => setSelected({ kind: 'flow', key }),
    locateNode: (key) => setSelected({ kind: 'node', key }),
    onClearSelection,
    onMoveNode,
    onDropNode,
    onChangeNode,
    onRenameNode,
    onDeleteNode,
    onChangeFlow,
    onDeleteFlow,
    onCreateGroup,
    onRenameGroup,
    onDeleteGroup,
  };
}
