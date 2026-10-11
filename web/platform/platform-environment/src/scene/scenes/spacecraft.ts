import {
  AmbientLight,
  BufferGeometry,
  Color,
  ConeGeometry,
  CylinderGeometry,
  Float32BufferAttribute,
  GridHelper,
  Mesh,
  MeshStandardMaterial,
  Points,
  PointsMaterial,
  PointLight,
  Scene,
  SphereGeometry,
} from 'three';
import {
  addBox,
  addSun,
  createPrng,
  disposeSubtree,
  forEachMaterial,
  glowMaterial,
  randomIn,
  solidMaterial,
} from '../common';
import type { SceneBuild, SceneDefinition, SceneMeta } from '../types';

/**
 * 太空飞船。
 *
 * **本场景刻意不加雾。** 雾是"空气里的悬浮物散射光线"，太空没有空气，
 * 硬加就变成了雾天里的飞船。别的场景靠雾做纵深，这里改由两样东西承担：
 * 一层球壳状分布的星点场，和一颗远处的行星 —— 前者给出无穷远的暗示，
 * 后者给出明确的尺度参照。
 */

const PALETTE = {
  hull: 0xb8bfc9,
  hullDark: 0x4a5058,
  panel: 0x1c2f52,
  window: 0x7fd4ff,
  flame: 0x6ab8ff,
  planet: 0x3f5f7d,
  star: 0xffffff,
} as const;

/** 星点半径范围。上界受相机远裁剪面约束 —— 超出去的点会被整片切掉，且不报错 */
const STAR_INNER_RADIUS = 26;
const STAR_OUTER_RADIUS = 96;
const STAR_COUNT = 1400;

const meta: SceneMeta = {
  id: 'spacecraft',
  title: '太空飞船',
  summary: '舱体、舷窗、太阳能板与球壳星点场',
  tags: ['太空', '载具'],
  icon: 'ship',
  order: 40,
  accent: '#7fd4ff',
  background: '#05070f',
  climate: 'space',
  camera: {
    position: [11, 5.5, 15],
    target: [0, 0, 0],
  },
  distance: [6, 55],
  // 真空里环境光几乎为零，反射主要来自恒星与行星，IBL 只留一点点补细节
  environmentIntensity: 0.25,
  options: { showGrid: false },
};

