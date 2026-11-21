/* ==========================================================================
 * 城图（小地图）—— Canvas 2D 俯视图
 *
 * 底图（三重城墙 / 环路 / 御道 / 宫殿 / 城门）只绘制一次并缓存，逐帧仅重绘动态层，
 * 开销可以忽略（约 0.05ms）。北向固定向上，玩家用箭头表示朝向。
 *
 * P2 的「真实地图」是另一条链路：由 whale-map 提供地理编码/围栏/路径，
 * 与这张场景图并存（一张是城，一张是疆域），两者共用同一套坐标换算。
 *
 * **尺度一律从 `CITY_GEOMETRY` 派生**（S5/T5.10 修）：这里曾写死 v1 的
 * 92/48/22/68 四个数字，S1 把城扩到 110/58/30/76 之后没人回来改，
 * 于是城图上的外城墙整整跑出画布 10 米、内城墙偏里 10 米——玩家看到的
 * 是"我明明站在城墙边，箭头却在图外"。城墙位置是布局数据的一部分，
 * 写两份就是等着漂移（与"前后端路径漂移"是同一类静默失效）。
 * ========================================================================== */

import { CITY_GEOMETRY } from './layout';
import { LANDMARK_GATES, WALL_OPENINGS } from './landmarks';

export interface MinimapState {
  player: { x: number; z: number; yaw: number };
  portals: Array<{ x: number; z: number; color: number }>;
  palaces: Array<{ x: number; z: number }>;
  npcs: Array<{ x: number; z: number }>;
  peers: Array<{ x: number; z: number }>;
}

export interface MinimapHandle {
  render: (state: MinimapState) => void;
  resize: () => void;
  /** 派生尺度快照（e2e 断言"城图尺度 = 布局数据"，不必去读画布像素） */
  geometry: () => MinimapGeometry;
  dispose: () => void;
}

/** 城图派生尺度：全部来自 {@code city-layout.json}，与场景同源 */
export interface MinimapGeometry {
  /** 画布覆盖的世界半径（米） */
  half: number;
  outer: number;
  inner: number;
  royal: number;
  ring: number;
  /** 画出来的城墙缺口数（四外门 + 两内门 + 四皇城门） */
  openings: number;
  /** 有门楼标记的四座外城门 */
  gates: Array<{ nodeKey: string; name: string; x: number; z: number }>;
}

/** 城图显示范围：外城再留一圈余量（写死 100 时外城墙 110 会跑到画布外） */
const HALF = CITY_GEOMETRY.outerWall + 10;
const OUTER = CITY_GEOMETRY.outerWall;
const INNER = CITY_GEOMETRY.innerWall;
const IMPERIAL = CITY_GEOMETRY.royalWall;
const RING_R = CITY_GEOMETRY.ringRoadRadius;

/** 城图底色（留缺口时要用它"擦掉"墙线，两处必须同一个值） */
const MAP_BG = '#08121a';

