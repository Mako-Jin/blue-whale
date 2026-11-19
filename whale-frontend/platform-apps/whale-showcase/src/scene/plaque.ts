import * as THREE from 'three';

/**
 * 匾额金字。
 *
 * <p>旧方案在 Blender 里用三组"王"形笔画方块冒充题字：两面 24 个 box、近 300 个
 * 三角面，在街景机位里占二十来个像素，横平竖直的方块组读不出"字"，只读出
 * "贴了块东西"。真做汉字几何需要字体轮廓（每字几十个顶点起步），对面片来说
 * 完全是浪费——字是**平面上的信息**，就该用贴图承载。</p>
 *
 * <p>做法：GLB 只保留深栗色漆地（0x2B1F19，见 `paifang.py` 的 PLAQUE），这里在
 * 地子两面各悬一片 4mm 出头的透明面片，canvas 用系统楷体画字。金色走
 * **金属材质**而不是把金色画死在贴图里：黄昏的低角度阳光扫过时笔画有真实高光，
 * 夜里桥头的暖灯也能在笔画上点亮——这是"描金"和"金漆印刷"的区别。</p>
 *
 * <p>为什么不把字烘进 GLB 的 UV：牌坊地子是 `blib.box` 出来的，没有按米 UV；
 * 为了三个字去给牌坊过一遍 UV 管线，收益比面片方案差一个量级。</p>
 */

/** 与 `paifang.py::bay()` 的 plaque 分支数值一一对应（Blender Z-up，导出后 Z→Y） */
const PLAQUE_CENTER_Y = 4.91; // beam_top 4.35 + 板高一半 0.56
const BOARD_FACE = 0.135; // 漆地半厚（0.27/2），导出后这条轴变成 three 的 Z
const BOARD_W = 2.38; // pw - 0.24
const BOARD_H = 0.88;

/** 面片比地子四周各缩一圈，避免盖住 bevel 倒角边 */
const SIGN_W = BOARD_W - 0.08;
const SIGN_H = BOARD_H - 0.06;
/** 出头 4mm：贴死了会 z-fight，出多了侧面看是悬空的 */
const PROUD = 0.004;

/** 题字。黄昏渡口，取"暮云"之意；用繁体保持匾额的书写惯例 */
const TEXT = '暮雲渡';

/** 贴图分辨率：面片 2.30×0.82m，440px/m 下单字约 270px，街景机位绰绰有余 */
const PX_PER_M = 440;

let sharedMaterial: THREE.MeshStandardMaterial | null = null;

function plaqueTexture(): THREE.CanvasTexture {
  const w = Math.round(SIGN_W * PX_PER_M);
  const h = Math.round(SIGN_H * PX_PER_M);
  const canvas = document.createElement('canvas');
  canvas.width = w;
  canvas.height = h;
  const ctx = canvas.getContext('2d');
  if (ctx) {
    const m = h / SIGN_H; // 像素/米
    const size = Math.round(0.62 * m); // 字高 0.62m：匾额字要占半块匾，"字大匾小"才压得住
    ctx.font = `${size}px "KaiTi","STKaiti","楷体","FangSong","SimSun",serif`;
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';

    // 三字均布：中心间距 0.735m（字高 0.62 + 净距 0.115），传统匾额的字距要松
    const cxm = [-0.735, 0, 0.735];

    // 描金边框：漆地四周一道 6mm 的细金线。匾额工艺里地子描边是常规做法，
    // 没有它深漆地就是一块"黑板"；太粗会变镜框，压在 3~4px 正好是"线"不是"框"
    ctx.strokeStyle = 'rgba(216,181,102,0.62)';
    ctx.lineWidth = Math.max(3, Math.round(0.006 * m));
    const inset = Math.round(0.055 * m);
    ctx.strokeRect(inset, inset, w - inset * 2, h - inset * 2);

    // 金漆渐变：上亮下沉。真描金的字在檐下看顶部受光多，纯色金反而像塑料
    const grad = ctx.createLinearGradient(0, h / 2 - size / 2, 0, h / 2 + size / 2);
    grad.addColorStop(0, '#f2d894');
    grad.addColorStop(0.55, '#dcb566');
    grad.addColorStop(1, '#b78f42');

    for (const [i, x] of cxm.entries()) {
      const ch = TEXT[i];
      const px = w / 2 + x * m;
      const py = h / 2;
      // 落影：字凸出漆面几毫米，光从上来时下缘有影——没有这层，字像印上去的
      ctx.fillStyle = 'rgba(0,0,0,0.45)';
      ctx.fillText(ch, px + size * 0.02, py + size * 0.035);
      // 深色描边与地子同色系：把笔画从渐变里"托"出来
      ctx.strokeStyle = 'rgba(43,31,25,0.75)';
      ctx.lineWidth = Math.max(2, size * 0.012);
      ctx.strokeText(ch, px, py);
      ctx.fillStyle = grad;
      ctx.fillText(ch, px, py);
    }
  }

  const tex = new THREE.CanvasTexture(canvas);
  tex.colorSpace = THREE.SRGBColorSpace;
  tex.anisotropy = 8;
  tex.name = 'plaque-gold-text';
  return tex;
}

function sharedPlaqueMaterial(): THREE.MeshStandardMaterial {
  if (!sharedMaterial) {
    sharedMaterial = new THREE.MeshStandardMaterial({
      map: plaqueTexture(),
      // 金属度给到 0.55：白天太阳扫出高光，夜里灯笼点出暖斑。再高会黑成镜子——
      // 金属只在有环境可反的时候才亮，这一版先看实拍再调。
      metalness: 0.55,
      roughness: 0.42,
      alphaTest: 0.4, // 硬裁剪代替透明混合：不进 transparent 排序，笔画边缘也干净
      side: THREE.FrontSide,
    });
    sharedMaterial.name = 'plaqueGold';
  }
  return sharedMaterial;
}

/**
 * 给牌坊挂上两面金字。幂等由调用方保证（`buildSettlement` 只走一次）。
 *
 * <p>两面共用同一张贴图：+Z 面不旋转、-Z 面绕 Y 转 π，UV 在两个视角下
 * 恰好都读作"正立不镜像"（三.js 里 plane 的 u 沿本地 +X，转 π 后本地 +X
 * 指向世界 -X，而从 -Z 侧看屏幕右也是世界 -X）。街景机位（z=21 看向 -Z）
 * 正对 +Z 面，是主展示面。</p>
 */
export function attachPlaqueSigns(gate: THREE.Object3D): void {
  const mat = sharedPlaqueMaterial();
  const geo = new THREE.PlaneGeometry(SIGN_W, SIGN_H);
  for (const side of [1, -1] as const) {
    const mesh = new THREE.Mesh(geo, mat);
    mesh.name = `plaque-sign${side > 0 ? '+z' : '-z'}`;
    mesh.position.set(0, PLAQUE_CENTER_Y, side * (BOARD_FACE + PROUD));
    if (side < 0) mesh.rotation.y = Math.PI;
    mesh.castShadow = false; // 字不投影：透明镂空的影子是一团噪点
    mesh.receiveShadow = true; // 檐口的影子落到字上反而是"匾在檐下"的证据
    gate.add(mesh);
  }
}
