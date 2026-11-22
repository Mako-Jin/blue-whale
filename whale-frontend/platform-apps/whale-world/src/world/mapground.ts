import * as THREE from 'three';

/**
 * 舆图司（S7/T7.6）的地面舆图：把库里的围栏真的画到殿前的地面上。
 *
 * <p><b>为什么是地面而不是又一块屏风。</b>这座殿管的是"哪片地归谁管"，
 * 屏风上排一张表只能读出名字与状态，读不出<b>形状</b>——两个围栏在地理上是否
 * 重叠、某个圆是否把隔壁坊圈了进去，只有画出来才看得见。所以本殿的特殊项是
 * 一块平铺在地面上的舆图：围栏按真实经纬度投影到画布，圆形是一个圈、
 * 多边形是一条闭合折线，停用的画成虚线。</p>
 *
 * <p><b>为什么画布而不是贴图。</b>围栏是租户自己划的，数量与位置都不固定，
 * 预先做一张图不可能覆盖；用 canvas 现画，几何完全由数据决定，离线也好、
 * 软渲染环境也好，都不依赖任何外部图片。</p>
 *
 * <p><b>不重绘。</b>舆图不是动画：数据不变就一直是同一张图。所以这里只在
 * 数据到达时画一次（{@link MapGroundHandle.setFences}），主循环只负责把它挂着。
 * 这与影戏院的幕布刻意相反——那块必须逐帧画，因为它的意义就是"在放东西"。</p>
 *
 * <p><b>取不到数就画一张空图并在探针里写明原因。</b>{@link MapGroundHandle.probe}
 * 的 {@code mode} 分四档：{@code live}（有围栏）、{@code empty}（库里有账号但
 * 一条围栏都没划）、{@code denied}（没凭据或没登录）、{@code error}（接口失败）。
 * e2e 无法"看"，只能读这个字段与 {@code contrast}（画布亮度标准差，
 * 纯色底约 0，有图形时明显大于 0）。</p>
 *
 * @author BlueWhale
 */

/** 画布宽度：1024 在近距离看字不糊，显存也就 2.8MB */
const CANVAS_W = 1000;

/** 画布高度：与地面网格 10×7 同比例 */
const CANVAS_H = 700;

/** 画布四周留白（像素）：题记与罗盘都放这里 */
const PAD = 54;

/** 最多画多少个围栏：再多画布上就成一团墨，台账在屏风上看得更清楚 */
const MAX_FENCES = 40;

/**
 * 地面舆图的宽（米）与进深（米）：8 / 5.6 与画布 1000×700 同比。
 *
 * <p><b>比例必须一致。</b>画布是等比投影，平面若与画布比例不同，圆形围栏会被
 * 拉成椭圆、正方形围栏会被拉成长方形——"围栏画歪了"这类问题极难查，因为数据没错，
 * 错的是贴图的拉伸。</p>
 *
 * <p><b>尺寸与落点是被空间挤出来的</b>：殿的台基（含台阶）占到殿心前 7.3 米，
 * 传送门环在 14 米，中间只剩 6.7 米净空；再向两侧让开将作监坊墙的足迹，
 * 就只能取 8×5.6。</p>
 */
export const MAP_GROUND_W = 8;

/** 地面舆图的进深（米），见 {@link MAP_GROUND_W} */
export const MAP_GROUND_D = 5.6;

/** 名号最长显示字数：再长就截断，免得压到别的图形上 */
const LABEL_CHARS = 7;

/** 地面舆图的显示模式 */
export type MapGroundMode = 'live' | 'empty' | 'denied' | 'error';

/** 一条围栏的绘图数据（只取画图要用的字段） */
export interface MapFenceShape {
  /** 围栏名称 */
  name: string;
  /** 围栏类型：CIRCLE / POLYGON */
  fenceType: string;
  /** 是否启用：1 启用 / 0 停用 */
  enabled: number;
  /** 圆心经度（仅 CIRCLE） */
  centerLng?: number | null;
  /** 圆心纬度（仅 CIRCLE） */
  centerLat?: number | null;
  /** 半径（米，仅 CIRCLE） */
  radiusMeters?: number | null;
  /** 多边形顶点（仅 POLYGON，展示坐标系） */
  points?: { lng: number; lat: number }[] | null;
}