export function createMinimap(canvas: HTMLCanvasElement): MinimapHandle {
  const ctx = canvas.getContext('2d');
  const base = document.createElement('canvas');
  const bctx = base.getContext('2d');

  let dpr = Math.min(window.devicePixelRatio || 1, 2);
  let size = 180;

  const toX = (x: number, s: number) => s / 2 + (x / (HALF * 2)) * s;
  const toY = (z: number, s: number) => s / 2 + (z / (HALF * 2)) * s;

  /** 绘制静态底图 */
  const drawBase = () => {
    if (!bctx) {
      return;
    }
    const s = size;
    const scale = s / (HALF * 2);
    bctx.clearRect(0, 0, s, s);

    // 底色 + 城外地貌暗示
    bctx.fillStyle = MAP_BG;
    bctx.fillRect(0, 0, s, s);

    // 环路
    bctx.strokeStyle = 'rgba(206, 196, 172, 0.10)';
    bctx.lineWidth = 26 * scale;
    bctx.beginPath();
    bctx.arc(s / 2, s / 2, RING_R * scale, 0, Math.PI * 2);
    bctx.stroke();

    // 御道（十字中轴）
    bctx.strokeStyle = 'rgba(206, 196, 172, 0.16)';
    bctx.lineWidth = 13 * scale;
    bctx.beginPath();
    bctx.moveTo(s / 2, toY(-OUTER, s));
    bctx.lineTo(s / 2, toY(OUTER, s));
    bctx.moveTo(toX(-OUTER, s), s / 2);
    bctx.lineTo(toX(OUTER, s), s / 2);
    bctx.stroke();

    // 三重城墙
    const wall = (half: number, alpha: number) => {
      bctx.strokeStyle = `rgba(216, 180, 90, ${alpha})`;
      bctx.lineWidth = 1.6;
      const x0 = toX(-half, s);
      const x1 = toX(half, s);
      const y0 = toY(-half, s);
      const y1 = toY(half, s);
      bctx.strokeRect(x0, y0, x1 - x0, y1 - y0);
    };
    wall(OUTER, 0.55);
    wall(INNER, 0.4);
    wall(IMPERIAL, 0.72);

    /* 门洞（S5/T5.10）：城墙不是一圈实线。把墙线在门的位置擦成底色，
       站在门洞里看城图才不会以为自己撞墙了。缺口宽度与碰撞分段同源
       （都是 gateHalfWidth），改一处两处一起变。 */
    for (const gap of WALL_OPENINGS) {
      const len = gap.half * 2 * scale;
      /* 墙线只有 1.6px，擦 3.2px 才盖得干净（含抗锯齿边） */
      const thick = 3.2;
      const w = gap.along === 'x' ? len : thick;
      const h = gap.along === 'x' ? thick : len;
      bctx.fillStyle = MAP_BG;
      bctx.fillRect(toX(gap.x, s) - w / 2, toY(gap.z, s) - h / 2, w, h);
    }
    /* 四座外城门楼：在缺口中央点一块金石色。门楼是地标，俯视图上要认得出，
       否则"城有四门"只有走进去才知道。 */
    for (const gate of LANDMARK_GATES) {
      bctx.fillStyle = 'rgba(216, 180, 90, 0.9)';
      bctx.fillRect(toX(gate.x, s) - 1.6, toY(gate.z, s) - 1.6, 3.2, 3.2);
    }

    // 数枢殿
    bctx.fillStyle = 'rgba(255, 209, 102, 0.85)';
    const hw = 11 * scale;
    const hh = 8 * scale;
    bctx.fillRect(s / 2 - hw, toY(-2, s) - hh, hw * 2, hh * 2);

    // 指北（挪到左上角）：城图正中上方就是北门门洞与门楼标记，
    // 原来居中写在 y=11 时，170px 的图上"北"字正好压在北门上，两样都读不清。
    // 三角自己画：10px 字号下 "↑" 会渲染成一根发丝，等宽三角才看得出来。
    bctx.fillStyle = 'rgba(216, 180, 90, 0.75)';
    bctx.beginPath();
    bctx.moveTo(9, 4);
    bctx.lineTo(5.6, 11);
    bctx.lineTo(12.4, 11);
    bctx.closePath();
    bctx.fill();
    bctx.font = '10px "Songti SC", "SimSun", serif';
    bctx.textAlign = 'left';
    bctx.fillText('北', 15, 12);

    // 边框
    bctx.strokeStyle = 'rgba(216, 180, 90, 0.28)';
    bctx.lineWidth = 1;
    bctx.strokeRect(0.5, 0.5, s - 1, s - 1);
  };

  const resize = () => {
    dpr = Math.min(window.devicePixelRatio || 1, 2);
    size = Math.max(120, Math.round(canvas.clientWidth || 180));
    canvas.width = Math.round(size * dpr);
    canvas.height = Math.round(size * dpr);
    base.width = Math.round(size * dpr);
    base.height = Math.round(size * dpr);
    if (ctx) {
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    }
    if (bctx) {
      bctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    }
    drawBase();
  };
  resize();

  const dot = (x: number, z: number, r: number, color: string) => {
    if (!ctx) {
      return;
    }
    ctx.beginPath();
    ctx.arc(toX(x, size), toY(z, size), r, 0, Math.PI * 2);
    ctx.fillStyle = color;
    ctx.fill();
  };

  return {
    render(state) {
      if (!ctx || !bctx) {
        return;
      }
      const s = size;
      ctx.clearRect(0, 0, s, s);
      ctx.drawImage(base, 0, 0, s, s);

      // 八方宫殿
      for (const p of state.palaces) {
        ctx.fillStyle = 'rgba(111, 227, 255, 0.55)';
        ctx.fillRect(toX(p.x, s) - 2, toY(p.z, s) - 2, 4, 4);
      }

      // 传送门
      for (const p of state.portals) {
        const hex = `#${hex6(p.color)}`;
        ctx.shadowColor = hex;
        ctx.shadowBlur = 6;
        dot(p.x, p.z, 2.6, hex);
        ctx.shadowBlur = 0;
      }

      // NPC
      for (const n of state.npcs) {
        dot(n.x, n.z, 1.5, 'rgba(232, 220, 192, 0.8)');
      }

      // 其他玩家
      for (const p of state.peers) {
        dot(p.x, p.z, 2, 'rgba(127, 216, 255, 0.95)');
      }

      // 玩家（箭头）
      const px = toX(state.player.x, s);
      const py = toY(state.player.z, s);
      ctx.save();
      ctx.translate(px, py);
      ctx.rotate(Math.PI - state.player.yaw);
      ctx.beginPath();
      ctx.moveTo(0, -6.5);
      ctx.lineTo(-4.2, 4.5);
      ctx.lineTo(0, 2.4);
      ctx.lineTo(4.2, 4.5);
      ctx.closePath();
      ctx.fillStyle = '#ff8a4c';
      ctx.fill();
      ctx.strokeStyle = 'rgba(10, 18, 24, 0.8)';
      ctx.lineWidth = 1;
      ctx.stroke();
      ctx.restore();
    },
    resize,
    geometry: () => ({
      half: HALF,
      outer: OUTER,
      inner: INNER,
      royal: IMPERIAL,
      ring: RING_R,
      openings: WALL_OPENINGS.length,
      gates: LANDMARK_GATES.map((gate) => ({
        nodeKey: gate.nodeKey,
        name: gate.name,
        x: gate.x,
        z: gate.z,
      })),
    }),
    dispose() {
      // 无外部资源，交给 GC
    },
  };
}

/** 颜色数值 → 十六进制（避免为了一个色值把 three 引进小地图模块） */
function hex6(color: number): string {
  return color.toString(16).padStart(6, '0');
}
