/**
 * 围栏草稿状态机（纯逻辑，不碰 DOM 与底图）。
 *
 * 抽出来的理由：围栏编辑的坑几乎都在状态上——撤销该退到哪一步、拖动顶点后是否自交、
 * 圆改成多边形后已画的顶点怎么办。把这些放进 React 组件的 `useState` 里，
 * 就只能靠手点页面来验证。
 *
 * 草稿坐标一律 **GCJ-02**（用户是在底图上画的，底图就是 GCJ-02）；
 * 提交时在 `toFenceSaveParams` 里显式声明 `coordType`，服务端负责换算落库。
 */

import { circleToPolygon, polygonArea, polygonPerimeter, roundCoord } from './coord';
import type { FenceSaveParams, FenceType, FenceVO, GeoPoint } from './types';

/** 顶点下限（与后端 `MIN_VERTICES` 一致：少于 3 个点构不成面） */
export const MIN_POLYGON_POINTS = 3;

/** 顶点上限（与后端 `MAX_VERTICES` 一致：再多通常是在拿行政区边界当打卡范围） */
export const MAX_POLYGON_POINTS = 200;

/** 圆围栏预览离散段数 */
export const CIRCLE_SEGMENTS = 64;

/** 草稿状态 */
export interface FenceDraftState {
  /** 形状 */
  shape: FenceType;
  /** 多边形顶点（GCJ-02，不重复首点） */
  points: GeoPoint[];
  /** 圆心（GCJ-02） */
  center: GeoPoint | null;
  /** 半径（米） */
  radiusMeters: number;
}

/** 草稿动作 */
export type FenceDraftAction =
  | { type: 'addPoint'; point: GeoPoint }
  | { type: 'insertPoint'; index: number; point: GeoPoint }
  | { type: 'movePoint'; index: number; point: GeoPoint }
  | { type: 'removePoint'; index: number }
  | { type: 'undo' }
  | { type: 'clear' }
  | { type: 'setShape'; shape: FenceType }
  | { type: 'setCircle'; center: GeoPoint; radiusMeters: number }
  | { type: 'setRadius'; radiusMeters: number }
  | { type: 'load'; fence: FenceVO };

/** 草稿统计 */
export interface FenceDraftStats {
  /** 实际生效的顶点数（圆按离散后的段数算） */
  vertexCount: number;
  /** 面积（平方米），圆按 πr² */
  areaSquareMeters: number;
  /** 周长（米），圆按 2πr */
  perimeterMeters: number;
  /** 是否构成合法形状（多边形 ≥3 点 / 圆半径 > 0） */
  closed: boolean;
}

/** 新建空草稿 */
export function createFenceDraft(): FenceDraftState {
  return { shape: 'POLYGON', points: [], center: null, radiusMeters: 200 };
}

/** 从既有围栏回显（服务端返回的坐标已是 GCJ-02） */
export function fenceToDraft(fence: FenceVO): FenceDraftState {
  const center =
    fence.centerLng !== null && fence.centerLat !== null
      ? {
          lng: fence.centerLng,
          lat: fence.centerLat,
          coordType: fence.coordType ?? 'GCJ02',
        }
      : null;
  return {
    shape: fence.fenceType,
    points: (fence.points ?? []).map((item) => ({
      lng: item.lng,
      lat: item.lat,
      coordType: item.coordType ?? 'GCJ02',
    })),
    center,
    radiusMeters: fence.radiusMeters ?? 200,
  };
}

/**
 * 去掉首尾重复的闭合点
 *
 * 底图点选框、GeoJSON 粘贴、后端回显都可能带一个与首点相同的尾点。
 * 留着它会产生一条零长度边，面积与自交判定都会算错。
 */
function stripClosingPoint(points: GeoPoint[]): GeoPoint[] {
  if (points.length < 2) {
    return points;
  }
  const first = points[0];
  const last = points[points.length - 1];
  if (Math.abs(first.lng - last.lng) < 1e-9 && Math.abs(first.lat - last.lat) < 1e-9) {
    return points.slice(0, -1);
  }
  return points;
}

