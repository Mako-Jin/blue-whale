/** 通用小工具：插值、噪声、色板。全部纯函数，便于单测与复用。 */

export const clamp = (v: number, a: number, b: number) => (v < a ? a : v > b ? b : v);

export const lerp = (a: number, b: number, t: number) => a + (b - a) * t;

/** 经典 smoothstep：edge0 处为 0，edge1 处为 1，中间平滑过渡 */
export function smoothstep(edge0: number, edge1: number, x: number): number {
  const t = clamp((x - edge0) / (edge1 - edge0 || 1e-6), 0, 1);
  return t * t * (3 - 2 * t);
}

function hash2(x: number, y: number): number {
  const s = Math.sin(x * 127.1 + y * 311.7) * 43758.5453123;
  return s - Math.floor(s);
}

/** 二维 value noise：比 Perlin 便宜，做地形起伏足够 */
export function vnoise(x: number, y: number): number {
  const xi = Math.floor(x);
  const yi = Math.floor(y);
  const xf = x - xi;
  const yf = y - yi;
  const u = xf * xf * (3 - 2 * xf);
  const v = yf * yf * (3 - 2 * yf);
  const a = hash2(xi, yi);
  const b = hash2(xi + 1, yi);
  const c = hash2(xi, yi + 1);
  const d = hash2(xi + 1, yi + 1);
  return lerp(lerp(a, b, u), lerp(c, d, u), v);
}

/** 分形叠加，返回 0..1 */
export function fbm(x: number, y: number, octaves = 4): number {
  let amp = 0.5;
  let freq = 1;
  let sum = 0;
  let norm = 0;
  for (let i = 0; i < octaves; i += 1) {
    sum += vnoise(x * freq, y * freq) * amp;
    norm += amp;
    amp *= 0.5;
    freq *= 2.03;
  }
  return sum / norm;
}

/** 场景色板：三源素材入场后统一往这几支颜色上靠（见规划 §5） */
export const PALETTE = {
  stone: 0xcfc6b4,
  stoneDark: 0x9d9587,
  wood: 0x6b4a33,
  woodDark: 0x4a3324,
  tile: 0x3f4a4d,
  lantern: 0xc04a32,
  water: 0x3f7c8c,
  moss: 0x5a6b3e,
} as const;
