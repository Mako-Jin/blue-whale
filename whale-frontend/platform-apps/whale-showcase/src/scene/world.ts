import * as THREE from 'three';
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js';
import { createSceneEnv } from './env';
import type { SceneAssets } from './loader';
import { createPostFx } from './postfx';
import { WALL_X, alleySpots, buildSettlement, lanternSpots, windTime } from './props';
import { createSky, type SkyState } from './sky';
import { createTerrain, heightAt, GATE_ROAD_Y } from './terrain';
import { createFarRidges } from './ridges';
import { tuneSurfaces } from './textures';
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
export type PresetName = 'overview' | 'bridge' | 'street' | 'dock' | 'shop' | 'alley' | 'roofs' | 'bank' | 'gate';

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
  /** 暗角强度：白天几乎不要（大地不该四角发黑），夜里要重 */
  vignette: number;
  /** 水体本色：俯视时水面 85% 是这一项，不能全场一个值 */
  waterDeep: number;
  /** 浅水色：贴岸河床透出的水体色（比 deep 亮且偏沙石绿） */
  waterShallow: number;
  /** 远山剪影两层色：近层深、远层浅（吃雾后即大气透视） */
  ridgeNear: number;
  ridgeFar: number;
  /** 暗部染色（后期把阴影往这个色偏）与强度 */
  shadowTint: number;
  shadowAmount: number;
  /** 云量 0..1 与云的受光/背光色（S20） */
  cloud: number;
  cloudLight: number;
  cloudShade: number;
  /** 窗纸基色（S27）：白天是"纸"，夜里才是"灯"——同一材质按时段换 albedo */
  paperColor: number;
  /** 星空强度 0..1（S27）：夜 1 / 蓝调 0.3 / 黄昏与白天 0 */
  stars: number;
}

