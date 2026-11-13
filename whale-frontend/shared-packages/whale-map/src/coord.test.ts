import { describe, expect, it } from 'vitest';
import {
  circleToPolygon,
  convertPoint,
  distanceMeters,
  gcj02ToWgs84,
  offsetByBearing,
  outOfChina,
  point,
  polygonArea,
  polygonPerimeter,
  wgs84ToGcj02,
} from './coord';

/** 天安门 WGS84 坐标 */
const TIANANMEN_WGS84 = point(116.39123, 39.90729, 'WGS84');

describe('wgs84ToGcj02', () => {
  it('北京地区加偏量落在 300–800 米之间', () => {
    const gcj = wgs84ToGcj02(TIANANMEN_WGS84);
    // 用 WGS84 度量偏移：把两个点都当 WGS84 传给 haversine，避免再走一次转换
    const shift = distanceMeters(TIANANMEN_WGS84, point(gcj.lng, gcj.lat, 'WGS84'));
    expect(shift).toBeGreaterThan(300);
    expect(shift).toBeLessThan(800);
  });

  it('加偏后经度与纬度都增大（北京地区）', () => {
    const gcj = wgs84ToGcj02(TIANANMEN_WGS84);
    expect(gcj.lng).toBeGreaterThan(TIANANMEN_WGS84.lng);
    expect(gcj.lat).toBeGreaterThan(TIANANMEN_WGS84.lat);
  });

  it('境外不加偏', () => {
    const tokyo = point(139.767, 35.681, 'WGS84');
    const gcj = wgs84ToGcj02(tokyo);
    expect(gcj.lng).toBe(tokyo.lng);
    expect(gcj.lat).toBe(tokyo.lat);
    expect(outOfChina(tokyo.lng, tokyo.lat)).toBe(true);
  });
});

describe('gcj02ToWgs84', () => {
  it('往返误差小于 1e-6 度（约 0.1 米）', () => {
    const gcj = wgs84ToGcj02(TIANANMEN_WGS84);
    const back = gcj02ToWgs84(gcj);
    expect(Math.abs(back.lng - TIANANMEN_WGS84.lng)).toBeLessThan(1e-6);
    expect(Math.abs(back.lat - TIANANMEN_WGS84.lat)).toBeLessThan(1e-6);
  });

  it('反算出的点比 GCJ 点更靠近真实位置', () => {
    const gcj = wgs84ToGcj02(TIANANMEN_WGS84);
    const toTrue = distanceMeters(point(gcj.lng, gcj.lat, 'WGS84'), TIANANMEN_WGS84);
    const back = gcj02ToWgs84(gcj);
    const afterInverse = distanceMeters(point(back.lng, back.lat, 'WGS84'), TIANANMEN_WGS84);
    expect(afterInverse).toBeLessThan(toTrue);
    expect(afterInverse).toBeLessThan(1);
  });
});

describe('convertPoint', () => {
  it('同坐标系直接返回原对象', () => {
    const source = point(116.4, 39.9, 'GCJ02');
    expect(convertPoint(source, 'GCJ02')).toBe(source);
  });

  it('BD09 经 GCJ02 中转，往返误差在 1 米内', () => {
    const gcj = point(116.397428, 39.90923, 'GCJ02');
    const bd = convertPoint(gcj, 'BD09');
    const back = convertPoint(bd, 'GCJ02');
    expect(distanceMeters(gcj, back)).toBeLessThan(1);
  });
});

