import * as THREE from 'three';
import { fbm, smoothstep } from './util';

/**
 * 程序化贴图。
 *
 * <p>只有一类东西值得上贴图：**白墙**。它面积最大、最平、最容易被照成一张纸。
 * 纯色 `MeshStandardMaterial` 在这套 IBL + ACES 管线下，一整面 7×5m 的墙就是
 * 一块发光板——不管把 roughness 调到 0.96 还是 0.99，它都没有一个像素的变化。</p>
 *
 * <p>为什么不用顶点色：墙面要的是 3~6cm 尺度的雨痕，靠细分网格写到这个精度，
 * 一栋房子就是几万顶点；而且 InstancedMesh 是九个实例共用同一份几何，
 * 顶点色只能"整栋一样脏"。贴图免费、分辨率高、还能给不同尺寸的墙面复用。</p>
 *
 * <p>为什么不用真实照片：这些资产是程序生成的，贴一张扫描的白墙会被 UV 拉伸
 * 成"墙纸"。这里的图只在 0.95 上下做 ±8% 的调制，<b>不承担颜色</b>——
 * 颜色仍然由 `baseColorFactor`（0xB6AE9C）给，图只负责"脏"。</p>
 */

/**
 * 贴图覆盖的世界尺寸（米）。必须与 `house.py` 里 `box_uv(scale=3.2)` 一致，
 * 否则同一张图在 GLB 墙与程序化院墙上会差一个尺度。
 */
export const PLASTER_METERS = 3.2;

const SIZE = 512;

/** 临时开关：把石面贴图换成 8×8 棋盘，用来验证 UV 是否真的被采样 */
const DEBUG_STONE_UV = false;

/**
 * 白墙：雨痕 + 霉斑 + 砂粒。
 *
 * <p>三层的尺度是分开的：霉斑 1~2m（墙面的"潮"是一块一块的）、雨痕宽 2~4cm
 * 长 0.5~1.5m（从檐口往下淋）、砂粒 3~5cm（抹灰的颗粒）。全部按米算，
 * 换墙的尺寸不会让颗粒跟着放大。</p>
 *
 * <p>这张图<b>可以平铺</b>：没有"墙根水渍"这种带上下方向的元素——一旦平铺，
 * 水渍会在墙上重复成一条条水平带。墙根的脏交给 `house.py` 的勒脚石带与
 * props 的门前踏石，那是几何，不是贴图。</p>
 *
 * <p>平铺用 `MirroredRepeatWrapping`：fbm 不是周期函数，直接 repeat 会在
 * 3.2m 网格上留一条看得见的接缝；镜像折返没有硬边，代价是对称感，
 * 而这个尺度上的对称没人会注意到。</p>
 */
export function plasterTexture(): THREE.CanvasTexture {
  const canvas = document.createElement('canvas');
  canvas.width = SIZE;
  canvas.height = SIZE;
  const ctx = canvas.getContext('2d');
  if (ctx) {
    const img = ctx.createImageData(SIZE, SIZE);
    for (let y = 0; y < SIZE; y += 1) {
      const v = y / SIZE;
      for (let x = 0; x < SIZE; x += 1) {
        const u = x / SIZE;

        // 1 UV = 3.2m = 512px，所以频率 f 对应尺度 3.2/f 米
        // 石灰不匀（1.5m）
        const blotch = fbm(u * 2.2 + 3.7, v * 2.2 - 1.3, 3) - 0.5;
        // 砂粒（6cm）
        const grain = fbm(u * 46 + 11.2, v * 46 + 6.4, 2) - 0.5;
        // 霉斑：低频 fbm 阈值化，只在约一成面积上出现
        const mold = smoothstep(0.60, 0.88, fbm(u * 4.4 + 9.1, v * 4.4 - 2.2, 4));

        // 雨痕分两种宽窄：
        //   细痕 2~5cm——雨水顺着墙面流下的水线，是"白墙"最典型的脏
        //   冲沟 8~18cm——檐口滴水常年冲出来的宽带
        // 再乘一个低频 cover：真实墙面上不是每一条竖线都有痕，是一段一段的，
        // 缺了这层掩码，整面墙就成了"竖条纹布"（第一版就是这个毛病）。
        const fineRaw = fbm(u * 86 + 21.5, v * 8.0 + 0.7, 3);
        const broadRaw = fbm(u * 24 + 5.3, v * 3.4 + 3.1, 3);
        const cover = smoothstep(0.40, 0.70, fbm(u * 9.0 + 40.2, v * 1.6 + 12.0, 3));
        const streak =
          cover * (smoothstep(0.58, 0.78, fineRaw) * 0.55 + smoothstep(0.66, 0.88, broadRaw));

        // 基准 0.97：整面墙暗 3%，肉眼几乎不可见，但脱掉了"发光板"感
        let lum = 0.97 + blotch * 0.10 + grain * 0.05;
        lum -= streak * 0.19;
        lum -= mold * 0.10;

        const dirt = streak * 0.7 + mold;
        const i = (y * SIZE + x) * 4;
        img.data[i] = Math.max(0, Math.min(255, lum * (1 - 0.05 * dirt) * 255));
        img.data[i + 1] = Math.max(0, Math.min(255, lum * (1 + 0.03 * mold) * 255));
        img.data[i + 2] = Math.max(0, Math.min(255, lum * (1 - 0.02 * dirt) * 255));
        img.data[i + 3] = 255;
      }
    }
    ctx.putImageData(img, 0, 0);
  }

  const tex = new THREE.CanvasTexture(canvas);
  tex.wrapS = tex.wrapT = THREE.MirroredRepeatWrapping;
  tex.colorSpace = THREE.SRGBColorSpace;
  // 墙是大面积掠射角观察对象，不开各向异性过滤时远处会糊成一团脏
  tex.anisotropy = 8;
  tex.name = `plaster-weather-${PLASTER_METERS}m`;
  return tex;
}

