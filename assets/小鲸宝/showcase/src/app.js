/**
 * 小鲸宝 · 交互展示页
 * 单文件构建：GLB 以 base64 内嵌，双击 html 即可打开（file:// 可用，无需起服务）
 */
import * as THREE from 'three';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';
import { RoomEnvironment } from 'three/addons/environments/RoomEnvironment.js';

const CLIPS = [
  { name: 'Idle_Float',       label: '漂浮待机', group: 'idle',  loop: true  },
  { name: 'Idle_Sleep',       label: '睡着',     group: 'idle',  loop: true  },
  { name: 'Swim_Slow',        label: '慢游',     group: 'swim',  loop: true  },
  { name: 'Swim_Fast',        label: '快游',     group: 'swim',  loop: true  },
  { name: 'Action_Snuggle',   label: '贴贴',     group: 'act',   loop: false },
  { name: 'Action_Happy',     label: '开心跳',   group: 'act',   loop: false },
  { name: 'Action_Startle',   label: '受惊',     group: 'act',   loop: false },
  { name: 'Action_Follow',    label: '跟随', group: 'act',   loop: true  },
  { name: 'Action_Heal',      label: '治愈',     group: 'state', loop: false },
  { name: 'Action_Sad',       label: '委屈',     group: 'state', loop: false },
  { name: 'Action_Appear',    label: '出场',     group: 'state', loop: false },
  { name: 'Action_Disappear', label: '收回',     group: 'state', loop: false },
];

const PRESETS = [
  { label: '3/4',  az: 38,  el: 16 },
  { label: '正面', az: 0,   el: 8  },
  { label: '侧面', az: 90,  el: 6  },
  { label: '背面', az: 180, el: 8  },
  { label: '俯视', az: 20,  el: 66 },
];

const $ = (id) => document.getElementById(id);

/* ---------------------------------------------------------------- 场景 */
const canvas = $('stage');
const renderer = new THREE.WebGLRenderer({
  canvas, antialias: true, alpha: true, powerPreference: 'high-performance',
});
renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
renderer.setSize(window.innerWidth, window.innerHeight, false);
renderer.toneMapping = THREE.ACESFilmicToneMapping;
renderer.toneMappingExposure = 0.94;
renderer.shadowMap.enabled = false;

const scene = new THREE.Scene();
scene.fog = null;

const pmrem = new THREE.PMREMGenerator(renderer);
scene.environment = pmrem.fromScene(new RoomEnvironment(), 0.05).texture;

const hemi = new THREE.HemisphereLight(0xdcefff, 0x0a2338, 0.62);
scene.add(hemi);

const key = new THREE.DirectionalLight(0xffffff, 2.05);
key.position.set(2.4, 3.4, 3.2);
scene.add(key);

const fill = new THREE.DirectionalLight(0xbfe4ff, 0.7);
fill.position.set(-3.0, 0.8, 2.2);
scene.add(fill);

const rim = new THREE.DirectionalLight(0x9fd8ff, 1.35);
rim.position.set(-1.8, 2.0, -3.4);
scene.add(rim);

const camera = new THREE.PerspectiveCamera(34, window.innerWidth / window.innerHeight, 0.05, 100);
camera.position.set(1.55, 0.95, 2.4);

const controls = new OrbitControls(camera, canvas);
controls.target.set(0, 0.56, 0);
controls.enableDamping = true;
controls.dampingFactor = 0.075;
controls.rotateSpeed = 0.9;
controls.zoomSpeed = 0.8;
controls.enablePan = false;
controls.minDistance = 1.2;
controls.maxDistance = 7;
controls.minPolarAngle = 0.16;
controls.maxPolarAngle = Math.PI * 0.86;
controls.autoRotate = true;
controls.autoRotateSpeed = 0.95;
controls.update();

/* 取景：按模型包围盒自动算距离，留出底部控制台的空间 */
const FIT = { center: new THREE.Vector3(0, 0.5, 0), dist: 3.0, radius: 0.6 };

/* ---------------------------------------------------------------- 状态 */
let model = null;
let mixer = null;
let actions = {};
let currentAction = null;
let currentName = '';
let camTween = null;
let ready = false;

const state = { spin: true, autoplay: false, repeat: false, speed: 1 };

/* ---------------------------------------------------------------- 取景 */
function presetPos(az, el) {
  const a = (az * Math.PI) / 180;
  const e = (el * Math.PI) / 180;
  const d = FIT.dist;
  return new THREE.Vector3(
    FIT.center.x + Math.sin(a) * Math.cos(e) * d,
    FIT.center.y + Math.sin(e) * d,
    FIT.center.z + Math.cos(a) * Math.cos(e) * d,
  );
}

