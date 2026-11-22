// IoT 平台接口封装：产品/物模型、设备、遥测与影子、指令、规则
//
// 路径与其后端控制器一一对应（网关不剥离 /iot 前缀）：
//   /iot/products、/iot/products/{id}/thing-model、
//   /iot/devices、/iot/commands、/iot/rules

import { http, type PageResult } from './client';

/** 产品视图 */
export interface ProductVO {
  id: string;
  code: string;
  name: string;
  protocol: string;
  description?: string;
  status: 'draft' | 'published' | 'disabled';
  deviceCount?: number;
  createTime?: string;
  updateTime?: string;
}

/** 物模型条目 */
export interface ThingModelVO {
  kind: 'property' | 'event' | 'service';
  identifier: string;
  name: string;
  dataType?: string;
  spec?: unknown;
  required?: number;
}

/** 设备视图 */
export interface DeviceVO {
  id: string;
  productId: string;
  productCode?: string;
  productName?: string;
  deviceName: string;
  nickname?: string;
  online?: number;
  lastOnlineTime?: string;
  lastReportTime?: string;
  status: 'active' | 'disabled';
  createTime?: string;
}

/** 设备创建响应（secret 仅此一次） */
export interface DeviceCreateVO extends DeviceVO {
  secret?: string;
}

/** 设备影子 */
export interface ShadowVO {
  deviceId: string;
  deviceName?: string;
  online?: number;
  reported?: Record<string, unknown>;
  desired?: Record<string, unknown>;
  version?: number;
  reportedTime?: string;
  updatedTime?: string;
}

/** 遥测数据点 */
export interface TelemetryVO {
  identifier: string;
  value?: number | null;
  valueText?: string | null;
  ts: string;
}

/** 指令视图 */
export interface CommandVO {
  id: string;
  deviceId: string;
  identifier: string;
  params?: unknown;
  messageId?: string;
  status: 'pending' | 'sent' | 'success' | 'failed' | 'timeout';
  reply?: unknown;
  issuedTime?: string;
  replyTime?: string;
  createTime?: string;
}

/** 规则触发条件 */
export interface RuleCondition {
  identifier: string;
  op: string;
  value: number;
  durationSec: number;
}

/** 规则动作配置 */
export interface RuleActionConfig {
  level?: string;
  silenceSeconds?: number;
  notifyChannels?: string[];
}

/** 规则视图 */
export interface RuleVO {
  id: string;
  name: string;
  productId?: string;
  productName?: string;
  deviceId?: string;
  deviceName?: string;
  condition?: RuleCondition;
  actionType?: string;
  actionConfig?: RuleActionConfig;
  enabled: number;
  lastFiredTime?: string;
  firedCount?: number;
  createTime?: string;
}

/** 产品分页参数 */
export interface ProductQuery extends Record<string, unknown> {
  current?: number;
  size?: number;
  keyword?: string;
  status?: string;
}

/** 设备分页参数 */
export interface DeviceQuery extends Record<string, unknown> {
  current?: number;
  size?: number;
  productId?: string;
  keyword?: string;
  status?: string;
}

/** 规则分页参数 */
export interface RuleQuery extends Record<string, unknown> {
  current?: number;
  size?: number;
  productId?: string;
  keyword?: string;
}

export const productApi = {
  page: (query: ProductQuery) => http.get<PageResult<ProductVO>>('/iot/v1/products/page', query),
  create: (body: { code: string; name: string; description?: string }) =>
    http.post<ProductVO>('/iot/v1/products', body),
  update: (id: string, body: { name: string; description?: string }) =>
    http.put<ProductVO>(`/iot/v1/products/${id}`, body),
  publish: (id: string) => http.post<ProductVO>(`/iot/v1/products/${id}/publish`),
  disable: (id: string) => http.post<ProductVO>(`/iot/v1/products/${id}/disable`),
  remove: (id: string) => http.del<void>(`/iot/v1/products/${id}`),
  thingModel: (id: string) => http.get<ThingModelVO[]>(`/iot/v1/products/${id}/thing-model`),
  saveThingModel: (id: string, items: ThingModelVO[]) =>
    http.put<ThingModelVO[]>(`/iot/v1/products/${id}/thing-model`, { items }),
};

export const deviceApi = {
  page: (query: DeviceQuery) => http.get<PageResult<DeviceVO>>('/iot/v1/devices/page', query),
  create: (body: { productId: string; deviceName: string; nickname?: string }) =>
    http.post<DeviceCreateVO>('/iot/v1/devices', body),
  update: (id: string, body: { nickname?: string; status?: string }) =>
    http.put<DeviceVO>(`/iot/v1/devices/${id}`, body),
  remove: (id: string) => http.del<void>(`/iot/v1/devices/${id}`),
  resetSecret: (id: string) => http.post<DeviceCreateVO>(`/iot/v1/devices/${id}/secret`),
  shadow: (id: string) => http.get<ShadowVO>(`/iot/v1/devices/${id}/shadow`),
  telemetry: (id: string, query?: Record<string, unknown>) =>
    http.get<TelemetryVO[]>(`/iot/v1/devices/${id}/telemetry`, query),
  commands: (query: { deviceId: string; current?: number; size?: number }) =>
    http.get<PageResult<CommandVO>>('/iot/v1/commands/page', query),
  sendCommand: (id: string, body: { identifier: string; params?: unknown; messageId?: string }) =>
    http.post<CommandVO>(`/iot/v1/devices/${id}/commands`, body),
};

export const ruleApi = {
  page: (query: RuleQuery) => http.get<PageResult<RuleVO>>('/iot/v1/rules/page', query),
  create: (body: unknown) => http.post<RuleVO>('/iot/v1/rules', body),
  update: (id: string, body: unknown) => http.put<RuleVO>(`/iot/v1/rules/${id}`, body),
  enable: (id: string, enabled: boolean) =>
    http.post<RuleVO>(`/iot/v1/rules/${id}/enable?enabled=${enabled}`),
  remove: (id: string) => http.del<void>(`/iot/v1/rules/${id}`),
};
