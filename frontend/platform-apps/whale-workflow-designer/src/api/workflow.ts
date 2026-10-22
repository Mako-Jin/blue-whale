// whale-workflow 契约层（Wave 5.32 冻结）：类型字段名与后端 controller/dto/vo 严格一致，禁止改名；
// 所有请求方法均为对 http.ts 的薄封装，服务内路径前缀 /workflow/definition、/workflow/instance、/workflow/task。

import { del, downloadBlob, get, post, put, type PageResult } from './http';

// 重要：后端 whale-common JacksonConfig 对 Long 统一走 ToStringSerializer，
// 雪花 ID 在 JSON 中一律为字符串。前端**必须**按 string 处理，
// 否则 Number(id) 会丢精度（>2^53）导致详情/删除等请求命中不存在的 ID（404）（Wave 5.29 P0 教训）。
/** 后端 Long 类主键/外键，统一以字符串承载 */
export type Id = string;

/** 节点类型（对应后端 ProcessNodeType 枚举，规范化 JSON 的 nodes[].type） */
export type NodeType =
  | 'START_EVENT'
  | 'END_EVENT'
  | 'USER_TASK'
  | 'EXCLUSIVE_GATEWAY'
  | 'PARALLEL_GATEWAY'
  | 'COUNTER_SIGN';

/**
 * 画布分组 / 泳道（规范化 JSON 的可选 groups[] 元素）。
 * 仅存语义元数据，容器矩形由成员节点包围盒派生，不持久化坐标。
 */
export interface ProcessModelGroup {
  key: string;
  name?: string | null;
}

/** 流程流转（对应后端 ProcessModelFlow，规范化 JSON 的 flows[] 元素） */
export interface ProcessModelFlow {
  key: string;
  from: string;
  to: string;
  condition?: string | null;
  isDefault?: boolean | null;
}

/**
 * 流程节点（对应后端 ProcessModelNode，规范化 JSON 的 nodes[] 元素）。
 * formId/formCode/x/y 为 Wave 5.32 后端补强的可空字段（节点绑定表单 + 画布坐标）。
 */
export interface ProcessModelNode {
  key: string;
  type: NodeType;
  name?: string | null;
  assignee?: string | null;
  /** 会签人账号列表（仅 COUNTER_SIGN） */
  assignees?: string[] | null;
  /** 会签策略：ALL / ANY / RATIO:n（仅 COUNTER_SIGN，缺省 ALL） */
  counterSignPolicy?: string | null;
  /** 绑定表单定义 ID（字符串承载，可空） */
  formId?: string | null;
  /** 绑定表单编码（可空） */
  formCode?: string | null;
  /** 画布 X 坐标（可空；legacy/BPMN 导入的节点无坐标） */
  x?: number | null;
  /** 画布 Y 坐标（可空；legacy/BPMN 导入的节点无坐标） */
  y?: number | null;
  /** 所属分组 key（可空；引用 groups[].key） */
  groupKey?: string | null;
}

/** 规范化流程定义 JSON（definitionJson 解析后的结构） */
export interface NormalizedDefinition {
  type: 'stateMachine';
  /** 画布分组 / 泳道（可选，旧定义无此字段） */
  groups?: ProcessModelGroup[];
  nodes: ProcessModelNode[];
  flows: ProcessModelFlow[];
}

/** 流程定义（对应后端 ProcessDefinitionSaveRequest / ProcessDefinitionVO） */
export interface ProcessDefinition {
  id?: Id;
  code: string;
  name: string;
  /** 流程定义 JSON 文本（规范化 {type,nodes[],flows[]} 或 legacy {nodes[]}） */
  definitionJson?: string | null;
  version?: number;
  /** 状态：1 正常 / 0 禁用 */
  status?: number;
  remark?: string | null;
  createTime?: string;
  updateTime?: string;
}

/** 保存流程定义入参（POST /workflow/definition、PUT /workflow/definition/{id}） */
export interface DefinitionSaveRequest {
  code: string;
  name: string;
  definitionJson?: string;
  version?: number;
  status?: number;
  remark?: string;
}

