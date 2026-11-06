import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { point } from '../coord';
import {
  boundsCenter,
  createTencentMapEngine,
  loadTencentMap,
  resetTencentMapLoader,
  zoomForSpan,
  type TencentOverlay,
  type TencentSdk,
} from './tencent';

/** 假 LatLng：把构造参数留下来，用来锁「纬度在前」这个顺序 */
class FakeLatLng {
  constructor(
    public lat: number,
    public lng: number,
  ) {}
}

/** 记录所有发生过的地图实例 */
const mapInstances: FakeMapInstance[] = [];

class FakeMapInstance {
  options: Record<string, unknown>;

  handlers = new Map<string, (event: { latLng: { lat: number; lng: number } }) => void>();

  setCenter = vi.fn();

  setZoom = vi.fn();

  destroy = vi.fn();

  constructor(_container: HTMLElement, options: Record<string, unknown>) {
    this.options = options;
    mapInstances.push(this);
  }

  on(event: string, handler: (event: { latLng: { lat: number; lng: number } }) => void) {
    this.handlers.set(event, handler);
  }

  off(event: string) {
    this.handlers.delete(event);
  }

  /** 测试触发点击 */
  emit(lat: number, lng: number) {
    this.handlers.get('click')?.({ latLng: { lat, lng } });
  }
}

class FakeOverlay implements TencentOverlay {
  geometries: Array<Record<string, unknown>>;

  setMap = vi.fn();

  constructor(options: Record<string, unknown>) {
    this.geometries = (options.geometries as Array<Record<string, unknown>>) ?? [];
    overlayInstances.push(this);
  }

  setGeometries(geometries: Array<Record<string, unknown>>) {
    this.geometries = geometries;
  }
}

/** 记录所有创建过的图元（标记层与多边形层） */
const overlayInstances: FakeOverlay[] = [];

class FakePolygonStyle {
  constructor(public options: Record<string, unknown>) {}
}

/** 装一个假 SDK 到全局，模拟「脚本已加载完」 */
function installFakeSdk() {
  const sdk = {
    Map: FakeMapInstance,
    LatLng: FakeLatLng,
    MultiMarker: FakeOverlay,
    MultiPolygon: FakeOverlay,
    PolygonStyle: FakePolygonStyle,
  } as unknown as TencentSdk;
  (globalThis as unknown as { TMap?: TencentSdk }).TMap = sdk;
  return sdk;
}

beforeEach(() => {
  resetTencentMapLoader();
  mapInstances.length = 0;
  overlayInstances.length = 0;
});

afterEach(() => {
  delete (globalThis as unknown as { TMap?: TencentSdk }).TMap;
  resetTencentMapLoader();
});

describe('zoomForSpan', () => {
  it('单点给一个街道级缩放', () => {
    expect(zoomForSpan([point(116.4, 39.9, 'GCJ02')])).toBe(16);
  });

  it('跨度越大缩放越小', () => {
    const small = zoomForSpan([point(116.4, 39.9, 'GCJ02'), point(116.402, 39.902, 'GCJ02')]);
    const large = zoomForSpan([point(116.4, 39.9, 'GCJ02'), point(121.47, 31.23, 'GCJ02')]);
    expect(large).toBeLessThan(small);
    expect(large).toBeLessThan(10);
  });

  it('空点集给默认值', () => {
    expect(zoomForSpan([])).toBe(12);
  });

  it('结果被夹在合理区间内', () => {
    const same = zoomForSpan([point(116.4, 39.9, 'GCJ02'), point(116.4, 39.9, 'GCJ02')]);
    expect(same).toBeLessThanOrEqual(17);
    expect(same).toBeGreaterThanOrEqual(3);
  });
});

describe('boundsCenter', () => {
  it('取点集算术平均', () => {
    const center = boundsCenter([point(116.4, 39.9, 'GCJ02'), point(116.5, 40.0, 'GCJ02')]);
    expect(center?.lng).toBeCloseTo(116.45, 6);
    expect(center?.lat).toBeCloseTo(39.95, 6);
    expect(center?.coordType).toBe('GCJ02');
  });

  it('空点集返回 null', () => {
    expect(boundsCenter([])).toBeNull();
  });
});

describe('loadTencentMap', () => {
  it('空 Key 直接拒绝', async () => {
    await expect(loadTencentMap('  ')).rejects.toThrow('Key');
  });

  it('SDK 已在全局时不再插脚本', async () => {
    installFakeSdk();
    const sdk = await loadTencentMap('demo-key');
    expect((sdk as unknown as { Map: unknown }).Map).toBe(FakeMapInstance);
  });

  it('同一 Key 重复调用复用同一个 Promise', () => {
    installFakeSdk();
    const first = loadTencentMap('demo-key');
    const second = loadTencentMap('demo-key');
    expect(first).toBe(second);
  });
});

