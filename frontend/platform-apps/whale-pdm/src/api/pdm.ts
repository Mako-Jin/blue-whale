// PDM 业务 API 层（骨架）
//
// TODO: 接入后端 /pdm/** 接口，经网关 8080 代理；后端未填充前先接占位数据源。
// 后续接入时替换为：http.get('/pdm/product/list') / http.post('/pdm/product', payload) 等。

/** 产品状态 */
export type PdmProductStatus = 'ENABLED' | 'DISABLED';

/** 产品/图纸记录 */
export interface PdmProduct {
  id: string;
  /** 编码 */
  code: string;
  /** 名称 */
  name: string;
  /** 状态 */
  status: PdmProductStatus;
  /** 备注 */
  remark?: string;
}

/** 新增/编辑入参（编辑时携带 id） */
export interface PdmProductInput {
  id?: string;
  code: string;
  name: string;
  status: PdmProductStatus;
  remark?: string;
}

/** 占位数据源（后端未填充前使用） */
const MOCK_PRODUCTS: PdmProduct[] = [
  { id: '1', code: 'PDM-001', name: '示例产品 A', status: 'ENABLED', remark: '占位数据' },
  { id: '2', code: 'PDM-002', name: '示例产品 B', status: 'DISABLED', remark: '占位数据' },
];

/** 查询产品/图纸列表 */
export function listPdmProducts(): Promise<PdmProduct[]> {
  return Promise.resolve(MOCK_PRODUCTS.map((item) => ({ ...item })));
}

/** 保存产品/图纸（新增或编辑） */
export function savePdmProduct(input: PdmProductInput): Promise<PdmProduct> {
  return Promise.resolve({ ...input, id: input.id ?? String(Date.now()) });
}