/**
 * 草稿归约器
 *
 * @param state 当前状态
 * @param action 动作
 * @returns 新状态（原状态不被修改）
 */
export function fenceDraftReducer(
  state: FenceDraftState,
  action: FenceDraftAction,
): FenceDraftState {
  switch (action.type) {
    case 'addPoint': {
      if (state.points.length >= MAX_POLYGON_POINTS) {
        return state;
      }
      return { ...state, shape: 'POLYGON', points: [...state.points, action.point] };
    }
    case 'insertPoint': {
      if (state.points.length >= MAX_POLYGON_POINTS) {
        return state;
      }
      const index = Math.max(0, Math.min(action.index, state.points.length));
      const next = [...state.points];
      next.splice(index, 0, action.point);
      return { ...state, points: next };
    }
    case 'movePoint': {
      if (action.index < 0 || action.index >= state.points.length) {
        return state;
      }
      const next = [...state.points];
      next[action.index] = action.point;
      return { ...state, points: next };
    }
    case 'removePoint': {
      if (action.index < 0 || action.index >= state.points.length) {
        return state;
      }
      return { ...state, points: state.points.filter((_, index) => index !== action.index) };
    }
    case 'undo': {
      if (state.shape === 'POLYGON' && state.points.length > 0) {
        return { ...state, points: state.points.slice(0, -1) };
      }
      return state;
    }
    case 'clear':
      return createFenceDraft();
    case 'setShape':
      // 切换形状不清空已画的顶点：用户常常先点几个点，再决定改成圆
      return { ...state, shape: action.shape };
    case 'setCircle':
      return {
        ...state,
        shape: 'CIRCLE',
        center: action.center,
        radiusMeters: Math.max(1, action.radiusMeters),
      };
    case 'setRadius':
      // 只改半径、不动圆心：还没点选时给圆心编一个默认值，
      // 会把围栏悄悄放到另一个城市，而页面上看不出任何异常
      return { ...state, radiusMeters: Math.max(1, action.radiusMeters) };
    case 'load':
      return fenceToDraft(action.fence);
    default:
      return state;
  }
}

/** 多边形顶点（已去重闭合点） */
export function draftPolygon(state: FenceDraftState): GeoPoint[] {
  return stripClosingPoint(state.points);
}

/**
 * 用于底图预览与提交的顶点序列
 *
 * 圆会被离散成多边形：多数底图 SDK 的多边形图元是稳定 API，圆图元各家不同名，
 * 而 64 边形在几公里尺度的视觉偏差远小于半个像素。
 */
export function draftOutline(state: FenceDraftState): GeoPoint[] {
  if (state.shape === 'CIRCLE') {
    if (!state.center) {
      return [];
    }
    return circleToPolygon(state.center, state.radiusMeters, CIRCLE_SEGMENTS);
  }
  return draftPolygon(state);
}

/** 统计面积与周长 */
export function fenceDraftStats(state: FenceDraftState): FenceDraftStats {
  if (state.shape === 'CIRCLE') {
    const radius = state.radiusMeters;
    const valid = Boolean(state.center) && radius > 0;
    return {
      vertexCount: CIRCLE_SEGMENTS,
      areaSquareMeters: valid ? Math.PI * radius * radius : 0,
      perimeterMeters: valid ? 2 * Math.PI * radius : 0,
      closed: valid,
    };
  }
  const points = draftPolygon(state);
  return {
    vertexCount: points.length,
    areaSquareMeters: polygonArea(points),
    perimeterMeters: polygonPerimeter(points),
    closed: points.length >= MIN_POLYGON_POINTS,
  };
}

/** 线段严格相交判定（共享端点不算） */
function segmentsIntersect(a1: GeoPoint, a2: GeoPoint, b1: GeoPoint, b2: GeoPoint): boolean {
  const cross = (o: GeoPoint, p: GeoPoint, q: GeoPoint) =>
    (p.lng - o.lng) * (q.lat - o.lat) - (p.lat - o.lat) * (q.lng - o.lng);
  const d1 = cross(b1, b2, a1);
  const d2 = cross(b1, b2, a2);
  const d3 = cross(a1, a2, b1);
  const d4 = cross(a1, a2, b2);
  return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0));
}

