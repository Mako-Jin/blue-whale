import {
  AmbientLight,
  BoxGeometry,
  Color,
  ConeGeometry,
  DirectionalLight,
  Fog,
  GridHelper,
  IcosahedronGeometry,
  Mesh,
  MeshStandardMaterial,
  PerspectiveCamera,
  PlaneGeometry,
  PointLight,
  Scene,
  TorusKnotGeometry,
  type BufferGeometry,
  type Material,
  type Object3D,
} from 'three';

/**
 * 场景图装配。
 *
 * 这一层**刻意不碰 `WebGLRenderer`**：渲染器需要真实的 WebGL 上下文，
 * 而场景图只是普通对象。分开之后，「场景里有什么、动画怎么推进、资源怎么释放」
 * 全部可以在 jsdom 里跑单元测试，只有真正需要 GPU 的那一步留在浏览器里验证。
 */

/** 场景底色，同时用作雾色 —— 两者一致才有"远处融进背景"的纵深感。 */
const BACKGROUND = 0x0b1020;

/** 地面与网格的边长 */
const GROUND_SIZE = 64;

/** 环绕物体的公转半径 */
const ORBIT_RADIUS = 6.2;

export type World = {
  scene: Scene;
  camera: PerspectiveCamera;
  /** 可交互网格数量（不含地面与网格辅助线），供自检与测试断言 */
  readonly meshCount: number;
  /**
   * 推进一帧。
   *
   * 传**累计秒数**而不是增量：动画全部写成 `elapsed` 的显式函数，
   * 于是任何一帧的状态都可以由 `elapsed` 唯一确定 —— 测试里不用模拟帧序列，
   * 也天然不会因为掉帧而出现"转着转着速度变了"。
   */
  update(elapsed: number): void;
  /** 切换所有物体材质的线框模式 */
  setWireframe(enabled: boolean): void;
  setGridVisible(visible: boolean): void;
  /** 释放全部几何体与材质。**GPU 资源不会随对象被 GC 掉，必须显式释放。** */
  dispose(): void;
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

function standardMaterial(color: number): MeshStandardMaterial {
  return new MeshStandardMaterial({
    color,
    metalness: 0.28,
    roughness: 0.34,
  });
}

/** 释放一个材质或材质数组。 */
function disposeMaterials(material: Material | Material[]): void {
  const list = Array.isArray(material) ? material : [material];
  list.forEach((item) => item.dispose());
}

/**
 * 递归释放一棵子树的几何体与材质，然后清空它。
 *
 * 抽成导出函数是为了让"释放得干净不干净"这件事**可被断言**，
 * 而不是靠"我看了一眼没报错"。
 */
export function disposeSubtree(root: Object3D): void {
  root.traverse((child) => {
    const holder = child as Object3D & {
      geometry?: BufferGeometry;
      material?: Material | Material[];
    };
    holder.geometry?.dispose();
    if (holder.material) {
      disposeMaterials(holder.material);
    }
  });
  root.clear();
}

export function createWorld(): World {
  const scene = new Scene();
  scene.background = new Color(BACKGROUND);
  // 雾让远处的地面与网格淡出，而不是被相机远裁剪面硬生生切断
  scene.fog = new Fog(BACKGROUND, 24, 60);

  const camera = new PerspectiveCamera(50, 1, 0.1, 200);
  camera.position.set(10.5, 7.5, 13);
  camera.lookAt(0, 1.4, 0);

  // ---------- 光照 ----------
  // 环境光只负责"别让暗部死黑"，形体感要靠方向光
  scene.add(new AmbientLight(0x93a7d8, 0.75));

  const key = new DirectionalLight(0xffffff, 2.6);
  key.position.set(9, 15, 7);
  key.castShadow = true;
  key.shadow.mapSize.set(2048, 2048);
  key.shadow.camera.near = 1;
  key.shadow.camera.far = 64;
  key.shadow.camera.left = -18;
  key.shadow.camera.right = 18;
  key.shadow.camera.top = 18;
  key.shadow.camera.bottom = -18;
  // 阴影贴图的自遮挡麻点靠 bias 压掉；不设的话平面交界处会爬满条纹
  key.shadow.bias = -0.0005;
  key.shadow.camera.updateProjectionMatrix();
  scene.add(key);
  // 方向光的 target 必须也进场景，否则它的 lookAt 不参与矩阵更新
  scene.add(key.target);

  // 会绕场移动的暖色补光。物理单位下点光源强度要按距离衰减来给，55 是配着 distance=48 试出来的
  const rim = new PointLight(0xff7d00, 55, 48, 2);
  rim.position.set(-7, 5, -6);
  scene.add(rim);

  // ---------- 地面与网格 ----------
  const ground = new Mesh(
    new PlaneGeometry(GROUND_SIZE, GROUND_SIZE),
    new MeshStandardMaterial({ color: 0x121a2c, metalness: 0.1, roughness: 0.92 }),
  );
  ground.rotation.x = -Math.PI / 2;
  ground.receiveShadow = true;
  scene.add(ground);

  const grid = new GridHelper(GROUND_SIZE, GROUND_SIZE, 0x2f4a7d, 0x1b2a45);
  // 抬高一点点，避免与地面共面导致 z-fighting（表现为网格线忽明忽暗地闪）
  grid.position.y = 0.01;
  scene.add(grid);

  // ---------- 物体 ----------
  const core = new Mesh(new TorusKnotGeometry(2.15, 0.58, 220, 32), standardMaterial(0x165dff));
  core.position.y = 2.6;
  core.castShadow = true;
  scene.add(core);

  const orbiters: Orbiter[] = [
    {
      mesh: new Mesh(new IcosahedronGeometry(0.95, 1), standardMaterial(0x722ed1)),
      phase: 0,
      angularSpeed: 0.42,
      bob: 0.7,
    },
    {
      mesh: new Mesh(new BoxGeometry(1.35, 1.35, 1.35), standardMaterial(0x00b42a)),
      phase: (Math.PI * 2) / 3,
      angularSpeed: -0.34,
      bob: 0.45,
    },
    {
      mesh: new Mesh(new ConeGeometry(0.9, 1.9, 32), standardMaterial(0xff7d00)),
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

  const meshes: Mesh[] = [core, ...orbiters.map((orbiter) => orbiter.mesh)];

  return {
    scene,
    camera,
    meshCount: meshes.length,

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
      /**
       * 遍历整棵场景树而不是只改那几个"物体"：线框模式的用途是**看清几何构成**，
       * 地面本身也是几何体。只挑一部分改，用户点开关时会觉得"有的生效有的没生效"。
       */
      scene.traverse((child) => {
        if (!(child instanceof Mesh)) {
          return;
        }
        const materials = Array.isArray(child.material) ? child.material : [child.material];
        materials.forEach((material) => {
          if (material instanceof MeshStandardMaterial) {
            material.wireframe = enabled;
          }
        });
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
