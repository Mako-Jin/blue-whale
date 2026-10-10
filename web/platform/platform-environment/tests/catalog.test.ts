import { describe, expect, it } from 'vitest';
import {
  DEFAULT_SCENE_ID,
  DEFAULT_SCENE_OPTIONS,
  defaultScene,
  findScene,
  loadSceneCatalog,
  localSceneCatalog,
  mergeCatalogOverrides,
  sceneOptions,
} from '@/scene';

/**
 * 注册表与清单的行为契约。
 *
 * 重点在两条护栏：
 * - 护栏 A：清单只能经 `localSceneCatalog()` / `loadSceneCatalog()` 取，
 *   且两者当前必须给出同一份数据（将来接了后端，异步那一个才会不同）；
 * - 护栏 B：远端覆盖只能改元数据，**不能凭空造出场景**。
 */

const LOCAL = localSceneCatalog().scenes;

describe('清单', () => {
  it('内置清单同步可用，且首屏不必等异步', () => {
    expect(LOCAL.length).toBeGreaterThan(0);
    expect(localSceneCatalog().source).toBe('local');
  });

  it('异步取到的清单与内置一致（当前清单只有本地一个来源）', async () => {
    const loaded = await loadSceneCatalog();

    expect(loaded.source).toBe('local');
    expect(loaded.scenes.map((item) => item.meta.id)).toEqual(LOCAL.map((item) => item.meta.id));
  });

  it('默认场景必须在清单里，否则首帧无可渲染', () => {
    expect(LOCAL.some((item) => item.meta.id === DEFAULT_SCENE_ID)).toBe(true);
    expect(defaultScene().meta.id).toBe(DEFAULT_SCENE_ID);
  });
});

describe('findScene', () => {
  it('按 id 找到场景', () => {
    expect(findScene(DEFAULT_SCENE_ID)?.meta.id).toBe(DEFAULT_SCENE_ID);
  });

  it('认不出的 id 返回 null 而不是抛异常 —— id 可能来自远端配置或地址栏', () => {
    expect(findScene('does-not-exist')).toBeNull();
    expect(findScene('')).toBeNull();
    expect(findScene(null)).toBeNull();
    expect(findScene(undefined)).toBeNull();
  });

  it('不会误命中 Object 原型上的键', () => {
    // 用 in 判断的实现会在这里返回一个奇怪的东西
    expect(findScene('constructor')).toBeNull();
    expect(findScene('toString')).toBeNull();
  });
});

describe('sceneOptions', () => {
  it('把场景自己的覆盖叠在全局默认值上', () => {
    const meta = LOCAL.find((item) => item.meta.options?.showGrid === false)?.meta;
    expect(meta).toBeDefined();

    const merged = sceneOptions(meta!);

    expect(merged.showGrid).toBe(false);
    // 未在场景里声明的项沿用全局默认
    expect(merged.wireframe).toBe(DEFAULT_SCENE_OPTIONS.wireframe);
    expect(merged.autoRotate).toBe(meta!.options?.autoRotate ?? DEFAULT_SCENE_OPTIONS.autoRotate);
  });
});

describe('mergeCatalogOverrides（护栏 B：远端只能覆盖，不能发明）', () => {
  const first = LOCAL[0];

  it('可以覆盖展示字段，但实现仍然只认本地那一份', () => {
    const result = mergeCatalogOverrides(LOCAL, [
      { id: first.meta.id, patch: { title: '远端改的名字', summary: '远端改的说明' } },
    ]);

    const updated = result.scenes.find((item) => item.meta.id === first.meta.id);
    expect(updated?.meta.title).toBe('远端改的名字');
    expect(updated?.meta.summary).toBe('远端改的说明');
    // 关键：`create` 必须是同一个函数引用 —— 几何体不可能从远端下发
    expect(updated?.create).toBe(first.create);
    expect(result.unknownIds).toEqual([]);
  });

  it('可以隐藏场景', () => {
    const result = mergeCatalogOverrides(LOCAL, [{ id: first.meta.id, hidden: true }]);

    expect(result.scenes.some((item) => item.meta.id === first.meta.id)).toBe(false);
    expect(result.scenes).toHaveLength(LOCAL.length - 1);
  });

  it('可以改排序，结果按新的 order 重排', () => {
    const last = LOCAL[LOCAL.length - 1];
    const result = mergeCatalogOverrides(LOCAL, [{ id: last.meta.id, order: -10 }]);

    expect(result.scenes[0].meta.id).toBe(last.meta.id);
  });

  it('认不出的 id 只记进 unknownIds —— 既不抛错，也不凭空造一个场景出来', () => {
    const result = mergeCatalogOverrides(LOCAL, [
      { id: 'atlantis', patch: { title: '亚特兰蒂斯' } },
    ]);

    expect(result.unknownIds).toEqual(['atlantis']);
    // 场景数不变：造出来也只会在选中时白屏
    expect(result.scenes).toHaveLength(LOCAL.length);
  });

  it('是不修改入参的纯函数', () => {
    const titlesBefore = LOCAL.map((item) => item.meta.title);

    mergeCatalogOverrides(LOCAL, [
      { id: first.meta.id, patch: { title: '被改过' } },
      { id: first.meta.id, hidden: true },
    ]);

    expect(LOCAL.map((item) => item.meta.title)).toEqual(titlesBefore);
    expect(LOCAL.some((item) => item.meta.id === first.meta.id)).toBe(true);
  });

  it('空覆盖返回按 order 排好的原清单', () => {
    const result = mergeCatalogOverrides(LOCAL, []);

    expect(result.scenes).toHaveLength(LOCAL.length);
    expect(result.scenes[0].meta.id).toBe(LOCAL[0].meta.id);
  });
});
