/**
 * T0.1 · CSS3D + WebGL 混合渲染最小验证
 *
 * <p>目的不是"好看"，是回答一个问题：两层能不能用同一个相机、同一帧、零漂移地对齐。</p>
 *
 * <p>验证手法有三条：
 * ① 在面板所在的世界位置画一个同尺寸的 WebGL 线框——两层若同步，线框会被面板精确盖住，
 *    一旦露出边就说明错位；
 * ② 每帧把面板中心的世界坐标投影到屏幕，与 DOM 的 getBoundingClientRect 中心做像素差，
 *    读数应 < 2px；
 * ③ 面板里有真实 input，为 T0.2 的输入法验证预埋。</p>
 */

import * as THREE from 'three';
import { CSS3DRenderer, CSS3DObject } from 'three/addons/renderers/CSS3DRenderer.js';

const stage = document.getElementById('stage') as HTMLElement;
const css3dRoot = document.getElementById('css3d-root') as HTMLElement;
const occluderRoot = document.getElementById('occluder-root') as HTMLElement;
const hudErr = document.getElementById('hud-err') as HTMLElement;
const hudSize = document.getElementById('hud-size') as HTMLElement;
const hudFps = document.getElementById('hud-fps') as HTMLElement;
const hudDist = document.getElementById('hud-dist') as HTMLElement;

let width = window.innerWidth;
let height = window.innerHeight;

/* ------------------------------------------------------------------ 相机 */

const camera = new THREE.PerspectiveCamera(55, width / height, 0.1, 500);

/** 球面轨道参数：拖拽改 theta/phi，滚轮改 radius */
let theta = 0.18;
let phi = 1.12;
let radius = 14;
const target = new THREE.Vector3(0, 1.7, 0);

// 支持 ?theta=&phi=&radius= 直接指定机位，便于自动化截图比对不同角度下的遮挡关系
{
  const q = new URLSearchParams(window.location.search);
  const num = (k: string, fallback: number): number => {
    const v = q.get(k);
    const n = v === null ? NaN : Number(v);
    return Number.isFinite(n) ? n : fallback;
  };
  theta = num('theta', theta);
  phi = num('phi', phi);
  radius = num('radius', radius);
}

function applyCamera(): void {
  camera.position.set(
    radius * Math.sin(phi) * Math.sin(theta),
    radius * Math.cos(phi),
    radius * Math.sin(phi) * Math.cos(theta),
  );
  camera.lookAt(target);
}
applyCamera();

/* ------------------------------------------------------- WebGL 渲染器 */

const renderer = new THREE.WebGLRenderer({ antialias: true });
renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
renderer.setSize(width, height);
renderer.shadowMap.enabled = true;
renderer.shadowMap.type = THREE.PCFSoftShadowMap;
stage.appendChild(renderer.domElement);

const scene = new THREE.Scene();
scene.background = new THREE.Color(0x0b1016);
scene.fog = new THREE.Fog(0x0b1016, 20, 46);

/* ------------------------------------------------------- CSS3D 渲染器 */

const css3d = new CSS3DRenderer();
css3d.setSize(width, height);
css3dRoot.appendChild(css3d.domElement);

/**
 * CSS3D 的"场景"与 WebGL 场景是两个容器，但共用同一个 camera。
 * 这是 three 官方 mixed 用法：两层各自 render，矩阵来自同一台相机，天然同步。
 */
const css3dScene = new THREE.Scene();

/* ------------------------------------------------------- 遮挡层（T0.3） */

/**
 * CSS3D 是 DOM 合成层，浏览器保证它永远盖在 canvas 之上，深度缓冲不共享。
 * 所以要反过来：再开一块透明 canvas 压在 CSS3D 上面，只渲染"应该挡住界面的物体"。
 *
 * <p>代价是一套额外的 GPU 资源（几何/着色器各自上传），所以这层只放真正会挡视线的
 * 物体（柱子、墙、屋檐），绝不把整座城塞进来。</p>
 */
const OCCLUDER_LAYER = 1;

const occluder = new THREE.WebGLRenderer({ alpha: true, antialias: true });
occluder.setPixelRatio(Math.min(window.devicePixelRatio, 2));
occluder.setSize(width, height);
occluder.setClearColor(0x000000, 0);
// 遮挡层不需要阴影：阴影已在主 pass 算过，这里再算一遍纯属浪费
occluder.shadowMap.enabled = false;
occluderRoot.appendChild(occluder.domElement);

/* ------------------------------------------------------------- 场景内容 */

const grid = new THREE.GridHelper(44, 44, 0x24485c, 0x132430);
css3dScene.add(grid);
scene.add(grid);

const ambient = new THREE.AmbientLight(0x9fc4dd, 0.55);
scene.add(ambient);

