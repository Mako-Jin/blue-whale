import {
  BoxGeometry,
  DirectionalLight,
  Fog,
  Mesh,
  MeshStandardMaterial,
  PlaneGeometry,
  type ColorRepresentation,
  type Material,
  type Object3D,
  type Scene,
} from 'three';

/**
 * 各场景共用的构建工具。
 *
 * 抽出来的标准是「**每个场景都要做、且做法必须完全一致**」：
 * 释放 GPU 资源、遍历材质、阴影参数、撒点用的伪随机。
 * 「场景里长什么样」一律留在各自场景文件里 —— 那是每个场景唯一的表达，不该被抽象掉。
 */

/** 释放一个材质或材质数组。 */
export function disposeMaterials(material: Material | Material[]): void {
  const list = Array.isArray(material) ? material : [material];
  list.forEach((item) => item.dispose());
}

/**
 * 递归释放一棵子树的几何体与材质，然后清空它。
 *
 * **GPU 资源不会随对象被 GC 掉。** 几何体与材质各自持有显存缓冲，
 * 不显式 dispose 就会一直留在显存里 —— 场景切换频繁时攒到上下文上限
 * （浏览器通常 16 个），整个页面会突然渲染不动。
 * 抽成函数是为了让"释放得干净不干净"**可被断言**，而不是靠肉眼看。
 */
export function disposeSubtree(root: Object3D): void {
  root.traverse((child) => {
    const holder = child as Object3D & {
      geometry?: { dispose(): void };
      material?: Material | Material[];
      /** 光源的影子贴图。**只有 `Light` 有**，用可选链区分 */
      shadow?: { dispose(): void };
    };
    holder.geometry?.dispose();
    if (holder.material) {
      disposeMaterials(holder.material);
    }
    /**
     * 影子贴图要单独放 —— 它**不在这两类里**，这一点很坑。
     *
     * 影子贴图是 2048×2048 的 `WebGLRenderTarget`，由光源持有，
     * 既不挂在任何 mesh 的 `geometry`/`material` 上，也不随光源对象被 GC。
     * 只放前两者的写法乍看没问题（场景图确实清空了、`diagnostics().geometries`
     * 也确实回落），但**实测每切一次场景净增 2 张纹理**，攒到上下文上限时整页黑掉，
     * 而在此之前**没有任何症状**。它是靠"来回切四个场景后纹理数单调上涨"
     * （5 → 7 → 9 → 11 → 13）逮住的，补上这一行之后回到恒定 5。
     */
    holder.shadow?.dispose();
  });
  root.clear();
}

/** 遍历场景里全部材质（含数组材质的每一项），用于批量改属性。 */
export function forEachMaterial(root: Object3D, visit: (material: Material) => void): void {
  root.traverse((child) => {
    const holder = child as Object3D & { material?: Material | Material[] };
    if (!holder.material) {
      return;
    }
    const list = Array.isArray(holder.material) ? holder.material : [holder.material];
    list.forEach(visit);
  });
}

/**
 * 确定性伪随机（mulberry32）。
 *
 * 场景里的树、岩石、星点都要"随机"分布，但**绝不能用 `Math.random()`**：
 * 那样每次挂载画面都不一样 —— 既没法写断言，也没法复现用户截图里那一帧，
 * 排查问题时连"你看到的是哪个版本"都说不清。固定种子后，
 * 同一场景每次构建出的布局逐字一致。
 */
