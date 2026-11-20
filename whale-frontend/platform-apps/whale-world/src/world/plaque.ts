import * as THREE from 'three';

/**
 * 城门楼匾额（自 whale-showcase 的 plaque.ts 移植，视觉合并批4）。
 *
 * <p>字是**平面上的信息**，就该由贴图承载：字体轮廓做成几何（每字几十个顶点）
 * 在几十米外的城楼上只占几十个像素，纯粹浪费。做法是漆地 box + 悬出 4mm 的
 * 透明面片，canvas 用系统楷体画字。</p>
 *
 * <p>金色走**金属材质**而不是把金色画死在贴图里：黄昏的低角度阳光扫过时笔画
 * 有真实高光，夜里檐灯点出暖斑——这是"描金"与"金漆印刷"的区别。</p>
 *
 * <p>与 showcase 版的差别：那边城楼匾面朝 ±X（楼沿 Z 向排布），蓝鲸城四个门楼
 * 的立面都在本地 ±Z，所以这里的方向常量按 ±Z 出图，题字由调用方按门名传入
 * （南薰/望江/朝陽/鯨津）。</p>
 */

/** 漆地厚度（米） */
export const BOARD_D = 0.14;
/** 面片比地子面出头多少：贴死了会 z-fight，出多了侧看是悬空的 */
export const SIGN_PROUD = 0.004;

/** 贴图分辨率（px/m）：匾面 4m 宽时单字约 300px，街景与门洞机位都够 */
const PX_PER_M = 440;

/**
 * 金字漆匾贴图：描金边框 + 金漆渐变楷体题字。
 *
 * <p>字距 = 字高 × 1.185（净距 0.185 字高）。showcase 在牌坊上实测的比例，
 * "匾额的字距要松"——按比例走，任意尺寸都不破这条惯例。</p>
 */
export function drawGoldPlaqueTexture(
  text: string,
  signW: number,
  signH: number,
  charH: number,
  /** 金漆渐变：檐下阴影里的匾用提亮版（默认），见调用处 */
  grad: [string, string, string] = ['#fae7ae', '#ecc97c', '#d1a752'],
): THREE.CanvasTexture {
  const w = Math.round(signW * PX_PER_M);
  const h = Math.round(signH * PX_PER_M);
  const canvas = document.createElement('canvas');
  canvas.width = w;
  canvas.height = h;
  const ctx = canvas.getContext('2d');
  if (ctx) {
    const m = h / signH; // 像素/米
    const size = Math.round(charH * m); // 字要占半块匾，"字大匾小"才压得住
    ctx.font = `${size}px "KaiTi","STKaiti","楷体","FangSong","SimSun",serif`;
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';

    // 沿匾横向均布，中心间距 = 字高 × 1.185
    const cxm = [...text].map((_, i) => (i - (text.length - 1) / 2) * charH * 1.185);

    // 描金边框：漆地四周一道 6mm 细金线。没有它深漆地就是一块"黑板"；
    // 太粗会变镜框，3~4px 正好是"线"不是"框"
    ctx.strokeStyle = 'rgba(216,181,102,0.62)';
    ctx.lineWidth = Math.max(3, Math.round(0.006 * m));
    const inset = Math.round(0.055 * m);
    ctx.strokeRect(inset, inset, w - inset * 2, h - inset * 2);

    // 金漆渐变：上亮下沉。真描金的字在檐下看顶部受光多，纯色金反而像塑料
    const gold = ctx.createLinearGradient(0, h / 2 - size / 2, 0, h / 2 + size / 2);
    gold.addColorStop(0, grad[0]);
    gold.addColorStop(0.55, grad[1]);
    gold.addColorStop(1, grad[2]);

    for (const [i, x] of cxm.entries()) {
      const ch = text[i];
      const px = w / 2 + x * m;
      const py = h / 2;
      // 落影：字凸出漆面几毫米，光从上来时下缘有影——没有这层，字像印上去的
      ctx.fillStyle = 'rgba(0,0,0,0.45)';
      ctx.fillText(ch, px + size * 0.02, py + size * 0.035);
      // 深色描边与地子同色系：把笔画从渐变里"托"出来
      ctx.strokeStyle = 'rgba(43,31,25,0.75)';
      ctx.lineWidth = Math.max(2, size * 0.012);
      ctx.strokeText(ch, px, py);
      ctx.fillStyle = gold;
      ctx.fillText(ch, px, py);
    }
  }

  const tex = new THREE.CanvasTexture(canvas);
  tex.colorSpace = THREE.SRGBColorSpace;
  tex.anisotropy = 4;
  tex.name = `plaque-gold-${text}`;
  return tex;
}

/**
 * 金字面片材质。
 *
 * <p>金属度压到 0.25：城楼匾挂在檐下阴影里，高金属度在暗环境反射的还是暗环境
 * （showcase 首轮实拍金字几乎全黑）；白天靠漫反射读字，夜里檐灯点亮后金属
 * 高光再回来。alphaTest 硬裁剪代替透明混合：不进 transparent 排序，笔画边缘也干净。</p>
 */
export function goldSignMaterial(
  map: THREE.Texture,
  name: string,
  metalness = 0.25,
): THREE.MeshStandardMaterial {
  const mat = new THREE.MeshStandardMaterial({
    map,
    metalness,
    roughness: 0.42,
    alphaTest: 0.4,
    side: THREE.FrontSide,
    /* 笔画自发光：夜里匾额挂在檐下，仅有的光离匾面还有几米，纯漫反射时整块匾
     * 是个黑洞。贴图的 alpha 只覆盖笔画，自发光只落在字上；亮度压在 bloom 阈值
     * （0.92）之下，白天只是"金字略提亮"，不会发光溢边。 */
    emissive: new THREE.Color(0xffcf8a),
    emissiveMap: map,
    emissiveIntensity: 0.42,
  });
  mat.name = name;
  return mat;
}