/** 地面舆图句柄 */
export interface MapGroundHandle {
  /** 地面网格（挂到场景即可） */
  mesh: THREE.Mesh;
  /**
   * 换一批围栏重画
   *
   * @param fences 围栏（空数组表示"一条都没有"）
   */
  setFences(fences: MapFenceShape[]): void;
  /**
   * 标记为"没有数据"的某一档，并重画一张带说明的空图
   *
   * @param mode 模式（live 不接受，走 setFences）
   * @param note 说明文字（写进探针，也画在图上）
   */
  setMode(mode: MapGroundMode, note?: string): void;
  /**
   * 状态探针（e2e 无法"看"，只能读状态）
   *
   * @returns 模式、围栏数与形状数、画布尺寸、亮度标准差
   */
  probe(): {
    mode: MapGroundMode;
    fences: number;
    circles: number;
    polygons: number;
    vertices: number;
    width: number;
    height: number;
    contrast: number;
    note: string;
    drawn: number;
  };
  /** 释放：销毁纹理与几何 */
  dispose(): void;
}

/**
 * 从围栏列表接口取一圈形状。
 *
 * <p><b>分页参数是 page/size</b>（{@code FenceQuery}），不是平台统一的
 * {@code current/size}：发错了服务端不报错，只是按默认值永远返回第 1 页。
 * 这里一次要 100 条（上限 200），够画出城里的围栏全貌。</p>
 *
 * @param options 取数参数
 * @returns 取数结果（失败时 fences 为空，error 写明原因）
 */
export async function fetchFenceShapes(options: {
  token: string | null;
  permission?: string;
  permissions?: () => string[] | null;
  listPath: string;
  size?: number;
  timeoutMillis?: number;
}): Promise<{ ok: boolean; status: number; fences: MapFenceShape[]; total: number; error: string }> {
  if (!options.token) {
    return { ok: false, status: 401, fences: [], total: 0, error: '未登录' };
  }
  const perms = options.permissions?.();
  if (options.permission && perms && perms.length > 0 && !perms.includes(options.permission)) {
    return { ok: false, status: 403, fences: [], total: 0, error: '无此殿凭据' };
  }
  const size = options.size ?? 100;
  const controller = new AbortController();
  const timer = window.setTimeout(() => controller.abort(), options.timeoutMillis ?? 6000);
  try {
    const response = await fetch(`${options.listPath}?page=1&size=${size}`, {
      method: 'GET',
      headers: { Authorization: `Bearer ${options.token}`, Accept: 'application/json' },
      credentials: 'omit',
      signal: controller.signal,
    });
    if (response.status === 401 || response.status === 403) {
      return { ok: false, status: response.status, fences: [], total: 0, error: '无此殿凭据' };
    }
    if (!response.ok) {
      return { ok: false, status: response.status, fences: [], total: 0, error: `HTTP ${response.status}` };
    }
    const payload: unknown = await response.json();
    const body = (payload ?? {}) as { code?: number; message?: string; data?: unknown };
    if (typeof body.code === 'number' && body.code !== 0) {
      return { ok: false, status: 200, fences: [], total: 0, error: String(body.message ?? '业务拒绝') };
    }
    const page = (body.data ?? {}) as { records?: unknown; total?: unknown };
    const records = Array.isArray(page.records) ? page.records : Array.isArray(body.data) ? body.data : [];
    const fences = (records as unknown[]).map(normalizeFence).filter((item): item is MapFenceShape => item !== null);
    const total = typeof page.total === 'number' ? page.total : fences.length;
    return { ok: true, status: 200, fences, total, error: '' };
  } catch (error) {
    const reason = controller.signal.aborted
      ? '问询超时'
      : (error instanceof Error && error.message ? error.message : '未接通').slice(0, 28);
    return { ok: false, status: 0, fences: [], total: 0, error: reason };
  } finally {
    window.clearTimeout(timer);
  }
}

