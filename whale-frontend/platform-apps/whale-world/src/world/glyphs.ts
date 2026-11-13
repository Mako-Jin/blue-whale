/**
 * 殿前牌坊 —— 把八座殿所辖业务系统的实况挂在殿门口。
 *
 * <p>做成场景里的实体木牌（不是叠在屏幕上的 HUD）：牌子立在山门前、面朝来路，
 * 玩家走近才读得清。这样"哪个系统出事了"是逛城时自然看见的，
 * 而不是先点开某个面板去找。牌面用 {@link THREE.CanvasTexture} 绘制，
 * 数字变了只重绘那张 512×296 的位图，不动几何体。</p>
 *
 * <p>牌面样式刻意克制：两三条指标 + 一个状态色，不画图表。
 * 站远处看不清细节时，仍然能靠颜色判断这座殿是安是危——那正是牌坊的作用。</p>
 */

import * as THREE from 'three';
import type { GlyphCard, GlyphState, GlyphTone } from './metrics';
import type { PalaceSpec } from './layout';

/** 牌面世界尺寸（米） */
const PANEL_W = 5.2;
const PANEL_H = 2.9;
/** 位图分辨率：约 100 px/米，走近两米内仍清晰 */
const TEX_W = 512;
const TEX_H = 296;

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

const TONE_COLOR: Record<GlyphTone, string> = {
  good: '#8fe0b0',
  warn: '#e8c069',
  bad: '#e8867a',
  flat: '#efe3c6',
};

/** 牌面上的一个可绘制单元 */
interface GlyphSlot {
  palace: PalaceSpec;
  canvas: HTMLCanvasElement;
  texture: THREE.CanvasTexture;
  /** 上次绘制内容的指纹；相同则跳过重绘（每帧都可能调用） */
  painted: string;
}

export interface GlyphsHandle {
  group: THREE.Group;
  /** 按 nodeKey 更新牌面 */
  update: (cards: Map<string, GlyphCard>) => void;
  /** 导出某块牌面的位图（PNG dataURL）；仅供排查"牌立着但读不清"这类问题 */
  faceOf: (nodeKey: string) => string | null;
  dispose: () => void;
}

const FONT = '"Songti SC", "STSong", "SimSun", serif';

/** 圆角矩形路径 */
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
  ctx.quadraticCurveTo(x, y, x + r, y);
  ctx.closePath();
}

/** 把一块牌画到 canvas 上（整张重绘：牌面小，增量绘制反而更容易出错） */
function paint(canvas: HTMLCanvasElement, palace: PalaceSpec, card: GlyphCard | undefined): void {
  const ctx = canvas.getContext('2d');
  if (!ctx) {
    return;
  }
  const state: GlyphState = card?.state ?? 'loading';
  const accent = STATE_COLOR[state];
  const metrics = card?.metrics ?? [];

  ctx.clearRect(0, 0, TEX_W, TEX_H);

  // 牌底：深色漆木 + 内描边，远处也不糊成一团黑
  roundRect(ctx, 6, 6, TEX_W - 12, TEX_H - 12, 10);
  const bg = ctx.createLinearGradient(0, 0, 0, TEX_H);
  bg.addColorStop(0, 'rgba(18, 26, 32, 0.97)');
  bg.addColorStop(1, 'rgba(10, 16, 21, 0.97)');
  ctx.fillStyle = bg;
  ctx.fill();
  ctx.strokeStyle = 'rgba(216, 180, 90, 0.42)';
  ctx.lineWidth = 2;
  ctx.stroke();

  // 左侧状态竖条：远看只有它最显眼
  ctx.fillStyle = accent;
  roundRect(ctx, 18, 22, 5, TEX_H - 44, 2.5);
  ctx.fill();

  // 殿名
  ctx.fillStyle = '#f0d79b';
  ctx.font = `600 30px ${FONT}`;
  ctx.textAlign = 'left';
  ctx.textBaseline = 'alphabetic';
  ctx.fillText(palace.name, 38, 56);

  // 右上角：系统缩写（ERP/MES…）。用无衬线字体：宋体渲染拉丁字母时分不清 O 和 0
  if (palace.sub) {
    const tag = palace.sub.split('·')[0].trim().toUpperCase();
    ctx.font = '600 13px "Segoe UI", "Helvetica Neue", Arial, sans-serif';
    ctx.fillStyle = 'rgba(216, 180, 90, 0.7)';
    ctx.textAlign = 'right';
    ctx.fillText(tag, TEX_W - 26, 52);
  }

  // 分隔线
  ctx.strokeStyle = 'rgba(216, 180, 90, 0.2)';
  ctx.lineWidth = 1;
  ctx.beginPath();
  ctx.moveTo(38, 70);
  ctx.lineTo(TEX_W - 26, 70);
  ctx.stroke();

  if (metrics.length === 0) {
    // 无数字时居中一句说明：空牌子比写着"暂无数据"更让人困惑
    ctx.textAlign = 'center';
    ctx.fillStyle = STATE_COLOR[state];
    ctx.font = `17px ${FONT}`;
    ctx.fillText(STATE_LABEL[state], TEX_W / 2, TEX_H / 2 + 6);
    ctx.fillStyle = 'rgba(236, 223, 196, 0.42)';
    ctx.font = `12px ${FONT}`;
    ctx.fillText(card?.note ?? '等待问询', TEX_W / 2, TEX_H / 2 + 32);
    return;
  }

  // 指标行：左标签右数值，深色底上数值用语气色
  const top = 104;
  const rowH = metrics.length > 2 ? 50 : 58;
  metrics.forEach((metric, index) => {
    const y = top + index * rowH;
    ctx.textAlign = 'left';
    ctx.fillStyle = 'rgba(236, 223, 196, 0.58)';
    ctx.font = `15px ${FONT}`;
    ctx.fillText(metric.label, 40, y);

    ctx.textAlign = 'right';
    ctx.fillStyle = TONE_COLOR[metric.tone ?? 'flat'];
    ctx.font = `600 23px ${FONT}`;
    ctx.fillText(metric.value, TEX_W - 30, y + 1);
  });

  // 底部：状态说明（刷新时刻 / 失败原因），字号最小
  ctx.textAlign = 'left';
  ctx.fillStyle = 'rgba(236, 223, 196, 0.36)';
  ctx.font = `11.5px ${FONT}`;
  ctx.fillText(`${STATE_LABEL[state]} · ${card?.note ?? ''}`, 40, TEX_H - 16);
}

