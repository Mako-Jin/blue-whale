// whale-audit 审计日志查询 API 客户端（经网关 /audit/** 转发）

import { get, type PageResult } from './client';

/** 审计日志视图对象（字段与后端 AuditLogVO 对齐） */
export interface AuditLogVO {
  id: number | string;
  traceId?: string;
  userId?: number | string;
  username?: string;
  module?: string;
  operation?: string;
  operateType?: string;
  method?: string;
  requestUri?: string;
  httpMethod?: string;
  targetType?: string;
  targetId?: string;
  responseCode?: number;
  bizStatus?: number;
  errorMsg?: string;
  ip?: string;
  costMs?: number;
  createTime?: string;
}

/** 审计日志分页查询条件（与后端 AuditLogQueryRequest 对齐） */
export interface AuditLogQuery {
  current?: number;
  size?: number;
  username?: string;
  module?: string;
  operateType?: string;
  bizStatus?: number;
  traceId?: string;
  startTime?: string;
  endTime?: string;
  /** URI 关键字（前端筛选项；后端未绑定时安全忽略） */
  requestUri?: string;
  /** HTTP 方法（前端筛选项；后端未绑定时安全忽略） */
  httpMethod?: string;
}

// 分页查询审计日志：GET /audit/log/page
export function pageAuditLog(params: AuditLogQuery): Promise<PageResult<AuditLogVO>> {
  return get<PageResult<AuditLogVO>>('/audit/log/page', params as Record<string, unknown>);
}
