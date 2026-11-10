// 流程定义页的常量、共享类型与下载工具。

import type { ValidationIssue } from '../../api/workflow';

/** 新建定义时写入的最小合法骨架（规范化 JSON），后续在设计器中补齐 */
export const MINIMAL_DEFINITION_JSON = JSON.stringify({
  type: 'stateMachine',
  nodes: [{ key: 'start', type: 'START_EVENT', name: '开始' }],
  flows: [],
});

/** 查询条件（写入 state 后触发请求，空串由 http 层自动忽略） */
export interface QueryState {
  current: number;
  size: number;
  code?: string;
  name?: string;
}

/** 查询表单值 */
export interface SearchValues {
  code?: string;
  name?: string;
}

/** 新建弹窗表单值 */
export interface CreateFormValues {
  code: string;
  name: string;
}

/** 编辑弹窗表单值 */
export interface EditFormValues {
  code: string;
  name: string;
}

/** BPMN 导入弹窗表单值 */
export interface BpmnFormValues {
  code: string;
  name: string;
  bpmnXml: string;
}

/** 校验结果表格行：错误与警告合并展示，以 level 区分 */
export interface IssueRow extends ValidationIssue {
  rowKey: string;
}

/** 触发浏览器下载原始字节流（参考 form-designer 的导出用法） */
export function triggerDownload(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}
