import { describe, expect, it } from 'vitest';
import { point } from './coord';
import {
  CIRCLE_SEGMENTS,
  MAX_POLYGON_POINTS,
  createFenceDraft,
  draftOutline,
  fenceDraftReducer,
  fenceDraftStats,
  fenceToDraft,
  formatArea,
  formatDistance,
  isSelfIntersecting,
  toFenceSaveParams,
  validateFenceDraft,
  type FenceDraftState,
} from './fenceDraft';
import type { FenceVO } from './types';

/** 以「北京某点 + 偏移度数」造点，方便目测（1e-3 度 ≈ 100 米） */
function at(dx: number, dy: number) {
  return point(116.4 + dx, 39.9 + dy, 'GCJ02');
}

/** 正方形草稿（边长约 220 米） */
function squareDraft(): FenceDraftState {
  let state = createFenceDraft();
  [at(0, 0), at(0.002, 0), at(0.002, 0.002), at(0, 0.002)].forEach((item) => {
    state = fenceDraftReducer(state, { type: 'addPoint', point: item });
  });
  return state;
}

describe('fenceDraftReducer', () => {
  it('加点后顶点数增加，且自动切回多边形', () => {
    let state = createFenceDraft();
    state = fenceDraftReducer(state, { type: 'setShape', shape: 'CIRCLE' });
    state = fenceDraftReducer(state, { type: 'addPoint', point: at(0, 0) });
    expect(state.points).toHaveLength(1);
    expect(state.shape).toBe('POLYGON');
  });

  it('撤销只删最后一个点', () => {
    const state = fenceDraftReducer(squareDraft(), { type: 'undo' });
    expect(state.points).toHaveLength(3);
  });

  it('撤销在空草稿上是空操作（返回同一对象）', () => {
    const empty = createFenceDraft();
    expect(fenceDraftReducer(empty, { type: 'undo' })).toBe(empty);
  });

  it('顶点数达到上限后不再追加', () => {
    let state = createFenceDraft();
    for (let i = 0; i < MAX_POLYGON_POINTS + 5; i++) {
      state = fenceDraftReducer(state, { type: 'addPoint', point: at(i * 1e-5, 0) });
    }
    expect(state.points).toHaveLength(MAX_POLYGON_POINTS);
  });

  it('删除越界索引不改变状态', () => {
    const state = squareDraft();
    expect(fenceDraftReducer(state, { type: 'removePoint', index: 99 })).toBe(state);
    expect(fenceDraftReducer(state, { type: 'removePoint', index: -1 })).toBe(state);
  });

  it('拖动顶点只改一个点', () => {
    const state = fenceDraftReducer(squareDraft(), {
      type: 'movePoint',
      index: 1,
      point: at(0.003, 0),
    });
    expect(state.points[1].lng).toBeCloseTo(116.403, 6);
    expect(state.points[0].lng).toBeCloseTo(116.4, 6);
  });

  it('只改半径不会凭空给圆心安一个位置', () => {
    const state = fenceDraftReducer(createFenceDraft(), { type: 'setRadius', radiusMeters: 800 });
    expect(state.radiusMeters).toBe(800);
    expect(state.center).toBeNull();
  });

  it('半径不接受 0 或负数', () => {
    const state = fenceDraftReducer(createFenceDraft(), { type: 'setRadius', radiusMeters: 0 });
    expect(state.radiusMeters).toBeGreaterThan(0);
  });

  it('清空回到初始状态', () => {
    const state = fenceDraftReducer(squareDraft(), { type: 'clear' });
    expect(state.points).toHaveLength(0);
    expect(state.shape).toBe('POLYGON');
  });
});

describe('draftOutline', () => {
  it('圆形草稿离散成固定段数的多边形', () => {
    const state = fenceDraftReducer(createFenceDraft(), {
      type: 'setCircle',
      center: at(0, 0),
      radiusMeters: 300,
    });
    expect(draftOutline(state)).toHaveLength(CIRCLE_SEGMENTS);
  });

  it('圆形草稿没设圆心时没有轮廓', () => {
    const state = fenceDraftReducer(createFenceDraft(), { type: 'setShape', shape: 'CIRCLE' });
    expect(draftOutline(state)).toEqual([]);
  });

  it('回显的围栏带重复闭合点时会被去掉（否则出现零长度边）', () => {
    const fence: FenceVO = {
      id: 1,
      name: '厂区',
      fenceType: 'POLYGON',
      bizType: null,
      bizId: null,
      centerLng: null,
      centerLat: null,
      radiusMeters: null,
      points: [
        { lng: 116.4, lat: 39.9, coordType: 'GCJ02' },
        { lng: 116.402, lat: 39.9, coordType: 'GCJ02' },
        { lng: 116.402, lat: 39.902, coordType: 'GCJ02' },
        { lng: 116.4, lat: 39.9, coordType: 'GCJ02' },
      ],
      coordType: 'GCJ02',
      enabled: 1,
      remark: null,
      updateTime: null,
    };
    const draft = fenceToDraft(fence);
    expect(draft.points).toHaveLength(4);
    expect(draftOutline(draft)).toHaveLength(3);
  });
});