/**
 * 创建八块殿前牌坊。
 *
 * @param palaces   宫殿列表（牌位由殿的位置与朝向算出）
 * @param groundAt  取地形高度，牌子要站在地上而不是悬空
 */
export function createGlyphs(
  palaces: PalaceSpec[],
  groundAt: (x: number, z: number) => number,
): GlyphsHandle {
  const group = new THREE.Group();
  group.name = 'bww-glyphs';

  const slots: GlyphSlot[] = [];

  const postMat = new THREE.MeshStandardMaterial({ color: 0x4a2f1f, roughness: 0.92 });
  const beamMat = new THREE.MeshStandardMaterial({ color: 0x5d3a25, roughness: 0.88 });

  for (const palace of palaces) {
    /*
     * 牌位与朝向（这两行是本文件最容易踩错的地方）：
     *  - 偏移按**台基**算：createPalace 的台基是 (w+9)*scale，只按殿身尺寸算会把牌子埋进台基里。
     *  - 沿**切向**（殿的宽度方向）偏移，而不是沿正面方向。原因是殿与内城之间只有 5–9 米空地，
     *    正对立的牌会挤在窄巷里：第三人称相机退不开（自动避让后贴到角色后脑），
     *    玩家也几乎贴着内城墙才能读到牌。挪到环路上、牌面朝切向来路，走一趟就能依次读完八块。
     */
    const standoff = ((palace.w + 9) * palace.scale) / 2 + 1.6;
    const tangentX = Math.cos(palace.yaw);
    const tangentZ = -Math.sin(palace.yaw);
    const px = palace.x + tangentX * standoff;
    const pz = palace.z + tangentZ * standoff;
    const faceYaw = palace.yaw + Math.PI / 2;
    const baseY = groundAt(px, pz);

    const stand = new THREE.Group();
    stand.position.set(px, baseY, pz);
    stand.rotation.y = faceYaw;

    // 两根立柱
    const postGeo = new THREE.BoxGeometry(0.22, 3.35, 0.22);
    for (const sign of [-1, 1]) {
      const post = new THREE.Mesh(postGeo, postMat);
      post.position.set(sign * (PANEL_W / 2 - 0.16), 1.675, 0);
      post.castShadow = true;
      stand.add(post);
    }

    // 顶部横梁
    const beam = new THREE.Mesh(new THREE.BoxGeometry(PANEL_W + 0.5, 0.24, 0.34), beamMat);
    beam.position.y = 3.3;
    beam.castShadow = true;
    stand.add(beam);

    // 牌面：Basic 材质，不受光照影响（夜里也要看得清，它是告示不是装饰）
    const canvas = document.createElement('canvas');
    canvas.width = TEX_W;
    canvas.height = TEX_H;
    const texture = new THREE.CanvasTexture(canvas);
    texture.colorSpace = THREE.SRGBColorSpace;
    texture.anisotropy = 4;

    const panel = new THREE.Mesh(
      new THREE.PlaneGeometry(PANEL_W, PANEL_H),
      new THREE.MeshBasicMaterial({ map: texture, transparent: true, toneMapped: false }),
    );
    panel.position.set(0, 1.95, 0.12);
    stand.add(panel);

    group.add(stand);
    slots.push({ palace, canvas, texture, painted: '' });
  }

  return {
    group,
    update(cards) {
      for (const slot of slots) {
        const card = cards.get(slot.palace.nodeKey);
        // 指纹只看影响牌面的字段：状态 + 数值 + 说明
        const fingerprint = `${card?.state ?? 'loading'}|${card?.note ?? ''}|${(card?.metrics ?? [])
          .map((m) => `${m.label}:${m.value}:${m.tone ?? 'flat'}`)
          .join(',')}`;
        if (fingerprint === slot.painted) {
          continue;
        }
        slot.painted = fingerprint;
        paint(slot.canvas, slot.palace, card);
        slot.texture.needsUpdate = true;
      }
    },
    faceOf(nodeKey) {
      const slot = slots.find((s) => s.palace.nodeKey === nodeKey);
      return slot ? slot.canvas.toDataURL('image/png') : null;
    },
    dispose() {
      for (const slot of slots) {
        slot.texture.dispose();
      }
      group.traverse((object) => {
        if (object instanceof THREE.Mesh) {
          object.geometry.dispose();
        }
      });
      postMat.dispose();
      beamMat.dispose();
    },
  };
}
