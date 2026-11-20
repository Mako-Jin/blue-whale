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

/**
 * 城门通道底的高程（S30）。
 *
 * <p>城台（props.ts::buildGateTower）的门洞底、门槛石、路面铺带、门洞壁灯
 * 全部引用这一个值——改动它必须四处同步。选 2.90 的原因：城内广场 1.18，
 * 城墙处自然地形 2.7~3.6，门洞底取中间偏下，让门内段与两侧敌台基座
 * 高程相接时两端都"埋得住"。</p>
 */
export const GATE_ROAD_Y = 2.9;

/** 河道中心线随 Z 摆动：让河谷不是一条笔直的渠。水面几何与地形共用这一条线 */
export function riverCenter(z: number): number {
  /*
   * 第三项是 S26 的远段弯河。前两项波长 483m/1337m，在全景机位可见的 ~200m
   * 里几乎是一条直线——植被撒点沿直线排布反而强化"运河"。弯河项波长 203m、
   * 幅 9m，只在南段渐入（smoothstep -150→-70：z=-70 处 0、z=-150 处全幅），
   * 镇区平台（z -56…40 固定 x）、桥（z=2）、码头（z -18…-10）、西埠头（z=6.2）
   * 全部零波及；水面几何、地形、撒点都走 riverCenter，自动跟随。
   * <b>water.ts 的 GLSL 副本 riverCenterF 必须逐项同步</b>——浅水色按到河心的
   * 距离算，不同步远段浅色带会与实际水线错位。
   */
  return (
    Math.sin(z * 0.013) * 6.5 +
    Math.sin(z * 0.0047 + 1.3) * 4.2 +
    Math.sin(z * 0.031 + 1.05) * 9.0 * (1.0 - smoothstep(-150, -70, z))
  );
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

  /*
   * 城门道（S30）。
   *
   * <p>城墙豁口（z −8…12，见 props.ts 的 WALL_RUNS）此前只是两座敌台之间的空档，
   * 通道底是自然地形——从桥广场（1.15）到城墙（3.2~3.6）一路上坡，门洞里
   * 30% 的纵坡，出图像"在土坡上挖了个洞"。真实的城门道是一条<b>有自己纵坡的
   * 道路</b>：城内段从广场缓起，门洞底基本取平，出城一段爬坡接东郊田头。</p>
   *
   * <p>三个约定（与 props.ts::buildGateTower 逐项对齐）：</p>
   * <ol>
   *   <li>门洞底高程 {@link GATE_ROAD_Y}=2.90——城台、路面共用；</li>
   *   <li>z 向纵坡 −0.044/m——与城墙 run 的俯仰角同一条线（城门道跟着
   *       城墙一起"顺坡"），否则门洞底与路面在豁口两端错开 ±0.44；</li>
   *   <li>横向全宽 ±1.9m、肩部渐收到 ±2.8m——路肩是草坡，路面靠
   *       props.ts 的土路带铺面。</li>
   * </ol>
   */
  const roadDz = z - 2;
  const roadBand = 1 - smoothstep(1.9, 2.8, Math.abs(roadDz));
  if (roadBand > 0.001) {
    let road: number;
    if (x < 29) {
      road = 1.18 + (GATE_ROAD_Y - 1.18) * smoothstep(20.5, 29, x);
    } else if (x < 34.4) {
      road = GATE_ROAD_Y;
    } else {
      road = GATE_ROAD_Y + (4.15 - GATE_ROAD_Y) * smoothstep(34.4, 39.5, x);
    }
    road -= roadDz * 0.044;
    const roadMask =
      roadBand * smoothstep(20.5, 21.5, x) * (1 - smoothstep(39.5, 42, x));
    if (roadMask > 0.001) {
      h = h * (1 - roadMask) + road * roadMask;
    }
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

    /*
     * 湿带的宽度沿河伸缩（S26 去运河感第一刀）。
     *
     * <p>滩地色带原本只由高度场决定（y<0.55 是湿沙），而高度场沿 z 几乎不变——
     * 出图是一条均匀平行贴着河道的亮带，远景里两条平行线 = 运河。真实河岸的
     * 滩宽由局地冲淤决定：有的段草直接咬到水线，有的段滩地铺开十几米。
     * 用两层低频噪声调制色带边界：shoreWob 让湿沙上限在 0.55±0.35 摆动，
     * mudWob 让滩涂线（水下泥→湿沙）同步摆——两条边不同相位，色带就
     * "忽宽忽窄、局部断开"，平行感消失。频率取 0.02/0.035（50m/30m 级），
     * 在 2.3m 顶点网格上远高于走样下限。</p>
     */
    const shoreWob = (fbm(x * 0.019 + 31.7, z * 0.021 - 9.2, 3) - 0.5) * 0.7;
    const mudWob = (fbm(x * 0.031 - 5.5, z * 0.034 + 17.9, 2) - 0.5) * 0.5;
    const sandTop = 0.55 + shoreWob;
    const mudTop = 0.15 + mudWob * 0.6;

    if (y < WATER_LEVEL + mudTop) {
      c.setHex(cMud);
    } else if (y < sandTop) {
      c.setHex(cWetSand).lerp(new THREE.Color(cGrass), smoothstep(mudTop, sandTop, y));
    } else {
      // 草地 → 台地干土，按高度插值
      c.setHex(cGrass).lerp(new THREE.Color(cGrassDry), smoothstep(1.2, 3.4, y));
      c.lerp(new THREE.Color(cEarth), smoothstep(3.4, 6.5, y) * 0.7);
    }
    // 陡坡露岩
    c.lerp(new THREE.Color(cRock), clamp((slope - 0.75) * 1.2, 0, 0.6));

    /*
     * 大尺度色斑：整片单色是"低模感"最直接的来源。
     *
     * <p>三层：28m 一级（田块 / 干湿斑）、11m 一级（草簇团的疏密、坡面冲沟痕）
     * 与 90m 一级（坡向差异、远处的整片草色漂移）。只做两级时，30~60m 的
     * 中景山坡仍是一块平滑的橄榄绿——两级之间跨了三倍频，中间正好空一档。
     * 11m 这档是把中景"接"到远景的桥：0.088 的频率在 2.3m 顶点网格上还有
     * 5 个顶点一个周期，不会再走高频走样的老路。</p>
     */
    const patch = fbm(x * 0.034 + 3.7, z * 0.034 - 1.9, 2) - 0.5;
    const mid = fbm(x * 0.088 + 13.4, z * 0.088 - 6.1, 2) - 0.5;
    const broad = fbm(x * 0.0105 - 8.3, z * 0.0105 + 2.6, 3) - 0.5;
    const tone = patch * 0.8 + mid * 0.45 + broad * 0.9;
    // 饱和度抖动 0.16 → 0.10：白天 hemi 提到 1.15 之后，0.16 的饱和抖动
    // 让向阳面草地产出 0x7f9a3a 一类的"荧光绿"。干湿差靠明度就够了。
    c.offsetHSL(tone * 0.045, tone * 0.10, tone * 0.105);
    // 细颗粒：频率必须低于网格分辨率（620/264≈2.3m 一格，0.9 的噪声在顶点上采样
    // 会直接走样成一条条竖向条纹），这里给到 0.22 只留低频起伏
    const grain = (fbm(x * 0.22, z * 0.22, 2) - 0.5) * 0.05;
    c.offsetHSL(0, 0, grain);

    /*
     * 湿带内部的干湿斑（S26 去运河感第二刀）。
     *
     * <p>南段地形下沉让滩涂带宽达十几米，但 mud/wetSand 是两块纯色——远景里
     * 这条宽带平得像人工护坡。叠一层 15m 级的明度/色相调制，滩涂出现"这里干、
     * 那里湿"的碎斑，宽带才有内容。只对湿带生效，草地另有三级色斑。</p>
     */
    if (y < sandTop + 0.5) {
      const wet = fbm(x * 0.065 + 51.3, z * 0.058 - 23.1, 3) - 0.5;
      c.offsetHSL(wet * 0.04, wet * 0.05, wet * 0.15);
    }

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
