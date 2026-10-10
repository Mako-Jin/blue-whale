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
import type { SceneBuild, SceneDefinition, SceneMeta } from '../types';

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

const meta: SceneMeta = {
  id: 'ancient-architecture',
  title: '古建筑',
  summary: '中式殿宇：台基、柱列、斗拱与四坡屋顶',
  tags: ['室外', '建筑'],
  icon: 'roof',
  order: 10,
  accent: '#b99a5e',
  background: '#342f3d',
  camera: {
    position: [11, 7.5, 14],
    target: [0, 4.6, 0],
  },
  distance: [8, 60],
  environmentIntensity: 0.42,
  // 台基是石板地面，网格辅助线在这里只会碍眼
  options: { showGrid: false },
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
  addFog(scene, meta.background, 18, 55);

  // ---------- 光照：黄昏暖阳从西侧低角度打过来 ----------
  scene.add(new AmbientLight(0x7a86b8, 0.55));
  addSun(scene, {
    color: 0xffd9a0,
    intensity: 3.1,
    position: [14, 13, 9],
    extent: 24,
  });
  // 低处的地面反射光，让屋檐下的暗部不至于死黑
  const bounce = new PointLight(0xff8a3d, 42, 60, 2);
  bounce.position.set(-9, 3.2, -7);
  scene.add(bounce);

  addGround(scene, { size: 90, color: PALETTE.ground, roughness: 0.95 });

  const grid = new GridHelper(44, 22, 0x6b665e, 0x4a4640);
  grid.position.y = 0.01;
  scene.add(grid);

  // ---------- 台基与台阶 ----------
  const stoneMaterial = solidMaterial(PALETTE.stone, { metalness: 0.05, roughness: 0.88 });
  const stoneDarkMaterial = solidMaterial(PALETTE.stoneDark, {
    metalness: 0.05,
    roughness: 0.92,
  });

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

    dispose(): void {
      disposeSubtree(scene);
    },
  };
}

export const ancientArchitecture: SceneDefinition = { meta, create };