const TIMES: Record<TimeName, TimePreset> = {
  // 黄昏：太阳刻意放在**侧后 70°**（azim -25），不是正逆光。
  // 第一版把太阳放正前方（azim -118），结果整城变成纯黑剪影、水面烧成白带。
  dusk: {
    sunElev: 14,
    sunAzim: -25,
    sunColor: 0xffb066,
    sunIntensity: 3.4,
    // 黄昏的 hemi 提到 1.5：西岸整片落在山影里，只有 hemi + IBL 给光，
    // 0.95（实际 0.21）时那里是一块纯黑，画面左三分之一全废。
    // 不能再高：到 2.0 时石桥与白墙的暗面开始发灰，灯笼的光池就没对比了。
    hemiSky: 0x5a7ba8,
    hemiGround: 0x4a3c2c,
    hemiIntensity: 1.5,
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
    vignette: 0.5,
    waterDeep: 0x0d2126,
    waterShallow: 0x35443a,
    ridgeNear: 0x3a2f47,
    ridgeFar: 0x54435c,
    shadowTint: 0x1b2742,
    shadowAmount: 0.24,
    // 黄昏云：贴着天际线的积云被落日从侧面点亮，底面是紫灰
    cloud: 0.85,
    cloudLight: 0xffd0a0,
    cloudShade: 0x4a4560,
    // 黄昏窗纸：暖橙（Blender 原值），灯刚点上
    paperColor: 0xffb964,
    stars: 0,
  },
  day: {
    // azim -95 时太阳从正北打过来：全城主街的临河立面（法线 -X）与城墙外墙面
    // （+X）同时吃不到直射光，整座城只有屋顶顶面亮——"白天偏暗偏冷"的真正原因
    // 不是曝光，是主光方向与所有可见立面垂直。

    // 改成西南偏南（130°）：主立面与 +Z 端都有 0.6~0.7 的入射角，阴影朝东北
    // 落在建筑背后，既不挡视线又给街道一层硬影。
    sunElev: 48,
    sunAzim: 130,
    sunColor: 0xfff1d8,
    sunIntensity: 3.2,
    // hemi 不能是纯蓝：0x9fc7ff 会让所有背光白墙变成"蓝灰水泥板"。
    // 天空蓝要保留一点，但往灰里拉；地面反弹给暖色（土、石、草都是暖的）。
    hemiSky: 0xc4cbd2,
    hemiGround: 0x7d6c50,
    hemiIntensity: 1.15,
    skyTop: 0x4a86c4,
    // 0xd8e6f0 太白——可见天带几乎全是这一色，白云没有对比（云被雾吃掉的根因）
    skyHorizon: 0xcfe2ee,
    skyGround: 0x6d7a72,
    fogColor: 0xcbdadf,
    fogDensity: 0.0012,
    exposure: 1.0,
    haze: 0.32,
    lamp: 0.15,
    // 1.8 的 IBL 让整个白墙吃满蓝天天光，出图是"青瓷墙"。环境反射只作补光
    envIntensity: 1.25,
    bloom: 0.1,
    vignette: 0.3,
    // 正午的河水靠反射天光发亮，但俯视时菲涅尔只有 0.15，剩下 85% 是水体色。
    // 沿用黄昏的 0x0d2126 会得到一条黑河（"白天比黄昏还暗"的根源之一）。
    waterDeep: 0x2c4a52,
    waterShallow: 0x68836b,
    ridgeNear: 0x5f7873,
    ridgeFar: 0x7f968f,
    shadowTint: 0x4a4740,
    shadowAmount: 0.1,
    // 白天云：白顶灰底，量适中——纯蓝天也好看，但全景上三分之一太空。
    // shade 必须压到 0x7f95ad 一档：白天可见天带全是 0xd8e6f0 的亮雾色，
    // 云底不够深就直接被雾色吃掉
    cloud: 0.90,
    cloudLight: 0xfffaf0,
    cloudShade: 0x647a92,
    // 白天窗纸必须换"纸本色"（S27）：0xFFB964 的暖橙 albedo 在 3.2 的日照下
    // 读作三块发光 LED 板，与蓝天白云直接矛盾。漂白桑皮纸的灰黄才是白天
    // 该有的样子；夜里暖橙由本行换回去
    paperColor: 0xd8d2c2,
    stars: 0,
  },
  blue: {
    sunElev: 4,
    sunAzim: -30,
    sunColor: 0x93a8dc,
    sunIntensity: 1.1,
    hemiSky: 0x4a6a9a,
    hemiGround: 0x272d40,
    // 1.1 → 1.35：蓝调时刻西岸（背光面）几乎全黑，河谷只剩一条轮廓线。
    // 真实蓝调是"天还很亮、地已暗"，靠天光把地面托起来才像。
    hemiIntensity: 1.35,
    skyTop: 0x121d36,
    skyHorizon: 0x5d789f,
    skyGround: 0x1c222c,
    fogColor: 0x44546c,
    fogDensity: 0.0024,
    exposure: 1.22,
    haze: 0.5,
    lamp: 0.9,
    envIntensity: 2.0,
    bloom: 0.5,
    vignette: 0.55,
    waterDeep: 0x14282e,
    waterShallow: 0x2e4038,
    ridgeNear: 0x232f44,
    ridgeFar: 0x384a62,
    shadowTint: 0x141d38,
    shadowAmount: 0.34,
    // 蓝调云：只剩天光，云是比天顶亮半档的灰蓝剪影
    cloud: 0.55,
    cloudLight: 0x93a5c2,
    cloudShade: 0x39435a,
    // 蓝调：天还没黑透，窗纸微暖（灯刚亮，透光但不暖橙）
    paperColor: 0xe6d2a8,
    stars: 0.3,
  },
  night: {
    sunElev: 34,
    // 62 → 250（S27）：碎金路径在水面上的走向 = 月亮方位角的水平投影。
    // azim 62 时整条路径在相机背后；试 205 仍差 52°（超全景机位水平半视场
    // 30.6°）。全景视线方位 ≈254°，月方位给 250 → 路径从桥前一直铺进
    // 可见河段。副产物：临河白墙（-X）吃 0.28 的月光，屋面 0.56 不变
    sunAzim: 250,
    sunColor: 0x8fa6e0,
    // 0.55 时月光几乎照不出方向，屋顶与城墙全靠灯笼的局部光勾边，
    // 全景里整片建筑是黑的，只剩一排灯笼飘在空处——看不出"这是一座城"。
    // 提亮月光（保留冷蓝）比提 exposure 好：exposure 一抬，天空与灯笼一起变灰。
    sunIntensity: 0.95,
    hemiSky: 0x1b2740,
    hemiGround: 0x080a0e,
    // 0.42 → 0.7：夜景要能读出体量（墙、屋顶的转折），但天空必须仍比地面亮
    hemiIntensity: 0.7,
    skyTop: 0x050812,
    skyHorizon: 0x131c30,
    skyGround: 0x07090d,
    fogColor: 0x0f1626,
    fogDensity: 0.0028,
    exposure: 1.46,
    haze: 0.28,
    lamp: 1.7,
    envIntensity: 2.2,
    bloom: 0.45,
    vignette: 0.62,
    waterDeep: 0x081820,
    waterShallow: 0x1a2a26,
    ridgeNear: 0x0e1622,
    ridgeFar: 0x182234,
    shadowTint: 0x0c1730,
    shadowAmount: 0.4,
    // 夜云：月光给一点轮廓就够，量压低——夜里大团亮云会抢灯笼的戏
    cloud: 0.30,
    cloudLight: 0x2e3a54,
    cloudShade: 0x10161f,
    paperColor: 0xffb964,
    stars: 1,
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
  /**
   * 铺面：站在街心正对临街铺面。
   *
   * <p>街景机位（y=3.1）刚好在腰檐（3.38）以下，看过去是一道黑槽——檐廊里有什么
   * 全看不见。这一位压低到 2.2、退到街对面，用来看立面层次（腰檐 / 二层格窗 /
   * 檐廊 / 柜台 / 台阶）。</p>
   */
  shop: { pos: [12.4, 2.6, 17.2], target: [22.4, 2.6, 11.2], fov: 50 },
  /**
   * 巷口：站在街上斜看巷门。
   *
   * <p>正对着拍是一张"门洞证件照"——透视全被压平，巷子的深度反而看不出来。
   * 退到 z=20.6（巷口北侧 3.4m）、x=15.4，用 48° 斜看进去，巷门是斜的、
   * 巷内的光斑与尽头照壁形成两级纵深。</p>
   */
  alley: { pos: [13.8, 1.85, 19.2], target: [26.5, 2.55, 17.0], fov: 46 },
  /**
   * 俯视屋顶。
   *
   * <p>屋顶是这个场景里面积最大的一块"画面"，而全景机位（高 21m、距 60m）看过去
   * 屋顶只是几条斜边。要判断"有没有脊、有没有马头墙、有没有巷口"，必须有一个
   * 直接对着屋面的机位——否则改的全在盲区里。</p>
   */
  roofs: { pos: [-1, 25, 33], target: [24, 5, 11], fov: 42 },
  /**
   * 河岸：站在西岸草坡上、眼高 1.7m 往东北看码头与桥。
   *
   * <p>地被（草簇 / 灌木 / 滩石）只有 0.3~1.2m 高，所有既有相机位都在 3.5m 以上——
   * 草在画面里是地面上的一层颗粒，看不出"是一片草"还是"撒了一把碎屑"。
   * 验收地被必须有一个真正贴地的机位，否则这一层改了也看不见。</p>
   */
  bank: { pos: [-19, 6.4, -31], target: [-5.5, 0.9, -12.5], fov: 46 },
  /**
   * 城门（S30）。
   *
   * <p>站在桥头广场东侧、门洞轴线南侧几步斜看。第一版站在广场西南
   * （15.5,11.5）——z=12.4 的铺面把右半屏连屋带墙全挡了，只能看到
   * 城门的一只角。退到广场里侧（22.5,8.5）距离门洞 9m，铺面退到
   * 画面外，券门、拱圈石、城楼两坡顶与北端敌台的接缝全进画面。</p>
   */
  /**
   * 城门（S30）。
   *
   * <p>从城外东郊看城门的外脸——唯一能同时验收"外侧门脸 + 出城道路
   * 爬坡 + 城台与两侧城墙接缝"的角度。城内视角两个坑都实测过：
   * ① 相机低于目标点会被 controls.maxPolarAngle 抬成平射（pos y=4 /
   * target 6.2 → clamp 成 6.3），视线正好怼上铺面二楼挑檐；② z=12.4
   * 铺面的挑檐西北角（x≈20.5、z≈8.9）戳在广场到城门的视线带上，
   * 广场里任何机位右半屏都是它；再退又被牌坊（17.9,2）堵死前景。
   * 城内的门脸交给桥头机位（08d），本机位走城外。</p>
   */
  gate: { pos: [50, 8.5, 12], target: [30, 7, 1.5], fov: 46 },
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
  // 远山剪影：两层环形山脊填住地形边缘外的地平线（Basic 剪影 + 场景雾做大气透视）
  const ridges = createFarRidges();
  scene.add(ridges.group);

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
  // 材质收尾必须放在 buildSettlement 之后：牌坊是整件 GLB、民居是 InstancedMesh、
  // 巷门院墙是程序化合并件，只有扫一遍整棵树才能把三条路都覆盖到
  tuneSurfaces(settlement);

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
   * 窗纸不是灯。
   *
   * <p>同一个 `PAPER` 材质（窗纸 / 铺面里的光板 / 檐廊吊灯）原来和灯笼本体的灯罩
   * 一起按 2.2 倍推——黄昏时二层三扇窗糊成三块纯白 LED 板，窗棂的格子被 bloom
   * 吃了一片，反而看不出"这是窗"。纸是**透光**的，不是发光体：降到 1.25 倍，
   * 颜色不变（Blender 里给的是 0xFFB964 暖橙），窗棂才重新长出来。
   *
   * <p>灯笼本体（`lanternGlow`）保持 2.2：它是灯，就该比纸亮一档，而且夜里的
   * bloom 光晕全靠它。</p>
   */
  const paperMaterials = lampMaterials.filter((m) => /paper/i.test(m.name ?? ''));
  const lampBodyMaterials = lampMaterials.filter((m) => !/paper/i.test(m.name ?? ''));

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

  /*
   * 巷内壁灯。
   *
   * <p>巷子只有 2.8m 宽、6m 深，两侧是整进深落地的山墙——没有一个独立光源的话，
   * 巷内在黄昏之后是一条纯黑的槽，除了"有条缝"什么也看不出来。衰减距离给 8m：
   * 够照亮整条巷子与尽头的照壁，又不会漏到街上跟街灯打架。</p>
   *
   * <p>单独一个数组而不是塞进 {@code lanternLights}：那个数组同时喂给
   * {@code water.setLamps} 算水面倒影，巷子离河 9m、倒影是假的。</p>
   */
  const alleyLights: THREE.PointLight[] = [];
  for (const sp of alleySpots()) {
    const light = new THREE.PointLight(0xff9d52, 0, 9, 2);
    // 离墙面留 0.8m：壁灯贴着墙时，26 分之一米的距离就能把白墙烧出一块纯白
    light.position.set(23.5, heightAt(23.5, sp.z) + 2.0, sp.z - sp.w / 2 + 1.0);
    light.castShadow = false;
    scene.add(light);
    alleyLights.push(light);
  }

  /*
   * 城门壁灯（S30）。
   *
   * <p>门洞两侧各一盏，高度从门洞底（GATE_ROAD_Y）起算——门洞这一段地形
   * 被城门道掩模重塑过，直接查 heightAt 会拿到路肩的过渡值。与巷灯同理
   * 独立成数组：lanternLights 同时喂给 water.setLamps 算倒影，城门离河
   * 25m，倒影是假的。</p>
   */
  const gateLights: THREE.PointLight[] = [];
  for (const sx of [-1, 1]) {
    for (const dz of [-2.2, 2.2]) {
      const light = new THREE.PointLight(0xff9d52, 0, 10, 2);
      // 城内外两面各一对：外脸夜里没有灯就是一块纯黑剪影（S30 首轮夜景实测）
      light.position.set(WALL_X + sx * 3.4, GATE_ROAD_Y + 2.5, 2 + dz);
      light.castShadow = false;
      scene.add(light);
      gateLights.push(light);
    }
  }

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
      cloud: p.cloud,
      cloudLight: p.cloudLight,
      cloudShade: p.cloudShade,
      stars: p.stars,
    };
    sky.update(skyState);
    // 重建后贴图是新对象，必须重新挂上（旧的那张在 env 内部已释放）
    scene.environment = env.update(skyState);
    scene.environmentIntensity = p.envIntensity;

    for (const m of paperMaterials) {
      m.emissiveIntensity = 1.25 * lampScale;
      // 纸本色按时段换 albedo（S27）：白天 0xFFB964 的暖橙在日照下是 LED 板
      m.color.set(p.paperColor);
    }
    for (const m of lampBodyMaterials) m.emissiveIntensity = 2.2 * lampScale;
    for (const l of lanternLights) l.intensity = 26 * (p.lamp - 0.1);
    // 巷灯比街灯弱一档：巷子只有 2.8m 宽，26 candela 会把两侧白墙打爆
    for (const l of alleyLights) l.intensity = 9 * (p.lamp - 0.1);
    for (const l of gateLights) l.intensity = 9 * (p.lamp - 0.1);
    // 水面倒影的灯位与亮度：与点光源同源，灯亮水亮
    water.setLamps(
      lanternLights.map((l) => l.position),
      lanternLights.map(() => Math.max(0, (p.lamp - 0.25) * 0.95)),
    );
    glowMat.opacity = Math.max(0, (p.lamp - 0.15) * 0.55);
    water.setSky(new THREE.Color(p.skyHorizon), new THREE.Color(p.skyTop));
    water.setDeep(new THREE.Color(p.waterDeep));
    water.setShallow(new THREE.Color(p.waterShallow));
    ridges.setColors(p.ridgeNear, p.ridgeFar);

    postfx.setBloom(p.bloom);
    postfx.setVignette(p.vignette);
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
    sky.tick(t); // 云漂移
    windTime.value = t; // 地被风动
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

  // dev 调试句柄：截图探针（e2e/tools/probe-gate.mjs）从画面任意像素发射线
  // 查"挡视线的是什么"。生产构建 tree-shake 不掉也只是一次赋值，无副作用。
  if (import.meta.env.DEV) {
    (window as unknown as Record<string, unknown>).__bwScene = { scene, camera };
  }

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