let cachedPlaster: THREE.CanvasTexture | null = null;

/** 单例：九个实例、三变体、外加程序化院墙共用同一张图（省显存、省上传） */
export function sharedPlasterTexture(): THREE.CanvasTexture {
  if (!cachedPlaster) cachedPlaster = plasterTexture();
  return cachedPlaster;
}

/**
 * 把白墙贴图挂到材质上。幂等：重复调用不会重建。
 *
 * <p>顺手把 `envMapIntensity` 压到 0.7：白墙在掠射角会被天空贴图整片点亮，
 * 那是"瓷砖"的观感；真实的粉刷墙是漫反射为主，几乎不反射天光。</p>
 */
export function applyPlasterMap(mat: THREE.MeshStandardMaterial | THREE.Material): void {
  const std = mat as THREE.MeshStandardMaterial;
  if (!std.isMeshStandardMaterial || std.map) return;
  std.map = sharedPlasterTexture();
  std.envMapIntensity = 0.7;
  std.needsUpdate = true;
}

/** 材质名判定：GLB 里叫 `plaster` / `plasterBack`，程序化件里也是这两支 */
export const isPlaster = (name: string | undefined): boolean => !!name && /^plaster/i.test(name);

/**
 * 青石：颗粒 + 石斑 + 少量深色凹坑。
 *
 * <p>石板街在 S9 已经做了逐块的色差与 3cm 沉陷，但<b>一块 3.4m 的石板内部是纯色</b>——
 * 站在街上往下看，脚边这块石头没有任何细节，整条街读起来就是"刷了浅灰漆的水泥"。
 * 石材是粗磨面，颗粒 2~5cm，这个尺度在 5m 内一眼可见。</p>
 *
 * <p>幅度必须比白墙更克制：石板是画面下半部的大面积，一旦看起来"有噪点"，
 * 街道就从"石头铺的"变成"地毯"。所以只做 ±8% 的明暗，深色点也只在约 1% 面积上出现。</p>
 */