/** 流程定义版本快照（对应后端 ProcessDefinitionVersionVO） */
export interface ProcessDefinitionVersion {
  id: Id;
  definitionId: Id;
  /** 版本号 */
  version: number;
  /** 该版本的流程定义 JSON 快照 */
  definitionJson?: string | null;
  /** 发布状态：0 草稿 / 1 已发布 */
  publishStatus?: number | null;
  createTime?: string;
  updateTime?: string;
}

/** 发布指定版本入参（POST /workflow/definition/{id}/publish） */
export interface DefinitionPublishRequest {
  version: number;
}

/** 实例版本迁移入参（POST /workflow/definition/{id}/migrate） */
export interface DefinitionMigrationRequest {
  /** 目标版本号（须存在对应快照） */
  targetVersion: number;
  /** 指定实例 ID 列表；为空表示该定义下全部运行中实例（字符串承载雪花 ID） */
  instanceIds?: Id[];
}

/** 实例版本迁移结果（对应后端 DefinitionMigrationVO） */
export interface DefinitionMigrationResult {
  definitionId: Id;
  targetVersion: number;
  migrated: number;
  skipped: number;
  failed: number;
  /** 失败明细：`实例ID: 原因` */
  failures: string[];
}

/** 流程实例（对应后端 ProcessInstanceVO） */
export interface ProcessInstance {
  id: Id;
  definitionId: Id;
  definitionCode: string;
  definitionVersion: number;
  title: string;
  /** 业务键（本应用约定：绑定表单数据 ID，可空） */
  businessKey?: string | null;
  /** 状态：0 运行中 / 1 已通过 / 2 已驳回 / 3 已撤销 */
  status: number;
  /** 当前审批节点下标（0 起） */
  currentIndex?: number;
  /** 当前审批节点键（规范化定义；为空时回落 currentIndex） */
  currentNodeKey?: string | null;
  /** 发起人账号 */
  initiator?: string | null;
  createTime?: string;
  updateTime?: string;
  /** 该实例的任务列表（仅详情接口填充） */
  tasks?: ProcessTask[];
}

/** 发起流程入参（POST /workflow/instance） */
export interface InstanceStartRequest {
  definitionId: Id;
  title: string;
  businessKey?: string;
  /** 流程变量（条件选路用；随实例落库） */
  variables?: Record<string, unknown>;
}

/** 待办任务（对应后端 ProcessTaskVO；nodeKey/formId/formCode 为 Wave 5.32 后端补强字段） */
export interface ProcessTask {
  id: Id;
  instanceId: Id;
  /** 审批节点下标（0 起） */
  nodeIndex?: number;
  nodeName?: string | null;
  assignee?: string | null;
  /** 状态：0 待办 / 1 已同意 / 2 已驳回 / 3 已取消 */
  status?: number;
  comment?: string | null;
  createTime?: string;
  updateTime?: string;
  /** 建任务时的节点键 */
  nodeKey?: string | null;
  /** 节点绑定的表单定义 ID（字符串承载，可空） */
  formId?: string | null;
  /** 节点绑定的表单编码（可空） */
  formCode?: string | null;
  /** 任务类型：COUNTER_SIGN 会签 / ADD_SIGN 加签 / 空 普通审批 */
  signType?: string | null;
  /** 会签策略快照：ALL / ANY / RATIO:n */
  signPolicy?: string | null;
  /** 任务归属人（转办后仍为原审批人） */
  ownerId?: string | null;
  /** 委派来源人（非委派为空；非空时可执行 resolve） */
  delegateFrom?: string | null;
}

/** 校验问题（对应后端 ValidationIssueVO） */
export interface ValidationIssue {
  /** 校验码（ValidationCode 名称） */
  code: string;
  message: string;
  /** 级别：ERROR / WARNING */
  level: 'ERROR' | 'WARNING';
  /** 关联元素键（节点 key 或流转 key），可为 null */
  refKey?: string | null;
}

/** 校验结果（对应后端 ProcessDefinitionValidateVO） */
export interface ProcessDefinitionValidate {
  valid: boolean;
  /** 定义模式：LEGACY / NORMALIZED / BPMN */
  mode: 'LEGACY' | 'NORMALIZED' | 'BPMN';
  errors: ValidationIssue[];
  warnings: ValidationIssue[];
  /** 归一化后的规范化 JSON（仅合法时非空） */
  normalizedJson?: string | null;
  nodeCount: number;
  flowCount: number;
}

