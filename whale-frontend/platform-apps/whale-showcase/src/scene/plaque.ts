import * as THREE from 'three';

/**
 * 匾额金字。
 *
 * <p>旧方案在 Blender 里用三组"王"形笔画方块冒充题字：两面 24 个 box、近 300 个
 * 三角面，在街景机位里占二十来个像素，横平竖直的方块组读不出"字"，只读出
 * "贴了块东西"。真做汉字几何需要字体轮廓（每字几十个顶点起步），对面片来说
 * 完全是浪费——字是**平面上的信息**，就该用贴图承载。</p>
 *
 * <p>做法：地子（深栗色漆面）+ 面片各悬 4mm 出头的透明面片，canvas 用系统楷体
 * 画字。金色走**金属材质**而不是把金色画死在贴图里：黄昏的低角度阳光扫过时
 * 笔画有真实高光，夜里灯笼点出暖斑——这是"描金"和"金漆印刷"的区别。</p>
 *
 * <p>两种地子来源（S31 起分叉）：</p>
 * <ul>
 *   <li>牌坊：GLB 自带深漆地子（`paifang.py` 的 PLAQUE），这里只补字面片——
 *     {@link attachPlaqueSigns}；</li>
 *   <li>城楼：程序几何没有 GLB 地子，地子 box 在本模块里一并建——
 *     {@link createGatePlaque}。</li>
 * </ul>
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

/** 贴图分辨率：牌坊面片 2.30×0.82m，440px/m 下单字约 270px，街景机位绰绰有余 */
const PX_PER_M = 440;

/** 题字。黄昏渡口，取"暮云"之意；用繁体保持匾额的书写惯例 */
const TEXT = '暮雲渡';

/**
 * 金字漆匾贴图：描金边框 + 金属质感描金的楷体题字。
 *
 * <p>字距 = 字高 × 1.185（净距 0.185 字高）：牌坊实测 0.62 字高配 0.735 间距，
 * "匾额的字距要松"——按比例走，任意尺寸都不破这一惯例。</p>
 */
