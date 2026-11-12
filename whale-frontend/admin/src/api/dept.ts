// 部门 API（经网关 → whale-service-auth /dept）

import { del, get, post, put } from './client';

export interface DeptVO {
  /** 后端 Long 序列化为字符串（雪花 ID） */
  id: string;
  /** 后端 Long 序列化为字符串；顶级为 "0" */
  parentId: string;
  deptName: string;
  sort?: number;
  leader?: string;
  createTime?: string;
  updateTime?: string;
  children?: DeptVO[];
}

/** 新增/更新部门请求体：parentId 为 "0" 或缺省表示顶级 */
export interface DeptPayload {
  /** 后端 Long 序列化为字符串 */
  parentId?: string;
  deptName: string;
  sort?: number;
  leader?: string;
}

export function treeDepts(): Promise<DeptVO[]> {
  return get<DeptVO[]>('/dept/v1/tree');
}

export function createDept(body: DeptPayload): Promise<void> {
  return post<void>('/dept/v1', body);
}

export function updateDept(id: string, body: DeptPayload): Promise<void> {
  return put<void>(`/dept/v1/${id}`, body);
}

export function deleteDept(id: string): Promise<void> {
  return del<void>(`/dept/v1/${id}`);
}
