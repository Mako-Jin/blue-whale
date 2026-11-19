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