/**
 * 把接口返回的一行整理成绘图数据；缺关键坐标的行直接丢掉。
 *
 * @param raw 接口返回的一行
 * @returns 绘图数据；不可画时返回 null
 */
function normalizeFence(raw: unknown): MapFenceShape | null {
  if (!raw || typeof raw !== 'object') {
    return null;
  }
  const row = raw as Record<string, unknown>;
  const points = Array.isArray(row.points)
    ? (row.points as Record<string, unknown>[])
        .map((point) => ({ lng: Number(point.lng), lat: Number(point.lat) }))
        .filter((point) => Number.isFinite(point.lng) && Number.isFinite(point.lat))
    : null;
  const shape: MapFenceShape = {
    name: typeof row.name === 'string' && row.name ? row.name : '未名围栏',
    fenceType: typeof row.fenceType === 'string' ? row.fenceType : 'POLYGON',
    enabled: Number(row.enabled ?? 0) === 1 ? 1 : 0,
    centerLng: row.centerLng === null || row.centerLng === undefined ? null : Number(row.centerLng),
    centerLat: row.centerLat === null || row.centerLat === undefined ? null : Number(row.centerLat),
    radiusMeters: row.radiusMeters === null || row.radiusMeters === undefined ? null : Number(row.radiusMeters),
    points,
  };
  if (shape.fenceType === 'CIRCLE') {
    return Number.isFinite(shape.centerLng) && Number.isFinite(shape.centerLat) ? shape : null;
  }
  return points && points.length >= 3 ? shape : null;
}

/**
 * 把经纬度压到画布的"米平面"上。
 *
 * <p>经度一度对应的米数随纬度收缩，直接按度画会把华北的方形围栏画成扁的；
 * 先统一折成米，再等比缩放到画布，形状才不会被画歪。</p>
 *
 * @param fences 围栏
 * @returns 投影函数与包围盒（米）
 */
function buildProjection(fences: MapFenceShape[]): {
  project: (lng: number, lat: number) => { x: number; y: number };
  scale: number;
  centerLat: number;
} {
  let minLat = Number.POSITIVE_INFINITY;
  let maxLat = Number.NEGATIVE_INFINITY;
  let minLng = Number.POSITIVE_INFINITY;
  let maxLng = Number.NEGATIVE_INFINITY;
  const eat = (lng: number, lat: number, radiusMeters = 0): void => {
    const dLat = radiusMeters / 111_320;
    const dLng = radiusMeters / (111_320 * Math.max(0.2, Math.cos((lat * Math.PI) / 180)));
    minLat = Math.min(minLat, lat - dLat);
    maxLat = Math.max(maxLat, lat + dLat);
    minLng = Math.min(minLng, lng - dLng);
    maxLng = Math.max(maxLng, lng + dLng);
  };
  for (const fence of fences) {
    if (fence.fenceType === 'CIRCLE') {
      eat(Number(fence.centerLng), Number(fence.centerLat), Number(fence.radiusMeters ?? 0));
    } else {
      for (const point of fence.points ?? []) {
        eat(point.lng, point.lat);
      }
    }
  }
  if (!Number.isFinite(minLat) || !Number.isFinite(minLng)) {
    minLat = 0;
    maxLat = 0.01;
    minLng = 0;
    maxLng = 0.01;
  }
  /* 单个点或一条直线（跨度 0）时给一个最小窗，否则缩放会除零 */
  const centerLat = (minLat + maxLat) / 2;
  const cosLat = Math.max(0.2, Math.cos((centerLat * Math.PI) / 180));
  const spanX = Math.max(1, (maxLng - minLng) * 111_320 * cosLat);
  const spanY = Math.max(1, (maxLat - minLat) * 111_320);
  const scale = Math.min((CANVAS_W - PAD * 2) / spanX, (CANVAS_H - PAD * 2) / spanY);
  const originX = (minLng * 111_320 * cosLat + maxLng * 111_320 * cosLat) / 2;
  const originY = ((minLat + maxLat) / 2) * 111_320;
  return {
    project: (lng: number, lat: number) => ({
      x: CANVAS_W / 2 + (lng * 111_320 * cosLat - originX) * scale,
      /* 画布 y 向下、纬度向北：取反，北在上 */
      y: CANVAS_H / 2 - (lat * 111_320 - originY) * scale,
    }),
    scale,
    centerLat,
  };
}

