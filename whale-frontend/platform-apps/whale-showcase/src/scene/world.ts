import * as THREE from 'three';
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js';
import { createSceneEnv } from './env';
import type { SceneAssets } from './loader';
import { createPostFx } from './postfx';
import { buildSettlement, lanternSpots } from './props';
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
export type Quality = 'high' | 'mid' | 'low';
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
  /**
   * 码头：贴水面低机位看船与桥影。
   *
   * <p>机位不能太近：原 (-16,2,-26) 距码头只有 15m，8.6m 的平台占满下半屏，
   * 两艘船全被裁在画面下沿外——"换了码头和船"在图上一点都看不出来。</p>
   */
  dock: { pos: [5.5, 3.6, -25.5], target: [-4.2, 1.3, -14.6], fov: 44 },
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
  /** 外部加载好的 GLB 资产；缺省则全部走程序化占位件 */
  assets?: SceneAssets;
  quality?: Quality;
}

export interface ShowcaseHandle {
  setTime: (name: TimeName) => void;
  setPreset: (name: PresetName) => void;
  setQuality: (q: Quality) => void;
  dispose: () => void;
}

/**
 * 画质档。
 *
 * <p>这台机器在 1080p、55 万三角面、Bloom + SMAA 全开时只有 25~35 fps，
 * 必须给一个能立刻见效的旋钮。三档只动三处——像素比、抗锯齿、阴影分辨率，
 * 因为这三项是平方级成本；动三角面要重建场景，切换体验太差。</p>
 */