export function stoneTexture(): THREE.CanvasTexture {
  const canvas = document.createElement('canvas');
  canvas.width = SIZE;
  canvas.height = SIZE;
  const ctx = canvas.getContext('2d');
  if (ctx) {
    const img = ctx.createImageData(SIZE, SIZE);
    for (let y = 0; y < SIZE; y += 1) {
      const v = y / SIZE;
      for (let x = 0; x < SIZE; x += 1) {
        const u = x / SIZE;
        // fbm 的输出集中在 0.5 附近（std 约 0.1），直接乘小系数等于没有——
        // 第一版 ±0.08 铺到石板上只差 6 个灰阶，出图完全看不见。先乘 2 拉满幅度，
        // 再按需要收缩。
        const blotch = (fbm(u * 4.0 + 7.3, v * 4.0 + 2.9, 3) - 0.5) * 2;
        // 颗粒（4cm）：花岗岩/青石被凿过之后的麻点
        const grain = (fbm(u * 76 + 3.1, v * 76 + 8.8, 2) - 0.5) * 2;
        // 凹坑：高频噪声阈值化，出现率低、形状碎
        const pit = smoothstep(0.78, 0.92, fbm(u * 130 + 15.7, v * 130 + 4.2, 2));

        // 苔斑：石板缝周边常年潮，0.4~0.5m 的不规则暗绿斑。这条是石面<b>唯一</b>
        // 在透视压缩后还留得住的信息——颗粒（4cm）在 5m 外就被 mip 平均掉了，
        // 只有 0.4m 尺度、30% 的对比度才能让街面读起来是"老石头"。
        const moss = smoothstep(0.60, 0.80, fbm(u * 7.0 + 31.5, v * 7.0 + 9.4, 4));
        // 凿痕：平行细密的手工凿纹，2~3cm 间距。频率比颗粒高、方向随机
        const chisel = smoothstep(0.56, 0.72, fbm(u * 170 + 2.2, v * 42 + 7.7, 2)) * 0.10;

        let lum = 0.975 + blotch * 0.22 + grain * 0.12;
        lum -= pit * 0.16;
        lum -= moss * 0.28 + chisel;
        if (DEBUG_STONE_UV) lum = (Math.floor(u * 8) + Math.floor(v * 8)) % 2 ? 0.95 : 0.35;

        const i = (y * SIZE + x) * 4;
        const c = Math.max(0, Math.min(255, lum * 255));
        img.data[i] = c;
        img.data[i + 1] = c * (1 + moss * 0.06);
        img.data[i + 2] = c * (0.995 - moss * 0.05); // 苔处偏绿：只压蓝，不动绿
        img.data[i + 3] = 255;
      }
    }
    ctx.putImageData(img, 0, 0);
  }
  const tex = new THREE.CanvasTexture(canvas);
  tex.wrapS = tex.wrapT = THREE.MirroredRepeatWrapping;
  tex.colorSpace = THREE.SRGBColorSpace;
  tex.anisotropy = 8;
  tex.name = `stone-grain-${PLASTER_METERS}m`;
  return tex;
}

let cachedStone: THREE.CanvasTexture | null = null;

export function sharedStoneTexture(): THREE.CanvasTexture {
  if (!cachedStone) cachedStone = stoneTexture();
  return cachedStone;
}

/** 石面贴图挂载。同样幂等。 */
export function applyStoneMap(mat: THREE.Material): void {
  const std = mat as THREE.MeshStandardMaterial;
  if (!std.isMeshStandardMaterial || std.map) return;
  std.map = sharedStoneTexture();
  std.envMapIntensity = 0.85;
  std.needsUpdate = true;
}

/* ------------------------------------------------------------------ 城墙砖石 */

/**
 * 砖石贴图覆盖的世界尺寸（米）。
 *
 * <p><b>必须是段长 8m 的整数分之一</b>：8 / 4 = 2，一个标准段正好铺两次，
 * 段与段之间贴图相位自然接上。改成 3m 或 5m 都会让每 8m 出现一次半块砖错位——
 * 本来想消除"格子感"，结果每段边界上多出一条错缝，等于给格子描了边。</p>
 */
export const BRICK_METERS = 4.0;

/** 每层砌石高 0.5m（4m / 8 层），恰好是 64px 的整数 —— 不做小数层高，否则最后一行会薄一截 */
const BRICK_COURSES = 8;
/** 每层 6 块（0.67m/块）。条石的尺度就是 0.6~1m，用 5 块时块头太胖、像混凝土砌块 */
const BRICK_PER_ROW = 6;
/** 灰缝宽（px）：2px ≈ 1.6cm。3px 时缝宽到 2.3cm，远看是一圈亮网格 */
const BRICK_GAP = 2;

/** 整数哈希 → [0,1)。同一 (row,col) 永远同一个值，平铺时不会在接缝处跳变 */
function hash2(x: number, y: number): number {
  const s = Math.sin(x * 127.1 + y * 311.7) * 43758.5453;
  return s - Math.floor(s);
}