/**
 * 画一张舆图。
 *
 * @param ctx 画布上下文
 * @param fences 围栏（空数组画"未收"）
 * @param note 空图时的说明文字
 */
function paintMap(ctx: CanvasRenderingContext2D, fences: MapFenceShape[], note: string): void {
  /* ---- 纸底：底色 + 细密噪点，远看是绢，近看不是纯色块 ---- */
  ctx.fillStyle = '#e7dab9';
  ctx.fillRect(0, 0, CANVAS_W, CANVAS_H);
  ctx.fillStyle = 'rgba(120, 96, 56, 0.06)';
  for (let i = 0; i < 2400; i++) {
    const x = (i * 97) % CANVAS_W;
    const y = (i * 61) % CANVAS_H;
    ctx.fillRect(x, y, 2, 2);
  }

  /* ---- 边框：双线，像卷轴的边 ---- */
  ctx.strokeStyle = 'rgba(92, 70, 38, 0.55)';
  ctx.lineWidth = 3;
  ctx.strokeRect(16, 16, CANVAS_W - 32, CANVAS_H - 32);
  ctx.lineWidth = 1;
  ctx.strokeRect(26, 26, CANVAS_W - 52, CANVAS_H - 52);

  /* ---- 格网：十等分，横竖各一条中线加粗 ---- */
  ctx.strokeStyle = 'rgba(92, 70, 38, 0.16)';
  ctx.lineWidth = 1;
  ctx.beginPath();
  for (let i = 1; i < 10; i++) {
    const x = (CANVAS_W / 10) * i;
    const y = (CANVAS_H / 10) * i;
    ctx.moveTo(x, 30);
    ctx.lineTo(x, CANVAS_H - 30);
    ctx.moveTo(30, y);
    ctx.lineTo(CANVAS_W - 30, y);
  }
  ctx.stroke();

  const drawn = fences.slice(0, MAX_FENCES);
  if (drawn.length === 0) {
    ctx.fillStyle = 'rgba(92, 70, 38, 0.42)';
    ctx.font = '700 44px "Noto Serif SC", "Songti SC", serif';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(note || '舆图未收', CANVAS_W / 2, CANVAS_H / 2 - 10);
    ctx.font = '400 20px "Noto Serif SC", "Songti SC", serif';
    ctx.fillStyle = 'rgba(92, 70, 38, 0.34)';
    ctx.fillText('围栏台账里还没有可画的一笔', CANVAS_W / 2, CANVAS_H / 2 + 42);
    paintCartouche(ctx, 0);
    return;
  }

  const { project, scale } = buildProjection(drawn);

  /* ---- 围栏：先铺淡色，再描边，最后写名号（先画后写，字不会被盖住） ---- */
  for (const fence of drawn) {
    const live = fence.enabled === 1;
    ctx.beginPath();
    if (fence.fenceType === 'CIRCLE') {
      const center = project(Number(fence.centerLng), Number(fence.centerLat));
      const radius = Math.max(6, Number(fence.radiusMeters ?? 0) * scale);
      ctx.arc(center.x, center.y, radius, 0, Math.PI * 2);
    } else {
      const points = (fence.points ?? []).map((point) => project(point.lng, point.lat));
      points.forEach((point, index) => {
        if (index === 0) {
          ctx.moveTo(point.x, point.y);
        } else {
          ctx.lineTo(point.x, point.y);
        }
      });
      ctx.closePath();
    }
    ctx.fillStyle = live ? 'rgba(58, 104, 88, 0.14)' : 'rgba(120, 112, 96, 0.10)';
    ctx.fill();
    ctx.setLineDash(live ? [] : [7, 5]);
    ctx.strokeStyle = live ? 'rgba(38, 84, 70, 0.85)' : 'rgba(120, 112, 96, 0.8)';
    ctx.lineWidth = live ? 2.4 : 1.8;
    ctx.stroke();
    ctx.setLineDash([]);
  }

  for (const fence of drawn) {
    const anchor =
      fence.fenceType === 'CIRCLE'
        ? project(Number(fence.centerLng), Number(fence.centerLat))
        : project(fence.points?.[0]?.lng ?? 0, fence.points?.[0]?.lat ?? 0);
    const label = fence.name.length > LABEL_CHARS ? `${fence.name.slice(0, LABEL_CHARS)}…` : fence.name;
    ctx.font = '600 19px "Noto Serif SC", "Songti SC", serif';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    /* 先描一圈纸色再写字：围栏互相重叠时，字压在别的边上也读得出来 */
    ctx.lineWidth = 4;
    ctx.strokeStyle = 'rgba(231, 218, 185, 0.9)';
    ctx.strokeText(label, anchor.x, anchor.y);
    ctx.fillStyle = fence.enabled === 1 ? 'rgba(30, 62, 52, 0.95)' : 'rgba(96, 90, 76, 0.9)';
    ctx.fillText(label, anchor.x, anchor.y);
  }

  paintCompass(ctx);
  paintCartouche(ctx, drawn.length);
}

