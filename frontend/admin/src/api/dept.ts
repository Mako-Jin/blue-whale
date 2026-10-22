// 部门 API（经网关 → whale-auth /dept）

import { del, get, post, put } from './client';

export interface DeptVO {
  id: number;
  parentId: number;
  deptName: string;
  sort?: number;
  leader?: string;
  createTime?: string;
  updateTime?: string;
  children?: DeptVO[];
}

/** 新增/更新部门请求体：parentId 为 0 或缺省表示顶级 */
export interface DeptPayload {
  parentId?: number;
  deptName: string;
  sort?: number;
  leader?: string;
}

export function treeDepts(): Promise<DeptVO[]> {
  return get<DeptVO[]>('/dept/tree');
}

export function createDept(body: DeptPayload): Promise<void> {
  return post<void>('/dept', body);
}

export function updateDept(id: number, body: DeptPayload): Promise<void> {
  return put<void>(`/dept/${id}`, body);
}

export function deleteDept(id: number): Promise<void> {
  return del<void>(`/dept/${id}`);
}
