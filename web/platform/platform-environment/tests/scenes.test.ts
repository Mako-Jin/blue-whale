import { describe, expect, it, vi } from 'vitest';
import {
  DirectionalLight,
  Mesh,
  MeshStandardMaterial,
  PerspectiveCamera,
  Scene,
  Vector3,
  type BufferGeometry,
  type Object3D,
  type WebGLRenderTarget,
} from 'three';
import {
  DEFAULT_ENVIRONMENT,
  localSceneCatalog,
  sampleEnvironment,
  type SceneDefinition,
} from '@/scene';
import { addSun, disposeSubtree } from '@/scene/common';

/**
 * 场景实现的通用契约。
 *
 * 刻意**遍历注册表**而不是逐个场景手写一遍：新增场景时这套断言会自动覆盖它，
 * 而"忘记给新场景写测试"恰恰是最容易发生的事。
 */

const SCENES = localSceneCatalog().scenes;

function meshesOf(root: Object3D): Mesh[] {
  const found: Mesh[] = [];
  root.traverse((child) => {
    if (child instanceof Mesh) {
      found.push(child);
    }
  });
  return found;
}

/**
 * 把整棵树的位姿拍成一个字符串。
 *
 * 精度截到 1e-6：够检出"布局变了"，又不会被浮点末位噪声误报。
 */
function poseFingerprint(root: Object3D): string {
  const values: number[] = [];
  root.traverse((child) => {
    values.push(
      child.position.x,
      child.position.y,
      child.position.z,
      child.rotation.x,
      child.rotation.y,
      child.rotation.z,
    );
  });
  return values.map((value) => value.toFixed(6)).join(',');
}

