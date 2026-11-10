// 孪生子应用 API 层：场景 CRUD + 实体场景树 + 模型预览取流
//
// 后端统一返回 Result 信封（code===0 成功）；请求走相对路径 /twin/**，
// 由基座 / Nginx 经网关代理（不剥离一级前缀，REQ-ARCH-003）。

import { getToken, http } from './client';

/** 场景视图对象（对齐后端 SceneVO） */
export interface SceneVO {
  id: string;
  code: string;
  name: string;
  description?: string | null;
  thumbnailFileId?: string | null;
  sceneConfig?: string | null;
  version: number;
  status: 'draft' | 'published' | 'disabled';
  entityCount: number;
  /** 未恢复告警数 / 其中严重 / 其中未确认（仅列表接口装配，详情为空） */
  unresolvedAlerts?: number | null;
  criticalAlerts?: number | null;
  unackedAlerts?: number | null;
  createdBy?: string | null;
  createTime?: string | null;
  updateTime?: string | null;
}

/** 场景保存请求 */
export interface SceneSaveRequest {
  code: string;
  name: string;
  description?: string;
  thumbnailFileId?: number | null;
  sceneConfig?: string | null;
}

/** 孪生实体视图对象（children 构成场景树） */
export interface EntityVO {
  id: string;
  sceneId: string;
  entityCode: string;
  name: string;
  parentId?: string | null;
  modelFileId?: string | null;
  transformJson?: string | null;
  extraConfig?: string | null;
  sortOrder: number;
  children: EntityVO[];
}

/** 实体保存请求 */
export interface EntitySaveRequest {
  entityCode: string;
  name: string;
  parentId?: number | null;
  modelFileId?: number | null;
  transformJson?: string | null;
  extraConfig?: string | null;
  sortOrder?: number;
}

/** 分页信封（对齐后端 PageResult） */
export interface Paged<T> {
  records: T[];
  total: number;
  current: number;
  size: number;
}

// ---- 场景 ----

export const sceneApi = {
  page: (query?: Record<string, unknown>) =>
    http.get<Paged<SceneVO>>('/twin/scenes/page', query),
  get: (id: string) => http.get<SceneVO>(`/twin/scenes/${id}`),
  create: (body: SceneSaveRequest) => http.post<SceneVO>('/twin/scenes', body),
  update: (id: string, body: SceneSaveRequest) =>
    http.put<SceneVO>(`/twin/scenes/${id}`, body),
  publish: (id: string, body?: { remark?: string }) =>
    http.post<SceneVO>(`/twin/scenes/${id}/publish`, body ?? {}),
  disable: (id: string) => http.post<SceneVO>(`/twin/scenes/${id}/disable`),
  remove: (id: string) => http.del<void>(`/twin/scenes/${id}`),
};

// ---- 实体（场景树）----

export const entityApi = {
  tree: (sceneId: string) => http.get<EntityVO[]>(`/twin/scenes/${sceneId}/entities/tree`),
  list: (sceneId: string) => http.get<EntityVO[]>(`/twin/scenes/${sceneId}/entities`),
  create: (sceneId: string, body: EntitySaveRequest) =>
    http.post<EntityVO>(`/twin/scenes/${sceneId}/entities`, body),
  update: (sceneId: string, id: string, body: EntitySaveRequest) =>
    http.put<EntityVO>(`/twin/scenes/${sceneId}/entities/${id}`, body),
  remove: (sceneId: string, id: string) =>
    http.del<void>(`/twin/scenes/${sceneId}/entities/${id}`),
};

/**
 * 取模型预览地址（GLB/GLTF 经 whale-file /file/{id}/preview）。
 *
 * <p>预览接口需登录态，three 的 GLTFLoader 无法带请求头，因此由本函数
 * fetch 为 Blob 再转 objectURL；加载完成后调用方须 {@link revokeModelUrl} 释放。</p>
 *
 * @param fileId 文件ID
 * @returns objectURL
 */
