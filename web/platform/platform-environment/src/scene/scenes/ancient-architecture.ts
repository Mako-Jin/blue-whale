import {
  AmbientLight,
  Color,
  CylinderGeometry,
  GridHelper,
  Group,
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
  disposeSubtree,
  forEachMaterial,
  glowMaterial,
  solidMaterial,
} from '../common';
import { createAtmosphereRig } from '../atmosphere';
import { createPlayerControls } from '../player/controls';
import { obstacleFromRect } from '../player/motor';
import type {
  MinimapFeatures,
  SceneAtmosphere,
  SceneBuild,
  SceneContext,
  SceneControls,
  SceneDefinition,
  SceneMeta,
} from '../types';

/**
 * 古建筑（中式殿宇）。
 *
 * 全部用基础几何体拼，不加载任何外部模型 —— 这一版的目标是**可辨识**：
 * 台基、柱列、斗拱层、四坡屋顶、正脊、檐下灯笼，六样齐了这个建筑就立住了。
 * 细节（瓦垄、彩画、门窗槅扇）留给后续深化。
 */

const PALETTE = {
  ground: 0x3a3630,
  stone: 0x6b665e,
  stoneDark: 0x545049,
  column: 0x8f3126,
  beam: 0x2f5d4a,
  bracket: 0xb99a5e,
  tile: 0x414956,
  ridge: 0x2b3138,
  lantern: 0xff5f2e,
} as const;

const PLATFORM = { width: 15, depth: 11, height: 1 } as const;
const COLUMN = { height: 4.4, radius: 0.21 } as const;
/** 屋顶投影尺寸：比台基每边出檐 1.2，这是中式屋顶最好认的比例特征 */
const EAVE = { width: 17.4, depth: 13.4 } as const;
const ROOF_HEIGHT = 3.4;

/** 柱底、柱顶、额枋、斗拱、屋顶逐层往上摞出来的高度 */
const BASE_TOP = PLATFORM.height;
const COLUMN_TOP = BASE_TOP + COLUMN.height;
const BEAM_Y = COLUMN_TOP + 0.21;
const BRACKET_LOWER_Y = COLUMN_TOP + 0.62;
const BRACKET_UPPER_Y = COLUMN_TOP + 0.99;
const ROOF_BOTTOM_Y = COLUMN_TOP + 1.14;
const ROOF_CENTER_Y = ROOF_BOTTOM_Y + ROOF_HEIGHT / 2;
const ROOF_TOP_Y = ROOF_BOTTOM_Y + ROOF_HEIGHT;

/**
 * 可行走广场：以原点为中心的正方形。
 *
 * 主人公被限制在这个范围内（见 `player/motor.ts` 的边界夹取），
 * 广场四周立一圈矮石压边，让"走到头了"有视觉反馈，
 * 而不是被一堵看不见的墙拦住。
 */
const ARENA = {
  halfExtent: 40,
  /** 压边石 */
  curbHeight: 0.34,
  curbThickness: 0.6,
  /** 网格分格数：80 / 40 = 2 世界单位一格，正好当铺装分缝 */
  gridDivisions: 40,
} as const;

const PLAYER = {
  /** 出生在殿前御路正中，面朝殿宇（-Z 方向，即 heading = π） */
  spawn: { x: 0, z: 13, heading: Math.PI },
  radius: 0.5,
  speed: 7.2,
} as const;

/** 台阶区域：三级踏跺从台基正面往外伸出来，一并算作不可通行 */
const STEP_BLOCK = {
  centerZ: PLATFORM.depth / 2 + 0.75,
  width: 4.2,
  depth: 1.5,
} as const;

/**
 * 不可通行的矩形（世界坐标）。
 *
 * 只挡殿宇本体与台阶 —— 广场是空的，玩家绕得过去。
 * 刻意不引物理引擎：这一阶段的诉求是"能走、不会穿墙"，不是碰撞仿真。
 */
const OBSTACLES = [
  obstacleFromRect(0, 0, PLATFORM.width, PLATFORM.depth),
  obstacleFromRect(0, STEP_BLOCK.centerZ, STEP_BLOCK.width, STEP_BLOCK.depth),
];