const key = new THREE.DirectionalLight(0xffe0b0, 2.1);
key.position.set(6, 12, 8);
key.castShadow = true;
key.shadow.mapSize.set(1024, 1024);
key.shadow.camera.left = -14;
key.shadow.camera.right = 14;
key.shadow.camera.top = 14;
key.shadow.camera.bottom = -14;
scene.add(key);

const rim = new THREE.PointLight(0x5fb6e8, 18, 30);
rim.position.set(-6, 4, -4);
scene.add(rim);

/** 三个立方体代表"建筑"，用来观察 3D 一致性 */
const blocks: Array<[number, number, number, number, number]> = [
  [-3.4, 0.9, 0.8, 1.1, 0x8f3a2c],
  [0, 0.55, 1.6, 0.7, 0x2f6b5f],
  [3.4, 1.15, -0.4, 1.4, 0x3f5f78],
];
for (const [x, y, z, s, color] of blocks) {
  const mesh = new THREE.Mesh(
    new THREE.BoxGeometry(s * 1.6, s * 2, s * 1.6),
    new THREE.MeshStandardMaterial({ color, roughness: 0.72, metalness: 0.06 }),
  );
  mesh.position.set(x, y, z);
  mesh.castShadow = true;
  mesh.receiveShadow = true;
  scene.add(mesh);
}

/**
 * 遮挡柱：立在面板左前方，应该把面板左半边切掉。
 * 同时属于 layer 0（主场景要看见它）和 layer 1（遮挡层也要画它）。
 */
const pillar = new THREE.Mesh(
  new THREE.CylinderGeometry(0.52, 0.6, 3.8, 28),
  new THREE.MeshStandardMaterial({ color: 0x9a8a6a, roughness: 0.82, metalness: 0.04 }),
);
pillar.position.set(-1.85, 1.9, -0.85);
pillar.castShadow = true;
pillar.receiveShadow = true;
pillar.layers.enable(OCCLUDER_LAYER);
scene.add(pillar);

/**
 * 光源必须同时属于遮挡层。
 * three 的 projectObject 会按 camera.layers 过滤光源——只 set(1) 时 layer 0 的灯被跳过，
 * 遮挡 pass 里的柱子就成了纯黑剪影，看起来像贴纸而不是实体。
 */
for (const light of [ambient, key, rim]) {
  light.layers.enable(OCCLUDER_LAYER);
}

/* ------------------------------------------------- CSS3D 面板与对齐线框 */

/** 面板像素尺寸 */
const PANEL_W = 640;
const PANEL_H = 400;
/** 像素 → 世界单位 */
const PANEL_SCALE = 0.008;
const PANEL_WORLD_W = PANEL_W * PANEL_SCALE; // 5.12
const PANEL_WORLD_H = PANEL_H * PANEL_SCALE; // 3.2
const PANEL_POS = new THREE.Vector3(0, 2.15, -2.2);

const paper = document.createElement('div');
paper.className = 'paper';
paper.innerHTML = `
  <h2>殿前告示 · CSS3D 面板</h2>
  <div class="hint">这块面板是真实 DOM，贴在三维空间里。可以选中文字、可以输入。</div>
  <div class="row">
    <div class="stat"><b>1284</b><span>本月订单</span></div>
    <div class="stat"><b>4.6</b><span>库存周转</span></div>
    <div class="stat"><b>3</b><span>待办告警</span></div>
  </div>
  <input type="text" placeholder="在这里输入中文，验证输入法候选框位置" />
  <button type="button">示例按钮</button>
  <span class="probe" aria-hidden="true"></span>
`;

const panel = new CSS3DObject(paper);
panel.position.copy(PANEL_POS);
panel.scale.setScalar(PANEL_SCALE);
css3dScene.add(panel);

/**
 * 对齐线框：与面板同尺寸、同位置。
 * 正常情况下它会完全被面板盖住（CSS3D 是合成层，永远在 canvas 之上）。
 * 只要看到边框露出来，就说明某一层的矩阵对不上。
 */
const frameGeo = new THREE.BufferGeometry().setFromPoints([
  new THREE.Vector3(-PANEL_WORLD_W / 2, -PANEL_WORLD_H / 2, 0),
  new THREE.Vector3(PANEL_WORLD_W / 2, -PANEL_WORLD_H / 2, 0),
  new THREE.Vector3(PANEL_WORLD_W / 2, PANEL_WORLD_H / 2, 0),
  new THREE.Vector3(-PANEL_WORLD_W / 2, PANEL_WORLD_H / 2, 0),
  new THREE.Vector3(-PANEL_WORLD_W / 2, -PANEL_WORLD_H / 2, 0),
]);
const frame = new THREE.Line(
  frameGeo,
  new THREE.LineBasicMaterial({ color: 0x00ff88 }),
);
frame.position.copy(PANEL_POS);
scene.add(frame);

