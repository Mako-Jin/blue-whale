// 画布模型 ↔ 规范化 JSON（definitionJson）的序列化 / 反序列化 —— 纯函数，便于复用与单测。
//
// 冻结契约（Wave 5.32 spec §3.3）：规范化 JSON 为 { type:"stateMachine", nodes[], flows[] }；
// 节点键须匹配 ^[A-Za-z0-9_-]{1,64}$；key 缺失 / 非法 / 重复时自动生成 n1/f1…（避开已占用）；
// 缺 x/y 的节点（legacy {"nodes":[…]} 或 BPMN 导入）按「列宽 220 / 行高 120」网格自动布局并在保存时回填。
// legacy 兼容：根对象无 type 且无 flows → 节点顺序即流转顺序（f1..f(n-1)），节点统一按 USER_TASK
//   （与后端 ProcessModelParser#legacy 归一化口径一致）。

import type { NodeType, NormalizedDefinition, ProcessModelFlow, ProcessModelNode } from '../../api/workflow';

/** 画布本体（SVG 容器）的 droppable id */
export const CANVAS_DROPPABLE_ID = 'wf-canvas';

/** 画布节点：坐标以图形左上角为基准（保证圆形/菱形/矩形统一） */
export interface CanvasNode {
  key: string;
  type: NodeType;
  name: string;
  assignee: string;
  formId: string | null;
  formCode: string | null;
  x: number;
  y: number;
}

/** 画布流转 */
export interface CanvasFlow {
  key: string;
  from: string;
  to: string;
  condition: string;
  isDefault: boolean;
}

/** 画布模型 */
export interface CanvasModel {
  nodes: CanvasNode[];
  flows: CanvasFlow[];
}

/** 画布选中元素（节点或流转） */
export interface Selection {
  kind: 'node' | 'flow';
  key: string;
}

/** 节点图形尺寸（宽 × 高，左上角基准） */
export const NODE_SIZE: Record<NodeType, { width: number; height: number }> = {
  START_EVENT: { width: 64, height: 64 },
  END_EVENT: { width: 64, height: 64 },
  USER_TASK: { width: 168, height: 64 },
  EXCLUSIVE_GATEWAY: { width: 76, height: 76 },
};

/** 节点类型主色（与图形描边一致） */
export const NODE_TYPE_COLORS: Record<NodeType, string> = {
  START_EVENT: '#52c41a',
  END_EVENT: '#f5222d',
  USER_TASK: '#1677ff',
  EXCLUSIVE_GATEWAY: '#fa8c16',
};

/** 节点类型中文名 */
export const NODE_TYPE_LABELS: Record<NodeType, string> = {
  START_EVENT: '开始事件',
  END_EVENT: '结束事件',
  USER_TASK: '用户任务',
  EXCLUSIVE_GATEWAY: '排他网关',
};

/** 节点类型下拉选项（属性面板 type 编辑） */
export const NODE_TYPE_OPTIONS: { value: NodeType; label: string }[] = (
  ['START_EVENT', 'END_EVENT', 'USER_TASK', 'EXCLUSIVE_GATEWAY'] as NodeType[]
).map((type) => ({ value: type, label: NODE_TYPE_LABELS[type] }));

/** 新建节点的默认名称 */
const DEFAULT_NODE_NAME: Record<NodeType, string> = {
  START_EVENT: '开始',
  END_EVENT: '结束',
  USER_TASK: '审批任务',
  EXCLUSIVE_GATEWAY: '网关',
};

/** 自动布局网格参数（缺坐标节点专用） */
const AUTO_LAYOUT = { originX: 80, originY: 60, columnWidth: 220, rowHeight: 120, columns: 4 };

/** 节点/流转 key 合法字符集（与后端 KEY_PATTERN 一致） */
const KEY_PATTERN = /^[A-Za-z0-9_-]{1,64}$/;

/** 可识别节点类型集合 */
const KNOWN_TYPES: NodeType[] = ['START_EVENT', 'END_EVENT', 'USER_TASK', 'EXCLUSIVE_GATEWAY'];