/** 小地图地物。与 `OBSTACLES` 同源 —— 挡人的东西必须也是地图上画出来的东西 */
const MINIMAP: MinimapFeatures = {
  groundSize: ARENA.halfExtent * 2,
  obstacles: [
    { x: 0, z: 0, width: PLATFORM.width, depth: PLATFORM.depth },
    { x: 0, z: STEP_BLOCK.centerZ, width: STEP_BLOCK.width, depth: STEP_BLOCK.depth },
  ],
};

/**
 * 雾的**基准**起止。
 *
 * 环境会在它之上乘一个能见度系数（夜里更近、正午稍远），所以这里给的是
 * "正午晴天"的值，而不是某一天的值。广场半径 40，雾若从 18 就起，
 * 人走到广场边缘时自己会被雾洗白 —— 那是"看不清人物"最常见的假故障。
 * 近端推到 26、远端 85：远处地面照样淡出到背景色，但主人公在整块广场上都清晰。
 */
const BASE_FOG = { near: 26, far: 85 } as const;

/**
 * 主光的阴影覆盖半径 —— 要罩得住"人会走到的地方"。
 *
 * 给 24 只够殿宇周围一圈，人一走出阴影范围，自己的影子会**无声消失**
 * —— 那看起来像"影子功能坏了"。代价是同样的贴图尺寸摊到更大的面积上，
 * 阴影边缘会稍软一点；这一版用清晰度换覆盖范围是划算的。
 */
const SUN_EXTENT = 30;

const meta: SceneMeta = {
  id: 'ancient-architecture',
  title: '古建筑',
  summary: '中式殿宇与可操控漫步的殿前广场',
  tags: ['室外', '建筑', '可操控'],
  icon: 'roof',
  order: 10,
  accent: '#b99a5e',
  background: '#342f3d',
  climate: 'outdoor',
  /**
   * 第三人称初始机位：出生点后方偏上，正对殿宇，焦点对齐主人公胸口。
   *
   * 俯角压到约 18°（高 5、水平距 11）而不是"俯瞰广场"，是**加了天穹之后必须的配套**：
   * 垂直半视角只有 25°，俯角一旦超过它，**地平线就落到画面之外** ——
   * 那样天空、日出、云全都看不见，等于白做。这个角度下画面顶部留出一条天，
   * 殿宇仍然完整入画，而且从低于屋檐的高度仰视，中式殿宇的体量反而更足。
   */
  camera: {
    position: [0, 5, 24],
    target: [0, 1.35, PLAYER.spawn.z],
  },
  distance: [7, 40],
  environmentIntensity: 0.42,
  /**
   * 网格现在是广场的铺装分缝，对"走到哪了"有实际参照作用，因此默认打开。
   * 自动环绕默认关闭 —— 镜头绕人转的同时还要操控人物会让人晕。
   */
  options: { showGrid: true, autoRotate: false },
};

/** 沿前、后檐与两侧铺开的柱位（台基面上）。 */
function columnPositions(): Array<[number, number]> {
  const halfWidth = PLATFORM.width / 2;
  const halfDepth = PLATFORM.depth / 2;
  const frontZ = halfDepth - 0.9;
  const edgeX = halfWidth - 1.3;
  // 六根檐柱均布
  const spans = [-1, -0.6, -0.2, 0.2, 0.6, 1] as const;

  const positions: Array<[number, number]> = [];
  spans.forEach((ratio) => {
    positions.push([ratio * edgeX, frontZ]);
    positions.push([ratio * edgeX, -frontZ]);
  });
  [-2.3, 0, 2.3].forEach((z) => {
    positions.push([edgeX, z]);
    positions.push([-edgeX, z]);
  });
  return positions;
}

