// 项目工作区上下文：项目详情与状态配置，供工作区子页面消费

import { createContext, useContext } from 'react';
import type { Project, Status } from './api/pm';

/** 工作区上下文值 */
export interface WorkspaceContextValue {
  project: Project;
  statuses: Status[];
  reloadProject: () => Promise<void>;
}

/** 工作区上下文（未挂载时为 null） */
export const WorkspaceContext = createContext<WorkspaceContextValue | null>(null);

/** 读取工作区上下文（必须在 Workspace 路由内使用） */
export function useWorkspace(): WorkspaceContextValue {
  const context = useContext(WorkspaceContext);
  if (!context) {
    throw new Error('useWorkspace 必须在项目工作区内使用');
  }
  return context;
}
