/**
 * 坐标系转换与球面几何（前端侧）。
 *
 * 算法参数与后端 `CoordTransform` / `GeoDistance` / `LocalProjection` **逐字一致**：
 * 前端算出来的距离、面积要能和服务端对得上，否则会出现「页面上显示 98 米，接口说超范围」。
 * 后端改参数时这里必须同步，单测里锁了偏移量与往返误差。
 *
 * 为什么前端也要一套：浏览器 `navigator.geolocation` 给的是 **WGS84**，
 * 而腾讯/高德底图用的是 **GCJ-02**。直接拿 GPS 原始坐标打点，图上会偏 300–500 米，
 * 且数据本身完全合法——这是最难查的一类问题，必须在源头转。
 */

import type { CoordType, GeoPoint } from './types';

/** 不带坐标系的裸坐标（入参用，调用方自己保证口径） */
export interface LngLat {
  lng: number;
  lat: number;
}

/** 克拉索夫斯基椭球长半轴 */
const ELLIPSOID_A = 6378245.0;
/** 椭球偏心率平方（与后端同值；末尾几位在 double 里本就无法精确表示，多写只会触发 lint） */
const ELLIPSOID_EE = 0.006693421622965943;
/** 百度偏移用的大圆周率常数 */
const X_PI = (Math.PI * 3000.0) / 180.0;
/** 反算迭代次数：5 次可把误差压到 1e-9 度（毫米级） */
const INVERSE_ITERATIONS = 5;
const BD_OFFSET_LNG = 0.0065;
const BD_OFFSET_LAT = 0.006;
const BD_FINE_FACTOR = 0.00002;
const BD_ANGLE_FACTOR = 0.000003;

/** 中国境内范围（与后端一致，用于判定是否需要加偏） */
const CHINA_MIN_LNG = 72.004;
const CHINA_MAX_LNG = 137.8347;
const CHINA_MIN_LAT = 0.8293;
const CHINA_MAX_LAT = 55.8271;

/** 地球平均半径（米），与后端 `GeoDistance` 相同 */
export const EARTH_RADIUS_METERS = 6371008.8;

/** 每纬度 1 度对应的米数 */
const METERS_PER_LAT_DEGREE = (Math.PI * EARTH_RADIUS_METERS) / 180.0;

/** 判断坐标是否在中国境外（境外不加偏） */
export function outOfChina(lng: number, lat: number): boolean {
  return lng < CHINA_MIN_LNG || lng > CHINA_MAX_LNG || lat < CHINA_MIN_LAT || lat > CHINA_MAX_LAT;
}

/** 纬度加偏分量 */
function transformLat(x: number, y: number): number {
  let ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x));
  ret += ((20.0 * Math.sin(6.0 * x * Math.PI) + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0) / 3.0;
  ret += ((20.0 * Math.sin(y * Math.PI) + 40.0 * Math.sin((y / 3.0) * Math.PI)) * 2.0) / 3.0;
  ret +=
    ((160.0 * Math.sin((y / 12.0) * Math.PI) + 320.0 * Math.sin((y * Math.PI) / 30.0)) * 2.0) / 3.0;
  return ret;
}

/** 经度加偏分量 */
function transformLng(x: number, y: number): number {
  let ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x));
  ret += ((20.0 * Math.sin(6.0 * x * Math.PI) + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0) / 3.0;
  ret += ((20.0 * Math.sin(x * Math.PI) + 40.0 * Math.sin((x / 3.0) * Math.PI)) * 2.0) / 3.0;
  ret +=
    ((150.0 * Math.sin((x / 12.0) * Math.PI) + 300.0 * Math.sin((x / 30.0) * Math.PI)) * 2.0) / 3.0;
  return ret;
}

