// 场景子应用根组件：城池导航（2D / 3D）/ 场景管理 / 入口绑定 / 可见性
//
// 3D 当前为预览：先提供 2D → 3D 入口，场景选择写入用户偏好。

import { Suspense, lazy } from 'react';
import { Spin } from 'antd';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import SceneLayout from './layout/SceneLayout';
import NavigationPage from './pages/NavigationPage';
import SceneEditorPage from './pages/SceneEditorPage';
import SceneListPage from './pages/SceneListPage';

/** 3D 页按需加载：three.js 不打进 2D 首屏主包 */
const Scene3DPage = lazy(() => import('./pages/Scene3DPage'));

export default function App() {
  return (
    <BrowserRouter basename="/scene">
      <Routes>
        <Route element={<SceneLayout />}>
          <Route index element={<Navigate to="/navigation" replace />} />
          <Route path="navigation" element={<NavigationPage />} />
          <Route
            path="3d"
            element={
              <Suspense fallback={<Spin style={{ display: 'block', marginTop: 80 }} />}>
                <Scene3DPage />
              </Suspense>
            }
          />
          <Route path="manage" element={<SceneListPage mode="manage" />} />
          <Route path="manage/:id" element={<SceneEditorPage />} />
          <Route path="bindings" element={<SceneListPage mode="bindings" />} />
          <Route path="visibility" element={<SceneListPage mode="visibility" />} />
          <Route path="*" element={<Navigate to="/navigation" replace />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}
