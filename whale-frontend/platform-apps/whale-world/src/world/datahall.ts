/**
 * 数据厅 —— 殿内的 3D 数据展厅（S3 数据厅框架）。
 *
 * <p>与屏风（CS S3D，承载<b>可交互</b>界面）互补：这里放的是<b>只读数据</b>——
 * KPI 大字卡、折线/柱/环形图、告警条与状态灯。按总纲 §1.1 的分工，只读数据走
 * WebGL 层：Canvas 画成纹理贴 3D 平面，随透视自然变形，同屏零 DOM 开销。</p>
 *
 * <p><b>数据源不另造</b>：厅里所有数字都来自 {@link metrics} 已经采集好的
 * {@link GlyphCard}（8 路只读概览接口），本模块不发任何后端请求。图表的时间序列
 * 由每次 setData 累积的真实采样派生——刷新一次多一个点，折线随之变化，不是画死的。</p>
 *
 * <p><b>空间隔离</b>：厅是一个自包含 Group，驻留在城池上空 y=400（相机 far=4000，
 * 但天花板挡住天空）。平时 group.visible=false，连同厅内灯一起被 three 的
 * projectObject 跳过，对城池的 drawcall 与光照零影响；进厅时才显形。这样不必改
 * collision/layout，也不碰数枢殿那座实心 box 的几何。</p>
 *
 * <p><b>进出过场</b>：#bww-fade 遮罩 → 相机 snap 到厅内固定机位 → 遮罩退去。
 * 城池相机由调用方（city.ts）在激活期冻结，退出时 snap 回进入前的位姿。</p>
 */

import * as THREE from 'three';
import type { GlyphMetric, GlyphState, GlyphTone } from './metrics';

/* ---------------------------------------------------------------- 常量 */

/** 厅驻留高度：远离城池，避免与城郭/天象互相干扰 */
const HALL_Y = 400;
/** 厅内尺寸（本地坐标，地板 y=0） */
const ROOM_W = 22;
const ROOM_D = 18;
const ROOM_H = 8;
/** 厅内相机机位与视点 */
const CAM_POS = new THREE.Vector3(0, 4.2, 7);
const CAM_LOOK = new THREE.Vector3(0, 4.2, -9);

const FONT = '"Songti SC", "STSong", "SimSun", serif';
const SANS = '"Segoe UI", "Helvetica Neue", Arial, sans-serif';

const TONE_COLOR: Record<GlyphTone, string> = {
  good: '#8fe0b0',
  warn: '#e8c069',
  bad: '#e8867a',
  flat: '#d8e6ee',
};
const STATE_COLOR: Record<GlyphState, string> = {
  idle: '#66707c',
  loading: '#5f7d8c',
  live: '#7fd8ff',
  stale: '#c9a659',
  denied: '#6b7078',
  unreachable: '#b0584c',
};
const STATE_LABEL: Record<GlyphState, string> = {
  idle: '待问',
  loading: '问询中',
  live: '在案',
  stale: '陈旧',
  denied: '无凭据',
  unreachable: '未接通',
};

/* ---------------------------------------------------------------- 类型 */

/** 一个厅要展示的全部数据（由 city.ts 从 metrics.cards 映射而来） */
export interface HallData {
  nodeKey: string;
  name: string;
  sub: string;
  state: GlyphState;
  tone: GlyphTone;
  metrics: GlyphMetric[];
  note: string;
  updatedAt: number;
}

export interface DataHallOptions {
  scene: THREE.Scene;
  /** 与主场景共用的相机（进厅时 snap 到厅内机位，出厅时复位） */
  camera: THREE.PerspectiveCamera;
  /** 点击检测用的画布（厅内左右殿门） */
  domElement: HTMLCanvasElement;
  /** 退出时相机要回到的跟随位姿（复用 city 的避让算法结果，避免两处不一致） */
  getFollowPose: () => { position: THREE.Vector3; target: THREE.Vector3 };
  /** 进出厅回调：nodeKey 为当前厅；null 表示已出厅回到城中 */
  onEngagedChange?: (nodeKey: string | null) => void;
}

export interface HallBrief {
  index: number;
  nodeKey: string;
  name: string;
  sub: string;
}

export interface DataHallHandle {
  group: THREE.Group;
  /** 进厅（index 省略时开第一个）。相机 snap 到厅内机位 */
  open: (index?: number) => void;
  /** 出厅，相机回到进入前位姿 */
  close: () => void;
  /** 上一厅 / 下一厅（循环） */
  prev: () => void;
  next: () => void;
  isActive: () => boolean;
  current: () => HallBrief | null;
  /** 喂入全部厅数据；只重绘内容变化的卡（指纹跳过） */
  setData: (halls: HallData[]) => void;
  /** 键盘：← → 切厅、Esc 出厅。返回是否消费了该键 */
  handleKey: (code: string) => boolean;
  /** 导出某张卡的位图（PNG dataURL），仅供 e2e 排查"画没画出来" */
  faceOf: (kind: string, index?: number) => string | null;
  dispose: () => void;
}