/** WGS84 → GCJ-02（境外原样返回） */
export function wgs84ToGcj02(point: LngLat): LngLat {
  if (outOfChina(point.lng, point.lat)) {
    return { lng: point.lng, lat: point.lat };
  }
  let dLat = transformLat(point.lng - 105.0, point.lat - 35.0);
  let dLng = transformLng(point.lng - 105.0, point.lat - 35.0);
  const radLat = (point.lat / 180.0) * Math.PI;
  let magic = Math.sin(radLat);
  magic = 1 - ELLIPSOID_EE * magic * magic;
  const sqrtMagic = Math.sqrt(magic);
  dLat = (dLat * 180.0) / (((ELLIPSOID_A * (1 - ELLIPSOID_EE)) / (magic * sqrtMagic)) * Math.PI);
  dLng = (dLng * 180.0) / ((ELLIPSOID_A / sqrtMagic) * Math.cos(radLat) * Math.PI);
  return { lng: point.lng + dLng, lat: point.lat + dLat };
}

/**
 * GCJ-02 → WGS84（无解析解，迭代反算）
 *
 * 后端同样是 5 次迭代。少迭代几次不会报错，只会「大概准」——
 * 而这种误差在 1–2 米量级时谁都看不出来，等它和别的数据源对齐时才会暴露。
 */
export function gcj02ToWgs84(point: LngLat): LngLat {
  let lng = point.lng;
  let lat = point.lat;
  for (let i = 0; i < INVERSE_ITERATIONS; i++) {
    const forward = wgs84ToGcj02({ lng, lat });
    lng += point.lng - forward.lng;
    lat += point.lat - forward.lat;
  }
  return { lng, lat };
}

/** GCJ-02 → BD-09 */
export function gcj02ToBd09(point: LngLat): LngLat {
  const x = point.lng;
  const y = point.lat;
  const z = Math.sqrt(x * x + y * y) + BD_FINE_FACTOR * Math.sin(y * X_PI);
  const theta = Math.atan2(y, x) + BD_ANGLE_FACTOR * Math.cos(x * X_PI);
  return { lng: Math.cos(theta) * z + BD_OFFSET_LNG, lat: Math.sin(theta) * z + BD_OFFSET_LAT };
}

/** BD-09 → GCJ-02 */
export function bd09ToGcj02(point: LngLat): LngLat {
  const x = point.lng - BD_OFFSET_LNG;
  const y = point.lat - BD_OFFSET_LAT;
  const z = Math.sqrt(x * x + y * y) - BD_FINE_FACTOR * Math.sin(y * X_PI);
  const theta = Math.atan2(y, x) - BD_ANGLE_FACTOR * Math.cos(x * X_PI);
  return { lng: Math.cos(theta) * z, lat: Math.sin(theta) * z };
}

/** 构造带坐标系的点 */
export function point(lng: number, lat: number, coordType: CoordType): GeoPoint {
  return { lng, lat, coordType };
}

/**
 * 转换到目标坐标系（以 GCJ-02 为中转站）
 *
 * WGS84 与 BD-09 之间没有直接算法，硬凑一步的误差比走两跳更大。
 */
export function convertPoint(source: GeoPoint, target: CoordType): GeoPoint {
  if (source.coordType === target) {
    return source;
  }
  const gcj =
    source.coordType === 'WGS84'
      ? wgs84ToGcj02(source)
      : source.coordType === 'BD09'
        ? bd09ToGcj02(source)
        : source;
  if (target === 'GCJ02') {
    return { lng: gcj.lng, lat: gcj.lat, coordType: 'GCJ02' };
  }
  const out = target === 'BD09' ? gcj02ToBd09(gcj) : gcj02ToWgs84(gcj);
  return { lng: out.lng, lat: out.lat, coordType: target };
}

/** 两点直线距离（米）；跨坐标系时先统一，不做「度」上的近似 */
export function distanceMeters(from: GeoPoint, to: GeoPoint): number {
  const target = convertPoint(to, from.coordType);
  const radLat1 = (from.lat * Math.PI) / 180;
  const radLat2 = (target.lat * Math.PI) / 180;
  const deltaLat = radLat2 - radLat1;
  const deltaLng = ((target.lng - from.lng) * Math.PI) / 180;
  const sinLat = Math.sin(deltaLat / 2);
  const sinLng = Math.sin(deltaLng / 2);
  const h = sinLat * sinLat + Math.cos(radLat1) * Math.cos(radLat2) * sinLng * sinLng;
  return 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1, Math.sqrt(h)));
}

