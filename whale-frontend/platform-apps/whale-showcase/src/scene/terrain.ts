import * as THREE from 'three';
import { clamp, fbm, smoothstep } from './util';

/**
 * 河谷地形。
 *
 * <p>一条河沿 Z 轴纵贯，中心线随 Z 缓慢摆动（`RIVER_MEANDER`），两侧抬升为台地。
 * 高度用解析函数给出而非噪声堆叠——因为地形高度还要被桥、码头、树、房子复用，
 * 必须能"查到任意一点的准确高度"（{@link Terrain.heightAt}），否则物件落地只能靠手调。</p>
 */

export const RIVER_HALF = 9; // 河槽半宽
export const WATER_LEVEL = 0;
const RIVER_BED = -2.4;
const BANK_H = 0.35; // 岸顶高度：必须高于水面，否则水会"淹掉"河岸
const TERRACE_H = 3.6; // 台地基准高度

/** 河道中心线随 Z 摆动：让河谷不是一条笔直的渠。水面几何与地形共用这一条线 */
export function riverCenter(z: number): number {
  return Math.sin(z * 0.013) * 6.5 + Math.sin(z * 0.0047 + 1.3) * 4.2;
}

/** 距河道中心的横向距离 */
function distToRiver(x: number, z: number): number {
  return Math.abs(x - riverCenter(z));
}

/** 未加噪声的基础剖面 */
function baseHeight(d: number, z: number): number {
  if (d < RIVER_HALF) {
    // 河床 → 岸边：窄处陡然收边，宽处平缓
    return RIVER_BED + (BANK_H - RIVER_BED) * smoothstep(RIVER_HALF * 0.5, RIVER_HALF, d);
  }
  // 岸 → 台地
  const t = smoothstep(RIVER_HALF, RIVER_HALF + 26, d);
  const h = BANK_H + (TERRACE_H - BANK_H) * t;
  // 北端抬升成崖口（S2 瀑布的落差点），南端缓降
  const north = smoothstep(70, 165, z) * 9.5;
  const south = smoothstep(-60, -180, z) * -4.0;
  return h + north + south;
}

/**
 * 全局高度查询。地形的 mesh、桥头、码头、树、房子都走这一个函数。
 */
export function heightAt(x: number, z: number): number {
  const d = distToRiver(x, z);
  let h = baseHeight(d, z);

  // 噪声只加在岸上：河床要平，否则水面会露出坑洼
  const landMask = smoothstep(RIVER_HALF * 0.92, RIVER_HALF + 4, d);
  if (landMask > 0.001) {
    const n = fbm(x * 0.019 + 11.3, z * 0.019 - 7.7, 3) - 0.5;
    h += n * 2.4 * landMask;
    // 台地上再叠一层大尺度缓丘，避免地平线是一条死线
    h += (fbm(x * 0.006 - 3.1, z * 0.006 + 5.9, 3) - 0.5) * 7.0 * smoothstep(18, 60, d);
  }

  // 桥头广场压平：桥面要落在硬质平台上。压的是**岸上台地**（d 在 11~19 之间），
  // 河槽本身不压——否则河床被抬平，水面会整片消失。
  const bankBand = smoothstep(RIVER_HALF + 0.5, RIVER_HALF + 2.0, d) * (1 - smoothstep(19, 23, d));
  const plazaMask = smoothstep(24, 10, Math.abs(z - 2)) * bankBand;
  if (plazaMask > 0.001) {
    const flat = 1.15;
    h = h * (1 - plazaMask) + flat * plazaMask;
  }

  /*
   * 镇区临水平台。
   *
   * <p><b>为什么必须有这一层</b>：河道中心线随 Z 摆动（{@link riverCenter}），
   * 到 z=38 时中心在 7.1、东岸水线在 16.1，而街面从 13.5 起——北段有 2.6m 的街道
   * <b>泡在河里</b>；z=20 附近即使没淹，街面也只有 0.2m 高，贴着水面。同一件事的
   * 第二个后果是房子：台地在 x=24 已经抬到 1.65m，而街面 0.4m，一栋房子比街高
   * 1.2m，0.36m 的台基根本接不上。</p>
   *
   * <p>临河城镇本来就是这个做法：<b>一条固定的岸线 + 石砌驳岸 + 平台上的街</b>，
   * 河怎么摆都在岸线以西。所以这里把 x 13.5…26 压成 1.18 的平台（与桥头广场的
   * 1.15 对齐），12.4→12.9 之间收成陡岸（驳岸石会盖在这道坡上），26→31 再缓缓
   * 交回自然地形，城墙因此仍然站在起伏的台地上。</p>
   */
  const townZ = smoothstep(-56, -46, z) * (1 - smoothstep(40, 50, z));
  const townX = smoothstep(13.38, 13.72, x) * (1 - smoothstep(26, 31, x));
  const townMask = townZ * townX;
  if (townMask > 0.001) {
    const flat = 1.18;
    h = h * (1 - townMask) + flat * townMask;
  }

  return h;
}

/**
 * 坡向（无量纲：每米的高差）。
 *
 * <p>地形自己用它给陡坡上岩石色，地被（草簇/碎石）也用它排除陡坡——
 * 两处必须同一个函数，否则"上色的坡"和"长草的坡"会对不上。</p>
 */
export function slopeAt(x: number, z: number): number {
  const e = 1.2;
  const hx = heightAt(x + e, z) - heightAt(x - e, z);
  const hz = heightAt(x, z + e) - heightAt(x, z - e);
  return Math.sqrt(hx * hx + hz * hz) / (2 * e);
}

