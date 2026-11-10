// 邮箱子应用根组件。
//
// Wave 5.89 范围：独立服务底座（8099 / `/mail/**` / `bluewhale_mail`）+
// 账号绑定 + 文件夹 + 邮件列表/详情 + 发信 + 草稿 + 手动同步。
//
// 与 whale-message 的关系：`whale-message` 是消息管控（SMS / 公告 / 邮件通道编排），
// **不存邮件**；邮箱域独立成服务与子应用，两者无共享表。

import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import AccountPage from './pages/AccountPage';
import MailPage from './pages/MailPage';
import MailLayout from './layout/MailLayout';

export default function App() {
  return (
    <BrowserRouter basename="/mail">
      <Routes>
        <Route element={<MailLayout />}>
          <Route index element={<MailPage />} />
          <Route path="account" element={<AccountPage />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}