/**
 * 可平铺的 value noise。
 *
 * <p>`fbm()` 不可平铺：u=0 与 u=1 取到不同的值，贴图平铺时接缝上会跳一条硬边。
 * 这里把格点索引对 `period` 取模，于是 u=1 与 u=0 落在同一批格点上，
 * 数学上严格无缝。代价是只能出"周期图案"——对石墙这种没有强特征的表面正合适。</p>
 */
function pnoise(u: number, v: number, period: number, periodV = period): number {
  const fx = u * period;
  const fy = v * periodV;
  const ix = Math.floor(fx);
  const iy = Math.floor(fy);
  const tx = fx - ix;
  const ty = fy - iy;
  const sx = tx * tx * (3 - 2 * tx);
  const sy = ty * ty * (3 - 2 * ty);
  const p = (x: number, y: number) =>
    hash2(((x % period) + period) % period, ((y % periodV) + periodV) % periodV);
  const a = p(ix, iy);
  const b = p(ix + 1, iy);
  const c = p(ix, iy + 1);
  const d = p(ix + 1, iy + 1);
  return (a * (1 - sx) + b * sx) * (1 - sy) + (c * (1 - sx) + d * sx) * sy;
}

/** 三倍频可平铺 fbm，输出 0..1 */
function pfbm(u: number, v: number, period: number): number {
  return pnoise(u, v, period) * 0.5 + pnoise(u, v, period * 2) * 0.32 + pnoise(u, v, period * 4) * 0.18;
}

/** 临时开关：把砖纹换成"一块=一格"的棋盘，用来核对 UV 尺度（0.5m 层 × 0.67m 块） */
const DEBUG_BRICK_UV = false;

/**
 * 城墙：层砌条石。
 *
 * <p>第一版画成了"亮块 + 深缝 + 每块一个色"，出图是<b>马赛克瓷砖</b>——
 * 问题不在缝，在块：色差 0.86~1.12 让每块都自成一格，眼晴读到的是"格子"，
 * 而城墙最不该有的就是格子感。</p>
 *
 * <p>真实条石墙的三个特征：<b>石头颜色几乎一样</b>（同一采石场）、<b>缝是细的暗线</b>
 * （不是亮网格）、<b>脏是连续的</b>（雨痕、水渍横跨几十块石头）。所以这一版：
 * 块色差收到 10%、缝压到 0.6、再叠一层 1.5m 尺度的可平铺斑驳把相邻块"连"起来。</p>
 */