describe('distanceMeters', () => {
  it('北京到上海约 1060–1080 公里', () => {
    const beijing = point(116.4074, 39.9042, 'GCJ02');
    const shanghai = point(121.4737, 31.2304, 'GCJ02');
    const meters = distanceMeters(beijing, shanghai);
    expect(meters).toBeGreaterThan(1_050_000);
    expect(meters).toBeLessThan(1_090_000);
  });

  it('跨坐标系时先统一再算（不会出现几度当几米）', () => {
    const gcj = point(116.4074, 39.9042, 'GCJ02');
    const wgs = convertPoint(gcj, 'WGS84');
    // 注意：distanceMeters 会把入参统一到第一个点的坐标系，所以若直接传 {gcj, wgs}
    // 两者会被算成同一个物理点、距离恒为 0——这正是「加偏前后偏移量」测不出来的原因。
    // 要度量偏移，得把加偏后的数值当成同一种坐标系来比较。
    const shift = distanceMeters(gcj, point(wgs.lng, wgs.lat, 'GCJ02'));
    expect(shift).toBeGreaterThan(300);
    expect(shift).toBeLessThan(800);

    // 真实语义：同一物理位置在两种坐标系下的距离为 0（统一坐标系后完全重合）
    expect(distanceMeters(gcj, wgs)).toBe(0);
  });

  it('同一点距离为 0', () => {
    const a = point(116.4, 39.9, 'GCJ02');
    expect(distanceMeters(a, a)).toBe(0);
  });
});

describe('polygonArea', () => {
  it('100 米见方的四边形面积约 10000 平方米', () => {
    const center = point(116.4, 39.9, 'WGS84');
    // 从四条边的中点构造正方形：半径 50√2 ≈ 70.71 米
    const d = 50 * Math.SQRT2;
    const corners = [
      offsetByBearing(center, d, Math.PI / 4),
      offsetByBearing(center, d, (3 * Math.PI) / 4),
      offsetByBearing(center, d, (5 * Math.PI) / 4),
      offsetByBearing(center, d, (7 * Math.PI) / 4),
    ];
    const area = polygonArea(corners);
    expect(area).toBeGreaterThan(9900);
    expect(area).toBeLessThan(10100);
  });

  it('顶点顺序反转不影响面积（取绝对值）', () => {
    const center = point(116.4, 39.9, 'WGS84');
    const ring = circleToPolygon(center, 120, 32);
    const reversed = [...ring].reverse();
    expect(polygonArea(reversed)).toBeCloseTo(polygonArea(ring), 3);
  });

  it('少于 3 个点面积为 0', () => {
    expect(polygonArea([])).toBe(0);
    expect(polygonArea([point(116.4, 39.9, 'GCJ02')])).toBe(0);
  });
});

describe('circleToPolygon', () => {
  it('离散后的多边形面积接近 πr²（误差 < 1%）', () => {
    const center = point(116.4, 39.9, 'WGS84');
    const radius = 200;
    const ring = circleToPolygon(center, radius, 64);
    const area = polygonArea(ring);
    const ideal = Math.PI * radius * radius;
    expect(Math.abs(area - ideal) / ideal).toBeLessThan(0.01);
  });

  it('每个顶点到圆心的距离等于半径（误差 < 0.5 米）', () => {
    const center = point(116.4, 39.9, 'WGS84');
    const ring = circleToPolygon(center, 300, 16);
    ring.forEach((item) => {
      expect(Math.abs(distanceMeters(center, item) - 300)).toBeLessThan(0.5);
    });
  });

  it('段数有下限：传 2 也至少给出 8 个点', () => {
    const ring = circleToPolygon(point(116.4, 39.9, 'GCJ02'), 100, 2);
    expect(ring.length).toBeGreaterThanOrEqual(8);
  });
});

describe('polygonPerimeter', () => {
  it('100 米正方形周长约 400 米', () => {
    const center = point(116.4, 39.9, 'WGS84');
    const d = 50 * Math.SQRT2;
    const corners = [
      offsetByBearing(center, d, Math.PI / 4),
      offsetByBearing(center, d, (3 * Math.PI) / 4),
      offsetByBearing(center, d, (5 * Math.PI) / 4),
      offsetByBearing(center, d, (7 * Math.PI) / 4),
    ];
    expect(polygonPerimeter(corners)).toBeGreaterThan(398);
    expect(polygonPerimeter(corners)).toBeLessThan(402);
  });
});
