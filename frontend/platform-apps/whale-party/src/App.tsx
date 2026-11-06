// 党政工会子应用根组件。
//
// Wave 5.88 范围：独立服务底座（9009 / `/party/**` / `bluewhale_party`）+ M-P1 党建台账 + M-P2 党建内容。
// 已注册页面：
//   工作台（M-P0）/ 支部台账、党员档案、组织生活、党费台账（M-P1）
//   党建内容管理、党建园地（M-P2）
// 后续波次：M-P3 工会、M-P4 看板与收口。
//
// 与 whale-oa 的关系：党政工会**独立成服务**，不在 OA 子应用内挂菜单；
// 党建内容与 OA 公告各自独立，不互相同步。

import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import ActivityPage from './pages/ActivityPage';
import BranchPage from './pages/BranchPage';
import ContentPage from './pages/ContentPage';
import ContentPortalPage from './pages/ContentPortalPage';
import FeePage from './pages/FeePage';
import MemberPage from './pages/MemberPage';
import PartyHomePage from './pages/PartyHomePage';
import PartyLayout from './layout/PartyLayout';

export default function App() {
  return (
    <BrowserRouter basename="/party">
      <Routes>
        <Route element={<PartyLayout />}>
          <Route index element={<PartyHomePage />} />
          <Route path="branch" element={<BranchPage />} />
          <Route path="member" element={<MemberPage />} />
          <Route path="activity" element={<ActivityPage />} />
          <Route path="fee" element={<FeePage />} />
          <Route path="content" element={<ContentPage />} />
          <Route path="content/portal" element={<ContentPortalPage />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}
