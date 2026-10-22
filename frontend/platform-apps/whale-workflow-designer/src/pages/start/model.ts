// 流程定义 JSON 解析与「入口审批节点」求解（Wave 5.32 F3）
// 兼容规范化 {type:"stateMachine", nodes[], flows[]} 与 legacy {nodes[]}（顺序即流转，无 type/flows）。

import type { NodeType, ProcessModelFlow } from '../../api/workflow';

/** 支持的节点类型集合（与后端 ProcessNodeType 一致） */
const NODE_TYPES: readonly NodeType[] = [
  'START_EVENT',
  'END_EVENT',
  'USER_TASK',
  'EXCLUSIVE_GATEWAY',
  'PARALLEL_GATEWAY',
  'COUNTER_SIGN',
];

/** 解析后的节点（legacy/缺 type 归一化为 USER_TASK；缺 key 合成 n{i+1}） */
export interface ParsedNode {
  key: string;
  type: NodeType;
  name?: string | null;
  assignee?: string | null;
  /** 绑定的表单定义 ID（字符串承载，可空） */
  formId?: string | null;
  /** 绑定的表单编码（可空） */
  formCode?: string | null;
}

/** 解析后的定义模型 */
export interface ParsedDefinition {
  nodes: ParsedNode[];
  flows: ProcessModelFlow[];
  /** legacy 结构（无 type/flows，顺序即流转） */
  legacy: boolean;
}

/** 取可空字符串（number 也字符串化，兼容后端 Long 字符串化） */
function toOptionalString(value: unknown): string | null {
  if (typeof value === 'string') {
    return value;
  }
  if (typeof value === 'number') {
    return String(value);
  }
  return null;
}

/** 归一化节点类型：非法/缺失返回 undefined */
function toNodeType(value: unknown): NodeType | undefined {
  return typeof value === 'string' && (NODE_TYPES as readonly string[]).includes(value)
    ? (value as NodeType)
    : undefined;
}

/** 把任意值安全地视为普通对象 */
function asRecord(value: unknown): Record<string, unknown> {
  return typeof value === 'object' && value !== null ? (value as Record<string, unknown>) : {};
}

/**
 * 解析 definitionJson；结构非法或空节点返回 null。
 *
 * @param json 流程定义 JSON 文本（规范化或 legacy）
 * @returns 解析后的模型，无法解析时返回 null
 */
export function parseDefinitionModel(json: string | null | undefined): ParsedDefinition | null {
  if (!json) {
    return null;
  }
  let raw: unknown;
  try {
    raw = JSON.parse(json);
  } catch {
    return null;
  }
  const container = asRecord(raw);
  if (!Array.isArray(container.nodes) || container.nodes.length === 0) {
    return null;
  }

  const nodes: ParsedNode[] = container.nodes.map((item, index) => {
    const node = asRecord(item);
    return {
      key: toOptionalString(node.key) ?? `n${index + 1}`,
      type: toNodeType(node.type) ?? 'USER_TASK',
      name: toOptionalString(node.name),
      assignee: toOptionalString(node.assignee),
      formId: toOptionalString(node.formId),
      formCode: toOptionalString(node.formCode),
    };
  });

  const flows: ProcessModelFlow[] = (Array.isArray(container.flows) ? container.flows : [])
    .map((item, index): ProcessModelFlow | null => {
      const flow = asRecord(item);
      const from = toOptionalString(flow.from);
      const to = toOptionalString(flow.to);
      if (!from || !to) {
        return null;
      }
      return {
        key: toOptionalString(flow.key) ?? `f${index + 1}`,
        from,
        to,
        condition: toOptionalString(flow.condition),
        isDefault: flow.isDefault === true,
      };
    })
    .filter((flow): flow is ProcessModelFlow => flow !== null);

  const hasTypedNode = container.nodes.some(
    (item) => toNodeType(asRecord(item).type) !== undefined,
  );

  return { nodes, flows, legacy: !hasTypedNode && flows.length === 0 };
}

/**
 * 求「入口审批节点」：
 * - legacy：nodes[0]（顺序即流转）。
 * - 规范化：从入口节点（优先 START_EVENT，其次 nodes[0]）沿 flows 透传
 *   START_EVENT / EXCLUSIVE_GATEWAY / PARALLEL_GATEWAY，直到命中第一个审批节点（USER_TASK / COUNTER_SIGN）；
 *   网关多出流取 isDefault 的那条；无出流或命中 END_EVENT 视为无审批节点。
 *
 * @param model 解析后的定义模型
 * @returns 入口审批节点，无法求解时返回 null
 */
export function findEntryApprovalNode(model: ParsedDefinition): ParsedNode | null {
  if (model.nodes.length === 0) {
    return null;
  }
  if (model.legacy) {
    return model.nodes[0] ?? null;
  }

  const start = model.nodes.find((node) => node.type === 'START_EVENT') ?? model.nodes[0];
  if (!start) {
    return null;
  }

  const visited = new Set<string>();
  let current: ParsedNode | undefined = start;

  while (current) {
    if (visited.has(current.key)) {
      return null; // 成环防护
    }
    visited.add(current.key);

    if (current.type === 'USER_TASK' || current.type === 'COUNTER_SIGN') {
      return current;
    }
    if (current.type === 'END_EVENT') {
      return null;
    }

    // START_EVENT / 网关：透传到下一节点
    const outgoing = model.flows.filter((flow) => flow.from === current?.key);
    if (outgoing.length === 0) {
      return null;
    }
    const nextFlow =
      outgoing.length === 1
        ? outgoing[0]
        : (outgoing.find((flow) => flow.isDefault) ?? outgoing[0]);
    current = model.nodes.find((node) => node.key === nextFlow.to);
  }
  return null;
}
