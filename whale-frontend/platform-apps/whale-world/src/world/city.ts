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
import { createSpeech } from './speech';
import { mountSayPanel } from './saypanel';
import { createWorldAudio } from './audio';
import type { GroundKind } from './audio';
import { GUIDE_LINES, createGuide, fallbackLine } from './guide';
import { CITY_GEOMETRY, MAIN_HALL, MAIN_PORTAL, PALACES, SIDE_HALLS } from './layout';
import {
  WORLD_BOUND,
  camBlocked as isCamBlocked,
  collide as resolveCollide,
  createBlockers,
} from './collision';
import { createNavGrid } from './path';
import { createQuestSystem } from './quests';
import type { CharPanelHandle } from './charpanel';
import type { AvatarParams } from './avatar';
import type { NpcChatHandle } from './npcchat';

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

  /* 工具栏、HUD、各种面板都是 #bww-scene 的**兄弟节点**（React 里同一层渲染），
   * 不在 host 里面。所有按 data-action 找按钮的代码必须从页面根查，
   * 否则 querySelectorAll 恒返回空——按钮看着在、点了没反应，而且不报任何错。 */
  const uiRoot = host.closest('.bww-page') ?? host.parentElement ?? host;

  const hemi = new THREE.HemisphereLight(0x9fc7ff, 0x2c2a20, 0.5);
  scene.add(hemi);

  const sunLight = new THREE.DirectionalLight(0xfff0cf, 2.1);
  sunLight.castShadow = true;
  sunLight.shadow.mapSize.set(2048, 2048);
  sunLight.shadow.camera.left = -130;
  sunLight.shadow.camera.right = 130;
  sunLight.shadow.camera.top = 130;
  sunLight.shadow.camera.bottom = -130;
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

  function box(w, h, d, mat, cast = true) {
    const m = new THREE.Mesh(new THREE.BoxGeometry(w, h, d), mat);
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
    { x: (z) => 155 + Math.sin(z * 0.017) * 10, joinZ: -198 }, // 中支·清溪（城东南侧）
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
   *  玩家被限在城墙内（OUTER - 4 = 88 米），够不到 132 米外的河，所以没有"涉水"这一档。
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
  };
  const waterMat = new THREE.ShaderMaterial({
    uniforms: waterUniforms,
    transparent: true,
    vertexShader: /* glsl */ `
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
  `,
    fragmentShader: /* glsl */ `
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
  `,
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

  // 主河·北江（东西向，含江湾段；槽深 13，水面 -4.4 与原河一致）
  scene.add(riverRibbon((t) => ({ x: t, z: NORTH_RIVER(t) }), -380, 380, 24, -4.4));
  // 支流（南 → 北汇入北江；水面与主河同高避免汇口台阶，上游没入南岭山体）
  for (const t of TRIBS) {
    scene.add(riverRibbon((s) => ({ x: t.x(s), z: s }), t.joinZ - 22, 300, 9, -4.4, 60));
  }

  /* ==========================================================================
   * 3. 天空 / 太阳 / 月亮 / 星空
   * ========================================================================== */
  const skyUniforms = {
    uTop: { value: new THREE.Color(0x2f6fb0) },
    uBottom: { value: new THREE.Color(0xbfe0f5) },
    uSunDir: { value: new THREE.Vector3(0, 1, 0) },
    uSunColor: { value: new THREE.Color(0xfff2d0) },
    uHaze: { value: 0.0 },
  };
  const sky = new THREE.Mesh(
    new THREE.SphereGeometry(1600, 32, 24),
    new THREE.ShaderMaterial({
      uniforms: skyUniforms,
      side: THREE.BackSide,
      depthWrite: false,
      vertexShader: /* glsl */ `
      varying vec3 vDir;
      void main() {
        vDir = normalize(position);
        gl_Position = projectionMatrix * viewMatrix * modelMatrix * vec4(position, 1.0);
      }
    `,
      fragmentShader: /* glsl */ `
      uniform vec3 uTop;
      uniform vec3 uBottom;
      uniform vec3 uSunDir;
      uniform vec3 uSunColor;
      uniform float uHaze;
      varying vec3 vDir;
      void main() {
        float h = clamp(vDir.y * 0.5 + 0.5, 0.0, 1.0);
        vec3 col = mix(uBottom, uTop, pow(h, 0.85));
        float sd = max(dot(normalize(vDir), normalize(uSunDir)), 0.0);
        col += uSunColor * pow(sd, 420.0) * 2.4;
        col += uSunColor * pow(sd, 10.0) * 0.30;
        col = mix(col, vec3(0.62, 0.68, 0.72) * (0.35 + h * 0.6), uHaze * 0.75);
        gl_FragColor = vec4(col, 1.0);
      }
    `,
    }),
  );
  sky.renderOrder = -10;
  scene.add(sky);

  const sunMesh = new THREE.Mesh(
    new THREE.SphereGeometry(22, 20, 16),
    new THREE.MeshBasicMaterial({ color: 0xfff6d8 }),
  );
  scene.add(sunMesh);

  const moonMesh = new THREE.Mesh(
    new THREE.SphereGeometry(16, 20, 16),
    new THREE.MeshBasicMaterial({ color: 0xdfe8ff }),
  );
  scene.add(moonMesh);

  // 星空
  const starCount = 1800;
  const starGeo = new THREE.BufferGeometry();
  {
    const arr = new Float32Array(starCount * 3);
    const size = new Float32Array(starCount);
    for (let i = 0; i < starCount; i++) {
      const u = Math.random();
      const v = Math.random() * 0.85 + 0.05;
      const theta = u * Math.PI * 2;
      const phi = Math.acos(1 - 2 * v * 0.5);
      const r = 1400;
      arr[i * 3] = r * Math.sin(phi) * Math.cos(theta);
      arr[i * 3 + 1] = Math.abs(r * Math.cos(phi)) * 0.9 + 60;
      arr[i * 3 + 2] = r * Math.sin(phi) * Math.sin(theta);
      size[i] = Math.random() * 2.2 + 0.6;
    }
    starGeo.setAttribute('position', new THREE.BufferAttribute(arr, 3));
    starGeo.setAttribute('aSize', new THREE.BufferAttribute(size, 1));
  }
  const starMat = new THREE.PointsMaterial({
    color: 0xffffff,
    size: 3.4,
    sizeAttenuation: true,
    transparent: true,
    opacity: 0,
    depthWrite: false,
  });
  const stars = new THREE.Points(starGeo, starMat);
  stars.renderOrder = -9;
  scene.add(stars);

  /* ==========================================================================
   * 4. 城墙
   * ========================================================================== */
  const merlonMatrices = [];
  const _q = new THREE.Quaternion();
  const _m4 = new THREE.Matrix4();
  const _v3 = new THREE.Vector3();
  const _s3 = new THREE.Vector3(1, 1, 1);

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
  function buildWallSide(half, height, thick, along, sign, gateW, gateH, group) {
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

    // 垛口
    if (along === 'x') {
      addMerlons(-half, sign * half, -gateW / 2 - 0.5, sign * half, height);
      addMerlons(gateW / 2 + 0.5, sign * half, half, sign * half, height);
    } else {
      addMerlons(sign * half, -half, sign * half, -gateW / 2 - 0.5, height);
      addMerlons(sign * half, gateW / 2 + 0.5, sign * half, half, height);
    }
  }

  /** 城门楼 */
  function buildGateTower(x, z, yaw, scale = 1, roofColor = 0x2f6b5f) {
    const g = new THREE.Group();
    g.position.set(x, 0, z);
    g.rotation.y = yaw;

    const base = box(20 * scale, 4 * scale, 12 * scale, matStone);
    base.position.y = 12 * scale;
    g.add(base);

    const hall = box(15 * scale, 6 * scale, 9 * scale, matRed);
    hall.position.y = 17 * scale;
    g.add(hall);

    const roof = new THREE.Mesh(
      new THREE.CylinderGeometry(
        (11.5 * scale) / Math.SQRT2,
        (18 * scale) / Math.SQRT2,
        3.4 * scale,
        4,
      ),
      M(roofColor, { roughness: 0.6, metalness: 0.15 }),
    );
    roof.rotation.y = Math.PI / 4;
    roof.position.y = 21.7 * scale;
    roof.castShadow = true;
    g.add(roof);

    const ridge = box(16 * scale, 0.55, 0.55, matGold);
    ridge.position.y = 23.5 * scale;
    g.add(ridge);

    return g;
  }

  const cityGroup = new THREE.Group();
  scene.add(cityGroup);

  // 外城（尺度来自 city-layout.json）
  const OUTER = CITY_GEOMETRY.outerWall;
  buildWallSide(OUTER, 10, 3.2, 'x', -1, 16, 9.2, cityGroup);
  buildWallSide(OUTER, 10, 3.2, 'x', 1, 16, 9.2, cityGroup);
  buildWallSide(OUTER, 10, 3.2, 'z', -1, 16, 9.2, cityGroup);
  buildWallSide(OUTER, 10, 3.2, 'z', 1, 16, 9.2, cityGroup);
  cityGroup.add(buildGateTower(0, OUTER, 0, 1.0, 0x2f6b5f));
  cityGroup.add(buildGateTower(0, -OUTER, Math.PI, 1.0, 0x2f6b5f));
  cityGroup.add(buildGateTower(OUTER, 0, -Math.PI / 2, 1.0, 0x2f6b5f));
  cityGroup.add(buildGateTower(-OUTER, 0, Math.PI / 2, 1.0, 0x2f6b5f));

  // 内城
  const INNER = CITY_GEOMETRY.innerWall;
  buildWallSide(INNER, 8, 2.4, 'x', -1, 12, 7.2, cityGroup);
  buildWallSide(INNER, 8, 2.4, 'x', 1, 12, 7.2, cityGroup);
  buildWallSide(INNER, 8, 2.4, 'z', -1, 12, 7.2, cityGroup);
  buildWallSide(INNER, 8, 2.4, 'z', 1, 12, 7.2, cityGroup);
  cityGroup.add(buildGateTower(0, INNER, 0, 0.78, 0x3f5f78));
  cityGroup.add(buildGateTower(0, -INNER, Math.PI, 0.78, 0x3f5f78));

  // 垛口合批
  {
    const merlonGeo = new THREE.BoxGeometry(1.7, 1.6, 0.8);
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

  /** 计算宫殿朝向：面向城池中心 */
  const faceCenter = (x, z) => Math.atan2(-x, -z);

  // 皇城墙
  {
    const R = CITY_GEOMETRY.royalWall;
    buildWallSide(R, 12, 2.6, 'x', -1, 10, 8.5, palaceGroup);
    buildWallSide(R, 12, 2.6, 'x', 1, 10, 8.5, palaceGroup);
    buildWallSide(R, 12, 2.6, 'z', -1, 10, 8.5, palaceGroup);
    buildWallSide(R, 12, 2.6, 'z', 1, 10, 8.5, palaceGroup);
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

  /* ---- 八方宫殿（对应业务子系统，坐标与配色来自 city-layout.json） ---- */
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

  /* ==========================================================================
   * 6. 道路 / 灯笼 / 传送门
   * ========================================================================== */
  // 环形路
  {
    const ring = new THREE.Mesh(new THREE.RingGeometry(RING_R - 13, RING_R + 13, 96), matRoad);
    ring.rotation.x = -Math.PI / 2;
    ring.position.y = 0.06;
    ring.receiveShadow = true;
    scene.add(ring);

    const innerRing = new THREE.Mesh(new THREE.RingGeometry(30, 38, 72), matRoad);
    innerRing.rotation.x = -Math.PI / 2;
    innerRing.position.y = 0.06;
    innerRing.receiveShadow = true;
    scene.add(innerRing);
  }

  // 中轴御道
  {
    const axis = new THREE.Mesh(new THREE.PlaneGeometry(13, 176), matRoad);
    axis.rotation.x = -Math.PI / 2;
    axis.position.set(0, 0.07, 4);
    axis.receiveShadow = true;
    scene.add(axis);

    const cross = new THREE.Mesh(new THREE.PlaneGeometry(13, 176), matRoad);
    cross.rotation.x = -Math.PI / 2;
    cross.rotation.z = Math.PI / 2;
    cross.position.set(0, 0.065, 0);
    cross.receiveShadow = true;
    scene.add(cross);
  }

  // 灯笼：沿御道两侧
  const lanterns = [];
  {
    const poleGeo = new THREE.CylinderGeometry(0.11, 0.15, 4.6, 8);
    const ballGeo = new THREE.SphereGeometry(0.38, 12, 10);
    for (let i = 0; i < 11; i++) {
      const z = 86 - i * 16;
      for (const s of [-1, 1]) {
        const px = s * 7.4;
        if (Math.abs(z) < 26 || (Math.abs(z) > 38 && Math.abs(z) < 58)) continue;
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

  const metrics = createWorldMetrics(PALACES, {
    // 与问话共用同一套令牌/权限来源；debug 注入的令牌也让牌坊受益
    getToken: () => debugToken() ?? opts.getToken?.() ?? null,
    getPermissions: opts.getPermissions,
    onChange: (cards) => glyphs.update(cards),
  });
  // 先把"问询中/上次 xx:xx"的初始态画上去：牌子一开始就该是块牌子，而不是空白木板
  glyphs.update(metrics.cards);

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
    keys.add(e.code);
    if (e.code === 'KeyE') togglePortalPanel();
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

    sunMesh.position.copy(sunDir).multiplyScalar(900);
    moonMesh.position.copy(sunDir).multiplyScalar(-900);

    const h = sunDir.y; // -1..1
    const dayF = smooth(h, -0.06, 0.34);
    const duskF = 1 - smooth(Math.abs(h), 0.02, 0.42);

    const top = new THREE.Color().copy(SKY_NIGHT_TOP).lerp(SKY_DAY_TOP, dayF);
    const bot = new THREE.Color().copy(SKY_NIGHT_BOT).lerp(SKY_DAY_BOT, dayF);
    top.lerp(SKY_DUSK_TOP, duskF * 0.75);
    bot.lerp(SKY_DUSK_BOT, duskF * 0.8);

    const haze = world.weatherMix[3];
    skyUniforms.uTop.value.copy(top);
    skyUniforms.uBottom.value.copy(bot);
    skyUniforms.uSunDir.value.copy(sunDir);
    skyUniforms.uHaze.value = haze;

    sunLight.position.copy(sunDir).multiplyScalar(220);
    sunLight.intensity = lerp(0.05, 2.5, dayF);
    const sunCol = new THREE.Color(0xffe0b0).lerp(new THREE.Color(0xfff6e0), smooth(h, 0, 0.4));
    sunLight.color.copy(sunCol).lerp(new THREE.Color(0xff8a3c), duskF * 0.7);

    moonLight.position.copy(sunDir).multiplyScalar(-220);
    moonLight.intensity = lerp(0.55, 0.0, dayF);

    hemi.intensity = lerp(0.14, 0.62, dayF);
    hemi.color.copy(new THREE.Color(0x2a3f6b).lerp(new THREE.Color(0xa8ccff), dayF));

    starMat.opacity = clamp(1 - dayF * 1.5, 0, 0.95) * (1 - haze * 0.9);

    fogColor.copy(bot).lerp(new THREE.Color(0x8f9aa0), haze * 0.6);
    scene.fog.color.copy(fogColor);
    scene.fog.density =
      lerp(0.0016, 0.0052, haze) + world.weatherMix[1] * 0.0016 + world.weatherMix[2] * 0.0012;

    // 灯笼夜间点亮
    const lanternOn = 1 - dayF;
    matLantern.color.copy(new THREE.Color(0x4a3226).lerp(new THREE.Color(0xff5a3c), lanternOn));
    matLanternGlow.opacity = 0.05 + lanternOn * 0.2;

    waterUniforms.uSky.value.copy(bot).multiplyScalar(1.0);
    sky.position.copy(camera.position);
    stars.position.copy(camera.position);
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

    // 城墙与建筑边界（简单推挤）
    const lim = OUTER - 4;
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
    camTarget.copy(player.pos).add(new THREE.Vector3(0, 2.4, 0));
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
    if (nearest && nearestDist < 7) {
      portalHint.classList.add('bww-show');
      portalHint.innerHTML = `按 <b>E</b> 打开传送门 · <span>${nearest.name}</span>`;
    } else {
      portalHint.classList.remove('bww-show');
    }
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
    updatePlayer(dt);
    // 位置更新之后才判达成：这一帧走到门口就该这一帧算数
    updateQuests(dt);
    updateWalkMarker(dt);
    // 脚步声跟速度走：静止时 update 内部会自己归零，不必在外面判
    audio.update(dt, player.speed, groundAt(player.pos.x, player.pos.z));
    updateActors(dt);
    updateCamera(dt);
    updateWhale(dt);
    updatePortals(dt);
    updateHUD();

    renderer.render(scene, camera);
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
  });

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
    window.__bwwLook = (yaw, pitch, dist) => {
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
    renderer.dispose();
    renderer.domElement.remove();
  }

  return { dispose };
}
