// 党政工会子应用根组件。
//
// Wave 5.88 M-P0 范围：独立服务底座（9009 / `/party/**` / `bluewhale_party`）、
// 门禁状态工作台。业务页面随波次增量注册：
//   M-P1 党建台账（支部 / 党员 / 三会一课 / 党费）
//   M-P2 党建内容与先审后发
//   M-P3 工会（会员 / 慰问 / 福利 / 活动 / 提案）
//   M-P4 看板与收口
//
// 与 whale-oa 的关系：党政工会**独立成服务**，不在 OA 子应用内挂菜单；
// 党建内容与 OA 公告各自独立，不互相同步。

import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import PartyLayout from './layout/PartyLayout';
import PartyHomePage from './pages/PartyHomePage';

export default function App() {
  return (
    <BrowserRouter basename="/party">
      <Routes>
        <Route element={<PartyLayout />}>
          <Route index element={<PartyHomePage />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}
