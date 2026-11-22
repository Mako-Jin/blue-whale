/* eslint-disable */
// @ts-nocheck
/* ==========================================================================
 * 蓝鲸城 BlueWhale City — 3D 主场景（P0）
 * 由 tmp/preview-3d/app.js 原型机械转换而来：
 *  - three.js r169，ESM 依赖（不再是内联单文件）
 *  - DOM id / class 全部带 bww- 前缀，避免与基座及其他子应用冲突
 *  - 生命周期化：createWorld(host, opts) 返回 dispose()，供 React 卸载回收
 *  - 传送面板新增「进入」按钮：qiankun 内 pushState 跳转对应 2D 子应用
 * P1 增补：
 *  - 角色系统（avatar.ts / profile.ts / charpanel.ts）：参数化捏脸 + 照片取色
 *  - NPC 生态（npc.ts）与多人同屏骨架（net.ts，本地模拟，P2 换 whale-im 帧协议）
 *  - 城图小地图（minimap.ts）
 * P2 地形：城外按「虚拟都城（圣京）分层制作参考」规格重塑 —— 南岭主脉 / 东山丘陵
 * （东关垭口）/ 冲积平原田块 / 北江江湾 / 三条北向支流 / 滩湿地（详见 §1 注释）。
 * TODO(P2)：拆分本文件的城墙/宫殿/天气等子系统为独立模块并补全类型。
 * ========================================================================== */

import * as THREE from 'three';
import { DEFAULT_AVATAR, createAvatar } from './avatar';
import { loadProfile, saveProfile } from './profile';
import { NPCS, PET_PERSONA, createNpcs, npcPersona } from './npc';
import { createWorldChannel } from './net';
import { createMinimap } from './minimap';
import { mountCharPanel } from './charpanel';
import { createWorldBrain } from './brain';
import { mountNpcChat } from './npcchat';
import { createGlyphs } from './glyphs';
import { createWorldMetrics } from './metrics';
import { createDataHall } from './datahall';
import type { HallData } from './datahall';
import { createHallOverview } from './halls';
import { OVERVIEW_HALLS } from './halls';
import { createSpeech } from './speech';
import { mountSayPanel } from './saypanel';
import { createWorldAudio } from './audio';
import type { GroundKind } from './audio';
import { GUIDE_LINES, createGuide, fallbackLine } from './guide';
import { GATE_TOWERS, gateHintText, nearestGate, type NearestGate } from './landmarks';
import {
  CITY_GEOMETRY,
  DISTRICT_WALL,
  DISTRICTS,
  GATE_HALF,
  MAIN_HALL,
  MAIN_PORTAL,
  PALACES,
  SIDE_HALLS,
} from './layout';
import {
  WORLD_BOUND,
  camBlocked as isCamBlocked,
  collide as resolveCollide,
  createBlockers,
} from './collision';
import { createNavGrid } from './path';
import { createQuestSystem } from './quests';
import { createScreenLayer } from './screen';
import type { ScreenHandle } from './screen';
import { mountScreenPage, type ScreenPageHandle } from './screenpage';
import {
  PURCHASE_ORDER_PAGE,
  HUB_HALLS_PAGE,
  LOGIN_HISTORY_PAGE,
  PAY_ORDER_PAGE,
  FILE_ASSET_PAGE,
  JOB_INFO_PAGE,
  PROCESS_INSTANCE_PAGE,
  FORM_DEFINITION_PAGE,
  MESSAGE_RECORD_PAGE,
  AUDIT_LOG_PAGE,
  WORK_ORDER_PAGE,
  ATTENDANCE_DAILY_PAGE,
  CUSTOMER_PAGE,
  DEVICE_PAGE,
  COURSE_PAGE,
  PART_PAGE,
  PROJECT_PAGE,
  CONTRACT_PAGE,
  MEMBER_PAGE,
  SPACE_PAGE,
  ORDER_PAGE,
} from './screenpages';
import { createSceneEnv, type SceneEnv } from './env';
import { createPostFx, type PostFx } from './postfx';
import { createSky, type SkyState } from './env-sky';
import {
  applyBrickMap,
  applyStoneMap,
  applyPlasterMap,
} from './textures';
import { mergeGeometries } from 'three/examples/jsm/utils/BufferGeometryUtils.js';
import { BOARD_D, SIGN_PROUD, drawGoldPlaqueTexture, goldSignMaterial } from './plaque';
import type { CharPanelHandle } from './charpanel';
import type { AvatarParams } from './avatar';
import type { NpcChatHandle } from './npcchat';
import type { ScreenLayerHandle } from './screen';

export interface WorldOptions {
  /** 是否运行在 qiankun 基座内（决定传送门「进入」是否跳转 2D） */
  poweredByQiankun: boolean;
  /** 跳转到 2D 子应用路由（qiankun 劫持 pushState 触发 activeRule 切换） */
  navigate: (route: string) => void;
  /** 读取 accessToken（基座经 qiankun props 注入）；缺省时世界频道只跑本地模拟 */
  getToken?: () => string | null;
  /** 读取权限码（基座经 qiankun props 注入）；缺 sys:agent:chat 时 NPC 只回本地台词 */
  getPermissions?: () => string[] | null;
}

export interface WorldHandle {
  dispose: () => void;
}