/** 校验入参（POST /workflow/definition/validate；definitionJson 与 bpmnXml 二选一） */
export interface DefinitionValidateRequest {
  definitionJson?: string;
  bpmnXml?: string;
}

/** 模拟单步（对应后端 SimulateStepVO） */
export interface SimulateStep {
  /** 步骤序号（1 起） */
  order: number;
  /** 节点 key */
  nodeKey: string;
  /** 节点名称（可空） */
  nodeName?: string | null;
  /** 节点类型（ProcessNodeType 名称） */
  nodeType?: string | null;
  /** 进入本节点的流转 key（首节点为空） */
  flowKey?: string | null;
  /** 进入本节点的流转条件（可空） */
  condition?: string | null;
  /** 进入本节点的流转是否为默认流 */
  isDefault?: boolean | null;
  /** 审批人（仅 USER_TASK） */
  assignee?: string | null;
  /** 会签人列表（仅 COUNTER_SIGN） */
  assignees?: string[] | null;
  /** 会签策略（仅 COUNTER_SIGN） */
  counterSignPolicy?: string | null;
}

/** 选路模拟入参（POST /workflow/definition/simulate） */
export interface DefinitionSimulateRequest {
  /** 流程定义 JSON（规范化或 legacy） */
  definitionJson: string;
  /** 模拟用流程变量（条件表达式求值上下文） */
  variables?: Record<string, unknown>;
}

/** 选路模拟结果（对应后端 ProcessDefinitionSimulateVO） */
export interface ProcessDefinitionSimulate {
  /** 定义结构是否合法 */
  valid: boolean;
  /** 定义模式：LEGACY / NORMALIZED */
  mode: 'LEGACY' | 'NORMALIZED';
  /** 结构错误 */
  errors: ValidationIssue[];
  /** 模拟路径（按实际走位顺序） */
  steps: SimulateStep[];
  /** 模拟是否结束 */
  terminated: boolean;
  /** 是否抵达结束事件 */
  reachedEnd: boolean;
  /** 终止节点 key（可空） */
  terminalNodeKey?: string | null;
  /** 运行期选路错误码（可空） */
  errorCode?: string | null;
  /** 运行期选路错误文案（可空） */
  errorMessage?: string | null;
}

/** BPMN 导入入参（POST /workflow/definition/bpmn） */
export interface BpmnImportRequest {
  code: string;
  name: string;
  bpmnXml: string;
  version?: number;
  status?: number;
  remark?: string;
}

/** 审批入参（approve/reject 共用） */
export interface ApprovalRequest {
  comment?: string;
  /** 本次审批追加的流程变量（供后续条件选路） */
  variables?: Record<string, unknown>;
  /** 关联表单数据 ID（可选，后端保留字段） */
  formDataId?: Id | number;
}

/** 转办 / 委派入参（PUT /workflow/task/{id}/transfer、/delegate） */
export interface TaskTransferRequest {
  targetUser: string;
}

/** 回退入参（POST /workflow/task/{id}/rollback） */
export interface TaskRollbackRequest {
  targetNodeKey: string;
}

/** 加签入参（POST /workflow/task/{id}/add-sign；单次最多 10 人） */
export interface TaskAddSignRequest {
  assignees: string[];
}

/** 流程定义分页查询条件（GET /workflow/definition/page） */
export type DefinitionQuery = {
  current?: number;
  size?: number;
  /** 流程名称（模糊匹配） */
  name?: string;
  /** 流程编码（精确匹配） */
  code?: string;
  /** 状态：1 正常 / 0 禁用 */
  status?: number;
};

/** 流程实例分页查询条件（GET /workflow/instance/page） */
export type InstanceQuery = {
  current?: number;
  size?: number;
  /** 实例标题（模糊匹配） */
  title?: string;
  /** 状态：0 运行中 / 1 已通过 / 2 已驳回 / 3 已撤销 */
  status?: number;
  /** 流程定义编码（精确匹配） */
  definitionCode?: string;
};

/** 待办任务分页查询条件（GET /workflow/task/page） */
export type TaskQuery = {
  current?: number;
  size?: number;
  /** 审批人账号（精确匹配） */
  assignee?: string;
  /** 状态：0 待办 / 1 已同意 / 2 已驳回 / 3 已取消 */
  status?: number;
  /** 所属流程实例 ID */
  instanceId?: Id;
};

