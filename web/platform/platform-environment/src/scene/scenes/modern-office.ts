import {
  AmbientLight,
  Color,
  CylinderGeometry,
  GridHelper,
  Mesh,
  MeshStandardMaterial,
  PointLight,
  Scene,
  SphereGeometry,
} from 'three';
import {
  addBox,
  addFog,
  addGround,
  addSun,
  createPrng,
  disposeSubtree,
  forEachMaterial,
  glowMaterial,
  randomIn,
  solidMaterial,
} from '../common';
import { createAtmosphereRig } from '../atmosphere';
import type { SceneAtmosphere, SceneBuild, SceneDefinition, SceneMeta } from '../types';

/**
 * 现代写字楼。
 *
 * 玻璃幕墙的观感**几乎全来自反射**：`metalness` 拉到 0.9 而 `roughness` 压到 0.06
 * 之后，材质表面等于一面镜子 —— 场景若没有 `environment`（IBL），
 * 它就没有东西可反射，会渲染成一坨死黑。这是"用了 PBR 反而比 Phong 更难看"
 * 最常见的原因，所以本场景特意把 `environmentIntensity` 给得比其他场景高。
 */

const PALETTE = {
  ground: 0x3a4150,
  glass: 0x9fc6e8,
  slab: 0xb9c2cf,
  core: 0x4a5260,
  podium: 0x6b7484,
  lamp: 0xffcf8a,
  beacon: 0xff3b30,
} as const;

/** 层高（米）。楼板层线按它往上摞 */
const FLOOR_HEIGHT = 1.7;

const TOWER = { width: 7, height: 22, depth: 7, x: -2 } as const;
const ANNEX = { width: 5, height: 13, depth: 5, x: 8, z: -2 } as const;

/**
 * 雾的基准起止。
 *
 * 城市尺度下雾**不负责遮挡，负责天际线** —— 远处的高楼淡进天光里，城市才有纵深。
 * 所以这里的数字比丛林大一个数量级（46~130 对 9~46）。接上大气之后它只是**基调**，
 * 舞台会乘上 `look` 的能见度系数。这也正是雾距**不能**在 rig 里写死的原因：
 * 一套绝对值伺候不了差着数量级的两个场景。
 */
const BASE_FOG = { near: 46, far: 130 } as const;

/**
 * 阴影正交相机的范围。
 *
 * 要罩住主塔、副楼与广场上那一圈灯柱 —— 罩不住的构件**不投影且不报错**，
 * 看起来就像"这栋楼的阴影坏了"。
 */
const SUN_EXTENT = 30;

const meta: SceneMeta = {
  id: 'modern-office',
  title: '现代写字楼',
  summary: '玻璃幕墙塔楼、楼板层线与暮色城市广场',
  tags: ['室外', '建筑'],
  icon: 'tower',
  order: 30,
  accent: '#5aa0e0',
  background: '#27303f',
  climate: 'outdoor',
  camera: {
    position: [22, 14, 24],
    target: [0, 9, 0],
  },
  distance: [10, 80],
  /**
   * 玻璃是镜面，幕墙的样子几乎全靠环境反射 —— 但这个系数**不是越大越好**。
   *
   * 0.85 是按"兜底的室内环境贴图"（RoomEnvironment，中等亮度）标定的；换成
   * 天穹烘出来的真实 IBL 之后，正午天空散射的辐亮度远高于室内环境，
   * 0.85 等效于过曝 —— 实测整个塔楼反射成一整面白，楼板层线与轮廓全部糊掉。
   * 压到 0.45 之后幕墙里"有天空、有云的明暗"，楼的体量才回得来。
   * （写死别的场景同理：`environmentIntensity` 的标定基准是 IBL 的**实际亮度**，
   * IBL 换了来源，这里的数字就得重标。）
   */
  environmentIntensity: 0.45,
  options: { showGrid: true },
};

