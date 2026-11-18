import * as THREE from 'three';
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js';
import { createSceneEnv } from './env';
import { createPostFx } from './postfx';
import { buildSettlement } from './props';
import { createSky, type SkyState } from './sky';
import { createTerrain } from './terrain';
import { createWater } from './water';

/**
 * 场景装配。
 *
 * <p>S0 只做"能看"：地形、河、天空、聚落占位、一套黄昏光、一台可自由摆的相机。
 * S1 加上 IBL 与后期处理（Bloom / 调色 / 暗角 / SMAA）。所有可调项都收进
 * {@link TimePreset} 与 {@link CameraPreset}，后面加效果不改装配逻辑。</p>
 */

export interface SceneStats {
  fps: number;
  triangles: number;
  calls: number;
}

export type TimeName = 'dusk' | 'day' | 'blue' | 'night';
export type PresetName = 'overview' | 'bridge' | 'street' | 'dock';

interface TimePreset {
  /** 太阳高度角（度） */
  sunElev: number;
  /** 太阳方位角（度，0 为 +X 方向） */
  sunAzim: number;
  sunColor: number;
  sunIntensity: number;
  hemiSky: number;
  hemiGround: number;
  hemiIntensity: number;
  skyTop: number;
  skyHorizon: number;
  skyGround: number;
  fogColor: number;
  fogDensity: number;
  exposure: number;
  haze: number;
  /** 灯笼等自发光体的强度倍率 */
  lamp: number;
  /** IBL 强度：环境贴图整体权重 */
  envIntensity: number;
  /** Bloom 强度：夜里灯火需要更明显的溢光 */
  bloom: number;
  /** 暗部染色（后期把阴影往这个色偏）与强度 */
  shadowTint: number;
  shadowAmount: number;
}

const TIMES: Record<TimeName, TimePreset> = {
  // 黄昏：太阳刻意放在**侧后 70°**（azim -25），不是正逆光。
  // 第一版把太阳放正前方（azim -118），结果整城变成纯黑剪影、水面烧成白带。
  dusk: {
    sunElev: 14,
    sunAzim: -25,
    sunColor: 0xffb066,
    sunIntensity: 3.4,
    hemiSky: 0x5a7ba8,
    hemiGround: 0x40342a,
    hemiIntensity: 0.95,
    skyTop: 0x22386a,
    skyHorizon: 0xd98f52,
    skyGround: 0x2e2b26,
    fogColor: 0xa8836a,
    fogDensity: 0.0018,
    exposure: 1.0,
    haze: 0.55,
    lamp: 1.0,
    envIntensity: 1.5,
    bloom: 0.3,
    shadowTint: 0x1b2742,
    shadowAmount: 0.24,
  },
  day: {
    sunElev: 52,
    sunAzim: -95,
    sunColor: 0xfff2d6,
    sunIntensity: 4.2,
    hemiSky: 0x9fc7ff,
    hemiGround: 0x4f4a35,
    hemiIntensity: 0.9,
    skyTop: 0x3d78b8,
    skyHorizon: 0xd2e5f4,
    skyGround: 0x6d7a72,
    fogColor: 0xc3d6e2,
    fogDensity: 0.0012,
    exposure: 1.0,
    haze: 0.35,
    lamp: 0.15,
    envIntensity: 1.8,
    bloom: 0.1,
    shadowTint: 0x223046,
    shadowAmount: 0.14,
  },
  blue: {
    sunElev: 4,
    sunAzim: -30,
    sunColor: 0x93a8dc,
    sunIntensity: 1.1,
    hemiSky: 0x4a6a9a,
    hemiGround: 0x232838,
    hemiIntensity: 0.8,
    skyTop: 0x121d36,
    skyHorizon: 0x5d789f,
    skyGround: 0x1c222c,
    fogColor: 0x44546c,
    fogDensity: 0.0024,
    exposure: 1.16,
    haze: 0.5,
    lamp: 0.9,
    envIntensity: 2.0,
    bloom: 0.5,
    shadowTint: 0x141d38,
    shadowAmount: 0.34,
  },
  night: {
    sunElev: 34,
    sunAzim: 62,
    sunColor: 0x8fa6e0,
    sunIntensity: 0.55,
    hemiSky: 0x1b2740,
    hemiGround: 0x080a0e,
    hemiIntensity: 0.42,
    skyTop: 0x050812,
    skyHorizon: 0x131c30,
    skyGround: 0x07090d,
    fogColor: 0x0f1626,
    fogDensity: 0.0028,
    exposure: 1.42,
    haze: 0.28,
    lamp: 1.7,
    envIntensity: 2.2,
    bloom: 0.45,
    shadowTint: 0x0c1730,
    shadowAmount: 0.4,
  },
};

interface CameraPreset {
  pos: [number, number, number];
  target: [number, number, number];
  fov: number;
}

