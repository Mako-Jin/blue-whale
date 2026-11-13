/**
 * 内存版底图引擎（无 DOM、无网络）。
 *
 * 用来做两件事：组件单测不必拉真 SDK；宿主在没有 Key 的环境（CI、离线演示）里
 * 也能把「画围栏 → 校验 → 提交」整条链路跑通。它只记录状态，不做任何渲染。
 */

import type { GeoPoint } from '../types';
import type { MapEngine, MapMarker, MapPolygon } from './types';

/** 内存引擎：记录最后一次被要求画什么，并允许测试触发点击 */
export interface FakeMapEngine extends MapEngine {
  /** 当前中心点 */
  readonly center: GeoPoint | null;
  /** 当前缩放级别 */
  readonly zoom: number;
  /** 当前标记 */
  readonly markers: MapMarker[];
  /** 当前多边形 */
  readonly polygons: MapPolygon[];
  /** 是否已销毁 */
  readonly destroyed: boolean;
  /** 模拟一次地图点击 */
  emitClick: (point: GeoPoint) => void;
}

/**
 * 创建内存引擎
 *
 * @param initial 初始中心与缩放
 * @returns 内存引擎
 */
export function createFakeEngine(
  initial: { center?: GeoPoint; zoom?: number } = {},
): FakeMapEngine {
  let center = initial.center ?? null;
  let zoom = initial.zoom ?? 15;
  let markers: MapMarker[] = [];
  let polygons: MapPolygon[] = [];
  let destroyed = false;
  const handlers = new Set<(point: GeoPoint) => void>();

  return {
    get center() {
      return center;
    },
    get zoom() {
      return zoom;
    },
    get markers() {
      return markers;
    },
    get polygons() {
      return polygons;
    },
    get destroyed() {
      return destroyed;
    },
    setCenter: (point) => {
      center = point;
    },
    setZoom: (value) => {
      zoom = value;
    },
    fitBounds: (points) => {
      if (points.length === 0) {
        return;
      }
      let sumLng = 0;
      let sumLat = 0;
      points.forEach((item) => {
        sumLng += item.lng;
        sumLat += item.lat;
      });
      center = {
        lng: sumLng / points.length,
        lat: sumLat / points.length,
        coordType: points[0].coordType,
      };
    },
    setMarkers: (value) => {
      markers = [...value];
    },
    setPolygons: (value) => {
      polygons = [...value];
    },
    onClick: (handler) => {
      handlers.add(handler);
      return () => {
        handlers.delete(handler);
      };
    },
    destroy: () => {
      destroyed = true;
      handlers.clear();
    },
    emitClick: (point) => {
      if (destroyed) {
        return;
      }
      handlers.forEach((handler) => handler(point));
    },
  };
}
