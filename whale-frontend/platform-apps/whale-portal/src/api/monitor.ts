// monitor.ts —— 系统服务统计
//
// GET /monitor/service/list 返回 Nacos 注册的服务摘要列表（需 sys:monitor:list）。

import { http } from './client';

/** 查询在线服务数；请求失败由调用方降级展示 */
export async function countServices(): Promise<number> {
  const list = await http.get<unknown[]>('/monitor/v1/service/list');
  return list?.length ?? 0;
}
