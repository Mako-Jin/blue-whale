// 孪生子应用根组件：左侧模块导航 + 右侧页面（无路由库，按内部状态切换）
//
// 基座以 activeRule=/twin 挂载本子应用；页面切换只切内部状态，回写 URL 便于刷新定位。

import { useCallback, useState } from 'react';
import { Menu } from 'antd';
import ScenePage from './pages/ScenePage';
import ViewerPage from './pages/ViewerPage';

type PageKey = 'scene' | 'viewer';

const PAGES: Array<{ key: PageKey; label: string }> = [
  { key: 'scene', label: '场景管理' },
  { key: 'viewer', label: '3D 查看器' },
];

/** 从路径推断初始页（/twin/viewer → viewer），未知值回落场景页 */
function initialPage(): PageKey {
  const segment = window.location.pathname.split('/')[2] ?? '';
  const matched = PAGES.find((page) => page.key === segment);
  return matched?.key ?? 'scene';
}

export default function App() {
  const [active, setActive] = useState<PageKey>(initialPage);
  // 查看器当前场景（场景页「查看」带入；查看器内也可下拉切换）
  const [viewerSceneId, setViewerSceneId] = useState<string | null>(null);

  const openViewer = useCallback((sceneId: string) => {
    setViewerSceneId(sceneId);
    setActive('viewer');
    window.history.replaceState(null, '', `/twin/viewer`);
  }, []);

  const switchPage = useCallback((key: PageKey) => {
    setActive(key);
    window.history.replaceState(null, '', `/twin/${key}`);
  }, []);

  return (
    <div className="twin-layout">
      <aside className="twin-sidebar">
        <div className="twin-brand">数字孪生</div>
        <Menu
          mode="inline"
          selectedKeys={[active]}
          items={PAGES.map((page) => ({ key: page.key, label: page.label }))}
          onClick={(info) => switchPage(info.key as PageKey)}
        />
      </aside>
      <main className="twin-content">
        {active === 'scene' ? (
          <ScenePage onOpenViewer={openViewer} />
        ) : (
          <ViewerPage initialSceneId={viewerSceneId} />
        )}
      </main>
    </div>
  );
}
