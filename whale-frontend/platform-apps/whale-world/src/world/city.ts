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
import {
  CITY_GEOMETRY,
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
  time: 0.30,          // 0..1 一天（0=子夜 0.5=正午）
  daySpeed: 1 / 150,   // 一整天 150 秒
  paused: false,
  season: 2,           // 0 春 1 夏 2 秋 3 冬
  weather: 0,          // 0 晴 1 雨 2 雪 3 雾
  weatherMix: [1, 0, 0, 0],
  sunDir: new THREE.Vector3(0, 1, 0),
};

/* ------------------------------------------------------------------ 渲染基础 */
const scene = new THREE.Scene();
scene.fog = new THREE.FogExp2(0x0c1a26, 0.0022);

const camera = new THREE.PerspectiveCamera(52, innerWidth / innerHeight, 0.4, 4000);

const renderer = new THREE.WebGLRenderer({ antialias: true, powerPreference: 'high-performance' });
renderer.setPixelRatio(Math.min(devicePixelRatio || 1, 2));
renderer.setSize(innerWidth, innerHeight);
renderer.shadowMap.enabled = true;
renderer.shadowMap.type = THREE.PCFSoftShadowMap;
renderer.toneMapping = THREE.ACESFilmicToneMapping;
renderer.toneMappingExposure = 1.02;
host.appendChild(renderer.domElement);

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
  color: 0xff6a3c, transparent: true, opacity: 0.16,
  blending: THREE.AdditiveBlending, depthWrite: false,
});

function box(w, h, d, mat, cast = true) {
  const m = new THREE.Mesh(new THREE.BoxGeometry(w, h, d), mat);
  m.castShadow = cast;
  m.receiveShadow = true;
  return m;
}

/* ==========================================================================
 * 1. 地形
 * ========================================================================== */

const RIVER_Z = (x) => -132 + Math.sin(x * 0.018) * 14;

