import {
  AmbientLight,
  Color,
  ConeGeometry,
  CylinderGeometry,
  GridHelper,
  IcosahedronGeometry,
  Mesh,
  MeshStandardMaterial,
  PointLight,
  Scene,
} from 'three';
import {
  addFog,
  addGround,
  addSun,
  createPrng,
  disposeSubtree,
  forEachMaterial,
  randomIn,
  solidMaterial,
} from '../common';
import { createAtmosphereRig } from '../atmosphere';
import type { SceneAtmosphere, SceneBuild, SceneDefinition, SceneMeta } from '../types';

/**
 * 原始丛林。
 *
 * 丛林感主要不来自"树多"，而来自两件事：
 * 1. **浓雾**把超过三四十米的植被整片吃掉，视线被"墙"挡住 —— 没有雾的树林
 *    一眼就能望到边，看起来像公园而不是丛林；
 * 2. **矮处的密集灌木**填掉视平线以下，让人看不到地面延伸到哪。
 *
 * 所有散布都走固定种子的伪随机（见 `createPrng`），因此每次构建出的林子
 * 逐棵一致 —— 否则这个场景既没法写断言，也没法复现用户截图里的那一帧。
 */

const PALETTE = {
  ground: 0x25301d,
  trunk: 0x4a3a28,
  canopy: 0x33582c,
  canopyLight: 0x4c7a37,
  fern: 0x3f6b33,
  rock: 0x4c4a45,
} as const;

/** 树的圈层：内圈留空，相机才能从林间望进去 */
const TREE_INNER_RADIUS = 16;
const TREE_OUTER_RADIUS = 27;
const TREE_COUNT = 14;

/** 散布种子。改这个数字会让整片林子的布局变一副样子。 */
const JUNGLE_SEED = 20261010;

/**
 * 雾的基准起止。
 *
 * 丛林感不来自"树多"，而来自**雾把中景整片吃掉** —— 视线被一堵软墙拦住，
 * 一眼望不到边才像丛林。接上大气之后这两段距离不再写死：它是一份**基调**，
 * 舞台会在此之上乘 `look` 给的能见度系数 —— 晴天看得更远，阴天更近。
 * 这也是本场景**必须**把基准交给 `createAtmosphereRig` 的原因：
 * 丛林与写字楼的雾距差着一个数量级（9~46 对 46~130），绝对值得不到两全。
 */
const BASE_FOG = { near: 9, far: 46 } as const;

/**
 * 阴影正交相机的范围。
 *
 * 它得罩住整片林子（树散布到外圈 27 米），否则边缘的树**不投影且不报错** ——
 * 症状是"阴影只在中景出现"，很容易被误判成阴影开关坏了。
 */
const SUN_EXTENT = 34;

const meta: SceneMeta = {
  id: 'primeval-jungle',
  title: '原始丛林',
  summary: '浓雾中的密林：树干、两层树冠、蕨丛与碎岩',
  tags: ['室外', '自然'],
  icon: 'tree',
  order: 20,
  accent: '#4c7a37',
  background: '#22331f',
  climate: 'outdoor',
  camera: {
    position: [10, 4.5, 14],
    target: [0, 4.2, 0],
  },
  distance: [3, 34],
  environmentIntensity: 0.5,
  // 林地上没有铺装，网格线在这里没有意义
  options: { showGrid: false, autoRotate: true },
};

type Canopy = {
  mesh: Mesh;
  /** 静止时的基准横坐标，"风"在它附近做偏移 */
  baseX: number;
  phase: number;
};

