import { describe, expect, it } from 'vitest';
import { arrowPolygon, createMinimapProjection, mapDirection, viewWedgePolygon } from '@/scene';

/**
 * 小地图的投影与几何。
 *
 * 这些断言存在的理由很具体：**方向画反了在真机上只表现为"感觉怪怪的"**，
 * 不会报错、不会崩，review 时也看不出来。把"北在上、东在右、比例不溢出"
 * 写成断言，是唯一能让它不悄悄退化的办法。
 */

const SIZE = 200;
const GROUND = 80;

describe('createMinimapProjection', () => {
  const projection = createMinimapProjection(SIZE, GROUND);

  it('世界原点落在画布中心', () => {
    expect(projection.toMap(0, 0)).toEqual({ x: 100, y: 100 });
  });

  it('正北（-Z）在地图上方', () => {
    expect(projection.toMap(0, -10).y).toBeLessThan(projection.toMap(0, 0).y);
  });

  it('正东（+X）在地图右方', () => {
    expect(projection.toMap(10, 0).x).toBeGreaterThan(projection.toMap(0, 0).x);
  });

  it('整块广场都装得下，且四周留了边距', () => {
    const half = GROUND / 2;
    const corner = projection.toMap(half, half);

    expect(corner.x).toBeLessThan(SIZE);
    expect(corner.y).toBeLessThan(SIZE);
    // 留白：广场边距画布边缘应当还有余量
    expect(corner.x).toBeLessThan(SIZE - 1);
  });

  it('比例是等比的：x 与 z 方向每单位走一样多像素', () => {
    const stepX = projection.toMap(10, 0).x - projection.toMap(0, 0).x;
    const stepZ = projection.toMap(0, 10).y - projection.toMap(0, 0).y;

    expect(stepX).toBeCloseTo(stepZ, 10);
  });

  it('toPixels 与 toMap 的比例一致', () => {
    expect(projection.toPixels(10)).toBeCloseTo(
      projection.toMap(10, 0).x - projection.toMap(0, 0).x,
      10,
    );
  });

  it('地面尺寸为 0 时不产生 NaN/Infinity（会污染整张地图）', () => {
    const degenerate = createMinimapProjection(SIZE, 0);
    const point = degenerate.toMap(1, 1);

    expect(Number.isFinite(point.x)).toBe(true);
    expect(Number.isFinite(point.y)).toBe(true);
  });
});

describe('mapDirection', () => {
  it('角度 0 指向 +Z（地图下方）', () => {
    const direction = mapDirection(0);
    expect(direction.x).toBeCloseTo(0, 10);
    expect(direction.y).toBeCloseTo(1, 10);
  });

  it('角度 π 指向 -Z（地图上方，即正北）', () => {
    const direction = mapDirection(Math.PI);
    expect(direction.x).toBeCloseTo(0, 10);
    expect(direction.y).toBeCloseTo(-1, 10);
  });

  it('角度 π/2 指向 +X（地图右方，即正东）', () => {
    const direction = mapDirection(Math.PI / 2);
    expect(direction.x).toBeCloseTo(1, 10);
    expect(direction.y).toBeCloseTo(0, 10);
  });

  it('始终是单位向量', () => {
    [0, 0.7, 2.2, -1.9, Math.PI].forEach((angle) => {
      const direction = mapDirection(angle);
      expect(Math.hypot(direction.x, direction.y)).toBeCloseTo(1, 10);
    });
  });
});

describe('viewWedgePolygon', () => {
  const origin = { x: 50, y: 50 };

  it('第一个点是原点，扇形的中轴对着相机方位', () => {
    const points = viewWedgePolygon(origin, Math.PI, 0.5, 20, 4);

    expect(points[0]).toEqual(origin);
    // 4 段 → 中间那个点（索引 3）落在中轴上
    const middle = points[3];
    expect(middle.x).toBeCloseTo(origin.x, 6);
    expect(middle.y).toBeCloseTo(origin.y - 20, 6);
  });

  it('点数 = 原点 + 分段数 + 1', () => {
    expect(viewWedgePolygon(origin, 0, 0.4, 10, 6)).toHaveLength(8);
  });

  it('所有扇面点都在给定半径上（是个扇形而不是任意形状）', () => {
    const points = viewWedgePolygon(origin, 1.1, 0.6, 15, 8).slice(1);

    points.forEach((point) => {
      expect(Math.hypot(point.x - origin.x, point.y - origin.y)).toBeCloseTo(15, 6);
    });
  });
});

describe('arrowPolygon', () => {
  it('箭头尖端在朝向方向上', () => {
    const origin = { x: 0, y: 0 };
    const [tip] = arrowPolygon(origin, Math.PI, 10); // 朝北 → 地图上方

    expect(tip.x).toBeCloseTo(0, 10);
    expect(tip.y).toBeCloseTo(-10, 10);
  });

  it('三个顶点、且不共线（不会退化成一条线）', () => {
    const [tip, left, right] = arrowPolygon({ x: 5, y: 5 }, 0.3, 8);
    const area = Math.abs(
      (left.x - tip.x) * (right.y - tip.y) - (right.x - tip.x) * (left.y - tip.y),
    );

    expect(area).toBeGreaterThan(1);
  });
});