/**
 * 多边形是否自交（自交的多边形面积算出来是几块区域的代数和，判定也毫无意义）
 *
 * @param points 顶点（不含重复闭合点）
 * @returns 自交返回 true
 */
export function isSelfIntersecting(points: GeoPoint[]): boolean {
  const count = points.length;
  if (count < 4) {
    return false;
  }
  for (let i = 0; i < count; i++) {
    const a1 = points[i];
    const a2 = points[(i + 1) % count];
    for (let j = i + 1; j < count; j++) {
      // 相邻边共享端点，跳过
      if (j === i || (j + 1) % count === i || j === (i + 1) % count) {
        continue;
      }
      const b1 = points[j];
      const b2 = points[(j + 1) % count];
      if (segmentsIntersect(a1, a2, b1, b2)) {
        return true;
      }
    }
  }
  return false;
}

/**
 * 校验草稿几何（名称、业务归属等非几何字段由调用方校验）
 *
 * @param state 草稿
 * @returns 错误提示；合法返回 null
 */
export function validateFenceDraft(state: FenceDraftState): string | null {
  if (state.shape === 'CIRCLE') {
    if (!state.center) {
      return '请在地图上点选圆心';
    }
    if (!(state.radiusMeters > 0)) {
      return '半径必须大于 0 米';
    }
    return null;
  }
  const points = draftPolygon(state);
  if (points.length < MIN_POLYGON_POINTS) {
    return `至少需要 ${MIN_POLYGON_POINTS} 个顶点`;
  }
  if (points.length > MAX_POLYGON_POINTS) {
    return `顶点不能超过 ${MAX_POLYGON_POINTS} 个`;
  }
  if (polygonArea(points) < 1) {
    return '围栏面积过小，请重新绘制';
  }
  if (isSelfIntersecting(points)) {
    return '围栏边界自交，请调整顶点顺序';
  }
  return null;
}

/**
 * 生成保存请求
 *
 * @param state 草稿
 * @param base  非几何字段（名称、业务归属、启用状态等）
 * @returns 保存请求体（`coordType` 固定 GCJ02，与草稿来源一致）
 */
export function toFenceSaveParams(
  state: FenceDraftState,
  base: Omit<
    FenceSaveParams,
    'fenceType' | 'points' | 'centerLng' | 'centerLat' | 'radiusMeters' | 'coordType'
  >,
): FenceSaveParams {
  if (state.shape === 'CIRCLE') {
    return {
      ...base,
      fenceType: 'CIRCLE',
      coordType: 'GCJ02',
      centerLng: state.center ? roundCoord(state.center.lng) : undefined,
      centerLat: state.center ? roundCoord(state.center.lat) : undefined,
      radiusMeters: Math.round(state.radiusMeters),
    };
  }
  return {
    ...base,
    fenceType: 'POLYGON',
    coordType: 'GCJ02',
    points: draftPolygon(state).map((item) => ({
      lng: roundCoord(item.lng),
      lat: roundCoord(item.lat),
    })),
  };
}

/** 面积的人类可读文本（米制习惯：小于 1 万平米用平方米，否则用公顷/平方公里） */
export function formatArea(areaSquareMeters: number): string {
  if (!Number.isFinite(areaSquareMeters) || areaSquareMeters <= 0) {
    return '—';
  }
  if (areaSquareMeters < 10000) {
    return `${Math.round(areaSquareMeters)} m²`;
  }
  if (areaSquareMeters < 1000000) {
    return `${(areaSquareMeters / 10000).toFixed(2)} 公顷`;
  }
  return `${(areaSquareMeters / 1000000).toFixed(2)} km²`;
}

/** 距离的人类可读文本 */
export function formatDistance(meters: number): string {
  if (!Number.isFinite(meters)) {
    return '—';
  }
  const abs = Math.abs(meters);
  const sign = meters < 0 ? '-' : '';
  if (abs < 1000) {
    return `${sign}${Math.round(abs)} m`;
  }
  return `${sign}${(abs / 1000).toFixed(2)} km`;
}