function create(): SceneBuild {
  const prng = createPrng(JUNGLE_SEED);
  const scene = new Scene();
  scene.background = new Color(meta.background);
  // 雾给到中景才开始起效：太近的话树全都融成剪影，画面只剩一片模糊的绿，
  // 有雾而看不清树形，等于两头都没占到
  addFog(scene, meta.background, BASE_FOG.near, BASE_FOG.far);

  /**
   * ---------- 光照：从树冠缝隙里漏下来的日光 ----------
   *
   * 下面两个初值只在"没挂大气"时可见（例如单测里直接 `create()`）；
   * 一旦 `createAtmosphere()` 被舞台调用，主光的方向、颜色、强度就**由太阳高度角算出来**，
   * 天光也跟着走 —— 黄昏时它会转暖，入夜转深蓝。也就是说从这一刻起，
   * "林子里几点该是什么光"不再由这里的一行常量决定。
   *
   * 注意 `ambient` 必须**留引用**（不能 `scene.add(new AmbientLight(...))` 了事）：
   * 大气要拿它当天光的把手。下面那盏绿色的 `bounce` 才是本场景的**固有特征光**
   * （落叶层反射），它不参与昼夜循环 —— 这属于二期"补光随昼夜淡出"的范围。
   */
  const ambient = new AmbientLight(0x4d7a4a, 0.85);
  scene.add(ambient);
  const sun = addSun(scene, {
    color: 0xffeec0,
    intensity: 2.6,
    position: [20, 28, 14],
    extent: SUN_EXTENT,
  });
  // 低处的绿色反弹光，模拟落叶层的反射 —— 少了它树冠下会黑成一片
  const bounce = new PointLight(0x76c05a, 34, 30, 2);
  bounce.position.set(-6, 2.4, -5);
  scene.add(bounce);

  addGround(scene, { size: 110, color: PALETTE.ground, roughness: 0.98 });

  const grid = new GridHelper(48, 24, 0x4c7a37, 0x3a5c2c);
  grid.position.y = 0.01;
  scene.add(grid);

  // ---------- 树干与树冠：单位几何体 + scale 出尺寸 ----------
  const trunkMaterial = solidMaterial(PALETTE.trunk, { metalness: 0.02, roughness: 0.95 });
  const canopyMaterial = solidMaterial(PALETTE.canopy, { metalness: 0.02, roughness: 0.9 });
  const canopyLightMaterial = solidMaterial(PALETTE.canopyLight, {
    metalness: 0.02,
    roughness: 0.86,
  });

  // 顶细底粗的单位圆柱，直接当树干用
  const trunkGeometry = new CylinderGeometry(0.68, 1, 1, 8);
  const coneGeometry = new ConeGeometry(1, 1, 7);

  const canopies: Canopy[] = [];

  for (let index = 0; index < TREE_COUNT; index += 1) {
    /**
     * 方位角取**均布 + 小幅抖动**而不是纯随机。
     *
     * 纯随机会把树挤成一堆，也会挡住轴向的通透视线；均布保证任意方向望出去
     * 都至少有树干可看，抖动又消除了"人工排列"的整齐感。
     */
    const baseAngle = (index / TREE_COUNT) * Math.PI * 2;
    const angle = baseAngle + randomIn(prng, -0.1, 0.1);
    const radius = randomIn(prng, TREE_INNER_RADIUS, TREE_OUTER_RADIUS);
    const x = Math.cos(angle) * radius;
    const z = Math.sin(angle) * radius;

    const trunkHeight = randomIn(prng, 7, 13);
    const trunkRadius = randomIn(prng, 0.28, 0.5);

    const trunk = new Mesh(trunkGeometry, trunkMaterial);
    trunk.scale.set(trunkRadius, trunkHeight, trunkRadius);
    trunk.position.set(x, trunkHeight / 2, z);
    trunk.castShadow = true;
    trunk.receiveShadow = true;
    scene.add(trunk);

    const crownBottom = trunkHeight * 0.55;
    const lowerHeight = trunkHeight * 0.5;
    const lowerRadius = trunkHeight * 0.3;
    const upperHeight = trunkHeight * 0.42;
    const upperRadius = trunkHeight * 0.21;

    const lower = new Mesh(coneGeometry, canopyMaterial);
    lower.scale.set(lowerRadius, lowerHeight, lowerRadius);
    lower.position.set(x, crownBottom + lowerHeight / 2, z);
    lower.castShadow = true;
    scene.add(lower);
    canopies.push({ mesh: lower, baseX: x, phase: index * 1.3 });

    const upper = new Mesh(coneGeometry, canopyLightMaterial);
    upper.scale.set(upperRadius, upperHeight, upperRadius);
    upper.position.set(x, crownBottom + lowerHeight * 0.62 + upperHeight / 2, z);
    upper.castShadow = true;
    scene.add(upper);
    canopies.push({ mesh: upper, baseX: x, phase: index * 1.3 + 0.6 });
  }

  // ---------- 灌木与蕨丛：填掉视平线以下，别让地面一览无余 ----------
  const fernMaterial = solidMaterial(PALETTE.fern, { metalness: 0.02, roughness: 0.92 });
  const bushGeometry = new IcosahedronGeometry(1, 0);

  for (let index = 0; index < 26; index += 1) {
    const angle = prng() * Math.PI * 2;
    const radius = randomIn(prng, 3, 15);
    const scale = randomIn(prng, 0.35, 0.95);
    const bush = new Mesh(bushGeometry, fernMaterial);
    // 压扁成"丛"，不压的话像球
    bush.scale.set(scale, scale * randomIn(prng, 0.45, 0.7), scale);
    bush.position.set(Math.cos(angle) * radius, scale * 0.32, Math.sin(angle) * radius);
    bush.rotation.y = prng() * Math.PI;
    bush.castShadow = true;
    bush.receiveShadow = true;
    scene.add(bush);
  }

  // ---------- 碎岩 ----------
  const rockMaterial = solidMaterial(PALETTE.rock, { metalness: 0.08, roughness: 0.95 });
  for (let index = 0; index < 9; index += 1) {
    const angle = prng() * Math.PI * 2;
    const radius = randomIn(prng, 4, 16);
    const scale = randomIn(prng, 0.35, 1.1);
    const rock = new Mesh(bushGeometry, rockMaterial);
    rock.scale.set(scale, scale * 0.7, scale * randomIn(prng, 0.8, 1.3));
    rock.position.set(Math.cos(angle) * radius, scale * 0.3, Math.sin(angle) * radius);
    rock.rotation.set(prng() * 0.6, prng() * Math.PI, prng() * 0.6);
    rock.castShadow = true;
    rock.receiveShadow = true;
    scene.add(rock);
  }

  /** 大气只挂一次（`??=`），生命周期严格跟着场景走 —— 舞台切回来时是全新的林子与全新的天穹 */
  let atmosphere: SceneAtmosphere | null = null;

  return {
    scene,

    update(elapsed: number): void {
      /**
       * 风：树冠在基准位置附近平移，而不是绕自身中心旋转。
       *
       * 旋转会让锥体看起来在自转（因为旋转中心在几何体中心），
       * 平移才像被风吹得整体摇晃。每棵树给了不同相位，避免整片林子齐步走。
       */
      canopies.forEach((canopy) => {
        const sway = Math.sin(elapsed * 0.75 + canopy.phase) * 0.16;
        canopy.mesh.position.x = canopy.baseX + sway;
      });
      bounce.position.set(-6 + Math.sin(elapsed * 0.4) * 4, 2.4, -5 + Math.cos(elapsed * 0.31) * 3);
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
     * 天穹在这里的意义与古建筑略有不同：林冠之上本来就该看得见天 ——
     * 而原来的写法把背景写死成一片深绿，等于宣称"丛林上面盖着绿盖子"。
     * 接上之后，从树冠缝隙望出去才是真的天空，雾色也会跟着天光走。
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

export const primevalJungle: SceneDefinition = { meta, create };