export async function fetchModelUrl(fileId: string): Promise<string> {
  const token = getToken();
  const response = await fetch(`/file/${fileId}/preview`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  if (!response.ok) {
    throw new Error(`模型预览拉取失败：HTTP ${response.status}`);
  }
  const blob = await response.blob();
  return URL.createObjectURL(blob);
}

/** 释放 fetchModelUrl 生成的 objectURL */
export function revokeModelUrl(url: string): void {
  URL.revokeObjectURL(url);
}

// ---- 数据绑定（M2）----

/** 绑定视图对象（对齐后端 BindingVO） */
export interface BindingVO {
  id: string;
  entityId: string;
  entityCode?: string | null;
  entityName?: string | null;
  sourceType: 'iot_device' | 'internal_api';
  bindKey: string;
  propertyMap?: string | null;
  pollIntervalMs: number;
  enabled: number;
  createTime?: string | null;
}

/** 绑定保存请求 */
export interface BindingSaveRequest {
  entityId: number;
  sourceType: 'iot_device' | 'internal_api';
  bindKey: string;
  propertyMap?: string | null;
  pollIntervalMs?: number;
  enabled?: number;
}

/** 绑定实时态（对齐后端 BindingStateVO；values 键为实体状态键） */
export interface BindingStateVO {
  bindingId: string;
  entityId: string;
  entityCode: string;
  sourceType: string;
  bindKey: string;
  online: boolean;
  values: Record<string, unknown>;
  ts?: string | null;
}

/** IoT 设备精简项（绑定设备选择器用，来自 /iot/devices 分页） */
export interface IotDeviceItem {
  id: string;
  deviceName: string;
  nickname?: string | null;
  online?: number;
}

export const bindingApi = {
  list: (sceneId: string) => http.get<BindingVO[]>(`/twin/scenes/${sceneId}/bindings`),
  create: (sceneId: string, body: BindingSaveRequest) =>
    http.post<BindingVO>(`/twin/scenes/${sceneId}/bindings`, body),
  update: (sceneId: string, id: string, body: BindingSaveRequest) =>
    http.put<BindingVO>(`/twin/scenes/${sceneId}/bindings/${id}`, body),
  remove: (sceneId: string, id: string) =>
    http.del<void>(`/twin/scenes/${sceneId}/bindings/${id}`),
  state: (sceneId: string) =>
    http.get<BindingStateVO[]>(`/twin/scenes/${sceneId}/bindings/state`),
};

/** 拉取 IoT 设备精简列表（绑定选择器；失败返回空数组不打断查看器） */
export async function fetchIotDevices(): Promise<IotDeviceItem[]> {
  try {
    const page = await http.get<Paged<IotDeviceItem>>('/iot/devices', {
      current: 1,
      size: 200,
    });
    return page.records ?? [];
  } catch {
    return [];
  }
}

// ---- 阈值告警（6.06 M2）----

/** SSE alert 帧条目（对齐后端 BindingAlertVO：级别变化才推） */
export interface BindingAlertVO {
  bindingId: string;
  entityId?: string | null;
  entityCode?: string | null;
  stateKey: string;
  value?: unknown;
  previous?: string | null;
  level: 'normal' | 'warn' | 'critical';
  thresholds?: number[];
  ts?: string | null;
}

/** 告警视图（对齐后端 TwinAlertVO：历史查询/未恢复列表） */
export interface TwinAlertVO {
  id: string;
  /** 场景ID与场景编码/名称：跨场景总览才有值（场景维度接口留空） */
  sceneId?: string | null;
  sceneCode?: string | null;
  sceneName?: string | null;
  entityId?: string | null;
  entityCode?: string | null;
  bindingId: string;
  stateKey: string;
  value?: string | null;
  previousLevel?: string | null;
  level: 'normal' | 'warn' | 'critical';
  thresholds?: number[] | null;
  resolved?: boolean;
  resolveTime?: string | null;
  createTime?: string | null;
  acked?: boolean;
  ackTime?: string | null;
  ackBy?: string | null;
}

export const alertApi = {
  list: (sceneId: string, query?: Record<string, unknown>) =>
    http.get<TwinAlertVO[]>(`/twin/scenes/${sceneId}/alerts`, query),
  unresolved: (sceneId: string) =>
    http.get<TwinAlertVO[]>(`/twin/scenes/${sceneId}/alerts/unresolved`),
  ack: (sceneId: string, alertId: string) =>
    http.post<TwinAlertVO>(`/twin/scenes/${sceneId}/alerts/${alertId}/ack`),
};

/** 平台级告警统计（对齐后端 TwinAlertSummaryVO；口径均为未恢复） */
export interface TwinAlertSummaryVO {
  unresolved: number;
  critical: number;
  warn: number;
  unacked: number;
}

/**
 * 跨场景告警总览（Wave 6.08 M1 接口）：不依赖「先选场景」。
 *
 * <p>条目自带 sceneId/sceneName，可直接跳转对应场景查看器；
 * 确认操作仍走场景维度接口（{@link alertApi.ack}）。</p>
 */
export const alertOverviewApi = {
  /** 跨场景未恢复列表（默认 100，上限 200） */
  unresolved: (limit?: number) =>
    http.get<TwinAlertVO[]>('/twin/alerts/unresolved', { limit }),
  summary: () => http.get<TwinAlertSummaryVO>('/twin/alerts/summary'),
};

// ---- 告警通知设置与投递记录（6.10 M2）----

/** 告警级别门槛 */
export type AlertNotifyLevel = 'warn' | 'critical';

/** 通道 */
export type AlertDispatchChannel = 'WEBHOOK' | 'INAPP';

/** 投递结果 */
export type AlertDispatchStatus = 'SUCCESS' | 'FAILED' | 'SKIPPED';

/**
 * 场景级通知设置（对齐后端 TwinAlertNotifySettingsVO）。
 *
 * <p>原始字段为 {@code null} 表示「跟随全局」；{@code effective*} 是叠加全局后的实际生效值
 * （场景只能收紧：关通道、抬门槛，突破不了全局开关）。</p>
 */
export interface TwinAlertNotifySettingsVO {
  sceneId: string;
  webhookEnabled?: boolean | null;
  inappEnabled?: boolean | null;
  minLevel?: AlertNotifyLevel | null;
  /** 场景级恢复通知开关（6.11；null = 跟随全局） */
  recoveryEnabled?: boolean | null;
  /** 场景级静默开始（HH:mm；null = 跟随全局） */
  quietStart?: string | null;
  /** 场景级静默结束（HH:mm） */
  quietEnd?: string | null;
  userIds: string[];
  fallbackToCreator: boolean;
  effectiveWebhookEnabled: boolean;
  effectiveInappEnabled: boolean;
  effectiveMinLevel: AlertNotifyLevel;
  effectiveRecoveryEnabled: boolean;
  /** 实际生效的静默窗口（全局口径；场景窗口为叠加） */
  effectiveQuietStart?: string | null;
  effectiveQuietEnd?: string | null;
  /** 当前时刻是否处于静默（后端算好，前端只展示） */
  quietNow: boolean;
  /** 静默期内严重告警是否穿透 */
  quietCriticalBypass: boolean;
  globalMinLevel: AlertNotifyLevel;
  globalWebhookReady: boolean;
  globalInappEnabled: boolean;
  globalRecoveryEnabled: boolean;
  globalQuietStart?: string | null;
  globalQuietEnd?: string | null;
}

/** 保存请求（null/缺省 = 清除该项，回到跟随全局） */
export interface TwinAlertNotifySettingsRequest {
  webhookEnabled?: boolean | null;
  inappEnabled?: boolean | null;
  minLevel?: AlertNotifyLevel | null;
  recoveryEnabled?: boolean | null;
  /** HH:mm；空字符串 = 清除 */
  quietStart?: string | null;
  quietEnd?: string | null;
  userIds?: number[] | null;
}

/** 接收人候选项（6.11 M2，来自 auth 内网通讯录） */
export interface TwinNotifyRecipientVO {
  id: string;
  name: string;
  username?: string | null;
  deptName?: string | null;
}

/** 投递记录（对齐后端 TwinAlertDispatchVO） */
export interface TwinAlertDispatchVO {
  id: string;
  alertId?: string | null;
  sceneId?: string | null;
  stateKey?: string | null;
  level?: string | null;
  channel: AlertDispatchChannel;
  target?: string | null;
  status: AlertDispatchStatus;
  messageId?: string | null;
  errorMsg?: string | null;
  createTime?: string | null;
}

export const notifyApi = {
  /** 读取场景通知设置 */
  getSettings: (sceneId: string) =>
    http.get<TwinAlertNotifySettingsVO>(`/twin/scenes/${sceneId}/alert-notify`),
  /** 保存场景通知设置（整体覆盖，null = 清除） */
  saveSettings: (sceneId: string, body: TwinAlertNotifySettingsRequest) =>
    http.put<TwinAlertNotifySettingsVO>(`/twin/scenes/${sceneId}/alert-notify`, body),
  /** 投递记录列表（时间倒序，可按结果/通道过滤） */
  dispatches: (sceneId: string, query?: Record<string, unknown>) =>
    http.get<TwinAlertDispatchVO[]>(`/twin/scenes/${sceneId}/alert-dispatches`, query),
  /** 检索候选接收人（走 twin 代理的 auth 内网通讯录，无需用户管理权限） */
  recipients: (keyword?: string) =>
    http.get<TwinNotifyRecipientVO[]>('/twin/alert-notify/recipients', {
      keyword: keyword || undefined,
      size: 20,
    }),
};

/**
 * GET 流式订阅场景实时事件（SSE）：EventSource 不支持自定义请求头，
 * 手工读流分帧（对齐 whale-iot 子应用同款实现）。
 *
 * @param url     流式端点
 * @param onEvent 事件回调（事件名, data JSON 串）
 * @param signal  中断信号
 */
export async function subscribeSceneStream(
  url: string,
  onEvent: (event: string, data: string) => void,
  signal?: AbortSignal,
): Promise<void> {
  const token = getToken();
  const response = await fetch(url, {
    method: 'GET',
    headers: {
      Accept: 'text/event-stream',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    signal,
  });
  if (!response.ok || !response.body) {
    throw new Error(`实时订阅失败：HTTP ${response.status}`);
  }
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  let event = 'message';
  let data = '';
  for (;;) {
    const { done, value } = await reader.read();
    if (done) {
      break;
    }
    buffer += decoder.decode(value, { stream: true });
    let index = buffer.indexOf('\n');
    while (index >= 0) {
      const line = buffer.slice(0, index).replace(/\r$/, '');
      buffer = buffer.slice(index + 1);
      if (line === '') {
        if (data) {
          onEvent(event, data);
          event = 'message';
          data = '';
        }
      } else if (line.startsWith('event:')) {
        event = line.slice(6).trim();
      } else if (line.startsWith('data:')) {
        data += (data ? '\n' : '') + line.slice(5).trim();
      }
      index = buffer.indexOf('\n');
    }
  }
}

// ---- 标注/热点（M3）----

/** 标注视图对象（对齐后端 MarkerVO） */
export interface MarkerVO {
  id: string;
  sceneId: string;
  entityId?: string | null;
  entityCode?: string | null;
  entityName?: string | null;
  markerType: 'label' | 'hotspot' | 'alarm';
  content?: string | null;
  positionJson?: string | null;
  linkUrl?: string | null;
  enabled: number;
  createTime?: string | null;
}

/** 标注保存请求 */
export interface MarkerSaveRequest {
  entityId?: number | null;
  markerType: 'label' | 'hotspot' | 'alarm';
  content?: string | null;
  positionJson?: string | null;
  linkUrl?: string | null;
  enabled?: number;
}

export const markerApi = {
  list: (sceneId: string) => http.get<MarkerVO[]>(`/twin/scenes/${sceneId}/markers`),
  create: (sceneId: string, body: MarkerSaveRequest) =>
    http.post<MarkerVO>(`/twin/scenes/${sceneId}/markers`, body),
  update: (sceneId: string, id: string, body: MarkerSaveRequest) =>
    http.put<MarkerVO>(`/twin/scenes/${sceneId}/markers/${id}`, body),
  remove: (sceneId: string, id: string) =>
    http.del<void>(`/twin/scenes/${sceneId}/markers/${id}`),
};

// ---- 发布快照（M3）----

/** 快照视图对象（对齐后端 SnapshotVO；snapshotJson 仅详情返回） */
export interface SnapshotVO {
  id: string;
  sceneId: string;
  version: number;
  remark?: string | null;
  publishedBy?: string | null;
  publishTime?: string | null;
  snapshotJson?: string | null;
}

export const snapshotApi = {
  list: (sceneId: string) => http.get<SnapshotVO[]>(`/twin/scenes/${sceneId}/snapshots`),
  get: (sceneId: string, id: string) =>
    http.get<SnapshotVO>(`/twin/scenes/${sceneId}/snapshots/${id}`),
  rollback: (sceneId: string, id: string) =>
    http.post<SnapshotVO>(`/twin/scenes/${sceneId}/snapshots/${id}/rollback`),
};

// ---- AI 解读（Wave 6.04 M1）----

/** AI 解读请求 */
export interface InsightRequest {
  entityId?: number | null;
  question?: string | null;
}

/** AI 解读结果（对齐后端 InsightVO） */
export interface InsightVO {
  answer: string;
  degraded: boolean;
  tokens: number;
  elapsedMillis: number;
  sessionId?: string | null;
  citations?: Record<string, unknown>[] | null;
}

export const insightApi = {
  ask: (sceneId: string, body: InsightRequest) =>
    http.post<InsightVO>(`/twin/scenes/${sceneId}/ai/insight`, body),
};

/** 流式解读终帧（done）载荷，对齐 assistant ChatDoneVO */
export interface InsightDoneMeta {
  sessionId?: string;
  assistantId?: string;
  messageId?: string;
  tokens: number;
  degraded: boolean;
  elapsedMillis: number;
  citations?: Record<string, unknown>[] | null;
}

/** 构造流式 AI 解读 SSE 地址（delta/done/error 帧契约，与内部会话流一致） */
export function buildInsightStreamUrl(
  sceneId: string,
  entityId?: number | null,
  question?: string | null,
): string {
  const params = new URLSearchParams();
  if (entityId) params.set('entityId', String(entityId));
  if (question && question.trim()) params.set('question', question.trim());
  const query = params.toString();
  return `/twin/scenes/${sceneId}/ai/insight/stream${query ? `?${query}` : ''}`;
}
