import {
  AmbientLight,
  BoxGeometry,
  Color,
  ConeGeometry,
  GridHelper,
  IcosahedronGeometry,
  Mesh,
  MeshStandardMaterial,
  PointLight,
  Scene,
  TorusKnotGeometry,
} from 'three';
import {
  addFog,
  addGround,
  addSun,
  disposeSubtree,
  forEachMaterial,
  solidMaterial,
} from '../common';
import type { SceneBuild, SceneDefinition, SceneMeta } from '../types';

/**
 * 渲染基线场景。
 *
 * 它是本应用**唯一一个不模拟任何真实环境**的场景，存在的理由是留一把尺子：
 * 几何体数量与三角面数固定、光照参数固定，因此换渲染器配置、换像素比、
 * 换画质档位之后，还能拿它对比帧率有没有掉。删掉它，性能变化就无从判断。
 */

const GROUND_SIZE = 64;
const ORBIT_RADIUS = 6.2;

const meta: SceneMeta = {
  id: 'baseline-lab',
  title: '渲染基线',
  summary: '几何与光照的对照场景，用来比对帧率与三角面数',
  tags: ['基准', '抽象'],
  icon: 'cube',
  // 排在最后：它是参照物，不是内容
  order: 90,
  accent: '#165dff',
  background: '#0b1020',
  climate: 'indoor',
  camera: {
    position: [10.5, 7.5, 13],
    target: [0, 1.8, 0],
  },
  distance: [6, 48],
  environmentIntensity: 0.35,
  options: { showGrid: true },
};

type Orbiter = {
  mesh: Mesh;
  /** 起始相位，避免几个物体挤在一起出发 */
  phase: number;
  /** 公转角速度（弧度/秒） */
  angularSpeed: number;
  /** 上下浮动幅度 */
  bob: number;
};

function create(): SceneBuild {
  const scene = new Scene();
  scene.background = new Color(meta.background);
  // 雾让远处的地面与网格淡出，而不是被相机远裁剪面硬生生切断
  addFog(scene, meta.background, 24, 60);

  // 环境光只负责"别让暗部死黑"，形体感要靠方向光
  scene.add(new AmbientLight(0x93a7d8, 0.75));
  addSun(scene, {
    color: 0xffffff,
    intensity: 2.6,
    position: [9, 15, 7],
    extent: 18,
  });

  // 会绕场移动的暖色补光。物理单位下点光源强度要按距离衰减来给
  const rim = new PointLight(0xff7d00, 55, 48, 2);
  rim.position.set(-7, 5, -6);
  scene.add(rim);

  const ground = addGround(scene, {
    size: GROUND_SIZE,
    color: 0x121a2c,
    metalness: 0.1,
    roughness: 0.92,
  });
  // 它在场景图里，`disposeSubtree(scene)` 会一并释放
  void ground;

  const grid = new GridHelper(GROUND_SIZE, GROUND_SIZE, 0x2f4a7d, 0x1b2a45);
  // 抬高一点点，避免与地面共面导致 z-fighting（表现为网格线忽明忽暗地闪）
  grid.position.y = 0.01;
  scene.add(grid);

  const core = new Mesh(new TorusKnotGeometry(2.15, 0.58, 220, 32), solidMaterial(0x165dff));
  core.position.y = 2.6;
  core.castShadow = true;
  scene.add(core);

  const orbiters: Orbiter[] = [
    {
      mesh: new Mesh(new IcosahedronGeometry(0.95, 1), solidMaterial(0x722ed1)),
      phase: 0,
      angularSpeed: 0.42,
      bob: 0.7,
    },
    {
      mesh: new Mesh(new BoxGeometry(1.35, 1.35, 1.35), solidMaterial(0x00b42a)),
      phase: (Math.PI * 2) / 3,
      angularSpeed: -0.34,
      bob: 0.45,
    },
    {
      mesh: new Mesh(new ConeGeometry(0.9, 1.9, 32), solidMaterial(0xff7d00)),
      phase: (Math.PI * 4) / 3,
      angularSpeed: 0.5,
      bob: 0.9,
    },
  ];

  orbiters.forEach((orbiter) => {
    orbiter.mesh.castShadow = true;
    orbiter.mesh.position.set(
      Math.cos(orbiter.phase) * ORBIT_RADIUS,
      1.5,
      Math.sin(orbiter.phase) * ORBIT_RADIUS,
    );
    scene.add(orbiter.mesh);
  });

  return {
    scene,

    update(elapsed: number): void {
      // 主体：绕自身轴慢转 + 轻微点头
      core.rotation.y = elapsed * 0.34;
      core.rotation.x = Math.sin(elapsed * 0.4) * 0.16;

      orbiters.forEach((orbiter, index) => {
        const angle = orbiter.phase + elapsed * orbiter.angularSpeed;
        orbiter.mesh.position.set(
          Math.cos(angle) * ORBIT_RADIUS,
          1.5 + Math.sin(elapsed * 1.1 + index * 1.7) * orbiter.bob,
          Math.sin(angle) * ORBIT_RADIUS,
        );
        orbiter.mesh.rotation.y = elapsed * (0.5 + index * 0.25);
        orbiter.mesh.rotation.x = elapsed * 0.28;
      });

      // 补光反向绕场，让高光在物体表面缓慢扫过
      rim.position.set(
        Math.cos(-elapsed * 0.45) * 9,
        5 + Math.sin(elapsed * 0.8) * 1.8,
        Math.sin(-elapsed * 0.45) * 9,
      );
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
      // 地面、网格、所有物体都在场景图里，一次 traverse 全部释放
      disposeSubtree(scene);
    },
  };
}

export const baselineLab: SceneDefinition = { meta, create };
