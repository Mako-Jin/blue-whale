// OA 业务 API 层（骨架）
//
// TODO: 接入后端 /oa/** 接口，经网关 8080 代理；后端未填充前先接占位数据源。
// 后续接入时替换为：http.get('/oa/document/list') / http.post('/oa/document', payload) 等。

/** 公文状态 */
export type OaDocumentStatus = 'ENABLED' | 'DISABLED';

/** 公文/流程单据记录 */
export interface OaDocument {
  id: string;
  /** 编码 */
  code: string;
  /** 名称 */
  name: string;
  /** 状态 */
  status: OaDocumentStatus;
  /** 备注 */
  remark?: string;
}

/** 新增/编辑入参（编辑时携带 id） */
export interface OaDocumentInput {
  id?: string;
  code: string;
  name: string;
  status: OaDocumentStatus;
  remark?: string;
}

/** 占位数据源（后端未填充前使用） */
const MOCK_DOCUMENTS: OaDocument[] = [
  { id: '1', code: 'OA-001', name: '示例公文 A', status: 'ENABLED', remark: '占位数据' },
  { id: '2', code: 'OA-002', name: '示例公文 B', status: 'DISABLED', remark: '占位数据' },
];

/** 查询公文/流程单据列表 */
export function listOaDocuments(): Promise<OaDocument[]> {
  return Promise.resolve(MOCK_DOCUMENTS.map((item) => ({ ...item })));
}

/** 保存公文/流程单据（新增或编辑） */
export function saveOaDocument(input: OaDocumentInput): Promise<OaDocument> {
  return Promise.resolve({ ...input, id: input.id ?? String(Date.now()) });
}
