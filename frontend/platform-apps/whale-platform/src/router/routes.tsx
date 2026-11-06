// 基础路由配置
// 基座自身路由 + 子应用挂载点；/login 独立于主布局，主布局整体由 RequireAuth 守卫

import { lazy, Suspense } from 'react';
import Dashboard from '../pages/Dashboard';
import MenuList from '../pages/system/MenuList';
import RoleList from '../pages/system/RoleList';
import SubAppContainer from '../pages/SubAppContainer';
import UserList from '../pages/system/UserList';
import { GuestOnly, RequireAuth } from './guards';

const MainLayout = lazy(() => import('../layouts/MainLayout'));
const LoginPage = lazy(() => import('../pages/login'));

export const routes = [
  {
    path: '/login',
    element: (
      <Suspense fallback={null}>
        <GuestOnly>
          <LoginPage />
        </GuestOnly>
      </Suspense>
    ),
  },
  {
    path: '/',
    element: (
      <Suspense fallback={null}>
        <RequireAuth>
          <MainLayout />
        </RequireAuth>
      </Suspense>
    ),
    children: [
      // 工作台（首页，多标签页中固定不可关）
      { index: true, element: <Dashboard /> },
      // 系统管理（只读查询，维护仍在 whale-admin）
      { path: 'system/users', element: <UserList /> },
      { path: 'system/roles', element: <RoleList /> },
      { path: 'system/menus', element: <MenuList /> },
      // 子应用路由 - qiankun 根据 activeRule 激活
      { path: 'portal/*', element: <SubAppContainer /> },
      { path: 'erp/*', element: <SubAppContainer /> },
      { path: 'oa/*', element: <SubAppContainer /> },
      { path: 'mes/*', element: <SubAppContainer /> },
      { path: 'pdm/*', element: <SubAppContainer /> },
      // 消息中心（whale-im 子应用，具体页面由 qiankun 接管）
      { path: 'im/*', element: <SubAppContainer /> },
      // 项目管理（whale-pm 子应用，对标 Jira / 禅道）
      { path: 'pm/*', element: <SubAppContainer /> },
      // 合同管理（whale-contract 子应用，Wave 5.61 M1）
      { path: 'contract/*', element: <SubAppContainer /> },
      // 文档中心（whale-doc 子应用，Wave 5.74 M1 骨架）
      { path: 'doc/*', element: <SubAppContainer /> },
      // 音乐中心（whale-music 子应用，Wave 5.75 M1）
      { path: 'music/*', element: <SubAppContainer /> },
      // 视频中心（whale-video 子应用，Wave 5.76）
      { path: 'video/*', element: <SubAppContainer /> },
      // 城池导航（whale-scene 子应用）
      { path: 'scene/*', element: <SubAppContainer /> },
      // 学习培训考试（whale-lms 子应用，Wave 5.87 M1）
      { path: 'lms/*', element: <SubAppContainer /> },
      // 党政工会（whale-party 子应用，Wave 5.88 M-P0 独立起服务）
      { path: 'party/*', element: <SubAppContainer /> },
      // 邮箱（whale-mail 子应用，Wave 5.89 M0 独立起服务）
      { path: 'mail/*', element: <SubAppContainer /> },
    ],
  },
];