/** 流程定义管理 API（服务内前缀 /workflow/definition） */
export const workflowApi = {
  definitionPage: (query?: DefinitionQuery) =>
    get<PageResult<ProcessDefinition>>('/workflow/definition/page', query),
  definitionDetail: (id: Id) => get<ProcessDefinition>(`/workflow/definition/${id}`),
  definitionCreate: (data: DefinitionSaveRequest) =>
    post<ProcessDefinition>('/workflow/definition', data),
  definitionUpdate: (id: Id, data: DefinitionSaveRequest) =>
    put<ProcessDefinition>(`/workflow/definition/${id}`, data),
  definitionRemove: (id: Id) => del<void>(`/workflow/definition/${id}`),
  // 校验：内容非法返回 valid=false（非异常），errors[] 按 refKey 映射画布元素
  definitionValidate: (data: DefinitionValidateRequest) =>
    post<ProcessDefinitionValidate>('/workflow/definition/validate', data),
  // 选路模拟：按流程变量预演走向，不创建实例（错误落在 VO 字段而非异常）
  definitionSimulate: (data: DefinitionSimulateRequest) =>
    post<ProcessDefinitionSimulate>('/workflow/definition/simulate', data),
  definitionBpmnImport: (data: BpmnImportRequest) =>
    post<ProcessDefinition>('/workflow/definition/bpmn', data),
  // BPMN 导出为原始字节流（响应非 JSON 信封），文件名从 Content-Disposition 解析
  definitionBpmnExport: (id: Id) => downloadBlob(`/workflow/definition/${id}/bpmn`),
  // 版本快照：版本号倒序；publishStatus 0 草稿 / 1 已发布
  definitionVersions: (id: Id) =>
    get<ProcessDefinitionVersion[]>(`/workflow/definition/${id}/versions`),
  // 发布指定版本：切换定义表内容，新实例按该版本发起
  definitionPublish: (id: Id, data: DefinitionPublishRequest) =>
    post<ProcessDefinition>(`/workflow/definition/${id}/publish`, data),
  // 迁移运行中实例到目标版本（instanceIds 为空表示全部运行中实例）
  definitionMigrate: (id: Id, data: DefinitionMigrationRequest) =>
    post<DefinitionMigrationResult>(`/workflow/definition/${id}/migrate`, data),

  // 流程实例 API（服务内前缀 /workflow/instance）
  instancePage: (query?: InstanceQuery) =>
    get<PageResult<ProcessInstance>>('/workflow/instance/page', query),
  instanceDetail: (id: Id) => get<ProcessInstance>(`/workflow/instance/${id}`),
  instanceStart: (data: InstanceStartRequest) => post<ProcessInstance>('/workflow/instance', data),
  instanceCancel: (id: Id) => post<void>(`/workflow/instance/${id}/cancel`),

  // 待办任务 API（服务内前缀 /workflow/task）
  taskPage: (query?: TaskQuery) => get<PageResult<ProcessTask>>('/workflow/task/page', query),
  taskApprove: (id: Id, data?: ApprovalRequest) => post<void>(`/workflow/task/${id}/approve`, data),
  taskReject: (id: Id, data?: ApprovalRequest) => post<void>(`/workflow/task/${id}/reject`, data),
  taskTransfer: (id: Id, data: TaskTransferRequest) =>
    put<void>(`/workflow/task/${id}/transfer`, data),
  taskDelegate: (id: Id, data: TaskTransferRequest) =>
    put<void>(`/workflow/task/${id}/delegate`, data),
  /** 完成委派：被委派人处理完，任务交回委派人 */
  taskResolve: (id: Id, data?: ApprovalRequest) => put<void>(`/workflow/task/${id}/resolve`, data),
  taskRollback: (id: Id, data: TaskRollbackRequest) =>
    post<void>(`/workflow/task/${id}/rollback`, data),
  /** 加签：为当前节点追加同级审批人，追加人全部同意后节点才完成 */
  taskAddSign: (id: Id, data: TaskAddSignRequest) =>
    post<void>(`/workflow/task/${id}/add-sign`, data),
};
