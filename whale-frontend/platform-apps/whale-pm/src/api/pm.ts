// Whale PM 接口定义：项目 / 成员 / 状态 / 流转 / 工作项 / 迭代 / 看板 / 报表

import { http, type PageResult } from './client';

/** 工作项类型 */
export type WorkItemType = 'EPIC' | 'STORY' | 'TASK' | 'BUG' | 'SUBTASK';

/** 工作项优先级 */
export type Priority = 'HIGHEST' | 'HIGH' | 'MEDIUM' | 'LOW' | 'LOWEST';

/** 状态分类 */
export type StatusCategory = 'TODO' | 'IN_PROGRESS' | 'DONE';

/** 项目 */
export interface Project {
  id: string;
  key: string;
  name: string;
  projectType: string;
  visibility: string;
  leadUserId?: string | null;
  description?: string | null;
  status: string;
  createTime?: string;
  updateTime?: string;
}

/** 项目成员 */
export interface ProjectMember {
  id: string;
  projectId: string;
  userId: string;
  projectRole: string;
  joinedTime?: string;
  createTime?: string;
}

/** 状态配置 */
export interface Status {
  id: string;
  projectId: string;
  code: string;
  name: string;
  category: string;
  sortNo?: number;
  isDefault?: boolean;
  createTime?: string;
}

/** 流转配置 */
export interface Transition {
  id: string;
  projectId: string;
  fromStatusId: string;
  toStatusId: string;
  name: string;
  requireComment?: boolean;
  requirePermission?: string | null;
  createTime?: string;
}

/** 工作项 */
export interface WorkItem {
  id: string;
  projectId: string;
  itemNo: number;
  key: string;
  type: WorkItemType;
  title: string;
  description?: string | null;
  statusId: string;
  statusCode: string;
  statusName: string;
  statusCategory: StatusCategory;
  priority: Priority;
  assigneeId?: string | null;
  reporterId?: string | null;
  parentId?: string | null;
  sprintId?: string | null;
  storyPoints?: number | null;
  dueDate?: string | null;
  labels?: string | null;
  rankValue?: string;
  version: number;
  createTime?: string;
  updateTime?: string;
}

/** 工作项动态 */
export interface Activity {
  id: string;
  projectId: string;
  workItemId: string;
  actorId?: string | null;
  action: string;
  field?: string | null;
  oldValue?: string | null;
  newValue?: string | null;
  createTime?: string;
}

/** 工作项评论 */
export interface Comment {
  id: string;
  workItemId: string;
  authorId?: string | null;
  content: string;
  parentId?: string | null;
  createTime?: string;
}

/** 工作项附件 */
export interface Attachment {
  id: string;
  workItemId: string;
  fileId: string;
  fileName: string;
  uploaderId?: string | null;
  createTime?: string;
}

/** 工作项关联 */
export interface WorkItemLink {
  id: string;
  sourceId: string;
  targetId: string;
  targetKey?: string;
  targetTitle?: string;
  linkType: string;
  createTime?: string;
}

/** 迭代 */
export interface Sprint {
  id: string;
  projectId: string;
  name: string;
  goal?: string | null;
  startDate?: string | null;
  endDate?: string | null;
  status: 'FUTURE' | 'ACTIVE' | 'CLOSED';
  createTime?: string;
  updateTime?: string;
}

/** 看板列 */
export interface BoardColumn {
  id: string;
  boardId: string;
  statusId: string;
  statusCode?: string;
  name: string;
  sortNo: number;
  wipLimit: number;
}

/** 看板 */
export interface Board {
  id: string;
  projectId: string;
  name: string;
  boardType: string;
  columns?: BoardColumn[];
  createTime?: string;
  updateTime?: string;
}

/** 看板执行视图列 */
export interface BoardColumnView extends BoardColumn {
  itemCount: number;
  overloaded: boolean;
  items: WorkItem[];
}

/** 看板执行视图 */
export interface BoardView {
  id: string;
  projectId: string;
  name: string;
  boardType: string;
  columns: BoardColumnView[];
}

/** 燃尽图数据点 */
export interface BurndownPoint {
  date: string;
  remainingPoints: number;
  idealPoints: number;
}

/** 燃尽图 */
export interface Burndown {
  sprintId?: string | null;
  sprintName?: string | null;
  startDate?: string | null;
  endDate?: string | null;
  totalPoints: number;
  points: BurndownPoint[];
}