function create(): SceneBuild {
  const prng = createPrng(77031);
  const scene = new Scene();
  scene.background = new Color(meta.background);
  // 城市尺度大，雾要退到很远处才起效 —— 它在这里负责的是天际线，不是遮挡
  addFog(scene, meta.background, BASE_FOG.near, BASE_FOG.far);

  /**
   * 主光与天光在挂上大气之后**不再由这里决定**：方向、颜色、强度全部来自太阳高度角，
   * 下面的初值只在"没挂大气"时可见（例如单测里直接 `create()`）。
   *
   * 对本场景来说这不只是"更自然"——**它就是玻璃幕墙能不能看的前提**：
   * `metalness 0.92` / `roughness 0.06` 的材质等于一面镜子，没有 IBL 可反射时
   * 会渲染成一坨死黑。大气挂上之后，环境贴图由天穹烘出来，幕墙里就有了天与暮色。
   *
   * `ambient` 必须留引用（不能 `scene.add(new AmbientLight(...))` 了事）：大气拿它当天光的把手。
   */
  const ambient = new AmbientLight(0x8fa8c8, 0.62);
  scene.add(ambient);
  const sun = addSun(scene, {
    color: 0xffffff,
    intensity: 2.5,
    position: [18, 24, 11],
    extent: SUN_EXTENT,
  });
  // 低位暖色补光：傍晚的城市地面会把余晖反上来
  const cityGlow = new PointLight(0xffb070, 60, 70, 2);
  cityGlow.position.set(-14, 3, 12);
  scene.add(cityGlow);

  addGround(scene, {
    size: 120,
    color: PALETTE.ground,
    metalness: 0.35,
    roughness: 0.6,
  });

  // 广场铺装的分格，正好由网格辅助线充当，所以本场景**默认开着**它
  const grid = new GridHelper(64, 32, 0x5a6b85, 0x424f63);
  grid.position.y = 0.01;
  scene.add(grid);

  // ---------- 材质 ----------
  const glassMaterial = solidMaterial(PALETTE.glass, {
    metalness: 0.92,
    roughness: 0.06,
    transparent: true,
    opacity: 0.62,
  });
  const slabMaterial = solidMaterial(PALETTE.slab, { metalness: 0.35, roughness: 0.45 });
  const coreMaterial = solidMaterial(PALETTE.core, { metalness: 0.5, roughness: 0.5 });
  const podiumMaterial = solidMaterial(PALETTE.podium, { metalness: 0.55, roughness: 0.4 });

  // ---------- 主塔楼 ----------
  addBox(
    scene,
    glassMaterial,
    [TOWER.width, TOWER.height, TOWER.depth],
    [TOWER.x, TOWER.height / 2, 0],
  );

  /**
   * 楼板层线：每层用**一块比塔楼截面略大的扁盒**。
   *
   * 它中间是实心的，但从任何角度看都只是一圈外露的边 —— 因为实心部分
   * 完全埋在玻璃体内部。比起"每层四面各拼一条盒"，这省掉了四分之三的绘制调用，
   * 而画面上看不出区别。
   */
  const floors = Math.floor(TOWER.height / FLOOR_HEIGHT);
  for (let floor = 1; floor <= floors; floor += 1) {
    addBox(
      scene,
      slabMaterial,
      [TOWER.width + 0.18, 0.16, TOWER.depth + 0.18],
      [TOWER.x, floor * FLOOR_HEIGHT, 0],
    );
  }

  // 屋顶机房与天线
  const roofY = TOWER.height;
  addBox(scene, coreMaterial, [3.4, 1.8, 3.4], [TOWER.x, roofY + 0.9, 0]);

  const mastGeometry = new CylinderGeometry(0.08, 0.14, 4.5, 8);
  const mast = new Mesh(mastGeometry, coreMaterial);
  mast.position.set(TOWER.x, roofY + 1.8 + 2.25, 0);
  mast.castShadow = true;
  scene.add(mast);

  // 航空障碍灯：高空建筑的硬性标识，也顺手给了场景一个动态元素
  const beaconMaterial = glowMaterial(PALETTE.beacon, 1.4);
  const beacon = new Mesh(new SphereGeometry(0.24, 14, 10), beaconMaterial);
  beacon.position.set(TOWER.x, roofY + 1.8 + 4.5, 0);
  scene.add(beacon);

  // 竖向灯带：现代塔楼立面最常见的元素，一条就足够点题
  const stripMaterial = glowMaterial(PALETTE.lamp, 1.1);
  addBox(
    scene,
    stripMaterial,
    [0.56, TOWER.height - 2.4, 0.16],
    [TOWER.x, TOWER.height / 2 + 0.6, TOWER.depth / 2 + 0.02],
    { shadows: false },
  );

  // ---------- 副楼 ----------
  addBox(
    scene,
    glassMaterial,
    [ANNEX.width, ANNEX.height, ANNEX.depth],
    [ANNEX.x, ANNEX.height / 2, ANNEX.z],
  );
  const annexFloors = Math.floor(ANNEX.height / FLOOR_HEIGHT);
  for (let floor = 1; floor <= annexFloors; floor += 1) {
    addBox(
      scene,
      slabMaterial,
      [ANNEX.width + 0.16, 0.15, ANNEX.depth + 0.16],
      [ANNEX.x, floor * FLOOR_HEIGHT, ANNEX.z],
    );
  }

  // 连廊：把两栋楼连起来，否则它们只是两个孤立的盒子
  addBox(scene, podiumMaterial, [4.6, 2.6, 3], [(TOWER.x + ANNEX.x) / 2, 4.4, -1]);

  // 裙房大堂：底部一圈发光的落地玻璃
  const lobbyMaterial = glowMaterial(PALETTE.lamp, 0.55);
  addBox(scene, lobbyMaterial, [TOWER.width + 0.6, 1.5, TOWER.depth + 0.6], [TOWER.x, 0.9, 0], {
    shadows: false,
  });

  // ---------- 广场上的行人尺度的灯柱 ----------
  // 有了它们，建筑的体量才有参照物 —— 没有参照的高楼看不出有多高
  const poleGeometry = new CylinderGeometry(0.07, 0.09, 5, 8);
  const poleMaterial = coreMaterial;
  const lampMaterial = glowMaterial(PALETTE.lamp, 1.6);
  for (let index = 0; index < 6; index += 1) {
    const angle = (index / 6) * Math.PI * 2 + 0.4;
    const radius = randomIn(prng, 12, 16);
    const x = Math.cos(angle) * radius;
    const z = Math.sin(angle) * radius;

    const pole = new Mesh(poleGeometry, poleMaterial);
    pole.position.set(x, 2.5, z);
    pole.castShadow = true;
    scene.add(pole);

    const head = new Mesh(new SphereGeometry(0.16, 12, 10), lampMaterial);
    head.position.set(x, 5.05, z);
    scene.add(head);
  }

  /** 大气只挂一次（`??=`），生命周期严格跟着场景走 */
  let atmosphere: SceneAtmosphere | null = null;

  return {
    scene,

    update(elapsed: number): void {
      // 障碍灯闪烁：用 0.35~1.35 的区间来回走，绝不让它暗到看不见
      beaconMaterial.emissiveIntensity = 0.85 + Math.sin(elapsed * 2.4) * 0.5;
      // 灯带缓慢呼吸，和障碍灯的节奏错开，免得整个画面在同一个频率上跳
      stripMaterial.emissiveIntensity = 0.9 + Math.sin(elapsed * 0.8) * 0.35;
      cityGlow.position.set(-14 + Math.sin(elapsed * 0.22) * 5, 3, 12);
    },

    setWireframe(enabled: boolean): void {
      forEachMaterial(scene, (material) => {
        if (material instanceof MeshStandardMaterial) {
          material.wireframe = enabled;
        }
      });
    },

    setGridVisible(visible: boolean): void {
      grid.visible = visible;
    },

    /**
     * 本场景是**室外**（`climate: 'outdoor'`），所以接管大气。
     *
     * 这里拿到的天穹还兼职干一件别的活：它是**环境贴图的光源**。
     * 暮色广场上那面玻璃幕墙反射到的，正是这一片天。
     */
    createAtmosphere(): SceneAtmosphere {
      atmosphere ??= createAtmosphereRig({
        scene,
        keyLight: sun,
        ambient,
        baseBackground: meta.background,
        fog: BASE_FOG,
        sky: true,
      });
      return atmosphere;
    },

    dispose(): void {
      /**
       * 先拆大气：天穹有**两份**（主天穹 + 供烘环境贴图的捕获天穹），
       * 而捕获那份**不在场景图里** —— 只靠下面的 `traverse` 清不掉它，
       * 漏掉就是漏一份几何体加一份着色器。
       */
      atmosphere?.dispose();
      atmosphere = null;
      disposeSubtree(scene);
    },
  };
}

export const modernOffice: SceneDefinition = { meta, create };