export function createWorld(host: HTMLElement, opts: WorldOptions): WorldHandle {
  let disposed = false;
  let rafId = 0;
  const cleanups: Array<() => void> = [];
  const on = (t: EventTarget, k: string, f: EventListener) => {
    t.addEventListener(k, f);
    cleanups.push(() => t.removeEventListener(k, f));
  };
  /* ==========================================================================
   * 蓝鲸城 BlueWhale City — 3D 主场景概念原型
   * 纯程序化生成：地形 / 水系 / 城墙 / 宫殿 / 植被 / 昼夜 / 天气 / 传送门 / 小鲸宝
   * 运行于 three.js r169（three 源码内联在同一模块作用域，无外部依赖）
   * ========================================================================== */

  const clock = { t: 0, dt: 0, last: performance.now() };
  const Y_AXIS = new THREE.Vector3(0, 1, 0);
  const clamp = (v, a, b) => Math.min(b, Math.max(a, v));
  const smooth = (x, a, b) => {
    const t = clamp((x - a) / (b - a), 0, 1);
    return t * t * (3 - 2 * t);
  };
  const lerp = (a, b, t) => a + (b - a) * t;

  /* ------------------------------------------------------------------ 世界状态 */
  const world = {
    time: 0.3, // 0..1 一天（0=子夜 0.5=正午）
    daySpeed: 1 / 150, // 一整天 150 秒
    paused: false,
    season: 2, // 0 春 1 夏 2 秋 3 冬
    weather: 0, // 0 晴 1 雨 2 雪 3 雾
    weatherMix: [1, 0, 0, 0],
    sunDir: new THREE.Vector3(0, 1, 0),
  };

  /* ------------------------------------------------------------------ 渲染基础 */
  const scene = new THREE.Scene();
  scene.fog = new THREE.FogExp2(0x0c1a26, 0.0022);

  const camera = new THREE.PerspectiveCamera(52, innerWidth / innerHeight, 0.4, 4000);

  const renderer = new THREE.WebGLRenderer({
    antialias: true,
    powerPreference: 'high-performance',
  });
  renderer.setPixelRatio(Math.min(devicePixelRatio || 1, 2));
  renderer.setSize(innerWidth, innerHeight);
  renderer.shadowMap.enabled = true;
  renderer.shadowMap.type = THREE.PCFSoftShadowMap;
  renderer.toneMapping = THREE.ACESFilmicToneMapping;
  renderer.toneMappingExposure = 1.02;
  host.appendChild(renderer.domElement);

  /* ==========================================================================
   * 渲染增强底座（自 whale-showcase 移植，2026-11-20 批1）：IBL + 后处理 + 画质档
   *
   * <p>three 的 toneMapping 只在渲染到画布时生效，渲染到 RenderTarget 时是空操作；
   * 后处理链的 OutputPass 统一做 ACES——所以直渲（low 档）与 composer 两条路径
   * 都只有一次色调映射，不会双重映射。renderer.toneMapping 保持原值两条路径共用。</p>
   *
   * <p>e2e 在 SwiftShader 软渲染下跑 WebGL：PMREM 与多 pass 后处理在软渲染下
   * 又慢又费，检测到软渲染强制 low 档（直渲 + 不烘 IBL）——e2e 断言只依赖
   * DOM/探针，不依赖像素，降级不影响冒烟。</p>
   * ========================================================================== */
  type Quality = 'high' | 'mid' | 'low';
  /* mid 不开 SMAA（两遍全屏 pass 拖 ~4fps），抗锯齿交给 low 之外共有的 canvas MSAA
   * 之外的 bloom 半分辨率模糊天然柔化；SMAA 只留给 high。 */
  const QUALITY: Record<Quality, { dpr: number; aa: boolean }> = {
    high: { dpr: 2, aa: true },
    mid: { dpr: 1, aa: false },
    low: { dpr: 1, aa: false },
  };
  const gl = renderer.getContext();
  const dbgInfo = gl.getExtension('WEBGL_debug_renderer_info');
  const gpuName = dbgInfo ? String(gl.getParameter(dbgInfo.UNMASKED_RENDERER_WEBGL)) : '';
  const isSoftwareGpu = /swiftshader|llvmpipe|softpipe|software/i.test(gpuName);
  let quality: Quality = isSoftwareGpu ? 'low' : 'mid';

  /* 调试自由漫游（仅 ?debug=1 暴露）：默认玩家被钳在 OUTER-4 城内 —— 城外河道、
   * 门楼外侧的机位拍不到。截图评审 / 探针需要出城时必须显式打开，否则会被
   * 每帧 clamp 拖回城里（批3 踩过）。 */
  let freeRoam = false;

  const postfx: PostFx = createPostFx(renderer, scene, camera, innerWidth, innerHeight);
  postfx.setAntialias(QUALITY[quality].aa);

  let env: SceneEnv | null = null;
  let envIntensity = 0.85;
  if (quality !== 'low') {
    env = createSceneEnv(renderer);
    scene.environment = env.texture;
    scene.environmentIntensity = envIntensity;
  }
  /* 低画质（软渲染）env 为 null：半球光 + 平行光回退到移植前的光照结构 */

  function setQuality(q: Quality) {
    quality = q;
    const cfg = QUALITY[q];
    renderer.setPixelRatio(Math.min(devicePixelRatio || 1, cfg.dpr));
    renderer.setSize(innerWidth, innerHeight);
    postfx.setAntialias(cfg.aa);
    postfx.setSize(innerWidth, innerHeight);
  }

  /* 后处理昼夜风味：值在 updateSky 末尾逐帧写入（复用预分配对象，避免每帧 new） */
  const gradeShadowTint = new THREE.Color(0x1a2440);
  const gradeHighTint = new THREE.Color(0xffd2a0);

  /* env 烘焙节流：连续昼夜 150s/天，PMREM 每帧烘不可能——每 6s 一次（一天 25 次），
   * 太阳走约 14°，环境光随昼夜平滑过渡足够。SkyState 对象复用不重建。 */
  const ENV_INTERVAL = 6;
  let lastEnvAt = -1e9;
  const envState: SkyState = {
    top: new THREE.Color(),
    horizon: new THREE.Color(),
    ground: new THREE.Color(),
    sunDir: new THREE.Vector3(),
    sunColor: new THREE.Color(),
    sunIntensity: 1,
    haze: 0,
    stars: 0,
  };
  const ENV_MOON_COLOR = new THREE.Color(0x9fb2ff);
  /* 云受光/背光色的逐帧计算底稿（getHex 后写进 envState，避免每帧 new Color） */
  const cloudLightCol = new THREE.Color();
  const cloudShadeCol = new THREE.Color();

  /* 工具栏、HUD、各种面板都是 #bww-scene 的**兄弟节点**（React 里同一层渲染），
   * 不在 host 里面。所有按 data-action 找按钮的代码必须从页面根查，
   * 否则 querySelectorAll 恒返回空——按钮看着在、点了没反应，而且不报任何错。 */
  const uiRoot = host.closest('.bww-page') ?? host.parentElement ?? host;

  /* ------------------------------------------------------------------ 屏风系统（S2）
   * 三层渲染共用一台相机，自下而上：
   *   WebGL 主画布（城池）→ CSS3D 层（屏风/真 DOM）→ 遮挡遮罩（只画会挡住屏风的实体）
   * 屏风不是贴图，是真 DOM：字是矢量的、输入框是真输入框、滚动是真滚动。
   *
   * 容器节点由 App.tsx 渲染；万一缺了（例如被基座裁剪），这里兜底自建，
   * 宁可位置可能不完美，也不要整块屏风系统静默失效。 */
  const layerRoot = (id, cls) => {
    let el = document.getElementById(id);
    if (!el) {
      el = document.createElement('div');
      el.id = id;
      el.className = cls;
      uiRoot.appendChild(el);
    }
    return el;
  };

  /** 屏风接管相机期间，DOM 里有输入框聚焦时玩家不该继续走 */
  let domFocused = false;
  /** 每帧算出的"脚下最近的屏风"，供 E 键与提示条复用 */
  let nearbyScreen = null;
  /** 每帧算出的"脚下最近的传送门"与距离（提示条按优先级裁决，见 updateInteractionHint） */
  let nearestPortal = null;
  let nearestPortalDist = Infinity;
  /** 每帧算出的"脚下最近的城门楼"（S5/T5.10）：只有地标，没有内页 */
  let nearbyGate: NearestGate | null = null;
  /** 上一帧跟随相机的位姿：退出屏风时插值回这里，不用重算一遍避让逻辑 */
  const followPos = new THREE.Vector3();
  const followTarget = new THREE.Vector3();

  const screens: ScreenLayerHandle = createScreenLayer({
    host,
    css3dRoot: layerRoot('bww-css3d', 'bww-css3d-root'),
    occluderRoot: layerRoot('bww-occluder', 'bww-occluder-root'),
    camera,
    scene,
    onEngagedChange: (id) => {
      /* 进殿后收起城里的操作提示与工具栏——由 screen.css 里
         body[data-screen-engaged] 统一控制，一条选择器就够。
         这里不再弹 toast：屏风脚注已经写明了怎么退出，中间再浮一个提示
         正好压在屏风标题上，是纯干扰。 */
      void id;
    },
    onDomFocus: (focused) => {
      domFocused = focused;
    },
    getFollowPose: () => ({
      position: followPos.clone(),
      target: followTarget.clone(),
    }),
  });

  const hemi = new THREE.HemisphereLight(0x9fc7ff, 0x2c2a20, 0.2);
  scene.add(hemi);

  const sunLight = new THREE.DirectionalLight(0xfff0cf, 2.1);
  sunLight.castShadow = true;
  sunLight.shadow.mapSize.set(2048, 2048);
  /* 阴影盒从 ±130 收紧到 ±44 并逐帧跟随玩家（showcase 口径）：盒不变时 texel
   * 约 13cm，桥板/瓦垄全是自阴影网格；±44 跟随后 texel 约 4.3cm。范围只罩
   * 玩家周边，NPC 稍远丢影可接受（游戏相机永远盯着玩家）。 */
  sunLight.shadow.camera.left = -44;
  sunLight.shadow.camera.right = 44;
  sunLight.shadow.camera.top = 44;
  sunLight.shadow.camera.bottom = -44;
  sunLight.shadow.camera.near = 1;
  sunLight.shadow.camera.far = 800;
  sunLight.shadow.bias = -0.0008;
  sunLight.shadow.normalBias = 1.2;
  scene.add(sunLight);
  scene.add(sunLight.target);

  const moonLight = new THREE.DirectionalLight(0x88a6ff, 0.0);
  scene.add(moonLight);

  /* ------------------------------------------------------------------ 材质工厂 */
  const M = (color, opts = {}) =>
    new THREE.MeshStandardMaterial({
      color,
      roughness: opts.roughness ?? 0.88,
      metalness: opts.metalness ?? 0.02,
      emissive: opts.emissive ?? 0x000000,
      emissiveIntensity: opts.emissiveIntensity ?? 1,
      transparent: opts.transparent ?? false,
      opacity: opts.opacity ?? 1,
      side: opts.side ?? THREE.FrontSide,
      flatShading: opts.flat ?? false,
    });

  const matWall = M(0x8b8478, { roughness: 0.95 });
  const matWallDark = M(0x6f695f, { roughness: 0.95 });
  const matStone = M(0xd6cfbd, { roughness: 0.85 });
  /* 门洞抱框/台明用的整石：比 matStone 暗两档。matStone 是给压顶、栏板这类小
   * 构件做高光用的，整面用它在正午 IBL 下白得刺眼（批4 首轮实拍）。 */
  const matAshlar = M(0xb9b09b, { roughness: 0.9 });
  const matRoad = M(0x9a9384, { roughness: 0.98 });
  const matRed = M(0x8f3a2c, { roughness: 0.8 });
  const matRedDark = M(0x6b2a20, { roughness: 0.85 });
  const matGold = M(0xc9a13a, { roughness: 0.45, metalness: 0.55 });
  const matWood = M(0x5d3a25, { roughness: 0.9 });
  const matLantern = new THREE.MeshBasicMaterial({ color: 0xff5a3c });
  const matLanternGlow = new THREE.MeshBasicMaterial({
    color: 0xff6a3c,
    transparent: true,
    opacity: 0.16,
    blending: THREE.AdditiveBlending,
    depthWrite: false,
  });

  /* ---- 材质贴图（视觉合并批2，自 showcase 移植）----
   * 纯色 MeshStandardMaterial 在 IBL+ACES 下大面积墙面是一块发光板：图不承担
   * 颜色，只做 ±8% 的雨痕/霉斑/颗粒调制，颜色仍由材质 baseColor 给。
   * matRoad 不挂图：环路 RingGeometry 的 UV 是圆盘映射，贴上去成放射状拉花。
   * low 档（软渲染 e2e）不挂：512²×3 张 anisotropy=8 的贴图在 SwiftShader 下
   * 拖帧率，寻路/导览按帧推进会卡——e2e 断言不依赖像素，无图不影响冒烟。 */
  if (quality !== 'low') {
    applyBrickMap(matWall);
    applyBrickMap(matWallDark);
    applyStoneMap(matStone);
    applyPlasterMap(matRed);
    applyPlasterMap(matRedDark);
  }

  /* BoxGeometry 六面 UV 按米缩放：默认 UV 每面 0..1，一张 4m 砖纹贴到 40m 墙
   * 会被拉伸成「墙纸」。面序 +x,-x,+y,-y,+z,-z，每面 4 顶点。 */
  const TEX_METERS = 4.0;
  function scaleBoxUV(geo, w, h, d, meters = TEX_METERS) {
    const uv = geo.attributes.uv;
    if (!uv || uv.count !== 24) return;
    const faceScale = [
      [d / meters, h / meters],
      [d / meters, h / meters],
      [w / meters, d / meters],
      [w / meters, d / meters],
      [w / meters, h / meters],
      [w / meters, h / meters],
    ];
    for (let f = 0; f < 6; f++) {
      const su = faceScale[f][0];
      const sv = faceScale[f][1];
      for (let i = 0; i < 4; i++) {
        const k = f * 4 + i;
        uv.setXY(k, uv.getX(k) * su, uv.getY(k) * sv);
      }
    }
    uv.needsUpdate = true;
  }

  function box(w, h, d, mat, cast = true) {
    const geo = new THREE.BoxGeometry(w, h, d);
    scaleBoxUV(geo, w, h, d);
    const m = new THREE.Mesh(geo, mat);
    m.castShadow = cast;
    m.receiveShadow = true;
    return m;
  }

  /* ==========================================================================
   * 1. 地形 —— 按「虚拟都城（圣京）分层制作参考」规格重塑城外（视觉层）
   * 五步工序：① 起南岭与东山丘陵 → ② 塑中央平原与台地 → ③ 做北江与支流凹陷
   *           → ④ 分植被遮罩 → ⑤ 放置城池与关隘（⑤即既有蓝鲸城，见 §4+）
   * 高度层：江面 0 / 平原 -3~+6 / 台地(城内) 恒 0 / 丘陵 6~18 / 南岭主脉 14~46
   * 水系层：北江沿北部平原东西向，「江湾」在城东北向城凸；三条支流自南岭垭口向北汇流
   * 地貌层：南岭主脉 / 东山丘陵（东关垭口）/ 江北冲积平原 / 河湾阶地 / 滩湿地
   * 坐标约定与城图一致：-z 为北、+z 为南、+x 为东。
   * 红线：城内（max(|x|,|z|) < 96）terrainHeight 恒为 0 —— 落点拾取/角色/相机
   * 都依赖 y=0 平面，改地形只许动城外权重 out 的部分。
   * ========================================================================== */

  // 高斯权重（江湾凸出、东关垭口豁口用）
  const gauss = (t) => Math.exp(-t * t);

  // 北江：沿北部平原东西向；x≈130 处向城凸出成「江湾」（仍离外墙 ~54m）
  const NORTH_RIVER = (x) => -200 + Math.sin(x * 0.011) * 16 + 34 * gauss((x - 130) / 85);

  // 支流：自南岭垭口向北汇入北江（中线 x 随 z 变化；joinZ 为汇入口纬度）。
  // 红线：城占 x∈[-92,92]，支流中线必须 |x|≥150，否则河道会贴/穿城墙。
  const TRIBS = [
    { x: (z) => -168 + Math.sin(z * 0.021) * 12, joinZ: -186 }, // 西支·玉溪
    { x: (z) => 155 + Math.sin(z * 0.017) * 10, joinZ: -153 }, // 中支·清溪（城东南侧；joinZ=主河在中线处的实算 z，勿拍脑袋定）
    { x: (z) => 226 + Math.sin(z * 0.024) * 14, joinZ: -174 }, // 东支·云川
  ];

  /** 到任意河道的平面距离（河谷切槽 / 植被避让 / 滩涂遮罩共用） */
  function riverDist(x, z) {
    let d = Math.abs(z - NORTH_RIVER(x));
    for (const t of TRIBS) {
      if (z > t.joinZ - 20) d = Math.min(d, Math.abs(x - t.x(z)));
    }
    return d;
  }

  function terrainHeight(x, z) {
    const d = Math.max(Math.abs(x), Math.abs(z));
    const out = smooth(d, 96, 150); // 城外权重：城内恒 0
    if (out <= 0) return 0;
    let h = 0;

    // ② 中央平原与台地：城建于台地（城内 0），四周冲积平原略低并带田块微起伏
    const plain =
      -1.2 + Math.sin(x * 0.05) * Math.cos(z * 0.043) * 1.1 + Math.sin(x * 0.013 - z * 0.017) * 1.4;
    h += out * plain;

    // ① 南岭主脉（+z 南缘）：分形山脊，越靠南越高；主脊走向沿 x 起伏
    const southW = smooth(z, 150, 320) * out;
    const ridge = 1 - Math.abs(Math.sin(x * 0.016 + 2.1) * 0.72 + Math.sin(x * 0.041 + z * 0.013) * 0.28);
    h += southW * (14 + ridge * ridge * 26 + Math.sin(x * 0.05 + z * 0.037) * 3.2);

    // ① 东山丘陵（+x 东缘）：中低丘；z≈-30 处按规格留「东关」垭口豁口
    const eastW = smooth(x, 170, 320) * out * (1 - 0.75 * gauss((z + 30) / 55));
    const ridgeE = 1 - Math.abs(Math.sin(z * 0.019 + 0.7) * 0.65 + Math.sin(x * 0.031 + z * 0.021) * 0.35);
    h += eastW * (6 + ridgeE * ridgeE * 12 + Math.cos(x * 0.045) * 2.4);

    // 西缘缓丘：衔接南岭与北部平原，高度克制（河湾阶地观感）
    const westW = smooth(-x, 210, 360) * out;
    h += westW * (4 + Math.sin(x * 0.024 - 1.2) * Math.cos(z * 0.02) * 5);

    // ③ 河谷切槽：北江主槽（深 13）+ 支流槽（愈近汇入口愈深；槽口与水带起点对齐）
    const rmask = 1 - smooth(riverDist(x, z), 8, 34);
    h -= rmask * 13 * out;
    for (const t of TRIBS) {
      if (z < t.joinZ - 30) continue;
      const tmask = 1 - smooth(Math.abs(x - t.x(z)), 4, 14);
      if (tmask <= 0) continue;
      const deep = 1 - smooth(z, t.joinZ, t.joinZ + 130); // 近汇入口更深
      h -= tmask * (5 + 7 * deep) * out;
    }
    return h;
  }

  /** 脚下是什么地面：只影响脚步音色，判错也不影响玩法，故用最省事的启发式。
   *  玩家被限在城墙内（OUTER - 4 = 106 米），够不到 132 米外的河，所以没有"涉水"这一档。
   *  城内也不是一整块铺装：中轴御道与环路是石板，其余是夯土与草地。 */
  function groundAt(x: number, z: number): GroundKind {
    if (Math.abs(x) < 7) return 'stone'; // 御道
    const r = Math.sqrt(x * x + z * z);
    if (Math.abs(r - CITY_GEOMETRY.ringRoadRadius) < 6) return 'stone'; // 环路
    return 'grass';
  }

  const GROUND_SIZE = 760;
  const groundGeo = new THREE.PlaneGeometry(GROUND_SIZE, GROUND_SIZE, 200, 200);
  groundGeo.rotateX(-Math.PI / 2);
  {
    const pos = groundGeo.attributes.position;
    const colors = new Float32Array(pos.count * 3);
    const cGrassA = new THREE.Color(0x47632f);
    const cGrassB = new THREE.Color(0x577039);
    const cRock = new THREE.Color(0x6c665a);
    const cRockHi = new THREE.Color(0x8c8578);
    const cSnow = new THREE.Color(0xdfe6ea);
    const cMud = new THREE.Color(0x6d6247);
    const cWet = new THREE.Color(0x4f5a38); // 滩湿地
    const cFarmA = new THREE.Color(0x8a9448); // 平原草地农田·两种田块色
    const cFarmB = new THREE.Color(0x6e7d3c);
    const tmpC = new THREE.Color();
    for (let i = 0; i < pos.count; i++) {
      const x = pos.getX(i);
      const z = pos.getZ(i);
      const h = terrainHeight(x, z);
      pos.setY(i, h);
      const noise = (Math.sin(x * 0.21) * Math.cos(z * 0.19) + 1) * 0.5;
      tmpC.copy(cGrassA).lerp(cGrassB, noise);
      // ④ 植被遮罩：平原田块（16m 网格伪随机取色，只在离河较远的低地）
      const field = Math.sin(Math.floor(x / 16) * 12.9898 + Math.floor(z / 16) * 78.233) * 43758.5453;
      const fpatch = field - Math.floor(field);
      if (h > -3 && h < 6 && riverDist(x, z) > 40) {
        tmpC.lerp(fpatch > 0.5 ? cFarmA : cFarmB, 0.55 * (1 - smooth(h, 4, 8)));
      }
      // 滩湿地：河道两侧的低洼带
      if (h < 0.6 && riverDist(x, z) < 46) {
        tmpC.lerp(cWet, (1 - smooth(riverDist(x, z), 30, 46)) * smooth(-h + 0.6, 0, 2.5) * 0.8);
      }
      if (h < -2) tmpC.lerp(cMud, smooth(-h, 2, 9));
      if (h > 8) tmpC.lerp(cRock, smooth(h, 8, 24));
      if (h > 24) tmpC.lerp(cRockHi, smooth(h, 24, 34));
      if (h > 34) tmpC.lerp(cRockHi, smooth(h, 34, 44));
      if (h > 38) tmpC.lerp(cSnow, smooth(h, 38, 46)); // 南岭雪线（仅最高峰）
      colors[i * 3] = tmpC.r;
      colors[i * 3 + 1] = tmpC.g;
      colors[i * 3 + 2] = tmpC.b;
    }
    groundGeo.setAttribute('color', new THREE.BufferAttribute(colors, 3));
    groundGeo.computeVertexNormals();
  }
  const groundMat = M(0xffffff, { roughness: 0.97 });
  groundMat.vertexColors = true;
  const ground = new THREE.Mesh(groundGeo, groundMat);
  ground.receiveShadow = true;
  scene.add(ground);

  /* ==========================================================================
   * 2. 水系
   * ========================================================================== */
  const waterUniforms = {
    uTime: { value: 0 },
    uDeep: { value: new THREE.Color(0x123a4a) },
    uShallow: { value: new THREE.Color(0x3f7c8c) },
    uSky: { value: new THREE.Color(0x9fd6f0) },
    /* 批3：太阳碎金与涟漪源（rich 档用；simple 档不采这些 uniform，无开销） */
    uSunDir: { value: new THREE.Vector3(0, 1, 0) },
    uSunColor: { value: new THREE.Color(0xfff2d0) },
    uSunIntensity: { value: 1 },
    /** 涟漪源（showcase S32 同款）：北江江湾顶点 / 清溪汇口 / 玉溪中游，
     *  z = 周期相位（0~1 错开，三处不同时起环）。 */
    uRipples: {
      value: [
        new THREE.Vector3(130, -151, 0.0),
        new THREE.Vector3(150, -155, 0.37),
        new THREE.Vector3(-178, -100, 0.71),
      ],
    },
  };

  const waterVert = /* glsl */ `
    uniform float uTime;
    varying vec2 vUv;
    varying vec3 vN;
    varying vec3 vW;
    void main() {
      vUv = uv;
      vec3 p = position;
      float w = sin(p.x * 0.12 + uTime * 1.1) * 0.32
              + sin(p.y * 0.17 - uTime * 0.8) * 0.26
              + sin((p.x + p.y) * 0.06 + uTime * 0.5) * 0.4;
      p.z += w;
      vN = normalize(normalMatrix * vec3(0.0, 0.0, 1.0));
      vec4 wp = modelMatrix * vec4(p, 1.0);
      vW = wp.xyz;
      gl_Position = projectionMatrix * viewMatrix * wp;
    }
  `;

  /* low 档简化片元 = 移植前原样。批2 教训：SwiftShader 软渲染下一切 GPU 加法
   * 都要单独验——噪声法线（4 次采样×3 层）+ 涟漪循环是数倍 ALU，e2e 跑不动。 */
  const waterFragSimple = /* glsl */ `
    uniform float uTime;
    uniform vec3 uDeep;
    uniform vec3 uShallow;
    uniform vec3 uSky;
    varying vec2 vUv;
    varying vec3 vN;
    varying vec3 vW;
    void main() {
      vec3 viewDir = normalize(cameraPosition - vW);
      float fres = pow(1.0 - clamp(dot(vec3(0.0, 1.0, 0.0), viewDir), 0.0, 1.0), 2.5);
      float ripple = sin(vW.x * 1.7 + uTime * 2.0) * 0.5 + 0.5;
      ripple *= sin(vW.z * 2.1 - uTime * 1.6) * 0.5 + 0.5;
      vec3 col = mix(uDeep, uShallow, fres * 0.85);
      col = mix(col, uSky, fres * 0.45);
      col += vec3(0.85, 0.93, 1.0) * pow(ripple, 8.0) * 0.35;
      float alpha = mix(0.72, 0.94, fres);
      gl_FragColor = vec4(col, alpha);
    }
  `;

  /* rich 片元（mid/high）：自 showcase water.ts 移植裁剪——保留「值噪声三层波场
   * → 解析法线 + 太阳碎金 + 涟漪环带」三件套；env 探针/灯影/河床剖面不搬
   * （蓝鲸城水面是半透明条带、无码头灯组、无探针预算）。
   * 值噪声而不是正弦叠加：三条相近频率的正弦必然拍出周期编织纹（像木地板）。 */
  const waterFragRich = /* glsl */ `
    uniform float uTime;
    uniform vec3 uDeep;
    uniform vec3 uShallow;
    uniform vec3 uSky;
    uniform vec3 uSunDir;
    uniform vec3 uSunColor;
    uniform float uSunIntensity;
    uniform vec3 uRipples[3];
    varying vec2 vUv;
    varying vec3 vN;
    varying vec3 vW;

    float hash21(vec2 p) {
      p = fract(p * vec2(123.34, 456.21));
      p += dot(p, p + 45.32);
      return fract(p.x * p.y);
    }

    /* 值噪声（双线性 + 三次平滑） */
    float vnoise(vec2 p) {
      vec2 i = floor(p);
      vec2 f = fract(p);
      vec2 u = f * f * (3.0 - 2.0 * f);
      float a = hash21(i);
      float b = hash21(i + vec2(1.0, 0.0));
      float c = hash21(i + vec2(0.0, 1.0));
      float d = hash21(i + vec2(1.0, 1.0));
      return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
    }

    /* 波纹高度场：x 压缩、z 拉长——反射高光是顺河向的碎金，不是满屏方格 */
    float surface(vec2 p, float t) {
      vec2 q = vec2(p.x * 1.10, p.y * 0.30);
      float n = vnoise(q * 1.20 + vec2(t * 0.10, t * 0.34));
      n += vnoise(q * 2.70 - vec2(t * 0.16, -t * 0.52)) * 0.52;
      n += vnoise(q * 6.10 + vec2(-t * 0.28, t * 0.74)) * 0.26;
      return n / 1.78;
    }

    void main() {
      vec3 viewDir = normalize(cameraPosition - vW);
      vec2 p = vW.xz;
      float dist = length(cameraPosition - vW);

      /* 远处必须衰减法线扰动：不衰减则 150m 外每个像素都在噪声格子间跳，
       * 远水面变成一片闪烁白噪点（showcase 实测的"碎冰"）。 */
      float atten = 1.0 / (1.0 + dist * 0.09 + dist * dist * 0.0008);

      float e = 0.42;
      float dhdx = (surface(p + vec2(e, 0.0), uTime) - surface(p - vec2(e, 0.0), uTime)) / (2.0 * e);
      float dhdz = (surface(p + vec2(0.0, e), uTime) - surface(p - vec2(0.0, e), uTime)) / (2.0 * e);

      /* 涟漪扩散：三处固定源各自生一个向外跑的高斯环带。叠加进法线扰动
       * 而不是单独打亮——环带改反射/碎金的方向，才像"水在动"而不是
       * "屏幕上画了个圈"。幅度出生最强、随半径二次衰减（能量摊薄）。 */
      for (int i = 0; i < 3; i++) {
        vec2 rc = uRipples[i].xy;
        vec2 dv = p - rc;
        float d = length(dv);
        if (d > 3.6 || d < 1e-3) continue;
        float life = fract(uTime / 7.5 + uRipples[i].z);
        float r = 0.35 + life * 2.9;
        float sig = 0.30;
        float band = exp(-pow((d - r) / sig, 2.0));
        float amp = (1.0 - life) * (1.0 - life) * 0.24;
        float g = band * (-2.0 * (d - r) / (sig * sig)) * amp;
        dhdx += (dv.x / d) * g;
        dhdz += (dv.y / d) * g;
      }

      float fres = pow(1.0 - clamp(dot(vec3(0.0, 1.0, 0.0), viewDir), 0.0, 1.0), 2.5);
      vec3 col = mix(uDeep, uShallow, fres * 0.85);
      col = mix(col, uSky, fres * 0.45);

      /* 太阳碎金：宽反光打底 + 高次碎金。全强度法线管碎金，夜里 uSunIntensity
       * 0.15 + 月色（与天空 moonK 同判据），白天日路自动变淡蓝月路。 */
      vec3 N = normalize(vec3(-dhdx * 1.55 * atten, 1.0, -dhdz * 1.55 * atten));
      vec3 L = normalize(uSunDir);
      vec3 H = normalize(viewDir + L);
      float nh = max(dot(N, H), 0.0);
      /* 仰角补偿：月路亮度随掠射角变化，低仰角按比例收（showcase S32） */
      float elevK = mix(0.55, 1.0, smoothstep(0.08, 0.45, uSunDir.y));
      col += uSunColor * pow(nh, 26.0) * 0.045 * uSunIntensity;
      col += uSunColor * pow(nh, 420.0) * 3.2 * uSunIntensity * atten * elevK;

      float alpha = mix(0.72, 0.94, fres);
      gl_FragColor = vec4(col, alpha);
    }
  `;

  const waterMat = new THREE.ShaderMaterial({
    uniforms: waterUniforms,
    transparent: true,
    vertexShader: waterVert,
    fragmentShader: quality === 'low' ? waterFragSimple : waterFragRich,
  });
  waterMat.side = THREE.DoubleSide; // 条带按河道走向生成，面朝向不固定

  /** 沿河道中线铺水面条带（替代整块水平面：北江有江湾、支流走向各异）。
   *  centerAt(t) 返回中线点 {x,z}；条带顶点沿切向法线左右展开，恒定高度 y。 */
  function riverRibbon(centerAt, t0, t1, halfWidth, y, steps = 90) {
    const posArr = [];
    const idx = [];
    for (let i = 0; i <= steps; i++) {
      const t = t0 + ((t1 - t0) * i) / steps;
      const c = centerAt(t);
      const a = centerAt(Math.max(t0, t - 1));
      const b = centerAt(Math.min(t1, t + 1));
      let nx = -(b.z - a.z);
      let nz = b.x - a.x;
      const len = Math.hypot(nx, nz) || 1;
      nx /= len;
      nz /= len;
      posArr.push(c.x - nx * halfWidth, y, c.z - nz * halfWidth);
      posArr.push(c.x + nx * halfWidth, y, c.z + nz * halfWidth);
      if (i < steps) {
        const k = i * 2;
        idx.push(k, k + 1, k + 2, k + 1, k + 3, k + 2);
      }
    }
    const geo = new THREE.BufferGeometry();
    geo.setAttribute('position', new THREE.Float32BufferAttribute(posArr, 3));
    geo.setIndex(idx);
    geo.computeVertexNormals();
    const mesh = new THREE.Mesh(geo, waterMat);
    mesh.renderOrder = 1;
    return mesh;
  }

  // 主河·北江（东西向，含江湾段；槽深 13，水面 -4.4 与原河一致）。
  // 半宽 34 与河谷切槽口对齐（切槽 smooth(d,8,34)）：水沿延伸到槽口外被地形
  // 盖住，水线自然落在槽深 = 水面处；半宽 24 时 d∈[24,34] 有一条宽达 10m 的
  // 干河床滩地（批3 评审发现）。
  scene.add(riverRibbon((t) => ({ x: t, z: NORTH_RIVER(t) }), -380, 380, 34, -4.4));
  // 支流（南 → 北汇入北江；水面与主河同高避免汇口台阶，上游没入南岭山体；
  // 半宽 14 与支流槽口 smooth(d,4,14) 对齐，同上防干滩）
  for (const t of TRIBS) {
    scene.add(riverRibbon((s) => ({ x: t.x(s), z: s }), t.joinZ - 22, 300, 14, -4.4, 60));
  }

  /* ==========================================================================
   * 3. 天空 —— showcase 全套（视觉合并批3）
   *
   * 渐变天空、地平线雾带、日盘（limb darkening）、月盘+月海、星空、云层
   * 全在 env-sky 这一个 shader 里——原来的独立 sunMesh/moonMesh/星空 Points
   * 全部下线。与 PMREM env 烘焙共用 skyState（updateSky 里逐帧填），
   * 天空和反射环境光永远同源；月盘出现条件 uSunIntensity<1 与水面月路同判据。
   * ========================================================================== */
  const visibleSky = createSky(1600);
  scene.add(visibleSky.mesh);

  /* ==========================================================================
   * 4. 城墙
   * ========================================================================== */
  const merlonMatrices = [];
  const _q = new THREE.Quaternion();
  const _m4 = new THREE.Matrix4();
  const _v3 = new THREE.Vector3();
  const _s3 = new THREE.Vector3(1, 1, 1);

  /* ==========================================================================
   * 城门楼合批设施（视觉合并批4）
   *
   * <p>六个门楼 × 数十个构件，逐个 mesh add 会白吃上百 draw call；构件几何乘上
   * 门楼世界矩阵后按材质入桶，末尾每桶 mergeGeometries 成 1 个 mesh（8 桶）。
   * 门洞与城墙几何一律不动——collision.ts 的穿门/挡墙断言依赖它们。</p>
   * ========================================================================== */
  const gateGeo: Record<string, THREE.BufferGeometry[]> = {
    wall: [],
    ashlar: [],
    stone: [],
    red: [],
    redDark: [],
    wood: [],
    gold: [],
    board: [],
    roofOuter: [],
    roofInner: [],
  };
  /** 匾额面片（每面一张独立贴图，合不了批）与檐灯世界坐标 */
  const gateSigns: THREE.Mesh[] = [];
  const gateLanternPos: THREE.Vector3[] = [];
  /** 有门名的门楼锚点（x/z + 门外朝向），供"最近门楼"那盏点光逐帧就位 */
  const gateAnchors: { x: number; z: number; yaw: number }[] = [];
  /**
   * 门楼檐灯的实际照明（只此一盏，逐帧移到最近的门楼）。
   *
   * <p>檐灯本身是 MeshBasicMaterial + 溢光壳，只"看着亮"不照别人；四个门外各挂
   * 一盏点光会让所有材质的灯数常量翻倍（实测吃 3~4fps），而四门外相距 180m，
   * 同一时刻只可能靠近一座——一盏跟着最近门楼走，视觉等价、开销一份。</p>
   */
  const gateLight = new THREE.PointLight(0xffa060, 0, 34, 2);
  gateLight.name = 'gate-light';
  scene.add(gateLight);
  const GATE_ID = new THREE.Matrix4();
  const _gm = new THREE.Matrix4();
  const _gl = new THREE.Matrix4();
  const _gq = new THREE.Quaternion();
  const _ge = new THREE.Euler();
  const _gp = new THREE.Vector3();
  const _gone = new THREE.Vector3(1, 1, 1);

  /** 几何按「门楼世界矩阵 × 局部变换」入桶 */
  function gateEmit(bucket, geo, G, px = 0, py = 0, pz = 0, rx = 0, ry = 0, rz = 0) {
    _gq.setFromEuler(_ge.set(rx, ry, rz));
    _gl.compose(_gp.set(px, py, pz), _gq, _gone);
    geo.applyMatrix4(_gm.copy(G).multiply(_gl));
    gateGeo[bucket].push(geo);
  }

  /** 门楼盒体：UV 按米缩放，与 box() 工厂同口径（砖石纹不被拉伸） */
  function gateBox(bucket, G, w, h, d, px, py, pz, rx = 0, ry = 0, rz = 0) {
    const geo = new THREE.BoxGeometry(w, h, d);
    scaleBoxUV(geo, w, h, d);
    gateEmit(bucket, geo, G, px, py, pz, rx, ry, rz);
  }

  function addMerlons(x0, z0, x1, z1, yTop, step = 3.6) {
    const dx = x1 - x0;
    const dz = z1 - z0;
    const len = Math.hypot(dx, dz);
    const n = Math.floor(len / step);
    const yaw = Math.atan2(dx, dz);
    _q.setFromAxisAngle(Y_AXIS, yaw);
    for (let i = 0; i <= n; i++) {
      const t = i / n;
      _v3.set(x0 + dx * t, yTop + 0.75, z0 + dz * t);
      _m4.compose(_v3, _q, _s3);
      merlonMatrices.push(_m4.clone());
    }
  }

  /**
   * 建造一段带门洞的城墙。
   * @param {number} half 半边长
   * @param {number} height 墙高
   * @param {number} thick 墙厚
   * @param {'x'|'z'} along 延伸方向
   * @param {number} sign 位置符号
   */
  function buildWallSide(half, height, thick, along, sign, gateW, gateH, group, frame = true) {
    const len = half * 2;
    const segW = (len - gateW) / 2;
    const yBase = 0;

    for (const s of [-1, 1]) {
      const c = s * (gateW / 2 + segW / 2);
      let m;
      if (along === 'x') {
        m = box(segW, height + 2, thick, matWall);
        m.position.set(c, (height + 2) / 2 - 1, sign * half);
      } else {
        m = box(thick, height + 2, segW, matWall);
        m.position.set(sign * half, (height + 2) / 2 - 1, c);
      }
      group.add(m);
    }

    // 门洞上方过梁
    const lintelH = height - gateH;
    if (lintelH > 1) {
      let m;
      if (along === 'x') {
        m = box(gateW, lintelH, thick, matWall);
        m.position.set(0, gateH + lintelH / 2, sign * half);
      } else {
        m = box(thick, lintelH, gateW, matWall);
        m.position.set(sign * half, gateH + lintelH / 2, 0);
      }
      group.add(m);
    }

    /* 门洞石框（批4）：门额石带 + 两侧抱框，内外两面各一套。纯装饰——洞口
     * 尺寸分毫不动（collision.ts 的穿门断言依赖它）。没有这一圈，穿门时看到的
     * 是墙体截断面：砖纹贴图在截面上是一片横切撕口，很脏。 */
    const fz = thick / 2 + 0.2;
    if (frame) {
      for (const f of [-1, 1]) {
        if (along === 'x') {
          const zf = sign * half + f * fz;
          gateBox('ashlar', GATE_ID, gateW + 1.7, 0.9, 0.4, 0, gateH + 0.45, zf);
          for (const ps of [-1, 1]) {
            gateBox('ashlar', GATE_ID, 0.55, gateH, 0.4, ps * (gateW / 2 + 0.28), gateH / 2, zf);
          }
        } else {
          const xf = sign * half + f * fz;
          gateBox('ashlar', GATE_ID, 0.4, 0.9, gateW + 1.7, xf, gateH + 0.45, 0);
          for (const ps of [-1, 1]) {
            gateBox('ashlar', GATE_ID, 0.4, gateH, 0.55, xf, gateH / 2, ps * (gateW / 2 + 0.28));
          }
        }
      }
    }

    // 垛口
    if (along === 'x') {
      addMerlons(-half, sign * half, -gateW / 2 - 0.5, sign * half, height);
      addMerlons(gateW / 2 + 0.5, sign * half, half, sign * half, height);
    } else {
      addMerlons(sign * half, -half, sign * half, -gateW / 2 - 0.5, height);
      addMerlons(sign * half, gateW / 2 + 0.5, sign * half, half, height);
    }
  }

  /**
   * 城门楼（批4 重做）：城台 + 台明 + 周围廊十二柱 + 填板墙 + 额枋斗拱 + 两坡
   * 瓦顶 + 正脊鸱吻；有门名的再挂两面金字匾额与檐下灯笼。
   *
   * <p>局部 **+Z 就是门外方向**（yaw 把 +Z 转到该门朝向），匾与檐灯都在 ±Z 立面。
   * 几何全部入桶，由末尾统一合批，函数本身不建 mesh。</p>
   *
   * @param roofBucket 'roofOuter' | 'roofInner'（瓦色分桶）
   * @param name 门名（匾额题字）；null 不挂匾也不挂灯（内城门）
   */
  function buildGateTower(x, z, yaw, scale, roofBucket, name) {
    const s = scale;
    const G = new THREE.Matrix4().compose(
      new THREE.Vector3(x, 0, z),
      new THREE.Quaternion().setFromAxisAngle(Y_AXIS, yaw),
      new THREE.Vector3(1, 1, 1),
    );

    /* ---- 城台：外形与旧版一致（20×4×12 @ y=12），只加压顶石与女墙 ---- */
    gateBox('wall', G, 20 * s, 4 * s, 9 * s, 0, 12 * s, 0);
    /* 城台挑檐：城外机位没有阴影贴图（阴影盒只跟玩家 44m），只靠材质差读不出
     * 城台比城墙厚 6m —— 沿底沿加一道出挑的整石，明暗交界自己就把厚度说清了。 */
    gateBox('ashlar', G, 20.6 * s, 0.45 * s, 9.6 * s, 0, 10.1 * s, 0);
    gateBox('ashlar', G, 20.8 * s, 0.36 * s, 9.8 * s, 0, 14.05 * s, 0);
    /* 女墙只做两端：前后檐柱在 z=±4.55，正好压在城台前沿上，前后加墙会插进柱子；
     * 有周围廊的城楼本来也是廊柱临边、只在山面设墙。 */
    for (const sg of [-1, 1]) {
      gateBox('ashlar', G, 0.42 * s, 0.9 * s, 9.6 * s, sg * 10 * s, 14.7 * s, 0);
    }

    /* ---- 城楼：台明 + 填板墙 + 周围廊十二柱（4 角 + 4 前后檐中柱 + 4 山面） ---- */
    gateBox('ashlar', G, 16 * s, 0.5 * s, 10.4 * s, 0, 14.35 * s, 0); // 台明（顶 14.6）
    gateBox('red', G, 14.4 * s, 4.9 * s, 8.4 * s, 0, 17.05 * s, 0);
    const colGeo = () => new THREE.CylinderGeometry(0.25 * s, 0.27 * s, 5.2 * s, 10);
    const column = (cx, cz) => {
      gateBox('ashlar', G, 0.62 * s, 0.34 * s, 0.62 * s, cx * s, 14.77 * s, cz * s); // 柱础
      gateEmit('redDark', colGeo(), G, cx * s, 17.2 * s, cz * s); // 柱顶 19.8
    };
    for (const cx of [-7, -2.6, 2.6, 7]) {
      for (const cz of [-4.55, 4.55]) column(cx, cz);
    }
    for (const cx of [-7, 7]) {
      for (const cz of [-2.4, 2.4]) column(cx, cz);
    }

    /* ---- 板门与直棂窗（填板墙内外两面）---- */
    for (const sg of [-1, 1]) {
      gateBox('wood', G, 3.6 * s, 3.4 * s, 0.22 * s, 0, 16.5 * s, sg * 4.28 * s);
      for (const wx of [-4.8, 4.8]) {
        gateBox('wood', G, 2.2 * s, 1.6 * s, 0.18 * s, wx * s, 17.5 * s, sg * 4.28 * s);
      }
    }

    /* ---- 额枋 + 檐下斗拱示意（短悬臂；只有十来像素，但去掉檐口就"飘"）---- */
    gateBox('redDark', G, 15.2 * s, 0.6 * s, 9.4 * s, 0, 20.1 * s, 0);
    for (let i = -6; i <= 6; i++) {
      for (const sg of [-1, 1]) {
        gateBox('wood', G, 0.34 * s, 0.5 * s, 1.4 * s, i * 1.1 * s, 20.65 * s, sg * 4.9 * s);
      }
    }

    /* ---- 两坡瓦顶：檐口 20.9，坡度 0.33rad（showcase 同口径），正脊 22.95 ---- */
    for (const sg of [-1, 1]) {
      gateBox(roofBucket, G, 17.4 * s, 0.34 * s, 6.2 * s, 0, 21.9 * s, sg * 2.94 * s, sg * 0.33);
    }
    for (const sg of [-1, 1]) {
      gateBox('redDark', G, 17.6 * s, 0.36 * s, 0.3 * s, 0, 20.95 * s, sg * 5.42 * s); // 封檐板
    }
    gateBox('gold', G, 17.8 * s, 0.5 * s, 0.64 * s, 0, 22.95 * s, 0);
    for (const sg of [-1, 1]) {
      gateBox('gold', G, 0.7 * s, 1.4 * s, 0.5 * s, sg * 8.6 * s, 23.6 * s, 0, 0, 0, -sg * 0.3);
    }

    if (!name) return;
    gateAnchors.push({ x, z, yaw });

    /* ---- 匾额：明间（柱 -2.6..2.6）挂 4.2×1.15 漆地，两面各一块 + 金字面片 ---- */
    const bw = 4.2 * s;
    const bh = 1.15 * s;
    const by = 18.6 * s;
    const signW = bw - 0.1 * s;
    const signH = bh - 0.08 * s;
    /* 金字面片在 low 档（SwiftShader，e2e 路径）跳过：一块匾一张 1800×500 的
     * canvas + alphaTest 采样，在软渲染下是纯负担，而 e2e 只断言 DOM/探针。
     * 漆地照建，远看仍是"挂了匾"。 */
    const signMat =
      quality === 'low'
        ? null
        : goldSignMaterial(
            drawGoldPlaqueTexture(name, signW, signH, 0.74 * s),
            `gatePlaque-${name}`,
          );
    const signGeo = signMat ? new THREE.PlaneGeometry(signW, signH) : null;
    for (const sg of [-1, 1]) {
      gateBox('board', G, bw, bh, BOARD_D * s, 0, by, sg * (4.2 * s + (BOARD_D * s) / 2));
      if (!signMat) continue;
      // plane 法线 +Z：+Z 面不转、-Z 面绕 Y 转 π——两面读字都正立不镜像
      const sign = new THREE.Mesh(signGeo, signMat);
      sign.position.set(0, by, sg * (4.2 * s + BOARD_D * s + SIGN_PROUD));
      sign.rotation.y = sg > 0 ? 0 : Math.PI;
      sign.castShadow = false; // 字不投影：alphaTest 镂空的影子是一团噪点
      sign.receiveShadow = true; // 檐口影子落到字上，反而是"匾在檐下"的证据
      sign.updateMatrix();
      gateSigns.push(sign); // 位置在合批末尾统一乘门楼矩阵
      sign.applyMatrix4(G);
    }

    /* ---- 檐下灯笼：额枋外沿吊 3 盏 × 内外两面（复用 matLantern，夜间自动点亮）---- */
    /* 每面 4 盏、避开明间中轴：3 盏时中间那盏正好挂在匾额上沿，溢光壳半径 1.35
     * 会把"南"字糊掉。 */
    for (const lx of [-6.3, -3.3, 3.3, 6.3]) {
      for (const sg of [-1, 1]) {
        const lz = sg * 5.55 * s;
        gateEmit('wood', new THREE.CylinderGeometry(0.04 * s, 0.04 * s, 1.5 * s, 6), G, lx * s, 20.5 * s, lz);
        gateLanternPos.push(new THREE.Vector3(lx * s, 19.3 * s, lz).applyMatrix4(G));
      }
    }
  }

  const cityGroup = new THREE.Group();
  scene.add(cityGroup);

  // 外城（尺度来自 city-layout.json）
  // gateW 取 GATE_HALF × 2：视觉门洞与 collision.ts 的门洞分段必须同宽，
  // 否则会出现「看得见门、走不过去」的经典静默缺陷。
  const OUTER = CITY_GEOMETRY.outerWall;
  buildWallSide(OUTER, 10, 3.2, 'x', -1, GATE_HALF.outer * 2, 9.2, cityGroup);
  buildWallSide(OUTER, 10, 3.2, 'x', 1, GATE_HALF.outer * 2, 9.2, cityGroup);
  buildWallSide(OUTER, 10, 3.2, 'z', -1, GATE_HALF.outer * 2, 9.2, cityGroup);
  buildWallSide(OUTER, 10, 3.2, 'z', 1, GATE_HALF.outer * 2, 9.2, cityGroup);
  /* 四座外城门：指北针以 -Z 为北（小地图口径），故南 +Z、北 -Z、东 +X、西 -X。
   * yaw 必须把门楼局部 +Z 转到"门外"，匾额与檐灯才朝向城外。 */
  buildGateTower(0, OUTER, 0, 1.0, 'roofOuter', '南薰門');
  buildGateTower(0, -OUTER, Math.PI, 1.0, 'roofOuter', '望江門');
  buildGateTower(OUTER, 0, Math.PI / 2, 1.0, 'roofOuter', '朝陽門');
  buildGateTower(-OUTER, 0, -Math.PI / 2, 1.0, 'roofOuter', '鯨津門');

  // 内城
  const INNER = CITY_GEOMETRY.innerWall;
  buildWallSide(INNER, 8, 2.4, 'x', -1, GATE_HALF.inner * 2, 7.2, cityGroup);
  buildWallSide(INNER, 8, 2.4, 'x', 1, GATE_HALF.inner * 2, 7.2, cityGroup);
  buildWallSide(INNER, 8, 2.4, 'z', -1, GATE_HALF.inner * 2, 7.2, cityGroup);
  buildWallSide(INNER, 8, 2.4, 'z', 1, GATE_HALF.inner * 2, 7.2, cityGroup);
  buildGateTower(0, INNER, 0, 0.78, 'roofInner', null);
  buildGateTower(0, -INNER, Math.PI, 0.78, 'roofInner', null);

  /* ---- 门楼合批收口（批4）：每材质桶并成 1 个 mesh ---- */
  {
    const gateMats = {
      wall: matWall,
      ashlar: matAshlar,
      stone: matStone,
      red: matRed,
      redDark: matRedDark,
      wood: matWood,
      gold: matGold,
      board: M(0x2b1f19, { roughness: 0.52, metalness: 0.05 }),
      roofOuter: M(0x2f6b5f, { roughness: 0.55, metalness: 0.18 }),
      roofInner: M(0x3f5f78, { roughness: 0.55, metalness: 0.18 }),
    };
    for (const [key, list] of Object.entries(gateGeo)) {
      if (!list.length) continue;
      const merged = mergeGeometries(list, false);
      list.forEach((geo) => geo.dispose());
      const mesh = new THREE.Mesh(merged, gateMats[key]);
      mesh.name = `gate-${key}`;
      mesh.castShadow = true;
      mesh.receiveShadow = true;
      cityGroup.add(mesh);
    }
    gateSigns.forEach((sign) => cityGroup.add(sign));

    /* 檐下灯笼：24 盏两个 InstancedMesh（灯体 + 溢光壳）。材质复用御道灯笼的
     * matLantern/matLanternGlow——它们每帧按昼夜改色与不透明度，门楼灯自动跟着亮。 */
    if (gateLanternPos.length) {
      const bodyGeo = new THREE.SphereGeometry(0.44, 10, 8);
      bodyGeo.scale(1, 1.18, 1);
      const glowGeo = new THREE.SphereGeometry(1.35, 8, 6);
      const bodies = new THREE.InstancedMesh(bodyGeo, matLantern, gateLanternPos.length);
      const glows = new THREE.InstancedMesh(glowGeo, matLanternGlow, gateLanternPos.length);
      gateLanternPos.forEach((p, i) => {
        _gm.makeTranslation(p.x, p.y, p.z);
        bodies.setMatrixAt(i, _gm);
        glows.setMatrixAt(i, _gm);
      });
      bodies.instanceMatrix.needsUpdate = true;
      glows.instanceMatrix.needsUpdate = true;
      bodies.name = 'gate-lanterns';
      glows.name = 'gate-lantern-glows';
      cityGroup.add(bodies, glows);
    }
  }

  // 垛口合批
  {
    const merlonGeo = new THREE.BoxGeometry(1.7, 1.6, 0.8);
    scaleBoxUV(merlonGeo, 1.7, 1.6, 0.8);
    const merlonMesh = new THREE.InstancedMesh(merlonGeo, matWallDark, merlonMatrices.length);
    merlonMatrices.forEach((m, i) => merlonMesh.setMatrixAt(i, m));
    merlonMesh.instanceMatrix.needsUpdate = true;
    merlonMesh.castShadow = true;
    merlonMesh.receiveShadow = true;
    cityGroup.add(merlonMesh);
  }

  /* ==========================================================================
   * 5. 宫殿
   * ========================================================================== */
  /** 生成匾额贴图 */
  function plaqueTexture(text, bg = '#221a15', fg = '#d8b45a') {
    const c = document.createElement('canvas');
    c.width = 512;
    c.height = 200;
    const ctx = c.getContext('2d');
    ctx.fillStyle = bg;
    ctx.fillRect(0, 0, 512, 200);
    ctx.strokeStyle = fg;
    ctx.lineWidth = 8;
    ctx.strokeRect(12, 12, 488, 176);
    ctx.fillStyle = fg;
    ctx.font = 'bold 96px "Songti SC", "STSong", "SimSun", serif';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(text, 256, 106);
    const tex = new THREE.CanvasTexture(c);
    tex.colorSpace = THREE.SRGBColorSpace;
    tex.anisotropy = 4;
    return tex;
  }

  /**
   * 建造一座宫殿。
   * @param {{x:number,z:number,w:number,d:number,name:string,roof:number,body?:number,yaw?:number,scale?:number}} cfg
   */
  function createPalace(cfg) {
    const { x, z, w, d, name } = cfg;
    const scale = cfg.scale ?? 1;
    const roofColor = cfg.roof ?? 0x2f6b5f;
    const bodyColor = cfg.body ?? 0x8f3a2c;
    const yaw = cfg.yaw ?? 0;

    const g = new THREE.Group();
    g.position.set(x, 0, z);
    g.rotation.y = yaw;

    const baseH = 2.6 * scale;
    const bodyH = 6.4 * scale;

    // 台基
    const base = box((w + 9) * scale, baseH, (d + 9) * scale, matStone);
    base.position.y = baseH / 2;
    g.add(base);

    // 台阶（南向）
    for (let i = 0; i < 4; i++) {
      const st = box(w * 0.42 * scale, 0.5 * scale, (1.5 - i * 0.18) * scale, matStone);
      st.position.set(0, 0.25 + i * 0.55 * scale, ((d + 9) / 2) * scale + (0.75 + i * 0.3) * scale);
      g.add(st);
    }

    // 殿身
    const bodyMat = M(bodyColor, { roughness: 0.85 });
    const body = box(w * scale, bodyH, d * scale, bodyMat);
    body.position.y = baseH + bodyH / 2;
    g.add(body);

    // 廊柱
    const colMat = M(0x6d2a20, { roughness: 0.8 });
    const colGeo = new THREE.CylinderGeometry(0.42 * scale, 0.46 * scale, bodyH, 10);
    const front = Math.round(w / 3);
    for (let i = 0; i < front; i++) {
      const cx = (-w / 2 + (i * w) / (front - 1)) * scale;
      for (const face of [-1, 1]) {
        const col = new THREE.Mesh(colGeo, colMat);
        col.position.set(cx, baseH + bodyH / 2, face * (d / 2 + 0.75) * scale);
        col.castShadow = true;
        g.add(col);
      }
    }

    // 门窗
    const doorMat = M(0x2a1c14, { roughness: 0.7, emissive: 0x2a1408, emissiveIntensity: 0.5 });
    const doors = box(w * 0.5 * scale, bodyH * 0.62, 0.25, doorMat);
    doors.position.set(0, baseH + bodyH * 0.32, (d / 2) * scale + 0.15);
    g.add(doors);

    // 重檐屋顶
    const eaveB = (w + 12) * scale;
    const eaveT = (w + 2) * scale;
    const lower = new THREE.Mesh(
      new THREE.CylinderGeometry(eaveT / Math.SQRT2, eaveB / Math.SQRT2, 2.3 * scale, 4),
      M(roofColor, { roughness: 0.55, metalness: 0.18, flat: false }),
    );
    lower.rotation.y = Math.PI / 4;
    lower.position.y = baseH + bodyH + 1.15 * scale;
    lower.castShadow = true;
    lower.receiveShadow = true;
    g.add(lower);

    const upper = new THREE.Mesh(
      new THREE.CylinderGeometry(
        (w * 0.45 * scale) / Math.SQRT2,
        (eaveT * 0.86) / Math.SQRT2,
        2.9 * scale,
        4,
      ),
      M(roofColor, { roughness: 0.5, metalness: 0.2 }),
    );
    upper.rotation.y = Math.PI / 4;
    upper.position.y = baseH + bodyH + 3.5 * scale;
    upper.castShadow = true;
    g.add(upper);

    // 正脊 + 鸱吻
    const ridge = box(eaveT * 0.62, 0.62 * scale, 0.62 * scale, matGold);
    ridge.position.y = baseH + bodyH + 5.05 * scale;
    g.add(ridge);
    for (const s of [-1, 1]) {
      const kiss = box(0.8 * scale, 1.5 * scale, 0.5 * scale, matGold);
      kiss.position.set(s * eaveT * 0.31, baseH + bodyH + 5.65 * scale, 0);
      kiss.rotation.z = s * 0.32;
      g.add(kiss);
    }

    // 匾额
    const plaque = new THREE.Mesh(
      new THREE.PlaneGeometry(5.4 * scale, 2.1 * scale),
      new THREE.MeshBasicMaterial({ map: plaqueTexture(name), toneMapped: false }),
    );
    plaque.position.set(0, baseH + bodyH * 0.82, (d / 2) * scale + 0.32);
    g.add(plaque);

    return g;
  }

  /* ---- 皇城 ---- */
  const palaceGroup = new THREE.Group();
  scene.add(palaceGroup);

  /* ---- 内城十二坊的坊墙（单独成组，便于 S8 按距离整组裁剪） ---- */
  const districtGroup = new THREE.Group();
  scene.add(districtGroup);

  /** 计算宫殿朝向：面向城池中心 */
  const faceCenter = (x, z) => Math.atan2(-x, -z);

  // 皇城墙
  {
    const R = CITY_GEOMETRY.royalWall;
    buildWallSide(R, 12, 2.6, 'x', -1, GATE_HALF.royal * 2, 8.5, palaceGroup, false);
    buildWallSide(R, 12, 2.6, 'x', 1, GATE_HALF.royal * 2, 8.5, palaceGroup, false);
    buildWallSide(R, 12, 2.6, 'z', -1, GATE_HALF.royal * 2, 8.5, palaceGroup, false);
    buildWallSide(R, 12, 2.6, 'z', 1, GATE_HALF.royal * 2, 8.5, palaceGroup, false);
  }

  // 主殿：数枢殿 → 核心数据（参数来自 city-layout.json）
  const mainHall = createPalace({
    x: MAIN_HALL.x,
    z: MAIN_HALL.z,
    w: MAIN_HALL.w,
    d: MAIN_HALL.d,
    name: MAIN_HALL.name,
    roof: MAIN_HALL.roof,
    body: MAIN_HALL.body,
    scale: MAIN_HALL.scale,
    yaw: MAIN_HALL.yaw,
  });
  palaceGroup.add(mainHall);

  // 皇城内配殿
  SIDE_HALLS.forEach((hall) => {
    palaceGroup.add(
      createPalace({
        x: hall.x,
        z: hall.z,
        w: hall.w,
        d: hall.d,
        name: hall.name,
        roof: hall.roof,
        body: hall.body,
        scale: hall.scale,
        yaw: hall.yaw,
      }),
    );
  });

  /* ---- 内城十二坊（对应业务子系统，坐标与配色来自 city-layout.json） ---- */
  const RING_R = CITY_GEOMETRY.ringRoadRadius;
  const palaces = [];
  PALACES.forEach((p) => {
    const g = createPalace({
      x: p.x,
      z: p.z,
      w: p.w,
      d: p.d,
      name: p.name,
      roof: p.roof,
      body: p.body,
      scale: p.scale,
      yaw: p.yaw,
    });
    palaceGroup.add(g);
    palaces.push({ ...p, group: g });
  });

  /* ---- 坊墙 + 坊门（十二坊各一圈矮墙，朝圆心一侧开坊门挂匾额） ----
   *
   * 坊门只开朝圆心那一面：四个正方位是御道，坊若四面开口，站在环路上看不出
   * 「这是一坊」；只留一个正门，绕城一周时每坊都是"墙—门—墙"的节奏，才有街区感。
   * 墙高 3m 低于宫殿台基（2.6m）+ 殿身，不挡视线，只做分界。
   */
  {
    const WALL_H = DISTRICT_WALL.height;
    const WALL_T = DISTRICT_WALL.thick;
    const GATE_HALF_W = DISTRICT_WALL.gateHalf; // 坊门洞半宽
    const postGeo = new THREE.BoxGeometry(0.9, 6.2, 0.9);
    const lintelGeo = new THREE.BoxGeometry(1, 1, 1); // 下面按坊宽缩放

    DISTRICTS.forEach((dst) => {
      const g = new THREE.Group();
      g.position.set(dst.x, 0, dst.z);
      g.rotation.y = dst.yaw;

      const HW = dst.halfW;
      const HD = dst.halfD;
      const sideW = HW - GATE_HALF_W; // 南面门洞两侧墙段的宽度

      // 北 / 西 / 东 三面整段，南面按门洞切两段
      const segs = [
        { x: 0, z: -HD, w: HW * 2, d: WALL_T },
        { x: -HW, z: 0, w: WALL_T, d: HD * 2 },
        { x: HW, z: 0, w: WALL_T, d: HD * 2 },
        { x: -(GATE_HALF_W + sideW / 2), z: HD, w: sideW, d: WALL_T },
        { x: GATE_HALF_W + sideW / 2, z: HD, w: sideW, d: WALL_T },
      ];
      for (const s of segs) {
        const seg = box(s.w, WALL_H, s.d, matWall);
        seg.position.set(s.x, WALL_H / 2, s.z);
        g.add(seg);
        // 压顶：一道深色薄檐，远看能读出"这是墙不是方块"
        const cap = box(s.w + 0.35, 0.28, s.d + 0.35, matWallDark, false);
        cap.position.set(s.x, WALL_H + 0.14, s.z);
        g.add(cap);
      }

      // 坊门：两根门柱 + 门楣
      for (const sx of [-1, 1]) {
        const post = new THREE.Mesh(postGeo, matStone);
        post.position.set(sx * GATE_HALF_W, 3.1, HD);
        post.castShadow = true;
        post.receiveShadow = true;
        g.add(post);
      }
      const lintel = new THREE.Mesh(lintelGeo, matStone);
      lintel.scale.set(GATE_HALF_W * 2 + 2.0, 0.9, 1.1);
      lintel.position.set(0, 6.75, HD);
      lintel.castShadow = true;
      lintel.receiveShadow = true;
      g.add(lintel);

      // 匾额：挂在门楣下方，朝圆心
      const plaque = new THREE.Mesh(
        new THREE.PlaneGeometry(4.6, 1.7),
        new THREE.MeshBasicMaterial({ map: plaqueTexture(dst.name), transparent: true }),
      );
      plaque.position.set(0, 5.85, HD - 0.6);
      g.add(plaque);

      districtGroup.add(g);
    });
  }

  /* ==========================================================================
   * 6. 道路 / 灯笼 / 传送门
   * ========================================================================== */
  // 环形路：12 坊落在 r=76 的环上，环带宽 22（相邻坊巷约 21m），
  // 路面压在坊台基之下，只露出坊间与坊门前的那一截。
  {
    const ring = new THREE.Mesh(new THREE.RingGeometry(RING_R - 11, RING_R + 11, 96), matRoad);
    ring.rotation.x = -Math.PI / 2;
    ring.position.y = 0.06;
    ring.receiveShadow = true;
    scene.add(ring);

    // 皇城外的一圈前广场环路（贴皇城墙起 8m 宽）
    const plaza = CITY_GEOMETRY.royalWall;
    const innerRing = new THREE.Mesh(new THREE.RingGeometry(plaza + 8, plaza + 16, 72), matRoad);
    innerRing.rotation.x = -Math.PI / 2;
    innerRing.position.y = 0.06;
    innerRing.receiveShadow = true;
    scene.add(innerRing);
  }

  // 中轴御道：Stone 贴图独立材质（matRoad 本体不挂图，环路 RingGeometry UV 是
  // 圆盘映射会拉花）；长度随外城墙数据化（扩容后不能再用硬编码 176），UV 按比例平铺
  {
    const roadLen = (OUTER - 4) * 2;
    const matRoadTex = matRoad.clone();
    if (quality !== 'low') applyStoneMap(matRoadTex);
    const axisGeo = new THREE.PlaneGeometry(13, roadLen);
    const auv = axisGeo.attributes.uv;
    for (let i = 0; i < auv.count; i++) {
      auv.setXY(i, auv.getX(i) * (13 / 4), auv.getY(i) * (roadLen / 4));
    }
    const axis = new THREE.Mesh(axisGeo, matRoadTex);
    axis.rotation.x = -Math.PI / 2;
    axis.position.set(0, 0.07, 4);
    axis.receiveShadow = true;
    scene.add(axis);

    const crossGeo = new THREE.PlaneGeometry(13, roadLen);
    const cuv = crossGeo.attributes.uv;
    for (let i = 0; i < cuv.count; i++) {
      cuv.setXY(i, cuv.getX(i) * (13 / 4), cuv.getY(i) * (roadLen / 4));
    }
    const cross = new THREE.Mesh(crossGeo, matRoadTex);
    cross.rotation.x = -Math.PI / 2;
    cross.rotation.z = Math.PI / 2;
    cross.position.set(0, 0.065, 0);
    cross.receiveShadow = true;
    scene.add(cross);
  }

  // 灯笼：沿御道两侧，从外城南门内缘走到皇城与内城墙之间
  const lanterns = [];
  {
    const poleGeo = new THREE.CylinderGeometry(0.11, 0.15, 4.6, 8);
    const ballGeo = new THREE.SphereGeometry(0.38, 12, 10);
    // 皇城内（含广场）不放；内城墙带（墙心 ±10）也跳过，避免灯柱插在墙上
    const ROYAL_CLEAR = CITY_GEOMETRY.royalWall + 4;
    const INNER_LO = INNER - 10;
    const INNER_HI = INNER + 10;
    const GAP = 16;
    const count = Math.floor((OUTER - 6) * 2 / GAP) + 1;
    for (let i = 0; i < count; i++) {
      const z = OUTER - 6 - i * GAP;
      for (const s of [-1, 1]) {
        const px = s * 7.4;
        const az = Math.abs(z);
        if (az < ROYAL_CLEAR || (az > INNER_LO && az < INNER_HI)) continue;
        const pole = new THREE.Mesh(poleGeo, matWood);
        pole.position.set(px, 2.3, z);
        pole.castShadow = true;
        cityGroup.add(pole);

        const ball = new THREE.Mesh(ballGeo, matLantern);
        ball.position.set(px, 4.85, z);
        cityGroup.add(ball);

        const glow = new THREE.Mesh(new THREE.SphereGeometry(1.15, 12, 10), matLanternGlow);
        glow.position.copy(ball.position);
        cityGroup.add(glow);
        lanterns.push(ball);
      }
    }
  }

  /* ---- 传送门 ---- */
  const PORTAL_UNIFORMS = [];
  function createPortal(x, z, color, yaw) {
    const g = new THREE.Group();
    g.position.set(x, 0, z);
    g.rotation.y = yaw;

    const stone = new THREE.Mesh(new THREE.CylinderGeometry(3.2, 3.6, 0.6, 24), matStone);
    stone.position.y = 0.3;
    stone.receiveShadow = true;
    g.add(stone);

    const ringMat = new THREE.MeshBasicMaterial({ color, toneMapped: false });
    const ring = new THREE.Mesh(new THREE.TorusGeometry(2.9, 0.22, 12, 48), ringMat);
    ring.position.y = 3.2;
    g.add(ring);

    const runiforms = {
      uTime: { value: Math.random() * 10 },
      uColor: { value: new THREE.Color(color) },
    };
    PORTAL_UNIFORMS.push(runiforms);

    const disc = new THREE.Mesh(
      new THREE.CircleGeometry(2.72, 48),
      new THREE.ShaderMaterial({
        uniforms: runiforms,
        transparent: true,
        side: THREE.DoubleSide,
        depthWrite: false,
        blending: THREE.AdditiveBlending,
        vertexShader: /* glsl */ `
        varying vec2 vUv;
        void main() {
          vUv = uv;
          gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
        }
      `,
        fragmentShader: /* glsl */ `
        uniform float uTime;
        uniform vec3 uColor;
        varying vec2 vUv;
        void main() {
          vec2 p = vUv - 0.5;
          float r = length(p) * 2.0;
          float a = atan(p.y, p.x);
          float swirl = sin(a * 5.0 + r * 14.0 - uTime * 2.4) * 0.5 + 0.5;
          float core = smoothstep(1.0, 0.15, r);
          float edge = smoothstep(0.75, 1.0, r);
          float alpha = core * (0.32 + swirl * 0.42) * (1.0 - edge * 0.85);
          vec3 col = uColor * (0.7 + swirl * 0.9);
          gl_FragColor = vec4(col, alpha * 0.85);
        }
      `,
      }),
    );
    disc.position.y = 3.2;
    g.add(disc);

    const glow = new THREE.Mesh(
      new THREE.CircleGeometry(4.2, 32),
      new THREE.MeshBasicMaterial({
        color,
        transparent: true,
        opacity: 0.12,
        blending: THREE.AdditiveBlending,
        depthWrite: false,
        toneMapped: false,
      }),
    );
    glow.position.y = 0.65;
    glow.rotation.x = -Math.PI / 2;
    g.add(glow);

    return { group: g, ring, disc, color };
  }

  const portals = [];

  // 皇城传送门（数枢殿前，坐标来自 city-layout.json 的 portal.main）
  {
    const p = createPortal(MAIN_PORTAL.x, MAIN_PORTAL.z, MAIN_PORTAL.color, 0);
    scene.add(p.group);
    portals.push({
      name: MAIN_PORTAL.name,
      sub: MAIN_PORTAL.sub,
      x: MAIN_PORTAL.x,
      z: MAIN_PORTAL.z,
      route: MAIN_PORTAL.route,
      ...p,
      main: true,
    });
  }

  // 八方宫殿传送门（位置与光色在库里对应 sc_node_world.portal_*）
  palaces.forEach((pal) => {
    const p = createPortal(pal.portal.x, pal.portal.z, pal.portal.color, pal.yaw);
    scene.add(p.group);
    portals.push({
      name: pal.name,
      sub: pal.sub,
      x: pal.portal.x,
      z: pal.portal.z,
      route: pal.route,
      palace: pal,
      ...p,
    });
  });

  /* ==========================================================================
   * 7. 植被
   * ========================================================================== */
  function scatterVegetation() {
    const treeCount = 620;
    const trunkGeo = new THREE.CylinderGeometry(0.24, 0.42, 3.4, 6);
    const crownGeo = new THREE.ConeGeometry(2.5, 7.4, 7);
    const trunkMat = M(0x4a3524, { roughness: 0.95 });
    const crownMat = M(0xffffff, { roughness: 0.92 });

    const trunks = new THREE.InstancedMesh(trunkGeo, trunkMat, treeCount);
    const crowns = new THREE.InstancedMesh(crownGeo, crownMat, treeCount);
    crowns.castShadow = false;
    trunks.castShadow = false;
    crowns.receiveShadow = true;

    const colorSeason = [
      new THREE.Color(0x5f8f42),
      new THREE.Color(0x38632c),
      new THREE.Color(0xb8782a),
      new THREE.Color(0x7d8f7a),
    ];

    let n = 0;
    let guard = 0;
    while (n < treeCount && guard < treeCount * 40) {
      guard++;
      const a = Math.random() * Math.PI * 2;
      const r = 105 + Math.pow(Math.random(), 0.7) * 210;
      const x = Math.cos(a) * r;
      const z = Math.sin(a) * r;
      // 避开全部河道（北江 + 三支流；南岭密林带照常覆盖）
      if (riverDist(x, z) < 22) continue;
      const y = terrainHeight(x, z);
      if (y < -3) continue;
      const s = 0.6 + Math.random() * 0.9;
      _q.setFromAxisAngle(Y_AXIS, Math.random() * Math.PI * 2);
      _v3.set(x, y + 1.7 * s, z);
      _s3.set(s, s, s);
      _m4.compose(_v3, _q, _s3);
      trunks.setMatrixAt(n, _m4);
      _v3.set(x, y + (3.4 + 3.0) * s, z);
      _m4.compose(_v3, _q, _s3);
      crowns.setMatrixAt(n, _m4);

      const tint = colorSeason[world.season]
        .clone()
        .offsetHSL(
          (Math.random() - 0.5) * 0.06,
          (Math.random() - 0.5) * 0.1,
          (Math.random() - 0.5) * 0.12,
        );
      crowns.setColorAt(n, tint);
      n++;
    }
    trunks.count = n;
    crowns.count = n;
    trunks.instanceMatrix.needsUpdate = true;
    crowns.instanceMatrix.needsUpdate = true;
    if (crowns.instanceColor) crowns.instanceColor.needsUpdate = true;
    scene.add(trunks, crowns);
    return { crowns, colorSeason };
  }

  const vegetation = scatterVegetation();

  // 城内草丛（用四棱锥做草簇，任意角度都不会变成"绿卡片"）
  {
    const grassCount = 2600;
    const gGeo = new THREE.ConeGeometry(0.13, 0.55, 4);
    gGeo.translate(0, 0.27, 0);
    const gMat = M(0x5d7a38, { roughness: 1 });
    const grass = new THREE.InstancedMesh(gGeo, gMat, grassCount);
    grass.receiveShadow = true;
    let n = 0;
    let guard = 0;
    while (n < grassCount && guard < grassCount * 6) {
      guard++;
      const x = (Math.random() - 0.5) * 300;
      const z = (Math.random() - 0.5) * 300;
      const d = Math.max(Math.abs(x), Math.abs(z));
      if (d > 150) continue;
      // 避开道路与建筑密集区
      if (d < 26) continue;
      if (Math.abs(x) < 8 || Math.abs(z) < 8) continue;
      const y = terrainHeight(x, z);
      if (y < -2.6) continue;
      const s = 0.7 + Math.random() * 0.7;
      _q.setFromAxisAngle(Y_AXIS, Math.random() * Math.PI * 2);
      _v3.set(x, y, z);
      _s3.set(s, s, s);
      _m4.compose(_v3, _q, _s3);
      grass.setMatrixAt(n, _m4);
      n++;
    }
    grass.count = n;
    grass.instanceMatrix.needsUpdate = true;
    scene.add(grass);
  }

  /* ==========================================================================
   * 8. 天气粒子
   * ========================================================================== */
  function makeSprite(kind) {
    const c = document.createElement('canvas');
    c.width = 32;
    c.height = 64;
    const ctx = c.getContext('2d');
    if (kind === 'rain') {
      const grd = ctx.createLinearGradient(0, 0, 0, 64);
      grd.addColorStop(0, 'rgba(200,225,255,0)');
      grd.addColorStop(0.5, 'rgba(200,225,255,0.85)');
      grd.addColorStop(1, 'rgba(200,225,255,0.1)');
      ctx.fillStyle = grd;
      ctx.fillRect(13, 0, 6, 64);
    } else {
      const grd = ctx.createRadialGradient(16, 32, 0, 16, 32, 16);
      grd.addColorStop(0, 'rgba(255,255,255,1)');
      grd.addColorStop(0.6, 'rgba(255,255,255,0.75)');
      grd.addColorStop(1, 'rgba(255,255,255,0)');
      ctx.fillStyle = grd;
      ctx.beginPath();
      ctx.arc(16, 32, 16, 0, Math.PI * 2);
      ctx.fill();
    }
    const tex = new THREE.CanvasTexture(c);
    tex.colorSpace = THREE.SRGBColorSpace;
    return tex;
  }

  function makeParticles(count, kind, area, height) {
    const geo = new THREE.BufferGeometry();
    const pos = new Float32Array(count * 3);
    const vel = new Float32Array(count);
    for (let i = 0; i < count; i++) {
      pos[i * 3] = (Math.random() - 0.5) * area;
      pos[i * 3 + 1] = Math.random() * height;
      pos[i * 3 + 2] = (Math.random() - 0.5) * area;
      vel[i] = 0.6 + Math.random() * 0.8;
    }
    geo.setAttribute('position', new THREE.BufferAttribute(pos, 3));
    const mat = new THREE.PointsMaterial({
      size: kind === 'rain' ? 1.1 : 0.55,
      map: makeSprite(kind),
      transparent: true,
      opacity: 0,
      depthWrite: false,
      sizeAttenuation: true,
      color: kind === 'rain' ? 0xbdd8f0 : 0xffffff,
    });
    const pts = new THREE.Points(geo, mat);
    pts.frustumCulled = false;
    scene.add(pts);
    return { pts, geo, mat, pos, vel, area, height, kind };
  }

  const rain = makeParticles(4200, 'rain', 130, 70);
  const snow = makeParticles(3200, 'snow', 170, 80);

  function updateParticles(p, dt, camera, strength) {
    p.mat.opacity = strength;
    if (strength < 0.01) {
      p.pts.visible = false;
      return;
    }
    p.pts.visible = true;
    p.pts.position.set(Math.round(camera.position.x), 0, Math.round(camera.position.z));
    const pos = p.pos;
    const isRain = p.kind === 'rain';
    const fall = isRain ? 62 : 5.2;
    const drift = isRain ? 2.5 : 4.5;
    for (let i = 0; i < pos.length / 3; i++) {
      pos[i * 3 + 1] -= fall * p.vel[i] * dt;
      if (isRain) {
        pos[i * 3] += drift * dt;
      } else {
        pos[i * 3] += Math.sin(clock.t * 0.8 + i) * drift * dt;
        pos[i * 3 + 2] += Math.cos(clock.t * 0.6 + i * 0.7) * drift * dt;
      }
      if (pos[i * 3 + 1] < -6) {
        pos[i * 3 + 1] = p.height;
        pos[i * 3] = (Math.random() - 0.5) * p.area;
        pos[i * 3 + 2] = (Math.random() - 0.5) * p.area;
      }
      if (pos[i * 3] > p.area / 2) pos[i * 3] -= p.area;
    }
    p.geo.attributes.position.needsUpdate = true;
  }

  /* ---- 雨点落地水花 ----
   * 4200 颗粒子不可能每颗都真打在地面上再算水花（也没人看得见远处的）。
   * 这里用"玩家脚边随机落点"近似：人只会注意到近处溅起的那几圈，
   * 远处的雨丝本来在视野里就糊成一片。 */
  const SPLASH_COUNT = 24;

  /**
   * 水花贴图：径向渐变 + 一圈细外环，一次生成所有水花共用。
   *
   * <p>为什么不用 RingGeometry：纯几何细环在浅色石板上既不"水"也不明显
   * （实机连改三版仍然像地面上的划痕）。渐变贴图自带柔边，中心亮、边缘散，
   * 才像雨点砸出来的那一圈；外环留给"扩散"一个可读的方向。</p>
   */
  function makeSplashTexture() {
    const size = 96;
    const canvas = document.createElement('canvas');
    canvas.width = size;
    canvas.height = size;
    const ctx = canvas.getContext('2d');
    if (!ctx) {
      return null;
    }
    const c = size / 2;
    const grad = ctx.createRadialGradient(c, c, 2, c, c, c);
    grad.addColorStop(0, 'rgba(255,255,255,0.78)');
    grad.addColorStop(0.34, 'rgba(216,240,255,0.32)');
    grad.addColorStop(0.62, 'rgba(200,230,255,0.07)');
    grad.addColorStop(1, 'rgba(200,230,255,0)');
    ctx.fillStyle = grad;
    ctx.beginPath();
    ctx.arc(c, c, c, 0, Math.PI * 2);
    ctx.fill();
    ctx.strokeStyle = 'rgba(255,255,255,0.84)';
    ctx.lineWidth = size * 0.045;
    ctx.beginPath();
    ctx.arc(c, c, c * 0.76, 0, Math.PI * 2);
    ctx.stroke();
    const tex = new THREE.CanvasTexture(canvas);
    tex.colorSpace = THREE.SRGBColorSpace;
    return tex;
  }

  const splashTex = makeSplashTexture();
  // 1×1 的方片贴图，靠 scale 控制水花直径（0.5 → 1.9 米）
  const splashGeom = new THREE.PlaneGeometry(1, 1);
  const splashRoot = new THREE.Group();
  splashRoot.visible = false;
  const splashes = [];
  for (let i = 0; i < SPLASH_COUNT; i += 1) {
    const mat = new THREE.MeshBasicMaterial({
      map: splashTex,
      color: 0xffffff,
      transparent: true,
      opacity: 0,
      depthWrite: false,
      // 必须用普通混合：地面是浅色石板/草地，additive 叠上去几乎没有变化，
      // 实机看就是"雨下得挺大，地上什么都不溅"
      side: THREE.DoubleSide,
    });
    const mesh = new THREE.Mesh(splashGeom, mat);
    // 方片是竖着的，绕 x 转 90° 才平铺在地面
    mesh.rotation.x = -Math.PI / 2;
    mesh.visible = false;
    splashRoot.add(mesh);
    splashes.push({ mesh, mat, life: 0, dur: 1, fade: 0 });
  }
  scene.add(splashRoot);

  let splashTimer = 0;
  /** 取相机朝向用的临时向量（每帧只算一次，不新建对象） */
  const splashDir = new THREE.Vector3();
  /**
   * @param amount 雨强 0..1；0 时整组隐藏（不隐藏的话每帧仍会遍历 24 个空对象）
   */
  function updateSplashes(dt, amount) {
    for (const s of splashes) {
      if (s.life <= 0) {
        continue;
      }
      s.life -= dt;
      const t = 1 - Math.max(0, s.life) / s.dur;
      // 从贴着地面的一小圈扩到近一米，同时淡出——像水花开的那一下
      const scale = 0.5 + t * 1.4;
      s.mesh.scale.set(scale, scale, 1);
      s.mat.opacity = (1 - t) * s.fade;
      if (s.life <= 0) {
        s.mesh.visible = false;
      }
    }
    if (amount < 0.05) {
      splashRoot.visible = false;
      return;
    }
    splashRoot.visible = true;
    splashTimer -= dt;
    if (splashTimer > 0) {
      return;
    }
    splashTimer = 0.05;
    const free = splashes.find((s) => s.life <= 0);
    if (!free) {
      return;
    }
    // 必须落在**相机前方**：早先按"相机周围一圈"生成，结果一半水花溅在背后，
    // 屏幕上看着像什么都没发生（探针里 11 个活跃水花，画面里一个都找不到）。
    camera.getWorldDirection(splashDir);
    const ahead = 5 + Math.random() * 18;
    const side = (Math.random() - 0.5) * 18;
    const x = camera.position.x + splashDir.x * ahead - splashDir.z * side;
    const z = camera.position.z + splashDir.z * ahead + splashDir.x * side;
    // 必须抬到路面之上：环形路 y=0.06、御道 y=0.07，早先放在 +0.03 会被石板整片盖住
    // （实机表现是"雨下得挺大，地上什么都不溅"，探针里却一直有十几个活跃水花）。
    free.mesh.position.set(x, terrainHeight(x, z) + 0.12, z);
    free.life = 0.5 + Math.random() * 0.4;
    free.dur = free.life;
    free.fade = amount;
    free.mesh.visible = true;
  }

  /* ==========================================================================
   * 9. 玩家角色（参数化形象，形象取自本地档案）
   * ========================================================================== */
  const player = {
    pos: new THREE.Vector3(0, 0, 66),
    yaw: Math.PI,
    vel: new THREE.Vector3(),
    speed: 0,
    phase: 0,
  };

  const profileState = loadProfile();
  let profile: AvatarParams = profileState.params;

  const playerRig = createAvatar(profile);
  const avatar = playerRig.group;
  avatar.position.copy(player.pos);
  scene.add(avatar);

  /* ==========================================================================
   * 9.5 NPC / 其他玩家 / 城图 / 角色面板
   * ========================================================================== */
  const labelsRoot = document.getElementById('bww-labels');

  /* ---- 城内 NPC ---- */
  const npcs = createNpcs();
  scene.add(npcs.group);

  const npcLabels = new Map();
  for (const n of npcs.list) {
    const el = document.createElement('div');
    el.className = 'bww-nlabel';
    el.innerHTML = `<b>${n.def.name}</b><span>${n.def.role}</span>`;
    labelsRoot.appendChild(el);
    npcLabels.set(n.def.id, el);
  }

  /* ---- 城民委托 ----
   * 状态机自己管存档（localStorage），这里只管三件事：把玩家位置喂进去、
   * 把头顶标记画对、把追踪面板刷新。 */
  const quests = createQuestSystem();

  /** 头顶委托标记：! 可接 · ? 可交 · · 进行中（只画状态，不带数字，远处也认得出） */
  function paintQuestMarks() {
    for (const n of npcs.list) {
      const el = npcLabels.get(n.def.id);
      if (!el) {
        continue;
      }
      const mine = quests.forGiver(n.def.id);
      let mark = '';
      let kind = '';
      if (mine.some((q) => quests.stateOf(q.id) === 'ready')) {
        mark = '?';
        kind = 'ready';
      } else if (mine.some((q) => quests.stateOf(q.id) === 'available')) {
        mark = '!';
        kind = 'offer';
      } else if (mine.some((q) => quests.stateOf(q.id) === 'active')) {
        mark = '·';
        kind = 'active';
      }
      let badge = el.querySelector('.bww-nlabel-quest');
      if (!mark) {
        badge?.remove();
        delete el.dataset.quest;
        continue;
      }
      if (!badge) {
        badge = document.createElement('div');
        badge.className = 'bww-nlabel-quest';
        el.appendChild(badge);
      }
      badge.textContent = mark;
      el.dataset.quest = kind;
    }
  }

  /* ---- 委托追踪面板（右侧、城图之下） ---- */
  const questHud = document.getElementById('bww-quest');
  /** questId -> 这一行的距离元素，每帧只改 textContent，不重建 DOM */
  const questHudRows = new Map();

  function renderQuestHud() {
    if (!questHud) {
      return;
    }
    questHudRows.clear();
    const list = quests.active();
    if (list.length === 0) {
      questHud.innerHTML = '';
      questHud.classList.remove('bww-show');
      return;
    }
    questHud.innerHTML = list
      .map((item) => {
        const goal = item.nextStep ? item.nextStep.label : '回去向委托人复命';
        return `<div class="bww-quest-row${item.nextStep ? '' : ' is-ready'}" data-quest-row="${item.quest.id}">
        <div class="bww-quest-row-top"><b>${item.quest.title}</b><i>${item.done}/${item.total}</i></div>
        <div class="bww-quest-row-goal">${goal}</div>
        <div class="bww-quest-row-dist" data-quest-dist="${item.quest.id}">—</div>
      </div>`;
      })
      .join('');
    questHud.classList.add('bww-show');
    for (const item of list) {
      const el = questHud.querySelector(`[data-quest-dist="${item.quest.id}"]`);
      if (el) {
        questHudRows.set(item.quest.id, { el, item });
      }
    }
  }

  /** 距离刷新节流：每帧改 DOM 文字没有意义，4Hz 足够读 */
  let questDistTimer = 0;
  function updateQuestHud(dt) {
    if (questHudRows.size === 0) {
      return;
    }
    questDistTimer -= dt;
    if (questDistTimer > 0) {
      return;
    }
    questDistTimer = 0.25;
    for (const row of questHudRows.values()) {
      const step = row.item.nextStep;
      // 可交时目标不再是地点，而是"人在哪儿"——所以改报委托人距离
      let tx = 0;
      let tz = 0;
      let ok = false;
      if (step) {
        tx = step.x;
        tz = step.z;
        ok = true;
      } else {
        const giver = npcs.list.find((n) => n.def.id === row.item.quest.giver);
        if (giver) {
          tx = giver.pos.x;
          tz = giver.pos.z;
          ok = true;
        }
      }
      if (!ok) {
        row.el.textContent = '';
        continue;
      }
      const d = Math.hypot(player.pos.x - tx, player.pos.z - tz);
      // 可交时不再报"还差多少米"，那句话已经没意义了
      row.el.textContent = step ? `还差 ${Math.round(d)} 米` : `委托人 ${Math.round(d)} 米外`;
    }
  }

  /** 接取/交付后统一收尾：标记、面板、对话里的卡片一起刷新 */
  function syncQuestUi() {
    paintQuestMarks();
    renderQuestHud();
    npcChat?.refreshQuests();
  }

  function questGiverName(npcId) {
    return npcs.list.find((n) => n.def.id === npcId)?.def.name ?? '委托人';
  }

  /** 交给问话面板的委托卡（面板只画，不做判断） */
  function questCardsFor(npcId) {
    const list = quests.active();
    return quests.forGiver(npcId).map((def) => {
      const state = quests.stateOf(def.id);
      const view = list.find((v) => v.quest.id === def.id);
      if (state === 'available') {
        return {
          id: def.id,
          title: def.title,
          state: 'offer',
          text: def.offer,
          action: '接下此事',
        };
      }
      if (state === 'ready') {
        return {
          id: def.id,
          title: def.title,
          state: 'ready',
          text: def.report,
          action: '回禀',
          done: view ? view.done : def.steps.length,
          total: def.steps.length,
        };
      }
      const next = view && view.nextStep ? `还差：${view.nextStep.label}` : '';
      return {
        id: def.id,
        title: def.title,
        state: 'active',
        text: next,
        done: view ? view.done : 0,
        total: def.steps.length,
      };
    });
  }

  function handleQuestAction(npcId, questId, action) {
    const def = quests.defs.find((q) => q.id === questId);
    if (!def) {
      return;
    }
    if (action === '接下此事') {
      quests.accept(questId);
      showToast(`接下委托 · ${def.title}`, `${def.steps.length} 处要走 · 右键点地即可前往`);
      npcChat?.say('路上留神。都走完了回来找我。');
      whaleSay(`接着了：${def.title}。头一处是「${def.steps[0].label}」。`);
    } else if (action === '回禀') {
      quests.complete(questId);
      npcChat?.say(def.done);
      showToast(`委托已成 · ${def.title}`, def.reward);
      whaleSay(`${questGiverName(npcId)}把「${def.reward}」给主公了。`);
    }
    syncQuestUi();
  }

  /* ---- 推进：每帧喂位置，达成一步给一次回执 ---- */
  function updateQuests(dt) {
    const events = quests.tick(player.pos.x, player.pos.z);
    for (const ev of events) {
      if (ev.kind === 'step') {
        showToast(`委托 · ${ev.quest.title}`, `${ev.label} ✓`);
      } else {
        showToast(`委托可回禀 · ${ev.quest.title}`, `回去找${questGiverName(ev.quest.giver)}复命`);
      }
    }
    if (events.length > 0) {
      syncQuestUi();
    }
    updateQuestHud(dt);
  }

  /* ---- 城中声景 ----
   * 浏览器禁止无手势播放，所以这里只建对象不出声，等第一次按键/点击再 unlock。
   * 天气环境床先按当前天气设好目标值，解锁那一刻起就在正确音量上。 */
  const audio = createWorldAudio();
  audio.setWeather(world.weather);

  /** 首次交互：解锁音频并敲一声入城钟。
   *  浏览器要求手势后才能出声，这声钟就是“开城门”；之后再按键不再响。 */
  let soundWelcomed = false;
  function kickAudio(): void {
    audio.unlock();
    if (soundWelcomed) return;
    soundWelcomed = true;
    // resume 是异步的，等一拍再敲，否则那一下会被丢在 suspended 里
    setTimeout(() => audio.toll(), 280);
  }

  /** 静音开关：按钮与 N 键共用。文案必须跟着变，否则反复点不知道为什么还是没声 */
  function toggleSound(): void {
    const muted = audio.toggleMuted();
    showToast(
      muted ? '城中已静音' : '城中已开声',
      muted ? '按 N 或点「声」恢复' : '钟声 · 雨声 · 脚步声',
    );
    uiRoot.querySelectorAll('[data-action="sound"]').forEach((b) => {
      b.textContent = muted ? '声：静' : '声：开';
    });
  }

  /* ---- 同城喊话：气泡层（面板要等频道建好才能发送，放在 channel 之后） ---- */
  const speechRoot = document.getElementById('bww-speech');
  const speech = speechRoot
    ? createSpeech(speechRoot, {
        camera,
        size: () => ({ w: renderer.domElement.clientWidth, h: renderer.domElement.clientHeight }),
        groundAt: terrainHeight,
      })
    : null;

  /* ---- 其他玩家（world 帧协议；连不上自动退回本地模拟） ---- */
  const channel = createWorldChannel({
    getToken: opts.getToken,
    self: { name: profile.name, robe: profile.robe },
    onSay: (message) => speech?.say(message),
  });

  const sayRoot = document.getElementById('bww-say-panel');
  const sayPanel = sayRoot
    ? mountSayPanel(sayRoot, {
        onSend: (text) => {
          const sent = channel.say(text);
          if (sent) {
            audio.speak();
          } else {
            showToast('喊话未发出', '尚未连上同城频道，稍后再试');
          }
        },
        onOpenChange: (open) => {
          if (open) {
            closePortalPanel();
            charPanel?.close();
            npcChat?.close();
          }
          // 发言时停下脚步：一边打字一边跑会让人以为你在逃
          player.speed = 0;
        },
      })
    : null;

  /** 由 id 派生稳定的形象差异，避免所有人长一样 */
  function hashOf(value: string): number {
    let hash = 0;
    for (let i = 0; i < value.length; i++) {
      hash = (hash * 31 + value.charCodeAt(i)) | 0;
    }
    return Math.abs(hash);
  }

  /**
   * 其他玩家按 id 增量维护：联机模式下 peers 会随进出场景变化，
   * 早期把数组与模型一一绑定（静态 map）在有人进场时就会漏人。
   */
  const peerRigs = new Map();
  function createPeerRig(peer) {
    const seed = hashOf(peer.id);
    const rig = createAvatar({
      ...DEFAULT_AVATAR,
      name: peer.name,
      robe: peer.robe,
      hairStyle: seed % 3,
      accessory: seed % 2 === 0 ? 1 : 0,
      beard: seed % 5 === 0,
    });
    scene.add(rig.group);
    const el = document.createElement('div');
    el.className = 'bww-nlabel is-peer';
    el.innerHTML = `<b>${peer.name}</b><span>在线</span>`;
    labelsRoot.appendChild(el);
    return { peer, rig, el };
  }

  function syncPeerRigs(dt) {
    const alive = new Set();
    for (const peer of channel.peers) {
      alive.add(peer.id);
      let item = peerRigs.get(peer.id);
      if (!item) {
        item = createPeerRig(peer);
        peerRigs.set(peer.id, item);
      }
      item.peer = peer;
      const gh = terrainHeight(peer.x, peer.z);
      item.rig.group.position.set(peer.x, gh, peer.z);
      item.rig.group.rotation.y = peer.yaw;
      item.rig.update(dt, peer.speed);
    }
    for (const [id, item] of peerRigs) {
      if (!alive.has(id)) {
        scene.remove(item.rig.group);
        item.el.remove();
        peerRigs.delete(id);
      }
    }
  }

  /* ---- 城图 ---- */
  const minimapCanvas = document.getElementById('bww-minimap');
  const minimap = minimapCanvas ? createMinimap(minimapCanvas) : null;
  const mapPanel = document.getElementById('bww-map-panel');
  let mapVisible = true;

  function toggleMap() {
    mapVisible = !mapVisible;
    if (mapPanel) {
      mapPanel.classList.toggle('bww-hidden', !mapVisible);
    }
    if (mapVisible) {
      requestAnimationFrame(() => minimap?.resize());
    }
  }

  const minimapPortals = portals.map((p) => ({ x: p.x, z: p.z, color: p.color }));
  const minimapPalaces = palaces.map((p) => ({ x: p.x, z: p.z }));
  let mapTick = 0;

  function updateMinimap() {
    if (!minimap || !mapVisible) {
      return;
    }
    mapTick++;
    if (mapTick % 3 !== 0) {
      return;
    }
    minimap.render({
      player: { x: player.pos.x, z: player.pos.z, yaw: player.yaw },
      portals: minimapPortals,
      palaces: minimapPalaces,
      npcs: npcs.list.map((n) => ({ x: n.pos.x, z: n.pos.z })),
      peers: channel.peers.map((p) => ({ x: p.x, z: p.z })),
    });
  }

  /* ---- 角色面板（录名入城） ---- */
  const charPanelEl = document.getElementById('bww-char-panel');
  let charPanel: CharPanelHandle | null = null;
  if (charPanelEl) {
    charPanel = mountCharPanel(charPanelEl, {
      params: profile,
      onChange: (p) => {
        profile = p;
        playerRig.setParams(p);
      },
      onSave: (p) => {
        profile = p;
        playerRig.setParams(p);
        saveProfile(p);
        // 联机时让别人也看到新形象（单机模式内部直接忽略）
        channel.setSelf({ name: p.name, robe: p.robe });
        showToast('形象已存档', `${p.name} · 城民`);
      },
    });
  }

  /* ---- 城民问话（P3）：NPC / 小鲸宝 接 whale-agent ---- */
  const debugMode = new URLSearchParams(window.location.search).has('debug');
  /**
   * 仅 ?debug=1 且非基座内：允许 e2e 从 sessionStorage 注入令牌，
   * 用来验证「在线问数」整条链路（网关权限头 → SSE 流式）。正常使用不读该键。
   */
  const debugToken = () =>
    debugMode && !opts.poweredByQiankun ? window.sessionStorage.getItem('bww.debug.token') : null;

  /* ---- 殿前牌坊：八殿各挂一块，写该殿所辖系统的实况 ---- */
  const glyphs = createGlyphs(PALACES, terrainHeight);
  scene.add(glyphs.group);

  /* ---- 数据厅（S3）：只读数据走 WebGL 层，数据全部来自上面这套 metrics 采集 ----
     把 GlyphCard 映射成厅配置。tone 取该牌所有指标里最"严重"的一档（bad>warn>good>flat），
     没有指标时跟状态走——这样厅名匾下的状态灯与该殿牌坊永远同色。 */
  let dataHall = null;
  const hallTone = (card) => {
    const order = { flat: 0, good: 1, warn: 2, bad: 3 };
    let tone = 'flat';
    for (const m of card.metrics) {
      if (order[m.tone ?? 'flat'] > order[tone]) tone = m.tone ?? 'flat';
    }
    if (card.metrics.length === 0) {
      if (card.state === 'unreachable') tone = 'bad';
      else if (card.state === 'stale' || card.state === 'denied') tone = 'warn';
    }
    return tone;
  };
  const palaceName = (nodeKey) => PALACES.find((p) => p.nodeKey === nodeKey)?.name ?? nodeKey;
  /* 契约概览（S4）覆盖：只替换登记在 OVERVIEW_HALLS 里的殿，其余仍是 S3 的 metrics 口径。
     概览没数时不覆盖——回落 metrics，而不是把厅变成空壳（宁可显示旧口径的真数）。 */
  const overviewHallData = new Map();
  const mapHallData = () => {
    const rows = [...metrics.cards.values()].map((card) => {
      const row = {
        nodeKey: card.nodeKey,
        name: palaceName(card.nodeKey),
        sub: PALACES.find((p) => p.nodeKey === card.nodeKey)?.sub ?? card.nodeKey,
        state: card.state,
        tone: hallTone(card),
        metrics: card.metrics,
        note: card.note,
        updatedAt: card.updatedAt,
      };
      const overview = overviewHallData.get(card.nodeKey);
      return overview && overview.metrics.length > 0 ? overview : row;
    });
    // 已接入契约但 metrics 里没有对应探针的殿：补进去，否则那座厅永远打不开
    const known = new Set(rows.map((row) => row.nodeKey));
    overviewHallData.forEach((hall, nodeKey) => {
      if (!known.has(nodeKey) && hall.metrics.length > 0) {
        rows.push(hall);
      }
    });
    return rows;
  };

  /** 进出厅的操作提示：用现成 toast，不新增 DOM（守住 bww-* 契约） */
  const renderHallHint = (nodeKey) => {
    if (nodeKey === null) return;
    const hallName = palaceName(nodeKey);
    showToast(hallName + ' · 数据厅', '← → 切厅 · Esc 出厅 · 30s 自动刷新');
  };

  const metrics = createWorldMetrics(PALACES, {
    // 与问话共用同一套令牌/权限来源；debug 注入的令牌也让牌坊受益
    getToken: () => debugToken() ?? opts.getToken?.() ?? null,
    getPermissions: opts.getPermissions,
    onChange: (cards) => {
      glyphs.update(cards);
      // 厅开着时同步它的数据；没开也喂，省得进厅那一刻才第一次画
      dataHall?.setData(mapHallData());
    },
  });
  // 先把"问询中/上次 xx:xx"的初始态画上去：牌子一开始就该是块牌子，而不是空白木板
  glyphs.update(metrics.cards);

  /* 数据厅刷新调度（T3.6）：进厅停掉城池那套 60s 定时器，改由这里 30s 刷一次；
     离厅恢复城池采集。两套定时器不能同时跑，否则 8 路接口被刷两遍。 */
  let hallRefreshTimer = null;
  const stopHallRefresh = () => {
    if (hallRefreshTimer !== null) {
      window.clearInterval(hallRefreshTimer);
      hallRefreshTimer = null;
    }
  };
  const startHallRefresh = () => {
    stopHallRefresh();
    void metrics.refresh();
    hallRefreshTimer = window.setInterval(() => {
      if (document.visibilityState !== 'visible') return;
      void metrics.refresh();
    }, 30_000);
  };
  const onHallEngaged = (nodeKey) => {
    if (nodeKey === null) {
      stopHallRefresh();
      hallOverview?.stop(); // 出厅：契约概览不再轮询（它的数据只在厅里用）
      metrics.start(); // 回城恢复 60s 城情采集
    } else {
      metrics.stop(); // 进厅：城池采集让位
      hallOverview?.start(); // 进厅：契约概览接管已接入的殿
      startHallRefresh();
    }
    renderHallHint(nodeKey);
  };

  dataHall = createDataHall({
    scene,
    camera,
    domElement: renderer.domElement,
    getFollowPose: () => ({ position: followPos.clone(), target: followTarget.clone() }),
    onEngagedChange: onHallEngaged,
  });
  dataHall.setData(mapHallData());

  /* 契约概览采集（S4）：与城池采集共用令牌/权限来源，只覆盖已接入的殿。
     进出厅时与 metrics 交替启停，避免同一批数字被刷两遍。 */
  const hallOverview = createHallOverview({
    palaces: PALACES,
    mainHall: { nodeKey: MAIN_HALL.nodeKey, name: MAIN_HALL.name, sub: '总控' },
    getToken: () => debugToken() ?? opts.getToken?.() ?? null,
    getPermissions: opts.getPermissions,
    onChange: (halls) => {
      halls.forEach((hall, nodeKey) => overviewHallData.set(nodeKey, hall));
      dataHall?.setData(mapHallData());
    },
  });

  const brain = createWorldBrain({
    getToken: () => debugToken() ?? opts.getToken?.() ?? null,
    getPermissions: opts.getPermissions,
  });
  const chatEl = document.getElementById('bww-chat-panel');
  const npcChat: NpcChatHandle | null = chatEl
    ? mountNpcChat(chatEl, {
        brain,
        // 面板只负责画：能不能接、能不能交，全由 quests 状态机说了算
        quests: (npcId) => questCardsFor(npcId),
        onQuestAction: (npcId, questId, action) => handleQuestAction(npcId, questId, action),
        onOpenChange: (open, persona) => {
          setTalking(open && persona ? persona.id : null);
          if (open) {
            closePortalPanel();
            charPanel?.close();
          }
        },
      })
    : null;

  // 委托标记要在面板挂好之后再画一次（首次渲染时 npcChat 可能还不存在）
  paintQuestMarks();
  renderQuestHud();

  /** 正在对话的 NPC：停步并转身看向玩家 */
  let talkingId = null;
  function setTalking(id) {
    talkingId = id;
    for (const n of npcs.list) {
      n.hold = id === n.def.id;
    }
  }

  function nearestNpc(maxDist = 5.2) {
    let best = null;
    let bestDist = maxDist;
    for (const n of npcs.list) {
      const d = Math.hypot(player.pos.x - n.pos.x, player.pos.z - n.pos.z);
      if (d < bestDist) {
        bestDist = d;
        best = n;
      }
    }
    return best;
  }

  function talkToNearestNpc() {
    if (!npcChat) {
      return;
    }
    const npc = nearestNpc();
    if (!npc) {
      showToast('四下无人', '走近城民再按 F 交谈');
      return;
    }
    const persona = npcPersona(npc.def);
    // 他手上有事交代时，开场白不能还是那句寒暄：
    // 可回禀就说交付那句，进行中就说催问那句——否则玩家得自己在面板里找按钮
    const mine = quests.forGiver(npc.def.id);
    const ready = mine.find((q) => quests.stateOf(q.id) === 'ready');
    const active = mine.find((q) => quests.stateOf(q.id) === 'active');
    if (ready) {
      persona.greeting = ready.report;
    } else if (active) {
      persona.greeting = active.remind;
    }
    npcChat.open(persona);
  }

  function talkToPet() {
    npcChat?.open(PET_PERSONA);
  }

  /* ---- NPC 搭话 ---- */
  let npcTalkCooldown = 0;
  const npcSayTimers = new Set();

  function updateNpcTalk(dt) {
    npcTalkCooldown -= dt;
    // 正在问话：别让本地台词插进面板，也免得气泡跟着人走
    if (npcTalkCooldown > 0 || disposed || npcChat?.isOpen()) {
      return;
    }
    for (const n of npcs.list) {
      const d = Math.hypot(player.pos.x - n.pos.x, player.pos.z - n.pos.z);
      if (d > 5.2) {
        continue;
      }
      const el = npcLabels.get(n.def.id);
      if (el) {
        let say = el.querySelector('.bww-nlabel-say');
        if (!say) {
          say = document.createElement('div');
          say.className = 'bww-nlabel-say';
          el.appendChild(say);
        }
        say.textContent = n.def.lines[Math.floor(Math.random() * n.def.lines.length)];
        el.classList.add('is-talking');
        const timer = window.setTimeout(() => {
          el.classList.remove('is-talking');
          npcSayTimers.delete(timer);
        }, 4200);
        npcSayTimers.add(timer);
      }
      npcTalkCooldown = 10;
      return;
    }
  }

  /* ---- 每帧推进 NPC 与其他玩家 ---- */
  function updateActors(dt) {
    npcs.update(dt, terrainHeight);
    channel.update(dt);
    syncPeerRigs(dt);
    updateNpcTalk(dt);
    // 对话对象转身面向玩家（hold 状态下 npc.update 不再改朝向，这里接管）
    if (talkingId) {
      const npc = npcs.list.find((n) => n.def.id === talkingId);
      if (npc) {
        const yaw = Math.atan2(player.pos.x - npc.pos.x, player.pos.z - npc.pos.z);
        npc.yaw = yaw;
        npc.handle.group.rotation.y = yaw;
      }
    }
    updateTalkHint();
  }

  /* ---- 走近提示：按 F 交谈 ---- */
  const talkHint = document.getElementById('bww-talk-hint');
  function updateTalkHint() {
    if (!talkHint) {
      return;
    }
    const npc = npcChat?.isOpen() ? null : nearestNpc();
    if (npc) {
      talkHint.innerHTML = `按 <b>F</b> 与 <span>${npc.def.name}</span> 交谈`;
      talkHint.classList.add('bww-show');
    } else {
      talkHint.classList.remove('bww-show');
    }
  }

  /* ---- 名字标签投影 ---- */
  const _labelV = new THREE.Vector3();
  function projectLabel(el, x, y, z, maxDist = 58) {
    _labelV.set(x, y, z);
    const dist = camera.position.distanceTo(_labelV);
    const p = _labelV.project(camera);
    if (p.z > 1 || dist > maxDist) {
      el.style.opacity = '0';
      return;
    }
    const w = renderer.domElement.clientWidth;
    const h = renderer.domElement.clientHeight;
    const sx = (p.x * 0.5 + 0.5) * w;
    const sy = (-p.y * 0.5 + 0.5) * h;
    el.style.transform = `translate(-50%,-100%) translate(${sx}px,${sy}px)`;
    const fade = 1 - Math.max(0, dist - maxDist * 0.65) / (maxDist * 0.35);
    el.style.opacity = String(clamp(fade, 0.12, 1));
  }

  function updateNameLabels() {
    for (const n of npcs.list) {
      const el = npcLabels.get(n.def.id);
      if (el) {
        projectLabel(el, n.pos.x, n.pos.y + 2.95, n.pos.z);
      }
    }
    for (const item of peerRigs.values()) {
      const gh = terrainHeight(item.peer.x, item.peer.z);
      projectLabel(item.el, item.peer.x, gh + 2.95, item.peer.z, 50);
    }
  }

  /* ==========================================================================
   * 10. 小鲸宝
   * ========================================================================== */
  const whale = new THREE.Group();
  let whaleTail = null;
  let whaleFlukes = [];
  {
    const bodyMat = M(0x3f8fce, {
      roughness: 0.42,
      metalness: 0.08,
      emissive: 0x0b2d4a,
      emissiveIntensity: 0.55,
    });
    const bellyMat = M(0xd7ecf9, { roughness: 0.55 });
    const eyeMat = new THREE.MeshBasicMaterial({ color: 0x0a0f14 });

    const body = new THREE.Mesh(new THREE.SphereGeometry(1, 28, 20), bodyMat);
    body.scale.set(1.05, 0.94, 1.85);
    body.castShadow = true;
    whale.add(body);

    const belly = new THREE.Mesh(new THREE.SphereGeometry(1, 24, 16), bellyMat);
    belly.scale.set(0.88, 0.72, 1.62);
    belly.position.set(0, -0.22, 0.05);
    whale.add(belly);

    // 头部隆起
    const headBump = new THREE.Mesh(new THREE.SphereGeometry(0.62, 20, 14), bodyMat);
    headBump.scale.set(1, 0.9, 1.1);
    headBump.position.set(0, 0.32, -1.05);
    whale.add(headBump);

    // 眼睛
    for (const s of [-1, 1]) {
      const eye = new THREE.Mesh(new THREE.SphereGeometry(0.13, 12, 10), eyeMat);
      eye.position.set(s * 0.72, 0.12, -0.98);
      whale.add(eye);
      const shine = new THREE.Mesh(
        new THREE.SphereGeometry(0.045, 8, 8),
        new THREE.MeshBasicMaterial({ color: 0xffffff }),
      );
      shine.position.set(s * 0.76, 0.18, -1.05);
      whale.add(shine);
    }

    // 胸鳍
    for (const s of [-1, 1]) {
      const fin = new THREE.Mesh(new THREE.ConeGeometry(0.36, 1.15, 4), bodyMat);
      fin.position.set(s * 1.02, -0.16, -0.35);
      fin.rotation.z = s * 1.2;
      fin.rotation.x = 0.35;
      fin.scale.set(1, 1, 0.42);
      whale.add(fin);
    }

    // 尾鳍（水平）
    whaleTail = new THREE.Group();
    whaleTail.position.set(0, 0.05, 1.7);
    for (const s of [-1, 1]) {
      const fluke = new THREE.Mesh(new THREE.ConeGeometry(0.5, 1.35, 4), bodyMat);
      fluke.rotation.z = s * Math.PI * 0.5;
      fluke.rotation.y = s * 0.35;
      fluke.position.set(s * 0.62, 0, 0.42);
      fluke.scale.set(1, 1, 0.3);
      whaleTail.add(fluke);
      whaleFlukes.push(fluke);
    }
    whale.add(whaleTail);

    // 喷气孔
    const blowhole = new THREE.Mesh(
      new THREE.SphereGeometry(0.1, 8, 8),
      new THREE.MeshBasicMaterial({ color: 0x2a5f85 }),
    );
    blowhole.position.set(0, 0.92, -0.65);
    whale.add(blowhole);

    // 环绕光点
    const aura = new THREE.Mesh(
      new THREE.SphereGeometry(1.9, 16, 12),
      new THREE.MeshBasicMaterial({
        color: 0x7fd8ff,
        transparent: true,
        opacity: 0.07,
        blending: THREE.AdditiveBlending,
        depthWrite: false,
      }),
    );
    aura.scale.set(1.1, 0.9, 1.9);
    whale.add(aura);
  }
  whale.position.set(4, 4, 72);
  // 体型收敛：P0 里按"宠物"的感觉调小一点，避免比主角还抢镜
  whale.scale.setScalar(0.8);
  scene.add(whale);

  /* ==========================================================================
   * 11. 相机控制
   * ========================================================================== */
  const cam = {
    yaw: 0,
    pitch: 0.44,
    dist: 16,
    /** 取景点相对玩家眼睛的抬升（m）。正常玩法恒为 0，只有截图评审用
     *  __bwwLookAt 抬起来看匾额、屋顶这类高处的构件。 */
    rise: 0,
    target: new THREE.Vector3(),
  };
  const keys = new Set();
  let dragging = false;
  let lastPointer = { x: 0, y: 0 };

  /** 焦点在输入类控件上时，键盘事件不参与世界操作 */
  function isTypingTarget(target) {
    if (!(target instanceof HTMLElement)) {
      return false;
    }
    const tag = target.tagName;
    return tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' || target.isContentEditable;
  }

  renderer.domElement.addEventListener('pointerdown', (e) => {
    // 拖拽本身也是用户手势，顺手解锁音频（否则第一次听到声音要等到按键）
    kickAudio();
    // 右键是"设目的地"，不参与转视角；否则右键一按就顺手把镜头也拖歪了
    if (e.button === 2) {
      return;
    }
    dragging = true;
    lastPointer = { x: e.clientX, y: e.clientY };
    renderer.domElement.setPointerCapture(e.pointerId);
  });
  renderer.domElement.addEventListener('pointerup', (e) => {
    dragging = false;
    if (renderer.domElement.hasPointerCapture(e.pointerId)) {
      renderer.domElement.releasePointerCapture(e.pointerId);
    }
  });
  renderer.domElement.addEventListener('pointermove', (e) => {
    if (!dragging) return;
    const dx = e.clientX - lastPointer.x;
    const dy = e.clientY - lastPointer.y;
    lastPointer = { x: e.clientX, y: e.clientY };
    cam.yaw -= dx * 0.005;
    cam.pitch = clamp(cam.pitch + dy * 0.0035, 0.08, 1.15);
  });
  renderer.domElement.addEventListener(
    'wheel',
    (e) => {
      e.preventDefault();
      /* 屏风接管相机期间不准变焦：屏风铺满视野时，用户在纸面外滚两下
         会把 cam.dist 改掉，退出屏风的瞬间镜头突然弹到另一个距离。 */
      if (screens.isControlling()) return;
      cam.dist = clamp(cam.dist + e.deltaY * 0.014, 8, 48);
    },
    { passive: false },
  );

  /* 右键 = 设目的地。
   * 落点用射线与 y=0 平面求交：城内 terrainHeight 恒为 0（缓坡权重从 96 米外才起），
   * 所以这个平面在可达范围内就是地面本身；点在牌坊/宫殿顶上也能落到它脚下的广场。 */
  const pickRay = new THREE.Raycaster();
  const groundPlane = new THREE.Plane(new THREE.Vector3(0, 1, 0), 0);
  const pickNdc = new THREE.Vector2();
  const pickHit = new THREE.Vector3();

  renderer.domElement.addEventListener('contextmenu', (e) => {
    // 不拦就会弹浏览器菜单——游戏里右键变成"另存为图片"，等于这个功能不存在
    e.preventDefault();
    kickAudio();
    const rect = renderer.domElement.getBoundingClientRect();
    if (rect.width <= 0 || rect.height <= 0) {
      return;
    }
    pickNdc.set(
      ((e.clientX - rect.left) / rect.width) * 2 - 1,
      -((e.clientY - rect.top) / rect.height) * 2 + 1,
    );
    pickRay.setFromCamera(pickNdc, camera);
    if (!pickRay.ray.intersectPlane(groundPlane, pickHit)) {
      // 视线打不到地面（相机接近平视且点在上半屏）：不动，别把落点算到身后去
      return;
    }
    setWalkTarget(pickHit.x, pickHit.z);
  });

  on(window, 'keydown', (e) => {
    kickAudio();
    // 问话面板的输入框本身是 typing target：Esc 要能整块收起，而不是只让输入框失焦
    if (e.code === 'Escape' && npcChat?.isOpen()) {
      npcChat.close();
      return;
    }
    // 喊话面板同理：Esc 收面板（输入框有焦点时也要整块收起）
    if (e.code === 'Escape' && sayPanel?.isOpen()) {
      sayPanel.close();
      return;
    }
    // 面板输入中不劫持按键，否则在姓名框里打 W 会驱动角色走路
    if (isTypingTarget(e.target)) {
      if (e.code === 'Escape' && e.target instanceof HTMLElement) {
        e.target.blur();
      }
      return;
    }
    /* 数据厅内：方向键/A/D 切厅、Esc 出厅，先于行走与面板快捷键消费掉，
       否则按 ← 切厅的同时角色也会横移（厅在 y=400，位移看不见但状态会脏）。 */
    if (dataHall?.isActive() && dataHall.handleKey(e.code)) {
      return;
    }
    keys.add(e.code);
    /* 进入数据厅（S3）：按 V 开第一厅。玩家若站在某殿坊前，优先开那一厅。 */
    if (e.code === 'KeyV') {
      let idx = 0;
      const keys = [...metrics.cards.keys()];
      let best = Infinity;
      for (let i = 0; i < PALACES.length; i++) {
        const p = PALACES[i];
        const at = keys.indexOf(p.nodeKey);
        if (at < 0) continue;
        const dist = Math.hypot(player.pos.x - p.x, player.pos.z - p.z);
        if (dist < best) {
          best = dist;
          idx = at;
        }
      }
      dataHall?.open(idx);
      return;
    }
    if (e.code === 'KeyE') {
      /* 屏风优先于传送门：玩家既然已经站到屏风前，想做的就是看这块屏风。
         没有近处屏风时才回到"开传送门"的旧行为。 */
      if (nearbyScreen) {
        nearbyScreen.enter();
      } else {
        /* 城门楼（S5/T5.10）：E 在这里仍然是"过门"（开传送门列表），
           但先补一句解释——否则站在门洞里按 E 弹出一张殿宇清单，
           会被读成"门楼里还藏着一座殿"，而它其实只是通道。 */
        if (nearbyGate) {
          showToast(`城门楼 · ${nearbyGate.spec.name}`, nearbyGate.spec.duty);
        }
        togglePortalPanel();
      }
    }
    if (e.code === 'KeyF') talkToNearestNpc();
    if (e.code === 'KeyT') talkToPet();
    if (e.code === 'KeyG') toggleGuide();
    if (e.code === 'KeyQ') {
      closePortalPanel();
      charPanel?.close();
      npcChat?.close();
      sayPanel?.toggle();
    }
    if (e.code === 'Escape') {
      closePortalPanel();
      charPanel?.close();
      npcChat?.close();
      sayPanel?.close();
      // Esc 也是"停步"：自动寻路中按一下就停在原地（标记会跟着淡出）
      cancelAutoWalk('已停止寻路');
      // 导览中按 Esc 直接收工（不弹提示，HUD 消失本身就是反馈）
      if (guide.isActive()) {
        guide.stop();
        renderGuideHud();
      }
    }
    if (e.code === 'KeyR') {
      cam.yaw = 0;
      cam.pitch = 0.44;
      cam.dist = 16;
    }
    if (e.code === 'KeyC') {
      closePortalPanel();
      charPanel?.toggle();
    }
    if (e.code === 'KeyM') toggleMap();
    if (e.code === 'KeyN') toggleSound();
    const num = e.code.match(/^Digit([1-9])$/);
    if (num) {
      const idx = Number(num[1]) - 1;
      if (portals[idx]) teleportTo(portals[idx]);
    }
  });
  on(window, 'keyup', (e) => keys.delete(e.code));
  on(window, 'blur', () => keys.clear());

  /* ==========================================================================
   * 12. 传送
   * ========================================================================== */
  const fadeEl = document.getElementById('bww-fade');
  let teleporting = false;

  function teleportTo(portal) {
    if (teleporting) return;
    teleporting = true;
    closePortalPanel();
    fadeEl.style.opacity = '1';
    audio.portal();
    setTimeout(() => {
      // 主门（数枢殿）落在门南侧、面朝北进皇城；宫殿门面向宫殿
      const yaw = portal.palace ? faceCenter(portal.x, portal.z) : Math.PI;
      // 落在传送门外侧
      const out = portal.palace
        ? {
            x: portal.x + Math.sin(portal.palace.yaw) * 6.5,
            z: portal.z + Math.cos(portal.palace.yaw) * 6.5,
          }
        : { x: portal.x, z: portal.z + 7 };
      player.pos.set(out.x, 0, out.z);
      player.yaw = yaw;
      player.speed = 0;
      cam.yaw = yaw + Math.PI;
      whale.position.set(out.x + 4, 4, out.z + 3);
      cam.dist = 13;
      cam.pitch = 0.55;
      showToast(`已传送至 · ${portal.name}`, portal.sub);
      fadeEl.style.opacity = '0';
      setTimeout(() => {
        teleporting = false;
      }, 420);
    }, 420);
  }

  /* ==========================================================================
   * 13. HUD
   * ========================================================================== */
  const hudTime = document.getElementById('bww-hud-time');
  const hudDate = document.getElementById('bww-hud-date');
  const petBubble = document.getElementById('bww-pet-bubble');
  const petText = document.getElementById('bww-pet-text');
  const portalHint = document.getElementById('bww-portal-hint');
  const portalPanel = document.getElementById('bww-portal-panel');
  const portalList = document.getElementById('bww-portal-list');
  const toast = document.getElementById('bww-toast');
  const toastTitle = document.getElementById('bww-toast-title');
  const toastSub = document.getElementById('bww-toast-sub');
  const hudOnline = document.getElementById('bww-hud-online');
  const guideHud = document.getElementById('bww-guide');

  const SHICHEN = ['子', '丑', '寅', '卯', '辰', '巳', '午', '未', '申', '酉', '戌', '亥'];
  const SEASONS = ['春', '夏', '秋', '冬'];
  const WEATHER = ['晴', '雨', '雪', '雾'];

  let toastTimer = 0;
  function showToast(title, sub = '') {
    toastTitle.textContent = title;
    toastSub.textContent = sub;
    toast.classList.add('bww-show');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => toast.classList.remove('bww-show'), 2600);
  }

  const WHALE_LINES = [
    '主公，皇城里的「数枢殿」今日灯火通明。',
    '户部那边账目已平，要不要去看看？',
    '我闻到河风了……城北的水面在发光呢。',
    '这里的四季，是我游过最好的风景。',
    '跨过传送门就能到别的宫殿，我帮你看着风向。',
    '若是找不到路，跟着我背上的光就行。',
    '城外的山，冬天会披上一层白。',
    '主公，走累了就在石阶上坐一会儿吧。',
  ];
  let whaleTimer = 6;
  function whaleSay(text) {
    petText.textContent = text;
    petBubble.classList.add('bww-show');
    clearTimeout(whaleSay.timer);
    whaleSay.timer = setTimeout(() => petBubble.classList.remove('bww-show'), 5200);
  }

  /* ---- 带路导览：小鲸宝领着绕八方宫署走一圈，最后进数枢殿 ---- */
  // 站点顺序直接用 PALACES 的既有顺序（就是传送门数字键 2–9 的顺序，天然是绕环一圈），
  // 末尾补数枢殿：八殿是并列的业务入口，先走完再看总账才讲得通。
  const GUIDE_STOPS = [
    ...PALACES.map((p) => ({
      nodeKey: p.nodeKey,
      name: p.name,
      line: GUIDE_LINES[p.nodeKey] ?? fallbackLine(p.name, p.sub),
      x: p.portal.x,
      z: p.portal.z,
    })),
    {
      nodeKey: MAIN_PORTAL.nodeKey,
      name: MAIN_PORTAL.name,
      line: GUIDE_LINES[MAIN_PORTAL.nodeKey] ?? fallbackLine(MAIN_PORTAL.name, MAIN_PORTAL.sub),
      x: MAIN_PORTAL.x,
      z: MAIN_PORTAL.z,
    },
  ];
  const guide = createGuide(GUIDE_STOPS);

  function renderGuideHud() {
    if (!guideHud) {
      return;
    }
    const s = guide.status();
    if (!s.active) {
      guideHud.classList.remove('bww-show');
      return;
    }
    // 走完最后一站会立刻 done，index 已等于 total，夹一下避免显示「10/9」。
    // 文案写「下一站」而不是只写站名：到站瞬间进度已经指向下一站，而鲸鱼还在讲这一站，
    // 只写站名会出现「鲸鱼讲兵部、面板写翰林院」的错位，加个动词就不会误读了。
    const step = Math.min(s.index + 1, s.total);
    guideHud.innerHTML = `<b>导览 ${step}/${s.total}</b><span>下一站 · ${s.name}</span><i>按 G 结束</i>`;
    guideHud.classList.add('bww-show');
  }

  function toggleGuide() {
    if (guide.isActive()) {
      guide.stop();
      renderGuideHud();
      whaleSay('那就先到这儿。主公想再走，按 G 唤我。');
      return;
    }
    const intro = guide.start();
    if (intro) {
      whaleSay(intro);
    }
    renderGuideHud();
  }

  // 传送面板
  function buildPortalList() {
    portalList.innerHTML = '';
    portals.forEach((p, i) => {
      const btn = document.createElement('button');
      btn.className = 'bww-portal-item';
      btn.innerHTML = `<span class="bww-pi-idx">${i + 1}</span>
      <span class="bww-pi-name">${p.name}</span>
      <span class="bww-pi-sub">${p.sub}</span>
      <button type="button" class="bww-pi-enter">进入 ›</button>`;
      btn.style.setProperty('--pc', `#${new THREE.Color(p.color).getHexString()}`);
      btn.addEventListener('click', () => teleportTo(p));
      const enterBtn = btn.querySelector('.bww-pi-enter');
      if (enterBtn) {
        enterBtn.addEventListener('click', (ev) => {
          ev.stopPropagation();
          closePortalPanel();
          if (opts.poweredByQiankun) {
            opts.navigate(p.route);
          } else {
            showToast('独立运行模式', '2D 宫殿跳转需在平台基座内访问 /world');
          }
        });
      }
      portalList.appendChild(btn);
    });
  }
  buildPortalList();

  function togglePortalPanel() {
    portalPanel.classList.toggle('bww-open');
  }
  function closePortalPanel() {
    portalPanel.classList.remove('bww-open');
  }

  // 传送门世界坐标标签
  const portalLabels = portals.map((p) => {
    const el = document.createElement('div');
    el.className = 'bww-plabel';
    el.textContent = p.name;
    document.getElementById('bww-labels').appendChild(el);
    return { el, pos: new THREE.Vector3(p.x, 7.4, p.z), name: p.name };
  });

  /* 工具栏 */
  uiRoot.querySelectorAll('[data-action]').forEach((btn) => {
    btn.addEventListener('click', () => {
      const a = btn.dataset.action;
      if (a === 'weather') {
        world.weather = (world.weather + 1) % 4;
        audio.setWeather(world.weather);
        showToast(`天象 · ${WEATHER[world.weather]}`);
        uiRoot.querySelectorAll('[data-action="weather"]').forEach((b) => {
          b.textContent = `天象：${WEATHER[world.weather]}`;
        });
      }
      if (a === 'season') {
        world.season = (world.season + 1) % 4;
        showToast(`时节 · ${SEASONS[world.season]}`);
        document.querySelectorAll('[data-action="season"]').forEach((b) => {
          b.textContent = `时节：${SEASONS[world.season]}`;
        });
        applySeason();
      }
      if (a === 'speed') {
        world.daySpeed = world.daySpeed > 1 / 100 ? 1 / 900 : 1 / 150;
        showToast(world.daySpeed > 1 / 300 ? '时辰 · 常速' : '时辰 · 疾行');
      }
      if (a === 'portal') togglePortalPanel();
      if (a === 'pet') talkToPet();
      if (a === 'guide') {
        closePortalPanel();
        toggleGuide();
      }
      if (a === 'say') {
        closePortalPanel();
        charPanel?.close();
        npcChat?.close();
        sayPanel?.toggle();
      }
      if (a === 'char') {
        closePortalPanel();
        sayPanel?.close();
        charPanel?.toggle();
      }
      if (a === 'map') toggleMap();
      if (a === 'reset') {
        cam.yaw = 0;
        cam.pitch = 0.44;
        cam.dist = 16;
      }
      if (a === 'sound') toggleSound();
    });
  });

  // 静音偏好存在 localStorage，按钮文案得跟上；否则重进页面写着"声：开"其实还是静音
  uiRoot.querySelectorAll('[data-action="sound"]').forEach((b) => {
    b.textContent = audio.isMuted() ? '声：静' : '声：开';
  });

  function applySeason() {
    const c = vegetation.colorSeason[world.season];
    const dummy = new THREE.Color();
    for (let i = 0; i < vegetation.crowns.count; i++) {
      dummy
        .copy(c)
        .offsetHSL(
          Math.sin(i * 12.9898) * 0.5 * 0.06,
          Math.sin(i * 7.233) * 0.5 * 0.09,
          Math.sin(i * 3.117) * 0.5 * 0.1,
        );
      vegetation.crowns.setColorAt(i, dummy);
    }
    if (vegetation.crowns.instanceColor) vegetation.crowns.instanceColor.needsUpdate = true;
    const groundTint = [
      new THREE.Color(0xc8e8a8),
      new THREE.Color(0xffffff),
      new THREE.Color(0xe8c98a),
      new THREE.Color(0xc8d8e8),
    ];
    groundMat.color.copy(groundTint[world.season]);
  }

  /* ==========================================================================
   * 14. 昼夜与天气
   * ========================================================================== */
  const SKY_DAY_TOP = new THREE.Color(0x2c6cb0);
  const SKY_DAY_BOT = new THREE.Color(0xc4e2f7);
  const SKY_NIGHT_TOP = new THREE.Color(0x030612);
  const SKY_NIGHT_BOT = new THREE.Color(0x0a1730);
  const SKY_DUSK_TOP = new THREE.Color(0x2b2350);
  const SKY_DUSK_BOT = new THREE.Color(0xf08a3c);

  const fogColor = new THREE.Color();

  function updateSky(dt) {
    const ang = world.time * Math.PI * 2 - Math.PI / 2;
    // 太阳沿东→西弧线
    const sunDir = world.sunDir
      .set(Math.cos(ang) * 0.85, Math.sin(ang), 0.42 * Math.cos(ang * 0.5))
      .normalize();

    const h = sunDir.y; // -1..1
    const dayF = smooth(h, -0.06, 0.34);
    const duskF = 1 - smooth(Math.abs(h), 0.02, 0.42);

    const top = new THREE.Color().copy(SKY_NIGHT_TOP).lerp(SKY_DAY_TOP, dayF);
    const bot = new THREE.Color().copy(SKY_NIGHT_BOT).lerp(SKY_DAY_BOT, dayF);
    top.lerp(SKY_DUSK_TOP, duskF * 0.75);
    bot.lerp(SKY_DUSK_BOT, duskF * 0.8);

    const haze = world.weatherMix[3];

    sunLight.target.position.set(player.pos.x, 0, player.pos.z);
    /* 平行光方向由 position→target 决定：position 必须跟着 target 平移，
     * 否则 target 跟玩家走后方向会漂移（原版 position 固定在 sunDir*220）。 */
    sunLight.position.copy(sunLight.target.position).addScaledVector(sunDir, 220);
    sunLight.intensity = lerp(0.05, 2.5, dayF);
    const sunCol = new THREE.Color(0xffe0b0).lerp(new THREE.Color(0xfff6e0), smooth(h, 0, 0.4));
    sunLight.color.copy(sunCol).lerp(new THREE.Color(0xff8a3c), duskF * 0.7);

    moonLight.position.copy(sunDir).multiplyScalar(-220);
    moonLight.intensity = lerp(0.55, 0.0, dayF);

    hemi.intensity = lerp(0.06, 0.2, dayF);
    hemi.color.copy(new THREE.Color(0x2a3f6b).lerp(new THREE.Color(0xa8ccff), dayF));

    const starsOpacity = clamp(1 - dayF * 1.5, 0, 0.95) * (1 - haze * 0.9);

    fogColor.copy(bot).lerp(new THREE.Color(0x8f9aa0), haze * 0.6);
    scene.fog.color.copy(fogColor);
    scene.fog.density =
      lerp(0.0016, 0.0052, haze) + world.weatherMix[1] * 0.0016 + world.weatherMix[2] * 0.0012;

    // 灯笼夜间点亮
    const lanternOn = 1 - dayF;
    matLantern.color.copy(new THREE.Color(0x4a3226).lerp(new THREE.Color(0xff5a3c), lanternOn));
    matLanternGlow.opacity = 0.05 + lanternOn * 0.2;

    /* 门楼檐灯的照明：一盏点光跟到最近的门楼外侧，50m 内才点（超出即熄灭）。
     * 灯位取门楼中轴外 7m、高 13m——城台立面、匾额、门洞上沿都在照射角内。 */
    if (gateAnchors.length) {
      let bestD2 = Infinity;
      let best = null;
      for (const a of gateAnchors) {
        const d2 = (a.x - player.pos.x) ** 2 + (a.z - player.pos.z) ** 2;
        if (d2 < bestD2) {
          bestD2 = d2;
          best = a;
        }
      }
      const d = Math.sqrt(bestD2);
      if (best && d < 50) {
        gateLight.position.set(best.x + Math.sin(best.yaw) * 7, 13, best.z + Math.cos(best.yaw) * 7);
        gateLight.intensity = lanternOn * 120 * clamp(1 - d / 50, 0, 1);
      } else {
        gateLight.intensity = 0;
      }
    }

    waterUniforms.uSky.value.copy(bot).multiplyScalar(1.0);
    visibleSky.mesh.position.copy(camera.position);

    /* ---------------------------------------------------------------- 渲染增强同步
     * 后处理昼夜风味 + IBL 节流重烘。IBL 接管漫射后 hemi 只补死角（上面已压到 0.06~0.2）。
     * lanternOn 复用上面灯笼段的声明。 */
    postfx.setBloom(lerp(0.22, 0.7, lanternOn));
    postfx.setVignette(lerp(0.32, 0.55, lanternOn));
    gradeShadowTint.setHex(0x1a2440).lerp(new THREE.Color(0x2a1f3e), duskF * 0.6);
    postfx.setTint(gradeShadowTint, gradeHighTint, lerp(0.22, 0.3, duskF));

    /* ---------------------------------------------------- skyState（批3 合并）
     * 可见天空与 PMREM env 共用一份快照：白天太阳方向烘日景，夜里换月亮方向——
     * env-sky 的 moonK 判据 uSunIntensity<1 会自动带月盘月海与星空，环境光与
     * 主天空永远同源。visibleSky 每帧 update（uniform 拷贝，开销可忽略）。 */
    const night = h < -0.02;
    envState.top.copy(top);
    envState.horizon.copy(bot);
    envState.ground.copy(bot).multiplyScalar(0.3);
    if (night) {
      envState.sunDir.copy(sunDir).multiplyScalar(-1); // 月亮在太阳对面
      envState.sunColor.copy(ENV_MOON_COLOR);
      envState.sunIntensity = 0.15;
      envState.stars = starsOpacity;
    } else {
      envState.sunDir.copy(sunDir);
      envState.sunColor.copy(sunCol);
      envState.sunIntensity = 1;
      envState.stars = 0;
    }
    envState.haze = haze;
    /* 云量：晴天给一点底云，雨/雪按 weatherMix 加厚；受光色随昼夜与太阳色走 */
    envState.cloud = clamp(0.22 + world.weatherMix[1] * 0.7 + world.weatherMix[2] * 0.55, 0, 0.95);
    cloudLightCol.setHex(0xffffff).lerp(sunCol, 0.35).multiplyScalar(0.2 + dayF * 0.8);
    envState.cloudLight = cloudLightCol.getHex();
    cloudShadeCol.setHex(0x9aa4b0).multiplyScalar(0.15 + dayF * 0.85);
    envState.cloudShade = cloudShadeCol.getHex();
    visibleSky.update(envState);
    visibleSky.tick(clock.t);

    /* 水面碎金/月路与天空同源：夜里 uSunIntensity 0.15 + 月色，碎金自动变月路 */
    waterUniforms.uSunDir.value.copy(envState.sunDir);
    waterUniforms.uSunColor.value.copy(envState.sunColor);
    waterUniforms.uSunIntensity.value = envState.sunIntensity;

    if (env && clock.t - lastEnvAt > ENV_INTERVAL) {
      lastEnvAt = clock.t;
      scene.environment = env.update(envState);
      scene.environmentIntensity = lerp(0.55, 0.85, dayF);
    }
  }

  /* ==========================================================================
   * 14.6 自动寻路（鼠标右键设目的地）
   *
   * <p>手一直按方向键跨城确实累。右键点地面即算一条绕开城墙与建筑的路，
   * 角色自己走过去；任何方向键输入、Esc、或再点一次都会打断。</p>
   *
   * <p>几何全部来自 {@link createBlockers}，与碰撞推挤同源——两套几何各改各的
   * 是"看得见却走不过去"的经典来源。</p>
   * ========================================================================== */
  const BLOCKERS = createBlockers();

  /**
   * 寻路边界与 updatePlayer 里的 clamp 取同一个数（OUTER - 4）。
   * 若用更大的 WORLD_BOUND，A* 会把落点安排在贴墙外侧，玩家看着有标记却永远走不到。
   */
  const nav = createNavGrid(BLOCKERS, { bound: OUTER - 4 });

  /** 自动行走速率（米/秒）：比步行快一点，跨城不至于等到烦 */
  const AUTO_SPEED = 12.5;

  /** 提前转向的距离（米）：转向是插值的，等到拐点上再急转会甩到墙上 */
  const TURN_AHEAD = 2.6;

  const autoWalk = {
    active: false,
    points: [] as Array<{ x: number; z: number }>,
    index: 0,
    /** 距上次"看它有没有真的在动"的秒数 */
    idle: 0,
    /** 上次采样时的位置，用位移判断是不是卡住（用节点推进判断会误伤长直段） */
    lastX: 0,
    lastZ: 0,
    /** 本次寻路已经重算过几次，避免无限重试 */
    retries: 0,
    goal: { x: 0, z: 0 },
  };

  /* ---- 目的地光环：落地一圈脉冲 + 一道淡光柱，让"点哪儿"有回执 ----
   * 尺寸是实机截图调出来的：最初 0.82~1.06 米、纯青色 0.9 的半透明环，
   * 在草地上几乎看不见（颜色被地面吃掉），等于没有反馈。现在做成"厚环 + 外发光"，
   * 外圈用 additive 混出光晕，远一点也认得出落点在哪。 */
  const markerMats = [];
  const markerRoot = new THREE.Group();
  markerRoot.visible = false;
  /** 造标记材质：基础透明度记在 userData 上，淡入淡出时按比例缩放（各层不同） */
  const mkMat = (baseOpacity, additive = false) => {
    const m = new THREE.MeshBasicMaterial({
      color: 0x9ff0ff,
      transparent: true,
      opacity: baseOpacity,
      depthWrite: false,
      side: THREE.DoubleSide,
      blending: additive ? THREE.AdditiveBlending : THREE.NormalBlending,
    });
    m.userData.baseOpacity = baseOpacity;
    markerMats.push(m);
    return m;
  };
  const markerRing = new THREE.Mesh(new THREE.RingGeometry(0.92, 1.26, 56), mkMat(0.9));
  markerRing.rotation.x = -Math.PI / 2;
  const markerHalo = new THREE.Mesh(new THREE.RingGeometry(1.3, 1.46, 56), mkMat(0.55, true));
  markerHalo.rotation.x = -Math.PI / 2;
  markerHalo.position.y = 0.01;
  const markerCore = new THREE.Mesh(new THREE.CircleGeometry(0.34, 28), mkMat(0.4));
  markerCore.rotation.x = -Math.PI / 2;
  markerCore.position.y = 0.02;
  const markerBeam = new THREE.Mesh(
    new THREE.CylinderGeometry(0.14, 0.44, 6, 16, 1, true),
    mkMat(0.2, true),
  );
  markerBeam.position.y = 3;
  markerRoot.add(markerRing, markerHalo, markerCore, markerBeam);
  scene.add(markerRoot);

  /** 标记透明度：出发淡入，抵达/取消淡出 */
  let markerFade = 0;

  function showMarker(x, z) {
    markerRoot.position.set(x, terrainHeight(x, z) + 0.04, z);
    markerRoot.visible = true;
    markerFade = 0.001;
  }

  function updateWalkMarker(dt) {
    if (!markerRoot.visible) {
      return;
    }
    const target = autoWalk.active ? 1 : 0;
    markerFade += (target - markerFade) * Math.min(1, dt * 6);
    if (!autoWalk.active && markerFade < 0.02) {
      markerRoot.visible = false;
      return;
    }
    const pulse = 1 + Math.sin(clock.t * 5) * 0.07;
    // 环是绕 x 轴躺平的，局部 x/y 就对应世界 x/z，所以缩 x/y 才是"绕地扩大"
    markerRing.scale.set(pulse, pulse, 1);
    markerHalo.scale.set(pulse, pulse, 1);
    markerRing.rotation.z += dt * 0.7;
    for (const m of markerMats) {
      m.opacity = m.userData.baseOpacity * markerFade;
    }
  }

  /**
   * 设定自动寻路目的地（世界坐标）。
   *
   * @param x 落点 x（米）
   * @param z 落点 z（米）
   * @returns 是否成功起步
   */
  function setWalkTarget(x, z) {
    if (!Number.isFinite(x) || !Number.isFinite(z)) {
      return false;
    }
    const from = { x: player.pos.x, z: player.pos.z };
    const direct = Math.hypot(x - from.x, z - from.z);
    if (direct < 1.2) {
      showToast('就在脚下', '点远一点的地方');
      return false;
    }

    const path = nav.findPath(from, { x, z });
    if (!path || path.points.length === 0) {
      showToast('过不去', '那道墙后面得先走城门');
      return false;
    }

    autoWalk.active = true;
    autoWalk.points = path.points;
    autoWalk.index = 0;
    autoWalk.idle = 0;
    autoWalk.retries = 0;
    autoWalk.lastX = player.pos.x;
    autoWalk.lastZ = player.pos.z;
    autoWalk.goal = { x: path.goal.x, z: path.goal.z };
    showMarker(path.goal.x, path.goal.z);

    const dist = Math.round(Math.hypot(path.goal.x - from.x, path.goal.z - from.z));
    if (path.adjusted) {
      showToast('已改道', `落点站不住，改到 ${Math.round(dist)} 米外的空地`);
    } else {
      showToast('自动寻路中', `${dist} 米 · 按方向键或 Esc 中断`);
    }
    return true;
  }

  /** 停止自动行走；返回是否原本在走（供 Esc 判断要不要消费这次按键） */
  function cancelAutoWalk(reason) {
    if (!autoWalk.active) {
      return false;
    }
    autoWalk.active = false;
    autoWalk.points = [];
    autoWalk.index = 0;
    autoWalk.idle = 0;
    if (reason) {
      showToast('已停止', reason);
    }
    return true;
  }

  /** 抵达 */
  function finishAutoWalk() {
    if (!autoWalk.active) {
      return;
    }
    autoWalk.active = false;
    autoWalk.points = [];
    autoWalk.index = 0;
    showToast('已抵达', '到了，主公');
  }

  /**
   * 沿路径推进并把方向写进 moveDir。
   *
   * @returns 是否仍在自动行走（false 表示接下来按静止处理）
   */
  function steerAutoWalk(dt, moveDir) {
    if (!autoWalk.active) {
      return false;
    }
    while (autoWalk.index < autoWalk.points.length) {
      const pt = autoWalk.points[autoWalk.index];
      const dx = pt.x - player.pos.x;
      const dz = pt.z - player.pos.z;
      const dist = Math.hypot(dx, dz);
      const last = autoWalk.index === autoWalk.points.length - 1;
      // 终点要求踩准一点（标记就落在那儿），中间拐点可以提前切，避免绕着拐点打转
      const reach = last ? 0.5 : Math.max(0.8, player.speed * dt * 1.5);
      if (dist > reach) {
        moveDir.set(dx / dist, 0, dz / dist);
        /* 临近拐点时把下一段方向混进来：速度与朝向都是插值过去的，
         等踩到点再急转会冲过半个身位贴到墙上，接着被碰撞推回来，
         表现就是"在墙角来回抽"。前瞻一点，拐得圆滑。 */
        const next = autoWalk.points[autoWalk.index + 1];
        if (next && dist < TURN_AHEAD) {
          const ndx = next.x - player.pos.x;
          const ndz = next.z - player.pos.z;
          const nlen = Math.hypot(ndx, ndz);
          if (nlen > 0.001) {
            const w = (1 - dist / TURN_AHEAD) * 0.6;
            const mx = (dx / dist) * (1 - w) + (ndx / nlen) * w;
            const mz = (dz / dist) * (1 - w) + (ndz / nlen) * w;
            const mlen = Math.hypot(mx, mz);
            // 两段夹角接近 180° 时混合方向会趋近零向量，此时保持原方向更稳
            if (mlen > 0.35) {
              moveDir.set(mx / mlen, 0, mz / mlen);
            }
          }
        }
        return true;
      }
      autoWalk.index += 1;
      autoWalk.idle = 0;
    }
    finishAutoWalk();
    return false;
  }

  /**
   * 卡住检测：推挤、拐角擦墙都可能让角色原地磨。
   *
   * <p><b>必须按"实际位移"判，不能按"路径节点推进"判</b>——拉直之后的两个拐点之间
   * 可能隔着四十米直路，按节点推进计时会把正常长跑误判成卡住，然后在半路无端停下
   * （这正是斜穿全城用例一度停在城门洞里的原因）。</p>
   */
  function updateAutoWalkGuard(dt) {
    if (!autoWalk.active) {
      return;
    }
    autoWalk.idle += dt;
    if (autoWalk.idle < 2) {
      return;
    }
    autoWalk.idle = 0;
    const moved = Math.hypot(player.pos.x - autoWalk.lastX, player.pos.z - autoWalk.lastZ);
    autoWalk.lastX = player.pos.x;
    autoWalk.lastZ = player.pos.z;
    if (moved > 1.2) {
      return; // 在动，只是这一段长
    }
    // 磨住了就按当前位置重算（起点变了，路也可能变）；两次都不行才放弃，
    // 一次性放弃太容易把"擦墙角"这种小磕绊升级成"这功能不好用"
    if (autoWalk.retries < 2) {
      autoWalk.retries += 1;
      const retry = nav.findPath({ x: player.pos.x, z: player.pos.z }, autoWalk.goal);
      if (retry && retry.points.length > 0) {
        autoWalk.points = retry.points;
        autoWalk.index = 0;
        return;
      }
    }
    cancelAutoWalk('路上受阻，先停下');
  }

  /* ==========================================================================
   * 15. 主循环
   * ========================================================================== */
  const moveDir = new THREE.Vector3();
  const camPos = new THREE.Vector3();
  const camTarget = new THREE.Vector3();
  const tmpV = new THREE.Vector3();

  function updatePlayer(dt) {
    const forward = tmpV.set(-Math.sin(cam.yaw), 0, -Math.cos(cam.yaw));
    const right = new THREE.Vector3(-forward.z, 0, forward.x);

    moveDir.set(0, 0, 0);
    let manual = false;
    if (keys.has('KeyW') || keys.has('ArrowUp')) {
      moveDir.add(forward);
      manual = true;
    }
    if (keys.has('KeyS') || keys.has('ArrowDown')) {
      moveDir.sub(forward);
      manual = true;
    }
    if (keys.has('KeyA') || keys.has('ArrowLeft')) {
      moveDir.sub(right);
      manual = true;
    }
    if (keys.has('KeyD') || keys.has('ArrowRight')) {
      moveDir.add(right);
      manual = true;
    }

    // 一按方向键就交还控制权：自动寻路是"省手"，不是"接管"
    if (manual) {
      cancelAutoWalk('已切回手动');
    }
    const auto = !manual && steerAutoWalk(dt, moveDir);

    const running = keys.has('ShiftLeft') || keys.has('ShiftRight');
    const maxSpeed = auto ? AUTO_SPEED : running ? 16 : 7.5;
    const moving = moveDir.lengthSq() > 0.001;
    if (moving) moveDir.normalize();

    const targetSpeed = moving ? maxSpeed : 0;
    player.speed = lerp(player.speed, targetSpeed, 1 - Math.exp(-dt * 9));

    if (moving) {
      const targetYaw = Math.atan2(moveDir.x, moveDir.z);
      let diff = targetYaw - player.yaw;
      while (diff > Math.PI) diff -= Math.PI * 2;
      while (diff < -Math.PI) diff += Math.PI * 2;
      player.yaw += diff * Math.min(1, dt * 12);
    }

    const step = player.speed * dt;
    player.pos.x += Math.sin(player.yaw) * step;
    player.pos.z += Math.cos(player.yaw) * step;

    // 城墙与建筑边界（简单推挤；freeRoam 为截图/探针用，正常玩法恒为城内钳位）
    const lim = freeRoam ? 1e4 : OUTER - 4;
    player.pos.x = clamp(player.pos.x, -lim, lim);
    player.pos.z = clamp(player.pos.z, -lim, lim);
    collide(player.pos);

    player.pos.y = terrainHeight(player.pos.x, player.pos.z);

    // 走不动了就重算/放弃（必须在移动之后再判，否则第一帧就会被误判为卡住）
    if (auto) {
      updateAutoWalkGuard(dt);
    }

    // 世界频道上报（内部按 10Hz 节流；单机模式下只更新本地缓存）
    channel.report(player.pos.x, player.pos.z, player.yaw, player.speed);

    avatar.position.copy(player.pos);
    avatar.rotation.y = player.yaw;

    // 行走动画（姿态由 avatar 模块托管，含呼吸与静止张望）
    playerRig.update(dt, player.speed);
  }

  /** 建筑碰撞：几何全部由 collision 模块按布局生成，这里只保留调用点 */
  function collide(pos) {
    resolveCollide(BLOCKERS, pos);
  }

  /** 相机避让：落入墙体/建筑缓冲区则视为阻挡 */
  function camBlocked(x, z) {
    return isCamBlocked(BLOCKERS, x, z);
  }

  function updateCamera(dt) {
    camTarget.copy(player.pos).add(new THREE.Vector3(0, 2.4 + cam.rise, 0));
    const cp = Math.cos(cam.pitch);
    // 玩家在城内时，相机不允许越过外墙（避免城墙挡镜头）
    let d = cam.dist;
    const targetInside = Math.max(Math.abs(camTarget.x), Math.abs(camTarget.z)) < OUTER - 2;
    if (targetInside) {
      for (let k = 0; k < 16; k++) {
        const px = camTarget.x + Math.sin(cam.yaw) * d * cp;
        const pz = camTarget.z + Math.cos(cam.yaw) * d * cp;
        if (!camBlocked(px, pz) && Math.max(Math.abs(px), Math.abs(pz)) < OUTER - 2.5) break;
        d *= 0.8;
      }
    }
    camPos.set(
      camTarget.x + Math.sin(cam.yaw) * d * cp,
      camTarget.y + Math.sin(cam.pitch) * d,
      camTarget.z + Math.cos(cam.yaw) * d * cp,
    );
    // 不穿地
    const gh = terrainHeight(camPos.x, camPos.z) + 1.6;
    if (camPos.y < gh) camPos.y = gh;

    camera.position.lerp(camPos, 1 - Math.exp(-dt * 9));
    camera.lookAt(camTarget);
    /* 记下这一帧的跟随位姿：退出屏风时按它插值回去。
       不重算一遍是因为 updateCamera 里有一整套相机避让（不穿墙、不入地），
       复刻一份必然出现"两处算法不一致"的经典故障。 */
    followPos.copy(camera.position);
    followTarget.copy(camTarget);
  }

  function updateWhale(dt) {
    const t = clock.t;
    const guiding = guide.isActive();

    // 导览时鲸鱼不再是"跟着玩家"，而是"领着玩家"：先飞到该站等，主公到了再讲解、再走下一站
    let tick = null;
    if (guiding) {
      tick = guide.update(player.pos.x, player.pos.z, whale.position.x, whale.position.z);
      if (tick.signal) {
        whaleSay(tick.signal.stop.line);
        renderGuideHud();
        if (tick.signal.kind === 'done') {
          showToast('导览结束', '八方宫署走完，往后主公自己认路');
        }
      }
    }

    const target = new THREE.Vector3();
    if (guiding && tick?.target) {
      const gx = tick.target.x;
      const gz = tick.target.z;
      target.set(gx, terrainHeight(gx, gz) + 3.2, gz);
    } else {
      const off = new THREE.Vector3(3.0, 3.1, 2.6).applyAxisAngle(Y_AXIS, player.yaw);
      target.copy(player.pos).add(off);
    }
    target.y += Math.sin(t * 1.4) * 0.42;

    whale.position.lerp(target, 1 - Math.exp(-dt * 2.6));

    // 朝向：领路时朝目标飞，平时的朝向仍看着主公
    const faceX = guiding && tick?.target ? target.x : player.pos.x;
    const faceZ = guiding && tick?.target ? target.z : player.pos.z;
    const desired = Math.atan2(faceX - whale.position.x, faceZ - whale.position.z);
    let diff = desired - whale.rotation.y;
    while (diff > Math.PI) diff -= Math.PI * 2;
    while (diff < -Math.PI) diff += Math.PI * 2;
    whale.rotation.y += diff * Math.min(1, dt * 2.4);

    whale.rotation.z = Math.sin(t * 1.6) * 0.09;
    whale.rotation.x = Math.sin(t * 1.1) * 0.05;
    if (whaleTail) whaleTail.rotation.x = Math.sin(t * 2.2) * 0.42;

    // 定时说话：导览中闭嘴，否则闲聊会把讲解词顶掉
    if (guiding) {
      return;
    }
    whaleTimer -= dt;
    if (whaleTimer <= 0) {
      whaleTimer = 16 + Math.random() * 12;
      if (Math.random() < 0.7)
        whaleSay(WHALE_LINES[Math.floor(Math.random() * WHALE_LINES.length)]);
    }
  }

  function updatePortals(dt) {
    let nearest = null;
    let nearestDist = 1e9;
    for (let i = 0; i < portals.length; i++) {
      const p = portals[i];
      const u = PORTAL_UNIFORMS[i];
      if (u) u.uTime.value += dt * 1.6;
      const s = 1 + Math.sin(clock.t * 2 + i) * 0.03;
      p.ring.scale.setScalar(s);
      p.ring.rotation.z += dt * 0.5;

      const d = Math.hypot(player.pos.x - p.x, player.pos.z - p.z);
      if (d < nearestDist) {
        nearestDist = d;
        nearest = p;
      }
      p.ring.material.opacity = 1;
      p.near = d < 9;
    }
    /* 这里只记"最近的传送门是谁"，不再直接写提示条：
       提示条只有一条，谁该占它由 updateInteractionHint 统一裁决。 */
    nearestPortal = nearest;
    nearestPortalDist = nearestDist;
  }

  /* 交互提示条的唯一裁决者（S5/T5.10 收口）。
     优先级：屏风（4.6m）> 传送门（7m）> 城门楼（11m）。
     三条都写同一块 #bww-portal-hint，各自 if 就写会在同一帧里互相覆盖——
     旧写法靠"后调用的赢"，加第三条时必然打起来，索性一次算完。 */
  function updateInteractionHint() {
    if (screens.isControlling()) {
      nearbyScreen = null;
      nearbyGate = null;
      portalHint.classList.remove('bww-show');
      return;
    }
    nearbyScreen = screens.nearest(player.pos.x, player.pos.z, 4.6);
    if (nearbyScreen) {
      const title = nearbyScreen.el.querySelector('.bws-title')?.textContent ?? '屏风';
      portalHint.classList.add('bww-show');
      portalHint.innerHTML = `按 <b>E</b> 或点击进入 · <span>${title}</span>`;
      nearbyGate = null;
      return;
    }
    if (nearestPortal && nearestPortalDist < 7) {
      portalHint.classList.add('bww-show');
      portalHint.innerHTML = `按 <b>E</b> 打开传送门 · <span>${nearestPortal.name}</span>`;
      nearbyGate = null;
      return;
    }
    /* 城门楼（S5/T5.10）：网关在城里的化身，只有地标、没有内页。
       文案刻意不写"进入"——写"过门"才是真的（按 E 开的是传送门列表）。 */
    nearbyGate = nearestGate(player.pos.x, player.pos.z);
    if (nearbyGate) {
      const hint = gateHintText(nearbyGate.spec);
      portalHint.classList.add('bww-show');
      portalHint.innerHTML = `${hint.lead} · <span>${hint.name}</span>`;
      return;
    }
    portalHint.classList.remove('bww-show');
  }

  function updateLabels() {
    const w = renderer.domElement.clientWidth;
    const h = renderer.domElement.clientHeight;
    for (const l of portalLabels) {
      const p = tmpV.copy(l.pos).project(camera);
      const d = camera.position.distanceTo(l.pos);
      // 远处传送门的标签会在屏幕顶聚成一排遮住 HUD，这里收到 60m 内
      const visible = p.z < 1 && d < 42;
      if (!visible) {
        l.el.style.opacity = '0';
        continue;
      }
      const x = (p.x * 0.5 + 0.5) * w;
      const y = (-p.y * 0.5 + 0.5) * h;
      l.el.style.transform = `translate(-50%,-50%) translate(${x}px,${y}px)`;
      l.el.style.opacity = String(clamp(1 - (d - 26) / 16, 0.12, 1));
    }
  }

  function updateHUD() {
    const totalH = world.time * 24;
    const hh = Math.floor(totalH);
    const mm = Math.floor((totalH - hh) * 60);
    const sc = SHICHEN[Math.floor(((hh + 1) % 24) / 2)];
    hudTime.textContent = `${sc}时 · ${String(hh).padStart(2, '0')}:${String(mm).padStart(2, '0')}`;
    const w = WEATHER[world.weather];
    hudDate.textContent = `${SEASONS[world.season]} · ${w}`;
    if (hudOnline) {
      const net = channel.mode === 'local' ? '单机模拟' : '联机';
      hudOnline.textContent = `同屏 ${channel.peers.length + 1} 人 · NPC ${npcs.list.length} · ${net}`;
      hudOnline.classList.toggle('is-online', channel.mode === 'ws');
    }
  }

  function updateWeather(dt) {
    const target = [0, 0, 0, 0];
    target[world.weather] = 1;
    for (let i = 0; i < 4; i++) {
      world.weatherMix[i] = lerp(world.weatherMix[i], target[i], 1 - Math.exp(-dt * 2.2));
    }
    const wet = world.weatherMix[1];
    updateParticles(rain, dt, camera, wet * 0.9);
    updateParticles(snow, dt, camera, world.weatherMix[2] * 0.95);
    updateSplashes(dt, wet);
    // 城里的人不是纸片：雨天自己撑伞（雪天暂不撑，雪不湿衣）
    npcs.setWet(wet);
  }

  function animate() {
    rafId = requestAnimationFrame(animate);
    const now = performance.now();
    let dt = (now - clock.last) / 1000;
    clock.last = now;
    dt = Math.min(dt, 0.05);
    clock.t += dt;

    if (!world.paused) world.time = (world.time + dt * world.daySpeed) % 1;
    waterUniforms.uTime.value = clock.t;

    updateSky(dt);
    updateWeather(dt);
    /* 屏风接管相机时（过场中或已进殿），玩家停步、跟随镜头让位。
       只冻结"输入与相机"，NPC、天气、昼夜、传送门照常跑——城里不该因为
       有人站在屏风前看数就静止。 */
    /* 数据厅（S3）激活时同样冻结输入与跟随相机：厅在 y=400，相机 snap 到固定机位
       后若还跑 updateCamera 会立刻被拉回玩家身上，展厅直接穿帮。 */
    const hallActive = Boolean(dataHall?.isActive());
    const screenBusy = screens.isControlling() || domFocused;
    if (hallActive || screenBusy) {
      player.speed = 0;
      if (!hallActive) screens.updateCamera();
    } else {
      updatePlayer(dt);
    }
    // 位置更新之后才判达成：这一帧走到门口就该这一帧算数
    updateQuests(dt);
    updateWalkMarker(dt);
    // 脚步声跟速度走：静止时 update 内部会自己归零，不必在外面判
    audio.update(dt, player.speed, groundAt(player.pos.x, player.pos.z));
    updateActors(dt);
    if (!screenBusy && !hallActive) updateCamera(dt);
    updateWhale(dt);
    updatePortals(dt);
    updateInteractionHint();
    updateHUD();

    // low 档直渲（软渲染 e2e 路径）；mid/high 走后处理链（Bloom→Output→Grade→SMAA）
    if (quality === 'low') {
      renderer.render(scene, camera);
    } else {
      postfx.render(dt);
    }
    /* 屏风层紧随主场景之后：CSS3D 用同一台相机的同一帧矩阵，
       遮挡遮罩再压在最上面切掉被柱子挡住的部分。 */
    screens.render();
    updateLabels();
    updateNameLabels();
    // 气泡要在 render 之后投影：此时相机矩阵已是最新
    speech?.update();
    updateMinimap();
  }

  on(window, 'resize', () => {
    camera.aspect = innerWidth / innerHeight;
    camera.updateProjectionMatrix();
    renderer.setSize(innerWidth, innerHeight);
    postfx.setSize(innerWidth, innerHeight);
    screens.resize(innerWidth, innerHeight);
  });

  /* ------------------------------------------------------------------ 屏风布置（S2）
   * S2 先立两面屏风把系统跑通：一面告示（小屏）、一面数枢殿总览（中屏）。
   * S3 接数据厅框架后这里会改成按 city-layout.json 批量生成——那时屏风长在每座殿
   * 坊的门前，而不是像现在这样立在御道两侧。
   *
   * 位置选在出生点（0, 0, 66）正前方偏两侧：玩家进城第一眼就能看到"这里能点"，
   * 不用先学会走路才找到演示对象。 */
  /** 户部屏风与其业务页句柄：题头灯与标题由业务页回调驱动，调试探针也要用 */
  let hubuScreen: ScreenHandle | null = null;
  let hubuPage: ScreenPageHandle | null = null;

  /** 数枢殿总控屏与它的业务页句柄（同上） */
  let shushuScreen: ScreenHandle | null = null;
  let shushuPage: ScreenPageHandle | null = null;

  /** 枢密殿（S5/T5.2）屏风与其业务页句柄（同上） */
  let shumiScreen: ScreenHandle | null = null;
  let shumiPage: ScreenPageHandle | null = null;

  /** 度支殿（S5/T5.3）屏风与其业务页句柄（同上） */
  let duzhiScreen: ScreenHandle | null = null;
  let duzhiPage: ScreenPageHandle | null = null;

  /** 藏书殿（S5/T5.4）屏风与其业务页句柄（同上） */
  let cangshuScreen: ScreenHandle | null = null;
  let cangshuPage: ScreenPageHandle | null = null;

  /** 时辰殿（S5/T5.5）屏风与其业务页句柄（同上） */
  let shichenScreen: ScreenHandle | null = null;
  let shichenPage: ScreenPageHandle | null = null;

  /** 政事殿（S5/T5.6）屏风与其业务页句柄（同上） */
  let zhengshiScreen: ScreenHandle | null = null;
  let zhengshiPage: ScreenPageHandle | null = null;

  /** 考功殿（S5/T5.7）屏风与其业务页句柄（同上） */
  let kaogongScreen: ScreenHandle | null = null;
  let kaogongPage: ScreenPageHandle | null = null;

  /** 通政殿（S5/T5.8）屏风与其业务页句柄（同上） */
  let tongzhengScreen: ScreenHandle | null = null;
  let tongzhengPage: ScreenPageHandle | null = null;

  /** 起居殿（S5/T5.9）屏风与其业务页句柄（同上） */
  let qijuScreen: ScreenHandle | null = null;
  let qijuPage: ScreenPageHandle | null = null;

  /** 工部坊（S6/T6.4）屏风与其业务页句柄（同上） */
  let gongbuScreen: ScreenHandle | null = null;
  let gongbuPage: ScreenPageHandle | null = null;

  /** 吏部坊（S6/T6.7）屏风与其业务页句柄（同上） */
  let libuScreen: ScreenHandle | null = null;
  let libuPage: ScreenPageHandle | null = null;

  /** 礼部坊（S6/T6.10）屏风与其业务页句柄：节点 key 是 `libu-ritual-hall`，
      与吏部的 `libu-hall` 只差一词——布局里两坊同高对称（z=±19.67），
      变量名必须能区分，否则半年后没人看得出哪块屏是哪个坊的 */
  let libuRitualScreen: ScreenHandle | null = null;
  let libuRitualPage: ScreenPageHandle | null = null;

  /** 兵部坊（S6/T6.13）屏风与其业务页句柄（同上） */
  let bingbuScreen: ScreenHandle | null = null;
  let bingbuPage: ScreenPageHandle | null = null;

  /** 杏坛坊（S6/T6.16）屏风与其业务页句柄（同上） */
  let xingtanScreen: ScreenHandle | null = null;
  let xingtanPage: ScreenPageHandle | null = null;

  /** 将作坊（S6/T6.19）屏风与其业务页句柄（同上） */
  let jiangzuoScreen: ScreenHandle | null = null;
  let jiangzuoPage: ScreenPageHandle | null = null;

  /** 营造坊（S6/T6.22）屏风与其业务页句柄（同上） */
  let yingzaoScreen: ScreenHandle | null = null;
  let yingzaoPage: ScreenPageHandle | null = null;

  /** 盟府（S6/T6.25）屏风与其业务页句柄（同上） */
  let mengfuScreen: ScreenHandle | null = null;
  let mengfuPage: ScreenPageHandle | null = null;

  /** 宣抚坊（S6/T6.28）屏风与其业务页句柄（同上） */
  let xuanfuScreen: ScreenHandle | null = null;
  let xuanfuPage: ScreenPageHandle | null = null;

  /** 翰林院（S6/T6.31）屏风与其业务页句柄（同上） */
  let hanlinScreen: ScreenHandle | null = null;
  let hanlinPage: ScreenPageHandle | null = null;

  /** 市舶司（S6/T6.34）屏风与其业务页句柄（同上） */
  let shiboScreen: ScreenHandle | null = null;
  let shiboPage: ScreenPageHandle | null = null;

  /**
   * 坊的屏风落位：从布局数据现算，不手抄坐标。
   *
   * <p>内城坊的台基深 15.2 米（`(d + 9) × scale`），坊墙内缘只剩不到 2 米，
   * 屏风放不进台基里（CSS3D 不做深度测试，插进台基看着就是浮在半空）。
   * 所以沿殿的**正面法线**退到台基外、坊门内的那条地上，再沿切向偏一点避开台阶。</p>
   *
   * <p>两个偏移量是 S4 在户部实测出来的（落点 15.2 / 64.6，屏风底边投影确实踩在地面）。
   * 12 坊几何完全一致，公式套在 {@link PALACES} 上就能得到各自的落点；
   * 户部仍保留那组实测字面量（公式的来源），其余各坊一律用本函数，
   * 免得十二组坐标各写各的、改一处忘十一处。</p>
   *
   * @param nodeKey 坊的节点 key
   * @returns 屏风位置与朝向；找不到该坊时返回 null（宁可少一块屏，也不要摆错位置）
   */
  function districtScreenPose(nodeKey: string): { x: number; z: number; facing: number } | null {
    const palace = PALACES.find((item) => item.nodeKey === nodeKey);
    if (!palace) {
      return null;
    }
    // front 是正面法线（朝城内），lateral 是它的右法线；与 createPalace 的 yaw 同口径
    const frontX = Math.sin(palace.yaw);
    const frontZ = Math.cos(palace.yaw);
    const lateralX = Math.cos(palace.yaw);
    const lateralZ = -Math.sin(palace.yaw);
    return {
      x: palace.x + 9.67 * frontX + 2.04 * lateralX,
      z: palace.z + 9.67 * frontZ + 2.04 * lateralZ,
      facing: palace.yaw,
    };
  }

  function setupScreens() {
    try {
    screens.add({
      id: 'notice',
      position: [-7.6, 0.85, 63],
      facing: 0,
      size: 'small',
      title: '入城须知',
      subtitle: 'NOTICE',
      tone: 'good',
      mount: (body) => {
        body.innerHTML = `
          <p style="margin:0 0 .5em">城中殿坊皆可查看。走近立屏，<b>点击</b>或按 <b>E</b> 入内。</p>
          <p style="margin:0;color:rgba(96,74,42,.85);font-size:.92em">
            皇城九殿司中枢，内城十二坊理业务，外城九处备游观。
          </p>`;
      },
    });

    /* 数枢殿总控屏（S5/T5.1）：屏风里是真业务页 —— 概览与表格都来自
       /monitor/overview 的同一次聚合，各殿状态是服务端判定的，不是前端猜的。

       这一块原来（S2）是写死数据的排版演示：五行假服务名足以钉住样式，
       但它验证不了任何真链路。S5 把它换成总控页，顺便给"进屏风"这条路径
       再找一个真实入口 —— 数枢殿就在出生点正前方，玩家第一眼看到的就是它。 */
    shushuScreen = screens.add({
      id: 'shushu',
      position: [7.6, 1.55, 63],
      facing: 0,
      // 总控是张四列表 + 6 行分页，中屏（680×420）排不下；与户部台账同档大屏
      size: 'large',
      title: '数枢殿 · 总控',
      subtitle: 'HUB / OVERVIEW',
      tone: 'idle',
      mount: (body) => {
        shushuPage = mountScreenPage(body, {
          nodeKey: 'shushu-hall',
          overviewPath: '/monitor/overview',
          pages: [HUB_HALLS_PAGE],
          getToken: () => debugToken() ?? opts.getToken?.() ?? null,
          getPermissions: opts.getPermissions,
          onTone: (tone) => shushuScreen?.setTone(tone),
          onTitle: (title, subtitle) => {
            shushuScreen?.setTitle(title ? `${title} · 总控` : '数枢殿 · 总控', subtitle ?? 'HUB / OVERVIEW');
          },
        });
        return () => {
          shushuPage?.dispose();
          shushuPage = null;
        };
      },
    });

    /* 户部（ERP）屏风页（S4 / T4.5）：屏风里是一个真业务页 ——
       KPI 与图表来自 /erp/overview，列表与详情来自采购订单自己的接口。
       立在户部殿前（19.67, 73.41）偏北 4 米，正对来路，玩家从御道走过来就看见。

       为什么不复用数枢殿那块中屏：那块是 S2 的排版演示（写死数据），
       这块要证明的是"真接口 + 列表 + 详情"。两者用途不同，S5 才统一按布局生成。 */
    /* 位置按台基反算：户部台基深 (7+9)×0.95 = 15.2 米、顶面高 2.47 米，
       屏风若按殿心偏移会插进台基里（CSS3D 不做深度测试，看上去就是浮在半空）。
       所以沿殿的正面法线（yaw -165°）退到台基之外的地面上，离台阶也留出 2 米。 */
    hubuScreen = screens.add({
      id: 'hubu-erp',
      position: [15.2, 1.55, 64.6],
      facing: -2.88,
      size: 'large',
      title: '户部 · 采购台账',
      subtitle: 'ERP / PURCHASE',
      tone: 'idle',
      mount: (body) => {
        hubuPage = mountScreenPage(body, {
          nodeKey: 'hubu-hall',
          overviewPath: '/erp/overview',
          overviewPermission: 'sys:erp:bi:cockpit:list',
          pages: [PURCHASE_ORDER_PAGE],
          getToken: () => debugToken() ?? opts.getToken?.() ?? null,
          getPermissions: opts.getPermissions,
          // 题头灯跟业务页状态走：概览陈旧/失败时不该还亮绿灯
          onTone: (tone) => hubuScreen?.setTone(tone),
          onTitle: (title, subtitle) => {
            hubuScreen?.setTitle(title ? `${title} · 采购台账` : '户部 · 采购台账', subtitle ?? 'ERP / PURCHASE');
          },
        });
        return () => {
          hubuPage?.dispose();
          hubuPage = null;
        };
      },
    });

    /* 工部坊（S6/T6.4）：生产工单 —— 今天哪几张单在跑、跑到哪一步、计划量多少。

       **坊的屏风落位与前十二殿不同**：皇城殿的屏风立在台基上（殿前有空地），
       而内城坊的台基深 15.2 米、坊墙内缘只剩不到 2 米，屏风放不进台基里，
       只能像户部那样落在**台基外、坊门内**那一条地上。

       这套落点是 S4 在户部实测出来的（屏风底边投影 563px vs 地面投影 556px，
       确实踩在地上），所以这里**不手抄第二组坐标**，而是按同一套公式从
       {@link PALACES} 现算：沿殿的正面法线退 9.67 米，再沿切向偏 2.04 米避开台阶。
       户部那组数保留原地（它是公式的实测来源，改它就得重做一次投影校验）；
       后续十一坊都套公式，各坊的落点不会再各写各的。 */
    const gongbuPose = districtScreenPose('gongbu-hall');
    if (gongbuPose) {
      gongbuScreen = screens.add({
        id: 'gongbu',
        position: [gongbuPose.x, 1.55, gongbuPose.z],
        facing: gongbuPose.facing,
        size: 'large',
        title: '工部坊 · 生产工单',
        subtitle: 'MES / WORK ORDER',
        tone: 'idle',
        mount: (body) => {
          gongbuPage = mountScreenPage(body, {
            nodeKey: 'gongbu-hall',
            overviewPath: '/mes/overview',
            overviewPermission: 'sys:mes:dashboard:view',
            pages: [WORK_ORDER_PAGE],
            getToken: () => debugToken() ?? opts.getToken?.() ?? null,
            getPermissions: opts.getPermissions,
            // 题头灯跟业务页状态走：概览陈旧/失败时不该还亮绿灯
            onTone: (tone) => gongbuScreen?.setTone(tone),
            onTitle: (title, subtitle) => {
              gongbuScreen?.setTitle(title ? `${title} · 生产工单` : '工部坊 · 生产工单',
                subtitle ?? 'MES / WORK ORDER');
            },
          });
          return () => {
            gongbuPage?.dispose();
            gongbuPage = null;
          };
        },
      });
    }


    /* 吏部坊（S6/T6.7）：考勤日报 —— 今天谁没来、谁迟到了、这个月异常多少人次。

       落位与工部坊同一套公式（`districtScreenPose`），这里不再重复解释：
       内城坊的台基放不下屏风，只能落在台基外、坊门内那条地上。 */
    const libuPose = districtScreenPose('libu-hall');
    if (libuPose) {
      libuScreen = screens.add({
        id: 'libu',
        position: [libuPose.x, 1.55, libuPose.z],
        facing: libuPose.facing,
        size: 'large',
        title: '吏部坊 · 考勤日报',
        subtitle: 'OA / ATTENDANCE',
        tone: 'idle',
        mount: (body) => {
          libuPage = mountScreenPage(body, {
            nodeKey: 'libu-hall',
            overviewPath: '/oa/overview',
            overviewPermission: 'sys:oa:attendance:daily:list',
            pages: [ATTENDANCE_DAILY_PAGE],
            getToken: () => debugToken() ?? opts.getToken?.() ?? null,
            getPermissions: opts.getPermissions,
            // 题头灯跟业务页状态走：概览陈旧/失败时不该还亮绿灯
            onTone: (tone) => libuScreen?.setTone(tone),
            onTitle: (title, subtitle) => {
              libuScreen?.setTitle(title ? `${title} · 考勤日报` : '吏部坊 · 考勤日报',
                subtitle ?? 'OA / ATTENDANCE');
            },
          });
          return () => {
            libuPage?.dispose();
            libuPage = null;
          };
        },
      });
    }

    /* 礼部坊（S6/T6.10）：客户台账 + 销售看板 —— 本月卖了多少、离目标还差多少、
       管道里压着多少、哪些商机过期没关。

       落位同样是 `districtScreenPose`：礼部与吏部在布局里是对称的两坊
       （z = -19.67 / +19.67，yaw ±75° / -105°），公式套上各自的 yaw
       就给出两块互不重叠的屏位，不需要再手工调坐标。 */
    const libuRitualPose = districtScreenPose('libu-ritual-hall');
    if (libuRitualPose) {
      libuRitualScreen = screens.add({
        id: 'libu-ritual',
        position: [libuRitualPose.x, 1.55, libuRitualPose.z],
        facing: libuRitualPose.facing,
        size: 'large',
        title: '礼部坊 · 客户台账',
        subtitle: 'CRM / CUSTOMER',
        tone: 'idle',
        mount: (body) => {
          libuRitualPage = mountScreenPage(body, {
            nodeKey: 'libu-ritual-hall',
            overviewPath: '/crm/overview',
            overviewPermission: 'sys:crm:analytics:dashboard',
            pages: [CUSTOMER_PAGE],
            getToken: () => debugToken() ?? opts.getToken?.() ?? null,
            getPermissions: opts.getPermissions,
            // 题头灯跟业务页状态走：概览陈旧/失败（含 CRM 分析门禁关闭）时不该还亮绿灯
            onTone: (tone) => libuRitualScreen?.setTone(tone),
            onTitle: (title, subtitle) => {
              libuRitualScreen?.setTitle(title ? `${title} · 客户台账` : '礼部坊 · 客户台账',
                subtitle ?? 'CRM / CUSTOMER');
            },
          });
          return () => {
            libuRitualPage?.dispose();
            libuRitualPage = null;
          };
        },
      });
    }

    /* 兵部坊（S6/T6.13）：设备台账 + 兵部概览 —— 有多少哨探、几个在岗、
       近一天收上来多少条消息、有没有指令发出去就没了下文。

       落位同样套 `districtScreenPose`：兵部在布局里是东南角（x/z = 53.74/-53.74，
       yaw -45°），公式按它自己的正面法线算，不需要手调坐标。 */
    const bingbuPose = districtScreenPose('bingbu-hall');
    if (bingbuPose) {
      bingbuScreen = screens.add({
        id: 'bingbu',
        position: [bingbuPose.x, 1.55, bingbuPose.z],
        facing: bingbuPose.facing,
        size: 'large',
        title: '兵部坊 · 设备台账',
        subtitle: 'IOT / DEVICE',
        tone: 'idle',
        mount: (body) => {
          bingbuPage = mountScreenPage(body, {
            nodeKey: 'bingbu-hall',
            overviewPath: '/iot/overview',
            overviewPermission: 'sys:iot:device',
            pages: [DEVICE_PAGE],
            getToken: () => debugToken() ?? opts.getToken?.() ?? null,
            getPermissions: opts.getPermissions,
            // 题头灯跟业务页状态走：概览陈旧/失败（服务未起、无凭据）时不该还亮绿灯
            onTone: (tone) => bingbuScreen?.setTone(tone),
            onTitle: (title, subtitle) => {
              bingbuScreen?.setTitle(title ? `${title} · 设备台账` : '兵部坊 · 设备台账',
                subtitle ?? 'IOT / DEVICE');
            },
          });
          return () => {
            bingbuPage?.dispose();
            bingbuPage = null;
          };
        },
      });
    }

    /* 杏坛坊（S6/T6.16）：课程目录 + 学习看板 —— 这一城的人有没有在长本事。

       布局里杏坛在西南角（x/z = -19.67/-73.41，yaw 15°），是十二坊里最靠南的一座；
       `districtScreenPose` 按它自己的正面法线算落点，不需要手调坐标。

       屏风上半部是四张牌（在册学员 / 学习覆盖 / 学习完成 / 考试通过），
       下半部是课程目录。两者出自同一个看板：覆盖率低的时候先看"课够不够"，
       再看"是不是没人学"——这张表回答的是前半句。 */
    const xingtanPose = districtScreenPose('xingtan-hall');
    if (xingtanPose) {
      xingtanScreen = screens.add({
        id: 'xingtan',
        position: [xingtanPose.x, 1.55, xingtanPose.z],
        facing: xingtanPose.facing,
        size: 'large',
        title: '杏坛坊 · 课程目录',
        subtitle: 'LMS / COURSE',
        tone: 'idle',
        mount: (body) => {
          xingtanPage = mountScreenPage(body, {
            nodeKey: 'xingtan-hall',
            overviewPath: '/lms/overview',
            overviewPermission: 'sys:lms:analytics:view',
            pages: [COURSE_PAGE],
            getToken: () => debugToken() ?? opts.getToken?.() ?? null,
            getPermissions: opts.getPermissions,
            // 题头灯跟业务页状态走：概览陈旧/失败（服务未起、无凭据）时不该还亮绿灯
            onTone: (tone) => xingtanScreen?.setTone(tone),
            onTitle: (title, subtitle) => {
              xingtanScreen?.setTitle(title ? `${title} · 课程目录` : '杏坛坊 · 课程目录',
                subtitle ?? 'LMS / COURSE');
            },
          });
          return () => {
            xingtanPage?.dispose();
            xingtanPage = null;
          };
        },
      });
    }

    /* 将作坊（S6/T6.19）：物料台账 + 四本账 —— 这一城要造什么、图纸上改到第几版。

       布局里将作监在西侧（x/z = -73.41/-19.67），与东岸的市舶隔城相望；
       `districtScreenPose` 按它自己的正面法线算落点，不需要手调坐标。

       屏风上半部是四张牌（在册物料 / 生效 BOM / 未关闭变更 / 图纸文档），
       下半部是物料台账。四本账里只有物料是"造一台机器前要先看的东西"——
       BOM 与变更在 2D 里才有发布与审批按钮，城里点进去也只能看。 */
    const jiangzuoPose = districtScreenPose('jiangzuo-bureau');
    if (jiangzuoPose) {
      jiangzuoScreen = screens.add({
        id: 'jiangzuo',
        position: [jiangzuoPose.x, 1.55, jiangzuoPose.z],
        facing: jiangzuoPose.facing,
        size: 'large',
        title: '将作坊 · 物料台账',
        subtitle: 'PDM / PART',
        tone: 'idle',
        mount: (body) => {
          jiangzuoPage = mountScreenPage(body, {
            nodeKey: 'jiangzuo-bureau',
            overviewPath: '/pdm/overview',
            overviewPermission: 'sys:pdm:part:list',
            pages: [PART_PAGE],
            getToken: () => debugToken() ?? opts.getToken?.() ?? null,
            getPermissions: opts.getPermissions,
            // 题头灯跟业务页状态走：概览陈旧/失败（服务未起、无凭据）时不该还亮绿灯
            onTone: (tone) => jiangzuoScreen?.setTone(tone),
            onTitle: (title, subtitle) => {
              jiangzuoScreen?.setTitle(title ? `${title} · 物料台账` : '将作坊 · 物料台账',
                subtitle ?? 'PDM / PART');
            },
          });
          return () => {
            jiangzuoPage?.dispose();
            jiangzuoPage = null;
          };
        },
      });
    }

    /* 营造坊（S6/T6.22）：项目台账 + 三本账 —— 工程推进到哪了、有多少活没人干。

       布局里营造司在坊区与将作相邻（都是"造东西"的口径，一里一外：
       将作管器物图册，营造管工程节奏）；`districtScreenPose` 按它自己的
       正面法线算落点，不需要手调坐标。

       屏风上半部是四张牌（在建项目 / 活跃迭代 / 未完成工作项 / 逾期未完成），
       下半部是项目台账。工作项不单摆一页：后端的工作项分页强制按项目过滤，
       租户级的工作项清单在 2D 后台也不存在，硬凑只会误导。 */
    const yingzaoPose = districtScreenPose('yingzao-bureau');
    if (yingzaoPose) {
      yingzaoScreen = screens.add({
        id: 'yingzao',
        position: [yingzaoPose.x, 1.55, yingzaoPose.z],
        facing: yingzaoPose.facing,
        size: 'large',
        title: '营造坊 · 项目台账',
        subtitle: 'PM / PROJECT',
        tone: 'idle',
        mount: (body) => {
          yingzaoPage = mountScreenPage(body, {
            nodeKey: 'yingzao-bureau',
            overviewPath: '/pm/overview',
            overviewPermission: 'sys:pm:project:list',
            pages: [PROJECT_PAGE],
            getToken: () => debugToken() ?? opts.getToken?.() ?? null,
            getPermissions: opts.getPermissions,
            // 题头灯跟业务页状态走：概览陈旧/失败（服务未起、无凭据）时不该还亮绿灯
            onTone: (tone) => yingzaoScreen?.setTone(tone),
            onTitle: (title, subtitle) => {
              yingzaoScreen?.setTitle(title ? `${title} · 项目台账` : '营造坊 · 项目台账',
                subtitle ?? 'PM / PROJECT');
            },
          });
          return () => {
            yingzaoPage?.dispose();
            yingzaoPage = null;
          };
        },
      });
    }

    /* 盟府（S6/T6.25）：合同台账 + 契约三本账 —— 纸面上的承诺：金额、状态、到期。

       布局里盟府在内城西北角（将作的对面、营造的邻坊）；`districtScreenPose`
       按它自己的正面法线算落点，不需要手调坐标。

       屏风上半部是四张牌（生效中 / 在途 / 本月到期 / 已过期仍生效），
       中部是生效合同金额柱图，下半部是合同台账。印章与签署任务不单摆页：
       印章授权是管理操作不是台账，签署任务挂在具体合同下，租户级清单不存在。 */
    const mengfuPose = districtScreenPose('mengfu-hall');
    if (mengfuPose) {
      mengfuScreen = screens.add({
        id: 'mengfu',
        position: [mengfuPose.x, 1.55, mengfuPose.z],
        facing: mengfuPose.facing,
        size: 'large',
        title: '盟府 · 合同台账',
        subtitle: 'CONTRACT / 契约',
        tone: 'idle',
        mount: (body) => {
          mengfuPage = mountScreenPage(body, {
            nodeKey: 'mengfu-hall',
            overviewPath: '/contract/overview',
            overviewPermission: 'sys:contract:list',
            pages: [CONTRACT_PAGE],
            getToken: () => debugToken() ?? opts.getToken?.() ?? null,
            getPermissions: opts.getPermissions,
            // 题头灯跟业务页状态走：概览陈旧/失败（服务未起、无凭据）时不该还亮绿灯
            onTone: (tone) => mengfuScreen?.setTone(tone),
            onTitle: (title, subtitle) => {
              mengfuScreen?.setTitle(title ? `${title} · 合同台账` : '盟府 · 合同台账',
                subtitle ?? 'CONTRACT / 契约');
            },
          });
          return () => {
            mengfuPage?.dispose();
            mengfuPage = null;
          };
        },
      });
    }

    /* 宣抚坊（S6/T6.28）：党员台账 + 党务四张牌 —— 组织生活正不正常。

       布局里宣抚院在城南侧（z=+73.41，盟府的邻坊）；`districtScreenPose`
       按它自己的正面法线算落点，不需要手调坐标。

       屏风上半部是四张牌（在册党员 / 党费收缴 / 三会一课 / 组织生活参会），
       中部是党员年龄结构柱图，下半部是党员台账。支部与工会不单摆页：
       支部是组织结构不是台账，工会的账在联建（union）另算，混进来口径就乱了。 */
    const xuanfuPose = districtScreenPose('xuanfu-court');
    if (xuanfuPose) {
      xuanfuScreen = screens.add({
        id: 'xuanfu',
        position: [xuanfuPose.x, 1.55, xuanfuPose.z],
        facing: xuanfuPose.facing,
        size: 'large',
        title: '宣抚坊 · 党员台账',
        subtitle: 'PARTY / 党务',
        tone: 'idle',
        mount: (body) => {
          xuanfuPage = mountScreenPage(body, {
            nodeKey: 'xuanfu-court',
            overviewPath: '/party/overview',
            overviewPermission: 'sys:party:metric:list',
            pages: [MEMBER_PAGE],
            getToken: () => debugToken() ?? opts.getToken?.() ?? null,
            getPermissions: opts.getPermissions,
            // 题头灯跟业务页状态走：概览陈旧/失败（服务未起、无凭据）时不该还亮绿灯
            onTone: (tone) => xuanfuScreen?.setTone(tone),
            onTitle: (title, subtitle) => {
              xuanfuScreen?.setTitle(title ? `${title} · 党员台账` : '宣抚坊 · 党员台账',
                subtitle ?? 'PARTY / 党务');
            },
          });
          return () => {
            xuanfuPage?.dispose();
            xuanfuPage = null;
          };
        },
      });
    }

    /* 翰林院（S6/T6.31）：空间台账 + 文书四本账 —— 写出来的东西走到了哪一步。

       布局里翰林院在城北侧（z=-73.41，杏坛的对坊）；`districtScreenPose`
       按它自己的正面法线算落点，不需要手调坐标。

       屏风上半部是四张牌（文档空间 / 文稿页面 / 待发布审批 / 回收站），
       中部是文稿状态分布柱图，下半部是空间台账。页面树与回收站不单摆页：
       页面按空间树形组织、没有租户级清单，回收站在 2D 里也按空间进，
       硬凑一张跨空间大表只会误导。 */
    const hanlinPose = districtScreenPose('hanlin-academy');
    if (hanlinPose) {
      hanlinScreen = screens.add({
        id: 'hanlin',
        position: [hanlinPose.x, 1.55, hanlinPose.z],
        facing: hanlinPose.facing,
        size: 'large',
        title: '翰林院 · 空间台账',
        subtitle: 'DOC / 文档知识',
        tone: 'idle',
        mount: (body) => {
          hanlinPage = mountScreenPage(body, {
            nodeKey: 'hanlin-academy',
            overviewPath: '/doc/overview',
            overviewPermission: 'sys:doc:space:list',
            pages: [SPACE_PAGE],
            getToken: () => debugToken() ?? opts.getToken?.() ?? null,
            getPermissions: opts.getPermissions,
            // 题头灯跟业务页状态走：概览陈旧/失败（服务未起、无凭据）时不该还亮绿灯
            onTone: (tone) => hanlinScreen?.setTone(tone),
            onTitle: (title, subtitle) => {
              hanlinScreen?.setTitle(title ? `${title} · 空间台账` : '翰林院 · 空间台账',
                subtitle ?? 'DOC / 文档知识');
            },
          });
          return () => {
            hanlinPage?.dispose();
            hanlinPage = null;
          };
        },
      });
    }

    /* 市舶司（S6/T6.34，内城十二坊收官）：订单台账 + 通商四本账 —— 生意卡在哪一节。

       布局里市舶司在西北角（x/z = -53.74/-53.74，旋转 45°），`districtScreenPose`
       按它自己的正面法线算落点，不需要手调坐标。

       屏风上半部是四张牌（在售商品 / 待发货订单 / 待处理售后 / 零库存 SKU），
       中部是订单七态分布柱图，下半部是订单台账。售后与库存不单摆页：
       2D 里都从订单和商品进，硬凑只会把入口铺满。 */
    const shiboPose = districtScreenPose('shibosi-hall');
    if (shiboPose) {
      shiboScreen = screens.add({
        id: 'shibo',
        position: [shiboPose.x, 1.55, shiboPose.z],
        facing: shiboPose.facing,
        size: 'large',
        title: '市舶司 · 订单台账',
        subtitle: 'MALL / 商城交易',
        tone: 'idle',
        mount: (body) => {
          shiboPage = mountScreenPage(body, {
            nodeKey: 'shibosi-hall',
            overviewPath: '/mall/overview',
            overviewPermission: 'sys:mall:order:list',
            pages: [ORDER_PAGE],
            getToken: () => debugToken() ?? opts.getToken?.() ?? null,
            getPermissions: opts.getPermissions,
            // 题头灯跟业务页状态走：概览陈旧/失败（服务未起、无凭据）时不该还亮绿灯
            onTone: (tone) => shiboScreen?.setTone(tone),
            onTitle: (title, subtitle) => {
              shiboScreen?.setTitle(title ? `${title} · 订单台账` : '市舶司 · 订单台账',
                subtitle ?? 'MALL / 商城交易');
            },
          });
          return () => {
            shiboPage?.dispose();
            shiboPage = null;
          };
        },
      });
    }

    /* 枢密殿（AUTH）屏风页（S5 / T5.2）：屏风里是登录历史 ——
       谁、什么时候、用什么方式登录，失败了多少次。

       **皇城屏风的摆法**（T5.3 起统一）：立在**本殿台基上、殿门前**，
       而不是台基外的缝隙里。皇城九殿 14 米一档、台基深 10.8 米，殿与殿之间
       只剩 3.2 米——屏风放在缝隙里既会贴到隔壁台基侧面，第三人称相机也被
       台基夹到贴脸（实测相机落到 0.5 米、埋进鲸鱼）。放在台基上则有三件事同时成立：
       ① 屏风脚下是实体台基，不会看起来浮在暗处；
       ② 玩家站在台基边缘抬头就能读屏；
       ③ 视线从殿门进来正对屏面，不会被两侧殿墙切掉。

       **y 只能从台基顶面往上算**：`createPalace` 里台基高 `2.6 × scale`，
       皇城 `scale = 0.72` → 台基顶面 1.87 米。而 `ScreenSpec.position` 是屏风
       **中心**（`screen.ts` 里 `transformOrigin: 50% 50%`），大屏高 3.1 米，
       故中心 = 1.87 + 1.55 ≈ 3.42 米。早前取 2.55 是把它当底边估的，结果屏风
       下半截插进实心台基里——台基不透明，插进去的底边看不见，远看就成了
       "屏风浮在台基前"。
       z 取殿心 +4.5：台基前沿在 +5.4，留 0.9 米石面垫脚；殿身前沿 +2.16、
       檐柱 +2.7，也不会顶到柱子。

       规格仍用大屏：屏风页按 620px 高排版（KPI + 图表 + 6 行表格），中屏只有
       420px 高，装不下就会变成内部滚动——屏风上再套一层滚动是最难用的组合。 */
    shumiScreen = screens.add({
      id: 'shumi',
      position: [-20.5, 3.42, -16.5],
      facing: 0,
      size: 'large',
      title: '枢密殿 · 登录历史',
      subtitle: 'AUTH / LOGIN',
      tone: 'idle',
      mount: (body) => {
        shumiPage = mountScreenPage(body, {
          nodeKey: 'shumi-hall',
          overviewPath: '/auth/overview',
          overviewPermission: 'sys:login:history:list',
          pages: [LOGIN_HISTORY_PAGE],
          getToken: () => debugToken() ?? opts.getToken?.() ?? null,
          getPermissions: opts.getPermissions,
          onTone: (tone) => shumiScreen?.setTone(tone),
          onTitle: (title, subtitle) => {
            shumiScreen?.setTitle(title ? `${title} · 登录历史` : '枢密殿 · 登录历史', subtitle ?? 'AUTH / LOGIN');
          },
        });
        return () => {
          shumiPage?.dispose();
          shumiPage = null;
        };
      },
    });

    /* 度支殿（PAY）屏风页（S5 / T5.3）：屏风里是支付单台账 ——
       今天收进来多少钱、哪几笔还挂着没付、退了多少。

       位置同枢密殿：本殿台基上、殿门前（殿心 z = -7，屏风 z = -2.5，
       台基顶面 1.87 米 → 屏风中心 3.42 米，见上面那段摆法说明）。
       与枢密殿那块（z = -16.5）同列相距 14 米，各在自己台基上，互不遮挡。 */
    duzhiScreen = screens.add({
      id: 'duzhi',
      position: [-20.5, 3.42, -2.5],
      facing: 0,
      size: 'large',
      title: '度支殿 · 支付单',
      subtitle: 'PAY / SETTLEMENT',
      tone: 'idle',
      mount: (body) => {
        duzhiPage = mountScreenPage(body, {
          nodeKey: 'duzhi-hall',
          overviewPath: '/pay/overview',
          overviewPermission: 'sys:pay:list',
          pages: [PAY_ORDER_PAGE],
          getToken: () => debugToken() ?? opts.getToken?.() ?? null,
          getPermissions: opts.getPermissions,
          // 题头灯跟业务页状态走：概览陈旧/失败时不该还亮绿灯
          onTone: (tone) => duzhiScreen?.setTone(tone),
          onTitle: (title, subtitle) => {
            duzhiScreen?.setTitle(title ? `${title} · 支付单` : '度支殿 · 支付单', subtitle ?? 'PAY / SETTLEMENT');
          },
        });
        return () => {
          duzhiPage?.dispose();
          duzhiPage = null;
        };
      },
    });

    /* 藏书殿（FILE）屏风页（S5 / T5.4）：屏风里是文件台账 ——
       一共多少份、占了多少盘、最近传了什么、回收站里还堆着什么。

       位置同前两殿：本殿台基上、殿门前（藏书殿殿心 z = +7，屏风 z = +11.5，
       台基顶面 1.87 米 → 屏风中心 3.42 米）。 */
    cangshuScreen = screens.add({
      id: 'cangshu',
      position: [-20.5, 3.42, 11.5],
      facing: 0,
      size: 'large',
      title: '藏书殿 · 文件台账',
      subtitle: 'FILE / STORAGE',
      tone: 'idle',
      mount: (body) => {
        cangshuPage = mountScreenPage(body, {
          nodeKey: 'cangshu-hall',
          overviewPath: '/file/overview',
          overviewPermission: 'sys:file:list',
          pages: [FILE_ASSET_PAGE],
          getToken: () => debugToken() ?? opts.getToken?.() ?? null,
          getPermissions: opts.getPermissions,
          // 题头灯跟业务页状态走：概览陈旧/失败时不该还亮绿灯
          onTone: (tone) => cangshuScreen?.setTone(tone),
          onTitle: (title, subtitle) => {
            cangshuScreen?.setTitle(title ? `${title} · 文件台账` : '藏书殿 · 文件台账',
              subtitle ?? 'FILE / STORAGE');
          },
        });
        return () => {
          cangshuPage?.dispose();
          cangshuPage = null;
        };
      },
    });

    /* 时辰殿（JOB）屏风页（S5 / T5.5）：屏风里是定时任务台账 ——
       哪些任务在跑、按什么计划跑、今天失败了几次。

       位置同前几殿：本殿台基上、殿门前（时辰殿殿心 z = +21，屏风 z = +25.5，
       台基顶面 1.87 米 → 屏风中心 3.42 米）。 */
    shichenScreen = screens.add({
      id: 'shichen',
      position: [-20.5, 3.42, 25.5],
      facing: 0,
      size: 'large',
      title: '时辰殿 · 定时任务',
      subtitle: 'JOB / SCHEDULER',
      tone: 'idle',
      mount: (body) => {
        shichenPage = mountScreenPage(body, {
          nodeKey: 'shichen-hall',
          overviewPath: '/job/overview',
          overviewPermission: 'sys:job:list',
          pages: [JOB_INFO_PAGE],
          getToken: () => debugToken() ?? opts.getToken?.() ?? null,
          getPermissions: opts.getPermissions,
          // 题头灯跟业务页状态走：概览陈旧/失败时不该还亮绿灯
          onTone: (tone) => shichenScreen?.setTone(tone),
          onTitle: (title, subtitle) => {
            shichenScreen?.setTitle(title ? `${title} · 定时任务` : '时辰殿 · 定时任务',
              subtitle ?? 'JOB / SCHEDULER');
          },
        });
        return () => {
          shichenPage?.dispose();
          shichenPage = null;
        };
      },
    });

    /* 政事殿（WORKFLOW）屏风页（S5 / T5.6）：屏风里是流程实例台账 ——
       哪些奏折在途、今天批了几件、卡在谁那里。

       位置同前几殿：本殿台基上、殿门前。**注意政事殿在东列**（x = +20.5、
       殿心 z = -21），与西列枢密殿（x = -20.5、同 z）东西对称，所以屏风也镜像到
       东侧（x = +20.5、z = -16.5），而不是继续沿西列往南排。
       台基顶面 1.87 米 → 屏风中心 3.42 米（摆法说明见枢密殿那段）。 */
    zhengshiScreen = screens.add({
      id: 'zhengshi',
      position: [20.5, 3.42, -16.5],
      facing: 0,
      size: 'large',
      title: '政事殿 · 流程实例',
      subtitle: 'WORKFLOW / APPROVAL',
      tone: 'idle',
      mount: (body) => {
        zhengshiPage = mountScreenPage(body, {
          nodeKey: 'zhengshi-hall',
          overviewPath: '/workflow/overview',
          overviewPermission: 'sys:workflow:instance:list',
          pages: [PROCESS_INSTANCE_PAGE],
          getToken: () => debugToken() ?? opts.getToken?.() ?? null,
          getPermissions: opts.getPermissions,
          // 题头灯跟业务页状态走：概览陈旧/失败时不该还亮绿灯
          onTone: (tone) => zhengshiScreen?.setTone(tone),
          onTitle: (title, subtitle) => {
            zhengshiScreen?.setTitle(title ? `${title} · 流程实例` : '政事殿 · 流程实例',
              subtitle ?? 'WORKFLOW / APPROVAL');
          },
        });
        return () => {
          zhengshiPage?.dispose();
          zhengshiPage = null;
        };
      },
    });

    /* 考功殿（FORM）屏风页（S5 / T5.7）：屏风里是表单定义台账 ——
       有哪些表在收数、哪张表刚改过、这张表有哪几个字段。

       位置同前几殿：本殿台基上、殿门前。考功殿在**东列第二进**（x = +20.5、
       殿心 z = -7），紧挨政事殿南侧，所以屏风 z = -2.5（殿心 + 4.5）。
       台基顶面 1.87 米 → 屏风中心 3.42 米（摆法说明见枢密殿那段）。 */
    kaogongScreen = screens.add({
      id: 'kaogong',
      position: [20.5, 3.42, -2.5],
      facing: 0,
      size: 'large',
      title: '考功殿 · 表单定义',
      subtitle: 'FORM / LEDGER',
      tone: 'idle',
      mount: (body) => {
        kaogongPage = mountScreenPage(body, {
          nodeKey: 'kaogong-hall',
          overviewPath: '/form/overview',
          overviewPermission: 'sys:form:list',
          pages: [FORM_DEFINITION_PAGE],
          getToken: () => debugToken() ?? opts.getToken?.() ?? null,
          getPermissions: opts.getPermissions,
          // 题头灯跟业务页状态走：概览陈旧/失败时不该还亮绿灯
          onTone: (tone) => kaogongScreen?.setTone(tone),
          onTitle: (title, subtitle) => {
            kaogongScreen?.setTitle(title ? `${title} · 表单定义` : '考功殿 · 表单定义',
              subtitle ?? 'FORM / LEDGER');
          },
        });
        return () => {
          kaogongPage?.dispose();
          kaogongPage = null;
        };
      },
    });

    /* 通政殿（S5/T5.8）：今天送出多少封信、送得到吗、有没有卡在半路、有没有被退回。
       通政司管"上行下达"，所以这一屏看的是**消息是否真的到了人手里**：
       积压未发（收下了没送出）与退信（地址失效）是两件不同的坏事。

       位置同前几殿：本殿台基上、殿门前。通政殿在**东列第三进**（x = +20.5、
       殿心 z = 7），紧挨考功殿南侧，所以屏风 z = 11.5（殿心 + 4.5）。
       台基顶面 1.87 米 → 屏风中心 3.42 米（摆法说明见枢密殿那段）。 */
    tongzhengScreen = screens.add({
      id: 'tongzheng',
      position: [20.5, 3.42, 11.5],
      facing: 0,
      size: 'large',
      title: '通政殿 · 消息记录',
      subtitle: 'MESSAGE / DESPATCH',
      tone: 'idle',
      mount: (body) => {
        tongzhengPage = mountScreenPage(body, {
          nodeKey: 'tongzheng-hall',
          overviewPath: '/message/overview',
          overviewPermission: 'sys:message:record:list',
          pages: [MESSAGE_RECORD_PAGE],
          getToken: () => debugToken() ?? opts.getToken?.() ?? null,
          getPermissions: opts.getPermissions,
          // 题头灯跟业务页状态走：概览陈旧/失败时不该还亮绿灯
          onTone: (tone) => tongzhengScreen?.setTone(tone),
          onTitle: (title, subtitle) => {
            tongzhengScreen?.setTitle(title ? `${title} · 消息记录` : '通政殿 · 消息记录',
              subtitle ?? 'MESSAGE / DESPATCH');
          },
        });
        return () => {
          tongzhengPage?.dispose();
          tongzhengPage = null;
        };
      },
    });

    /* 起居殿（S5/T5.9）：审计留痕是否在正常工作。比起"抓到一次违规"，这一屏更怕
       "该记的那段是空白"——摄入断流、归档停摆都不报错，等查账才发现没证据。
       位置同前几殿：东列第四进（x = +20.5、殿心 z = 21），屏风 z = 25.5（殿心 + 4.5），
       台基顶面 1.87 米 → 屏风中心 3.42 米（摆法说明见枢密殿那段）。 */
    qijuScreen = screens.add({
      id: 'qiju',
      position: [20.5, 3.42, 25.5],
      facing: 0,
      size: 'large',
      title: '起居殿 · 审计日志',
      subtitle: 'AUDIT / RECORD',
      tone: 'idle',
      mount: (body) => {
        qijuPage = mountScreenPage(body, {
          nodeKey: 'qiju-hall',
          overviewPath: '/audit/overview',
          overviewPermission: 'sys:audit:log:list',
          pages: [AUDIT_LOG_PAGE],
          getToken: () => debugToken() ?? opts.getToken?.() ?? null,
          getPermissions: opts.getPermissions,
          // 题头灯跟业务页状态走：概览陈旧/失败时不该还亮绿灯
          onTone: (tone) => qijuScreen?.setTone(tone),
          onTitle: (title, subtitle) => {
            qijuScreen?.setTitle(title ? `${title} · 审计日志` : '起居殿 · 审计日志',
              subtitle ?? 'AUDIT / RECORD');
          },
        });
        return () => {
          qijuPage?.dispose();
          qijuPage = null;
        };
      },
    });

    /* 遮挡演示：御道边一根拴马桩，挂进遮挡层。
       从出生点看过去，桩子应该切掉屏风的一角——这是 S0 T0.3 在真实场景、真实
       几何、真实光照下的复核。只有这一根进遮挡层，遮挡 pass 的开销可以忽略。 */
    const post = new THREE.Group();
    const shaft = new THREE.Mesh(new THREE.CylinderGeometry(0.16, 0.2, 2.4, 10), matStone);
    shaft.position.y = 1.2;
    shaft.castShadow = true;
    const base = new THREE.Mesh(new THREE.BoxGeometry(0.86, 0.22, 0.86), matWallDark);
    base.position.y = 0.11;
    base.castShadow = true;
    base.receiveShadow = true;
    post.add(shaft, base);
    /* 位置是按"从近处正对时刚好切到屏风左边框"定的：
       相机在玩家 +Z 侧时，桩子在 x=-8.5 的水平角约 7.7°，略小于屏风左缘的 8.1°，
       于是它只压住纸面最左一小条——够证明遮挡生效，又不挡住正文。 */
    post.position.set(-8.5, 0, 63.9);
    scene.add(post);
    screens.setOccluders([post]);
    } catch (err) {
      /* 屏风坏了不该带崩整座城：城还是能走能看的，只是没有界面 */
      console.error('[screen] setup failed', err);
    }
  }
  setupScreens();

  /* 开场 */
  applySeason();
  world.time = 0.34;
  animate();
  setTimeout(() => {
    document.getElementById('bww-loading').classList.add('bww-done');
    whaleSay('主公，欢迎回城。我是小鲸宝，跟着我走。');
  }, 700);
  setTimeout(() => {
    document.getElementById('bww-loading').style.display = 'none';
  }, 1600);

  /* 首次入城：引导录入形象（已有档案则直接进城） */
  if (!profileState.saved) {
    setTimeout(() => {
      if (disposed) {
        return;
      }
      charPanel?.open();
      showToast('初次入城', '录个姓名与形貌 · 按 C 可随时重录');
    }, 2100);
  }

  /* 城情采集：进场景即问一遍，之后每 60 秒一次（页面不可见时自动跳过） */
  metrics.start();

  /* 仅 ?debug=1 时暴露最小调试接口：给 e2e 断言玩家坐标用，不影响正常路径 */
  if (new URLSearchParams(window.location.search).has('debug')) {
    window.__bwwDebug = () => ({
      x: Number(player.pos.x.toFixed(2)),
      z: Number(player.pos.z.toFixed(2)),
      yaw: Number(player.yaw.toFixed(2)),
      npcs: npcs.list.length,
      peers: channel.peers.length,
      profile: { ...profile },
      brainMode: brain.mode,
      brainReason: brain.reason,
      chat: { open: Boolean(npcChat?.isOpen()), persona: npcChat?.personaId() ?? null },
      online: channel.mode,
    });
    /* 画质档探针（视觉合并批1）：读当前档位 / 手动切换（low 直渲，mid/high 走后处理） */
    window.__bwwQuality = () => quality;
    window.__bwwSetQuality = (q: 'high' | 'mid' | 'low') => setQuality(q);
    /* 数据厅探针（S3）：读厅状态 / 开关 / 切厅 / 导出卡面位图（e2e 与截图评审用） */
    window.__bwwDataHall = () => ({
      active: Boolean(dataHall?.isActive()),
      current: dataHall?.current() ?? null,
      hallCount: metrics.cards.size,
      refreshing: metrics.isRunning(),
      hallRefresh: hallRefreshTimer !== null,
      cameraY: Number(camera.position.y.toFixed(1)),
      // S4 契约概览：接了哪些节点、采集器是否在跑（e2e 断言"离厅即停"）
      overviewNodes: OVERVIEW_HALLS.length,
      overviewRunning: hallOverview?.isRunning() ?? false,
      overviewHalls: [...overviewHallData.keys()],
    });
    window.__bwwHallOpen = (index) => dataHall?.open(index ?? 0);
    window.__bwwHallClose = () => dataHall?.close();
    window.__bwwHallNext = () => dataHall?.next();
    window.__bwwHallPrev = () => dataHall?.prev();
    window.__bwwHallFace = (kind, index) => dataHall?.faceOf(kind, index) ?? null;
    /* 演示数据探针：无后端/无权限时图表全是"无可比数据"，评审看不到真出图。
       这里注入一份确定的假数据（只在 ?debug=1 下存在、只喂数据厅、不碰牌坊与正常路径），
       连喂 5 次让折线积累 5 个采样点，便于截图评审 KPI/曲线/柱/环在"有数"时的样子。 */
    window.__bwwHallMock = () => {
      const keys = [...metrics.cards.keys()];
      // 连喂 5 次，每次微增主指标，让折线积累 5 个真实采样点
      for (let k = 0; k < 5; k++) {
        const demo = [];
        for (let n = 0; n < keys.length; n++) {
          const key = keys[n];
          demo.push({
            nodeKey: key,
            name: palaceName(key),
            sub: PALACES.find((p) => p.nodeKey === key)?.sub ?? key,
            state: 'live',
            tone: n === 2 ? 'warn' : 'good',
            note: '演示数据',
            updatedAt: Date.now(),
            metrics: [
              { label: '营收', value: `${(12.4 + n * 0.7 + k * 0.3).toFixed(1)}万`, tone: 'good' },
              { label: '订单', value: `${2840 + n * 137}`, tone: 'flat' },
              { label: '预警', value: `${n % 3}`, tone: n % 3 > 0 ? 'warn' : 'good' },
              { label: '达成', value: `${(88 + n).toFixed(1)}%`, tone: 'good' },
            ],
          });
        }
        dataHall?.setData(demo);
      }
    };
    /* 时段探针（视觉合并批3）：直接设 world.time（0..1，0.5=正午 0.25=日出 0.75=日落）。
     * e2e 与截图评审需要确定的昼夜相位，靠等 150s 一天的真实周期不可靠。 */
    window.__bwwSetTime = (t: number) => {
      world.time = t;
    };
    /* 出城探针（视觉合并批4）：门楼外立面/城外河道的机位都被 OUTER-4 的钳位
     * 挡住，截图脚本必须先开这个。只在 ?debug=1 下存在。 */
    window.__bwwFreeRoam = (v: boolean) => {
      freeRoam = Boolean(v);
    };
    /* 牌坊状态探针：给 e2e 断言"哪个殿拿到了真数、哪个殿降级了" */
    window.__bwwGlyphs = () =>
      [...metrics.cards.values()].map((card) => ({
        nodeKey: card.nodeKey,
        state: card.state,
        note: card.note,
        metrics: card.metrics.map((m) => `${m.label}=${m.value}`),
      }));
    /* 导出牌面位图：牌立着但远处读不清时，直接看画出来的那张图 */
    window.__bwwGlyphFace = (nodeKey) => glyphs.faceOf(nodeKey);
    /* 喊话探针：不经过 UI 直接发一句（验证通道），以及读当前屏幕上的气泡 */
    window.__bwwSay = (text) => channel.say(text);
    window.__bwwSpeeches = () =>
      [...document.querySelectorAll('#bww-speech .bww-speech')].map((el) => el.textContent ?? '');
    window.__bwwWarp = (x, z) => {
      // 传送式挪位会作废现有路径，先停掉自动行走，否则会朝旧目标倒着走回去
      cancelAutoWalk(null);
      player.speed = 0;
      player.pos.set(x, 0, z);
    };
    /* 城门楼地标探针（S5/T5.10）：地标表、脚下最近的门楼、以及提示条当前文案。
       门楼没有内页，能自动验的只有"认得出 + 说明没骗人"这两件事。 */
    window.__bwwLandmarks = () => ({
      gates: GATE_TOWERS.map((gate) => ({
        nodeKey: gate.nodeKey,
        name: gate.name,
        tier: gate.tier,
        landmark: gate.landmark,
        x: gate.x,
        z: gate.z,
        gateHalf: gate.gateHalf,
      })),
      // 用玩家真实坐标算，而不是复用每帧缓存：e2e 的 warp 与下一帧之间可能还没跑
      near: (() => {
        const hit = nearestGate(player.pos.x, player.pos.z);
        return hit
          ? {
              nodeKey: hit.spec.nodeKey,
              name: hit.spec.name,
              distance: Number(hit.distance.toFixed(2)),
            }
          : null;
      })(),
      hint: {
        shown: portalHint.classList.contains('bww-show'),
        text: (portalHint.textContent ?? '').replace(/\s+/g, ' ').trim(),
      },
      panelOpen: portalPanel.classList.contains('bww-open'),
    });
    /* 城图派生尺度：断言"城图 = 布局数据"，不去读画布像素（软渲染下像素不可靠） */
    window.__bwwMinimap = () => ({
      ...(minimap ? minimap.geometry() : null),
      visible: mapVisible,
    });
    /* 自动寻路探针：读状态，以及不经鼠标直接设目标（单独验算路） */
    window.__bwwPath = () => ({
      active: autoWalk.active,
      index: autoWalk.index,
      total: autoWalk.points.length,
      goalX: Number(autoWalk.goal.x.toFixed(2)),
      goalZ: Number(autoWalk.goal.z.toFixed(2)),
      nextX: autoWalk.points[autoWalk.index]
        ? Number(autoWalk.points[autoWalk.index].x.toFixed(2))
        : null,
      nextZ: autoWalk.points[autoWalk.index]
        ? Number(autoWalk.points[autoWalk.index].z.toFixed(2))
        : null,
    });
    window.__bwwWalkTo = (x, z) => setWalkTarget(x, z);
    /* 目的地光环探针：e2e 断言"点了有回执、到了就消失"。视觉对不对仍需人眼，但
       "根本没画出来"这种失败必须能被自动抓到。 */
    window.__bwwMarker = () => ({
      visible: markerRoot.visible,
      x: Number(markerRoot.position.x.toFixed(2)),
      z: Number(markerRoot.position.z.toFixed(2)),
      fade: Number(markerFade.toFixed(2)),
      opacity: Number((markerMats[0]?.opacity ?? 0).toFixed(2)),
    });
    /* 世界点 → 屏幕像素：e2e 要用真正的鼠标右键（而不是直接调函数）验证事件绑定 */
    window.__bwwScreenOf = (x, z) => {
      const v = new THREE.Vector3(x, terrainHeight(x, z), z).project(camera);
      const rect = renderer.domElement.getBoundingClientRect();
      return {
        x: rect.left + ((v.x + 1) / 2) * rect.width,
        y: rect.top + ((1 - v.y) / 2) * rect.height,
        front: v.z < 1,
      };
    };
    /* 屏风页探针（S4 / T4.5）：读业务页状态（概览/标签/列表/详情），
       以及"进屏风 / 注入演示数据"两个动作。只在 ?debug=1 下存在，
       正常路径既不暴露业务字段，也不会注入任何假数据。

       S5 收尾时皇城共十块业务屏风（户部 / 数枢殿 / 枢密殿 / 度支殿 / 藏书殿 / 时辰殿 /
       政事殿 / 考功殿 / 通政殿 / 起居殿），S6 起逐坊增加内城坊（工部 / 吏部 / 礼部 / 兵部 / 杏坛 …），
       按 id 分派；不给 id 时仍落到户部（S4 的老脚本靠这个默认）。 */
    /* 屏风 id → 业务页句柄。默认给户部：老脚本不带 id 调探针时的历史行为。 */
    const pageOf = (id?: string): ScreenPageHandle | null => {
      switch (id) {
        case 'shushu':
          return shushuPage;
        case 'shumi':
          return shumiPage;
        case 'duzhi':
          return duzhiPage;
        case 'cangshu':
          return cangshuPage;
        case 'shichen':
          return shichenPage;
        case 'zhengshi':
          return zhengshiPage;
        case 'kaogong':
          return kaogongPage;
        case 'tongzheng':
          return tongzhengPage;
        case 'qiju':
          return qijuPage;
        case 'gongbu':
          return gongbuPage;
        case 'libu':
          return libuPage;
        case 'libu-ritual':
          return libuRitualPage;
        case 'bingbu':
          return bingbuPage;
        case 'xingtan':
          return xingtanPage;
        case 'jiangzuo':
          return jiangzuoPage;
        case 'yingzao':
          return yingzaoPage;
        case 'mengfu':
          return mengfuPage;
        case 'xuanfu':
          return xuanfuPage;
        case 'hanlin':
          return hanlinPage;
        case 'shibo':
          return shiboPage;
        default:
          return hubuPage;
      }
    };
    window.__bwwScreenPage = (id?: string) => pageOf(id)?.probe() ?? null;
    window.__bwwScreenOpen = (id: string) => screens.get(id)?.enter();
    window.__bwwScreenExit = () => screens.exit();
    window.__bwwScreenMock = (id?: string) => pageOf(id)?.mock();
    /* 声景探针：e2e 无法"听"，只能读状态（是否解锁、是否静音）与脚下地面判定 */
    window.__bwwAudio = () => ({
      ready: audio.isReady(),
      muted: audio.isMuted(),
      ground: groundAt(player.pos.x, player.pos.z),
    });
    window.__bwwSound = () => toggleSound();
    /* 天象探针：e2e 无法"看"，只能读强度与可见性——真好不好看仍需人眼 */
    window.__bwwWeather = () => ({
      weather: world.weather,
      wet: Number(world.weatherMix[1].toFixed(3)),
      snow: Number(world.weatherMix[2].toFixed(3)),
      splashes: splashes.filter((s) => s.life > 0).length,
      splashVisible: splashRoot.visible,
      /* 第一个活跃水花的实际状态：看不见时用来分辨"没画"还是"太淡" */
      splashSample: (() => {
        const s = splashes.find((item) => item.life > 0);
        return s
          ? {
              x: Number(s.mesh.position.x.toFixed(1)),
              y: Number(s.mesh.position.y.toFixed(2)),
              z: Number(s.mesh.position.z.toFixed(1)),
              op: Number(s.mat.opacity.toFixed(2)),
              scale: Number(s.mesh.scale.x.toFixed(2)),
              vis: s.mesh.visible,
            }
          : null;
      })(),
      umbrella: Number(npcs.wetOpacity().toFixed(3)),
    });
    window.__bwwSetWeather = (kind) => {
      world.weather = kind;
      audio.setWeather(kind);
      uiRoot.querySelectorAll('[data-action="weather"]').forEach((b) => {
        b.textContent = `天象：${WEATHER[kind]}`;
      });
      // 渐变是逐帧插值的，e2e 不必等 3 秒：直接把目标值顶到位
      world.weatherMix = [0, 0, 0, 0];
      world.weatherMix[kind] = 1;
    };
    /* 委托探针：状态快照 + 面板文字，e2e 据此断言"接了才推进、交付才清零" */
    window.__bwwQuests = () => quests.snapshot();
    window.__bwwQuestHud = () => ({
      visible: Boolean(questHud?.classList.contains('bww-show')),
      text: (questHud?.textContent ?? '').replace(/\s+/g, ' ').trim(),
    });
    /* 头顶标记：读 name/role 标签上的 data-quest，验证"可接/可交"是否真的画到人头上 */
    window.__bwwQuestMarks = () =>
      npcs.list.map((n) => ({
        id: n.def.id,
        mark: npcLabels.get(n.def.id)?.dataset.quest ?? null,
        text: npcLabels.get(n.def.id)?.querySelector('.bww-nlabel-quest')?.textContent ?? '',
      }));
    window.__bwwQuestAccept = (id) => {
      quests.accept(id);
      syncQuestUi();
    };
    window.__bwwQuestComplete = (id) => {
      quests.complete(id);
      syncQuestUi();
    };
    window.__bwwQuestReset = () => {
      quests.reset();
      syncQuestUi();
    };
    /* NPC 坐标：e2e 要"走到韩五身边按 F"，而他是巡逻的，位置不固定 */
    window.__bwwNpcs = () =>
      npcs.list.map((n) => ({
        id: n.def.id,
        name: n.def.name,
        x: Number(n.pos.x.toFixed(2)),
        z: Number(n.pos.z.toFixed(2)),
      }));
    /* 导览探针：读进度与当前目标，以及不按键直接开始（e2e 走完整条路线用） */
    window.__bwwGuide = () => {
      const s = guide.status();
      return {
        active: s.active,
        index: s.index,
        total: s.total,
        name: s.name,
        targetX: Number(s.targetX.toFixed(2)),
        targetZ: Number(s.targetZ.toFixed(2)),
        done: s.done,
      };
    };
    window.__bwwGuideStops = () =>
      GUIDE_STOPS.map((s) => ({ nodeKey: s.nodeKey, name: s.name, x: s.x, z: s.z }));
    /* 直接定轨道相机：e2e 需要"站在某点、朝某处看"的确定视角，靠拖拽估算角度不可靠。
       注意这里的 yaw 是**相机**绕玩家的方位（cam.yaw），不是角色朝向（player.yaw） */
    /* 取景探针（视觉合并批4）：把相机环绕点抬到 rise 米，用于评审匾额/屋顶。
     * 正常操作里的 pitch 是俯角且不许为负，抬高处的构件只能靠这个看。 */
    window.__bwwLookAt = (yaw, pitch, dist, rise = 0) => {
      cam.yaw = yaw;
      cam.pitch = clamp(pitch, -0.6, 1.15);
      cam.dist = Math.max(3, dist);
      cam.rise = rise;
    };
    window.__bwwLook = (yaw, pitch, dist) => {
      cam.rise = 0;
      cam.yaw = yaw;
      if (typeof pitch === 'number') {
        cam.pitch = clamp(pitch, 0.08, 1.15);
      }
      if (typeof dist === 'number') {
        cam.dist = Math.max(3, dist);
      }
    };
    /* 问话链路探针：定位到某个 NPC 身旁（真实走 F 键的邻近判定），或直接点名要问谁 */
    window.__bwwNpcAt = (id) => {
      const npc = npcs.list.find((n) => n.def.id === id) ?? null;
      if (!npc) {
        return null;
      }
      return {
        id: npc.def.id,
        name: npc.def.name,
        x: Number(npc.pos.x.toFixed(2)),
        z: Number(npc.pos.z.toFixed(2)),
      };
    };
    window.__bwwTalk = (id) => {
      if (id === 'pet-whale') {
        talkToPet();
        return true;
      }
      const npc = npcs.list.find((n) => n.def.id === id);
      if (!npc) {
        return false;
      }
      npcChat?.open(npcPersona(npc.def));
      return true;
    };
    window.__bwwAsk = (text) => {
      npcChat?.ask(text);
    };
    window.__bwwChatLog = () =>
      Array.from(document.querySelectorAll('#bww-chat-panel .bww-chat-msg')).map((el) => ({
        who: el.className.includes('is-me') ? 'me' : 'npc',
        text: el.textContent ?? '',
      }));
  }

  function dispose() {
    if (disposed) return;
    disposed = true;
    cancelAnimationFrame(rafId);
    if (hallRefreshTimer !== null) {
      window.clearInterval(hallRefreshTimer);
      hallRefreshTimer = null;
    }
    dataHall?.dispose();
    dataHall = null;
    cleanups.forEach((fn) => fn());
    cleanups.length = 0;
    clearTimeout(toastTimer);
    clearTimeout(whaleSay.timer);
    npcSayTimers.forEach((timer) => clearTimeout(timer));
    npcSayTimers.clear();
    charPanel?.destroy();
    charPanel = null;
    npcChat?.destroy();
    brain.dispose();
    metrics.dispose();
    hallOverview?.dispose();
    glyphs.dispose();
    sayPanel?.destroy();
    speech?.dispose();
    audio.dispose();
    playerRig.dispose();
    npcs.dispose();
    channel.dispose();
    for (const item of peerRigs.values()) {
      item.rig.dispose();
    }
    peerRigs.clear();
    minimap?.dispose();
    screens.dispose();
    scene.traverse((obj) => {
      const o = obj;
      if (o.geometry) o.geometry.dispose();
      const mats = Array.isArray(o.material) ? o.material : o.material ? [o.material] : [];
      for (const mat of mats) {
        for (const v of Object.values(mat)) {
          if (v && v.isTexture) v.dispose();
        }
        mat.dispose();
      }
    });
    scene.clear();
    postfx.dispose();
    env?.dispose();
    renderer.dispose();
    renderer.domElement.remove();
  }

  return { dispose };
}