/** 一张纹理卡：canvas + texture + mesh + 上次内容的指纹 */
interface CardSlot {
  id: string;
  canvas: HTMLCanvasElement;
  texture: THREE.CanvasTexture;
  mesh: THREE.Mesh<THREE.PlaneGeometry, THREE.MeshBasicMaterial>;
  painted: string;
}

/* ---------------------------------------------------------------- 工具 */

function roundRect(
  ctx: CanvasRenderingContext2D,
  x: number,
  y: number,
  w: number,
  h: number,
  r: number,
): void {
  ctx.beginPath();
  ctx.moveTo(x + r, y);
  ctx.lineTo(x + w - r, y);
  ctx.quadraticCurveTo(x + w, y, x + w, y + r);
  ctx.lineTo(x + w, y + h - r);
  ctx.quadraticCurveTo(x + w, y + h, x + w - r, y + h);
  ctx.lineTo(x + r, y + h);
  ctx.quadraticCurveTo(x, y + h, x, y + h - r);
  ctx.lineTo(x, y + r);
  ctx.quadraticCurveTo(x, y + r, x + r, y);
  ctx.closePath();
}

/** 牌底：深墨底 + 金描边，数据卡与厅内暖光同调又压得住 */
function panelBase(ctx: CanvasRenderingContext2D, w: number, h: number, accent: string): void {
  ctx.clearRect(0, 0, w, h);
  roundRect(ctx, 8, 8, w - 16, h - 16, 14);
  const bg = ctx.createLinearGradient(0, 0, 0, h);
  bg.addColorStop(0, 'rgba(18, 30, 33, 0.97)');
  bg.addColorStop(1, 'rgba(9, 17, 20, 0.97)');
  ctx.fillStyle = bg;
  ctx.fill();
  ctx.strokeStyle = accent;
  ctx.lineWidth = 2.4;
  ctx.stroke();
}

/**
 * 把格式化数值解析成可用于图表的数字。
 * 兼容 "12,480" / "1.8万" / "¥1.2亿" / "99.9%" / "—"，解析失败回 0。
 */
function parseValue(value: string): number {
  if (!value) return 0;
  let s = value.replace(/[,，\s¥￥]/g, '').trim();
  let scale = 1;
  if (s.endsWith('%')) {
    s = s.slice(0, -1);
  } else if (s.endsWith('亿')) {
    scale = 1e8;
    s = s.slice(0, -1);
  } else if (s.endsWith('万')) {
    scale = 1e4;
    s = s.slice(0, -1);
  }
  const n = Number(s);
  return Number.isFinite(n) ? n * scale : 0;
}

/** 数值轴刻度：取一个好看的上界 */
function niceMax(max: number): number {
  if (max <= 0) return 1;
  const exp = Math.floor(Math.log10(max));
  const base = Math.pow(10, exp);
  const n = max / base;
  const step = n <= 1 ? 1 : n <= 2 ? 2 : n <= 5 ? 5 : 10;
  return step * base;
}

/* ---------------------------------------------------------------- 绘制 */

const TEX_KPI_W = 512;
const TEX_KPI_H = 256;
const TEX_CHART_W = 576;
const TEX_CHART_H = 320;
const TEX_PLAQUE_W = 768;
const TEX_PLAQUE_H = 192;
const TEX_ALERT_W = 240;
const TEX_ALERT_H = 512;

function paintKpi(slot: CardSlot, hall: HallData, index: number): void {
  const ctx = slot.canvas.getContext('2d');
  if (!ctx) return;
  const metric = hall.metrics[index];
  const tone: GlyphTone = metric?.tone ?? hall.tone;
  const accent = TONE_COLOR[tone];
  panelBase(ctx, TEX_KPI_W, TEX_KPI_H, accent + '88');

  // 殿名缩写角标
  ctx.textAlign = 'left';
  ctx.textBaseline = 'alphabetic';
  ctx.font = `600 20px ${SANS}`;
  ctx.fillStyle = 'rgba(216, 180, 90, 0.72)';
  ctx.fillText((hall.sub || 'HALL').split('·')[0].trim().toUpperCase().slice(0, 14), 30, 50);

  // 状态圆点
  ctx.beginPath();
  ctx.arc(TEX_KPI_W - 34, 44, 7, 0, Math.PI * 2);
  ctx.fillStyle = STATE_COLOR[hall.state];
  ctx.fill();

  if (!metric) {
    // 降级 / 无指标：居中说明，空卡比"暂无数据"更让人困惑
    ctx.textAlign = 'center';
    ctx.font = `600 42px ${FONT}`;
    ctx.fillStyle = STATE_COLOR[hall.state];
    ctx.fillText(STATE_LABEL[hall.state], TEX_KPI_W / 2, TEX_KPI_H / 2 + 6);
    ctx.font = `19px ${FONT}`;
    ctx.fillStyle = 'rgba(236, 223, 196, 0.5)';
    ctx.fillText(hall.note || '等待问询', TEX_KPI_W / 2, TEX_KPI_H / 2 + 44);
    return;
  }

  // 大数值
  ctx.textAlign = 'center';
  ctx.font = `600 78px ${FONT}`;
  ctx.fillStyle = accent;
  let size = 78;
  const label = metric.value;
  while (ctx.measureText(label).width > TEX_KPI_W - 60 && size > 34) {
    size -= 4;
    ctx.font = `600 ${size}px ${FONT}`;
  }
  ctx.fillText(label, TEX_KPI_W / 2, 150);

  // 指标名
  ctx.font = `22px ${FONT}`;
  ctx.fillStyle = 'rgba(236, 223, 196, 0.78)';
  ctx.fillText(metric.label, TEX_KPI_W / 2, 202);
}