const CAMERAS: Record<PresetName, CameraPreset> = {
  /** 全景：斜俯视河谷，主体（桥 + 牌坊 + 城墙）占画面下半，留天空给氛围 */
  // 相机往左前挪、抬高一点：第一版 (34,18,46) 把聚落全部推出右边界，
  // 画面 2/3 是空地。现在让牌坊落在右侧三分线、河湾从右下引出
  overview: { pos: [26, 21, 52], target: [10, 4, -6], fov: 40 },
  /** 桥头：从西岸看桥与牌坊，湿石与灯笼是主体 */
  bridge: { pos: [-13, 5.2, 17], target: [9, 4.6, 1], fov: 44 },
  /** 街景：沿街看牌坊与远处城墙，景深最出效果 */
  // 站在街面上往北看牌坊：机位必须严格落在街心（x≈17），偏一点就进墙里
  street: { pos: [17.2, 3.1, 21], target: [17.6, 5.0, -13], fov: 48 },
  /** 码头：贴水面低机位看船与桥影 */
  dock: { pos: [-16, 2.0, -26], target: [2, 5.0, -4], fov: 42 },
};

export function sunDirection(p: TimePreset): THREE.Vector3 {
  const el = (p.sunElev * Math.PI) / 180;
  const az = (p.sunAzim * Math.PI) / 180;
  return new THREE.Vector3(Math.cos(el) * Math.cos(az), Math.sin(el), Math.cos(el) * Math.sin(az)).normalize();
}

export interface ShowcaseOptions {
  time?: TimeName;
  preset?: PresetName;
  onStats?: (s: SceneStats) => void;
}

export interface ShowcaseHandle {
  setTime: (name: TimeName) => void;
  setPreset: (name: PresetName) => void;
  dispose: () => void;
}