/* --------------------------------------------------------- 相机交互 */

let dragging = false;
let lastX = 0;
let lastY = 0;

renderer.domElement.addEventListener('pointerdown', (ev) => {
  dragging = true;
  lastX = ev.clientX;
  lastY = ev.clientY;
  renderer.domElement.setPointerCapture(ev.pointerId);
});

renderer.domElement.addEventListener('pointermove', (ev) => {
  if (!dragging) return;
  theta -= (ev.clientX - lastX) * 0.005;
  phi = Math.min(Math.PI - 0.25, Math.max(0.28, phi - (ev.clientY - lastY) * 0.004));
  lastX = ev.clientX;
  lastY = ev.clientY;
  applyCamera();
});

const endDrag = (): void => {
  dragging = false;
};
renderer.domElement.addEventListener('pointerup', endDrag);
renderer.domElement.addEventListener('pointercancel', endDrag);

renderer.domElement.addEventListener(
  'wheel',
  (ev) => {
    ev.preventDefault();
    radius = Math.min(34, Math.max(4.5, radius + ev.deltaY * 0.012));
    applyCamera();
  },
  { passive: false },
);

/* ----------------------------------------------------------- 误差测量 */

const projected = new THREE.Vector3();
let lastMeasure = 0;
let smoothErr = 0;

/** 无面积探针：它的 rect 中心就是面板中心的真实投影位置 */
const probeEl = paper.querySelector('.probe') as HTMLElement;

/**
 * 面板中心世界坐标 → 屏幕像素，与探针实际位置比对。
 *
 * <p>不能用 paper.getBoundingClientRect() 的中心：透视变换后四边形变成梯形，
 * 轴对齐包围盒的中心会偏移数像素，那是测量方法的假误差，不是渲染误差。</p>
 */
function measurePanelError(): number {
  projected.copy(PANEL_POS).project(camera);
  const expectX = (projected.x * 0.5 + 0.5) * width;
  const expectY = (-projected.y * 0.5 + 0.5) * height;

  const rect = probeEl.getBoundingClientRect();
  const actualX = rect.left + rect.width / 2;
  const actualY = rect.top + rect.height / 2;

  return Math.hypot(expectX - actualX, expectY - actualY);
}

/* ------------------------------------------------------------ 主循环 */

let frames = 0;
let fpsMark = performance.now();

const clock = new THREE.Clock();

function animate(): void {
  requestAnimationFrame(animate);

  const t = clock.getElapsedTime();
  // 灯轻微呼吸，证明两层都在动、且是同一台相机在驱动
  rim.intensity = 14 + Math.sin(t * 1.6) * 5;

  applyCamera();
  renderer.render(scene, camera);
  css3d.render(css3dScene, camera);

  // 遮挡层：只画 OCCLUDER_LAYER，压在 CSS3D 之上。
  // 关键：必须临时摘掉 scene.background —— 否则它会覆盖 clearColor，
  // 把整块遮挡画布涂成不透明的深色，下面的主场景和面板全被盖死。
  const mainMask = camera.layers.mask;
  const bg = scene.background;
  scene.background = null;
  camera.layers.set(OCCLUDER_LAYER);
  occluder.render(scene, camera);
  camera.layers.mask = mainMask;
  scene.background = bg;

  const now = performance.now();
  if (now - lastMeasure > 120) {
    lastMeasure = now;
    const err = measurePanelError();
    smoothErr = smoothErr === 0 ? err : smoothErr * 0.7 + err * 0.3;

    hudErr.textContent = `${smoothErr.toFixed(2)} px`;
    hudErr.className = smoothErr < 2 ? 'v ok' : 'v bad';

    const rect = paper.getBoundingClientRect();
    hudSize.textContent = `${Math.round(rect.width)} × ${Math.round(rect.height)}`;
    hudDist.textContent = radius.toFixed(1);
  }

  frames += 1;
  if (now - fpsMark > 500) {
    hudFps.textContent = String(Math.round((frames * 1000) / (now - fpsMark)));
    frames = 0;
    fpsMark = now;
  }
}
animate();

/* ------------------------------------------------------------- resize */

window.addEventListener('resize', () => {
  width = window.innerWidth;
  height = window.innerHeight;
  camera.aspect = width / height;
  camera.updateProjectionMatrix();
  renderer.setSize(width, height);
  css3d.setSize(width, height);
  occluder.setSize(width, height);
  applyCamera();
});

// 便利：控制台可读，便于 T0.2 进一步量测
(window as unknown as { __spike?: unknown }).__spike = {
  camera,
  panel,
  paper,
  measure: measurePanelError,
};
