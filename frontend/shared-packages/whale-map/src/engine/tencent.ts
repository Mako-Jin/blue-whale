/**
 * 腾讯地图 GL JS 适配器。
 *
 * 两件事：把 SDK 脚本按需加载（并发去重、失败清理、多应用复用同一个 window），
 * 以及把 `MapEngine` 接口翻译成腾讯 SDK 的调用。
 *
 * **Key 由宿主传入**，共享包不读环境变量、不写死任何 Key。
 * 底图 Key 与服务端 Key 不是同一个：底图 Key 有 Referer 白名单、额度算法也不同，
 * 混用会出现「本地能跑、线上 403」。
 *
 * **只用了 SDK 的五个能力**：`Map` / `LatLng` / `MultiMarker` / `MultiPolygon` / `PolygonStyle`。
 * 刻意不用 `fitBounds`——各家 SDK 对「bounds 对象怎么造」差异很大，与其赌，
 * 不如按跨度自己算 zoom（见 `zoomForSpan`），逻辑可测且不会随 SDK 小版本漂移。
 */

import type { GeoPoint } from '../types';
import type { MapEngine, MapEngineFactory, MapEngineOptions, MapMarker, MapPolygon } from './types';

/** SDK 的经纬度对象（注意：腾讯构造器是 `LatLng(lat, lng)`，纬度在前） */
export interface TencentLatLng {
  lat: number;
  lng: number;
}

/** 地图实例（只声明用到的成员） */
export interface TencentMapInstance {
  setCenter: (center: TencentLatLng) => void;
  setZoom: (zoom: number) => void;
  on: (event: string, handler: (event: { latLng: TencentLatLng }) => void) => void;
  off: (event: string, handler: (event: { latLng: TencentLatLng }) => void) => void;
  destroy: () => void;
}

/** 图元实例（标记/多边形共用 setGeometries，销毁用 setMap(null)） */
export interface TencentOverlay {
  setGeometries?: (geometries: Array<Record<string, unknown>>) => void;
  setMap?: (map: TencentMapInstance | null) => void;
}

/** SDK 句柄（只声明用到的成员） */
export interface TencentSdk {
  Map: new (container: HTMLElement, options: Record<string, unknown>) => TencentMapInstance;
  LatLng: new (lat: number, lng: number) => TencentLatLng;
  MultiMarker?: new (options: Record<string, unknown>) => TencentOverlay;
  MultiPolygon?: new (options: Record<string, unknown>) => TencentOverlay;
  PolygonStyle?: new (options: Record<string, unknown>) => unknown;
}

/** SDK 版本，`1.exp` 是官方长期维护的体验版本号 */
export const TENCENT_SDK_VERSION = '1.exp';

/** 已发起或已完成的加载（按 Key 去重：同一页面多个组件只加载一次） */
const loaders = new Map<string, Promise<TencentSdk>>();

/** 读取全局 SDK 句柄 */
function readGlobalSdk(): TencentSdk | null {
  const holder = globalThis as unknown as { TMap?: TencentSdk };
  return holder.TMap && typeof holder.TMap.Map === 'function' ? holder.TMap : null;
}

/**
 * 加载腾讯地图 SDK
 *
 * @param key 底图 Key（带 Referer 白名单，由宿主提供）
 * @param version 版本号，默认 `1.exp`
 * @returns SDK 句柄
 */