describe('场景注册表', () => {
  it('至少包含舰长点名的四类场景，外加渲染基线', () => {
    const ids = SCENES.map((definition) => definition.meta.id);

    expect(ids).toEqual(
      expect.arrayContaining([
        'ancient-architecture',
        'primeval-jungle',
        'modern-office',
        'spacecraft',
        'baseline-lab',
      ]),
    );
  });

  it('按 order 升序排列 —— 这个顺序就是选择器里的展示顺序', () => {
    const orders = SCENES.map((definition) => definition.meta.order);

    expect(orders).toEqual([...orders].sort((left, right) => left - right));
  });

  it('id 唯一', () => {
    const ids = SCENES.map((definition) => definition.meta.id);

    expect(new Set(ids).size).toBe(ids.length);
  });

  it('每个场景都有展示所需的元数据', () => {
    SCENES.forEach(({ meta }) => {
      expect(meta.title.length).toBeGreaterThan(0);
      // 描述会显示在卡片上，太长会把卡片撑变形
      expect(meta.summary.length).toBeGreaterThan(0);
      expect(meta.summary.length).toBeLessThanOrEqual(28);
      expect(meta.tags.length).toBeGreaterThan(0);
      expect(meta.accent).toMatch(/^#[0-9a-f]{6}$/i);
      expect(meta.background).toMatch(/^#[0-9a-f]{6}$/i);
    });
  });

  it('相机取景合法：拉近上限严格小于拉远上限', () => {
    SCENES.forEach(({ meta }) => {
      expect(meta.distance[0]).toBeLessThan(meta.distance[1]);
    });
  });
});

SCENES.forEach((definition: SceneDefinition) => {
  const { meta } = definition;

  describe(`场景实现：${meta.title}`, () => {
    it('能构建出带背景的场景图', () => {
      const build = definition.create();

      expect(build.scene.background).not.toBeNull();
      // 空场景说明工厂没干活 —— 这类"看起来正常但什么都没有"最坑
      expect(build.scene.children.length).toBeGreaterThan(0);

      build.dispose();
    });

    it('取景高度为正，不会把主体顶出画面或压到地下', () => {
      expect(meta.camera.position[1]).toBeGreaterThan(0);
    });

    it('update 是 elapsed 的纯函数：同一时刻推两次结果完全一致', () => {
      const build = definition.create();

      build.update(3.5);
      const first = poseFingerprint(build.scene);
      build.update(3.5);
      const second = poseFingerprint(build.scene);

      expect(second).toBe(first);

      build.dispose();
    });

    it('update 会让画面动起来', () => {
      const build = definition.create();

      build.update(0);
      const before = poseFingerprint(build.scene);
      build.update(2.4);
      const after = poseFingerprint(build.scene);

      expect(after).not.toBe(before);

      build.dispose();
    });

    it('setWireframe 切换场景内所有实体材质', () => {
      const build = definition.create();
      const materials: MeshStandardMaterial[] = [];
      build.scene.traverse((child) => {
        if (child instanceof Mesh && child.material instanceof MeshStandardMaterial) {
          materials.push(child.material);
        }
      });
      expect(materials.length).toBeGreaterThan(0);

      build.setWireframe(true);
      expect(materials.every((material) => material.wireframe)).toBe(true);

      build.setWireframe(false);
      expect(materials.every((material) => !material.wireframe)).toBe(true);

      build.dispose();
    });

    it('setGridVisible 不会抛错（地面/太空类场景可以是空实现）', () => {
      const build = definition.create();

      expect(() => {
        build.setGridVisible(false);
        build.setGridVisible(true);
      }).not.toThrow();

      build.dispose();
    });

    it('dispose 释放全部几何体并清空场景', () => {
      const build = definition.create();
      // 去重：同一场景内多个构件共享几何体，重复 spy 同一个对象会直接报错
      const geometries = [...new Set(meshesOf(build.scene).map((mesh) => mesh.geometry))];
      expect(geometries.length).toBeGreaterThan(0);

      const spies = geometries.map((geometry: BufferGeometry) => vi.spyOn(geometry, 'dispose'));
      build.dispose();

      spies.forEach((spy) => expect(spy).toHaveBeenCalled());
      expect(build.scene.children).toHaveLength(0);
    });

    it('两次构建的布局逐字一致（散布走固定种子，不用 Math.random）', () => {
      const first = definition.create();
      const second = definition.create();

      first.update(1.5);
      second.update(1.5);

      expect(poseFingerprint(second.scene)).toBe(poseFingerprint(first.scene));

      first.dispose();
      second.dispose();
    });
  });
});

describe('场景各自的取舍', () => {
  const byId = new Map(SCENES.map((definition) => [definition.meta.id, definition]));

  it('太空场景不加雾 —— 真空里没有散射介质，加雾就成了雾天', () => {
    const build = byId.get('spacecraft')?.create();

    expect(build?.scene.fog).toBeNull();

    build?.dispose();
  });

  it('丛林场景必须有雾 —— 丛林纵深感几乎全靠它', () => {
    const build = byId.get('primeval-jungle')?.create();

    expect(build?.scene.fog).not.toBeNull();

    build?.dispose();
  });

  it('丛林与太空不默认显示网格辅助线', () => {
    expect(byId.get('primeval-jungle')?.meta.options?.showGrid).toBe(false);
    expect(byId.get('spacecraft')?.meta.options?.showGrid).toBe(false);
  });

  it('现代写字楼默认显示网格 —— 它正好充当广场铺装的分格', () => {
    expect(byId.get('modern-office')?.meta.options?.showGrid).toBe(true);
  });
});

describe('可操控场景（主人公）', () => {
  const byId = new Map(SCENES.map((definition) => [definition.meta.id, definition]));

  /** 造一个不依赖 WebGL 的场景上下文：控制器只用相机位姿与 DOM 事件。 */
  function context() {
    return {
      camera: new PerspectiveCamera(),
      domElement: document.createElement('canvas'),
    };
  }

  it('只有古建筑实现了 createControls —— 别的场景没有主人公', () => {
    const withControls = SCENES.map((definition) => {
      const build = definition.create();
      const provided = typeof build.createControls === 'function';
      build.dispose();
      return { id: definition.meta.id, provided };
    }).filter((item) => item.provided);

    expect(withControls.map((item) => item.id)).toEqual(['ancient-architecture']);
  });

  it('控制器把人物加进场景图，销毁时移除（不留幽灵人物）', () => {
    const definition = byId.get('ancient-architecture');
    const build = definition?.create();
    const before = build?.scene.children.length ?? 0;

    const controls = build?.createControls?.(context());
    expect(controls).toBeDefined();
    expect(build?.scene.children.length).toBe(before + 1);

    controls?.dispose();
    expect(build?.scene.children.length).toBe(before);

    build?.dispose();
  });

  it('快照给出人物位姿、相机朝向与小地图地物', () => {
    const build = byId.get('ancient-architecture')?.create();
    const controls = build?.createControls?.(context());
    const snapshot = controls?.snapshot();

    expect(snapshot?.sceneId).toBe('ancient-architecture');
    expect(snapshot?.map.groundSize).toBeGreaterThan(0);
    // 挡人的东西必须也是地图上画出来的东西：地物不能是空的
    expect(snapshot?.map.obstacles.length).toBeGreaterThan(0);
    // 出生点在广场内
    expect(Math.abs(snapshot?.player.x ?? 0)).toBeLessThan(snapshot?.map.groundSize ?? 0);
    expect(Math.abs(snapshot?.player.z ?? 0)).toBeLessThan(snapshot?.map.groundSize ?? 0);

    controls?.dispose();
    build?.dispose();
  });

  it('出生点不在殿宇占地里 —— 否则一进场就被推出障碍', () => {
    const build = byId.get('ancient-architecture')?.create();
    const controls = build?.createControls?.(context());
    const snapshot = controls?.snapshot();

    const inside = snapshot?.map.obstacles.some(
      (obstacle) =>
        Math.abs(snapshot.player.x - obstacle.x) < obstacle.width / 2 &&
        Math.abs(snapshot.player.z - obstacle.z) < obstacle.depth / 2,
    );

    expect(inside).toBe(false);

    controls?.dispose();
    build?.dispose();
  });
});

describe('大气能力', () => {
  const byId = new Map(SCENES.map((definition) => [definition.meta.id, definition]));

  /** 实现了大气的场景 id。逐个 create 探能力再 dispose，不留场景图 */
  const atmosphereIds = () =>
    SCENES.filter((definition) => {
      const build = definition.create();
      const has = typeof build.createAtmosphere === 'function';
      build.dispose();
      return has;
    }).map((definition) => definition.meta.id);

  const outdoorIds = SCENES.filter((definition) => definition.meta.climate === 'outdoor').map(
    (definition) => definition.meta.id,
  );

  /**
   * 这条**不能**写成"只有古建筑有大气"。
   *
   * 那种写法把"一期接到哪"这个**进度**固化成了断言：别人按契约给别的室外场景补齐时，
   * 它会认认真真地变红，逼着后人把它删掉 —— 而它本来该是一条护栏。这里断言的是**方向**：
   * `outdoor` 的含义就是"有天空与天气"，那么声明了 `outdoor` 就必须真的把大气挂上。
   * 将来新增一个室外场景忘了挂，这条会立刻红。相反的约束在下面一条。
   */
  it('每个 outdoor 场景都实现了 createAtmosphere —— 声明了有天空就得真有', () => {
    expect(atmosphereIds().sort()).toEqual([...outdoorIds].sort());
  });

  it('室内与太空场景不实现大气 —— 真空里没有散射，屋顶下没有太阳', () => {
    const offenders = SCENES.filter((definition) => {
      const build = definition.create();
      const has = typeof build.createAtmosphere === 'function';
      build.dispose();
      /**
       * 这条是**契约**而非"暂时还没做"：室内与太空**永远**不会挂天穹。
       * 与上面那条合起来，`climate` 与"有没有大气"就完全绑定了 ——
       * 一个场景想拿到大气，唯一的办法是先把气候域声明对。
       */
      return has && definition.meta.climate !== 'outdoor';
    }).map((definition) => definition.meta.id);

    expect(offenders).toEqual([]);
  });

  it('大气把天穹装进场景图，dispose 之后场景清空（不留幽灵天穹）', () => {
    outdoorIds.forEach((id) => {
      const build = byId.get(id)?.create();
      const before = build?.scene.children.length ?? 0;

      const atmosphere = build?.createAtmosphere?.();
      expect(atmosphere, `${id} 应当提供大气`).toBeTruthy();
      expect(build?.scene.children.length, `${id} 的天穹应当装进场景图`).toBeGreaterThan(before);

      // `dispose()` 必须连带拆掉大气 —— 断言"清空"就等于断言没有泄漏
      build?.dispose();
      expect(build?.scene.children, `${id} 的 dispose 之后应当清空`).toHaveLength(0);
    });
  });

  it('主光跟随太阳：白天顺着太阳，夜里指向反日点（月亮在太阳对面）', () => {
    /**
     * 三个室外场景都跑一遍，而不是只挑古建筑。
     *
     * 这条测的其实是 rig 的一条性质（"主光是太阳的把手"），但它**能否落地**取决于
     * 场景有没有老老实实把主光的引用交出去。只测一个场景，另外两个把 `addSun` 的返回值
     * 丢掉、或又加了一盏平行光，都不会被发现。`keyDistance` 三个场景都用默认值，
     * 所以归一化之后的断言与场景无关。
     */
    outdoorIds.forEach((id) => {
      const build = byId.get(id)?.create();
      const atmosphere = build?.createAtmosphere?.();
      const sun = build?.scene.children.find(
        (child) => child instanceof DirectionalLight,
      ) as DirectionalLight | undefined;
      expect(sun, `${id} 应当有一盏可被驱动的主光`).toBeDefined();

      const noon = sampleEnvironment({ ...DEFAULT_ENVIRONMENT, localHours: 12 });
      const midnight = sampleEnvironment({ ...DEFAULT_ENVIRONMENT, localHours: 0 });

      // 白天：主光就是太阳方向
      atmosphere?.update(noon.look);
      const noonLight = (sun?.position ?? new Vector3()).clone().normalize();
      expect(noonLight.y).toBeCloseTo(noon.look.keyDirection[1], 5);
      expect(noonLight.x).toBeCloseTo(noon.look.keyDirection[0], 5);
      const noonIntensity = sun?.intensity ?? 0;

      /**
       * 夜里：太阳已经在地平线以下，而主光**必须在地平线之上**。
       *
       * 这就是"取反成月亮"那一步的全部意义 —— 不取反的话光会从地底往上照，
       * 比没有光还假，而且这种错误在截图里几乎看不出来（只会觉得"夜景怪怪的"）。
       */
      expect(midnight.look.keyDirection[1]).toBeLessThan(0);
      atmosphere?.update(midnight.look);
      const midnightLight = (sun?.position ?? new Vector3()).clone().normalize();
      expect(midnightLight.y).toBeGreaterThan(0);
      expect(midnightLight.y).toBeCloseTo(-midnight.look.keyDirection[1], 5);
      expect(midnightLight.z).toBeCloseTo(-midnight.look.keyDirection[2], 5);

      // 而且夜里要比正午暗一个数量级以上
      expect(sun?.intensity ?? 0).toBeLessThan(noonIntensity / 10);

      build?.dispose();
    });
  });
});

/**
 * 释放契约。
 *
 * 盯的是"切场景到底有没有漏东西" —— 漏掉的 GPU 资源不会立刻出症状，
 * 而是攒到 WebGL 上下文上限（浏览器通常 16 个）时整页黑掉。
 */
describe('释放契约', () => {
  /**
   * 影子贴图**既不是网格，也不是材质**：它由光源持有，也不随光源对象被 GC。
   *
   * 只放 `geometry`/`material` 的写法能通过"场景图清空"这类断言，
   * 却会在每次切场景时把贴图留在显存里（实测每切一次净增 2 张纹理，
   * 来回切四个场景后从 5 涨到 13）。这条测试就是为它准备的 ——
   * 给光源挂一张假的贴图，断言 `disposeSubtree` 确实碰到了它。
   */
  it('disposeSubtree 会释放光源的影子贴图 —— 它不在 geometry / material 之列', () => {
    const scene = new Scene();
    const sun = addSun(scene, {
      color: 0xffffff,
      intensity: 1,
      position: [4, 6, 3],
      extent: 12,
    });

    let disposed = false;
    sun.shadow.map = {
      dispose: () => {
        disposed = true;
      },
    } as unknown as WebGLRenderTarget;

    disposeSubtree(scene);

    expect(disposed).toBe(true);
    // 顺手确认老行为没被破坏：场景图仍然清空
    expect(scene.children).toHaveLength(0);
  });
});
