/* ==========================================================================
 * 城图（小地图）—— Canvas 2D 俯视图
 *
 * 底图（三重城墙 / 环路 / 御道 / 宫殿）只绘制一次并缓存，逐帧仅重绘动态层，
 * 开销可以忽略（约 0.05ms）。北向固定向上，玩家用箭头表示朝向。
 *
 * P2 的「真实地图」是另一条链路：由 whale-map 提供地理编码/围栏/路径，
 * 与这张场景图并存（一张是城，一张是疆域），两者共用同一套坐标换算。
 * ========================================================================== */

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
  dispose: () => void;
}

/** 城图显示范围：世界 ±100m */
const HALF = 100;
const OUTER = 92;
const INNER = 48;
const IMPERIAL = 22;
const RING_R = 68;

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
    bctx.fillStyle = '#08121a';
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

    // 数枢殿
    bctx.fillStyle = 'rgba(255, 209, 102, 0.85)';
    const hw = 11 * scale;
    const hh = 8 * scale;
    bctx.fillRect(s / 2 - hw, toY(-2, s) - hh, hw * 2, hh * 2);

    // 指北
    bctx.fillStyle = 'rgba(216, 180, 90, 0.75)';
    bctx.font = '10px "Songti SC", "SimSun", serif';
    bctx.textAlign = 'center';
    bctx.fillText('北', s / 2, 11);

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
    dispose() {
      // 无外部资源，交给 GC
    },
  };
}

/** 颜色数值 → 十六进制（避免为了一个色值把 three 引进小地图模块） */
function hex6(color: number): string {
  return color.toString(16).padStart(6, '0');
}
