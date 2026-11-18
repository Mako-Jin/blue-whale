import * as THREE from 'three';
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js';

/**
 * 资产加载。
 *
 * <p>全部资产由 `tools/blender/*.py` 生成到 `public/assets/`，**不手工下载**——
 * 这样每件都能改参数重跑、可复现、可追溯（规划 §5 的"归一化工序"靠这个落地）。</p>
 *
 * <p>加载失败**不抛**：任何一件拿不到就退回程序化占位件。展示场景里"少一棵好树"
 * 远好过"整页白屏"，而且这条路径真会走到（新克隆仓库没有 public/assets）。</p>
 */

export type TreeVariant = 'round' | 'tall' | 'willow';
export type HouseVariant = 'small' | 'big';

export interface TemplateParts {
  geometry: THREE.BufferGeometry;
  material: THREE.Material;
}

export interface SceneAssets {
  /** 牌坊模板（未放置）；null 表示加载失败，调用方退回程序化版本 */
  gate: THREE.Object3D | null;
  /** 每种树拆成"几何 + 材质"对，供 InstancedMesh 使用 */
  trees: Record<TreeVariant, TemplateParts[]>;
  houses: Record<HouseVariant, TemplateParts[]>;
  /** 街灯（挑杆 + 灯笼），按实例放置 */
  lantern: TemplateParts[];
  /**
   * 城墙标准段（8m，沿 Z）。运行时按段长沿 Z 缩放铺满区间，
   * 所以这里同样拆成 parts 走 InstancedMesh。
   */
  wall: TemplateParts[];
  /** 码头：单件，整块放。拆成 parts 没有意义（只有一处） */
  dock: THREE.Object3D | null;
  /** 乌篷船：两艘同型不同朝向，走 InstancedMesh */
  boat: TemplateParts[];
  /**
   * 需要跟随时段调节的自发光材质（窗纸、灯罩）。
   * 材质实例是共享的，改一次全体生效——不要 clone 之后再改，那样只亮一栋。
   */
  glowMaterials: THREE.MeshStandardMaterial[];
}

const FILES: Record<string, string> = {
  gate: '/assets/paifang.glb',
  round: '/assets/tree_round.glb',
  tall: '/assets/tree_tall.glb',
  willow: '/assets/tree_willow.glb',
  houseSmall: '/assets/house_small.glb',
  houseBig: '/assets/house_big.glb',
  lantern: '/assets/lantern.glb',
  wall: '/assets/wall_seg.glb',
  dock: '/assets/dock.glb',
  boat: '/assets/boat.glb',
};

/**
 * 把一个 Object3D 拆成若干"几何 + 材质"对，并把各子节点的**相对变换烘进几何**。
 *
 * <p>InstancedMesh 要求"一个几何 + 一个材质"，而 GLB 里一棵树天然是"树干 + 树冠"
 * 两个 primitive（材质不同）。所以按材质拆开，每个 part 一套 InstancedMesh，
 * 实例矩阵相同——视觉上还是一棵树，但 drawcall 从"树数 × 2"降到"变体数 × 2"。</p>
 */
function extractParts(root: THREE.Object3D): TemplateParts[] {
  const parts: TemplateParts[] = [];
  root.updateMatrixWorld(true);
  const inv = new THREE.Matrix4().copy(root.matrixWorld).invert();
  root.traverse((o) => {
    const mesh = o as THREE.Mesh;
    if (!mesh.isMesh) return;
    const geo = mesh.geometry.clone();
    geo.applyMatrix4(new THREE.Matrix4().multiplyMatrices(inv, mesh.matrixWorld));
    const mats = Array.isArray(mesh.material) ? mesh.material : [mesh.material];
    for (const m of mats) {
      parts.push({ geometry: geo, material: m as THREE.Material });
    }
  });
  return parts;
}

export async function loadAssets(): Promise<SceneAssets> {
  const loader = new GLTFLoader();
  const load = (url: string) =>
    new Promise<THREE.Object3D | null>((resolve) => {
      loader.load(
        url,
        (gltf) => resolve(gltf.scene),
        undefined,
        (err) => {
          console.warn('[showcase] 资产加载失败，退回占位件:', url, err);
          resolve(null);
        },
      );
    });

  const [gate, round, tall, willow, houseSmall, houseBig, lantern, wall, dock, boat] = await Promise.all([
    load(FILES.gate),
    load(FILES.round),
    load(FILES.tall),
    load(FILES.willow),
    load(FILES.houseSmall),
    load(FILES.houseBig),
    load(FILES.lantern),
    load(FILES.wall),
    load(FILES.dock),
    load(FILES.boat),
  ]);

  // 窗纸/灯罩这类自发光材质要跟随时段调亮度，得先把实例收集起来
  const glow: THREE.MeshStandardMaterial[] = [];
  for (const root of [houseSmall, houseBig, lantern]) {
    root?.traverse((o) => {
      const m = o as THREE.Mesh;
      if (!m.isMesh) return;
      const mats = Array.isArray(m.material) ? m.material : [m.material];
      for (const mm of mats) {
        const std = mm as THREE.MeshStandardMaterial;
        if (std.emissive && std.emissiveIntensity > 0 && !glow.includes(std)) glow.push(std);
      }
    });
  }

  const prep = (r: THREE.Object3D | null): TemplateParts[] => {
    if (!r) return [];
    r.traverse((o) => {
      const m = o as THREE.Mesh;
      if (!m.isMesh) return;
      m.castShadow = true;
      m.receiveShadow = true;
    });
    return extractParts(r);
  };

  return {
    gate,
    trees: { round: prep(round), tall: prep(tall), willow: prep(willow) },
    houses: { small: prep(houseSmall), big: prep(houseBig) },
    lantern: prep(lantern),
    wall: prep(wall),
    dock,
    boat: prep(boat),
    glowMaterials: glow,
  };
}