/** 每经度 1 度对应的米数（随纬度收缩） */
export function metersPerLngDegree(lat: number): number {
  const radians = (Math.min(Math.abs(lat), 89.9) * Math.PI) / 180;
  return METERS_PER_LAT_DEGREE * Math.cos(radians);
}

/**
 * 多边形面积（平方米）
 *
 * 用「以质心为原点的局部等距投影 + 鞋带公式」：球面多边形面积需要积分，
 * 而在几公里量级的围栏上，等距投影的误差远小于「度当米算」带来的几十倍偏差。
 * 结果取绝对值——顶点顺序（顺时针/逆时针）不该影响面积。
 */
export function polygonArea(points: GeoPoint[]): number {
  if (points.length < 3) {
    return 0;
  }
  const normalized = points.map((item) => convertPoint(item, 'WGS84'));
  let sumLat = 0;
  let sumLng = 0;
  normalized.forEach((item) => {
    sumLat += item.lat;
    sumLng += item.lng;
  });
  const originLat = sumLat / normalized.length;
  const originLng = sumLng / normalized.length;
  const perLng = metersPerLngDegree(originLat);
  const projected = normalized.map((item) => ({
    x: (item.lng - originLng) * perLng,
    y: (item.lat - originLat) * METERS_PER_LAT_DEGREE,
  }));
  let doubleArea = 0;
  for (let i = 0; i < projected.length; i++) {
    const current = projected[i];
    const next = projected[(i + 1) % projected.length];
    doubleArea += current.x * next.y - next.x * current.y;
  }
  return Math.abs(doubleArea) / 2;
}

/** 多边形周长（米），自动闭合 */
export function polygonPerimeter(points: GeoPoint[]): number {
  if (points.length < 2) {
    return 0;
  }
  let total = 0;
  for (let i = 0; i < points.length; i++) {
    total += distanceMeters(points[i], points[(i + 1) % points.length]);
  }
  return total;
}

/**
 * 把圆离散成多边形顶点（逆时针，从正北开始）
 *
 * 底图上没有「圆」这个原始图元时用它画预览，**口径与后端保持一致**：
 * 后端对圆围栏也是按中心 + 半径判定，这里只影响「画出来」，
 * 但离散成 64 边形在几公里尺度上的视觉偏差远小于半个像素。
 */
export function circleToPolygon(center: GeoPoint, radiusMeters: number, segments = 64): GeoPoint[] {
  const count = Math.max(8, Math.floor(segments));
  const result: GeoPoint[] = [];
  for (let i = 0; i < count; i++) {
    const bearing = (i / count) * 2 * Math.PI;
    result.push(offsetByBearing(center, radiusMeters, bearing));
  }
  return result;
}

/**
 * 按方位角与距离求目标点
 *
 * @param origin 起点
 * @param distance 距离（米）
 * @param bearingRadians 方位角（弧度，正北为 0，顺时针）
 */
export function offsetByBearing(
  origin: GeoPoint,
  distance: number,
  bearingRadians: number,
): GeoPoint {
  const base = convertPoint(origin, 'WGS84');
  const angular = distance / EARTH_RADIUS_METERS;
  const lat1 = (base.lat * Math.PI) / 180;
  const lng1 = (base.lng * Math.PI) / 180;
  const lat2 = Math.asin(
    Math.sin(lat1) * Math.cos(angular) +
      Math.cos(lat1) * Math.sin(angular) * Math.cos(bearingRadians),
  );
  const lng2 =
    lng1 +
    Math.atan2(
      Math.sin(bearingRadians) * Math.sin(angular) * Math.cos(lat1),
      Math.cos(angular) - Math.sin(lat1) * Math.sin(lat2),
    );
  return {
    lng: (lng2 * 180) / Math.PI,
    lat: (lat2 * 180) / Math.PI,
    coordType: 'WGS84',
  };
}

/** 保留 6 位小数（约 0.1 米），避免把浮点噪声带进请求体与展示 */
export function roundCoord(value: number): number {
  return Math.round(value * 1e6) / 1e6;
}