function paintLine(slot: CardSlot, values: number[], tone: GlyphTone, label: string): void {
  const ctx = slot.canvas.getContext('2d');
  if (!ctx) return;
  const W = TEX_CHART_W;
  const H = TEX_CHART_H;
  panelBase(ctx, W, H, 'rgba(216, 180, 90, 0.42)');

  ctx.textAlign = 'left';
  ctx.font = `600 24px ${FONT}`;
  ctx.fillStyle = '#f0d79b';
  ctx.fillText('趋 势', 30, 48);
  ctx.textAlign = 'right';
  ctx.font = `16px ${SANS}`;
  ctx.fillStyle = 'rgba(236, 223, 196, 0.5)';
  ctx.fillText(`${values.length} 次采样`, W - 28, 46);

  const x0 = 46;
  const y0 = 74;
  const pw = W - 74;
  const ph = H - 130;

  // 网格
  ctx.strokeStyle = 'rgba(216, 180, 90, 0.12)';
  ctx.lineWidth = 1;
  for (let i = 0; i <= 3; i++) {
    const y = y0 + (ph * i) / 3;
    ctx.beginPath();
    ctx.moveTo(x0, y);
    ctx.lineTo(x0 + pw, y);
    ctx.stroke();
  }

  if (values.length === 0) {
    ctx.textAlign = 'center';
    ctx.font = `22px ${FONT}`;
    ctx.fillStyle = 'rgba(236, 223, 196, 0.45)';
    ctx.fillText('等待采样', W / 2, y0 + ph / 2);
    return;
  }

  const max = niceMax(Math.max(...values, 1));
  const pts = values.map((v, i) => {
    const x = values.length === 1 ? x0 + pw / 2 : x0 + (pw * i) / (values.length - 1);
    const y = y0 + ph - (v / max) * ph;
    return [x, y] as const;
  });

  // 面积填充
  ctx.beginPath();
  ctx.moveTo(pts[0][0], y0 + ph);
  for (const [x, y] of pts) ctx.lineTo(x, y);
  ctx.lineTo(pts[pts.length - 1][0], y0 + ph);
  ctx.closePath();
  const fill = ctx.createLinearGradient(0, y0, 0, y0 + ph);
  fill.addColorStop(0, TONE_COLOR[tone] + '66');
  fill.addColorStop(1, TONE_COLOR[tone] + '05');
  ctx.fillStyle = fill;
  ctx.fill();

  // 折线 + 节点
  ctx.beginPath();
  pts.forEach(([x, y], i) => (i === 0 ? ctx.moveTo(x, y) : ctx.lineTo(x, y)));
  ctx.strokeStyle = TONE_COLOR[tone];
  ctx.lineWidth = 3;
  ctx.lineJoin = 'round';
  ctx.stroke();
  for (const [x, y] of pts) {
    ctx.beginPath();
    ctx.arc(x, y, 4, 0, Math.PI * 2);
    ctx.fillStyle = TONE_COLOR[tone];
    ctx.fill();
  }

  ctx.textAlign = 'left';
  ctx.font = `18px ${FONT}`;
  ctx.fillStyle = 'rgba(236, 223, 196, 0.66)';
  ctx.fillText(label.slice(0, 12), 30, H - 24);
  ctx.textAlign = 'right';
  ctx.fillStyle = 'rgba(216, 180, 90, 0.66)';
  ctx.fillText(`峰值 ${max}`, W - 28, H - 24);
}

