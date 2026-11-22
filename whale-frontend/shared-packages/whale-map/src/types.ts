/**
 * 与 `whale-map` 后端（端口 8100，前缀 `/map/v1/**`）对齐的接口契约。
 *
 * 字段名逐一对齐后端 `dto` / `vo`（Jackson 默认 camelCase），不在这里做重命名——
 * 一层映射就是一处「改了后端忘了改前端」的隐患。
 *
 * 坐标口径：所有出入参都带 `coordType`，前端从底图拿到的通常是 GCJ-02，
 * 提交时**必须显式声明**，服务端据此换算成 WGS84 落库。缺省不等于 GCJ-02：
 * 省略后服务端按配置的展示坐标系处理，一旦运营改了配置，前端就会静默偏 300–500 米。
 */

/** 坐标系标识，与后端 `CoordType` 取值一致 */
export type CoordType = 'WGS84' | 'GCJ02' | 'BD09';

/** 带坐标系的坐标点 */
export interface GeoPoint {
  /** 经度 */
  lng: number;
  /** 纬度 */
  lat: number;
  /** 坐标系 */
  coordType: CoordType;
}

/** 后端 `GeoPointVO` */
export interface GeoPointVO {
  lng: number;
  lat: number;
  coordType: CoordType;
}

/** 后端 `GeocodeVO`（地址解析结果） */
export interface GeocodeVO {
  lng: number;
  lat: number;
  coordType: CoordType;
  /** 标准化后的完整地址，展示优先用它而不是用户输入 */
  formattedAddress: string | null;
  province: string | null;
  city: string | null;
  district: string | null;
  /** 行政区划码 */
  adcode: string | null;
  /** 匹配级别（门牌号/道路/POI/区县…），精度提示用 */
  level: string | null;
  /** 实际应答的供应商，用于排查「换了一家结果不一样」 */
  provider: string;
  /** 是否命中缓存 */
  cached: boolean;
}

/** 后端 `RegeoVO`（逆地理结果） */
export interface RegeoVO {
  lng: number;
  lat: number;
  coordType: CoordType;
  formattedAddress: string | null;
  province: string | null;
  city: string | null;
  district: string | null;
  adcode: string | null;
  street: string | null;
  streetNumber: string | null;
  provider: string;
}

/** 后端 `PoiVO` */
export interface PoiVO {
  id: string;
  name: string;
  address: string | null;
  lng: number;
  lat: number;
  coordType: CoordType;
  adcode: string | null;
  category: string | null;
  /** 传入中心点时的直线距离（米）；未传则为 null */
  distanceMeters: number | null;
}

/** 后端 `PoiPageVO` */
export interface PoiPageVO {
  items: PoiVO[];
  total: number;
  page: number;
  size: number;
  provider: string;
}

/** 出行方式，与后端 `RouteMode` 一致 */
export type RouteMode = 'DRIVING' | 'WALKING' | 'BICYCLING' | 'TRANSIT';

/** 后端 `RouteVO` */
export interface RouteVO {
  distanceMeters: number;
  durationMinutes: number;
  mode: string;
  provider: string;
  /** 路线形状点，用于底图叠线 */
  polyline: GeoPointVO[];
}

/** 后端 `DistanceVO`：`index` 与请求 targets 下标对齐 */
export interface DistanceVO {
  index: number;
  distanceMeters: number;
}

/** 后端 `MapLocationVO`（业务打点） */
export interface MapLocationVO {
  bizType: string;
  bizId: string;
  lng: number;
  lat: number;
  coordType: CoordType;
  address: string | null;
  province: string | null;
  city: string | null;
  district: string | null;
  adcode: string | null;
  provider: string | null;
  /** 打点来源：MANUAL（手填坐标）/ GEOCODE（地址解析） */
  source: string | null;
  remark: string | null;
  /** ISO 字符串（后端 LocalDateTime） */
  updateTime: string | null;
  /** 附近查询时才有值 */
  distanceMeters: number | null;
}

/** 后端 `ProviderHealthVO` */
export interface ProviderHealthVO {
  name: string;
  /** 是否已配 Key；未配置的供应商不参与路由 */
  configured: boolean;
  capabilities: string[];
  preferred: boolean;
  usedToday: number;
  limitToday: number;
  remaining: number;
}

/** 围栏类型：圆（中心 + 半径）/ 多边形 */
export type FenceType = 'CIRCLE' | 'POLYGON';

/** 后端 `FenceVO`：坐标已由服务端转回展示坐标系（GCJ-02），可直接叠到底图 */
export interface FenceVO {
  id: number;
  name: string;
  fenceType: FenceType;
  bizType: string | null;
  bizId: string | null;
  centerLng: number | null;
  centerLat: number | null;
  radiusMeters: number | null;
  points: GeoPointVO[] | null;
  coordType: CoordType;
  enabled: number;
  remark: string | null;
  updateTime: string | null;
}

/** 后端 `FenceHitVO`：`distanceMeters` 内正外负（在内=距边界多远，在外=超出多少米） */
export interface FenceHitVO {
  id: number;
  name: string;
  fenceType: FenceType;
  bizType: string | null;
  bizId: string | null;
  inside: boolean;
  distanceMeters: number;
}

/** 后端 `PageResult<T>` */
export interface PageResult<T> {
  records: T[];
  total: number;
  size: number;
  current: number;
}

/** 地址解析请求 */
export interface GeocodeParams {
  address: string;
  city?: string;
}

/** 逆地理请求 */
export interface RegeoParams {
  lng: number;
  lat: number;
  coordType?: CoordType;
  radiusMeters?: number;
}

/** 路径规划请求 */
export interface RouteParams {
  originLng: number;
  originLat: number;
  destinationLng: number;
  destinationLat: number;
  coordType?: CoordType;
  mode?: RouteMode;
}

/** 批量距离请求（服务端本地计算，不耗供应商配额） */
export interface DistanceParams {
  originLng: number;
  originLat: number;
  coordType?: CoordType;
  targets: Array<{ lng: number; lat: number }>;
}

/** 打点请求：`lng/lat` 与 `address` 二选一（都传时以坐标为准） */
export interface LocationSaveParams {
  lng?: number;
  lat?: number;
  address?: string;
  coordType?: CoordType;
  remark?: string;
}

/** 附近查询参数 */
export interface NearbyParams {
  lng: number;
  lat: number;
  bizType?: string;
  coordType?: CoordType;
  radiusMeters?: number;
  limit?: number;
}

/** 围栏保存请求：`id` 留空=新建；`coordType` 必须与 points/center 的来源一致 */
export interface FenceSaveParams {
  id?: number;
  name: string;
  fenceType: FenceType;
  bizType?: string;
  bizId?: string;
  centerLng?: number;
  centerLat?: number;
  radiusMeters?: number;
  points?: Array<{ lng: number; lat: number }>;
  coordType?: CoordType;
  enabled?: number;
  remark?: string;
}

/** 围栏分页查询 */
export interface FenceQueryParams {
  name?: string;
  bizType?: string;
  bizId?: string;
  enabled?: number;
  page?: number;
  size?: number;
}

/** 围栏判定请求 */
export interface FenceCheckParams {
  lng: number;
  lat: number;
  coordType?: CoordType;
  bizType?: string;
  bizId?: string;
}