export function createPrng(seed: number): () => number {
  let state = seed >>> 0;
  return () => {
    state = (state + 0x6d2b79f5) >>> 0;
    let t = state;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

/** 在 [min, max) 上取一个由伪随机驱动的小数。 */
export function randomIn(prng: () => number, min: number, max: number): number {
  return min + prng() * (max - min);
}

/** `MeshStandardMaterial` 的构造参数，用于材质工厂的覆盖项。 */
type StandardMaterialParams = ConstructorParameters<typeof MeshStandardMaterial>[0];

/** PBR 实体材质。参数集中在这里，避免各场景各写一套金属度/粗糙度。 */
export function solidMaterial(
  color: ColorRepresentation,
  overrides: Partial<StandardMaterialParams> = {},
): MeshStandardMaterial {
  return new MeshStandardMaterial({
    color,
    metalness: 0.28,
    roughness: 0.34,
    ...overrides,
  });
}

/** 自发光材质：舷窗、室内灯、引擎焰这类"自己会亮"的表面。 */
export function glowMaterial(
  color: ColorRepresentation,
  emissiveIntensity = 1.6,
): MeshStandardMaterial {
  return new MeshStandardMaterial({
    color,
    emissive: color,
    emissiveIntensity,
    metalness: 0,
    roughness: 1,
  });
}

/**
 * 加一盏会投影的方向主光。
 *
 * 阴影相机的范围**必须跟着场景尺寸给**：默认范围很小，场景一大阴影就整片消失
 * （不报错，只是没有影子），这是最容易被误判成"阴影开关坏了"的一类问题。
 */
export function addSun(
  scene: Scene,
  config: {
    color: ColorRepresentation;
    intensity: number;
    position: readonly [number, number, number];
    /** 阴影正交相机的半边长：给到能罩住主体即可，过大会让阴影贴图精度被摊薄 */
    extent: number;
    target?: readonly [number, number, number];
  },
): DirectionalLight {
  const sun = new DirectionalLight(config.color, config.intensity);
  sun.position.set(...config.position);
  sun.castShadow = true;
  sun.shadow.mapSize.set(2048, 2048);
  sun.shadow.camera.near = 1;
  sun.shadow.camera.far = config.extent * 4;
  sun.shadow.camera.left = -config.extent;
  sun.shadow.camera.right = config.extent;
  sun.shadow.camera.top = config.extent;
  sun.shadow.camera.bottom = -config.extent;
  // 自遮挡麻点靠 bias 压掉；不设的话平面交界处会爬满条纹
  sun.shadow.bias = -0.0005;
  sun.shadow.camera.updateProjectionMatrix();
  scene.add(sun);
  // 方向光的 target 必须也进场景，否则它的 lookAt 不参与矩阵更新
  sun.target.position.set(...(config.target ?? [0, 0, 0]));
  scene.add(sun.target);
  return sun;
}

/**
 * 加雾。
 *
 * 雾不只是氛围 —— 它让远处的地面与物体**淡出到背景色**，
 * 而不是在相机远裁剪面被硬生生切断（那道整齐的边一眼就是假的）。
 * 雾色必须与背景色一致，否则"融进背景"无从谈起。
 */
export function addFog(scene: Scene, color: ColorRepresentation, near: number, far: number): void {
  scene.fog = new Fog(color, near, far);
}

/**
 * 单位立方体，供 `addBox` 复用。
 *
 * 所有方盒物体都靠 `mesh.scale` 出尺寸，而不是每个物体 `new` 一份几何体 ——
 * 古建筑那种几十个构件的场景，省下的是几十份顶点缓冲。
 *
 * **共享几何体被重复 `dispose()` 是安全的**：它只让渲染器释放显存，
 * 顶点数据本身仍在对象里，下次渲染会重新上传。
 */
const UNIT_BOX = new BoxGeometry(1, 1, 1);

/** 加一个方盒构件。场景里绝大多数"墙、梁、板、舱段"都是它。 */
export function addBox(
  parent: Object3D,
  material: Material,
  size: readonly [number, number, number],
  position: readonly [number, number, number],
  options: { shadows?: boolean } = {},
): Mesh {
  const mesh = new Mesh(UNIT_BOX, material);
  mesh.scale.set(size[0], size[1], size[2]);
  mesh.position.set(position[0], position[1], position[2]);
  const shadows = options.shadows ?? true;
  mesh.castShadow = shadows;
  mesh.receiveShadow = shadows;
  parent.add(mesh);
  return mesh;
}

/** 铺一块地面。`receiveShadow` 打开才有"物体落在实处"的感觉。 */
export function addGround(
  scene: Scene,
  config: {
    size: number;
    color: ColorRepresentation;
    metalness?: number;
    roughness?: number;
    y?: number;
  },
): Mesh {
  const ground = new Mesh(
    new PlaneGeometry(config.size, config.size),
    solidMaterial(config.color, {
      metalness: config.metalness ?? 0.1,
      roughness: config.roughness ?? 0.92,
    }),
  );
  ground.rotation.x = -Math.PI / 2;
  ground.position.y = config.y ?? 0;
  ground.receiveShadow = true;
  scene.add(ground);
  return ground;
}