function placeAt(az, el, dur = 0.85) {
  const to = presetPos(az, el);
  if (!dur) {
    camera.position.copy(to);
    controls.target.copy(FIT.center);
    controls.update();
    return;
  }
  camTween = {
    t: 0, dur,
    from: camera.position.clone(), to,
    fromT: controls.target.clone(), toT: FIT.center.clone(),
  };
}

function fitModel() {
  model.updateMatrixWorld(true);
  const box = new THREE.Box3().setFromObject(model);
  const size = box.getSize(new THREE.Vector3());
  const h = Math.max(size.y, 0.001);
  const half = Math.tan((camera.fov * Math.PI) / 360);
  const aspect = Math.max(camera.aspect, 0.6);
  // 竖直方向留 1.9 倍身高的画幅（上方给标题、下方给控制台），横向再兜一次
  const byHeight = (h * 2.05) / (2 * half);
  const byWidth = (size.x * 1.28) / (2 * half * aspect);
  FIT.dist = Math.max(byHeight, byWidth);
  FIT.radius = Math.max(size.x, size.y, size.z) * 0.5;
  const mid = box.getCenter(new THREE.Vector3());
  FIT.center.set(mid.x * 0.62, box.min.y + h * 0.53, mid.z * 0.4);
  controls.minDistance = FIT.dist * 0.42;
  controls.maxDistance = FIT.dist * 2.4;
  placeAt(38, 16, 0);
  console.log('[xiaojingbao] size=%o dist=%s', size, FIT.dist.toFixed(2));
}

/* ---------------------------------------------------------------- 载入 */
function b64ToArrayBuffer(b64) {
  const bin = atob(b64);
  const len = bin.length;
  const bytes = new Uint8Array(len);
  for (let i = 0; i < len; i += 1) bytes[i] = bin.charCodeAt(i);
  return bytes.buffer;
}

