// 场景子应用根组件：城池导航（2D）/ 场景管理 / 入口绑定 / 可见性
//
// 3D 为 M3 可选增强，本波不注册 3D 路由、不加载 3D 资源。

import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import SceneLayout from './layout/SceneLayout';
import NavigationPage from './pages/NavigationPage';
import SceneEditorPage from './pages/SceneEditorPage';
import SceneListPage from './pages/SceneListPage';

export default function App() {
  return (
    <BrowserRouter basename="/scene">
      <Routes>
        <Route element={<SceneLayout />}>
          <Route index element={<Navigate to="/navigation" replace />} />
          <Route path="navigation" element={<NavigationPage />} />
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
