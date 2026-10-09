/**
 * common-api · 前端契约包
 *
 * **类型全部由 `docs/api/openapi.json` 生成，接口形状零手写。**
 *
 * 更新流程（后端改接口后）：
 * ```bash
 * bash scripts/export-openapi.sh          # 从运行中的应用导出契约
 * pnpm --filter common-api gen:api        # 重新生成类型
 * ```
 * 生成产物 `src/generated/schema.d.ts` **入库**：这样前端构建不依赖后端是否可运行，
 * 契约的每一次变化也都会在 code review 里显式出现。
 */

export { createApiClient } from './client';
export type { ApiClient } from './client';

export { createLogApi } from './log';
export type {
  ClientLogItem,
  ClientLogLevelCount,
  ClientLogPage,
  ClientLogQuery,
  ClientLogReportBody,
  ClientLogReportRequest,
  ClientLogReportResult,
  ClientLogStats,
  ClientLogStatsQuery,
  ClientLogVO,
  LogApi,
} from './log';

export { createSystemApi } from './system';
export type { Health, HealthDependencyStatus, SystemApi } from './system';

export type { ApiPaths, ApiSchemas, BodyOf, DataOf, EnvelopeOf, PathOf, QueryOf } from './types';
export type { JsonArray, JsonObject, JsonValue, WithJsonPayload } from './types';