function onLoaded(gltf) {
  model = gltf.scene;
  model.traverse((o) => {
    if (!o.isMesh) return;
    o.frustumCulled = false;
    const mats = Array.isArray(o.material) ? o.material : [o.material];
    mats.forEach((m) => {
      if (!m) return;
      m.envMapIntensity = 0.5;
      if (m.map) m.map.anisotropy = Math.min(8, renderer.capabilities.getMaxAnisotropy());
    });
  });
  scene.add(model);

  mixer = new THREE.AnimationMixer(model);
  gltf.animations.forEach((clip) => {
    const a = mixer.clipAction(clip);
    a.setEffectiveWeight(1);
    actions[clip.name] = a;
  });

  mixer.addEventListener('finished', (e) => {
    const item = CLIPS.find((c) => actions[c.name] === e.action);
    if (!item) return;
    if (state.autoplay) {
      const i = CLIPS.findIndex((c) => c.name === item.name);
      play(CLIPS[(i + 1) % CLIPS.length].name, 0.5);
    } else {
      play('Idle_Float', 0.55);
    }
  });

  ready = true;
  fitModel();
  $('loader').classList.add('gone');

  const hash = decodeURIComponent(location.hash.replace(/^#/, ''));
  const first = CLIPS.some((c) => c.name === hash) ? hash : 'Idle_Float';
  play(first, 0);
  revealDock();
}

function onError(err) {
  console.error(err);
  const el = $('loader');
  el.classList.add('failed');
  el.querySelector('p').textContent = '模型载入失败，请查看控制台';
}

function boot() {
  const node = $('glb');
  const b64 = node ? node.textContent.trim() : '';
  const loader = new GLTFLoader();
  if (b64) {
    // 单文件模式：GLB 以 base64 内嵌，file:// 也能直接打开
    try {
      loader.parse(b64ToArrayBuffer(b64), '', onLoaded, undefined, onError);
    } catch (e) {
      onError(e);
    }
  } else {
    // 目录模式：从构建时注入的相对路径取模型，需要经 http 提供（vite dev / 静态服务器）
    const url = window.__GLB_URL__ || '../model/xiaojingbao.glb';
    loader.load(url, onLoaded, undefined, onError);
  }
}

/* ---------------------------------------------------------------- 动画 */
function play(name, fade = 0.35) {
  const item = CLIPS.find((c) => c.name === name);
  const next = actions[name];
  if (!item || !next) return;
  if (next === currentAction && !next.paused) return;

  const loop = item.loop || state.repeat;
  next.reset();
  next.enabled = true;
  next.setEffectiveWeight(1);
  next.setEffectiveTimeScale(1);
  next.clampWhenFinished = !loop;
  next.setLoop(loop ? THREE.LoopRepeat : THREE.LoopOnce, loop ? Infinity : 1);

  if (currentAction && currentAction !== next) {
    next.crossFadeFrom(currentAction, fade, false);
  } else {
    next.fadeIn(fade);
  }
  next.play();

  currentAction = next;
  currentName = name;
  syncUI();
}

/* ---------------------------------------------------------------- UI */
function buildDock() {
  const box = $('clips');
  let lastGroup = null;
  CLIPS.forEach((c) => {
    if (lastGroup && c.group !== lastGroup) {
      const sep = document.createElement('i');
      sep.className = 'sep';
      box.appendChild(sep);
    }
    lastGroup = c.group;
    const b = document.createElement('button');
    b.type = 'button';
    b.className = 'chip';
    b.dataset.clip = c.name;
    b.innerHTML = `<span>${c.label}</span>`;
    b.title = `${c.name} · ${c.loop ? '循环' : '单次'}`;
    b.addEventListener('click', () => play(c.name));
    box.appendChild(b);
  });
}

function buildPresets() {
  const box = $('presets');
  PRESETS.forEach((p) => {
    const b = document.createElement('button');
    b.type = 'button';
    b.className = 'view';
    b.textContent = p.label;
    b.addEventListener('click', () => placeAt(p.az, p.el));
    box.appendChild(b);
  });
}

function revealDock() {
  document.querySelectorAll('.chip').forEach((el, i) => {
    el.style.transitionDelay = `${i * 22}ms`;
  });
  document.body.classList.add('ready');
}

function syncUI() {
  document.querySelectorAll('.chip').forEach((el) => {
    el.classList.toggle('on', el.dataset.clip === currentName);
  });
  const item = CLIPS.find((c) => c.name === currentName);
  $('nowLabel').textContent = item ? item.label : '—';
  $('nowClip').textContent = currentName || '—';
}

function bubbles() {
  const wrap = $('bubbles');
  for (let i = 0; i < 18; i += 1) {
    const b = document.createElement('i');
    const s = 3 + Math.random() * 14;
    b.style.left = `${Math.random() * 100}%`;
    b.style.width = `${s}px`;
    b.style.height = `${s}px`;
    b.style.animationDuration = `${9 + Math.random() * 14}s`;
    b.style.animationDelay = `${-Math.random() * 18}s`;
    b.style.opacity = `${0.08 + Math.random() * 0.22}`;
    wrap.appendChild(b);
  }
}

function bindUI() {
  $('spin').addEventListener('change', (e) => {
    state.spin = e.target.checked;
    controls.autoRotate = state.spin;
  });
  $('auto').addEventListener('change', (e) => { state.autoplay = e.target.checked; });
  $('repeat').addEventListener('change', (e) => {
    state.repeat = e.target.checked;
    if (currentName) play(currentName, 0.2);
  });
  $('speed').addEventListener('input', (e) => {
    state.speed = parseFloat(e.target.value);
    $('speedv').textContent = `${state.speed.toFixed(1)}×`;
    if (mixer) mixer.timeScale = state.speed;
  });

  window.addEventListener('keydown', (e) => {
    if (e.code === 'Space') {
      e.preventDefault();
      const cb = $('spin');
      cb.checked = !cb.checked;
      cb.dispatchEvent(new Event('change'));
    } else if (e.code === 'ArrowRight' || e.code === 'ArrowLeft') {
      const i = CLIPS.findIndex((c) => c.name === currentName);
      const step = e.code === 'ArrowRight' ? 1 : -1;
      play(CLIPS[(i + step + CLIPS.length) % CLIPS.length].name, 0.3);
    } else if (/^Digit[1-9]$/.test(e.code)) {
      const i = parseInt(e.code.slice(5), 10) - 1;
      if (CLIPS[i]) play(CLIPS[i].name, 0.3);
    }
  });

  window.addEventListener('resize', () => {
    const w = window.innerWidth;
    const h = window.innerHeight;
    camera.aspect = w / h;
    camera.updateProjectionMatrix();
    renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
    renderer.setSize(w, h, false);
  });

  controls.addEventListener('start', () => { camTween = null; });
}

/* ---------------------------------------------------------------- 循环 */
const clock = new THREE.Clock();

function tick() {
  const dt = Math.min(clock.getDelta(), 0.05);

  if (camTween) {
    camTween.t = Math.min(1, camTween.t + dt / camTween.dur);
    const e = 1 - Math.pow(1 - camTween.t, 3);
    camera.position.lerpVectors(camTween.from, camTween.to, e);
    controls.target.lerpVectors(camTween.fromT, camTween.toT, e);
    if (camTween.t >= 1) camTween = null;
  }

  if (mixer) mixer.update(dt);
  controls.update();
  renderer.render(scene, camera);
}

/* ---------------------------------------------------------------- 起 */
buildDock();
buildPresets();
bindUI();
bubbles();
syncUI();
renderer.setAnimationLoop(tick);
boot();
