// MES 业务 API 层（骨架）
//
// TODO: 接入后端 /mes/** 接口，经网关 8080 代理；后端未填充前先接占位数据源。
// 后续接入时替换为：http.get('/mes/work-order/list') / http.post('/mes/work-order', payload) 等。

/** 工单状态 */
export type MesWorkOrderStatus = 'ENABLED' | 'DISABLED';

/** 生产工单记录 */
export interface MesWorkOrder {
  id: string;
  /** 编码 */
  code: string;
  /** 名称 */
  name: string;
  /** 状态 */
  status: MesWorkOrderStatus;
  /** 备注 */
  remark?: string;
}

/** 新增/编辑入参（编辑时携带 id） */
export interface MesWorkOrderInput {
  id?: string;
  code: string;
  name: string;
  status: MesWorkOrderStatus;
  remark?: string;
}

/** 占位数据源（后端未填充前使用） */
const MOCK_WORK_ORDERS: MesWorkOrder[] = [
  { id: '1', code: 'MES-001', name: '示例工单 A', status: 'ENABLED', remark: '占位数据' },
  { id: '2', code: 'MES-002', name: '示例工单 B', status: 'DISABLED', remark: '占位数据' },
];

/** 查询生产工单列表 */
export function listMesWorkOrders(): Promise<MesWorkOrder[]> {
  return Promise.resolve(MOCK_WORK_ORDERS.map((item) => ({ ...item })));
}

/** 保存生产工单（新增或编辑） */
export function saveMesWorkOrder(input: MesWorkOrderInput): Promise<MesWorkOrder> {
  return Promise.resolve({ ...input, id: input.id ?? String(Date.now()) });
}