/** 速率图单项 */
export interface VelocitySprint {
  sprintId: string;
  sprintName: string;
  startDate?: string | null;
  endDate?: string | null;
  committedPoints: number;
  completedPoints: number;
}

/** 速率图 */
export interface Velocity {
  sprints: VelocitySprint[];
}

/** 累积流图序列 */
export interface CumulativeFlowSeries {
  category: StatusCategory;
  values: number[];
}

/** 累积流图 */
export interface CumulativeFlow {
  startDate: string;
  endDate: string;
  dates: string[];
  series: CumulativeFlowSeries[];
}

/** 成员负载 */
export interface WorkloadMember {
  assigneeId?: string | null;
  totalCount: number;
  openCount: number;
  doneCount: number;
  openPoints: number;
  donePoints: number;
}

/** 成员负载图 */
export interface Workload {
  members: WorkloadMember[];
}

/** 项目查询参数 */
export interface ProjectQuery {
  current?: number;
  size?: number;
  keyword?: string;
  status?: string;
  projectType?: string;
}

/** 工作项查询参数 */
export interface WorkItemQuery {
  current?: number;
  size?: number;
  projectId: string;
  keyword?: string;
  statusId?: string;
  assigneeId?: string;
  type?: string;
  sprintId?: string;
  parentId?: string;
  priority?: string;
}

/** 项目 */
export const projectApi = {
  page: (query: ProjectQuery) => http.get<PageResult<Project>>('/pm/v1/project/page', { ...query }),
  detail: (id: string) => http.get<Project>(`/pm/v1/project/${id}`),
  create: (body: Record<string, unknown>) => http.post<Project>('/pm/v1/project', body),
  update: (id: string, body: Record<string, unknown>) =>
    http.put<Project>(`/pm/v1/project/${id}`, body),
  changeStatus: (id: string, status: string) =>
    http.put<Project>(`/pm/v1/project/${id}/status`, { status }),
  remove: (id: string) => http.del<void>(`/pm/v1/project/${id}`),
};

/** 项目成员 */
export const memberApi = {
  list: (projectId: string) => http.get<ProjectMember[]>(`/pm/v1/project/${projectId}/member`),
  add: (projectId: string, body: { userId: number; projectRole: string }) =>
    http.post<ProjectMember>(`/pm/v1/project/${projectId}/member`, body),
  updateRole: (id: string, projectRole: string) =>
    http.put<ProjectMember>(`/pm/v1/member/${id}`, { projectRole }),
  remove: (id: string) => http.del<void>(`/pm/v1/member/${id}`),
};

/** 状态与流转配置 */
export const workflowApi = {
  statuses: (projectId: string) => http.get<Status[]>(`/pm/v1/project/${projectId}/status`),
  addStatus: (projectId: string, body: Partial<Status>) =>
    http.post<Status>(`/pm/v1/project/${projectId}/status`, body),
  updateStatus: (id: string, body: Partial<Status>) =>
    http.put<Status>(`/pm/v1/status/${id}`, body),
  removeStatus: (id: string) => http.del<void>(`/pm/v1/status/${id}`),
  transitions: (projectId: string) =>
    http.get<Transition[]>(`/pm/v1/project/${projectId}/transition`),
  addTransition: (projectId: string, body: Record<string, unknown>) =>
    http.post<Transition>(`/pm/v1/project/${projectId}/transition`, body),
  removeTransition: (id: string) => http.del<void>(`/pm/v1/transition/${id}`),
};