function create(): SceneBuild {
  const prng = createPrng(90210);
  const scene = new Scene();
  scene.background = new Color(meta.background);
  // 注意这里**没有** addFog —— 见文件头说明

  // ---------- 光照：一颗恒星 + 行星反照 ----------
  scene.add(new AmbientLight(0x22304a, 0.55));
  addSun(scene, {
    color: 0xfff4e0,
    intensity: 3.8,
    position: [26, 18, -14],
    extent: 34,
  });
  // 行星反照的冷光，从下方打上来 —— 少了它舱体下腹会黑成一个剪影
  const albedo = new PointLight(0x4a7ab5, 90, 90, 2);
  albedo.position.set(2, -18, -22);
  scene.add(albedo);

  // ---------- 星点场 ----------
  /**
   * 在球壳上取点要**均匀**，所以不能对角度直接均匀采样 ——
   * 那样会把点挤到两极（球面面积在极区小，同样的角度间隔挤了同样多的点）。
   * 正确做法是对 `cos θ` 均匀采样，再开方回角度。
   */
  const starPositions = new Float32Array(STAR_COUNT * 3);
  for (let index = 0; index < STAR_COUNT; index += 1) {
    const cosTheta = prng() * 2 - 1;
    const phi = prng() * Math.PI * 2;
    const radius = randomIn(prng, STAR_INNER_RADIUS, STAR_OUTER_RADIUS);
    const sinTheta = Math.sqrt(1 - cosTheta * cosTheta);

    starPositions[index * 3] = radius * sinTheta * Math.cos(phi);
    starPositions[index * 3 + 1] = radius * cosTheta;
    starPositions[index * 3 + 2] = radius * sinTheta * Math.sin(phi);
  }

  const starGeometry = new BufferGeometry();
  starGeometry.setAttribute('position', new Float32BufferAttribute(starPositions, 3));
  const starMaterial = new PointsMaterial({
    color: PALETTE.star,
    size: 0.9,
    sizeAttenuation: true,
    transparent: true,
    opacity: 0.92,
  });
  const stars = new Points(starGeometry, starMaterial);
  scene.add(stars);

  // ---------- 远处的行星 ----------
  const planet = new Mesh(
    new SphereGeometry(16, 48, 32),
    solidMaterial(PALETTE.planet, { metalness: 0.05, roughness: 0.92 }),
  );
  planet.position.set(10, -42, -74);
  scene.add(planet);

  const grid = new GridHelper(40, 20, 0x2c4a6e, 0x1d3350);
  grid.position.y = -6;
  scene.add(grid);

  // ---------- 飞船本体 ----------
  const hullMaterial = solidMaterial(PALETTE.hull, { metalness: 0.82, roughness: 0.3 });
  const darkMaterial = solidMaterial(PALETTE.hullDark, { metalness: 0.7, roughness: 0.42 });
  const panelMaterial = solidMaterial(PALETTE.panel, { metalness: 0.5, roughness: 0.35 });
  const windowMaterial = glowMaterial(PALETTE.window, 2.1);
  const flameMaterial = glowMaterial(PALETTE.flame, 2.4);

  const hullRadius = 2;
  const hullLength = 7;

  // 舱体用圆柱 + 两端球封口。比直接用胶囊几何体保险：不同 three 版本对后者的
  // 分段参数解释略有差异，而这三个几何体的行为是完全稳定的。
  const hull = new Mesh(new CylinderGeometry(hullRadius, hullRadius, hullLength, 24), hullMaterial);
  // 圆柱默认沿 y 轴，转成沿 z 轴（船头朝 -z）
  hull.rotation.x = Math.PI / 2;
  hull.castShadow = true;
  scene.add(hull);

  [hullLength / 2, -hullLength / 2].forEach((z) => {
    const cap = new Mesh(new SphereGeometry(hullRadius, 24, 16), hullMaterial);
    cap.position.z = z;
    cap.castShadow = true;
    scene.add(cap);
  });

  // 船头锥
  const nose = new Mesh(new ConeGeometry(hullRadius, 2.8, 24), hullMaterial);
  nose.rotation.x = -Math.PI / 2;
  nose.position.z = -(hullLength / 2 + 1.4);
  nose.castShadow = true;
  scene.add(nose);

  // 舷窗：左右两排
  const windowGeometry = new CylinderGeometry(0.55, 0.55, 0.14, 20);
  [-1.1, 1.1].forEach((z) => {
    [1, -1].forEach((side) => {
      const window = new Mesh(windowGeometry, windowMaterial);
      // 圆面朝 ±x：圆柱默认轴沿 y，绕 z 转 90° 后轴就沿 x 了
      window.rotation.z = Math.PI / 2;
      window.position.set(side * hullRadius, 0, z);
      scene.add(window);
    });
  });

  // 散热鳍
  addBox(scene, darkMaterial, [0.22, 2.8, 1.8], [0, 1.4, 3.2]);
  addBox(scene, darkMaterial, [0.22, 2.8, 1.8], [0, -1.4, 3.2]);

  // 太阳能板：两片翼，略带上反角
  [1, -1].forEach((side) => {
    addBox(scene, darkMaterial, [1.8, 0.2, 0.5], [side * 3.4, 0, 1.6]);
    const wing = addBox(scene, panelMaterial, [7.4, 0.12, 3], [side * 7.6, 0.35, 1.6]);
    wing.rotation.z = side * -0.07;
  });

  // 引擎与尾焰
  const engines = [0.95, -0.95].map((x) => {
    const nozzle = new Mesh(new ConeGeometry(0.9, 1.9, 18), darkMaterial);
    /**
     * 喷钟的**窄端接船体、喇叭口朝外** —— 这才是真实引擎的形状。
     * ConeGeometry 的尖是窄端，所以尖要转向船头方向（-z），
     * 即绕 x 轴转 **-90°**（转 +90° 会让尖朝外，看起来像一枚顶在船尾的子弹头）。
     */
    nozzle.rotation.x = -Math.PI / 2;
    nozzle.position.set(x, 0, hullLength / 2 + 0.9);
    nozzle.castShadow = true;
    scene.add(nozzle);

    const flame = new Mesh(new ConeGeometry(0.55, 2.2, 16), flameMaterial);
    // 焰正好相反：宽端贴喷口、尖端指向远处，所以转 +90° 让尖朝 +z
    flame.rotation.x = Math.PI / 2;
    flame.position.set(x, 0, hullLength / 2 + 2.6);
    scene.add(flame);
    return flame;
  });

  return {
    scene,

    update(elapsed: number): void {
      /**
       * 这里**不转整艘船**。
       *
       * 曾经试过 `scene.rotation.y = elapsed * k`，结果连星点场一起转了 ——
       * 星空的运动被飞船的旋转抵消，画面反而不动了。飞船的"环绕观察"交给
       * 相机的自动环绕（`autoRotate`），运动由星点场和尾焰各自提供。
       */
      stars.rotation.y = -elapsed * 0.008;
      // 尾焰长度脉动
      const pulse = 1 + Math.sin(elapsed * 5.5) * 0.16;
      engines.forEach((flame, index) => {
        flame.scale.set(1, 1, pulse + index * 0.03);
      });
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

export const spacecraft: SceneDefinition = { meta, create };