describe('fenceDraftStats', () => {
  it('220 米见方的正方形面积在 4.8 万平米上下', () => {
    const stats = fenceDraftStats(squareDraft());
    expect(stats.closed).toBe(true);
    // 0.002 度纬度 ≈ 222 米，0.002 度经度在 39.9° ≈ 171 米
    expect(stats.areaSquareMeters).toBeGreaterThan(30000);
    expect(stats.areaSquareMeters).toBeLessThan(45000);
  });

  it('圆按 πr² 计算，不走离散多边形', () => {
    const state = fenceDraftReducer(createFenceDraft(), {
      type: 'setCircle',
      center: at(0, 0),
      radiusMeters: 100,
    });
    const stats = fenceDraftStats(state);
    expect(stats.areaSquareMeters).toBeCloseTo(Math.PI * 100 * 100, 6);
    expect(stats.perimeterMeters).toBeCloseTo(2 * Math.PI * 100, 6);
  });

  it('不足 3 个点时 closed 为 false', () => {
    let state = createFenceDraft();
    state = fenceDraftReducer(state, { type: 'addPoint', point: at(0, 0) });
    state = fenceDraftReducer(state, { type: 'addPoint', point: at(0.001, 0) });
    expect(fenceDraftStats(state).closed).toBe(false);
  });
});

describe('isSelfIntersecting', () => {
  it('蝴蝶结形状判定为自交', () => {
    const bowtie = [at(0, 0), at(0.002, 0.002), at(0.002, 0), at(0, 0.002)];
    expect(isSelfIntersecting(bowtie)).toBe(true);
  });

  it('正常四边形不自交', () => {
    expect(isSelfIntersecting([at(0, 0), at(0.002, 0), at(0.002, 0.002), at(0, 0.002)])).toBe(
      false,
    );
  });

  it('三点与三角形永远不自交', () => {
    expect(isSelfIntersecting([at(0, 0), at(0.002, 0), at(0.001, 0.002)])).toBe(false);
  });
});

describe('validateFenceDraft', () => {
  it('合法多边形返回 null', () => {
    expect(validateFenceDraft(squareDraft())).toBeNull();
  });

  it('少于 3 点提示顶点不足', () => {
    let state = createFenceDraft();
    state = fenceDraftReducer(state, { type: 'addPoint', point: at(0, 0) });
    expect(validateFenceDraft(state)).toContain('至少');
  });

  it('圆形没设圆心时提示点选圆心', () => {
    const state = fenceDraftReducer(createFenceDraft(), { type: 'setShape', shape: 'CIRCLE' });
    expect(validateFenceDraft(state)).toContain('圆心');
  });

  it('退化（三点共线）多边形提示面积过小', () => {
    let state = createFenceDraft();
    [at(0, 0), at(0.001, 0), at(0.002, 0)].forEach((item) => {
      state = fenceDraftReducer(state, { type: 'addPoint', point: item });
    });
    expect(validateFenceDraft(state)).toContain('面积');
  });
});

describe('toFenceSaveParams', () => {
  it('多边形提交声明 GCJ02，且不带重复闭合点', () => {
    const params = toFenceSaveParams(squareDraft(), { name: '厂区', enabled: 1 });
    expect(params.fenceType).toBe('POLYGON');
    expect(params.coordType).toBe('GCJ02');
    expect(params.points).toHaveLength(4);
    expect(params.centerLng).toBeUndefined();
  });

  it('圆形提交带圆心与半径，不带顶点', () => {
    const state = fenceDraftReducer(createFenceDraft(), {
      type: 'setCircle',
      center: at(0.001, 0.001),
      radiusMeters: 350.4,
    });
    const params = toFenceSaveParams(state, { name: '打卡范围' });
    expect(params.fenceType).toBe('CIRCLE');
    expect(params.radiusMeters).toBe(350);
    expect(params.points).toBeUndefined();
    expect(params.centerLng).toBeCloseTo(116.401, 6);
  });

  it('坐标保留 6 位小数，避免把浮点噪声写进请求', () => {
    let state = createFenceDraft();
    [at(0.0000001234, 0), at(0.002, 0), at(0.002, 0.002)].forEach((item) => {
      state = fenceDraftReducer(state, { type: 'addPoint', point: item });
    });
    const params = toFenceSaveParams(state, { name: '测试' });
    const first = params.points?.[0];
    expect(first?.lng).toBe(116.4);
  });
});

describe('格式化', () => {
  it('面积分级显示', () => {
    expect(formatArea(0)).toBe('—');
    expect(formatArea(3200)).toBe('3200 m²');
    expect(formatArea(25000)).toBe('2.50 公顷');
    expect(formatArea(3_000_000)).toBe('3.00 km²');
  });

  it('距离内外有符号，超过 1 公里用 km', () => {
    expect(formatDistance(48.4)).toBe('48 m');
    expect(formatDistance(-30)).toBe('-30 m');
    expect(formatDistance(1500)).toBe('1.50 km');
  });
});