export function brickTexture(): THREE.CanvasTexture {
  const canvas = document.createElement('canvas');
  canvas.width = SIZE;
  canvas.height = SIZE;
  const ctx = canvas.getContext('2d');
  if (ctx) {
    const img = ctx.createImageData(SIZE, SIZE);
    const ch = SIZE / BRICK_COURSES; // 64
    const bw = SIZE / BRICK_PER_ROW; // 85.3
    const gap = BRICK_GAP;

    for (let y = 0; y < SIZE; y += 1) {
      const row = Math.floor(y / ch);
      const sy = y - row * ch;
      // 奇偶层错半块：砌筑最基本的做法，也是"一眼看出不是瓷砖"的关键
      const shift = (row % 2) * bw * 0.5;
      const v = y / SIZE;
      for (let x = 0; x < SIZE; x += 1) {
        const wrapped = (x + shift) % SIZE;
        let col = Math.floor(wrapped / bw);
        const sx = wrapped - col * bw;
        col %= BRICK_PER_ROW;
        const u = x / SIZE;

        const h = hash2(col, row);
        const h2 = hash2(col + 17, row + 5);
        // 块本体：只 ±6% 的明度差（26% 是马赛克、10% 仍像砌块，6% 才读成"同一批石头"）
        let lum = 0.965 + h * 0.06;
        // 层间差比块间差更"远视"：0.5m 的层理在 40m 外仍可读
        lum *= 0.985 + hash2(0, row) * 0.03;
        // 块内部的斜向渐变（石头表面不是平的）
        lum *= 1 + (sx / bw - 0.5) * 0.035;

        // 灰缝：细、暗，但只在近处成立。0.58 时缝是清晰的网格，30m 外整面墙
        // 变成"瓷砖"；0.80 后它退化成一圈灰雾，只剩层理与块差
        const edge = Math.min(sx, bw - sx, sy, ch - sy);
        if (edge < gap) lum = 0.80 + h2 * 0.05;
        else if (edge < gap + 1) lum *= 0.94;

        // 连续的脏：1.5m 斑驳 + 0.2m 麻点，把相邻石块"连"成一片
        const patina = (pfbm(u, v, 3) - 0.5) * 0.11 + (pfbm(u, v, 24) - 0.5) * 0.05;
        lum *= 1 + patina;

        // 雨痕：竖向拉长的暗条（u 方向 6 个周期 = 0.67m 一条，v 方向 1 个周期，
        // 所以是"竖条"而不是"斑点"；两个方向都用整数周期，平铺仍然无缝）
        const streak = smoothstep(0.60, 0.84, pnoise(u, v, 6, 1));
        if (edge >= gap) lum *= 1 - streak * 0.11;

        const i = (y * SIZE + x) * 4;
        let c = Math.max(0, Math.min(255, lum * 255));
        let r = c;
        let g = c;
        let bl = c;
        if (DEBUG_BRICK_UV) {
          // 每个"块"一格：正方块 255 / 错缝块 90。8m 段应出现 12 列 × 9 行
          const on = (col + row) % 2 === 0;
          r = on ? 250 : 90;
          g = on ? 250 : 90;
          bl = on ? 250 : 90;
        } else {
          // 冷暖：只在非缝区微调，缝是灰浆、永远中性
          const warm = edge < gap ? 0 : (h2 - 0.5) * 0.035;
          r = Math.max(0, Math.min(255, c * (1 + warm)));
          g = Math.max(0, Math.min(255, c * (1 + warm * 0.3)));
          bl = Math.max(0, Math.min(255, c * (1 - warm * 0.6)));
        }
        img.data[i] = r;
        img.data[i + 1] = g;
        img.data[i + 2] = bl;
        img.data[i + 3] = 255;
      }
    }
    ctx.putImageData(img, 0, 0);
  }

  const tex = new THREE.CanvasTexture(canvas);
  tex.wrapS = tex.wrapT = THREE.RepeatWrapping;
  tex.colorSpace = THREE.SRGBColorSpace;
  tex.anisotropy = 8;
  tex.name = `brick-${BRICK_METERS}m`;
  return tex;
}

let sharedBrick: THREE.CanvasTexture | null = null;
const sharedBrickTexture = (): THREE.CanvasTexture => (sharedBrick ??= brickTexture());

/** 给城墙材质挂砖石贴图。UV 由 `wall.py::box_uv(scale=4.0)` 按米算好 */
export function applyBrickMap(mat: THREE.MeshStandardMaterial): void {
  if (!mat.isMeshStandardMaterial || mat.map) return;
  mat.map = sharedBrickTexture();
  // 城墙是大块条石，不该有抹灰那种漫反射亮度；略降一点让砖缝自己出对比。
  // 但不能太低：砖的固有色本来就比白墙暗一倍，背光面只剩 hemi + IBL 时，
  // 压到 0.75 会让整面墙（和敌台）在阴影里糊成一块近黑的板。
  mat.envMapIntensity = 0.95;
  mat.needsUpdate = true;
}

/**
 * 全场景材质收尾：按材质名做统一的物理参数修正。
 *
 * <p>只做一件事——<b>压低瓦面的环境反射</b>。站在街上抬头看檐口时，瓦面几乎全部
 * 处在掠射角，菲涅尔项把整片暖天光反射回来，屋面白得像镀了锌。这不是贴图问题，
 * 也不是光照太强：把 roughness 从 0.58 抬到 0.75 只会让它变灰、垄形反而糊掉，
 * 而真实的青瓦本来就是哑光、几乎不反射天空。所以只压 `envMapIntensity`。</p>
 *
 * <p>扫全场景而不是逐个装配点处理：牌坊（整件 Object3D）、民居（InstancedMesh）、
 * 巷门/院墙（程序化合并件）三条路都会带瓦，漏掉任何一条都会在画面上露出一块
 * "镀锌"的瓦。</p>
 */
export function tuneSurfaces(root: THREE.Object3D): void {
  root.traverse((o) => {
    const m = o as THREE.Mesh;
    if (!m.isMesh) return;
    const mats = Array.isArray(m.material) ? m.material : [m.material];
    for (const raw of mats) {
      const std = raw as THREE.MeshStandardMaterial;
      if (!std.isMeshStandardMaterial || !/tile/i.test(std.name ?? '')) continue;
      std.envMapIntensity = 0.5;
    }
  });
}