/**
 * 画右下角的指北针。
 *
 * @param ctx 画布上下文
 */
function paintCompass(ctx: CanvasRenderingContext2D): void {
  const cx = CANVAS_W - 84;
  const cy = CANVAS_H - 86;
  ctx.strokeStyle = 'rgba(92, 70, 38, 0.5)';
  ctx.lineWidth = 1.5;
  ctx.beginPath();
  ctx.arc(cx, cy, 30, 0, Math.PI * 2);
  ctx.stroke();
  ctx.beginPath();
  ctx.moveTo(cx, cy - 26);
  ctx.lineTo(cx - 8, cy + 6);
  ctx.lineTo(cx, cy - 2);
  ctx.lineTo(cx + 8, cy + 6);
  ctx.closePath();
  ctx.fillStyle = 'rgba(140, 60, 44, 0.8)';
  ctx.fill();
  ctx.font = '600 16px "Noto Serif SC", "Songti SC", serif';
  ctx.textAlign = 'center';
  ctx.fillStyle = 'rgba(92, 70, 38, 0.75)';
  ctx.fillText('北', cx, cy + 24);
}

/**
 * 画左上角的题记。
 *
 * @param ctx 画布上下文
 * @param count 已画的围栏数
 */
function paintCartouche(ctx: CanvasRenderingContext2D, count: number): void {
  ctx.font = '700 26px "Noto Serif SC", "Songti SC", serif';
  ctx.textAlign = 'left';
  ctx.textBaseline = 'alphabetic';
  ctx.fillStyle = 'rgba(70, 54, 30, 0.86)';
  ctx.fillText('舆图司 · 疆界图', 40, 66);
  ctx.font = '400 15px "Noto Serif SC", "Songti SC", serif';
  ctx.fillStyle = 'rgba(92, 70, 38, 0.6)';
  ctx.fillText(count > 0 ? `共 ${count} 围 · 按真实经纬度缩绘` : '尚无围栏 · 空图', 40, 92);
}

/**
 * 建一块地面舆图。
 *
 * @param width 地面宽度（米）
 * @param depth 地面进深（米）
 * @returns 舆图句柄
 */