export function createShowcase(host: HTMLElement, opts: ShowcaseOptions = {}): ShowcaseHandle {
  const scene = new THREE.Scene();
  const fog = new THREE.FogExp2(0xc79a72, 0.0027);
  scene.fog = fog;

  const camera = new THREE.PerspectiveCamera(38, host.clientWidth / host.clientHeight, 0.4, 3000);

  const renderer = new THREE.WebGLRenderer({ antialias: true, powerPreference: 'high-performance' });
  // postfx 一帧要 render 五六次，自动重置的话 HUD 永远只能读到最后一个全屏 quad（2 个三角面）
  renderer.info.autoReset = false;
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
  renderer.setSize(host.clientWidth, host.clientHeight);
  renderer.shadowMap.enabled = true;
  renderer.shadowMap.type = THREE.PCFSoftShadowMap;
  renderer.toneMapping = THREE.ACESFilmicToneMapping;
  renderer.toneMappingExposure = 1.06;
  renderer.outputColorSpace = THREE.SRGBColorSpace;
  host.appendChild(renderer.domElement);

  /* ------------------------------------------------------------ 后期 */
  const postfx = createPostFx(renderer, scene, camera, host.clientWidth, host.clientHeight);

  /* ---------------------------------------------------------------- 光 */
  // 有了 IBL 之后半球光只做"补死角"，强度必须压下来：两盏漫射一起打会把人造物体的
  // 暗面全部提亮，阴影消失、整幅图发灰（第一版 hemi 0.95 + IBL 就是这个问题）。
  const hemi = new THREE.HemisphereLight(0x6f8cb0, 0x4a3a2a, 0.3);
  scene.add(hemi);

  const sun = new THREE.DirectionalLight(0xffa258, 3.1);
  sun.castShadow = true;
  // 阴影范围只罩聚落核心（±48）：范围铺到 ±72 时 texel 变粗，桥面板上全是自阴影网格
  sun.shadow.mapSize.set(3072, 3072);
  sun.shadow.camera.left = -38;
  sun.shadow.camera.right = 38;
  sun.shadow.camera.top = 38;
  sun.shadow.camera.bottom = -38;
  sun.shadow.camera.near = 20;
  sun.shadow.camera.far = 620;
  sun.shadow.bias = -0.0002;
  sun.shadow.normalBias = 2.2;
  sun.target.position.set(2, 3, 0);
  scene.add(sun);
  scene.add(sun.target);

  /* ---------------------------------------------------------------- 内容 */
  const sky = createSky(900);
  scene.add(sky.mesh);

  // 环境贴图必须在 sky 之后建：它直接用天空 shader 烘出来，保证反射与环境光同源
  const env = createSceneEnv(renderer);
  scene.environment = env.texture;

  const terrain = createTerrain();
  scene.add(terrain.mesh);

  const water = createWater();
  scene.add(water.mesh);

  const settlement = buildSettlement();
  scene.add(settlement);

  /* ---------------------------------------------------------------- 相机 */
  const controls = new OrbitControls(camera, renderer.domElement);
  controls.enableDamping = true;
  controls.dampingFactor = 0.06;
  controls.maxPolarAngle = Math.PI * 0.497; // 不许钻到地下
  controls.minDistance = 6;
  controls.maxDistance = 220;
  controls.target.set(2, 4, 0);
  controls.enablePan = true;

  const lampMaterials: THREE.MeshStandardMaterial[] = [];
  settlement.traverse((o) => {
    const m = o as THREE.Mesh;
    if (m.isMesh && Array.isArray(m.material)) return;
    if (m.isMesh) {
      const mat = m.material as THREE.MeshStandardMaterial;
      if (mat.emissive && mat.emissiveIntensity > 1) lampMaterials.push(mat);
    }
  });

  let currentTime: TimePreset = TIMES[opts.time ?? 'dusk'];
  let sunDir = sunDirection(currentTime);
  let lampScale = currentTime.lamp;

  function applyTime(p: TimePreset): void {
    currentTime = p;
    lampScale = p.lamp;
    sunDir = sunDirection(p);
    sun.color.set(p.sunColor);
    sun.intensity = p.sunIntensity;
    sun.position.copy(sunDir).multiplyScalar(260);
    hemi.color.set(p.hemiSky);
    hemi.groundColor.set(p.hemiGround);
    hemi.intensity = p.hemiIntensity * 0.22;
    fog.color.set(p.fogColor);
    fog.density = p.fogDensity;
    renderer.toneMappingExposure = p.exposure;

    const skyState: SkyState = {
      top: new THREE.Color(p.skyTop),
      horizon: new THREE.Color(p.skyHorizon),
      ground: new THREE.Color(p.skyGround),
      sunDir,
      sunColor: new THREE.Color(p.sunColor),
      sunIntensity: p.sunIntensity > 0.3 ? 1 : 0.15,
      haze: p.haze,
    };
    sky.update(skyState);
    // 重建后贴图是新对象，必须重新挂上（旧的那张在 env 内部已释放）
    scene.environment = env.update(skyState);
    scene.environmentIntensity = p.envIntensity;

    for (const m of lampMaterials) m.emissiveIntensity = 2.2 * lampScale;
    water.setSky(new THREE.Color(p.skyHorizon), new THREE.Color(p.skyTop));

    postfx.setBloom(p.bloom);
    postfx.setTint(new THREE.Color(p.shadowTint), new THREE.Color(0xffd2a0), p.shadowAmount);
    scene.background = null;
  }

  function applyCamera(name: PresetName, immediate = true): void {
    const preset = CAMERAS[name];
    camera.fov = preset.fov;
    camera.updateProjectionMatrix();
    const targetPos = new THREE.Vector3(...preset.pos);
    const targetLook = new THREE.Vector3(...preset.target);
    if (immediate) {
      camera.position.copy(targetPos);
      controls.target.copy(targetLook);
    } else {
      camera.position.lerp(targetPos, 0.12);
      controls.target.lerp(targetLook, 0.12);
    }
    controls.update();
  }

  applyTime(currentTime);
  applyCamera(opts.preset ?? 'overview');

  /* ---------------------------------------------------------------- 循环 */
  const clock = new THREE.Clock();
  let raf = 0;
  let frames = 0;
  let statAcc = 0;
  let disposed = false;

  const tmpFog = new THREE.Color();

  function frame(): void {
    if (disposed) return;
    const dt = clock.getDelta();
    const t = clock.getElapsedTime();

    controls.update();
    sky.mesh.position.copy(camera.position);
    tmpFog.copy(fog.color);
    water.update(t, sunDir, currentTime.sunIntensity, tmpFog, fog.density);

    // RenderPass 内部已经渲染了场景，这里再调 renderer.render 会白白多跑一整帧
    renderer.info.reset();
    postfx.render(dt);

    frames += 1;
    statAcc += dt;
    if (statAcc >= 0.5 && opts.onStats) {
      opts.onStats({
        fps: Math.round(frames / statAcc),
        triangles: renderer.info.render.triangles,
        calls: renderer.info.render.calls,
      });
      frames = 0;
      statAcc = 0;
    }

    raf = requestAnimationFrame(frame);
  }
  raf = requestAnimationFrame(frame);

  /* ---------------------------------------------------------------- 尺寸 */
  function resize(): void {
    const w = host.clientWidth;
    const h = host.clientHeight;
    if (w === 0 || h === 0) return;
    camera.aspect = w / h;
    camera.updateProjectionMatrix();
    renderer.setSize(w, h);
    // composer 有自己的 render target，不跟着改尺寸的话画面会被拉伸
    postfx.setSize(w, h);
  }
  const ro = new ResizeObserver(resize);
  ro.observe(host);

  return {
    setTime(name) {
      applyTime(TIMES[name]);
    },
    setPreset(name) {
      applyCamera(name, true);
    },
    dispose() {
      disposed = true;
      cancelAnimationFrame(raf);
      ro.disconnect();
      controls.dispose();
      postfx.dispose();
      env.dispose();
      scene.traverse((o) => {
        const m = o as THREE.Mesh;
        if (m.isMesh) {
          m.geometry.dispose();
          const mat = m.material;
          if (Array.isArray(mat)) mat.forEach((x) => x.dispose());
          else mat.dispose();
        }
      });
      renderer.dispose();
      if (renderer.domElement.parentElement === host) host.removeChild(renderer.domElement);
    },
  };
}
