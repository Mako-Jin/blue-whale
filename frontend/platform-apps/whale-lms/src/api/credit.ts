// LMS 学分域接口封装（M4-2：学分规则 / 记账 / 台账 / 年度汇总）。
//
// 记账幂等由服务端唯一键保证：同一学员在同一业务事件上重复记账返回同一条记录。
// 来源 ID 的语义按类型区分——EXAM 传考试 ID（不是作答 ID），否则补考会重复记分。

import { http } from './client';

/** 学分来源 */
export type CreditSource = 'COURSE' | 'EXAM' | 'TRAINING';

/** 分页信封 */
export interface PageData<T> {
  records: T[];
  total: number;
  current: number;
  size: number;
}

/** 学分规则 */
export interface CreditRuleItem {
  id: number;
  ruleCode: string;
  name: string;
  sourceType: CreditSource;
  matchId: number;
  matchAll: boolean;
  credit: number;
  status: 'ENABLED' | 'DISABLED';
  remark?: string;
  createTime?: string;
  updateTime?: string;
}

/** 学分台账 */
export interface CreditLedgerItem {
  id: number;
  userId: number;
  username?: string;
  sourceType: CreditSource;
  sourceId: number;
  ruleCode: string;
  credit: number;
  occurredAt: string;
  periodYear: number;
  recordedBy?: number;
  remark?: string;
}

/** 按来源分组的学分 */
export interface SourceCredit {
  sourceType: CreditSource;
  credit: number;
  count: number;
}

/** 年度汇总 */
export interface CreditSummary {
  userId: number;
  periodYear?: number;
  totalCredit: number;
  items: SourceCredit[];
}

/** 规则分页 */
export function pageCreditRules(params: {
  keyword?: string;
  sourceType?: string;
  status?: string;
  current?: number;
  size?: number;
}) {
  return http.get<PageData<CreditRuleItem>>('/lms/credit/rule/page', { ...params });
}

/** 新建规则 */
export function createCreditRule(body: {
  ruleCode: string;
  name: string;
  sourceType: CreditSource;
  matchId?: number;
  credit: number;
  remark?: string;
}) {
  return http.post<number>('/lms/credit/rule', body);
}

/** 编辑规则 */
export function updateCreditRule(
  id: number,
  body: {
    ruleCode: string;
    name: string;
    sourceType: CreditSource;
    matchId?: number;
    credit: number;
    remark?: string;
  },
) {
  return http.put<void>(`/lms/credit/rule/${id}`, body);
}

/** 删除规则 */
export function deleteCreditRule(id: number) {
  return http.del<void>(`/lms/credit/rule/${id}`);
}

/** 启用 / 停用规则 */
export function changeCreditRuleStatus(id: number, enabled: boolean) {
  return http.put<void>(`/lms/credit/rule/${id}/status?enabled=${enabled}`);
}

/** 记账（幂等） */
export function accrueCredit(body: {
  userId: number;
  username?: string;
  sourceType: CreditSource;
  sourceId: number;
  occurredAt?: string;
  remark?: string;
}) {
  return http.post<number>('/lms/credit/accrue', body);
}

/** 台账分页（管理端） */
export function pageCredits(params: {
  keyword?: string;
  userId?: number;
  sourceType?: string;
  periodYear?: number;
  current?: number;
  size?: number;
}) {
  return http.get<PageData<CreditLedgerItem>>('/lms/credit/page', { ...params });
}

/** 年度汇总 */
export function creditSummary(params: { userId?: number; periodYear?: number }) {
  return http.get<CreditSummary>('/lms/credit/summary', { ...params });
}

/** 我的学分台账 */
export function myCredits(params: { sourceType?: string; periodYear?: number; current?: number; size?: number }) {
  return http.get<PageData<CreditLedgerItem>>('/lms/credit/mine', { ...params });
}