export function loadTencentMap(
  key: string,
  version: string = TENCENT_SDK_VERSION,
): Promise<TencentSdk> {
  const trimmed = key.trim();
  if (!trimmed) {
    return Promise.reject(new Error('未配置腾讯地图底图 Key'));
  }
  const cached = loaders.get(trimmed);
  if (cached) {
    return cached;
  }
  const existing = readGlobalSdk();
  if (existing) {
    const resolved = Promise.resolve(existing);
    loaders.set(trimmed, resolved);
    return resolved;
  }
  const promise = new Promise<TencentSdk>((resolve, reject) => {
    if (typeof document === 'undefined') {
      reject(new Error('当前环境没有 document，无法加载底图 SDK'));
      return;
    }
    const script = document.createElement('script');
    script.src = `https://map.qq.com/api/gljs?v=${encodeURIComponent(version)}&key=${encodeURIComponent(trimmed)}`;
    script.async = true;
    script.dataset.whaleMap = trimmed;
    script.onload = () => {
      const sdk = readGlobalSdk();
      if (sdk) {
        resolve(sdk);
        return;
      }
      script.remove();
      loaders.delete(trimmed);
      reject(new Error('底图 SDK 已加载但未挂载 TMap，请检查 Key 与 Referer 白名单'));
    };
    script.onerror = () => {
      // 失败必须清掉缓存，否则一次网络抖动会让整个会话都认为「加载失败已缓存」
      script.remove();
      loaders.delete(trimmed);
      reject(new Error('底图 SDK 加载失败，请检查网络与 Key'));
    };
    document.head.appendChild(script);
  });
  loaders.set(trimmed, promise);
  return promise;
}

/**
 * 清空加载缓存（测试用）
 *
 * 生产代码不该调用：清空后再加载会重复插入 script 标签。
 */
export function resetTencentMapLoader(): void {
  loaders.clear();
}

/** 转成腾讯的 LatLng：纬度在前，这一处写错会得到「镜像到东南亚」的诡异结果 */
function toLatLng(sdk: TencentSdk, point: GeoPoint): TencentLatLng {
  return new sdk.LatLng(point.lat, point.lng);
}

/**
 * 按经纬跨度估算缩放级别
 *
 * 取「较大的一边」对应的级别，保证内容能装进视口；`paddingDegrees` 用于留边。
 * Web 墨卡托下 360° 对应 2^zoom 个 256px 瓦片，故 zoom = log2(360 / span)。
 */
export function zoomForSpan(
  points: GeoPoint[],
  paddingDegrees = 0.2,
  minZoom = 3,
  maxZoom = 17,
): number {
  if (points.length === 0) {
    return 12;
  }
  if (points.length === 1) {
    return 16;
  }
  let minLng = Number.POSITIVE_INFINITY;
  let maxLng = Number.NEGATIVE_INFINITY;
  let minLat = Number.POSITIVE_INFINITY;
  let maxLat = Number.NEGATIVE_INFINITY;
  points.forEach((item) => {
    minLng = Math.min(minLng, item.lng);
    maxLng = Math.max(maxLng, item.lng);
    minLat = Math.min(minLat, item.lat);
    maxLat = Math.max(maxLat, item.lat);
  });
  const spanLng = Math.max(0, maxLng - minLng);
  // 纬度按中心纬度折算成「等价的经度跨度」，否则高纬度地区会缩得过远
  const centerLatRad = ((minLat + maxLat) / 2) * (Math.PI / 180);
  const spanLat = Math.max(0, maxLat - minLat) / Math.max(0.05, Math.cos(centerLatRad));
  const span = Math.max(spanLng, spanLat, 1e-6) + paddingDegrees;
  const raw = Math.log2(360 / span);
  return Math.max(minZoom, Math.min(maxZoom, Math.floor(raw)));
}

/** 计算多边形/点集的中心 */
export function boundsCenter(points: GeoPoint[]): GeoPoint | null {
  if (points.length === 0) {
    return null;
  }
  let sumLng = 0;
  let sumLat = 0;
  points.forEach((item) => {
    sumLng += item.lng;
    sumLat += item.lat;
  });
  return {
    lng: sumLng / points.length,
    lat: sumLat / points.length,
    coordType: points[0].coordType,
  };
}

const DEFAULT_STROKE = 'rgba(22, 119, 255, 0.9)';
const DEFAULT_FILL = 'rgba(22, 119, 255, 0.18)';

/** fitBounds 时四周留出的度数（约 2 公里量级），避免围栏贴着视口边缘 */
const FIT_PADDING_DEGREES = 0.02;

/**
 * 创建腾讯地图引擎工厂
 *
 * @param key 底图 Key
 * @param version SDK 版本
 * @returns 引擎工厂（容器就绪后调用）
 */