function terrainHeight(x, z) {
  const d = Math.max(Math.abs(x), Math.abs(z));
  const out = smooth(d, 96, 150);      // 城外权重
  const far = smooth(d, 175, 320);     // 远山权重
  let h = 0;
  const hills =
    Math.sin(x * 0.031 + 1.7) * Math.cos(z * 0.026) * 5.5 +
    Math.sin(x * 0.012 - z * 0.017) * 9 +
    Math.cos(z * 0.048) * 2.2;
  h += out * (hills + 3.2);
  h += far * (16 + Math.sin(x * 0.008) * Math.cos(z * 0.009) * 26 + Math.sin(x * 0.023 + z * 0.019) * 9);
  // 河谷切槽
  const rd = Math.abs(z - RIVER_Z(x));
  const rmask = (1 - smooth(rd, 7, 36)) * out;
  h -= rmask * 14;
  return h;
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
  const cMud = new THREE.Color(0x6d6247);
  const tmpC = new THREE.Color();
  for (let i = 0; i < pos.count; i++) {
    const x = pos.getX(i);
    const z = pos.getZ(i);
    const h = terrainHeight(x, z);
    pos.setY(i, h);
    const noise = (Math.sin(x * 0.21) * Math.cos(z * 0.19) + 1) * 0.5;
    tmpC.copy(cGrassA).lerp(cGrassB, noise);
    if (h < -2) tmpC.lerp(cMud, smooth(-h, 2, 9));
    if (h > 8) tmpC.lerp(cRock, smooth(h, 8, 26));
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
const water = new THREE.Mesh(new THREE.PlaneGeometry(760, 110, 60, 12), waterMat);
water.rotation.x = -Math.PI / 2;
water.position.set(0, -4.4, -132);
water.renderOrder = 1;
scene.add(water);

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
  color: 0xffffff, size: 3.4, sizeAttenuation: true,
  transparent: true, opacity: 0, depthWrite: false,
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
    new THREE.CylinderGeometry((11.5 * scale) / Math.SQRT2, (18 * scale) / Math.SQRT2, 3.4 * scale, 4),
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
    const st = box((w * 0.42) * scale, 0.5 * scale, (1.5 - i * 0.18) * scale, matStone);
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
    new THREE.CylinderGeometry((w * 0.45 * scale) / Math.SQRT2, (eaveT * 0.86) / Math.SQRT2, 2.9 * scale, 4),
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
    kiss.position.set((s * eaveT * 0.31), baseH + bodyH + 5.65 * scale, 0);
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
  palaceGroup.add(createPalace({
    x: hall.x,
    z: hall.z,
    w: hall.w,
    d: hall.d,
    name: hall.name,
    roof: hall.roof,
    body: hall.body,
    scale: hall.scale,
    yaw: hall.yaw,
  }));
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
      color, transparent: true, opacity: 0.12,
      blending: THREE.AdditiveBlending, depthWrite: false, toneMapped: false,
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
    new THREE.Color(0x5f8f42), new THREE.Color(0x38632c), new THREE.Color(0xb8782a), new THREE.Color(0x7d8f7a),
  ];

  let n = 0;
  let guard = 0;
  while (n < treeCount && guard < treeCount * 40) {
    guard++;
    const a = Math.random() * Math.PI * 2;
    const r = 105 + Math.pow(Math.random(), 0.7) * 210;
    const x = Math.cos(a) * r;
    const z = Math.sin(a) * r;
    // 避开河谷
    if (Math.abs(z - RIVER_Z(x)) < 26) continue;
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

    const tint = colorSeason[world.season].clone().offsetHSL(
      (Math.random() - 0.5) * 0.06, (Math.random() - 0.5) * 0.1, (Math.random() - 0.5) * 0.12,
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

/* ---- 其他玩家（world 帧协议；连不上自动退回本地模拟） ---- */
const channel = createWorldChannel({
  getToken: opts.getToken,
  self: { name: profile.name, robe: profile.robe },
});

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
      onOpenChange: (open, persona) => {
        setTalking(open && persona ? persona.id : null);
        if (open) {
          closePortalPanel();
          charPanel?.close();
        }
      },
    })
  : null;

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
  npcChat.open(npcPersona(npc.def));
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
  const bodyMat = M(0x3f8fce, { roughness: 0.42, metalness: 0.08, emissive: 0x0b2d4a, emissiveIntensity: 0.55 });
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
    const shine = new THREE.Mesh(new THREE.SphereGeometry(0.045, 8, 8), new THREE.MeshBasicMaterial({ color: 0xffffff }));
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
  const blowhole = new THREE.Mesh(new THREE.SphereGeometry(0.1, 8, 8), new THREE.MeshBasicMaterial({ color: 0x2a5f85 }));
  blowhole.position.set(0, 0.92, -0.65);
  whale.add(blowhole);

  // 环绕光点
  const aura = new THREE.Mesh(
    new THREE.SphereGeometry(1.9, 16, 12),
    new THREE.MeshBasicMaterial({
      color: 0x7fd8ff, transparent: true, opacity: 0.07,
      blending: THREE.AdditiveBlending, depthWrite: false,
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
renderer.domElement.addEventListener('wheel', (e) => {
  e.preventDefault();
  cam.dist = clamp(cam.dist + e.deltaY * 0.014, 8, 48);
}, { passive: false });

on(window, 'keydown', (e) => {
  // 问话面板的输入框本身是 typing target：Esc 要能整块收起，而不是只让输入框失焦
  if (e.code === 'Escape' && npcChat?.isOpen()) {
    npcChat.close();
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
  if (e.code === 'Escape') {
    closePortalPanel();
    charPanel?.close();
    npcChat?.close();
  }
  if (e.code === 'KeyR') { cam.yaw = 0; cam.pitch = 0.44; cam.dist = 16; }
  if (e.code === 'KeyC') {
    closePortalPanel();
    charPanel?.toggle();
  }
  if (e.code === 'KeyM') toggleMap();
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
  setTimeout(() => {
    // 主门（数枢殿）落在门南侧、面朝北进皇城；宫殿门面向宫殿
    const yaw = portal.palace ? faceCenter(portal.x, portal.z) : Math.PI;
    // 落在传送门外侧
    const out = portal.palace
      ? { x: portal.x + Math.sin(portal.palace.yaw) * 6.5, z: portal.z + Math.cos(portal.palace.yaw) * 6.5 }
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
    setTimeout(() => { teleporting = false; }, 420);
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
host.querySelectorAll('[data-action]').forEach((btn) => {
  btn.addEventListener('click', () => {
    const a = btn.dataset.action;
    if (a === 'weather') {
      world.weather = (world.weather + 1) % 4;
      showToast(`天象 · ${WEATHER[world.weather]}`);
      host.querySelectorAll('[data-action="weather"]').forEach((b) => {
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
    if (a === 'char') {
      closePortalPanel();
      charPanel?.toggle();
    }
    if (a === 'map') toggleMap();
    if (a === 'reset') { cam.yaw = 0; cam.pitch = 0.44; cam.dist = 16; }
  });
});

function applySeason() {
  const c = vegetation.colorSeason[world.season];
  const dummy = new THREE.Color();
  for (let i = 0; i < vegetation.crowns.count; i++) {
    dummy.copy(c).offsetHSL(
      (Math.sin(i * 12.9898) * 0.5) * 0.06,
      (Math.sin(i * 7.233) * 0.5) * 0.09,
      (Math.sin(i * 3.117) * 0.5) * 0.1,
    );
    vegetation.crowns.setColorAt(i, dummy);
  }
  if (vegetation.crowns.instanceColor) vegetation.crowns.instanceColor.needsUpdate = true;
  const groundTint = [new THREE.Color(0xc8e8a8), new THREE.Color(0xffffff), new THREE.Color(0xe8c98a), new THREE.Color(0xc8d8e8)];
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
  const sunDir = world.sunDir.set(
    Math.cos(ang) * 0.85,
    Math.sin(ang),
    0.42 * Math.cos(ang * 0.5),
  ).normalize();

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
  scene.fog.density = lerp(0.0016, 0.0052, haze) + world.weatherMix[1] * 0.0016 + world.weatherMix[2] * 0.0012;

  // 灯笼夜间点亮
  const lanternOn = 1 - dayF;
  matLantern.color.copy(new THREE.Color(0x4a3226).lerp(new THREE.Color(0xff5a3c), lanternOn));
  matLanternGlow.opacity = 0.05 + lanternOn * 0.2;

  waterUniforms.uSky.value.copy(bot).multiplyScalar(1.0);
  sky.position.copy(camera.position);
  stars.position.copy(camera.position);
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
  if (keys.has('KeyW') || keys.has('ArrowUp')) moveDir.add(forward);
  if (keys.has('KeyS') || keys.has('ArrowDown')) moveDir.sub(forward);
  if (keys.has('KeyA') || keys.has('ArrowLeft')) moveDir.sub(right);
  if (keys.has('KeyD') || keys.has('ArrowRight')) moveDir.add(right);

  const running = keys.has('ShiftLeft') || keys.has('ShiftRight');
  const maxSpeed = running ? 16 : 7.5;
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

  // 世界频道上报（内部按 10Hz 节流；单机模式下只更新本地缓存）
  channel.report(player.pos.x, player.pos.z, player.yaw, player.speed);

  avatar.position.copy(player.pos);
  avatar.rotation.y = player.yaw;

  // 行走动画（姿态由 avatar 模块托管，含呼吸与静止张望）
  playerRig.update(dt, player.speed);
}

/** 建筑碰撞：几何全部由 collision 模块按布局生成，这里只保留调用点 */
const BLOCKERS = createBlockers();

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
  const off = new THREE.Vector3(3.0, 3.1, 2.6).applyAxisAngle(Y_AXIS, player.yaw);
  const t = clock.t;
  const target = player.pos.clone().add(off);
  target.y += Math.sin(t * 1.4) * 0.42;

  whale.position.lerp(target, 1 - Math.exp(-dt * 2.6));

  // 朝向移动方向
  const desired = Math.atan2(player.pos.x - whale.position.x, player.pos.z - whale.position.z);
  let diff = desired - whale.rotation.y;
  while (diff > Math.PI) diff -= Math.PI * 2;
  while (diff < -Math.PI) diff += Math.PI * 2;
  whale.rotation.y += diff * Math.min(1, dt * 2.4);

  whale.rotation.z = Math.sin(t * 1.6) * 0.09;
  whale.rotation.x = Math.sin(t * 1.1) * 0.05;
  if (whaleTail) whaleTail.rotation.x = Math.sin(t * 2.2) * 0.42;

  // 定时说话
  whaleTimer -= dt;
  if (whaleTimer <= 0) {
    whaleTimer = 16 + Math.random() * 12;
    if (Math.random() < 0.7) whaleSay(WHALE_LINES[Math.floor(Math.random() * WHALE_LINES.length)]);
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
    if (d < nearestDist) { nearestDist = d; nearest = p; }
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
  updateParticles(rain, dt, camera, world.weatherMix[1] * 0.9);
  updateParticles(snow, dt, camera, world.weatherMix[2] * 0.95);
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
  updateActors(dt);
  updateCamera(dt);
  updateWhale(dt);
  updatePortals(dt);
  updateHUD();

  renderer.render(scene, camera);
  updateLabels();
  updateNameLabels();
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
    window.__bwwWarp = (x, z) => {
      player.pos.set(x, 0, z);
    };
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
      return { id: npc.def.id, name: npc.def.name, x: Number(npc.pos.x.toFixed(2)), z: Number(npc.pos.z.toFixed(2)) };
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
