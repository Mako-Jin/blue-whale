// ERP 业务 API 层（骨架）
//
// TODO: 接入后端 /erp/** 接口，经网关 8080 代理；后端未填充前先接占位数据源。
// 后续接入时替换为：http.get('/erp/item/list') / http.post('/erp/item', payload) 等。

/** 商品/物料状态 */
export type ErpItemStatus = 'ENABLED' | 'DISABLED';

/** 商品/物料记录 */
export interface ErpItem {
  id: string;
  /** 编码 */
  code: string;
  /** 名称 */
  name: string;
  /** 状态 */
  status: ErpItemStatus;
  /** 备注 */
  remark?: string;
}

/** 新增/编辑入参（编辑时携带 id） */
export interface ErpItemInput {
  id?: string;
  code: string;
  name: string;
  status: ErpItemStatus;
  remark?: string;
}

/** 占位数据源（后端未填充前使用） */
const MOCK_ITEMS: ErpItem[] = [
  { id: '1', code: 'ERP-001', name: '示例物料 A', status: 'ENABLED', remark: '占位数据' },
  { id: '2', code: 'ERP-002', name: '示例物料 B', status: 'DISABLED', remark: '占位数据' },
];

/** 查询商品/物料列表 */
export function listErpItems(): Promise<ErpItem[]> {
  return Promise.resolve(MOCK_ITEMS.map((item) => ({ ...item })));
}

/** 保存商品/物料（新增或编辑） */
export function saveErpItem(input: ErpItemInput): Promise<ErpItem> {
  return Promise.resolve({ ...input, id: input.id ?? String(Date.now()) });
}
