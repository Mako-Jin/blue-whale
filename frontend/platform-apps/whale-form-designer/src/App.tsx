// BlueWhale whale-form-designer 路由入口：登录页 + 受保护应用（主框架 + 子路由）
// F6 接线：用真实页面组件替换骨架占位（表定义 / 表单视图 / 设计器 / 填报 / 数据）。

import type { RouteObject } from 'react-router-dom';
import { Navigate, useRoutes } from 'react-router-dom';
import AppLayout from './layouts/AppLayout';
import LoginPage from './pages/Login';
import FormDataList from './pages/data/FormDataList';
import FormDesigner from './pages/designer/FormDesigner';
import FormList from './pages/form/FormList';
import FormRender from './render/FormRender';
import TableDefinitionList from './pages/table/TableDefinitionList';
import { GuestOnly, RequireAuth } from './router/guards';

const routes: RouteObject[] = [
  {
    path: '/login',
    element: (
      <GuestOnly>
        <LoginPage />
      </GuestOnly>
    ),
  },
  {
    path: '/',
    element: (
      <RequireAuth>
        <AppLayout />
      </RequireAuth>
    ),
    children: [
      { index: true, element: <Navigate to="/tables" replace /> },
      { path: 'tables', element: <TableDefinitionList /> },
      { path: 'forms', element: <FormList /> },
      { path: 'design', element: <FormDesigner /> },
      { path: 'design/:id', element: <FormDesigner /> },
      { path: 'render/:id', element: <FormRender /> },
      { path: 'data', element: <FormDataList /> },
    ],
  },
];

export default function App() {
  return useRoutes(routes);
}