function create(): SceneBuild {
  const scene = new Scene();
  scene.background = new Color(meta.background);
  addFog(scene, meta.background, BASE_FOG.near, BASE_FOG.far);

  /**
   * ---------- 光照：主光与天光不再写死 ----------
   *
   * 下面这两个初值只在"没挂大气"时可见（例如单测里直接 `create()`）；
   * 一旦 `createAtmosphere()` 被舞台调用，主光的方向、颜色、强度就**由太阳高度角算出来**，
   * 雾距也跟着能见度系数走。换句话说：从这一刻起，"黄昏该是什么样"
   * 不再由这里的一行常量决定，而是由 `scene/env/` 里的物理关系决定。
   */
  const ambient = new AmbientLight(0x7a86b8, 0.55);
  scene.add(ambient);
  const sun = addSun(scene, {
    color: 0xffd9a0,
    intensity: 3.1,
    position: [14, 13, 9],
    extent: SUN_EXTENT,
  });
  // 低处的地面反射光，让屋檐下的暗部不至于死黑
  const bounce = new PointLight(0xff8a3d, 42, 60, 2);
  bounce.position.set(-9, 3.2, -7);
  scene.add(bounce);

  // ---------- 广场地面 ----------
  // 地面比可行走范围大得多：走到边界时看不到地面的硬边，视野里全是地
  addGround(scene, { size: 220, color: PALETTE.ground, roughness: 0.95 });

  const grid = new GridHelper(ARENA.halfExtent * 2, ARENA.gridDivisions, 0x6b665e, 0x4a4640);
  grid.position.y = 0.01;
  scene.add(grid);

  const stoneMaterial = solidMaterial(PALETTE.stone, { metalness: 0.05, roughness: 0.88 });
  const stoneDarkMaterial = solidMaterial(PALETTE.stoneDark, {
    metalness: 0.05,
    roughness: 0.92,
  });

  // 广场压边：一圈矮石。四下都矮，不挡视线，但一眼能看出范围到哪
  const curbEdge = ARENA.halfExtent * 2 + ARENA.curbThickness;
  const curbY = ARENA.curbHeight / 2;
  addBox(
    scene,
    stoneDarkMaterial,
    [curbEdge, ARENA.curbHeight, ARENA.curbThickness],
    [0, curbY, -ARENA.halfExtent],
  );
  addBox(
    scene,
    stoneDarkMaterial,
    [curbEdge, ARENA.curbHeight, ARENA.curbThickness],
    [0, curbY, ARENA.halfExtent],
  );
  addBox(
    scene,
    stoneDarkMaterial,
    [ARENA.curbThickness, ARENA.curbHeight, curbEdge],
    [ARENA.halfExtent, curbY, 0],
  );
  addBox(
    scene,
    stoneDarkMaterial,
    [ARENA.curbThickness, ARENA.curbHeight, curbEdge],
    [-ARENA.halfExtent, curbY, 0],
  );

  // ---------- 台基与台阶 ----------

  addBox(
    scene,
    stoneMaterial,
    [PLATFORM.width, PLATFORM.height, PLATFORM.depth],
    [0, PLATFORM.height / 2, 0],
  );
  // 压边石：上沿外挑一圈，建筑的"重"感几乎全靠这一道
  addBox(
    scene,
    stoneDarkMaterial,
    [PLATFORM.width + 0.6, 0.18, PLATFORM.depth + 0.6],
    [0, PLATFORM.height - 0.09, 0],
  );

  // 三级踏跺，从台基正面往下降
  const stepDepth = 0.5;
  const stepHeight = PLATFORM.height / 3;
  [0, 1, 2].forEach((index) => {
    addBox(
      scene,
      stoneDarkMaterial,
      [4.2, stepHeight, stepDepth],
      [
        0,
        PLATFORM.height - stepHeight / 2 - index * stepHeight,
        PLATFORM.depth / 2 + 0.25 + index * stepDepth,
      ],
    );
  });

  // ---------- 柱列 ----------
  const columnMaterial = solidMaterial(PALETTE.column, { metalness: 0.06, roughness: 0.55 });
  // 18 根柱子尺寸一致，共享一份几何体；柱顶略收，是一眼能看出的中式收分
  const columnGeometry = new CylinderGeometry(
    COLUMN.radius * 0.9,
    COLUMN.radius,
    COLUMN.height,
    16,
  );
  columnPositions().forEach(([x, z]) => {
    const column = new Mesh(columnGeometry, columnMaterial);
    column.position.set(x, BASE_TOP + COLUMN.height / 2, z);
    column.castShadow = true;
    scene.add(column);
  });

  // ---------- 额枋：柱头之间的横梁 ----------
  const beamMaterial = solidMaterial(PALETTE.beam, { metalness: 0.08, roughness: 0.6 });
  const beamHalfDepth = PLATFORM.depth / 2 - 0.9;
  const beamHalfWidth = PLATFORM.width / 2 - 1.3;
  addBox(scene, beamMaterial, [beamHalfWidth * 2 + 0.6, 0.42, 0.42], [0, BEAM_Y, beamHalfDepth]);
  addBox(scene, beamMaterial, [beamHalfWidth * 2 + 0.6, 0.42, 0.42], [0, BEAM_Y, -beamHalfDepth]);
  addBox(scene, beamMaterial, [0.42, 0.42, beamHalfDepth * 2], [beamHalfWidth, BEAM_Y, 0]);
  addBox(scene, beamMaterial, [0.42, 0.42, beamHalfDepth * 2], [-beamHalfWidth, BEAM_Y, 0]);

  // ---------- 斗拱：两层出挑的环带 + 外侧齿状"斗" ----------
  const bracketMaterial = solidMaterial(PALETTE.bracket, { metalness: 0.1, roughness: 0.7 });
  const outerZ = PLATFORM.depth / 2 - 0.8;
  const outerX = PLATFORM.width / 2 - 1.2;

  // 下层：紧贴额枋之上
  addBox(scene, bracketMaterial, [EAVE.width - 1.0, 0.44, 0.62], [0, BRACKET_LOWER_Y, outerZ]);
  addBox(scene, bracketMaterial, [EAVE.width - 1.0, 0.44, 0.62], [0, BRACKET_LOWER_Y, -outerZ]);
  addBox(scene, bracketMaterial, [0.62, 0.44, EAVE.depth - 2.4], [outerX, BRACKET_LOWER_Y, 0]);
  addBox(scene, bracketMaterial, [0.62, 0.44, EAVE.depth - 2.4], [-outerX, BRACKET_LOWER_Y, 0]);
  // 上层：再向外挑一点，形成"叠斗"的阶梯感
  addBox(scene, bracketMaterial, [EAVE.width - 0.4, 0.3, 0.5], [0, BRACKET_UPPER_Y, outerZ + 0.16]);
  addBox(
    scene,
    bracketMaterial,
    [EAVE.width - 0.4, 0.3, 0.5],
    [0, BRACKET_UPPER_Y, -outerZ - 0.16],
  );
  addBox(scene, bracketMaterial, [0.5, 0.3, EAVE.depth - 1.6], [outerX + 0.16, BRACKET_UPPER_Y, 0]);
  addBox(
    scene,
    bracketMaterial,
    [0.5, 0.3, EAVE.depth - 1.6],
    [-outerX - 0.16, BRACKET_UPPER_Y, 0],
  );

  const bracketTeeth: Array<[number, number]> = [];
  [-6.6, -4.4, -2.2, 0, 2.2, 4.4, 6.6].forEach((x) => {
    bracketTeeth.push([x, outerZ + 0.28]);
    bracketTeeth.push([x, -outerZ - 0.28]);
  });
  [-3.3, -1.1, 1.1, 3.3].forEach((z) => {
    bracketTeeth.push([outerX + 0.28, z]);
    bracketTeeth.push([-outerX - 0.28, z]);
  });
  bracketTeeth.forEach(([x, z]) => {
    addBox(scene, bracketMaterial, [0.74, 0.52, 0.74], [x, BRACKET_LOWER_Y + 0.06, z]);
  });

  // ---------- 四坡屋顶 ----------
  /**
   * 四棱柱当成屋顶用：`CylinderGeometry(..., 4)` 的底面顶点落在 ±x / ±z 轴上
   * （俯视是个**菱形**），先 `rotateY(45°)` 转成轴对齐正方形，
   * 再沿 z 压扁就得到矩形底面。两步都烘焙进几何体，
   * 因此 Mesh 本身不带旋转 —— 后面做缩放或加动画都不会踩到变换顺序的坑。
   *
   * 顶面半径不为 0（不是尖顶）是有意的：截出来的平顶加上正脊，
   * 看起来才是庑殿顶；尖顶会变成亭子。
   */
  const roofBottomRadius = EAVE.width / Math.SQRT2;
  const ridgeLength = 4.1;
  const roofTopRadius = ridgeLength / Math.SQRT2;
  const roofSquash = EAVE.depth / EAVE.width;

  const roofGeometry = new CylinderGeometry(roofTopRadius, roofBottomRadius, ROOF_HEIGHT, 4);
  roofGeometry.rotateY(Math.PI / 4);
  roofGeometry.scale(1, 1, roofSquash);

  const tileMaterial = solidMaterial(PALETTE.tile, { metalness: 0.12, roughness: 0.62 });
  const roof = new Mesh(roofGeometry, tileMaterial);
  roof.position.y = ROOF_CENTER_Y;
  roof.castShadow = true;
  roof.receiveShadow = true;
  scene.add(roof);

  const ridgeMaterial = solidMaterial(PALETTE.ridge, { metalness: 0.15, roughness: 0.5 });
  addBox(scene, ridgeMaterial, [ridgeLength + 0.3, 0.46, 0.5], [0, ROOF_TOP_Y + 0.1, 0]);
  // 正脊两端的鸱吻，用两块小方体示意
  addBox(scene, ridgeMaterial, [0.5, 0.72, 0.42], [ridgeLength / 2 + 0.28, ROOF_TOP_Y + 0.24, 0]);
  addBox(scene, ridgeMaterial, [0.5, 0.72, 0.42], [-ridgeLength / 2 - 0.28, ROOF_TOP_Y + 0.24, 0]);

  // ---------- 檐下灯笼 ----------
  const lanternMaterial = glowMaterial(PALETTE.lantern, 1.9);
  const cordMaterial = solidMaterial(0x2a2620, { metalness: 0, roughness: 0.9 });
  const cordGeometry = new CylinderGeometry(0.035, 0.035, 0.8, 6);
  const bulbGeometry = new SphereGeometry(0.34, 18, 14);

  const lanterns = [-5.5, 5.5].map((x) => {
    const group = new Group();
    group.position.set(x, BRACKET_LOWER_Y - 0.1, outerZ);

    const cord = new Mesh(cordGeometry, cordMaterial);
    cord.position.y = -0.4;
    group.add(cord);

    const bulb = new Mesh(bulbGeometry, lanternMaterial);
    bulb.position.y = -0.95;
    group.add(bulb);

    // 灯笼本身当不了光源：真正的照明靠这两盏暖色点光源
    const lamp = new PointLight(PALETTE.lantern, 9, 9, 2);
    lamp.position.y = -0.95;
    group.add(lamp);

    scene.add(group);
    return group;
  });

  /**
   * 大气实例**延迟创建**：只有舞台真的要这个能力时才建天穹。
   * 直接 `create()` 的调用方（单测、工具脚本）因此不会凭空多出两套天穹。
   */
  let atmosphere: SceneAtmosphere | null = null;

  return {
    scene,

    update(elapsed: number): void {
      // 灯笼轻摆：一左一右反相，画面才不像整体在晃
      const swing = Math.sin(elapsed * 0.9) * 0.075;
      lanterns.forEach((lantern, index) => {
        lantern.rotation.z = swing * (index === 0 ? 1 : -1);
      });
      // 地面反射光缓慢游移，屋檐下的高光会跟着扫过柱子
      bounce.position.set(-9 + Math.sin(elapsed * 0.3) * 3.2, 3.2, -7);
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
     * 本场景是**可操控**的：殿前广场上有一个可以走的主人公。
     *
     * 主人公与输入绑定都活在控制器里，而不是场景图里 —— 这样"切到别的场景"
     * 会把它们一并拆掉（含键盘/右键监听），不会留下一个还能响应按键的幽灵人物。
     */
    createControls(context: SceneContext): SceneControls {
      return createPlayerControls(
        {
          sceneId: meta.id,
          scene,
          spawn: PLAYER.spawn,
          halfExtent: ARENA.halfExtent,
          obstacles: OBSTACLES,
          minimap: MINIMAP,
          radius: PLAYER.radius,
          speed: PLAYER.speed,
        },
        context,
      );
    },

    /**
     * 本场景是**室外**（`climate: 'outdoor'`），所以接管大气：
     * 天穹 + 日月主光 + 雾与背景联动 + 被风推动的云。
     *
     * 只挂一次（`??=`）。生命周期严格跟着场景走 —— 舞台切回来时会重新 `create()`，
     * 拿到的是全新的场景图与全新的大气，不会有两份天穹同时活着。
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

export const ancientArchitecture: SceneDefinition = { meta, create };