function paintBar(slot: CardSlot, metrics: GlyphMetric[]): void {
  const ctx = slot.canvas.getContext('2d');
  if (!ctx) return;
  const W = TEX_CHART_W;
  const H = TEX_CHART_H;
  panelBase(ctx, W, H, 'rgba(216, 180, 90, 0.42)');

  ctx.textAlign = 'left';
  ctx.font = `600 24px ${FONT}`;
  ctx.fillStyle = '#f0d79b';
  ctx.fillText('对 比', 30, 48);

  const valid = metrics.slice(0, 5);
  if (valid.length === 0) {
    ctx.textAlign = 'center';
    ctx.font = `22px ${FONT}`;
    ctx.fillStyle = 'rgba(236, 223, 196, 0.45)';
    ctx.fillText('无可比数据', W / 2, H / 2);
    return;
  }

  const values = valid.map((m) => parseValue(m.value));
  const max = niceMax(Math.max(...values, 1));
  const x0 = 46;
  const y0 = 76;
  const pw = W - 74;
  const ph = H - 128;
  const slotW = pw / valid.length;
  const barW = slotW * 0.56;

  ctx.strokeStyle = 'rgba(216, 180, 90, 0.12)';
  for (let i = 0; i <= 3; i++) {
    const y = y0 + (ph * i) / 3;
    ctx.beginPath();
    ctx.moveTo(x0, y);
    ctx.lineTo(x0 + pw, y);
    ctx.stroke();
  }

  valid.forEach((m, i) => {
    const v = values[i];
    const bh = Math.max(2, (v / max) * ph);
    const x = x0 + slotW * i + (slotW - barW) / 2;
    const y = y0 + ph - bh;
    const grad = ctx.createLinearGradient(0, y, 0, y0 + ph);
    grad.addColorStop(0, TONE_COLOR[m.tone ?? 'flat']);
    grad.addColorStop(1, TONE_COLOR[m.tone ?? 'flat'] + '33');
    ctx.fillStyle = grad;
    roundRect(ctx, x, y, barW, bh, 4);
    ctx.fill();

    ctx.textAlign = 'center';
    ctx.font = `17px ${SANS}`;
    ctx.fillStyle = 'rgba(236, 223, 196, 0.78)';
    ctx.fillText(m.value.slice(0, 7), x + barW / 2, y - 8);
    ctx.font = `16px ${FONT}`;
    ctx.fillStyle = 'rgba(236, 223, 196, 0.55)';
    ctx.fillText(m.label.slice(0, 5), x + barW / 2, H - 22);
  });
}

function paintDonut(slot: CardSlot, metrics: GlyphMetric[]): void {
  const ctx = slot.canvas.getContext('2d');
  if (!ctx) return;
  const W = TEX_CHART_W;
  const H = TEX_CHART_H;
  panelBase(ctx, W, H, 'rgba(216, 180, 90, 0.42)');

  ctx.textAlign = 'left';
  ctx.font = `600 24px ${FONT}`;
  ctx.fillStyle = '#f0d79b';
  ctx.fillText('构 成', 30, 48);

  const valid = metrics.filter((m) => parseValue(m.value) > 0).slice(0, 6);
  const cx = 150;
  const cy = 178;
  const r = 88;
  const ir = 50;

  if (valid.length === 0) {
    ctx.textAlign = 'center';
    ctx.font = `22px ${FONT}`;
    ctx.fillStyle = 'rgba(236, 223, 196, 0.45)';
    ctx.fillText('无可占比数据', W / 2, H / 2);
    return;
  }

  const values = valid.map((m) => parseValue(m.value));
  const total = values.reduce((a, b) => a + b, 0);
  let angle = -Math.PI / 2;
  valid.forEach((m, i) => {
    const next = angle + (values[i] / total) * Math.PI * 2;
    ctx.beginPath();
    ctx.arc(cx, cy, r, angle, next);
    ctx.arc(cx, cy, ir, next, angle, true);
    ctx.closePath();
    ctx.fillStyle =
      TONE_COLOR[m.tone ?? (['flat', 'good', 'warn', 'bad', 'flat', 'good'][i % 6] as GlyphTone)];
    ctx.fill();
    angle = next;
  });
  ctx.beginPath();
  ctx.arc(cx, cy, ir, 0, Math.PI * 2);
  ctx.fillStyle = 'rgba(12, 20, 23, 0.96)';
  ctx.fill();
  ctx.textAlign = 'center';
  ctx.font = `600 30px ${FONT}`;
  ctx.fillStyle = '#f0d79b';
  ctx.fillText(String(valid.length), cx, cy + 4);
  ctx.font = `14px ${FONT}`;
  ctx.fillStyle = 'rgba(236, 223, 196, 0.5)';
  ctx.fillText('项', cx, cy + 28);

  // 图例
  ctx.textAlign = 'left';
  valid.forEach((m, i) => {
    const y = 100 + i * 34;
    ctx.fillStyle =
      TONE_COLOR[m.tone ?? (['flat', 'good', 'warn', 'bad', 'flat', 'good'][i % 6] as GlyphTone)];
    ctx.fillRect(272, y - 12, 14, 14);
    ctx.font = `18px ${FONT}`;
    ctx.fillStyle = 'rgba(236, 223, 196, 0.8)';
    ctx.fillText(m.label.slice(0, 8), 296, y);
  });
}

function paintPlaque(slot: CardSlot, name: string, sub: string): void {
  const ctx = slot.canvas.getContext('2d');
  if (!ctx) return;
  const W = TEX_PLAQUE_W;
  const H = TEX_PLAQUE_H;
  ctx.clearRect(0, 0, W, H);
  roundRect(ctx, 10, 10, W - 20, H - 20, 16);
  const bg = ctx.createLinearGradient(0, 0, 0, H);
  bg.addColorStop(0, '#3a2418');
  bg.addColorStop(0.5, '#2b1a12');
  bg.addColorStop(1, '#1e120c');
  ctx.fillStyle = bg;
  ctx.fill();
  ctx.strokeStyle = 'rgba(226, 188, 92, 0.85)';
  ctx.lineWidth = 4;
  ctx.stroke();
  ctx.strokeStyle = 'rgba(226, 188, 92, 0.3)';
  ctx.lineWidth = 1.5;
  roundRect(ctx, 22, 22, W - 44, H - 44, 10);
  ctx.stroke();

  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  ctx.font = `600 76px ${FONT}`;
  ctx.fillStyle = '#e8c86a';
  ctx.fillText(name.slice(0, 6), W / 2, H / 2 - 8);
  ctx.font = `22px ${SANS}`;
  ctx.fillStyle = 'rgba(232, 200, 106, 0.62)';
  ctx.fillText((sub || 'DATA HALL').toUpperCase().slice(0, 24), W / 2, H / 2 + 52);
  ctx.textBaseline = 'alphabetic';
}

