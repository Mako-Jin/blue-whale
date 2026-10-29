// 文档子应用根组件：空间列表 / 页面工作台 / 搜索 / 收藏

import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import DocLayout from './layout/DocLayout';
import SpaceList from './pages/SpaceList';
import Workspace from './pages/Workspace';
import SearchPage from './pages/SearchPage';
import FavoritesPage from './pages/FavoritesPage';
import TemplatesPage from './pages/TemplatesPage';
import SharedPage from './pages/SharedPage';

export default function App() {
  return (
    <BrowserRouter basename="/doc">
      <Routes>
        <Route element={<DocLayout />}>
          <Route index element={<Navigate to="/spaces" replace />} />
          <Route path="spaces" element={<SpaceList />} />
          <Route path="space/:spaceId" element={<Workspace />} />
          <Route path="search" element={<SearchPage />} />
          <Route path="favorites" element={<FavoritesPage />} />
          <Route path="templates" element={<TemplatesPage />} />
          <Route path="share/:token" element={<SharedPage />} />
          <Route path="*" element={<Navigate to="/spaces" replace />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}