/** key 是否合法（^[A-Za-z0-9_-]{1,64}$） */
export function isValidKey(key: string): boolean {
  return KEY_PATTERN.test(key);
}

/** 生成未占用的顺序键（prefix + 递增数字），避开 existing 中已存在的键 */
export function nextKey(prefix: 'n' | 'f', existing: Iterable<string>): string {
  const used = new Set(existing);
  let index = 1;
  while (used.has(`${prefix}${index}`)) {
    index += 1;
  }
  return `${prefix}${index}`;
}

/** 新建画布节点（默认名 + 默认审批人，assignee 仅对 USER_TASK 有意义） */
export function createNode(
  type: NodeType,
  key: string,
  x: number,
  y: number,
  assignee = '',
): CanvasNode {
  return {
    key,
    type,
    name: DEFAULT_NODE_NAME[type],
    assignee: type === 'USER_TASK' ? assignee : '',
    formId: null,
    formCode: null,
    x,
    y,
  };
}

/** 新建画布流转 */
export function createFlow(from: string, to: string, key: string): CanvasFlow {
  return { key, from, to, condition: '', isDefault: false };
}

/** 按 key 查找节点（未命中返回 undefined） */
export function findNode(model: CanvasModel, key: string): CanvasNode | undefined {
  return model.nodes.find((node) => node.key === key);
}

/** 反序列化草稿节点（key 可能缺失，由 parseDefinition 统一补齐） */
interface DraftNode {
  key: string;
  type: NodeType;
  name: string;
  assignee: string;
  formId: string | null;
  formCode: string | null;
  x: number | null;
  y: number | null;
}

/** 读取文本字段，非字符串统一返回空串（便于画布受控编辑） */
function readText(value: unknown): string {
  return typeof value === 'string' ? value : '';
}

/** 读取整型坐标，非法返回 null */
function readCoord(value: unknown): number | null {
  return typeof value === 'number' && Number.isFinite(value) ? Math.round(value) : null;
}

/** 类型名归一化：trim + 大写 + '-' 转 '_'（与后端 from() 的宽容解析一致） */
function normalizeTypeName(value: unknown): string {
  return readText(value).trim().toUpperCase().replace(/-/g, '_');
}

/** 读取节点类型；缺失 / 未知统一回落 USER_TASK（对齐后端 legacy 归一化） */
function readNodeType(value: unknown): NodeType {
  const normalized = normalizeTypeName(value);
  return KNOWN_TYPES.find((type) => type === normalized) ?? 'USER_TASK';
}

/** 读取单个节点为草稿（obj 非对象时按空对象兜底） */
function readDraftNode(item: unknown): DraftNode {
  const raw = (item && typeof item === 'object' ? item : {}) as Record<string, unknown>;
  return {
    key: readText(raw.key).trim(),
    type: readNodeType(raw.type),
    name: readText(raw.name),
    assignee: readText(raw.assignee),
    formId: readText(raw.formId) || null,
    formCode: readText(raw.formCode) || null,
    x: readCoord(raw.x),
    y: readCoord(raw.y),
  };
}

/** 读取单个流转为草稿 */
function readDraftFlow(item: unknown): CanvasFlow {
  const raw = (item && typeof item === 'object' ? item : {}) as Record<string, unknown>;
  return {
    key: readText(raw.key).trim(),
    from: readText(raw.from).trim(),
    to: readText(raw.to).trim(),
    condition: readText(raw.condition),
    isDefault: raw.isDefault === true,
  };
}

/** 补齐 key：非法 / 重复 → 生成 n{k} 并登记占用（就地改草稿） */
function ensureKeys<T extends { key: string }>(items: T[], prefix: 'n' | 'f'): void {
  const used = new Set<string>();
  items.forEach((item) => {
    const candidate = item.key;
    item.key = candidate && isValidKey(candidate) && !used.has(candidate)
      ? candidate
      : nextKey(prefix, used);
    used.add(item.key);
  });
}

