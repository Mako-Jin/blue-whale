/**
 * 底图引擎抽象。
 *
 * 共享包**不绑定任何一家底图 SDK**：腾讯、高德、天地图的 JS API 在初始化方式、
 * 图元命名、事件对象上都不同，而各应用要用的底图还取决于自己申请的 Key（带 Referer 白名单）。
 * 所以这里只定义一层「够画围栏、够放点位」的最小接口，由 `createTencentMapEngine`
 * 这类适配器实现，或者宿主自己实现（内网离线瓦片、天地图等）。
 *
 * **坐标约定：进出这一层的坐标一律 GCJ-02**（国内底图坐标系）。
 * 引擎内部不许再做任何坐标系转换——转换只发生在「入口（GPS/接口）→ GCJ-02」和
 * 「GCJ-02 → 提交接口」两处，中间层多转一次，位置就偏 300–500 米且没人报错。
 */

import type { GeoPoint } from '../types';

/** 点位标记 */
export interface MapMarker {
  /** 唯一 ID，引擎用它做增量更新 */
  id: string;
  /** 位置（GCJ-02） */
  point: GeoPoint;
  /** 悬浮/点击时的标题，多数引擎把它显示为 label */
  label?: string;
  /** 样式类别，适配器可据此换图标 */
  kind?: 'default' | 'origin' | 'destination' | 'user' | 'fence';
}

/** 多边形（围栏预览用它；圆由调用方离散成多边形） */
export interface MapPolygon {
  /** 唯一 ID */
  id: string;
  /** 顶点（GCJ-02），不需要重复首点 */
  points: GeoPoint[];
  /** 描边颜色（CSS 颜色串） */
  strokeColor?: string;
  /** 填充颜色（CSS 颜色串，含透明度） */
  fillColor?: string;
  /** 描边宽度（像素） */
  strokeWidth?: number;
}

/** 引擎初始化参数 */
export interface MapEngineOptions {
  /** 初始中心（GCJ-02） */
  center?: GeoPoint;
  /** 初始缩放级别 */
  zoom?: number;
}

/** 取消订阅函数 */
export type Unsubscribe = () => void;

/**
 * 底图引擎
 *
 * 实现方需要保证：所有方法在 `destroy()` 之后调用都不抛异常（幂等），
 * 因为 React 的卸载顺序与异步初始化经常交叉，抛异常会让整页白屏。
 */
export interface MapEngine {
  /** 移动到中心点（GCJ-02） */
  setCenter: (point: GeoPoint) => void;
  /** 设置缩放级别 */
  setZoom: (zoom: number) => void;
  /** 缩放到包含所有点（0 个点不动、1 个点居中并放大）；留边由实现决定 */
  fitBounds: (points: GeoPoint[]) => void;
  /** 全量替换标记 */
  setMarkers: (markers: MapMarker[]) => void;
  /** 全量替换多边形 */
  setPolygons: (polygons: MapPolygon[]) => void;
  /** 订阅地图点击，回调参数为点击位置（GCJ-02） */
  onClick: (handler: (point: GeoPoint) => void) => Unsubscribe;
  /** 销毁：解绑事件、释放图元与地图实例 */
  destroy: () => void;
}

/** 引擎工厂：由适配器提供，容器就绪后异步创建 */
export type MapEngineFactory = (
  container: HTMLElement,
  options?: MapEngineOptions,
) => Promise<MapEngine>;
