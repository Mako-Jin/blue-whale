// 部门 API（经网关 → whale-auth /dept）

import { get } from './http';

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

export function treeDepts(): Promise<DeptVO[]> {
  return get<DeptVO[]>('/dept/tree');
}