const QUALITY: Record<Quality, { dpr: number; aa: boolean; shadow: number }> = {
  high: { dpr: 2, aa: true, shadow: 2048 },
  mid: { dpr: 1, aa: true, shadow: 2048 },
  low: { dpr: 0.8, aa: false, shadow: 1024 },
};

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
  sun.shadow.mapSize.set(2048, 2048);
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

  /*
   * 水面反射探针。
   *
   * <p>用 CubeCamera 在河心烘一次全景（HDR、512），水 shader 按反射向量采样它。
   * 这一步是夜景的关键：只有解析天空时，夜里天空近黑，整条河就是一块黑布，
   * 岸上二十盏灯笼在水面上一丝影子都没有。</p>
   *
   * <p>三个必须处理的细节：</p>
   * <ul>
   *   <li><b>水面自身要隐藏</b>，否则反射里会看到水在反射自己（彩虹状反馈）；</li>
   *   <li><b>烘的时候关掉 toneMapping</b>，RT 里保持线性 HDR——水 shader 末尾自己会做
   *       tonemap，不关就是两次，水面会比岸上亮一档；</li>
   *   <li><b>只在切时段时重烘</b>，一次 6 面 × 58 万面，每帧跑是不可能的。</li>
   * </ul>
   */
  const probeRT = new THREE.WebGLCubeRenderTarget(512, { type: THREE.HalfFloatType });
  const probe = new THREE.CubeCamera(0.6, 1400, probeRT);
  probe.position.set(2, 5, -8); // 河心、贴着聚落：灯笼和牌坊都在这个点的视野里
  scene.add(probe);
  water.setEnv(probeRT.texture);

  function refreshProbe(): void {
    const prevTone = renderer.toneMapping;
    water.mesh.visible = false;
    renderer.toneMapping = THREE.NoToneMapping;
    probe.update(renderer, scene);
    renderer.toneMapping = prevTone;
    water.mesh.visible = true;
  }

  const settlement = buildSettlement(opts.assets);
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

  // 去重：GLB 里九栋房共享同一批材质实例，不去重会被 push 几十次（改一次改几十次）
  const lampSet = new Set<THREE.MeshStandardMaterial>();
  settlement.traverse((o) => {
    const m = o as THREE.Mesh;
    if (!m.isMesh) return;
    const mats = Array.isArray(m.material) ? m.material : [m.material];
    for (const raw of mats) {
      const mat = raw as THREE.MeshStandardMaterial;
      if (mat.emissive && mat.emissiveIntensity > 1) lampSet.add(mat);
    }
  });
  const lampMaterials = [...lampSet];

  /*
   * 灯笼点光源。
   *
   * <p>只靠自发光材质，灯笼自己亮但不照地——夜景里脚下是黑的，灯像是贴上去的。
   * 全景和街景都能看到灯下的地面，这几盏灯是把"夜里有人住"这个信息立住的关键。</p>
   *
   * <p>数量必须克制：每盏灯都要参与所有受光物体的着色。22 个灯位只取 7 盏
   * （隔 3 个取 1），强度按 candela 给，衰减距离 11m 刚好铺满街面。</p>
   */
  /*
   * 灯笼照明分两半，因为两者的性能曲线完全不同：
   *
   * <ul>
   *   <li><b>点光源只留 3 盏</b>：每盏都要参与所有受光物体的逐片元循环，22 个灯位
   *       全上实测掉到 25fps。3 盏放在桥头核心段，负责把墙面和牌坊打出暖边。</li>
   *   <li><b>其余靠地面光斑贴片</b>：一张半径渐变的加法混合平面，几乎零成本，
   *       但"灯下有光"这个信息就成立了——观众不会去数哪盏灯真的照亮了哪块石头。</li>
   * </ul>
   */
  const lanternLights: THREE.PointLight[] = [];
  const spots = lanternSpots();
  for (let i = 0; i < spots.length; i += Math.max(1, Math.floor(spots.length / 3))) {
    const sp = spots[i];
    const light = new THREE.PointLight(0xffa457, 0, 14, 2);
    light.position.set(sp.x + (sp.rotY > 0 ? 0.45 : -0.45), sp.y + 2.2, sp.z);
    light.castShadow = false; // 点光阴影很贵，灯笼不需要
    scene.add(light);
    lanternLights.push(light);
  }
  // 渡口自己的主光：上面按"每三个灯位取一个"挑出来的三盏都在街上，
  // 码头离最近的街灯 20m、超出 14m 衰减距离，夜里整片码头是黑的。
  const dockLight = new THREE.PointLight(0xffb066, 0, 17, 2);
  dockLight.position.set(-8.2, 2.7, -14);
  dockLight.castShadow = false;
  scene.add(dockLight);
  lanternLights.push(dockLight);

  // 地面光斑：程序化径向渐变，避免再引一张贴图资产
  const glowCanvas = document.createElement('canvas');
  glowCanvas.width = glowCanvas.height = 128;
  const gctx = glowCanvas.getContext('2d');
  if (gctx) {
    const grad = gctx.createRadialGradient(64, 64, 0, 64, 64, 64);
    grad.addColorStop(0, 'rgba(255,196,132,0.9)');
    grad.addColorStop(0.4, 'rgba(255,152,84,0.34)');
    grad.addColorStop(1, 'rgba(255,120,60,0)');
    gctx.fillStyle = grad;
    gctx.fillRect(0, 0, 128, 128);
  }
  const glowTex = new THREE.CanvasTexture(glowCanvas);
  glowTex.colorSpace = THREE.SRGBColorSpace;
  const glowMat = new THREE.MeshBasicMaterial({
    map: glowTex,
    transparent: true,
    blending: THREE.AdditiveBlending,
    depthWrite: false,
    opacity: 0,
    fog: true,
  });
  const glowGeo = new THREE.PlaneGeometry(1, 1).rotateX(-Math.PI / 2);
  const glowMeshes = new THREE.InstancedMesh(glowGeo, glowMat, spots.length);
  {
    const m = new THREE.Matrix4();
    spots.forEach((sp, i) => {
      const dx = sp.rotY > 0 ? 0.45 : -0.45;
      m.makeScale(7.5, 1, 7.5);
      m.setPosition(sp.x + dx, sp.y + 0.05, sp.z);
      glowMeshes.setMatrixAt(i, m);
    });
    glowMeshes.instanceMatrix.needsUpdate = true;
  }
  glowMeshes.renderOrder = 3;
  glowMeshes.name = 'lantern-glow';
  scene.add(glowMeshes);

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
    for (const l of lanternLights) l.intensity = 26 * (p.lamp - 0.1);
    // 水面倒影的灯位与亮度：与点光源同源，灯亮水亮
    water.setLamps(
      lanternLights.map((l) => l.position),
      lanternLights.map(() => Math.max(0, (p.lamp - 0.25) * 0.95)),
    );
    glowMat.opacity = Math.max(0, (p.lamp - 0.15) * 0.55);
    water.setSky(new THREE.Color(p.skyHorizon), new THREE.Color(p.skyTop));

    postfx.setBloom(p.bloom);
    postfx.setTint(new THREE.Color(p.shadowTint), new THREE.Color(0xffd2a0), p.shadowAmount);
    scene.background = null;

    // 探针必须最后烘：天空、灯、雾、曝光全都要先落到新状态
    refreshProbe();
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
  if (opts.quality) {
    const cfg = QUALITY[opts.quality];
    renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, cfg.dpr));
    postfx.setAntialias(cfg.aa);
  }

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
    setQuality(q) {
      const cfg = QUALITY[q];
      renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, cfg.dpr));
      postfx.setAntialias(cfg.aa);
      if (sun.shadow.mapSize.x !== cfg.shadow) {
        sun.shadow.mapSize.set(cfg.shadow, cfg.shadow);
        // 改 mapSize 必须把旧的 shadow map 丢掉，否则不会重建
        sun.shadow.map?.dispose();
        sun.shadow.map = null;
      }
      resize();
    },
    dispose() {
      disposed = true;
      cancelAnimationFrame(raf);
      ro.disconnect();
      controls.dispose();
      postfx.dispose();
      env.dispose();
      probeRT.dispose();
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
