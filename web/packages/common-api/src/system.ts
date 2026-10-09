import type { HttpClient } from 'common-tools/http';

import type { ApiSchemas, DataOf } from './types';
import type { paths } from './generated/schema';

type HealthOp = paths['/api/health']['get'];

/** 服务健康状态 */
export type Health = NonNullable<DataOf<HealthOp>>;

/** 依赖组件状态，取值 `UP` / `DOWN` */
export type HealthDependencyStatus = ApiSchemas['HealthVO']['dependencies'];

/** 系统域接口 */
export interface SystemApi {
  health(): Promise<Health>;
}

/** 健康检查路径，受契约约束 */
const PATH_HEALTH = '/api/health' satisfies keyof paths;

export function createSystemApi(http: HttpClient): SystemApi {
  return {
    // 探测类请求：{ silent: true } 让失败不写日志 —— 后端没起时，
    // 轮询健康检查会在控制台刷出一片错误，把真正有价值的日志淹没
    health: () => http.get<Health>(PATH_HEALTH, { silent: true }),
  };
}
