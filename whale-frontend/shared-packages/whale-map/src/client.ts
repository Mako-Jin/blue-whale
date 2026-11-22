/**
 * `whale-map` 接口客户端。
 *
 * 只依赖一个「发请求」的函数，而不是直接依赖 `@bluewhale/http`：
 * 各应用的 http 客户端配置不同（有的带续期、有的不带），共享包不该替宿主决定。
 * `createHttpClient()` 的返回值可以直接传进来（结构兼容）。
 */

import type {
  DistanceParams,
  DistanceVO,
  FenceCheckParams,
  FenceHitVO,
  FenceQueryParams,
  FenceSaveParams,
  FenceVO,
  GeocodeParams,
  GeocodeVO,
  LocationSaveParams,
  MapLocationVO,
  NearbyParams,
  PageResult,
  PoiPageVO,
  ProviderHealthVO,
  RegeoParams,
  RegeoVO,
  RouteParams,
  RouteVO,
} from './types';

/** 请求选项（`@bluewhale/http` 的 `HttpOptions` 子集） */
export interface MapRequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH';
  body?: unknown;
  query?: Record<string, unknown>;
  signal?: AbortSignal;
}

/** 发请求的函数签名（与 `createHttpClient()` 返回值兼容） */
export type MapRequest = <T>(path: string, options?: MapRequestOptions) => Promise<T>;

/** 地图客户端 */
export interface MapClient {
  /** 地址解析（服务端带缓存，同一地址反复调用不重复计费） */
  geocode: (params: GeocodeParams) => Promise<GeocodeVO>;
  /** 逆地理 */
  regeo: (params: RegeoParams) => Promise<RegeoVO>;
  /** 地点检索 */
  searchPoi: (
    keyword: string,
    options?: { city?: string; page?: number; size?: number },
  ) => Promise<PoiPageVO>;
  /** 路径规划（不同供应商的 polyline 精度不同，只用于画线，不用于算距离） */
  route: (params: RouteParams) => Promise<RouteVO>;
  /** 批量直线距离（服务端本地算，不耗配额） */
  distance: (params: DistanceParams) => Promise<DistanceVO[]>;
  /** 打点（幂等覆盖：同一 bizType+bizId 反复调用只更新） */
  saveLocation: (
    bizType: string,
    bizId: string | number,
    params: LocationSaveParams,
  ) => Promise<MapLocationVO>;
  /** 读打点；未打点返回 null */
  getLocation: (bizType: string, bizId: string | number) => Promise<MapLocationVO | null>;
  /** 取消打点（物理删除，不留空壳） */
  removeLocation: (bizType: string, bizId: string | number) => Promise<void>;
  /** 附近查询（按距离升序） */
  nearby: (params: NearbyParams) => Promise<MapLocationVO[]>;
  /** 保存围栏（id 为空=新建） */
  saveFence: (params: FenceSaveParams) => Promise<FenceVO>;
  /** 围栏分页 */
  pageFence: (query?: FenceQueryParams) => Promise<PageResult<FenceVO>>;
  /** 围栏详情 */
  getFence: (id: number) => Promise<FenceVO>;
  /** 删除围栏（逻辑删除，业务单据仍能指回名字） */
  removeFence: (id: number) => Promise<void>;
  /** 某点是否在指定围栏内（配送范围校验） */
  fenceContains: (id: number, params: FenceCheckParams) => Promise<FenceHitVO>;
  /** 某点命中的全部围栏（打卡）；空数组=不在任何范围内，不是错误 */
  fenceMatch: (params: FenceCheckParams) => Promise<FenceHitVO[]>;
  /** 供应商状态与今日配额 */
  providerHealth: () => Promise<ProviderHealthVO[]>;
}

/** 路径参数编码：bizType/bizId 可能含中文或斜杠，不编码会把路径切断 */
function segment(value: string | number): string {
  return encodeURIComponent(String(value));
}

/**
 * 创建地图客户端
 *
 * @param request 发请求的函数（`createHttpClient()` 的返回值）
 * @returns 地图客户端
 */
export function createMapClient(request: MapRequest): MapClient {
  return {
    geocode: (params) => request<GeocodeVO>('/map/v1/geocode', { method: 'POST', body: params }),

    regeo: (params) => request<RegeoVO>('/map/v1/regeo', { method: 'POST', body: params }),

    searchPoi: (keyword, options = {}) =>
      request<PoiPageVO>('/map/v1/poi/search', {
        method: 'GET',
        query: {
          keyword,
          city: options.city,
          page: options.page,
          size: options.size,
        },
      }),

    route: (params) => request<RouteVO>('/map/v1/route', { method: 'POST', body: params }),

    distance: (params) =>
      request<DistanceVO[]>('/map/v1/distance', { method: 'POST', body: params }),

    saveLocation: (bizType, bizId, params) =>
      request<MapLocationVO>(`/map/v1/location/${segment(bizType)}/${segment(bizId)}`, {
        method: 'PUT',
        body: params,
      }),

    getLocation: (bizType, bizId) =>
      request<MapLocationVO | null>(`/map/v1/location/${segment(bizType)}/${segment(bizId)}`, {
        method: 'GET',
      }),

    removeLocation: (bizType, bizId) =>
      request<void>(`/map/v1/location/${segment(bizType)}/${segment(bizId)}/remove`, {
        method: 'POST',
      }),

    nearby: (params) =>
      request<MapLocationVO[]>('/map/v1/location/nearby', {
        method: 'GET',
        query: {
          lng: params.lng,
          lat: params.lat,
          bizType: params.bizType,
          coordType: params.coordType,
          radiusMeters: params.radiusMeters,
          limit: params.limit,
        },
      }),

    saveFence: (params) => request<FenceVO>('/map/v1/fence', { method: 'POST', body: params }),

    pageFence: (query = {}) =>
      request<PageResult<FenceVO>>('/map/v1/fence/page', {
        method: 'GET',
        query: {
          name: query.name,
          bizType: query.bizType,
          bizId: query.bizId,
          enabled: query.enabled,
          page: query.page,
          size: query.size,
        },
      }),

    getFence: (id) => request<FenceVO>(`/map/v1/fence/${segment(id)}`, { method: 'GET' }),

    removeFence: (id) => request<void>(`/map/v1/fence/${segment(id)}/remove`, { method: 'POST' }),

    fenceContains: (id, params) =>
      request<FenceHitVO>(`/map/v1/fence/${segment(id)}/contains`, {
        method: 'POST',
        body: params,
      }),

    fenceMatch: (params) =>
      request<FenceHitVO[]>('/map/v1/fence/match', { method: 'POST', body: params }),

    providerHealth: () => request<ProviderHealthVO[]>('/map/v1/provider/health', { method: 'GET' }),
  };
}