/** 补齐坐标：缺 x/y 的节点按网格排布 */
function ensureCoordinates(drafts: DraftNode[]): CanvasNode[] {
  return drafts.map((draft, index) => {
    if (draft.x !== null && draft.y !== null) {
      return { ...draft, x: draft.x, y: draft.y };
    }
    const col = index % AUTO_LAYOUT.columns;
    const row = Math.floor(index / AUTO_LAYOUT.columns);
    return {
      ...draft,
      x: AUTO_LAYOUT.originX + col * AUTO_LAYOUT.columnWidth,
      y: AUTO_LAYOUT.originY + row * AUTO_LAYOUT.rowHeight,
    };
  });
}

/**
 * 解析 definitionJson 为画布模型。
 * 空 / 非法 JSON → 空模型；兼容 legacy {"nodes":[…]}(节点顺序即流转顺序)。
 *
 * @param definitionJson 后端返回的定义 JSON 文本
 * @returns 画布模型（节点均带 x/y，key 全部合法且唯一）
 */
export function parseDefinition(definitionJson?: string | null): CanvasModel {
  if (!definitionJson || !definitionJson.trim()) {
    return { nodes: [], flows: [] };
  }
  let root: unknown;
  try {
    root = JSON.parse(definitionJson);
  } catch {
    return { nodes: [], flows: [] };
  }
  if (!root || typeof root !== 'object' || Array.isArray(root)) {
    return { nodes: [], flows: [] };
  }
  const obj = root as Record<string, unknown>;
  const rawNodes = Array.isArray(obj.nodes) ? obj.nodes : [];
  if (rawNodes.length === 0) {
    return { nodes: [], flows: [] };
  }

  const drafts = rawNodes.map((item) => readDraftNode(item));
  ensureKeys(drafts, 'n');
  const nodes = ensureCoordinates(drafts);
  const nodeKeys = new Set(nodes.map((node) => node.key));

  const rawFlows = Array.isArray(obj.flows) ? obj.flows : [];
  const declaredType = readText(obj.type).trim().toLowerCase();
  // 判定口径对齐后端：存在 flows 数组或 type==stateMachine → 规范化（flows 以数组为准）；
  // 两者皆无 → legacy（节点顺序即流转顺序）
  if (Array.isArray(obj.flows) || declaredType === 'statemachine') {
    const flows = rawFlows
      .map((item) => readDraftFlow(item))
      .filter((flow) => flow.from !== flow.to && nodeKeys.has(flow.from) && nodeKeys.has(flow.to));
    ensureKeys(flows, 'f');
    return { nodes, flows };
  }

  // legacy：无 flows → 节点顺序即流转顺序 f1..f(n-1)
  const flows: CanvasFlow[] = [];
  for (let i = 0; i + 1 < nodes.length; i += 1) {
    flows.push(createFlow(nodes[i].key, nodes[i + 1].key, `f${i + 1}`));
  }
  return { nodes, flows };
}

/** 画布模型 → 规范化定义对象（{type:"stateMachine",nodes[],flows[]}） */
export function toNormalized(model: CanvasModel): NormalizedDefinition {
  return {
    type: 'stateMachine',
    nodes: model.nodes.map((node) => {
      const item: ProcessModelNode = {
        key: node.key,
        type: node.type,
        name: node.name,
        assignee: node.assignee,
        x: node.x,
        y: node.y,
      };
      if (node.formId) {
        item.formId = node.formId;
      }
      if (node.formCode) {
        item.formCode = node.formCode;
      }
      return item;
    }),
    flows: model.flows.map((flow) => {
      const item: ProcessModelFlow = { key: flow.key, from: flow.from, to: flow.to, isDefault: flow.isDefault };
      if (flow.condition) {
        item.condition = flow.condition;
      }
      return item;
    }),
  };
}

/** 画布模型 → 规范化定义 JSON 文本（保存 / 校验入参） */
export function serializeDefinition(model: CanvasModel): string {
  return JSON.stringify(toNormalized(model));
}