export function createTencentMapEngine(key: string, version?: string): MapEngineFactory {
  return async (container: HTMLElement, options: MapEngineOptions = {}) => {
    const sdk = await loadTencentMap(key, version);
    const center = options.center ?? { lng: 116.397428, lat: 39.90923, coordType: 'GCJ02' };
    const map = new sdk.Map(container, {
      center: toLatLng(sdk, center),
      zoom: options.zoom ?? 15,
      pitch: 0,
      rotation: 0,
      viewMode: '2D',
      baseMap: { type: 'vector' },
    });

    let destroyed = false;
    let markerOverlay: TencentOverlay | null = null;
    let polygonOverlay: TencentOverlay | null = null;
    const clickHandlers = new Set<(point: GeoPoint) => void>();

    /** 统一的点击事件入口：一次 map.on('click') 分发给所有订阅者 */
    const clickListener = (event: { latLng: TencentLatLng }) => {
      if (!event || !event.latLng) {
        return;
      }
      const point: GeoPoint = {
        lng: event.latLng.lng,
        lat: event.latLng.lat,
        coordType: 'GCJ02',
      };
      clickHandlers.forEach((handler) => handler(point));
    };
    map.on('click', clickListener);

    /** 释放一个图元 */
    const release = (overlay: TencentOverlay | null) => {
      try {
        overlay?.setMap?.(null);
      } catch {
        // SDK 内部销毁顺序不可控，释放失败不该影响页面
      }
    };

    const engine: MapEngine = {
      setCenter: (point) => {
        if (destroyed) {
          return;
        }
        map.setCenter(toLatLng(sdk, point));
      },
      setZoom: (zoom) => {
        if (destroyed) {
          return;
        }
        map.setZoom(zoom);
      },
      fitBounds: (points) => {
        if (destroyed || points.length === 0) {
          return;
        }
        const target = boundsCenter(points);
        if (target) {
          map.setCenter(toLatLng(sdk, target));
        }
        map.setZoom(zoomForSpan(points, FIT_PADDING_DEGREES));
      },
      setMarkers: (markers: MapMarker[]) => {
        if (destroyed) {
          return;
        }
        if (!sdk.MultiMarker) {
          return;
        }
        const geometries = markers.map((item) => ({
          id: item.id,
          position: toLatLng(sdk, item.point),
          properties: { title: item.label ?? '', kind: item.kind ?? 'default' },
        }));
        if (!markerOverlay) {
          markerOverlay = new sdk.MultiMarker({ map, geometries });
          return;
        }
        markerOverlay.setGeometries?.(geometries);
      },
      setPolygons: (polygons: MapPolygon[]) => {
        if (destroyed) {
          return;
        }
        if (!sdk.MultiPolygon) {
          return;
        }
        const styles: Record<string, unknown> = {};
        const geometries = polygons.map((item, index) => {
          const styleId = `whale-${index}`;
          if (sdk.PolygonStyle) {
            styles[styleId] = new sdk.PolygonStyle({
              color: item.fillColor ?? DEFAULT_FILL,
              showBorder: true,
              borderColor: item.strokeColor ?? DEFAULT_STROKE,
              borderWidth: item.strokeWidth ?? 2,
            });
          }
          return {
            id: item.id,
            styleId,
            paths: item.points.map((point) => toLatLng(sdk, point)),
          };
        });
        const options: Record<string, unknown> = { map, geometries };
        if (Object.keys(styles).length > 0) {
          options.styles = styles;
        }
        if (!polygonOverlay) {
          polygonOverlay = new sdk.MultiPolygon(options);
          return;
        }
        polygonOverlay.setGeometries?.(geometries);
      },
      onClick: (handler) => {
        clickHandlers.add(handler);
        return () => {
          clickHandlers.delete(handler);
        };
      },
      destroy: () => {
        if (destroyed) {
          return;
        }
        destroyed = true;
        clickHandlers.clear();
        try {
          map.off('click', clickListener);
        } catch {
          // 忽略重复解绑
        }
        release(markerOverlay);
        release(polygonOverlay);
        markerOverlay = null;
        polygonOverlay = null;
        try {
          map.destroy();
        } catch {
          // 容器已被 React 卸载时 SDK 可能抛错，忽略
        }
      },
    };
    return engine;
  };
}
