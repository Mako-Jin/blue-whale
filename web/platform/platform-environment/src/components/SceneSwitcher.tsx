import type { CSSProperties } from 'react';
import type { SceneCatalogSource, SceneDefinition, SceneIcon, SceneId } from '@/scene';
import './SceneSwitcher.less';

type SceneSwitcherProps = {
  scenes: readonly SceneDefinition[];
  activeId: SceneId;
  onSelect: (id: SceneId) => void;
  /** 清单来源。如实标注，而不是让人猜数据从哪来 */
  source: SceneCatalogSource;
};

/**
 * 图标路径表（24×24 视口，**按描边渲染**，所以路径不需要闭合）。
 *
 * 放在组件里而不是元数据里是刻意的：`SceneMeta.icon` 只说"要哪个图标"，
 * 画成什么样属于渲染。将来清单改由后端下发时，
 * 后端能影响的只是"用哪个图标"，不可能把图形塞进来。
 */
const ICON_PATHS: Record<SceneIcon, string> = {
  // 中式屋顶（两个斜面）+ 两根檐柱
  roof: 'M2 12 L12 3 L22 12 M6.5 12 V21 M17.5 12 V21',
  // 两层树冠 + 树干
  tree: 'M12 3 L4.5 13 H19.5 Z M12 13 L6 21 H18 Z M12 21 V23',
  // 塔楼 + 楼板层线
  tower: 'M7 2 H17 V22 H7 Z M7 7.5 H17 M7 12.5 H17 M7 17.5 H17',
  // 飞船俯视：方头尖尾 + 两片太阳能板
  ship: 'M5 9 H16 L21 12 L16 15 H5 Z M1 10.5 V13.5 M1 12 H5 M23 10.5 V13.5 M23 12 H21',
  // 等距立方体线框
  cube: 'M12 2.5 L20.5 7.4 V16.6 L12 21.5 L3.5 16.6 V7.4 Z M12 2.5 V12 M12 12 L20.5 7.4 M12 12 L3.5 7.4',
};

const SOURCE_LABEL: Record<SceneCatalogSource, string> = {
  local: '内置清单',
  'remote-merged': '远端清单 · 已与内置合并',
};

/**
 * 场景选择器。
 *
 * 做成**横排卡片而不是下拉框**：场景是视觉选项，"长什么样"是选择它的主要依据。
 * 下拉框恰好把这条信息藏起来了 —— 用户得先点开、再猜是哪个。
 *
 * 缩略图没有真实截图，改用「主色 → 场景背景色」的色块加一个线框图标：
 * 不依赖任何图片资源，而每个场景的颜色本身就有足够的区分度
 * （古建筑的土黄、丛林的深绿、写字楼的暮蓝、太空的近黑）。
 */
export default function SceneSwitcher({ scenes, activeId, onSelect, source }: SceneSwitcherProps) {
  const active = scenes.find((item) => item.meta.id === activeId) ?? scenes[0];

  return (
    <section className="scene-switcher" aria-label="场景选择">
      <div className="scene-switcher__head">
        <h2 className="scene-switcher__title">场景</h2>
        <span className="scene-switcher__count">{`${scenes.length} 个`}</span>
        <span className="scene-switcher__source" title="场景清单的来源">
          {SOURCE_LABEL[source]}
        </span>
      </div>

      <ul className="scene-switcher__list">
        {scenes.map((definition) => {
          const { meta } = definition;
          const isActive = meta.id === activeId;
          return (
            <li key={meta.id} className="scene-switcher__item">
              <button
                type="button"
                className={`scene-card${isActive ? ' scene-card--active' : ''}`}
                // 供端到端脚本与排查时精确定位。按文本匹配标题会在改文案时静默失效
                data-scene-id={meta.id}
                aria-pressed={isActive}
                onClick={() => onSelect(meta.id)}
                style={
                  {
                    '--scene-accent': meta.accent,
                    '--scene-bg': meta.background,
                  } as CSSProperties
                }
              >
                <span className="scene-card__thumb" aria-hidden="true">
                  <svg viewBox="0 0 24 24" fill="none">
                    <path d={ICON_PATHS[meta.icon]} />
                  </svg>
                </span>
                <span className="scene-card__body">
                  <span className="scene-card__title">{meta.title}</span>
                  <span className="scene-card__summary">{meta.summary}</span>
                </span>
              </button>
            </li>
          );
        })}
      </ul>

      {active ? (
        <p className="scene-switcher__tags">
          {active.meta.tags.map((tag) => (
            <span className="scene-switcher__tag" key={tag}>
              {tag}
            </span>
          ))}
        </p>
      ) : null}
    </section>
  );
}
