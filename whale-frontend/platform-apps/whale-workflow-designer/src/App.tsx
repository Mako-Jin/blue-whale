// BlueWhale whale-workflow-designer 路由入口：登录页 + 受保护应用（主框架 + 子路由）
// F0 骨架：挂载占位页面（流程定义 / 设计器 / 发起 / 待办 / 实例），后续阶段替换实现。

import type { RouteObject } from 'react-router-dom';
import { Navigate, useRoutes } from 'react-router-dom';
import AppLayout from './layouts/AppLayout';
import LoginPage from './pages/Login';
import DefinitionList from './pages/definition/DefinitionList';
import WorkflowDesigner from './pages/designer/WorkflowDesigner';
import InstanceList from './pages/instance/InstanceList';
import ProcessStart from './pages/start/ProcessStart';
import TaskCenter from './pages/task/TaskCenter';
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
      { index: true, element: <Navigate to="/definitions" replace /> },
      { path: 'definitions', element: <DefinitionList /> },
      { path: 'design', element: <WorkflowDesigner /> },
      { path: 'design/:id', element: <WorkflowDesigner /> },
      { path: 'start', element: <ProcessStart /> },
      { path: 'tasks', element: <TaskCenter /> },
      { path: 'instances', element: <InstanceList /> },
    ],
  },
];

export default function App() {
  return useRoutes(routes);
}
