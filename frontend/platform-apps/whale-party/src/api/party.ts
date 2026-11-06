// 党政工会接口封装（M-P0：门禁状态）。
//
// 权限码与 migration-5.88-party-m0.sql 对齐：
//   sys:party:browse（1950，门禁与工作台）。
// 路径前缀 `/party` 由网关注入，网关不剥离一级前缀（REQ-ARCH-003）。

import { http } from './client';

/** 门禁状态 */
export interface PartyMeta {
  demandGatePassed: boolean;
  message: string;
  milestones: string[];
  boundaries: string[];
}

/**
 * 查询党政工会门禁状态。
 *
 * @returns 门禁状态、里程碑与领域边界
 */
export function getMeta(): Promise<PartyMeta> {
  return http.get<PartyMeta>('/party/meta');
}