function paintAlert(slot: CardSlot, hall: HallData): void {
  const ctx = slot.canvas.getContext('2d');
  if (!ctx) return;
  const W = TEX_ALERT_W;
  const H = TEX_ALERT_H;
  panelBase(ctx, W, H, 'rgba(216, 180, 90, 0.4)');

  const alarming = hall.metrics.filter((m) => m.tone === 'bad' || m.tone === 'warn');
  const stateTone: Record<GlyphState, GlyphTone> = {
    idle: 'flat',
    loading: 'flat',
    live: 'good',
    stale: 'warn',
    denied: 'warn',
    unreachable: 'bad',
  };
  const tone =
    hall.state === 'live' && alarming.length > 0
      ? (alarming[0].tone ?? 'warn')
      : stateTone[hall.state];
  const accent = TONE_COLOR[tone];

  ctx.textAlign = 'center';
  ctx.font = `600 30px ${FONT}`;
  ctx.fillStyle = '#f0d79b';
  ctx.fillText('报 警', W / 2, 56);

  // 状态灯（大圆）
  ctx.beginPath();
  ctx.arc(W / 2, 150, 40, 0, Math.PI * 2);
  ctx.fillStyle = accent + '33';
  ctx.fill();
  ctx.beginPath();
  ctx.arc(W / 2, 150, 24, 0, Math.PI * 2);
  ctx.fillStyle = accent;
  ctx.fill();

  ctx.font = `600 26px ${FONT}`;
  ctx.fillStyle = accent;
  ctx.fillText(STATE_LABEL[hall.state], W / 2, 224);
  ctx.font = `17px ${FONT}`;
  ctx.fillStyle = 'rgba(236, 223, 196, 0.5)';
  ctx.fillText(hall.note.slice(0, 12), W / 2, 258);

  // 告警明细
  ctx.textAlign = 'left';
  ctx.font = `17px ${FONT}`;
  const items = alarming.length > 0 ? alarming : hall.metrics.slice(0, 3);
  items.slice(0, 4).forEach((m, i) => {
    const y = 320 + i * 42;
    ctx.fillStyle = TONE_COLOR[m.tone ?? 'flat'];
    ctx.fillRect(24, y - 13, 10, 10);
    ctx.fillStyle = 'rgba(236, 223, 196, 0.82)';
    ctx.fillText(m.label.slice(0, 7), 44, y - 4);
    ctx.textAlign = 'right';
    ctx.fillText(m.value.slice(0, 8), W - 22, y - 4);
    ctx.textAlign = 'left';
  });
}

/* ---------------------------------------------------------------- 主工厂 */

