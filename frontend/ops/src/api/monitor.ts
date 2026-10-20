// whale-monitor 服务监控 API 客户端（经网关 /monitor/** 转发）

import { get } from './http';

/** GET /monitor/service/list 返回的服务聚合概览 */
export interface ServiceSummaryVO {
  serviceName: string;
  instanceCount: number;
  healthyInstanceCount: number;
}

/** GET /monitor/service/{serviceName}/instances 返回的实例信息 */
export interface ServiceInstanceVO {
  serviceName: string;
  host: string;
  port: number;
  healthy: boolean;
  status: string;
}

// 服务概览列表（服务名 + 实例数 + 健康实例数）
export function getServiceList(): Promise<ServiceSummaryVO[]> {
  return get<ServiceSummaryVO[]>('/monitor/service/list');
}

// 指定服务的实例列表
export function getInstances(serviceName: string): Promise<ServiceInstanceVO[]> {
  return get<ServiceInstanceVO[]>(`/monitor/service/${encodeURIComponent(serviceName)}/instances`);
}