function drawGoldPlaqueTexture(
  text: string,
  signW: number,
  signH: number,
  charH: number,
  /** 金漆渐变：默认牌坊版（日光直射，反射环境亮）；檐下阴影里的匾用提亮版 */
  grad: [string, string, string] = ['#f2d894', '#dcb566', '#b78f42'],
): THREE.CanvasTexture {
  const w = Math.round(signW * PX_PER_M);
  const h = Math.round(signH * PX_PER_M);
  const canvas = document.createElement('canvas');
  canvas.width = w;
  canvas.height = h;
  const ctx = canvas.getContext('2d');
  if (ctx) {
    const m = h / signH; // 像素/米
    const size = Math.round(charH * m); // 匾额字要占半块匾，"字大匾小"才压得住
    ctx.font = `${size}px "KaiTi","STKaiti","楷体","FangSong","SimSun",serif`;
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';

    // 沿匾横向均布，中心间距 = 字高 × 1.185（牌坊实测比例，见函数头注释）
    const cxm = [...text].map((_, i) => (i - (text.length - 1) / 2) * charH * 1.185);

    // 描金边框：漆地四周一道 6mm 的细金线。匾额工艺里地子描边是常规做法，
    // 没有它深漆地就是一块"黑板"；太粗会变镜框，压在 3~4px 正好是"线"不是"框"
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
  tex.anisotropy = 8;
  tex.name = 'plaque-gold-text';
  return tex;
}

let sharedMaterial: THREE.MeshStandardMaterial | null = null;

function sharedPlaqueMaterial(): THREE.MeshStandardMaterial {
  if (!sharedMaterial) {
    sharedMaterial = new THREE.MeshStandardMaterial({
      map: drawGoldPlaqueTexture(TEXT, SIGN_W, SIGN_H, 0.62),
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

/** 金字面片材质的公共参数——牌坊与城楼两处共用一套工艺 */
function goldSignMaterial(
  map: THREE.Texture,
  name: string,
  metalness = 0.55,
): THREE.MeshStandardMaterial {
  const mat = new THREE.MeshStandardMaterial({
    map,
    metalness,
    roughness: 0.42,
    alphaTest: 0.4,
    side: THREE.FrontSide,
  });
  mat.name = name;
  return mat;
}

/**
 * 给牌坊挂上两面金字。幂等由调用方保证（`buildSettlement` 只走一次）。
 *
 * <p>两面共用同一张贴图：+Z 面不旋转、-Z 面绕 Y 转 π，UV 在两个视角下
 * 恰好都读作"正立不镜像"（three.js 里 plane 的 u 沿本地 +X，转 π 后本地 +X
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

/* ---------------------------------------------------------------- 城楼匾额 */

/** 城楼匾地子（厚 0.14）宽×高；城楼面阔 12m、明间净宽 1.58，匾横跨明间+两次间 */
const GATE_BOARD_W = 3.2;
const GATE_BOARD_H = 1.0;
const GATE_BOARD_D = 0.14;
const GATE_SIGN_W = GATE_BOARD_W - 0.08;
const GATE_SIGN_H = GATE_BOARD_H - 0.06;
/**
 * 匾面离楼中轴的距离：额枋面在 ±2.225，地子面 ±2.36（留 0.065 净距防
 * z-fight），面片再出 0.074。**首版把地子放在了原点（楼中轴）上**——整块
 * 匾连面片一起藏进楼体内部，实拍只剩门缝漏出的一点金字。
 */
const GATE_BOARD_X = 2.36;
/** 城楼匾字高：匾宽 3.12 容三字，0.72 字高 + 1.185 比例间距，两侧各余 0.35 净边 */
const GATE_CHAR_H = 0.72;
const GATE_TEXT = '朝陽門';

/**
 * 城楼匾额（S31）：深漆地子 + 两面金字「朝陽門」，局部原点在**楼中轴**上。
 *
 * <p>挂在城楼檐下、额枋之外（落位见 props.ts 的 GATE_PLAQUE_ANCHOR 注释）
 * ——真实城楼匾的标准位置。楼有内外两个立面，各挂一块地子（一块 0.14 厚
 * 的板横跨不了 4.7m 的进深），两面读字都正立不镜像。地子用纯 box：檐板把
 * 它顶边遮住，看不到倒角，牌坊那套 GLB bevel 工艺在这里没有收益。</p>
 *
 * <p>面片朝向：城楼匾面朝 ±X。plane 法线 +Z 经 Ry(±π/2) 转向：+X 面转 π/2
 * 后本地 +X（读字方向）指向世界 −Z，恰是 +X 侧观者的屏幕右；−X 面同理转
 * −π/2——两面读字都正立不镜像。</p>
 */
export function createGatePlaque(): THREE.Group {
  const group = new THREE.Group();
  group.name = 'gate-plaque';

  // 檐下阴影里的匾：金属度压到 0.25（高金属度在暗环境里反射暗环境，
  // 首轮实拍金字几乎全黑）、渐变整体提亮一档——白天靠漫反射读字，夜里
  // 檐灯点亮后金属高光再回来
  const mat = goldSignMaterial(
    drawGoldPlaqueTexture(GATE_TEXT, GATE_SIGN_W, GATE_SIGN_H, GATE_CHAR_H, [
      '#fae7ae',
      '#ecc97c',
      '#d1a752',
    ]),
    'gatePlaqueGold',
    0.25,
  );
  const geo = new THREE.PlaneGeometry(GATE_SIGN_W, GATE_SIGN_H);
  for (const side of [1, -1] as const) {
    const board = new THREE.Mesh(
      // 匾面向 ±X：长边沿墙（Z）、厚沿 X——首版把 3.2 写在 X 上，地子成了
      // 一条穿楼而过的竖片，正好从中间那颗字前面戳过去（X 光枚举实锤）
      new THREE.BoxGeometry(GATE_BOARD_D, GATE_BOARD_H, GATE_BOARD_W),
      new THREE.MeshStandardMaterial({ color: 0x2b1f19, roughness: 0.52, metalness: 0.05 }),
    );
    board.name = `gate-plaque-board${side > 0 ? '+x' : '-x'}`;
    board.position.set(side * GATE_BOARD_X, 0, 0);
    board.castShadow = false;
    board.receiveShadow = true;
    group.add(board);

    const mesh = new THREE.Mesh(geo, mat);
    mesh.name = `gate-plaque-sign${side > 0 ? '+x' : '-x'}`;
    mesh.position.set(side * (GATE_BOARD_X + GATE_BOARD_D / 2 + PROUD), 0, 0);
    mesh.rotation.y = side * (Math.PI / 2);
    mesh.castShadow = false;
    mesh.receiveShadow = true;
    group.add(mesh);
  }
  return group;
}