describe('createTencentMapEngine', () => {
  it('构造 Map 时把 LatLng 的纬度放在第一位', async () => {
    installFakeSdk();
    const factory = createTencentMapEngine('demo-key');
    const engine = await factory({} as HTMLElement, {
      center: point(116.397428, 39.90923, 'GCJ02'),
      zoom: 14,
    });
    const created = mapInstances[0];
    const center = created.options.center as FakeLatLng;
    expect(center.lat).toBeCloseTo(39.90923, 6);
    expect(center.lng).toBeCloseTo(116.397428, 6);
    expect(created.options.zoom).toBe(14);
    engine.destroy();
  });

  it('setMarkers 全量替换标记，位置同样纬度在前', async () => {
    installFakeSdk();
    const engine = await createTencentMapEngine('demo-key')({} as HTMLElement);
    engine.setMarkers([{ id: 'a', point: point(116.5, 39.8, 'GCJ02'), label: '起点' }]);
    const markerLayer = overlayInstances[0];
    const position = markerLayer.geometries[0].position as FakeLatLng;
    expect(position.lat).toBeCloseTo(39.8, 6);
    expect(position.lng).toBeCloseTo(116.5, 6);
    // 再次设置走 setGeometries（增量），不重建图层
    engine.setMarkers([]);
    expect(overlayInstances).toHaveLength(1);
    expect(markerLayer.geometries).toHaveLength(0);
    engine.destroy();
  });

  it('setPolygons 把顶点转成 LatLng 序列', async () => {
    installFakeSdk();
    const engine = await createTencentMapEngine('demo-key')({} as HTMLElement);
    engine.setPolygons([
      {
        id: 'fence',
        points: [
          point(116.4, 39.9, 'GCJ02'),
          point(116.402, 39.9, 'GCJ02'),
          point(116.401, 39.902, 'GCJ02'),
        ],
      },
    ]);
    const polygonLayer = overlayInstances[0];
    const paths = polygonLayer.geometries[0].paths as FakeLatLng[];
    expect(paths).toHaveLength(3);
    expect(paths[0].lat).toBeCloseTo(39.9, 6);
    expect(paths[0].lng).toBeCloseTo(116.4, 6);
    engine.destroy();
  });

  it('点击事件把 SDK 的 latLng 转成带坐标系的点，多个订阅者都会收到', async () => {
    installFakeSdk();
    const engine = await createTencentMapEngine('demo-key')({} as HTMLElement);
    const first = vi.fn();
    const second = vi.fn();
    engine.onClick(first);
    engine.onClick(second);
    mapInstances[0].emit(39.9, 116.4);
    expect(first).toHaveBeenCalledWith({ lng: 116.4, lat: 39.9, coordType: 'GCJ02' });
    expect(second).toHaveBeenCalledTimes(1);
    engine.destroy();
  });

  it('取消订阅后不再收到点击', async () => {
    installFakeSdk();
    const engine = await createTencentMapEngine('demo-key')({} as HTMLElement);
    const handler = vi.fn();
    const off = engine.onClick(handler);
    off();
    mapInstances[0].emit(39.9, 116.4);
    expect(handler).not.toHaveBeenCalled();
    engine.destroy();
  });

  it('destroy 之后调用各方法都不抛异常（幂等）', async () => {
    installFakeSdk();
    const engine = await createTencentMapEngine('demo-key')({} as HTMLElement);
    engine.destroy();
    expect(() => {
      engine.setCenter(point(116.4, 39.9, 'GCJ02'));
      engine.setZoom(12);
      engine.fitBounds([point(116.4, 39.9, 'GCJ02')]);
      engine.setMarkers([]);
      engine.setPolygons([]);
      engine.destroy();
    }).not.toThrow();
    expect(mapInstances[0].destroy).toHaveBeenCalledTimes(1);
  });

  it('destroy 时解绑点击事件（否则地图销毁后回调仍持有组件引用）', async () => {
    installFakeSdk();
    const engine = await createTencentMapEngine('demo-key')({} as HTMLElement);
    const handler = vi.fn();
    engine.onClick(handler);
    engine.destroy();
    mapInstances[0].emit(39.9, 116.4);
    expect(handler).not.toHaveBeenCalled();
  });

  it('fitBounds 只接收点时不动，接收多点时居中', async () => {
    installFakeSdk();
    const engine = await createTencentMapEngine('demo-key')({} as HTMLElement);
    engine.fitBounds([]);
    expect(mapInstances[0].setCenter).not.toHaveBeenCalled();
    engine.fitBounds([point(116.4, 39.9, 'GCJ02'), point(116.5, 40.0, 'GCJ02')]);
    const called = mapInstances[0].setCenter.mock.calls[0][0] as FakeLatLng;
    expect(called.lat).toBeCloseTo(39.95, 6);
    engine.destroy();
  });
});