/** 工作项 */
export const itemApi = {
  page: (query: WorkItemQuery) =>
    http.get<PageResult<WorkItem>>('/pm/v1/work-item/page', { ...query }),
  detail: (id: string) => http.get<WorkItem>(`/pm/v1/work-item/${id}`),
  create: (body: Record<string, unknown>) => http.post<WorkItem>('/pm/v1/work-item', body),
  update: (id: string, body: Record<string, unknown>) =>
    http.put<WorkItem>(`/pm/v1/work-item/${id}`, body),
  remove: (id: string) => http.del<void>(`/pm/v1/work-item/${id}`),
  transition: (id: string, body: { targetStatusId: string; comment?: string }) =>
    http.put<WorkItem>(`/pm/v1/work-item/${id}/transition`, body),
  rank: (id: string, body: { beforeItemId?: string; afterItemId?: string }) =>
    http.put<WorkItem>(`/pm/v1/work-item/${id}/rank`, body),
  activities: (id: string) => http.get<Activity[]>(`/pm/v1/work-item/${id}/activity`),
  projectActivities: (projectId: string) =>
    http.get<Activity[]>(`/pm/v1/project/${projectId}/activity`),
  comments: (id: string) => http.get<Comment[]>(`/pm/v1/work-item/${id}/comment`),
  addComment: (id: string, body: { content: string; parentId?: string }) =>
    http.post<Comment>(`/pm/v1/work-item/${id}/comment`, body),
  removeComment: (commentId: string) => http.del<void>(`/pm/v1/comment/${commentId}`),
  attachments: (id: string) => http.get<Attachment[]>(`/pm/v1/work-item/${id}/attachment`),
  addAttachment: (id: string, body: { fileId: number; fileName: string }) =>
    http.post<Attachment>(`/pm/v1/work-item/${id}/attachment`, body),
  removeAttachment: (attachmentId: string) => http.del<void>(`/pm/v1/attachment/${attachmentId}`),
  links: (id: string) => http.get<WorkItemLink[]>(`/pm/v1/work-item/${id}/link`),
  addLink: (id: string, body: { targetItemId: number; linkType: string }) =>
    http.post<WorkItemLink>(`/pm/v1/work-item/${id}/link`, body),
  removeLink: (linkId: string) => http.del<void>(`/pm/v1/work-item/link/${linkId}`),
};

/** 迭代 */
export const sprintApi = {
  page: (query: { current?: number; size?: number; projectId: string; status?: string }) =>
    http.get<PageResult<Sprint>>('/pm/v1/sprint/page', { ...query }),
  detail: (id: string) => http.get<Sprint>(`/pm/v1/sprint/${id}`),
  create: (body: Record<string, unknown>) => http.post<Sprint>('/pm/v1/sprint', body),
  update: (id: string, body: Record<string, unknown>) =>
    http.put<Sprint>(`/pm/v1/sprint/${id}`, body),
  remove: (id: string) => http.del<void>(`/pm/v1/sprint/${id}`),
  start: (id: string) => http.put<Sprint>(`/pm/v1/sprint/${id}/start`),
  close: (id: string) => http.put<Sprint>(`/pm/v1/sprint/${id}/close`),
  items: (id: string) => http.get<WorkItem[]>(`/pm/v1/sprint/${id}/item`),
  addItems: (id: string, itemIds: string[]) =>
    http.post<number>(`/pm/v1/sprint/${id}/item`, {
      itemIds: itemIds.map((value) => Number(value)),
    }),
  removeItem: (id: string, itemId: string) => http.del<void>(`/pm/v1/sprint/${id}/item/${itemId}`),
};

/** 看板 */
export const boardApi = {
  list: (projectId: string) => http.get<Board[]>('/pm/v1/board', { projectId }),
  detail: (id: string) => http.get<Board>(`/pm/v1/board/${id}`),
  view: (id: string) => http.get<BoardView>(`/pm/v1/board/${id}/view`),
  create: (body: { projectId: number; name: string; boardType?: string }) =>
    http.post<Board>('/pm/v1/board', body),
  update: (id: string, body: { projectId: number; name: string; boardType?: string }) =>
    http.put<Board>(`/pm/v1/board/${id}`, body),
  remove: (id: string) => http.del<void>(`/pm/v1/board/${id}`),
  addColumn: (id: string, body: Record<string, unknown>) =>
    http.post<Board>(`/pm/v1/board/${id}/column`, body),
  updateColumn: (id: string, columnId: string, body: Record<string, unknown>) =>
    http.put<Board>(`/pm/v1/board/${id}/column/${columnId}`, body),
  removeColumn: (id: string, columnId: string) =>
    http.del<void>(`/pm/v1/board/${id}/column/${columnId}`),
  drag: (id: string, body: { workItemId: number; targetStatusId: number; comment?: string }) =>
    http.put<BoardView>(`/pm/v1/board/${id}/drag`, body),
};

/** 报表 */
export const reportApi = {
  burndown: (projectId: string, sprintId?: string) =>
    http.get<Burndown>('/pm/v1/report/burndown', { projectId, sprintId }),
  velocity: (projectId: string, limit?: number) =>
    http.get<Velocity>('/pm/v1/report/velocity', { projectId, limit }),
  cumulativeFlow: (
    projectId: string,
    params?: { sprintId?: string; startDate?: string; endDate?: string },
  ) => http.get<CumulativeFlow>('/pm/v1/report/cumulative-flow', { projectId, ...params }),
  workload: (projectId: string) => http.get<Workload>('/pm/v1/report/workload', { projectId }),
};
