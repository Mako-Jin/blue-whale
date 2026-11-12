/* ==========================================================================
 * 角色档案：持久化 + 照片取色
 *
 * 存储：localStorage（浏览器本地，无后端调用 → 不影响 2D 用户与权限体系）。
 * P2 计划：登录后改为 `PUT /world/v1/profile` 存到 whale-scene / whale-file，
 * 并下发到 whale-im 的 world 帧里，让其他玩家看到你的真实形象。
 *
 * 照片取色：把照片按「发际 / 面部 / 衣身」三段采样，映射到古风配色板。
 * 这是「照片录入角色」当前阶段的诚实实现——还原的是你的气质与配色，
 * 不是三维重建；三维重建（Tripo / 混元 3D）是后续可插拔的后端能力。
 * ========================================================================== */

import { DEFAULT_AVATAR, HAIR_COLORS, ROBE_COLORS, SKIN_TONES } from './avatar';
import type { Accessory, AvatarParams, HairStyle } from './avatar';

const STORAGE_KEY = 'bww.avatar.v1';

export interface ProfileSnapshot {
  params: AvatarParams;
  /** 是否读取到已保存的档案（false 表示首次入城，需引导录入） */
  saved: boolean;
}

function clampNum(v: unknown, min: number, max: number, fallback: number): number {
  const n = typeof v === 'number' && Number.isFinite(v) ? v : fallback;
  return Math.min(max, Math.max(min, n));
}

function pickString(v: unknown, fallback: string): string {
  return typeof v === 'string' && /^#[0-9a-f]{6}$/i.test(v) ? v : fallback;
}

/** 读取本地档案；任何字段非法都回落到默认值，避免脏数据把场景画崩 */
export function loadProfile(): ProfileSnapshot {
  const fallback: AvatarParams = { ...DEFAULT_AVATAR };
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return { params: fallback, saved: false };
    }
    const d = JSON.parse(raw) as Partial<AvatarParams>;
    const params: AvatarParams = {
      name: typeof d.name === 'string' && d.name.trim() ? d.name.trim().slice(0, 8) : fallback.name,
      height: clampNum(d.height, 0.9, 1.15, fallback.height),
      build: clampNum(d.build, 0.85, 1.18, fallback.build),
      faceWidth: clampNum(d.faceWidth, 0.9, 1.12, fallback.faceWidth),
      skin: pickString(d.skin, fallback.skin),
      robe: pickString(d.robe, fallback.robe),
      hair: pickString(d.hair, fallback.hair),
      hairStyle: (d.hairStyle === 0 || d.hairStyle === 1 || d.hairStyle === 2
        ? d.hairStyle
        : fallback.hairStyle) as HairStyle,
      beard: Boolean(d.beard),
      accessory: (d.accessory === 0 || d.accessory === 1 || d.accessory === 2
        ? d.accessory
        : fallback.accessory) as Accessory,
    };
    return { params, saved: true };
  } catch {
    return { params: fallback, saved: false };
  }
}

export function saveProfile(params: AvatarParams): void {
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(params));
  } catch {
    // 隐私模式下 localStorage 不可写：静默降级为本次会话内生效
  }
}

export interface PhotoColors {
  thumb: string;
  skin: string;
  robe: string;
  hair: string;
}

type RGB = [number, number, number];

function toRgb(hex: string): RGB {
  const n = parseInt(hex.slice(1), 16);
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
}

/** 在给定色板里找最接近的颜色（加权：色相差异权重更高，避免暖冷混淆） */
function nearestInPalette(palette: readonly string[], rgb: RGB): string {
  let best = palette[0];
  let bestD = Number.POSITIVE_INFINITY;
  for (const hex of palette) {
    const [r, g, b] = toRgb(hex);
    const d = (r - rgb[0]) ** 2 * 1.0 + (g - rgb[1]) ** 2 * 1.15 + (b - rgb[2]) ** 2 * 1.0;
    if (d < bestD) {
      bestD = d;
      best = hex;
    }
  }
  return best;
}

/**
 * 照片 → 配色。
 * @throws 图片无法解码时抛出，由调用方提示用户换一张
 */
export async function analyzePhoto(file: File): Promise<PhotoColors> {
  const bitmap = await createImageBitmap(file);
  const size = 96;
  const canvas = document.createElement('canvas');
  canvas.width = size;
  canvas.height = size;
  const ctx = canvas.getContext('2d');
  if (!ctx) {
    bitmap.close();
    throw new Error('当前浏览器不支持 2D 画布');
  }

  // 居中裁成正方形，保证人脸区域采样比例稳定
  const side = Math.min(bitmap.width, bitmap.height);
  ctx.drawImage(
    bitmap,
    (bitmap.width - side) / 2,
    (bitmap.height - side) / 2,
    side,
    side,
    0,
    0,
    size,
    size,
  );

  const sample = (x0: number, y0: number, x1: number, y1: number): RGB => {
    const rect = ctx.getImageData(
      Math.floor(x0 * size),
      Math.floor(y0 * size),
      Math.max(1, Math.floor((x1 - x0) * size)),
      Math.max(1, Math.floor((y1 - y0) * size)),
    ).data;
    let r = 0;
    let g = 0;
    let b = 0;
    let n = 0;
    for (let i = 0; i < rect.length; i += 4) {
      const a = rect[i + 3];
      if (a < 128) {
        continue;
      }
      r += rect[i];
      g += rect[i + 1];
      b += rect[i + 2];
      n++;
    }
    if (!n) {
      return [200, 170, 140];
    }
    return [r / n, g / n, b / n];
  };

  const hairRgb = sample(0.34, 0.03, 0.66, 0.16);
  const faceRgb = sample(0.36, 0.2, 0.64, 0.42);
  const robeRgb = sample(0.22, 0.66, 0.78, 0.96);

  // 缩略图：给面板展示"取色来源"
  const thumbCanvas = document.createElement('canvas');
  thumbCanvas.width = 160;
  thumbCanvas.height = 160;
  const tctx = thumbCanvas.getContext('2d');
  if (tctx) {
    tctx.drawImage(canvas, 0, 0, size, size, 0, 0, 160, 160);
  }

  bitmap.close();

  return {
    thumb: thumbCanvas.toDataURL('image/jpeg', 0.72),
    skin: nearestInPalette(SKIN_TONES, faceRgb),
    robe: nearestInPalette(ROBE_COLORS, robeRgb),
    hair: nearestInPalette(HAIR_COLORS, hairRgb),
  };
}