/**
 * 地面细节贴图。
 *
 * <p>顶点色只能给到大尺度色块（网格 2.3m 一格，更细的变化采样不到）。没有这张图时
 * 草地是一整片平色，白天出图非常"塑料"。这里用程序化噪声生成一张平铺灰阶图当
 * `map`（与顶点色相乘），重复 90 次，等于给地表加了一层 7m 级的斑驳。</p>
 *
 * <p>用画布生成而不是引入贴图文件：不增加仓库体积，也能保证与色板同源。</p>
 */
function createGroundDetail(): THREE.Texture {
  const size = 256;
  const canvas = document.createElement('canvas');
  canvas.width = canvas.height = size;
  const ctx = canvas.getContext('2d');
  if (ctx) {
    const img = ctx.createImageData(size, size);
    for (let y = 0; y < size; y += 1) {
      for (let x = 0; x < size; x += 1) {
        // 三层噪声叠加，值域压到 0.62~1.0：只做明暗微差，不改变整体色相
        const n =
          fbm(x * 0.055, y * 0.055, 4) * 0.6 +
          fbm(x * 0.21 + 17.3, y * 0.21 - 5.1, 3) * 0.4;
        const v = Math.round(255 * (0.62 + n * 0.38));
        const i = (y * size + x) * 4;
        img.data[i] = v;
        img.data[i + 1] = v;
        img.data[i + 2] = v;
        img.data[i + 3] = 255;
      }
    }
    ctx.putImageData(img, 0, 0);
  }
  const tex = new THREE.CanvasTexture(canvas);
  tex.wrapS = tex.wrapT = THREE.RepeatWrapping;
  tex.repeat.set(90, 90);
  tex.colorSpace = THREE.SRGBColorSpace;
  tex.anisotropy = 4;
  return tex;
}

export interface Terrain {
  mesh: THREE.Mesh;
  heightAt: (x: number, z: number) => number;
}

/**
 * 建地形网格。顶点色按"水下 / 滩涂 / 草地 / 台地 / 岩石"分档，
 * 这一层不做贴图（S1 再上法线与细节），先把大形与色块立住。
 */
export function createTerrain(size = 620, segments = 264): Terrain {
  const geo = new THREE.PlaneGeometry(size, size, segments, segments);
  geo.rotateX(-Math.PI / 2);

  const pos = geo.attributes.position as THREE.BufferAttribute;
  const colors = new Float32Array(pos.count * 3);

  // 反照率。注意：THREE.Color 会把十六进制按 sRGB 转成线性工作空间，
  // 0x43512e 这种"看着还行的深绿"线性值只有 0.05，配合 /π 的漫射衰减就是死黑。
  // 走 IBL + ACES 之后必须按 sRGB 视觉值取色，别再手动压暗（那是无 IBL 时代的补丁）。
  const cMud = 0x4a4a38;
  const cWetSand = 0x8d8163;
  const cGrass = 0x6f7f49;
  const cGrassDry = 0x94914f;
  const cEarth = 0x9a8763;
  const cRock = 0x8f8a7d;

  for (let i = 0; i < pos.count; i += 1) {
    const x = pos.getX(i);
    const z = pos.getZ(i);
    const y = heightAt(x, z);
    pos.setY(i, y);

    const slope = slopeAt(x, z);
    const c = new THREE.Color();

    if (y < WATER_LEVEL + 0.15) {
      c.setHex(cMud);
    } else if (y < 0.55) {
      c.setHex(cWetSand).lerp(new THREE.Color(cGrass), smoothstep(0.15, 0.55, y));
    } else {
      // 草地 → 台地干土，按高度插值
      c.setHex(cGrass).lerp(new THREE.Color(cGrassDry), smoothstep(1.2, 3.4, y));
      c.lerp(new THREE.Color(cEarth), smoothstep(3.4, 6.5, y) * 0.7);
    }
    // 陡坡露岩
    c.lerp(new THREE.Color(cRock), clamp((slope - 0.75) * 1.2, 0, 0.6));

    // 大尺度色斑：整片单色是"低模感"最直接的来源。用极低频噪声在草色与干草色之间漂移
    const patch = fbm(x * 0.035 + 3.7, z * 0.035 - 1.9, 2) - 0.5;
    c.offsetHSL(patch * 0.035, patch * 0.12, patch * 0.075);
    // 细颗粒：频率必须低于网格分辨率（620/264≈2.3m 一格，0.9 的噪声在顶点上采样
    // 会直接走样成一条条竖向条纹），这里给到 0.22 只留低频起伏
    const grain = (fbm(x * 0.22, z * 0.22, 2) - 0.5) * 0.05;
    c.offsetHSL(0, 0, grain);

    colors[i * 3] = c.r;
    colors[i * 3 + 1] = c.g;
    colors[i * 3 + 2] = c.b;
  }

  geo.setAttribute('color', new THREE.BufferAttribute(colors, 3));
  geo.computeVertexNormals();

  const mat = new THREE.MeshStandardMaterial({
    vertexColors: true,
    roughness: 0.96,
    metalness: 0.0,
    map: createGroundDetail(),
  });

  const mesh = new THREE.Mesh(geo, mat);
  mesh.receiveShadow = true;
  mesh.castShadow = false;
  mesh.name = 'terrain';

  return { mesh, heightAt };
}