export function createMapGround(width: number, depth: number): MapGroundHandle {
  const canvas = document.createElement('canvas');
  canvas.width = CANVAS_W;
  canvas.height = CANVAS_H;
  const ctx = canvas.getContext('2d');
  if (!ctx) {
    throw new Error('2D canvas 不可用：地面舆图无法绘制');
  }
  const texture = new THREE.CanvasTexture(canvas);
  texture.colorSpace = THREE.SRGBColorSpace;
  texture.anisotropy = 4;
  const geometry = new THREE.PlaneGeometry(width, depth);
  const material = new THREE.MeshBasicMaterial({ map: texture, transparent: false, toneMapped: false });
  const mesh = new THREE.Mesh(geometry, material);
  /* 平铺在地面上。**必须高过环路**：环路的 RingGeometry 铺在 y=0.06（中轴御道 0.07），
     半径 65~87 米——正好盖住外城诸司门前的这段地。第一版按 y=0.02 铺，
     舆图整块被埋进环路底下：探针里的围栏数、对比度全是对的，画面上一片铺装——
     这类"数据全绿、画面没有"的坑只能靠人眼/截图核。 */
  mesh.rotation.x = -Math.PI / 2;
  mesh.position.y = 0.12;
  mesh.name = 'yutu-map-ground';

  let mode: MapGroundMode = 'empty';
  let note = '舆图未收';
  let drawn = 0;
  let circles = 0;
  let polygons = 0;
  let vertices = 0;
  let disposed = false;

  /**
   * 采样整张画的亮度标准差。
   *
   * <p>围栏数只说明"数据里有东西"，不说明"画上去了"：纹理没绑上、上下文取错，
   * 探针里的数也都是对的。标准差把"一张纯色纸"与"纸上画了圈"分开——
   * e2e 用它做正向兜底（阈值 4：纸底噪点约 2~3，画了围栏明显更高）。</p>
   *
   * @returns 亮度标准差（0~255 量纲）
   */
  function contrast(): number {
    const step = 20;
    let sum = 0;
    let sumSq = 0;
    let count = 0;
    for (let y = step; y < CANVAS_H - step; y += step) {
      for (let x = step; x < CANVAS_W - step; x += step) {
        const data = ctx?.getImageData(x, y, 1, 1).data;
        if (!data) {
          continue;
        }
        const lum = 0.299 * data[0] + 0.587 * data[1] + 0.114 * data[2];
        sum += lum;
        sumSq += lum * lum;
        count += 1;
      }
    }
    if (count === 0) {
      return 0;
    }
    const mean = sum / count;
    return Number(Math.sqrt(Math.max(0, sumSq / count - mean * mean)).toFixed(2));
  }

  /* 首帧先画一张"未收"，等数据到了再覆盖：地面不能是一块白板 */
  paintMap(ctx, [], note);

  return {
    mesh,
    setFences(fences: MapFenceShape[]): void {
      if (disposed) {
        return;
      }
      circles = fences.filter((fence) => fence.fenceType === 'CIRCLE').length;
      polygons = fences.length - circles;
      vertices = fences.reduce((total, fence) => total + (fence.points?.length ?? 0), 0);
      drawn = Math.min(fences.length, MAX_FENCES);
      mode = fences.length > 0 ? 'live' : 'empty';
      note = fences.length > 0 ? '' : '舆图未收';
      paintMap(ctx, fences, note);
      texture.needsUpdate = true;
    },
    setMode(next: MapGroundMode, reason?: string): void {
      if (disposed) {
        return;
      }
      mode = next === 'live' ? 'empty' : next;
      note = reason ?? (next === 'denied' ? '无此殿凭据' : '舆图未接通');
      circles = 0;
      polygons = 0;
      vertices = 0;
      drawn = 0;
      paintMap(ctx, [], note);
      texture.needsUpdate = true;
    },
    probe() {
      return {
        mode,
        fences: circles + polygons,
        circles,
        polygons,
        vertices,
        width: CANVAS_W,
        height: CANVAS_H,
        contrast: contrast(),
        note,
        drawn,
      };
    },
    dispose(): void {
      disposed = true;
      texture.dispose();
      material.dispose();
      geometry.dispose();
    },
  };
}
