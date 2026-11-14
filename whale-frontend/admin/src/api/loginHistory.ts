// 登录历史 API（经网关 → whale-service-auth /login-history）
// 权限码：sys:login:history:list(2411) / sys:login:history:export(2412)
// （V22 种子曾写 sys:loginhistory:*，与代码注解不一致，V29 已就地修正）

import { get, type PageResult } from './client';
import { getToken } from './token';

/** 登录历史（字段对齐后端 LoginHistoryResponse） */
export interface LoginHistoryVO {
  id: string;
  tenantId?: string;
  /** 后端 Long 序列化为字符串 */
  userId?: string;
  username?: string;
  /** 登录方式（PASSWORD / SMS 等） */
  loginType?: string;
  /** SUCCESS / FAILURE / LOGOUT */
  loginResult?: string;
  failureReason?: string;
  ip?: string;
  userAgent?: string;
  deviceFingerprint?: string;
  loginTime?: string;
}

export interface LoginHistoryPageParams {
  userId?: string;
  result?: string;
  startTime?: string;
  endTime?: string;
  current: number;
  size: number;
}

/** 分页查询登录历史 */
export function pageLoginHistories(
  params: LoginHistoryPageParams,
): Promise<PageResult<LoginHistoryVO>> {
  return get<PageResult<LoginHistoryVO>>('/login-history/v1/page', { ...params });
}

/**
 * 导出登录历史 CSV（上限 10000 行）。
 * 返回二进制流，统一 HTTP 客户端只解析 JSON 信封，故走原生 fetch。
 */
export async function exportLoginHistories(
  params: Omit<LoginHistoryPageParams, 'current' | 'size'>,
): Promise<void> {
  const query = new URLSearchParams();
  if (params.userId) {
    query.set('userId', params.userId);
  }
  if (params.result) {
    query.set('result', params.result);
  }
  if (params.startTime) {
    query.set('startTime', params.startTime);
  }
  if (params.endTime) {
    query.set('endTime', params.endTime);
  }
  const token = getToken();
  const response = await fetch(`/login-history/v1/export?${query.toString()}`, {
    method: 'GET',
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  if (!response.ok) {
    throw new Error(`导出失败（HTTP ${response.status}）`);
  }
  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = `login-history-${new Date().toISOString().slice(0, 19).replaceAll(':', '')}.csv`;
  document.body.appendChild(anchor);
  anchor.click();
  document.body.removeChild(anchor);
  URL.revokeObjectURL(url);
}
