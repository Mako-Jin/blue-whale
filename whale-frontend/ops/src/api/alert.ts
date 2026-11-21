// whale-alert 告警规则 / 记录查询 API 客户端（经网关 /alert/** 转发）

import { get, type PageResult } from './client';

/** 告警规则视图对象（字段与后端 alert_rule 对齐） */
export interface AlertRuleVO {
  id: number | string;
  ruleName?: string;
  ruleCode?: string;
  metric?: string;
  target?: string;
  operator?: string;
  threshold?: number;
  durationSeconds?: number;
  level?: string;
  channel?: string;
  status?: number;
  remark?: string;
  createTime?: string;
  updateTime?: string;
}

/** 告警规则分页查询条件 */
export interface AlertRuleQuery {
  current?: number;
  size?: number;
  keyword?: string;
  metric?: string;
  level?: string;
  status?: number;
}

// 分页查询告警规则：GET /alert/v1/rule/page
export function pageAlertRule(params: AlertRuleQuery): Promise<PageResult<AlertRuleVO>> {
  return get<PageResult<AlertRuleVO>>('/alert/v1/rule/page', params as Record<string, unknown>);
}

/** 告警记录视图对象（字段与后端 alert_record 对齐） */
export interface AlertRecordVO {
  id: number | string;
  ruleId?: number | string;
  ruleCode?: string;
  alertLevel?: string;
  content?: string;
  status?: string;
  triggerTime?: string;
  recoverTime?: string;
  createTime?: string;
}

/** 告警记录分页查询条件 */
export interface AlertRecordQuery {
  current?: number;
  size?: number;
  ruleCode?: string;
  alertLevel?: string;
  status?: string;
}

// 分页查询告警记录：GET /alert/v1/record/page
export function pageAlertRecord(params: AlertRecordQuery): Promise<PageResult<AlertRecordVO>> {
  return get<PageResult<AlertRecordVO>>('/alert/v1/record/page', params as Record<string, unknown>);
}