export function createDataHall(options: DataHallOptions): DataHallHandle {
  const { scene, camera, domElement, getFollowPose, onEngagedChange } = options;

  const group = new THREE.Group();
  group.name = 'bww-datahall';
  group.position.set(0, HALL_Y, 0);
  group.visible = false;

  const slots = new Map<string, CardSlot>();
  const history = new Map<string, number[]>();
  const MAX_HISTORY = 24;

  /* --- 厅堂几何：地板 / 四壁 / 天花板 / 匾额框 --- */
  const wood = new THREE.MeshStandardMaterial({ color: 0x4a2f1f, roughness: 0.88 });
  const wallMat = new THREE.MeshStandardMaterial({ color: 0x6b4a34, roughness: 0.94 });
  const stoneMat = new THREE.MeshStandardMaterial({ color: 0x9a8f7a, roughness: 0.92 });
  const floorMat = new THREE.MeshStandardMaterial({ color: 0x3b2a20, roughness: 0.95 });
  const goldMat = new THREE.MeshStandardMaterial({
    color: 0xb08d3e,
    roughness: 0.55,
    metalness: 0.5,
  });

  const box = (w: number, h: number, d: number, mat: THREE.Material): THREE.Mesh => {
    const m = new THREE.Mesh(new THREE.BoxGeometry(w, h, d), mat);
    m.castShadow = false;
    m.receiveShadow = false;
    return m;
  };

  const floor = box(ROOM_W, 0.4, ROOM_D, floorMat);
  floor.position.y = -0.2;
  // 基座：地板下再压一层深石，四周留出厚度——俯视时不至于像悬在空中的一块薄板
  const podium = box(ROOM_W + 1.6, 1.6, ROOM_D + 1.6, stoneMat);
  podium.position.y = -1.0;
  group.add(podium);
  const backWall = box(ROOM_W, ROOM_H, 0.5, wallMat);
  backWall.position.set(0, ROOM_H / 2, -ROOM_D / 2);
  const leftWall = box(0.5, ROOM_H, ROOM_D, wallMat);
  leftWall.position.set(-ROOM_W / 2, ROOM_H / 2, 0);
  const rightWall = box(0.5, ROOM_H, ROOM_D, wallMat);
  rightWall.position.set(ROOM_W / 2, ROOM_H / 2, 0);
  const ceiling = box(ROOM_W, 0.4, ROOM_D, wood);
  ceiling.position.y = ROOM_H + 0.2;
  // 藻井：几圈递减方框压出层次
  for (let i = 0; i < 3; i++) {
    const s = 12 - i * 3.2;
    const rim = box(s, 0.16, s * (ROOM_D / ROOM_W), goldMat);
    rim.position.y = ROOM_H - 0.05 - i * 0.06;
    group.add(rim);
  }
  group.add(floor, backWall, leftWall, rightWall, ceiling);

  /* --- 匾额 --- */
  const makeSlot = (
    id: string,
    texW: number,
    texH: number,
    worldW: number,
    worldH: number,
    x: number,
    y: number,
    z: number,
    ry = 0,
  ): CardSlot => {
    const canvas = document.createElement('canvas');
    canvas.width = texW;
    canvas.height = texH;
    const texture = new THREE.CanvasTexture(canvas);
    texture.colorSpace = THREE.SRGBColorSpace;
    texture.anisotropy = 4;
    const material = new THREE.MeshBasicMaterial({
      map: texture,
      transparent: true,
      toneMapped: false,
    });
    const mesh = new THREE.Mesh(new THREE.PlaneGeometry(worldW, worldH), material);
    mesh.position.set(x, y, z);
    mesh.rotation.y = ry;
    group.add(mesh);
    const slot: CardSlot = { id, canvas, texture, mesh, painted: '' };
    slots.set(id, slot);
    return slot;
  };

  const plaqueSlot = makeSlot(
    'plaque',
    TEX_PLAQUE_W,
    TEX_PLAQUE_H,
    8.4,
    2.1,
    0,
    7.0,
    -ROOM_D / 2 + 0.3,
  );
  // 匾额木框
  const plaqueFrame = box(9.0, 2.6, 0.24, wood);
  plaqueFrame.position.set(0, 7.0, -ROOM_D / 2 + 0.16);
  group.add(plaqueFrame);

  /* --- 4 张 KPI 卡（后墙上部） --- */
  const KPI_X = [-7.5, -2.5, 2.5, 7.5];
  const kpiSlots = KPI_X.map((x, i) =>
    makeSlot(`kpi${i}`, TEX_KPI_W, TEX_KPI_H, 4.4, 2.2, x, 4.9, -ROOM_D / 2 + 0.32),
  );
  // 卡框
  for (const x of KPI_X) {
    const frame = box(4.6, 2.4, 0.14, goldMat);
    frame.position.set(x, 4.9, -ROOM_D / 2 + 0.24);
    group.add(frame);
  }

  /* --- 3 张图表卡（后墙下部） --- */
  const CHART_X = [-6, 0, 6];
  const lineSlot = makeSlot(
    'chart-line',
    TEX_CHART_W,
    TEX_CHART_H,
    5.2,
    2.9,
    CHART_X[0],
    1.9,
    -ROOM_D / 2 + 0.32,
  );
  const barSlot = makeSlot(
    'chart-bar',
    TEX_CHART_W,
    TEX_CHART_H,
    5.2,
    2.9,
    CHART_X[1],
    1.9,
    -ROOM_D / 2 + 0.32,
  );
  const donutSlot = makeSlot(
    'chart-donut',
    TEX_CHART_W,
    TEX_CHART_H,
    5.2,
    2.9,
    CHART_X[2],
    1.9,
    -ROOM_D / 2 + 0.32,
  );
  for (const x of CHART_X) {
    const frame = box(5.4, 3.1, 0.14, goldMat);
    frame.position.set(x, 1.9, -ROOM_D / 2 + 0.24);
    group.add(frame);
  }

  /* --- 告警条（左墙） --- */
  const alertSlot = makeSlot(
    'alert',
    TEX_ALERT_W,
    TEX_ALERT_H,
    2.2,
    4.7,
    -ROOM_W / 2 + 0.32,
    4.2,
    0,
    Math.PI / 2,
  );

  /* --- 状态灯（四角檐灯） --- */
  const lampMat = new THREE.MeshStandardMaterial({
    color: 0x8fe0b0,
    emissive: 0x8fe0b0,
    emissiveIntensity: 1.1,
    roughness: 0.4,
  });
  const lamps: THREE.Mesh[] = [];
  for (const x of [-9.4, 9.4]) {
    for (const z of [-7.4, 7.4]) {
      const lamp = new THREE.Mesh(new THREE.SphereGeometry(0.3, 12, 10), lampMat);
      lamp.position.set(x, 7.2, z);
      group.add(lamp);
      lamps.push(lamp);
      const holder = box(0.5, 0.3, 0.5, goldMat);
      holder.position.set(x, 7.55, z);
      group.add(holder);
    }
  }

  /* --- 厅内照明：环境 + 两盏暖光，数据卡本身是 Basic 材质不受影响 --- */
  const ambient = new THREE.AmbientLight(0xffe8c4, 0.55);
  group.add(ambient);
  for (const x of [-5, 5]) {
    const light = new THREE.PointLight(0xffd9a0, 45, 26, 1.6);
    light.position.set(x, 6.6, 2);
    group.add(light);
  }

  /* --- 左右殿门导航（前部两侧，可点击） --- */
  const makeDoor = (x: number, ry: number, text: string): { mesh: THREE.Mesh; slot: CardSlot } => {
    const frame = box(2.6, 4.2, 0.3, wood);
    frame.position.set(x, 2.1, ROOM_D / 2 - 2.2);
    frame.rotation.y = ry;
    group.add(frame);
    const slot = makeSlot('door-' + text, 256, 384, 2.2, 3.8, x, 2.1, ROOM_D / 2 - 2.0, ry);
    const ctx = slot.canvas.getContext('2d');
    if (ctx) {
      panelBase(ctx, 256, 384, 'rgba(216, 180, 90, 0.6)');
      ctx.textAlign = 'center';
      ctx.font = `600 96px ${SANS}`;
      ctx.fillStyle = '#e8c86a';
      ctx.fillText(text === 'prev' ? '‹' : '›', 128, 180);
      ctx.font = `26px ${FONT}`;
      ctx.fillText(text === 'prev' ? '上一厅' : '下一厅', 128, 260);
      slot.texture.needsUpdate = true;
    }
    return { mesh: frame, slot };
  };
  const doorPrev = makeDoor(-ROOM_W / 2 + 1.7, Math.PI / 2, 'prev');
  const doorNext = makeDoor(ROOM_W / 2 - 1.7, -Math.PI / 2, 'next');

  scene.add(group);

  /* ---------------------------------------------------------------- 状态 */
  let halls: HallData[] = [];
  let index = 0;
  let active = false;
  const enterPos = new THREE.Vector3();
  const enterTarget = new THREE.Vector3();
  const fadeEl = document.getElementById('bww-fade') as HTMLElement | null;
  let transitionTimer: number | null = null;

  /** 重绘一张卡（指纹相同跳过） */
  const repaint = (slot: CardSlot, fingerprint: string, draw: () => void): void => {
    if (slot.painted === fingerprint) return;
    draw();
    slot.texture.needsUpdate = true;
    slot.painted = fingerprint;
  };

  const renderHall = (): void => {
    const hall = halls[index];
    const name = hall?.name ?? '数 枢 殿';
    const sub = hall?.sub ?? 'OVERVIEW';
    repaint(plaqueSlot, `${name}|${sub}`, () => paintPlaque(plaqueSlot, name, sub));

    const metrics = hall?.metrics ?? [];
    const state = hall?.state ?? 'loading';
    const tone = hall?.tone ?? 'flat';
    const note = hall?.note ?? '';
    const fp = (i: number) =>
      `${state}|${note}|${metrics[i]?.label}|${metrics[i]?.value}|${metrics[i]?.tone}`;

    kpiSlots.forEach((slot, i) =>
      repaint(slot, fp(i), () => paintKpi(slot, hall ?? emptyHall(), i)),
    );

    const series = hall ? (history.get(hall.nodeKey) ?? []) : [];
    const seriesFp = `${hall?.nodeKey}|${series.join(',')}|${tone}|${metrics[0]?.label}`;
    repaint(lineSlot, seriesFp, () =>
      paintLine(lineSlot, series, tone, metrics[0]?.label ?? '主指标'),
    );
    repaint(barSlot, fp(0) + '|bar|' + metrics.map((m) => m.value).join(','), () =>
      paintBar(barSlot, metrics),
    );
    repaint(donutSlot, fp(0) + '|donut|' + metrics.map((m) => m.value).join(','), () =>
      paintDonut(donutSlot, metrics),
    );
    repaint(alertSlot, fp(0) + '|alert|' + metrics.map((m) => m.tone).join(','), () =>
      paintAlert(alertSlot, hall ?? emptyHall()),
    );

    // 檐灯颜色跟整体语气
    const color = TONE_COLOR[tone];
    lampMat.color.set(color);
    lampMat.emissive.set(color);
  };

  function emptyHall(): HallData {
    return {
      nodeKey: '',
      name: '',
      sub: '',
      state: 'loading',
      tone: 'flat',
      metrics: [],
      note: '',
      updatedAt: 0,
    };
  }

  const setCameraToHall = (): void => {
    // 厅在世界坐标 y=HALL_Y：本地机位要加上偏移
    camera.position.copy(CAM_POS).add(new THREE.Vector3(0, HALL_Y, 0));
    camera.lookAt(CAM_LOOK.clone().add(new THREE.Vector3(0, HALL_Y, 0)));
  };

  /** 遮罩淡入 → 做事 → 淡出 */
  const withFade = (mid: () => void): void => {
    if (transitionTimer !== null) {
      window.clearTimeout(transitionTimer);
    }
    if (fadeEl) {
      fadeEl.style.transition = 'opacity .22s';
      fadeEl.style.opacity = '1';
      fadeEl.style.pointerEvents = 'auto';
    }
    transitionTimer = window.setTimeout(() => {
      mid();
      if (fadeEl) {
        fadeEl.style.opacity = '0';
        fadeEl.style.pointerEvents = 'none';
      }
      transitionTimer = null;
    }, 230);
  };

  const handle: DataHallHandle = {
    group,
    open(openIndex = 0) {
      if (halls.length === 0) return;
      const pose = getFollowPose();
      enterPos.copy(pose.position);
      enterTarget.copy(pose.target);
      withFade(() => {
        index = Math.max(0, Math.min(openIndex, halls.length - 1));
        active = true;
        group.visible = true;
        setCameraToHall();
        renderHall();
        onEngagedChange?.(halls[index]?.nodeKey ?? null);
      });
    },
    close() {
      if (!active) return;
      withFade(() => {
        active = false;
        group.visible = false;
        camera.position.copy(enterPos);
        camera.lookAt(enterTarget);
        onEngagedChange?.(null);
      });
    },
    prev() {
      if (!active || halls.length === 0) return;
      index = (index - 1 + halls.length) % halls.length;
      renderHall();
      onEngagedChange?.(halls[index]?.nodeKey ?? null);
    },
    next() {
      if (!active || halls.length === 0) return;
      index = (index + 1) % halls.length;
      renderHall();
      onEngagedChange?.(halls[index]?.nodeKey ?? null);
    },
    isActive() {
      return active;
    },
    current() {
      const hall = halls[index];
      if (!hall) return null;
      return { index, nodeKey: hall.nodeKey, name: hall.name, sub: hall.sub };
    },
    setData(next) {
      halls = next;
      // 累积时间序列：每个厅取第一个可解析指标的值
      for (const hall of halls) {
        const value = parseValue(hall.metrics[0]?.value ?? '');
        if (!Number.isFinite(value)) continue;
        const arr = history.get(hall.nodeKey) ?? [];
        arr.push(value);
        if (arr.length > MAX_HISTORY) arr.shift();
        history.set(hall.nodeKey, arr);
      }
      if (active) renderHall();
    },
    handleKey(code) {
      if (!active) return false;
      if (code === 'Escape') {
        handle.close();
        return true;
      }
      if (code === 'ArrowLeft' || code === 'KeyA') {
        handle.prev();
        return true;
      }
      if (code === 'ArrowRight' || code === 'KeyD') {
        handle.next();
        return true;
      }
      return false;
    },
    faceOf(kind, indexArg = 0) {
      let slot: CardSlot | undefined;
      if (kind === 'plaque') slot = plaqueSlot;
      else if (kind === 'alert') slot = alertSlot;
      else if (kind === 'line') slot = lineSlot;
      else if (kind === 'bar') slot = barSlot;
      else if (kind === 'donut') slot = donutSlot;
      else if (kind === 'kpi') slot = kpiSlots[indexArg];
      return slot ? slot.canvas.toDataURL('image/png') : null;
    },
    dispose() {
      if (transitionTimer !== null) {
        window.clearTimeout(transitionTimer);
        transitionTimer = null;
      }
      if (fadeEl) {
        fadeEl.style.opacity = '0';
        fadeEl.style.pointerEvents = 'none';
      }
      group.traverse((obj) => {
        const o = obj as THREE.Mesh;
        if (o.geometry) o.geometry.dispose();
      });
      for (const slot of slots.values()) {
        slot.texture.dispose();
        (slot.mesh.material as THREE.Material).dispose();
      }
      wood.dispose();
      wallMat.dispose();
      stoneMat.dispose();
      floorMat.dispose();
      goldMat.dispose();
      lampMat.dispose();
      scene.remove(group);
      slots.clear();
      history.clear();
    },
  };

  // 先画一次空厅（未进厅时不可见，避免首次进厅闪空白）
  renderHall();

  /* ---------------------------------------------------------------- 点击检测 */
  const raycaster = new THREE.Raycaster();
  const onPointerDown = (ev: PointerEvent): void => {
    if (!active) return;
    if (ev.button !== 0) return;
    const rect = domElement.getBoundingClientRect();
    const ndc = new THREE.Vector2(
      ((ev.clientX - rect.left) / rect.width) * 2 - 1,
      -((ev.clientY - rect.top) / rect.height) * 2 + 1,
    );
    raycaster.setFromCamera(ndc, camera);
    const hits = raycaster.intersectObjects([doorPrev.mesh, doorNext.mesh], false);
    if (hits.length === 0) return;
    if (hits[0].object === doorPrev.mesh) handle.prev();
    else handle.next();
  };
  domElement.addEventListener('pointerdown', onPointerDown);

  // 包一层 dispose 以解绑事件
  const baseDispose = handle.dispose;
  handle.dispose = () => {
    domElement.removeEventListener('pointerdown', onPointerDown);
    baseDispose();
  };

  return handle;
}
