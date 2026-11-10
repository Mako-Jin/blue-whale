// PM 子应用根组件：项目列表 → 项目工作区（概览/Backlog/看板/迭代/报表/设置）→ 工作项详情

import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import ProjectList from './pages/ProjectList';
import Workspace from './pages/Workspace';
import Overview from './pages/Overview';
import Backlog from './pages/Backlog';
import BoardPage from './pages/BoardPage';
import SprintPage from './pages/SprintPage';
import ReportPage from './pages/ReportPage';
import Setting from './pages/Setting';
import ItemDetail from './pages/ItemDetail';

export default function App() {
  return (
    <BrowserRouter basename="/pm">
      <Routes>
        <Route path="/" element={<ProjectList />} />
        <Route path="/project/:projectId" element={<Workspace />}>
          <Route index element={<Navigate to="overview" replace />} />
          <Route path="overview" element={<Overview />} />
          <Route path="backlog" element={<Backlog />} />
          <Route path="board" element={<BoardPage />} />
          <Route path="sprint" element={<SprintPage />} />
          <Route path="report" element={<ReportPage />} />
          <Route path="setting" element={<Setting />} />
        </Route>
        <Route path="/project/:projectId/item/:itemId" element={<ItemDetail />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
